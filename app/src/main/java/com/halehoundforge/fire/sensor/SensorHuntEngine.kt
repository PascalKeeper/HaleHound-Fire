package com.halehoundforge.fire.sensor

import android.content.Context
import com.halehoundforge.fire.debug.Breadcrumbs
import com.halehoundforge.fire.radio.BleDeviceRow
import com.halehoundforge.fire.radio.BleSurvey
import com.halehoundforge.fire.radio.WifiApRow
import com.halehoundforge.fire.radio.WifiSurvey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class SensorHit(
    val kind: SensorFingerprints.Kind,
    val confidence: SensorFingerprints.Confidence,
    val title: String,
    val address: String,
    val rssi: Int?,
    val transport: String,
    val signals: List<String>,
    val detail: String,
    /** Best-effort uplink / peer IP hints (LAN only for most cams) */
    val networkHints: List<String> = emptyList()
)

data class HuntReport(
    val hits: List<SensorHit>,
    val bleScanned: Int,
    val wifiScanned: Int,
    val lanHosts: Int,
    val notes: List<String>
)

/**
 * Correlates BLE + Wi‑Fi + optional LAN probes into sensor intel hits.
 * Passive / Blue Team only — no TX attacks.
 */
object SensorHuntEngine {

    suspend fun hunt(
        context: Context,
        includeLan: Boolean = true,
        bleMs: Long = 8_000L
    ): HuntReport = withContext(Dispatchers.Default) {
        Breadcrumbs.add("HUNT", "sensor hunt start lan=$includeLan")
        coroutineScope {
            val bleJob = async { BleSurvey(context).scan(bleMs) }
            val wifiJob = async { WifiSurvey(context).scanOnce() }
            val lanJob = async {
                if (includeLan) {
                    try {
                        LanProbe.scanCameraLikely(context, maxHosts = 28)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else emptyList()
            }

            val ble = bleJob.await()
            val wifi = wifiJob.await()
            val lan = lanJob.await()

            val hits = mutableListOf<SensorHit>()
            ble.forEach { hits += classifyBle(it) }
            wifi.forEach { hits += classifyWifi(it) }
            lan.forEach { hits += classifyLan(it) }

            val merged = hits
                .groupBy { "${it.kind}|${it.address}" }
                .map { (_, group) -> group.maxByOrNull { confRank(it.confidence) }!! }
                .sortedWith(
                    compareByDescending<SensorHit> { confRank(it.confidence) }
                        .thenBy { it.kind.name }
                        .thenByDescending { it.rssi ?: -999 }
                )

            val notes = mutableListOf<String>()
            notes += "Passive BLE+Wi‑Fi. No deauth / no camera hack."
            notes += "Municipal Flock often uses cellular uplink — destination IPs rarely visible from your LAN."
            notes += "LAN port scan only covers *your* Wi‑Fi subnet (spy cams / NVRs you can actually reach)."
            notes += "Matches are heuristic — verify physically before acting."
            if (ble.isEmpty()) notes += "BLE empty — enable Bluetooth + location permission."
            if (wifi.isEmpty()) notes += "Wi‑Fi scan empty — location permission / Wi‑Fi on."

            Breadcrumbs.add("HUNT", "hits=${merged.size} ble=${ble.size} wifi=${wifi.size} lan=${lan.size}")
            HuntReport(
                hits = merged,
                bleScanned = ble.size,
                wifiScanned = wifi.size,
                lanHosts = lan.size,
                notes = notes
            )
        }
    }

    private fun confRank(c: SensorFingerprints.Confidence) = when (c) {
        SensorFingerprints.Confidence.HIGH -> 3
        SensorFingerprints.Confidence.MED -> 2
        SensorFingerprints.Confidence.LOW -> 1
    }

    private fun classifyBle(row: BleDeviceRow): List<SensorHit> {
        val out = mutableListOf<SensorHit>()
        val signals = mutableListOf<String>()
        val name = row.name
        val oui = row.oui

        // --- Flock / Raven ---
        var flockScore = 0
        if (SensorFingerprints.MFG_FLOCK_XUNTONG in row.mfgIds) {
            flockScore += 3
            signals += "BLE mfg 0x09C8 (XUNTONG / Flock battery-module class)"
        }
        row.serviceUuids.forEach { u ->
            if (SensorFingerprints.isRavenServiceUuid(u)) {
                flockScore += 3
                signals += "Raven-range service UUID $u"
            }
        }
        if (oui in SensorFingerprints.FLOCK_OUI_HIGH) {
            flockScore += 3
            signals += "OUI $oui (Flock Safety assigned)"
        } else if (oui in SensorFingerprints.FLOCK_OUI_COMPONENT) {
            flockScore += 1
            signals += "OUI $oui (component vendor often on Flock HW — weak alone)"
        }
        if (name.contains("flock", true) || name.contains("raven", true) ||
            name.contains("falcon", true)
        ) {
            flockScore += 2
            signals += "name hint: $name"
        }
        if (flockScore >= 3) {
            val kind = if (
                SensorFingerprints.MFG_FLOCK_XUNTONG in row.mfgIds ||
                row.serviceUuids.any { SensorFingerprints.isRavenServiceUuid(it) }
            ) SensorFingerprints.Kind.FLOCK_RAVEN
            else SensorFingerprints.Kind.FLOCK_RELATED
            val conf = when {
                flockScore >= 5 -> SensorFingerprints.Confidence.HIGH
                flockScore >= 3 -> SensorFingerprints.Confidence.MED
                else -> SensorFingerprints.Confidence.LOW
            }
            out += SensorHit(
                kind = kind,
                confidence = conf,
                title = if (kind == SensorFingerprints.Kind.FLOCK_RAVEN) "Flock/Raven BLE sensor"
                else "Flock-related BLE device",
                address = row.address,
                rssi = row.rssi,
                transport = "BLE",
                signals = signals.toList(),
                detail = buildString {
                    appendLine("name=${name.ifBlank { "—" }}  rssi=${row.rssi}dBm")
                    appendLine("mfg=${row.manufacturer}")
                    appendLine("uuids=${row.serviceUuids.joinToString().ifBlank { "—" }}")
                    if (row.rawMfgHex.isNotBlank()) appendLine("mfg_payload=${row.rawMfgHex.take(80)}")
                    appendLine("Uplink: Flock units often use LTE/cellular — not visible as a LAN peer.")
                    appendLine("Per-camera cloud IPs require being on path (ISP/MDM/lab capture), not stock Fire OS.")
                }.trimEnd(),
                networkHints = listOf(
                    "Expected cloud: flocksafety.com infrastructure (not resolved here)",
                    "Provisioning SSID pattern Flock-* if unit in setup mode (Wi‑Fi scan)"
                )
            )
        }

        // --- AirTag / Find My ---
        if (SensorFingerprints.MFG_APPLE in row.mfgIds) {
            val appleSignals = mutableListOf("Apple company ID 0x004C")
            // Nearby Info / Find My often type byte in manufacturer data — heuristic on hex
            val raw = row.rawMfgHex.lowercase()
            var conf = SensorFingerprints.Confidence.MED
            if (raw.contains("0x4c:") || raw.contains("004c")) {
                appleSignals += "Apple mfg payload present"
            }
            // Status byte patterns commonly associated with Find My network ads (best-effort)
            if (name.isBlank() && row.connectable.not()) {
                appleSignals += "unnamed non-connectable Apple adv (tracker-like)"
                conf = SensorFingerprints.Confidence.MED
            }
            if (name.contains("airtag", true) || name.contains("find my", true)) {
                appleSignals += "name: $name"
                conf = SensorFingerprints.Confidence.HIGH
            }
            out += SensorHit(
                kind = SensorFingerprints.Kind.AIRTAG_FINDMY,
                confidence = conf,
                title = if (name.contains("airtag", true)) "AirTag / Apple tracker"
                else "Apple Find My network advertisement",
                address = row.address,
                rssi = row.rssi,
                transport = "BLE",
                signals = appleSignals,
                detail = buildString {
                    appendLine("Apple BLE presence near this tablet.")
                    appendLine("rssi=${row.rssi}  name=${name.ifBlank { "—" }}")
                    appendLine("Cannot pull owner PII from stock OS — only RF advertisement metadata.")
                    appendLine("payload=${row.rawMfgHex.take(96).ifBlank { "—" }}")
                }.trimEnd(),
                networkHints = listOf("Find My uses Apple crowd-sourced network — no fixed camera C2 IP")
            )
        }

        if (SensorFingerprints.MFG_TILE in row.mfgIds || name.contains("tile", true)) {
            out += SensorHit(
                kind = SensorFingerprints.Kind.TILE_TRACKER,
                confidence = SensorFingerprints.Confidence.MED,
                title = "Tile-class tracker",
                address = row.address,
                rssi = row.rssi,
                transport = "BLE",
                signals = listOf(row.manufacturer, name),
                detail = "Passive tracker advertisement",
                networkHints = emptyList()
            )
        }

        // --- Cheap BLE cameras ---
        if (SensorFingerprints.SPY_BLE_NAMES.any { it.containsMatchIn(name) }) {
            out += SensorHit(
                kind = SensorFingerprints.Kind.SPY_CAM_BLE,
                confidence = SensorFingerprints.Confidence.MED,
                title = "Cheap BLE camera / IoT cam name",
                address = row.address,
                rssi = row.rssi,
                transport = "BLE",
                signals = listOf("name match: $name", row.manufacturer),
                detail = "Temu/Amazon spy-cam class often uses generic BLE names for app pairing.",
                networkHints = listOf(
                    "Often pairs to phone app then joins your Wi‑Fi",
                    "Check LAN hunt for open 554/80 once on same SSID"
                )
            )
        }

        return out
    }

    private fun classifyWifi(row: WifiApRow): List<SensorHit> {
        val out = mutableListOf<SensorHit>()
        val ssid = row.ssid

        if (SensorFingerprints.WIFI_SSID_PATTERNS.any { it.containsMatchIn(ssid) }) {
            out += SensorHit(
                kind = SensorFingerprints.Kind.FLOCK_RELATED,
                confidence = SensorFingerprints.Confidence.MED,
                title = "Flock-like Wi‑Fi SSID",
                address = row.bssid,
                rssi = row.rssi,
                transport = "WIFI",
                signals = listOf("SSID=$ssid", "ch${row.channel}", row.security),
                detail = buildString {
                    appendLine("Provisioning / hotspot style SSID associated with Flock gear in public research.")
                    appendLine("bssid=${row.bssid}  ${row.rssi}dBm  ${row.freq}MHz")
                    appendLine("If this is a setup AP, joining may be possible on your device — only if authorized.")
                }.trimEnd(),
                networkHints = listOf(
                    "Setup AP often 192.168.4.x class on embedded gear",
                    "Production LPR usually not open Wi‑Fi — cellular backhaul"
                )
            )
        }

        if (SensorFingerprints.SPY_WIFI_SSID.any { it.containsMatchIn(ssid) }) {
            out += SensorHit(
                kind = SensorFingerprints.Kind.SPY_CAM_WIFI,
                confidence = SensorFingerprints.Confidence.MED,
                title = "Cheap Wi‑Fi camera / IoT AP",
                address = row.bssid,
                rssi = row.rssi,
                transport = "WIFI",
                signals = listOf("SSID=$ssid", row.security, "ch${row.channel}"),
                detail = "SSID pattern common on Temu/Ali A9/IPC/V380-class cams and Tuya gear.",
                networkHints = listOf(
                    "Open/WPA2 cams may host web UI on 80/8080 after you join",
                    "RTSP often :554 — only probe networks you own"
                )
            )
        }

        // CYD softAP hints
        if (ssid.contains("hale", true) || ssid.contains("cyd", true) ||
            ssid.contains("esp", true) && ssid.contains("32", true)
        ) {
            out += SensorHit(
                kind = SensorFingerprints.Kind.CYD_SOFTAP,
                confidence = SensorFingerprints.Confidence.LOW,
                title = "Possible ESP/CYD softAP",
                address = row.bssid,
                rssi = row.rssi,
                transport = "WIFI",
                signals = listOf("SSID=$ssid"),
                detail = "May be a field CYD — use CYD tab CONNECT NEAREST after joining AP.",
                networkHints = listOf("Typical softAP gateway http://192.168.4.1")
            )
        }

        return out
    }

    private fun classifyLan(hit: LanHostHit): List<SensorHit> {
        val signals = hit.openPorts.map { "tcp/$it" } + hit.notes
        val conf = when {
            554 in hit.openPorts || 8554 in hit.openPorts -> SensorFingerprints.Confidence.HIGH
            hit.openPorts.size >= 2 -> SensorFingerprints.Confidence.MED
            else -> SensorFingerprints.Confidence.LOW
        }
        return listOf(
            SensorHit(
                kind = SensorFingerprints.Kind.SPY_CAM_WIFI,
                confidence = conf,
                title = "LAN host with camera-like ports",
                address = hit.ip,
                rssi = null,
                transport = "LAN",
                signals = signals,
                detail = buildString {
                    appendLine("ip=${hit.ip}")
                    appendLine("ports=${hit.openPorts}")
                    appendLine("http_title=${hit.httpTitle ?: "—"}")
                    appendLine("server=${hit.httpServer ?: "—"}")
                    appendLine("This is the on-LAN peer IP — data *to* cloud needs outbound capture (router logs).")
                }.trimEnd(),
                networkHints = listOf(
                    "Peer IP on your subnet: ${hit.ip}",
                    "Outbound C2: check router DHCP name + firewall/connection log for this host",
                    "Common cam vendors phone home to cloud regions — varies by OEM"
                )
            )
        )
    }
}

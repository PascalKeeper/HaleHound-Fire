package com.halehoundforge.fire.hardening

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * On-device network hardening auditor for Fire OS.
 * Probes gateway + self, scores posture, never requires a laptop.
 */
class HardeningEngine(private val context: Context) {

    data class PortHit(
        val host: String,
        val port: Int,
        val open: Boolean,
        val name: String,
        val risk: HardeningKnowledge.Risk,
        val latencyMs: Long
    )

    data class AuditResult(
        val score: Int,
        val grade: String,
        val wifiLine: String,
        val dnsLine: String,
        val gateway: String,
        val localIp: String,
        val portHits: List<PortHit>,
        val findings: List<String>,
        val checklistStatus: List<Pair<HardeningKnowledge.ChecklistItem, String>>,
        val optimTips: List<String>,
        val reportText: String
    )

    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun runFullAudit(
        scanLanHost: String? = null,
        extraHosts: List<String> = emptyList()
    ): AuditResult = withContext(Dispatchers.IO) {
        val gw = resolveGateway() ?: "—"
        val local = localIpv4() ?: "—"
        val wifiInfo = wifiPosture()
        val dns = dnsServers()

        val targets = linkedSetOf<String>()
        if (gw != "—") targets += gw
        if (local != "—") targets += local
        scanLanHost?.let { if (it.isNotBlank()) targets += it.trim() }
        extraHosts.forEach { if (it.isNotBlank()) targets += it.trim() }

        val ports = HardeningKnowledge.dangerPorts.map { it.port }.distinct()
        val hits = ConcurrentLinkedQueue<PortHit>()

        coroutineScope {
            targets.flatMap { host ->
                ports.map { port ->
                    async {
                        val meta = HardeningKnowledge.portByNumber(port)
                        val start = System.currentTimeMillis()
                        val open = probe(host, port, 350)
                        val ms = System.currentTimeMillis() - start
                        hits += PortHit(
                            host = host,
                            port = port,
                            open = open,
                            name = meta?.name ?: "port/$port",
                            risk = meta?.risk ?: HardeningKnowledge.Risk.MEDIUM,
                            latencyMs = ms
                        )
                    }
                }
            }.awaitAll()
        }

        val hitList = hits.toList().sortedWith(
            compareBy<PortHit> { !it.open }
                .thenByDescending { riskRank(it.risk) }
                .thenBy { it.host }
                .thenBy { it.port }
        )

        val findings = mutableListOf<String>()
        var score = 100

        // Wi‑Fi encryption
        val enc = wifiInfo.security
        when {
            enc.contains("OPEN") || enc.contains("NONE") -> {
                findings += "CRITICAL: Wi‑Fi appears OPEN — anyone can join"
                score -= 40
            }
            enc.contains("WEP") -> {
                findings += "CRITICAL: WEP is broken — upgrade AP encryption"
                score -= 35
            }
            enc.contains("WPA3") -> findings += "OK: WPA3 detected (best practical)"
            enc.contains("WPA2") -> {
                findings += "OK: WPA2 — enable PMF/802.11w on router if available"
                score -= 5
            }
            else -> {
                findings += "WATCH: Could not classify Wi‑Fi security ($enc)"
                score -= 10
            }
        }

        if (wifiInfo.rssi in -100..-80) {
            findings += "WATCH: Weak RSSI ${wifiInfo.rssi} dBm — instability / evil-twin risk higher"
            score -= 5
        }

        // Dangerous open ports on gateway (WAN-facing CPE often has admin UIs)
        val openDanger = hitList.filter {
            it.open && it.risk in setOf(
                HardeningKnowledge.Risk.CRITICAL,
                HardeningKnowledge.Risk.HIGH
            )
        }
        openDanger.forEach {
            findings += "${it.risk}: ${it.host}:${it.port} ${it.name} OPEN (${it.latencyMs}ms)"
            score -= when (it.risk) {
                HardeningKnowledge.Risk.CRITICAL -> 15
                HardeningKnowledge.Risk.HIGH -> 10
                else -> 5
            }
        }

        // SMB/RDP specifically
        if (hitList.any { it.open && it.port == 445 }) {
            findings += "CRITICAL: SMB 445 open (WannaCry-class risk if routable)"
        }
        if (hitList.any { it.open && it.port == 3389 }) {
            findings += "CRITICAL: RDP 3389 open — #1 ransomware vector if exposed"
        }
        if (hitList.any { it.open && it.port == 23 }) {
            findings += "HIGH: Telnet 23 open — cleartext remote access"
        }
        if (hitList.any { it.open && it.port == 5555 && it.host == local }) {
            findings += "HIGH: ADB 5555 open on THIS device — prefer USB debugging only"
            score -= 10
        }

        // DNS
        val dnsLine = if (dns.isEmpty()) {
            findings += "INFO: DNS servers not exposed by OS API — set Private DNS manually"
            score -= 2
            "DNS: (unavailable via API on this Fire build)"
        } else {
            val trusted = HardeningKnowledge.dnsProfiles.flatMap { listOf(it.primary, it.secondary) }
            val usingTrusted = dns.any { it in trusted }
            if (!usingTrusted) {
                findings += "MEDIUM: DNS $dns — consider Cloudflare 1.1.1.1 or Quad9 (ultimate_net_optimizer)"
                score -= 5
            } else {
                findings += "OK: Using known public resolver $dns"
            }
            "DNS: ${dns.joinToString(", ")}"
        }

        score = score.coerceIn(0, 100)
        val grade = when {
            score >= 90 -> "A — tight"
            score >= 75 -> "B — solid"
            score >= 60 -> "C — fix highs"
            score >= 40 -> "D — exposed"
            else -> "F — harden NOW"
        }

        val checklistStatus = HardeningKnowledge.checklist.map { item ->
            val status = when (item.id) {
                "wifi_encryption" -> when {
                    enc.contains("WPA3") || enc.contains("WPA2") -> "PASS (on-device sense)"
                    enc.contains("OPEN") || enc.contains("WEP") -> "FAIL"
                    else -> "CHECK MANUALLY"
                }
                "private_dns" -> if (dns.any { it in listOf("1.1.1.1", "1.0.0.1", "9.9.9.9", "1.1.1.2") })
                    "PASS" else "SET ON DEVICE"
                "block_smb_wan" -> if (hitList.any { it.open && it.port == 445 && it.host == gw })
                    "FAIL gateway:445 open" else "OK on scanned hosts / verify WAN"
                "block_rdp_wan" -> if (hitList.any { it.open && it.port == 3389 })
                    "FAIL open on LAN host" else "OK on scanned hosts"
                "no_telnet_ftp" -> if (hitList.any { it.open && it.port in setOf(21, 23) })
                    "FAIL legacy open" else "OK on scanned hosts"
                "adb_wifi" -> if (hitList.any { it.open && it.port == 5555 && it.host == local })
                    "FAIL adb port open" else "OK / check developer options"
                "guardian_on" -> "RUN GUARD TAB"
                "pmf", "wps_off", "router_admin", "guest_wifi", "upnp_off", "admin_https", "firmware", "ssid_lock" ->
                    "OPERATOR CHECKLIST"
                else -> "REVIEW"
            }
            item to status
        }

        val report = buildString {
            appendLine("HALEHOUND-FIRE HARDEN REPORT")
            appendLine("score=$score  grade=$grade")
            appendLine("wifi=${wifiInfo.ssid} sec=$enc rssi=${wifiInfo.rssi}")
            appendLine("ip=$local  gw=$gw")
            appendLine(dnsLine)
            appendLine("--- FINDINGS ---")
            findings.forEach { appendLine("• $it") }
            appendLine("--- OPEN PORTS ---")
            hitList.filter { it.open }.forEach {
                appendLine("${it.host}:${it.port} ${it.name} [${it.risk}] ${it.latencyMs}ms")
            }
            if (hitList.none { it.open }) appendLine("(none of the danger set responded)")
            appendLine("--- SOURCES ---")
            appendLine("Secure Firewall Ports.bat · secure_gaming_firewall.ps1")
            appendLine("ultimate_net_optimizer.bat · HardenServices.ps1")
            appendLine("PERSEUS SecurityAutomation · SecuritySentinel_Pro.ps1")
            appendLine("WifiGuard · NetworkMonitor_v2 mitigations")
        }

        AuditResult(
            score = score,
            grade = grade,
            wifiLine = "${wifiInfo.ssid} · $enc · ${wifiInfo.rssi}dBm · ch~${wifiInfo.freq}",
            dnsLine = dnsLine,
            gateway = gw,
            localIp = local,
            portHits = hitList,
            findings = findings,
            checklistStatus = checklistStatus,
            optimTips = HardeningKnowledge.optimNotes,
            reportText = report
        )
    }

    private data class WifiPosture(val ssid: String, val security: String, val rssi: Int, val freq: Int)

    @Suppress("DEPRECATION")
    private fun wifiPosture(): WifiPosture {
        val info: WifiInfo? = wifi.connectionInfo
        val ssid = info?.ssid?.trim('"')?.takeIf { it != "<unknown ssid>" } ?: "—"
        val rssi = info?.rssi ?: -127
        val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info?.frequency ?: 0 else 0
        // Scan results for capabilities of current BSSID
        var sec = "UNKNOWN"
        try {
            val bssid = info?.bssid
            @SuppressLint("MissingPermission")
            val match = wifi.scanResults?.firstOrNull { it.BSSID.equals(bssid, true) }
            val caps = match?.capabilities ?: ""
            sec = when {
                caps.contains("WEP") -> "WEP"
                caps.contains("WPA3") -> "WPA3"
                caps.contains("WPA2") -> "WPA2"
                caps.contains("WPA") -> "WPA"
                caps.contains("ESS") && !caps.contains("WPA") && !caps.contains("WEP") -> "OPEN/NONE"
                caps.isBlank() -> "UNKNOWN"
                else -> caps.take(32)
            }
        } catch (_: Exception) {
        }
        return WifiPosture(ssid, sec, rssi, freq)
    }

    private fun probe(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveGateway(): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return null
                val lp: LinkProperties = cm.getLinkProperties(net) ?: return null
                val gw = lp.routes.firstOrNull { it.isDefaultRoute }?.gateway
                if (gw is Inet4Address) return gw.hostAddress
                if (gw != null) return gw.hostAddress
            }
        } catch (_: Exception) {
        }
        @Suppress("DEPRECATION")
        val dhcp = wifi.dhcpInfo
        if (dhcp != null && dhcp.gateway != 0) {
            val g = dhcp.gateway
            return String.format(
                "%d.%d.%d.%d",
                g and 0xff, g shr 8 and 0xff, g shr 16 and 0xff, g shr 24 and 0xff
            )
        }
        return null
    }

    private fun localIpv4(): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return null
                val lp = cm.getLinkProperties(net) ?: return null
                val addr = lp.linkAddresses.mapNotNull { it.address }
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                return addr?.hostAddress
            }
        } catch (_: Exception) {
        }
        @Suppress("DEPRECATION")
        val ip = wifi.connectionInfo?.ipAddress ?: 0
        if (ip == 0) return null
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
        )
    }

    private fun dnsServers(): List<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return emptyList()
                val lp = cm.getLinkProperties(net) ?: return emptyList()
                lp.dnsServers.mapNotNull { it.hostAddress }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun riskRank(r: HardeningKnowledge.Risk): Int = when (r) {
        HardeningKnowledge.Risk.CRITICAL -> 5
        HardeningKnowledge.Risk.HIGH -> 4
        HardeningKnowledge.Risk.MEDIUM -> 3
        HardeningKnowledge.Risk.LOW -> 2
        HardeningKnowledge.Risk.INFO -> 1
    }
}

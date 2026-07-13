package com.halehoundforge.fire.guardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * Fully on-device Blue Team Wi‑Fi Guardian for stock Fire OS.
 *
 * NO laptop, NO Npcap, NO raw 802.11. Everything uses Android public APIs only.
 *
 * Stock Fire OS cannot capture Dot11Deauth management frames (no monitor mode).
 * We approximate deauth/jam *pressure* via multi-signal fusion:
 *  - Supplicant disconnect / inactive storms
 *  - ConnectivityManager network lost
 *  - RSSI cliffs (sudden dBm drop)
 *  - Link-speed collapse
 *  - Gateway latency / jitter / timeouts
 *  - BSSID churn (forced roam / AP thrash)
 *  - Preferred-SSID lock (WiFiGuardianKiller-style)
 *
 * True frame-level Guardian still requires a HaleHound CYD (or other monitor radio).
 */
class NetworkGuardianEngine(private val context: Context) {

    data class Snapshot(
        val ssid: String,
        val bssid: String,
        val rssi: Int,
        val linkMbps: Int,
        val frequencyMhz: Int,
        val ip: String,
        val gateway: String,
        val connected: Boolean,
        val supplicant: String,
        val txMbps: Double,
        val rxMbps: Double,
        val latencyMs: Int,
        val jitterMs: Double,
        val packetDrops: Int,
        val disconnectEvents: Int,
        val stormScore: Int,
        val predictiveStatus: String,
        val predictiveLevel: Level,
        val mitigation: String,
        val alerts: List<String>,
        val preferredSsid: String?,
        val ssidLockAlert: Boolean,
        val modeLabel: String,
        val signals: String
    )

    enum class Level { STABLE, WATCH, HIGH, CRITICAL }

    interface Listener {
        fun onSnapshot(snapshot: Snapshot)
    }

    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val alerts = ArrayDeque<String>(40)
    private val latencyHistory = ArrayDeque<Int>(30)
    private val jitterHistory = ArrayDeque<Int>(10)
    private val disconnectTimestamps = ArrayDeque<Long>(50)
    private val rssiHistory = ArrayDeque<Pair<Long, Int>>(40)

    private var job: Job? = null
    private var lastLatency = -1
    private var packetDrops = 0
    private var disconnectEvents = 0
    private var rssiCliffEvents = 0
    private var bssidChurnEvents = 0
    private var linkCollapseEvents = 0
    private var lastTxBytes = -1L
    private var lastRxBytes = -1L
    private var lastBwTime = 0L
    private var txMbps = 0.0
    private var rxMbps = 0.0
    private var preferredSsid: String? = null
    private var running = false
    private var lastBssid: String? = null
    private var lastLinkMbps: Int = -1
    private var lastRssiSample: Int = -127

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                WifiManager.SUPPLICANT_STATE_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val info = wifi.connectionInfo
                    val state = info?.supplicantState
                    if (state == SupplicantState.DISCONNECTED ||
                        state == SupplicantState.INACTIVE ||
                        state == SupplicantState.INTERFACE_DISABLED ||
                        state == SupplicantState.SCANNING
                    ) {
                        // scanning alone is soft; only hard-fail states count heavy
                        if (state != SupplicantState.SCANNING) {
                            onDisconnectHeuristic("supplicant=$state")
                        }
                    }
                    if (state == SupplicantState.FOUR_WAY_HANDSHAKE ||
                        state == SupplicantState.GROUP_HANDSHAKE ||
                        state == SupplicantState.ASSOCIATING ||
                        state == SupplicantState.AUTHENTICATING
                    ) {
                        // reauth churn can indicate deauth-then-reconnect
                        pushAlert("REAUTH churn: $state (possible deauth recovery)")
                    }
                }
                WifiManager.RSSI_CHANGED_ACTION -> {
                    // polled; receiver just keeps us warm
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            onDisconnectHeuristic("network_lost")
        }

        override fun onUnavailable() {
            onDisconnectHeuristic("network_unavailable")
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                pushAlert("Path unvalidated — captive/portal or broken uplink")
            }
        }
    }

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    fun setPreferredSsid(ssid: String?) {
        preferredSsid = ssid?.trim()?.ifEmpty { null }
        // encrypted at rest
        try {
            com.halehoundforge.fire.privacy.SecureStore.putString(
                context,
                "preferred_ssid",
                preferredSsid ?: ""
            )
        } catch (_: Exception) {
        }
    }

    fun getPreferredSsid(): String? {
        if (preferredSsid != null) return preferredSsid
        return try {
            val s = com.halehoundforge.fire.privacy.SecureStore.getString(context, "preferred_ssid", "")
            preferredSsid = s.ifBlank { null }
            preferredSsid
        } catch (_: Exception) {
            preferredSsid
        }
    }

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true

        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
        }
        context.registerReceiver(wifiReceiver, filter)

        try {
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(req, networkCallback)
        } catch (_: Exception) {
        }

        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastBwTime = SystemClock.elapsedRealtime()

        pushAlert("LOCAL Guardian online — fully on-device (no laptop)")

        job = scope.launch(Dispatchers.Default) {
            while (isActive && running) {
                sampleBandwidth()
                sampleRadioDynamics()
                val lat = probeGatewayLatency()
                updateLatencyStats(lat)
                val snap = buildSnapshot()
                listeners.forEach { it.onSnapshot(snap) }
                delay(1000L)
            }
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
        try {
            context.unregisterReceiver(wifiReceiver)
        } catch (_: Exception) {
        }
        try {
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    private fun nowStamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun pushAlert(msg: String) {
        val line = "[${nowStamp()}] $msg"
        synchronized(alerts) {
            // de-dupe identical consecutive lines
            if (alerts.lastOrNull() == line) return
            if (alerts.size >= 36) alerts.removeFirst()
            alerts.addLast(line)
        }
    }

    private fun onDisconnectHeuristic(reason: String) {
        val now = SystemClock.elapsedRealtime()
        disconnectEvents++
        disconnectTimestamps.addLast(now)
        while (disconnectTimestamps.isNotEmpty() && now - disconnectTimestamps.first() > 60_000L) {
            disconnectTimestamps.removeFirst()
        }
        val recent = disconnectTimestamps.count { now - it <= 30_000L }
        if (recent >= 2) {
            pushAlert("DISCONNECT STORM x$recent /30s ($reason) — deauth/jam pressure?")
        } else {
            pushAlert("Link drop: $reason")
        }
    }

    @Suppress("DEPRECATION")
    private fun sampleRadioDynamics() {
        val info = wifi.connectionInfo ?: return
        val rssi = info.rssi
        val bssid = info.bssid
        val link = info.linkSpeed
        val now = SystemClock.elapsedRealtime()

        rssiHistory.addLast(now to rssi)
        while (rssiHistory.isNotEmpty() && now - rssiHistory.first().first > 20_000L) {
            rssiHistory.removeFirst()
        }

        // RSSI cliff: ≥18 dB drop within 8s while still associated
        if (lastRssiSample > -120 && rssi > -120) {
            val drop = lastRssiSample - rssi
            if (drop >= 18 && info.networkId != -1) {
                rssiCliffEvents++
                pushAlert("RSSI CLIFF ${lastRssiSample}→${rssi} dBm (Δ$drop) — interference/deauth?")
            }
        }
        lastRssiSample = rssi

        // BSSID churn while same SSID
        if (bssid != null && bssid != "00:00:00:00:00:00" && lastBssid != null &&
            lastBssid != bssid && info.networkId != -1
        ) {
            bssidChurnEvents++
            pushAlert("BSSID CHURN $lastBssid → $bssid (forced roam / thrash?)")
        }
        if (bssid != null) lastBssid = bssid

        // Link speed collapse
        if (lastLinkMbps > 0 && link > 0 && lastLinkMbps >= 72 && link <= 12) {
            linkCollapseEvents++
            pushAlert("LINK COLLAPSE ${lastLinkMbps}→${link} Mbps")
        }
        if (link > 0) lastLinkMbps = link
    }

    private fun sampleBandwidth() {
        val now = SystemClock.elapsedRealtime()
        val tx = TrafficStats.getTotalTxBytes()
        val rx = TrafficStats.getTotalRxBytes()
        if (tx < 0 || rx < 0 || lastTxBytes < 0 || lastRxBytes < 0) return
        val dt = (now - lastBwTime).coerceAtLeast(1L) / 1000.0
        txMbps = ((tx - lastTxBytes) * 8.0 / 1_000_000.0) / dt
        rxMbps = ((rx - lastRxBytes) * 8.0 / 1_000_000.0) / dt
        if (txMbps < 0) txMbps = 0.0
        if (rxMbps < 0) rxMbps = 0.0
        lastTxBytes = tx
        lastRxBytes = rx
        lastBwTime = now
    }

    private fun probeGatewayLatency(): Int {
        val gw = resolveGateway() ?: return -1
        return try {
            val start = SystemClock.elapsedRealtime()
            Socket().use { sock -> sock.connect(InetSocketAddress(gw, 80), 800) }
            (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            try {
                val start = SystemClock.elapsedRealtime()
                Socket().use { sock -> sock.connect(InetSocketAddress(gw, 53), 800) }
                (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1)
            } catch (_: Exception) {
                -1
            }
        }
    }

    private fun updateLatencyStats(lat: Int) {
        if (lat < 0) {
            packetDrops++
            latencyHistory.addLast(9999)
            if (latencyHistory.size > 30) latencyHistory.removeFirst()
            lastLatency = -1
            return
        }
        if (lastLatency > 0) {
            val j = abs(lat - lastLatency)
            jitterHistory.addLast(j)
            if (jitterHistory.size > 10) jitterHistory.removeFirst()
        }
        lastLatency = lat
        latencyHistory.addLast(lat)
        if (latencyHistory.size > 30) latencyHistory.removeFirst()
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
                g and 0xff,
                g shr 8 and 0xff,
                g shr 16 and 0xff,
                g shr 24 and 0xff
            )
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun buildSnapshot(): Snapshot {
        val info = wifi.connectionInfo
        val connected = info != null && info.networkId != -1 &&
            info.supplicantState == SupplicantState.COMPLETED
        val ssid = info?.ssid?.trim('"')?.takeIf { it != "<unknown ssid>" } ?: "—"
        val bssid = info?.bssid ?: "—"
        val rssi = info?.rssi ?: -127
        val link = info?.linkSpeed ?: 0
        val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info?.frequency ?: 0 else 0
        val supp = info?.supplicantState?.name ?: "UNKNOWN"
        val ip = formatIp(info?.ipAddress ?: 0)
        val gw = resolveGateway() ?: "—"

        val now = SystemClock.elapsedRealtime()
        val stormWindow = disconnectTimestamps.count { now - it <= 30_000L }

        // Multi-signal storm score (0-100) — fully local
        var score = 0
        score += (stormWindow * 22).coerceAtMost(55)
        score += (rssiCliffEvents.coerceAtMost(3) * 8)
        score += (bssidChurnEvents.coerceAtMost(3) * 6)
        score += (linkCollapseEvents.coerceAtMost(2) * 5)
        if (rssi in -100..-82) score += 10
        val recentLat = latencyHistory.toList().takeLast(5)
        val timeouts = recentLat.count { it == 9999 }
        score += timeouts * 10
        score = score.coerceAtMost(100)

        val avgJitter = if (jitterHistory.isEmpty()) 0.0 else jitterHistory.average()
        val avgLat = recentLat.filter { it != 9999 }.let { if (it.isEmpty()) 0.0 else it.average() }

        val (status, level) = when {
            !connected || timeouts >= 3 || score >= 80 ->
                "CRITICAL: link failure / high loss (local sensors)" to Level.CRITICAL
            stormWindow >= 3 || score >= 55 ->
                "HIGH: disconnect/RSSI storm — possible deauth pressure" to Level.HIGH
            timeouts > 0 || avgJitter > 120 || score >= 30 ->
                "WATCH: degradation (jitter/latency/radio)" to Level.WATCH
            rssi in -100..-82 ->
                "WATCH: weak RSSI (${rssi}dBm)" to Level.WATCH
            else ->
                "STABLE: local sensors nominal" to Level.STABLE
        }

        val mitigation = when (level) {
            Level.CRITICAL, Level.HIGH ->
                "LOCAL MITIGATION: move closer to AP · enable PMF/802.11w · prefer 5GHz · " +
                    "SSID lock + Reconnect. True Dot11 deauth frames need a CYD — not possible on stock Fire OS."
            Level.WATCH ->
                "Advice: check congestion/walls · note channel · keep Guardian open to log storms."
            Level.STABLE ->
                "Fully local Guardian active. No laptop required. Monitor runs on this tablet only."
        }

        val pref = preferredSsid
        val lockAlert = pref != null && connected && ssid != pref && ssid != "—"
        if (lockAlert) {
            val last = synchronized(alerts) { alerts.lastOrNull() }
            if (last == null || !last.contains("SSID LOCK")) {
                pushAlert("SSID LOCK: on '$ssid' (want '$pref')")
            }
        }

        val alertList = synchronized(alerts) { alerts.toList().asReversed() }

        val signals = buildString {
            append("drops=$disconnectEvents cliffs=$rssiCliffEvents ")
            append("churn=$bssidChurnEvents linkCol=$linkCollapseEvents ")
            append("score=$score")
        }

        return Snapshot(
            ssid = ssid,
            bssid = bssid,
            rssi = rssi,
            linkMbps = link,
            frequencyMhz = freq,
            ip = ip,
            gateway = gw,
            connected = connected,
            supplicant = supp,
            txMbps = txMbps,
            rxMbps = rxMbps,
            latencyMs = if (lastLatency < 0) -1 else lastLatency,
            jitterMs = avgJitter,
            packetDrops = packetDrops,
            disconnectEvents = disconnectEvents,
            stormScore = score,
            predictiveStatus = status,
            predictiveLevel = level,
            mitigation = mitigation,
            alerts = alertList,
            preferredSsid = pref,
            ssidLockAlert = lockAlert,
            modeLabel = "FULLY LOCAL · ON-DEVICE",
            signals = signals
        )
    }

    private fun formatIp(ip: Int): String {
        if (ip == 0) return "—"
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }

    @Suppress("DEPRECATION")
    fun attemptReconnectToPreferred(): String {
        val target = preferredSsid ?: return "No preferred SSID set"
        return try {
            val configs = wifi.configuredNetworks
            val match = configs?.firstOrNull { it.SSID?.trim('"') == target }
            if (match != null) {
                wifi.disconnect()
                wifi.enableNetwork(match.networkId, true)
                wifi.reconnect()
                "Reconnect requested → $target"
            } else {
                "Preferred SSID '$target' not in saved networks. Connect once in system Settings."
            }
        } catch (e: SecurityException) {
            "Blocked by OS: ${e.message}"
        } catch (e: Exception) {
            "Reconnect failed: ${e.message}"
        }
    }
}

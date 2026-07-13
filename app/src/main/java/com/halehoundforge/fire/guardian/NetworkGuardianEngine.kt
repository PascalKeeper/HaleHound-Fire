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
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * Blue Team Network Guardian engine for stock Fire OS.
 *
 * Ported concepts from:
 * - F:\imonlinegaming\NetworkMonitor_v2 (bandwidth, predictive latency/jitter, deauth radar UI)
 * - Documents\WiFiGuardianKiller.ps1 + WifiGuard.ps1 (disconnect storm / SSID lock heuristics)
 * - HaleHound CYD "WiFi Guardian" (defensive deauth flood awareness — approximated on Fire)
 *
 * Stock Fire OS CANNOT sniff Dot11Deauth frames (no monitor mode). We approximate
 * jamming/deauth pressure via rapid Supplicant disconnects + latency collapse.
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
        val ssidLockAlert: Boolean
    )

    enum class Level { STABLE, WATCH, HIGH, CRITICAL }

    interface Listener {
        fun onSnapshot(snapshot: Snapshot)
    }

    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val alerts = ArrayDeque<String>(20)
    private val latencyHistory = ArrayDeque<Int>(30)
    private val jitterHistory = ArrayDeque<Int>(10)
    private val disconnectTimestamps = ArrayDeque<Long>(50)

    private var job: Job? = null
    private var lastLatency = -1
    private var packetDrops = 0
    private var disconnectEvents = 0
    private var lastTxBytes = -1L
    private var lastRxBytes = -1L
    private var lastBwTime = 0L
    private var txMbps = 0.0
    private var rxMbps = 0.0
    private var preferredSsid: String? = null
    private var running = false

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                WifiManager.SUPPLICANT_STATE_CHANGED_ACTION -> {
                    val info = wifi.connectionInfo
                    val state = info?.supplicantState
                    if (state == SupplicantState.DISCONNECTED ||
                        state == SupplicantState.INACTIVE ||
                        state == SupplicantState.INTERFACE_DISABLED
                    ) {
                        onDisconnectHeuristic("supplicant=$state")
                    }
                }
                WifiManager.RSSI_CHANGED_ACTION -> {
                    // handled in poll loop
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            onDisconnectHeuristic("network_lost")
        }
    }

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    fun setPreferredSsid(ssid: String?) {
        preferredSsid = ssid?.trim()?.ifEmpty { null }
    }

    fun getPreferredSsid(): String? = preferredSsid

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

        // Seed bandwidth counters
        val uid = android.os.Process.myUid()
        lastTxBytes = TrafficStats.getTotalTxBytes().takeIf { it >= 0 }
            ?: TrafficStats.getUidTxBytes(uid)
        lastRxBytes = TrafficStats.getTotalRxBytes().takeIf { it >= 0 }
            ?: TrafficStats.getUidRxBytes(uid)
        lastBwTime = SystemClock.elapsedRealtime()

        job = scope.launch(Dispatchers.Default) {
            while (isActive && running) {
                sampleBandwidth()
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

    private fun onDisconnectHeuristic(reason: String) {
        val now = SystemClock.elapsedRealtime()
        disconnectEvents++
        disconnectTimestamps.addLast(now)
        while (disconnectTimestamps.isNotEmpty() && now - disconnectTimestamps.first() > 60_000L) {
            disconnectTimestamps.removeFirst()
        }
        // Rapid disconnects in 30s window ≈ storm
        val recent = disconnectTimestamps.count { now - it <= 30_000L }
        if (recent >= 2) {
            pushAlert("DISCONNECT STORM x$recent in 30s ($reason) — possible deauth/jam pressure")
        } else {
            pushAlert("Link drop: $reason")
        }
    }

    private fun pushAlert(msg: String) {
        val line = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $msg"
        synchronized(alerts) {
            if (alerts.size >= 18) alerts.removeFirst()
            alerts.addLast(line)
        }
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
            Socket().use { sock ->
                sock.connect(InetSocketAddress(gw, 80), 800)
            }
            (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            // Fallback: try common DNS over TCP
            try {
                val start = SystemClock.elapsedRealtime()
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(gw, 53), 800)
                }
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
        // WifiManager DHCP fallback
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
        val stormScore = (stormWindow * 25).coerceAtMost(100) +
            if (rssi in -100..-80) 15 else 0

        val recent = latencyHistory.toList().takeLast(5)
        val timeouts = recent.count { it == 9999 }
        val avgJitter = if (jitterHistory.isEmpty()) 0.0 else jitterHistory.average()
        val avgLat = recent.filter { it != 9999 }.let { if (it.isEmpty()) 0.0 else it.average() }

        val (status, level) = when {
            !connected || timeouts >= 3 ->
                "CRITICAL: DISCONNECTED / HIGH LOSS" to Level.CRITICAL
            stormWindow >= 3 ->
                "HIGH RISK: Disconnect storm (possible deauth/jam)" to Level.HIGH
            timeouts > 0 ->
                "HIGH RISK: Signal degradation (${timeouts * 20}% drop window)" to Level.HIGH
            avgJitter > 120 ->
                "MODERATE: Unstable jitter (${"%.0f".format(avgJitter)}ms)" to Level.WATCH
            avgLat > 250 ->
                "WARNING: Latency spikes (${"%.0f".format(avgLat)}ms)" to Level.WATCH
            rssi in -100..-82 ->
                "WATCH: Weak RSSI (${rssi}dBm)" to Level.WATCH
            else ->
                "STABLE: Link within nominal tolerances" to Level.STABLE
        }

        val mitigation = when (level) {
            Level.CRITICAL, Level.HIGH ->
                "MITIGATION: Move closer to AP · enable PMF/802.11w if router supports · prefer 5GHz · " +
                    "if attacks persist, CYD WiFi Guardian can capture real deauth frames."
            Level.WATCH ->
                "Advice: Check congestion / interference · reduce wall obstruction · note AP channel."
            Level.STABLE ->
                "System advice: Operating on secure-looking path. Monitor active (heuristic)."
        }

        val pref = preferredSsid
        val lockAlert = pref != null && connected && ssid != pref && ssid != "—"
        if (lockAlert) {
            // avoid spam: only if last alert not same
            val last = synchronized(alerts) { alerts.lastOrNull() }
            if (last == null || !last.contains("SSID LOCK")) {
                pushAlert("SSID LOCK: connected to '$ssid' (preferred='$pref')")
            }
        }

        val alertList = synchronized(alerts) { alerts.toList().asReversed() }

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
            stormScore = stormScore.coerceAtMost(100),
            predictiveStatus = status,
            predictiveLevel = level,
            mitigation = mitigation,
            alerts = alertList,
            preferredSsid = pref,
            ssidLockAlert = lockAlert
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

    /**
     * Best-effort reconnect (WiFiGuardianKiller-style). Stock Android may ignore
     * depending on OEM policy; Fire OS often allows reconnect to saved networks.
     */
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

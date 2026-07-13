package com.halehoundforge.fire.sensor

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiManager
import android.os.Build
import com.halehoundforge.fire.perf.LatencyProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset

data class LanHostHit(
    val ip: String,
    val openPorts: List<Int>,
    val httpTitle: String?,
    val httpServer: String?,
    val notes: List<String>
)

/**
 * Best-effort LAN inventory for spy-cam style hosts on *your* Wi‑Fi.
 * Cannot see cellular backhaul of municipal Flock units off-LAN.
 */
object LanProbe {

    fun localIpv4(context: Context): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val net = cm.activeNetwork ?: return null
                val lp: LinkProperties = cm.getLinkProperties(net) ?: return null
                val v4 = lp.linkAddresses.mapNotNull { it.address as? Inet4Address }
                    .firstOrNull { !it.isLoopbackAddress }
                if (v4 != null) return v4.hostAddress
            }
        } catch (_: Exception) {
        }
        @Suppress("DEPRECATION")
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifi.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
        )
    }

    fun subnetPrefix(ip: String): String? {
        val p = ip.split(".")
        if (p.size != 4) return null
        return "${p[0]}.${p[1]}.${p[2]}"
    }

    /**
     * Probe a slice of the /24 for common camera ports.
     * [maxHosts] keeps Fire responsive (default last octet 1–40 + gateway).
     */
    suspend fun scanCameraLikely(
        context: Context,
        maxHosts: Int = 32
    ): List<LanHostHit> = withContext(Dispatchers.IO) {
        val me = localIpv4(context) ?: return@withContext emptyList()
        val prefix = subnetPrefix(me) ?: return@withContext emptyList()
        val targets = (1..254).map { "$prefix.$it" }
            .filter { it != me }
            .take(maxHosts)

        coroutineScope {
            targets.map { host ->
                async {
                    val open = SensorFingerprints.SPY_CAM_PORTS.mapNotNull { port ->
                        if (tcpOpen(host, port, LatencyProfiles.active.portProbeTimeoutMs.coerceAtMost(250))) port
                        else null
                    }
                    if (open.isEmpty()) return@async null
                    val title = if (80 in open || 8080 in open || 8000 in open) {
                        httpPeek(host, open.first { it in listOf(80, 8080, 8000, 81) })
                    } else null
                    val notes = mutableListOf<String>()
                    if (554 in open || 8554 in open) notes += "RTSP-ish port open (common IP cam stream)"
                    if (34567 in open || 37777 in open) notes += "DVR/NVR-class port"
                    val blob = "${title?.first} ${title?.second}".lowercase()
                    if (SensorFingerprints.FLOCK_CLOUD_HINTS.any { blob.contains(it) }) {
                        notes += "HTTP banner mentions Flock-related string"
                    }
                    if (open.size >= 2) notes += "multi-port IoT footprint"
                    LanHostHit(
                        ip = host,
                        openPorts = open,
                        httpTitle = title?.first,
                        httpServer = title?.second,
                        notes = notes
                    )
                }
            }.awaitAll().filterNotNull().sortedBy { it.ip }
        }
    }

    private fun tcpOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), timeoutMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun httpPeek(host: String, port: Int): Pair<String?, String?>? {
        return try {
            val p = LatencyProfiles.active
            val conn = (URL("http://$host:$port/").openConnection() as HttpURLConnection).apply {
                connectTimeout = p.httpConnectMs
                readTimeout = p.httpReadMs
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val server = conn.getHeaderField("Server")
            val body = try {
                BufferedReader(InputStreamReader(conn.inputStream, Charset.forName("UTF-8")))
                    .use { it.readText() }
            } catch (_: Exception) {
                ""
            }
            conn.disconnect()
            val title = Regex("""(?is)<title[^>]*>(.*?)</title>""").find(body)
                ?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim()?.take(80)
            Pair(title ?: "HTTP $code", server)
        } catch (_: Exception) {
            null
        }
    }
}

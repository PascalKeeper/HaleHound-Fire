package com.halehoundforge.fire.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

data class CydEndpoint(
    val url: String,
    val title: String,
    val latencyMs: Long
)

/**
 * Best-effort LAN discovery of a HaleHound / ESP web UI.
 * Probes common captive / AP addresses and open HTTP on the local /24.
 */
object CydDiscovery {

    private val commonHosts = listOf(
        "192.168.4.1",
        "192.168.0.1",
        "192.168.1.1",
        "10.0.0.1"
    )

    suspend fun discover(): List<CydEndpoint> = withContext(Dispatchers.IO) {
        val hits = mutableListOf<CydEndpoint>()
        for (host in commonHosts) {
            probeHttp(host, 80)?.let { hits += it }
            probeHttp(host, 8080)?.let { hits += it }
        }
        hits
    }

    suspend fun probeUrl(raw: String): CydEndpoint? = withContext(Dispatchers.IO) {
        val normalized = if (raw.startsWith("http")) raw else "http://$raw"
        try {
            val start = System.currentTimeMillis()
            val conn = (URL(normalized).openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val title = conn.headerFields["Server"]?.firstOrNull()
                ?: "HTTP $code"
            conn.disconnect()
            if (code in 200..499) {
                CydEndpoint(normalized, title, System.currentTimeMillis() - start)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun probeHttp(host: String, port: Int): CydEndpoint? {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), 400)
            }
            val url = if (port == 80) "http://$host/" else "http://$host:$port/"
            val openMs = System.currentTimeMillis() - start
            // Optional shallow GET
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 800
                    readTimeout = 800
                    requestMethod = "GET"
                }
                val code = conn.responseCode
                val server = conn.headerFields["Server"]?.firstOrNull() ?: "port $port open"
                conn.disconnect()
                CydEndpoint(url, "HTTP $code · $server", openMs)
            } catch (_: Exception) {
                CydEndpoint(url, "TCP open :$port", openMs)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Scan a small slice of a /24 for open :80 (slow on Fire — keep narrow). */
    suspend fun scanSubnet(prefix: String, from: Int = 1, to: Int = 20): List<CydEndpoint> =
        coroutineScope {
            (from..to).map { hostNum ->
                async(Dispatchers.IO) {
                    probeHttp("$prefix.$hostNum", 80)
                }
            }.awaitAll().filterNotNull()
        }
}

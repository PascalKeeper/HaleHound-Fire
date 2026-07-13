package com.halehoundforge.fire.guardian

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * LAN client for the Windows Npcap/Scapy guardian backend.
 */
class GuardianBackendClient(baseUrl: String) {

    private val root = baseUrl.trim().trimEnd('/')

    data class RemoteAlert(
        val message: String,
        val kind: String,
        val source: String,
        val dest: String,
        val reason: String,
        val note: String,
        val ts: String
    )

    data class RemoteStatus(
        val ok: Boolean,
        val mode: String,
        val snifferOk: Boolean,
        val virtual: Boolean,
        val npcap: Boolean,
        val scapy: Boolean,
        val totalDeauths: Int,
        val totalPackets: Int,
        val txMbps: Double,
        val rxMbps: Double,
        val iface: String,
        val error: String,
        val hostIps: List<String>,
        val alerts: List<RemoteAlert>,
        val rawSummary: String
    )

    suspend fun fetchStream(): Result<RemoteStatus> = withContext(Dispatchers.IO) {
        try {
            val json = getJson("$root/api/v1/stream")
            val status = json.optJSONObject("status") ?: JSONObject()
            val alertsArr = json.optJSONArray("alerts")
            val alerts = mutableListOf<RemoteAlert>()
            if (alertsArr != null) {
                for (i in 0 until alertsArr.length()) {
                    val a = alertsArr.optJSONObject(i) ?: continue
                    alerts += RemoteAlert(
                        message = a.optString("message", a.optString("note", "alert")),
                        kind = a.optString("kind", "deauth"),
                        source = a.optString("source", "?"),
                        dest = a.optString("dest", "?"),
                        reason = a.optString("reason", "?"),
                        note = a.optString("note", ""),
                        ts = a.optString("ts", "")
                    )
                }
            }
            val ips = mutableListOf<String>()
            val ipArr = status.optJSONArray("host_ips")
            if (ipArr != null) {
                for (i in 0 until ipArr.length()) ips += ipArr.optString(i)
            }
            Result.success(
                RemoteStatus(
                    ok = true,
                    mode = status.optString("mode", "?"),
                    snifferOk = status.optBoolean("sniffer_ok", false),
                    virtual = status.optBoolean("virtual", false),
                    npcap = status.optBoolean("npcap", false),
                    scapy = status.optBoolean("scapy", false),
                    totalDeauths = status.optInt("total_deauths", 0),
                    totalPackets = status.optInt("total_packets", 0),
                    txMbps = status.optDouble("tx_mbps", 0.0),
                    rxMbps = status.optDouble("rx_mbps", 0.0),
                    iface = status.optString("iface", "—"),
                    error = status.optString("sniffer_error", ""),
                    hostIps = ips,
                    alerts = alerts,
                    rawSummary = buildString {
                        appendLine("BACKEND  $root")
                        appendLine("mode     : ${status.optString("mode")}")
                        appendLine("npcap    : ${status.optBoolean("npcap")}  scapy=${status.optBoolean("scapy")}")
                        appendLine("sniffer  : ${status.optBoolean("sniffer_ok")}  virtual=${status.optBoolean("virtual")}")
                        appendLine("deauths  : ${status.optInt("total_deauths")}  pkts=${status.optInt("total_packets")}")
                        appendLine("bw       : TX ${"%.3f".format(status.optDouble("tx_mbps"))} / RX ${"%.3f".format(status.optDouble("rx_mbps"))} Mbps")
                        if (status.optString("sniffer_error").isNotBlank()) {
                            appendLine("error    : ${status.optString("sniffer_error")}")
                        }
                    }.trimEnd()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = getJson("$root/api/v1/health")
            json.optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
    }

    private fun getJson(urlStr: String): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500
            readTimeout = 3000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (code !in 200..299) error("HTTP $code: $body")
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}

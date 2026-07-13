package com.halehoundforge.fire.radio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class WifiApRow(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val freq: Int,
    val channel: Int,
    val security: String,
    val width: String
) {
    val line1: String get() = if (ssid.isBlank()) "<hidden SSID>" else ssid
    val line2: String
        get() = "$bssid  ·  ${rssi}dBm  ·  ch$channel  ·  ${freq}MHz  ·  $security  ·  $width"
}

/**
 * Passive Wi‑Fi survey via Android WifiManager.
 * No monitor mode, no deauth, no injection — Blue Team / inventory only.
 */
class WifiSurvey(private val context: Context) {

    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("MissingPermission")
    suspend fun scanOnce(): List<WifiApRow> = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try {
                    context.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                if (!cont.isActive) return
                @Suppress("DEPRECATION")
                val results = wifi.scanResults ?: emptyList()
                cont.resume(results.map { it.toRow() }.sortedByDescending { it.rssi })
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(receiver, filter)

        cont.invokeOnCancellation {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }

        @Suppress("DEPRECATION")
        val started = wifi.startScan()
        if (!started) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            // Fall back to last cached results
            @Suppress("DEPRECATION")
            val cached = wifi.scanResults ?: emptyList()
            if (cont.isActive) {
                cont.resume(cached.map { it.toRow() }.sortedByDescending { it.rssi })
            }
        }
    }

    private fun ScanResult.toRow(): WifiApRow {
        val sec = capabilitiesToSecurity(capabilities)
        val ch = freqToChannel(frequency)
        val w = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> "20MHz"
                ScanResult.CHANNEL_WIDTH_40MHZ -> "40MHz"
                ScanResult.CHANNEL_WIDTH_80MHZ -> "80MHz"
                ScanResult.CHANNEL_WIDTH_160MHZ -> "160MHz"
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80"
                else -> "?"
            }
        } else "?"
        val name = SSID?.trim().orEmpty().ifEmpty {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                wifiSsid?.toString()?.trim('"') ?: ""
            } else ""
        }
        return WifiApRow(
            ssid = name,
            bssid = BSSID ?: "??",
            rssi = level,
            freq = frequency,
            channel = ch,
            security = sec,
            width = w
        )
    }

    private fun capabilitiesToSecurity(caps: String?): String {
        val c = caps ?: return "OPEN?"
        return when {
            c.contains("WPA3") -> "WPA3"
            c.contains("WPA2") && c.contains("EAP") -> "WPA2-ENT"
            c.contains("WPA2") -> "WPA2"
            c.contains("WPA") -> "WPA"
            c.contains("WEP") -> "WEP"
            c.contains("OWE") -> "OWE"
            else -> "OPEN"
        }
    }

    private fun freqToChannel(freq: Int): Int = when (freq) {
        in 2412..2484 -> (freq - 2407) / 5
        in 5170..5825 -> (freq - 5000) / 5
        else -> -1
    }
}

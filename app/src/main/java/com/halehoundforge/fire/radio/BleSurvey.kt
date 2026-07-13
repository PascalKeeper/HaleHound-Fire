package com.halehoundforge.fire.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class BleDeviceRow(
    val name: String,
    val address: String,
    val rssi: Int,
    val connectable: Boolean,
    val manufacturer: String
) {
    val line1: String get() = if (name.isBlank()) "<unnamed>" else name
    val line2: String
        get() = "$address  ·  ${rssi}dBm  ·  ${if (connectable) "conn" else "adv"}  ·  $manufacturer"
}

/**
 * Passive BLE advertisement survey. No pairing storms, no spoof TX.
 */
class BleSurvey(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    @SuppressLint("MissingPermission")
    suspend fun scan(durationMs: Long = 8_000L): List<BleDeviceRow> {
        val bt = adapter ?: return emptyList()
        if (!bt.isEnabled) return emptyList()

        val scanner = bt.bluetoothLeScanner ?: return emptyList()
        val found = LinkedHashMap<String, BleDeviceRow>()

        return suspendCancellableCoroutine { cont ->
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val row = result.toRow()
                    val prev = found[row.address]
                    if (prev == null || row.rssi > prev.rssi) {
                        found[row.address] = row
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(0, it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    // Deliver whatever we have
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            try {
                scanner.startScan(null, settings, callback)
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                try {
                    scanner.stopScan(callback)
                } catch (_: Exception) {
                }
            }

            // Finish after duration on a background path
            Thread {
                try {
                    Thread.sleep(durationMs)
                } catch (_: InterruptedException) {
                }
                try {
                    scanner.stopScan(callback)
                } catch (_: Exception) {
                }
                if (cont.isActive) {
                    cont.resume(found.values.sortedByDescending { it.rssi })
                }
            }.start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toRow(): BleDeviceRow {
        val dev = device
        val nm = scanRecord?.deviceName
            ?: try {
                dev.name
            } catch (_: SecurityException) {
                null
            }
            ?: ""
        val mfg = scanRecord?.manufacturerSpecificData?.let { sparse ->
            if (sparse.size() == 0) "—"
            else {
                val id = sparse.keyAt(0)
                "mfg:0x${id.toString(16)}"
            }
        } ?: "—"
        val conn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) isConnectable else true
        return BleDeviceRow(
            name = nm,
            address = dev.address ?: "??",
            rssi = rssi,
            connectable = conn,
            manufacturer = mfg
        )
    }
}

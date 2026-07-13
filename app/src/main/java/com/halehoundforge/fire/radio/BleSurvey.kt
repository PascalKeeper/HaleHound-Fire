package com.halehoundforge.fire.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.SparseArray
import com.halehoundforge.fire.perf.LatencyProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class BleDeviceRow(
    val name: String,
    val address: String,
    val rssi: Int,
    val connectable: Boolean,
    val manufacturer: String,
    /** Company IDs seen in manufacturer data (e.g. 0x004C Apple, 0x09C8 Flock/Xuntong) */
    val mfgIds: List<Int> = emptyList(),
    /** 16/128-bit service UUIDs from advertisement */
    val serviceUuids: List<String> = emptyList(),
    val txPower: Int? = null,
    val rawMfgHex: String = ""
) {
    val line1: String get() = if (name.isBlank()) "<unnamed>" else name
    val line2: String
        get() = "$address  ·  ${rssi}dBm  ·  ${if (connectable) "conn" else "adv"}  ·  $manufacturer"
    val oui: String
        get() = address.split(":").take(3).joinToString(":").uppercase()
}

/**
 * Passive BLE advertisement survey. No pairing storms, no spoof TX.
 */
class BleSurvey(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    @SuppressLint("MissingPermission")
    suspend fun scan(durationMs: Long = LatencyProfiles.active.bleScanMs): List<BleDeviceRow> =
        withContext(Dispatchers.Default) {
            val bt = adapter ?: return@withContext emptyList()
            if (!bt.isEnabled) return@withContext emptyList()
            val scanner = bt.bluetoothLeScanner ?: return@withContext emptyList()
            val found = LinkedHashMap<String, BleDeviceRow>()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val row = result.toRow()
                    synchronized(found) {
                        val prev = found[row.address]
                        if (prev == null || row.rssi > prev.rssi) {
                            found[row.address] = row
                        }
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(0, it) }
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            try {
                scanner.startScan(null, settings, callback)
            } catch (_: SecurityException) {
                return@withContext emptyList()
            }

            try {
                delay(durationMs)
            } finally {
                try {
                    scanner.stopScan(callback)
                } catch (_: Exception) {
                }
            }

            synchronized(found) {
                found.values.sortedByDescending { it.rssi }
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
        val mfgIds = mutableListOf<Int>()
        val hexParts = mutableListOf<String>()
        scanRecord?.manufacturerSpecificData?.let { sparse: SparseArray<ByteArray> ->
            for (i in 0 until sparse.size()) {
                val id = sparse.keyAt(i)
                mfgIds += id
                val bytes = sparse.valueAt(i) ?: continue
                hexParts += "0x${id.toString(16)}:${bytes.joinToString("") { b -> "%02x".format(b) }.take(32)}"
            }
        }
        val mfgLabel = if (mfgIds.isEmpty()) "—"
        else mfgIds.joinToString(",") { "mfg:0x${it.toString(16)}" }

        val uuids = scanRecord?.serviceUuids?.map { it.toShortString() }.orEmpty()
        val conn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) isConnectable else true
        val tx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val t = txPower
            if (t == ScanResult.TX_POWER_NOT_PRESENT) null else t
        } else scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }

        return BleDeviceRow(
            name = nm,
            address = dev.address ?: "??",
            rssi = rssi,
            connectable = conn,
            manufacturer = mfgLabel,
            mfgIds = mfgIds,
            serviceUuids = uuids,
            txPower = tx,
            rawMfgHex = hexParts.joinToString(" ")
        )
    }

    private fun ParcelUuid.toShortString(): String {
        val u = uuid.toString().lowercase()
        // Compress Bluetooth base UUID 0000xxxx-0000-1000-8000-00805f9b34fb
        val m = Regex("""0000([0-9a-f]{4})-0000-1000-8000-00805f9b34fb""").find(u)
        return if (m != null) "0x${m.groupValues[1]}" else u
    }
}

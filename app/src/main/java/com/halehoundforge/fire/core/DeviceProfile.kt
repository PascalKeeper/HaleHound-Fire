package com.halehoundforge.fire.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.halehoundforge.fire.BuildConfig

data class CapabilityRow(
    val name: String,
    val fireNative: String,
    val cydRequired: String
)

object DeviceProfile {

    fun hostBanner(context: Context): String {
        return buildString {
            appendLine("model   : ${Build.MODEL}")
            appendLine("device  : ${Build.DEVICE}")
            appendLine("brand   : ${Build.BRAND}/${Build.MANUFACTURER}")
            appendLine("android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("abi     : ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("app     : HaleHound Fire ${BuildConfig.VERSION_NAME}")
            appendLine("build   : ${BuildConfig.VERSION_CODE}")
        }.trimEnd()
    }

    fun hostInfoBlock(context: Context): String {
        val pm = context.packageManager
        fun feat(name: String) = if (pm.hasSystemFeature(name)) "yes" else "no"
        return buildString {
            appendLine("HOST TABLET (companion)")
            appendLine("-----------------------")
            appendLine("Model        : ${Build.MODEL}")
            appendLine("Codename     : ${Build.DEVICE}")
            appendLine("Hardware     : ${Build.HARDWARE}")
            appendLine("OS           : Android ${Build.VERSION.RELEASE} / Fire OS 8.x class")
            appendLine("Wi‑Fi        : ${feat(PackageManager.FEATURE_WIFI)}")
            appendLine("Bluetooth LE : ${feat(PackageManager.FEATURE_BLUETOOTH_LE)}")
            appendLine("USB host     : ${feat(PackageManager.FEATURE_USB_HOST)}")
            appendLine("NFC          : ${feat(PackageManager.FEATURE_NFC)}")
            appendLine("GPS hardware : ${feat(PackageManager.FEATURE_LOCATION_GPS)}")
            appendLine("Camera       : ${feat(PackageManager.FEATURE_CAMERA_ANY)}")
        }.trimEnd()
    }

    fun capabilityMatrix(context: Context): String {
        val pm = context.packageManager
        val wifi = pm.hasSystemFeature(PackageManager.FEATURE_WIFI)
        val ble = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val nfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        val gps = pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        val usb = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

        val rows = listOf(
            CapabilityRow("Wi‑Fi survey (passive)", if (wifi) "NATIVE" else "N/A", "optional"),
            CapabilityRow("Guardian fully local", if (wifi) "NATIVE" else "N/A", "CYD true frames"),
            CapabilityRow("Wi‑Fi deauth / evil twin", "BLOCKED stock OS", "CYD required"),
            CapabilityRow("EAPOL / PMKID capture", "BLOCKED stock OS", "CYD required"),
            CapabilityRow("BLE survey (passive)", if (ble) "NATIVE" else "N/A", "optional"),
            CapabilityRow("BLE flood / spoof TX", "BLOCKED stock OS", "CYD + NRF24/BLE"),
            CapabilityRow("Sub‑GHz (CC1101)", "no radio", "CYD + CC1101"),
            CapabilityRow("2.4GHz NRF24", "no radio", "CYD + NRF24"),
            CapabilityRow("NFC / PN532", if (nfc) "limited OS NFC" else "no NFC HW", "CYD + PN532"),
            CapabilityRow("GPS wardrive", if (gps) "GPS HW" else "network loc only", "CYD + GT-U7"),
            CapabilityRow("USB serial flash", if (usb) "host OK*" else "no", "use desktop flasher"),
            CapabilityRow("CYD companion UI", "NATIVE (this app)", "CYD online")
        )

        return buildString {
            appendLine(String.format("%-24s %-18s %s", "MODULE", "FIRE 7", "FULL ARSENAL"))
            appendLine("-".repeat(64))
            rows.forEach { r ->
                appendLine(String.format("%-24s %-18s %s", r.name.take(24), r.fireNative.take(18), r.cydRequired))
            }
            appendLine()
            appendLine("* USB host present; ESP32 UART flash still best on Windows Chrome/Edge Web Serial.")
        }.trimEnd()
    }
}

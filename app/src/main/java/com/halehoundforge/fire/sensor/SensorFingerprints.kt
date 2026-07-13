package com.halehoundforge.fire.sensor

/**
 * Passive fingerprint catalog — Blue Team detection only.
 * Sources: public research (Flock/Raven BLE, Apple Find My, common IoT spy-cam patterns).
 * Confidence is heuristic; always verify in the field.
 */
object SensorFingerprints {

    enum class Kind {
        FLOCK_RAVEN,
        FLOCK_RELATED,
        AIRTAG_FINDMY,
        TILE_TRACKER,
        SPY_CAM_WIFI,
        SPY_CAM_BLE,
        CYD_SOFTAP,
        UNKNOWN
    }

    enum class Confidence { HIGH, MED, LOW }

    /** BLE company ID used on Flock Raven / battery modules (XUNTONG) — public research */
    const val MFG_FLOCK_XUNTONG = 0x09C8

    /** Apple company ID — Find My / AirTag family advertisements */
    const val MFG_APPLE = 0x004C

    /** Tile company ID (common tracker) */
    const val MFG_TILE = 0x00C4

    /**
     * Raven-oriented short UUID range reported in open Flock detection work (0x3100–0x3500).
     * Match when advertised as 16-bit service.
     */
    fun isRavenServiceUuid(u: String): Boolean {
        val s = u.lowercase().removePrefix("0x")
        val n = s.toIntOrNull(16) ?: return false
        return n in 0x3100..0x3500
    }

    /** Flock Safety assigned OUI (high signal). Component-vendor OUIs alone are LOW. */
    val FLOCK_OUI_HIGH = setOf(
        "B4:1E:52"
    )

    /** Component vendors often seen on Flock hardware — LOW alone, boosts with other signals */
    val FLOCK_OUI_COMPONENT = setOf(
        "B4:E6:2D", // Silicon Labs common modules
        "84:FD:27",
        "60:A4:23",
        "90:FD:9F",
        "C4:4F:33"
    )

    val WIFI_SSID_PATTERNS = listOf(
        Regex("""(?i)^Flock-[A-Z0-9]+$"""),
        Regex("""(?i)^Flock"""),
        Regex("""(?i)falcon"""),
        Regex("""(?i)sparrow""")
    )

    /** Cheap Wi‑Fi cam / Temu-class provisioning SSIDs */
    val SPY_WIFI_SSID = listOf(
        Regex("""(?i)^IPC[_\-]?"""),
        Regex("""(?i)^CAM[_\-]?"""),
        Regex("""(?i)WIFI[_\-]?CAM"""),
        Regex("""(?i)^HW[_\-]?"""),
        Regex("""(?i)^A9"""),
        Regex("""(?i)^ZC[_\-]"""),
        Regex("""(?i)ESP[_\-]?CAM"""),
        Regex("""(?i)^CARDV"""),
        Regex("""(?i)TUYA"""),
        Regex("""(?i)^SmartLife"""),
        Regex("""(?i)^HIPCAM"""),
        Regex("""(?i)^V380"""),
        Regex("""(?i)^MGW-"""),
        Regex("""(?i)^DGO_"""),
        Regex("""(?i)^CloudEDGE"""),
        Regex("""(?i)^ANYKA"""),
        Regex("""(?i)^XIAOVV"""),
        Regex("""(?i)^Wansview"""),
        Regex("""(?i)^Foscam""")
    )

    val SPY_BLE_NAMES = listOf(
        Regex("""(?i)^A9"""),
        Regex("""(?i)camera"""),
        Regex("""(?i)^IPC"""),
        Regex("""(?i)v380"""),
        Regex("""(?i)tuya"""),
        Regex("""(?i)smart.?life"""),
        Regex("""(?i)^cam-"""),
        Regex("""(?i)esp32"""),
        Regex("""(?i)esp_"""),
        Regex("""(?i)ble.?cam"""),
        Regex("""(?i)baby.?cam"""),
        Regex("""(?i)^X5"""),
        Regex("""(?i)^DGO""")
    )

    /** LAN ports often open on cheap IP cams */
    val SPY_CAM_PORTS = listOf(80, 81, 443, 554, 8000, 8080, 8081, 8554, 8899, 34567, 37777, 5000)

    /** Destinations / hosts Flock cloud may use — for LAN DNS/HTTP title hints only */
    val FLOCK_CLOUD_HINTS = listOf(
        "flocksafety.com",
        "flock.safety",
        "getflock",
        "raven"
    )
}

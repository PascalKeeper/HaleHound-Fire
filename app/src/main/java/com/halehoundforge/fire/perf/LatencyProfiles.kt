package com.halehoundforge.fire.perf

/**
 * Velora-inspired latency profiles adapted for Fire OS field kit.
 *
 * Velora lessons applied:
 * - Budget tokens/time per mode (ultra vs balanced)
 * - Prefer short timeouts + parallel work over long serial waits
 * - Coalesce UI / skip redundant updates
 * - Soft-ack: show progress before heavy work finishes
 *
 * Research (2026): Baseline Profiles / startup DEX layout, bounded concurrency,
 * avoid main-thread I/O, recycle UI updates on low-RAM devices (Fire 7 ~1.8GB).
 */
object LatencyProfiles {

    data class NetworkOps(
        val name: String,
        /** Gateway / host TCP connect timeout */
        val connectTimeoutMs: Int,
        /** Full harden port probe timeout per socket */
        val portProbeTimeoutMs: Int,
        /** Max concurrent socket probes (Fire SoC thrash guard) */
        val maxConcurrentProbes: Int,
        /** Guardian radio sample period (cheap) */
        val guardianRadioMs: Long,
        /** Guardian gateway latency sample period (expensive) */
        val guardianGatewayMs: Long,
        /** BLE scan default duration */
        val bleScanMs: Long,
        /** Wi‑Fi scan receiver max wait */
        val wifiScanMaxWaitMs: Long,
        /** HTTP client timeouts (CYD discover) */
        val httpConnectMs: Int,
        val httpReadMs: Int
    )

    /** Field ninja default — snappy on Fire 7 MT8168 */
    val ULTRA = NetworkOps(
        name = "ultra",
        connectTimeoutMs = 600,
        portProbeTimeoutMs = 280,
        maxConcurrentProbes = 6,
        guardianRadioMs = 1000L,
        guardianGatewayMs = 2500L,
        bleScanMs = 5500L,
        wifiScanMaxWaitMs = 9000L,
        httpConnectMs = 700,
        httpReadMs = 900
    )

    /** Slightly more thorough when plugged in / lab */
    val BALANCED = NetworkOps(
        name = "balanced",
        connectTimeoutMs = 900,
        portProbeTimeoutMs = 400,
        maxConcurrentProbes = 10,
        guardianRadioMs = 1000L,
        guardianGatewayMs = 2000L,
        bleScanMs = 8000L,
        wifiScanMaxWaitMs = 12000L,
        httpConnectMs = 1200,
        httpReadMs = 1500
    )

    @Volatile
    var active: NetworkOps = ULTRA

    fun useUltra() {
        active = ULTRA
    }

    fun useBalanced() {
        active = BALANCED
    }
}

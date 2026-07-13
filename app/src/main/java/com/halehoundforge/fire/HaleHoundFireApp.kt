package com.halehoundforge.fire

import android.app.Application
import android.os.Build
import com.halehoundforge.fire.debug.Breadcrumbs
import com.halehoundforge.fire.debug.CrashGuard
import com.halehoundforge.fire.debug.HangWatchdog
import com.halehoundforge.fire.debug.JankMonitor
import com.halehoundforge.fire.debug.StrictDebug
import com.halehoundforge.fire.perf.LatencyProfiles

class HaleHoundFireApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        // ── elite local debug stack (no phone-home) ──
        CrashGuard.install(this)
        StrictDebug.installIfDebug()
        HangWatchdog.start(intervalMs = 2_000L, timeoutMs = 5_000L)
        // Jank monitor needs a Choreographer; start after first frame via main post
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                JankMonitor.start(60)
            } catch (e: Exception) {
                Breadcrumbs.warn("JankMonitor start failed: ${e.message}")
            }
        }

        // Fire 7 class devices: ultra latency profile (bounded probes, faster timeouts)
        LatencyProfiles.useUltra()
        Breadcrumbs.add(
            "APP",
            "onCreate sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL} v=${BuildConfig.VERSION_NAME}"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // Package manager compile is shell-side; app just selects profile defaults.
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        lateinit var instance: HaleHoundFireApp
            private set
    }
}

package com.halehoundforge.fire

import android.app.Application
import android.os.Build
import com.halehoundforge.fire.perf.LatencyProfiles

class HaleHoundFireApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Fire 7 class devices: ultra latency profile (bounded probes, faster timeouts)
        // Plugged / higher-end can switch to balanced later via TERM
        LatencyProfiles.useUltra()
        // Encourage ART to keep hot paths after install (best-effort; no-op if denied)
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

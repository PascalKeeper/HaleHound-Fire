package com.halehoundforge.fire.debug

import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.halehoundforge.fire.BuildConfig

/**
 * Debug-only StrictMode — catches disk/network on main thread early.
 * Never enabled in release minify builds.
 */
object StrictDebug {

    private const val TAG = "HHF-Strict"

    fun installIfDebug() {
        if (!BuildConfig.DEBUG) return
        try {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            val vm = StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .penaltyLog()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vm.detectUnsafeIntentLaunch()
            }
            StrictMode.setVmPolicy(vm.build())
            Breadcrumbs.add("STRICT", "StrictMode ON (debug)")
            Log.i(TAG, "StrictMode armed")
        } catch (e: Exception) {
            Log.w(TAG, "StrictMode failed: ${e.message}")
        }
    }
}

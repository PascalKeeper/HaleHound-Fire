package com.halehoundforge.fire.core

import android.content.Context
import com.halehoundforge.fire.privacy.SecureStore

/**
 * VALHALLA Protocol gate — stored in encrypted prefs (no plain marker files).
 */
object ValhallaStore {
    private const val KEY_ACCEPTED = "valhalla_accepted_v1"
    private const val KEY_ACCEPTED_AT = "valhalla_accepted_at_ms"

    fun isAccepted(context: Context): Boolean {
        // migrate from legacy plain prefs once
        if (SecureStore.getBool(context, KEY_ACCEPTED, false)) return true
        val legacy = context.getSharedPreferences("halehound_fire_valhalla", Context.MODE_PRIVATE)
        if (legacy.getBoolean("accepted_v1", false)) {
            accept(context)
            legacy.edit().clear().apply()
            return true
        }
        return false
    }

    fun accept(context: Context) {
        SecureStore.putBool(context, KEY_ACCEPTED, true)
        SecureStore.putLong(context, KEY_ACCEPTED_AT, System.currentTimeMillis())
    }

    fun revoke(context: Context) {
        SecureStore.remove(context, KEY_ACCEPTED)
        SecureStore.remove(context, KEY_ACCEPTED_AT)
        context.getSharedPreferences("halehound_fire_valhalla", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}

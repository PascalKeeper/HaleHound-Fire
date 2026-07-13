package com.halehoundforge.fire.core

import android.content.Context

/**
 * VALHALLA Protocol gate — user must accept authorized/legal use before arsenal UI.
 */
object ValhallaStore {
    private const val PREFS = "halehound_fire_valhalla"
    private const val KEY_ACCEPTED = "accepted_v1"
    private const val KEY_ACCEPTED_AT = "accepted_at_ms"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCEPTED, false)

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACCEPTED, true)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun revoke(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .clear()
            .apply()
    }
}

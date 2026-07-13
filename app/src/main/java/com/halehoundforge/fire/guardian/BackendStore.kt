package com.halehoundforge.fire.guardian

import android.content.Context

object BackendStore {
    private const val PREFS = "halehound_fire_backend"
    private const val KEY_URL = "base_url"

    fun getUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, "")?.trim().orEmpty()

    fun setUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url.trim().trimEnd('/'))
            .apply()
    }
}

package com.halehoundforge.fire.guardian

import android.content.Context
import com.halehoundforge.fire.privacy.SecureStore

/** Sensei URL — encrypted at rest; call-home still gated by PrivacySettings. */
object BackendStore {
    private const val KEY_URL = "backend_base_url"

    fun getUrl(context: Context): String {
        val s = SecureStore.getString(context, KEY_URL, "")
        if (s.isNotBlank()) return s
        val legacy = context.getSharedPreferences("halehound_fire_backend", Context.MODE_PRIVATE)
            .getString("base_url", "")?.trim().orEmpty()
        if (legacy.isNotBlank()) {
            setUrl(context, legacy)
            context.getSharedPreferences("halehound_fire_backend", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        return SecureStore.getString(context, KEY_URL, "")
    }

    fun setUrl(context: Context, url: String) {
        SecureStore.putString(context, KEY_URL, url.trim().trimEnd('/'))
    }
}

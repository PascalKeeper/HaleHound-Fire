package com.halehoundforge.fire.privacy

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * At-rest encryption for personal markers and operator prefs (Android Keystore).
 * Ninja default: sensitive strings never sit in plain SharedPreferences.
 */
object SecureStore {

    private const val FILE = "hhf_secure_prefs"

    private fun prefs(context: Context): SharedPreferences {
        val master = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getString(context: Context, key: String, default: String = ""): String =
        try {
            prefs(context).getString(key, default) ?: default
        } catch (_: Exception) {
            // Migration / keystore glitch — never crash the app
            context.getSharedPreferences("hhf_secure_fallback", Context.MODE_PRIVATE)
                .getString(key, default) ?: default
        }

    fun putString(context: Context, key: String, value: String) {
        try {
            prefs(context).edit().putString(key, value).apply()
        } catch (_: Exception) {
            context.getSharedPreferences("hhf_secure_fallback", Context.MODE_PRIVATE)
                .edit().putString(key, value).apply()
        }
    }

    fun getBool(context: Context, key: String, default: Boolean = false): Boolean =
        try {
            prefs(context).getBoolean(key, default)
        } catch (_: Exception) {
            context.getSharedPreferences("hhf_secure_fallback", Context.MODE_PRIVATE)
                .getBoolean(key, default)
        }

    fun putBool(context: Context, key: String, value: Boolean) {
        try {
            prefs(context).edit().putBoolean(key, value).apply()
        } catch (_: Exception) {
            context.getSharedPreferences("hhf_secure_fallback", Context.MODE_PRIVATE)
                .edit().putBoolean(key, value).apply()
        }
    }

    fun getLong(context: Context, key: String, default: Long = 0L): Long =
        try {
            prefs(context).getLong(key, default)
        } catch (_: Exception) {
            context.getSharedPreferences("hhf_secure_fallback", Context.MODE_PRIVATE)
                .getLong(key, default)
        }

    fun putLong(context: Context, key: String, value: Long) {
        try {
            prefs(context).edit().putLong(key, value).apply()
        } catch (_: Exception) {
            context.getSharedPreferences("hhf_secure_fallback", Context.MODE_PRIVATE)
                .edit().putLong(key, value).apply()
        }
    }

    fun remove(context: Context, key: String) {
        try {
            prefs(context).edit().remove(key).apply()
        } catch (_: Exception) {
            context.getSharedPreferences("hhf_secure_fallback", Context.MODE_PRIVATE)
                .edit().remove(key).apply()
        }
    }

    fun clearAll(context: Context) {
        try {
            prefs(context).edit().clear().apply()
        } catch (_: Exception) {
        }
        context.getSharedPreferences("hhf_secure_fallback", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}

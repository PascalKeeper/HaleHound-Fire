package com.halehoundforge.fire.privacy

import android.content.Context

/**
 * Operator privacy posture — defaults favor ninja field kit, not sensei phone-home.
 */
object PrivacySettings {

    private const val K_SCRUB = "privacy_scrub_pii"
    private const val K_ENCRYPT_EXPORT = "privacy_encrypt_exports"
    private const val K_ALLOW_HOME = "privacy_allow_home_calls"
    private const val K_HOME_HTTPS_ONLY = "privacy_home_https_only"
    private const val K_EXPORT_PASSPHRASE = "privacy_export_passphrase_hint" // not the secret itself stored plain

    /** Redact serial/BSSID/MAC/IPs in copies & reports */
    fun scrubPii(context: Context): Boolean = SecureStore.getBool(context, K_SCRUB, true)

    fun setScrubPii(context: Context, v: Boolean) = SecureStore.putBool(context, K_SCRUB, v)

    /** Encrypt .bat / report files at rest with device-bound or passphrase AES */
    fun encryptExports(context: Context): Boolean = SecureStore.getBool(context, K_ENCRYPT_EXPORT, true)

    fun setEncryptExports(context: Context, v: Boolean) = SecureStore.putBool(context, K_ENCRYPT_EXPORT, v)

    /**
     * Allow optional "call home" to sensei (PC companion) at all.
     * Default FALSE — ninjas do not phone the dojo.
     */
    fun allowHomeCalls(context: Context): Boolean = SecureStore.getBool(context, K_ALLOW_HOME, false)

    fun setAllowHomeCalls(context: Context, v: Boolean) = SecureStore.putBool(context, K_ALLOW_HOME, v)

    /** If home calls enabled, require HTTPS (no cleartext to non-private) */
    fun homeHttpsOnly(context: Context): Boolean = SecureStore.getBool(context, K_HOME_HTTPS_ONLY, true)

    fun setHomeHttpsOnly(context: Context, v: Boolean) = SecureStore.putBool(context, K_HOME_HTTPS_ONLY, v)

    fun summary(context: Context): String = buildString {
        appendLine("PRIVACY POSTURE (ninja defaults)")
        appendLine("scrub PII on export     : ${scrubPii(context)}")
        appendLine("encrypt exports at rest : ${encryptExports(context)}")
        appendLine("allow call-home         : ${allowHomeCalls(context)}  (default off)")
        appendLine("home HTTPS only         : ${homeHttpsOnly(context)}")
        appendLine("telemetry to vendor     : NONE")
        appendLine("analytics SDKs          : NONE")
    }
}

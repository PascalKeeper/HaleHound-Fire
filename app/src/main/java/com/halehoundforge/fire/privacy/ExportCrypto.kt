package com.halehoundforge.fire.privacy

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypt exports so private data is not sitting as plain .bat/.txt on shared storage.
 * Format: HHF1.<b64 salt>.<b64 iv>.<b64 ciphertext>
 * Passphrase-based; if empty, uses device-bound material from SecureStore.
 */
object ExportCrypto {

    private const val PREFIX = "HHF1"
    private const val ITER = 120_000
    private const val KEY_LEN = 256
    private const val GCM_TAG = 128
    private const val DEVICE_KEY = "export_device_key_material"

    fun ensureDeviceKey(context: Context): String {
        var k = SecureStore.getString(context, DEVICE_KEY, "")
        if (k.isBlank()) {
            val raw = ByteArray(32)
            SecureRandom().nextBytes(raw)
            k = Base64.encodeToString(raw, Base64.NO_WRAP)
            SecureStore.putString(context, DEVICE_KEY, k)
        }
        return k
    }

    fun encryptToFile(
        context: Context,
        plain: ByteArray,
        outFile: File,
        passphrase: CharArray? = null
    ): File {
        val pass = if (passphrase != null && passphrase.isNotEmpty()) {
            passphrase
        } else {
            ensureDeviceKey(context).toCharArray()
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive(pass, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))
        val ct = cipher.doFinal(plain)
        val packed = buildString {
            append(PREFIX)
            append('.')
            append(Base64.encodeToString(salt, Base64.NO_WRAP))
            append('.')
            append(Base64.encodeToString(iv, Base64.NO_WRAP))
            append('.')
            append(Base64.encodeToString(ct, Base64.NO_WRAP))
            append('\n')
        }
        outFile.writeText(packed)
        return outFile
    }

    fun decryptFile(context: Context, file: File, passphrase: CharArray? = null): ByteArray {
        val parts = file.readText().trim().split('.')
        require(parts.size == 4 && parts[0] == PREFIX) { "Not an HHF encrypted export" }
        val salt = Base64.decode(parts[1], Base64.NO_WRAP)
        val iv = Base64.decode(parts[2], Base64.NO_WRAP)
        val ct = Base64.decode(parts[3], Base64.NO_WRAP)
        val pass = if (passphrase != null && passphrase.isNotEmpty()) passphrase
        else ensureDeviceKey(context).toCharArray()
        val key = derive(pass, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))
        return cipher.doFinal(ct)
    }

    private fun derive(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, ITER, KEY_LEN)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = skf.generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }
}

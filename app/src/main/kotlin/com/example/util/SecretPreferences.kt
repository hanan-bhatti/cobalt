package com.example.util

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Platform Keystore-backed AES-GCM; no security-crypto or new dependency. */
class SecretPreferences(private val prefs: SharedPreferences, private val namespace: String) {
    private fun key(create: Boolean): SecretKey = synchronized(LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: run {
            check(create) { "Credential key unavailable; stored ciphertext retained" }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            }.generateKey()
        }
    }
    private fun encrypt(name: String, value: String): String {
        require(value.length <= 65_536) { "Credential is too long" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        check(cipher.iv.size == 12)
        cipher.updateAAD("$namespace:$name".toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(name: String, value: String): String {
        val bytes = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD("$namespace:$name".toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
    }
    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        if (!stored.startsWith(PREFIX)) {
            // Migration failure retains the legacy credential and records a visible status.
            runCatching { putAll(mapOf(name to stored)) }.onFailure {
                prefs.edit().putBoolean("credential_storage_attention", true).apply()
            }
            return stored
        }
        return runCatching { decrypt(name, stored) }.getOrElse {
            prefs.edit().putBoolean("credential_storage_attention", true).apply()
            null // Never delete ciphertext or substitute another token on authentication failure.
        }
    }
    fun putAll(values: Map<String, String?>) {
        val encrypted = values.mapValues { (name, value) -> value?.let { encrypt(name, it) } }
        encrypted.forEach { (name, value) -> if (value != null) check(decrypt(name, value) == values[name]) }
        val editor = prefs.edit()
        encrypted.forEach { (name, value) -> if (value == null) editor.remove(name) else editor.putString(name, value) }
        check(editor.putBoolean("credential_storage_attention", false).commit()) { "Credential storage failed; retry" }
    }
    companion object {
        private val LOCK = Any()
        private const val ALIAS = "cobalt.credentials.v1"
        private const val PREFIX = "cobalt-enc-v1:"
    }
}

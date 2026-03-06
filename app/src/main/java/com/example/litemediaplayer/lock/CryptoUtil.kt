package com.example.litemediaplayer.lock

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoUtil @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hashSecret(secret: String): String {
        val salt = getOrCreateSalt()
        return hashWithSalt(secret, salt)
    }

    fun verifySecret(secret: String, expectedHash: String): Boolean {
        val salt = getOrCreateSalt()
        return hashWithSalt(secret, salt) == expectedHash
    }

    fun verifyHash(secret: String, expectedHash: String): Boolean {
        return verifySecret(secret, expectedHash)
    }

    private fun getOrCreateSalt(): String {
        val existing = preferences.getString(KEY_SALT, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val salt = Base64.encodeToString(random, Base64.NO_WRAP)
        preferences.edit().putString(KEY_SALT, salt).apply()
        return salt
    }

    private fun hashWithSalt(secret: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$secret".toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    companion object {
        private const val PREF_FILE = "lock_secure_prefs"
        private const val KEY_SALT = "hash_salt"
    }
}

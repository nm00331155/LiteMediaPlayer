package com.example.litemediaplayer.lock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.litemediaplayer.core.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoUtil @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val backupPreferences = appContext.getSharedPreferences(
        BACKUP_PREF_FILE,
        Context.MODE_PRIVATE
    )
    private val encryptedPreferences: SharedPreferences? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            EncryptedSharedPreferences.create(
                appContext,
                PREF_FILE,
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.onFailure { error ->
            AppLogger.w(
                "CryptoUtil",
                "EncryptedSharedPreferences is unavailable, using backup salt storage",
                error
            )
        }.getOrNull()
    }

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
        val encryptedSalt = encryptedPreferences
            ?.let { preferences ->
                runCatching { preferences.getString(KEY_SALT, null) }
                    .onFailure { error ->
                        AppLogger.w(
                            "CryptoUtil",
                            "Failed to read encrypted salt, falling back to backup storage",
                            error
                        )
                    }
                    .getOrNull()
            }
            ?.takeUnless { it.isNullOrBlank() }
        if (encryptedSalt != null) {
            persistBackupSalt(encryptedSalt)
            return encryptedSalt
        }

        val backupSalt = backupPreferences.getString(KEY_SALT, null)
        if (!backupSalt.isNullOrBlank()) {
            persistEncryptedSalt(backupSalt)
            return backupSalt
        }

        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val salt = Base64.encodeToString(random, Base64.NO_WRAP)
        persistBackupSalt(salt)
        persistEncryptedSalt(salt)
        AppLogger.w(
            "CryptoUtil",
            "Generated a replacement lock salt because no readable secure salt was found"
        )
        return salt
    }

    private fun persistBackupSalt(salt: String) {
        backupPreferences.edit().putString(KEY_SALT, salt).apply()
    }

    private fun persistEncryptedSalt(salt: String) {
        encryptedPreferences?.let { preferences ->
            runCatching {
                preferences.edit().putString(KEY_SALT, salt).apply()
            }.onFailure { error ->
                AppLogger.w(
                    "CryptoUtil",
                    "Failed to mirror salt into encrypted preferences",
                    error
                )
            }
        }
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
        private const val BACKUP_PREF_FILE = "lock_secure_prefs_backup"
        private const val KEY_SALT = "hash_salt"
    }
}

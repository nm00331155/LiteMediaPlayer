package com.example.litemediaplayer.lock

import com.example.litemediaplayer.data.LockConfig
import com.example.litemediaplayer.data.LockConfigDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockManager @Inject constructor(
    private val lockConfigDao: LockConfigDao,
    private val cryptoUtil: CryptoUtil
) {
    private val lastUnlocked = mutableMapOf<String, Long>()

    suspend fun isLocked(targetType: LockTargetType, targetId: Long?): Boolean {
        val config = lockConfigDao.findByTarget(targetType.name, targetId) ?: return false
        if (!config.isEnabled) {
            return false
        }

        if (config.autoLockMinutes <= 0) {
            return false
        }

        val key = createKey(targetType, targetId)
        val lastTime = lastUnlocked[key] ?: return true
        val elapsed = System.currentTimeMillis() - lastTime
        val autoLockMs = config.autoLockMinutes * 60_000L
        return elapsed > autoLockMs
    }

    fun recordUnlock(targetType: LockTargetType, targetId: Long?) {
        val key = createKey(targetType, targetId)
        lastUnlocked[key] = System.currentTimeMillis()
    }

    suspend fun isHidden(targetType: LockTargetType, targetId: Long?): Boolean {
        val config = lockConfigDao.findByTarget(targetType.name, targetId) ?: return false
        return config.isEnabled && config.isHidden
    }

    suspend fun verifyPin(targetType: LockTargetType, targetId: Long?, inputPin: String): Boolean {
        val config = lockConfigDao.findByTarget(targetType.name, targetId) ?: return true
        val hash = config.pinHash ?: return false
        return cryptoUtil.verifyHash(inputPin, hash)
    }

    suspend fun verifyPattern(targetType: LockTargetType, targetId: Long?, inputPattern: String): Boolean {
        val config = lockConfigDao.findByTarget(targetType.name, targetId) ?: return true
        val hash = config.patternHash ?: return false
        return cryptoUtil.verifyHash(inputPattern, hash)
    }

    suspend fun getConfig(targetType: LockTargetType, targetId: Long?): LockConfig? {
        return lockConfigDao.findByTarget(targetType.name, targetId)
    }

    fun clearUnlockRecord(targetType: LockTargetType, targetId: Long?) {
        val key = createKey(targetType, targetId)
        lastUnlocked.remove(key)
    }

    fun clearUnlockRecordsForType(targetType: LockTargetType) {
        val prefix = "${targetType.name}_"
        lastUnlocked.keys
            .filter { it.startsWith(prefix) }
            .toList()
            .forEach { key -> lastUnlocked.remove(key) }
    }

    private fun createKey(targetType: LockTargetType, targetId: Long?): String {
        return "${targetType.name}_${targetId ?: "global"}"
    }
}

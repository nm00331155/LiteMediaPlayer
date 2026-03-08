package com.example.litemediaplayer.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litemediaplayer.data.LockConfig
import com.example.litemediaplayer.data.LockConfigDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LockTargetType {
    APP_GLOBAL,
    VIDEO_FOLDER,
    COMIC_SHELF,
    COMIC_FOLDER
}

enum class LockAuthMethod {
    PIN,
    PATTERN,
    BIOMETRIC
}

data class LockUiState(
    val configs: List<LockConfig> = emptyList(),
    val showHiddenLockedContent: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockConfigDao: LockConfigDao,
    private val lockManager: LockManager,
    private val cryptoUtil: CryptoUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            lockConfigDao.observeAll().collect { configs ->
                _uiState.update { it.copy(configs = configs) }
            }
        }
    }

    fun configureLock(
        targetType: LockTargetType,
        targetId: Long?,
        authMethod: LockAuthMethod,
        pin: String?,
        pattern: String?,
        autoLockMinutes: Int,
        hidden: Boolean,
        enabled: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (authMethod == LockAuthMethod.PIN && !isValidPin(pin)) {
                _uiState.update { it.copy(errorMessage = "PIN は4〜8桁の数字で入力してください") }
                return@launch
            }

            val pinHash = pin?.let(cryptoUtil::hashSecret)
            val patternHash = pattern?.let(cryptoUtil::hashSecret)

            lockConfigDao.upsert(
                LockConfig(
                    targetType = targetType.name,
                    targetId = targetId,
                    authMethod = authMethod.name,
                    pinHash = pinHash,
                    patternHash = patternHash,
                    autoLockMinutes = autoLockMinutes,
                    isHidden = hidden,
                    isEnabled = enabled
                )
            )
        }
    }

    suspend fun unlockWithPin(
        targetType: LockTargetType,
        targetId: Long?,
        pin: String
    ): Boolean = withContext(Dispatchers.IO) {
        val config = lockManager.getConfig(targetType, targetId) ?: return@withContext true
        if (!config.isEnabled) {
            return@withContext true
        }
        val valid = lockManager.verifyPin(targetType, targetId, pin)
        if (valid) {
            lockManager.recordUnlock(targetType, targetId)
        }
        valid
    }

    suspend fun unlockWithPattern(
        targetType: LockTargetType,
        targetId: Long?,
        pattern: String
    ): Boolean = withContext(Dispatchers.IO) {
        val config = lockManager.getConfig(targetType, targetId) ?: return@withContext true
        if (!config.isEnabled) {
            return@withContext true
        }
        val valid = lockManager.verifyPattern(targetType, targetId, pattern)
        if (valid) {
            lockManager.recordUnlock(targetType, targetId)
        }
        valid
    }

    suspend fun unlockWithBiometric(
        targetType: LockTargetType,
        targetId: Long?
    ) {
        withContext(Dispatchers.IO) {
            lockManager.recordUnlock(targetType, targetId)
        }
    }

    suspend fun isAccessAllowed(
        targetType: LockTargetType,
        targetId: Long?
    ): Boolean = withContext(Dispatchers.IO) {
        !lockManager.isLocked(targetType, targetId)
    }

    fun getLockConfig(targetType: LockTargetType, targetId: Long?): LockConfig? {
        return _uiState.value.configs.firstOrNull {
            it.targetType == targetType.name && it.targetId == targetId
        }
    }

    fun isHiddenAndLocked(targetType: LockTargetType, targetId: Long?): Boolean {
        val config = getLockConfig(targetType, targetId) ?: return false
        if (!config.isHidden || !config.isEnabled) {
            return false
        }
        return !_uiState.value.showHiddenLockedContent
    }

    fun toggleHiddenContentVisibilityByGesture() {
        _uiState.update {
            it.copy(showHiddenLockedContent = !it.showHiddenLockedContent)
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun isValidPin(pin: String?): Boolean {
        return pin?.matches(Regex("^[0-9]{4,8}$")) == true
    }
}

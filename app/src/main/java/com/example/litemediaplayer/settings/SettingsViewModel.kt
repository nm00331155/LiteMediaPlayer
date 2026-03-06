package com.example.litemediaplayer.settings

import android.content.Context
import com.example.litemediaplayer.comic.ComicReaderSettings
import com.example.litemediaplayer.comic.ComicSettings
import com.example.litemediaplayer.comic.PageAnimation
import com.example.litemediaplayer.comic.ReaderMode
import com.example.litemediaplayer.comic.ReadingDirection
import com.example.litemediaplayer.comic.TrimSensitivity
import com.example.litemediaplayer.core.memory.CleanupWorker
import com.example.litemediaplayer.core.memory.MemoryMonitor
import com.example.litemediaplayer.core.memory.MemorySnapshot
import com.example.litemediaplayer.data.LockConfig
import com.example.litemediaplayer.data.LockConfigDao
import com.example.litemediaplayer.lock.CryptoUtil
import com.example.litemediaplayer.lock.LockAuthMethod
import com.example.litemediaplayer.lock.LockTargetType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appSettings: AppSettingsState = AppSettingsState(),
    val comicSettings: ComicReaderSettings = ComicReaderSettings(),
    val memorySnapshot: MemorySnapshot = MemorySnapshot(usedBytes = 0L, maxBytes = 1L),
    val memoryModuleUsage: Map<String, Long> = emptyMap(),
    val lockSecretDraft: String = "",
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsStore: AppSettingsStore,
    private val comicSettings: ComicSettings,
    private val memoryMonitor: MemoryMonitor,
    private val lockConfigDao: LockConfigDao,
    private val cryptoUtil: CryptoUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val lockSecretDraft = MutableStateFlow("")
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        appSettingsStore.settingsFlow,
        comicSettings.settingsFlow,
        memoryMonitor.snapshot,
        lockSecretDraft,
        statusMessage
    ) { app, comic, memory, draft, status ->
        SettingsUiState(
            appSettings = app,
            comicSettings = comic,
            memorySnapshot = memory,
            memoryModuleUsage = memoryMonitor.estimateModuleUsageMb(),
            lockSecretDraft = draft,
            statusMessage = status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    init {
        memoryMonitor.refresh()
    }

    fun updateSeekInterval(seconds: Int) {
        viewModelScope.launch {
            appSettingsStore.updateSeekInterval(seconds)
        }
    }

    fun updateVolumeSensitivity(sensitivity: Sensitivity) {
        viewModelScope.launch {
            appSettingsStore.updateVolumeSensitivity(sensitivity)
        }
    }

    fun updateBrightnessSensitivity(sensitivity: Sensitivity) {
        viewModelScope.launch {
            appSettingsStore.updateBrightnessSensitivity(sensitivity)
        }
    }

    fun updateDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            appSettingsStore.updateDefaultPlaybackSpeed(speed)
        }
    }

    fun updateRotationSetting(setting: RotationSetting) {
        viewModelScope.launch {
            appSettingsStore.updateRotationSetting(setting)
        }
    }

    fun updatePlayerRotation(playerRotation: PlayerRotation) {
        viewModelScope.launch {
            appSettingsStore.updatePlayerRotation(playerRotation)
        }
    }

    fun updatePlayerResizeMode(playerResizeMode: PlayerResizeMode) {
        viewModelScope.launch {
            appSettingsStore.updatePlayerResizeMode(playerResizeMode)
        }
    }

    fun updateResumeBehavior(behavior: ResumeBehavior) {
        viewModelScope.launch {
            appSettingsStore.updateResumeBehavior(behavior)
        }
    }

    fun updateComicDirection(direction: ReadingDirection) {
        viewModelScope.launch {
            comicSettings.updateReadingDirection(direction)
        }
    }

    fun updateComicAnimation(animation: PageAnimation) {
        viewModelScope.launch {
            comicSettings.updateAnimation(animation)
        }
    }

    fun updateComicAnimationSpeed(speedMs: Int) {
        viewModelScope.launch {
            comicSettings.updateAnimationSpeed(speedMs)
        }
    }

    fun updateComicMode(mode: ReaderMode) {
        viewModelScope.launch {
            comicSettings.updateMode(mode)
        }
    }

    fun updateComicZoomMax(maxZoom: Float) {
        viewModelScope.launch {
            comicSettings.updateZoomMax(maxZoom)
        }
    }

    fun updateComicBlueLightFilter(enabled: Boolean) {
        viewModelScope.launch {
            comicSettings.updateBlueLightFilter(enabled)
        }
    }

    fun updateComicAutoSplit(enabled: Boolean) {
        viewModelScope.launch {
            comicSettings.updateAutoSplit(enabled)
        }
    }

    fun updateComicSplitThreshold(value: Float) {
        viewModelScope.launch {
            comicSettings.updateSplitThreshold(value)
        }
    }

    fun updateComicSmartSplit(enabled: Boolean) {
        viewModelScope.launch {
            comicSettings.updateSmartSplit(enabled)
        }
    }

    fun updateComicSplitOffset(offset: Float) {
        viewModelScope.launch {
            comicSettings.updateSplitOffset(offset)
        }
    }

    fun updateComicAutoTrim(enabled: Boolean) {
        viewModelScope.launch {
            comicSettings.updateAutoTrim(enabled)
        }
    }

    fun updateComicTrimTolerance(tolerance: Int) {
        viewModelScope.launch {
            comicSettings.updateTrimTolerance(tolerance)
        }
    }

    fun updateComicTrimSafetyMargin(margin: Int) {
        viewModelScope.launch {
            comicSettings.updateTrimSafetyMargin(margin)
        }
    }

    fun updateComicTrimSensitivity(sensitivity: TrimSensitivity) {
        viewModelScope.launch {
            comicSettings.updateTrimSensitivity(sensitivity)
        }
    }

    fun updateLockAuthMethod(authMethod: LockAuthMethod) {
        viewModelScope.launch {
            appSettingsStore.updateLockAuthMethod(authMethod.name)
        }
    }

    fun updateRelockTimeout(minutes: Int) {
        viewModelScope.launch {
            appSettingsStore.updateRelockTimeout(minutes)
        }
    }

    fun updateHiddenLockVisibility(visible: Boolean) {
        viewModelScope.launch {
            appSettingsStore.updateHiddenLockContentVisible(visible)
        }
    }

    fun updateLockSecretDraft(value: String) {
        lockSecretDraft.value = value
    }

    fun saveAppLockConfig() {
        val state = uiState.value
        val authMethod = state.appSettings.lockAuthMethod
        val secret = state.lockSecretDraft

        viewModelScope.launch(Dispatchers.IO) {
            val method = runCatching { LockAuthMethod.valueOf(authMethod) }
                .getOrElse { LockAuthMethod.PIN }

            if (method == LockAuthMethod.PIN && !secret.matches(Regex("^[0-9]{4,8}$"))) {
                statusMessage.value = "PIN は4〜8桁の数字で入力してください"
                return@launch
            }

            val pinHash = if (method == LockAuthMethod.PIN) cryptoUtil.hashSecret(secret) else null
            val patternHash = if (method == LockAuthMethod.PATTERN) cryptoUtil.hashSecret(secret) else null

            lockConfigDao.upsert(
                LockConfig(
                    targetType = LockTargetType.APP_GLOBAL.name,
                    targetId = null,
                    authMethod = method.name,
                    pinHash = pinHash,
                    patternHash = patternHash,
                    autoLockMinutes = state.appSettings.relockTimeoutMinutes,
                    isHidden = !state.appSettings.hiddenLockContentVisible,
                    isEnabled = true
                )
            )

            statusMessage.value = "ロック設定を保存しました"
            lockSecretDraft.value = ""
        }
    }

    fun updateMemoryThreshold(ratio: Float) {
        memoryMonitor.updateThresholdRatio(ratio)
        viewModelScope.launch {
            appSettingsStore.updateMemoryThreshold(ratio)
        }
    }

    fun updateCleanupInterval(minutes: Int) {
        CleanupWorker.schedule(context, intervalMinutes = minutes)
        viewModelScope.launch {
            appSettingsStore.updateCleanupInterval(minutes)
        }
    }

    fun runManualCleanup() {
        memoryMonitor.triggerManualCleanup()
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettingsStore.updateThemeMode(mode)
        }
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch {
            appSettingsStore.updateLanguage(language)
            LocaleHelper.applyLanguage(context, language)
        }
    }

    fun clearStatusMessage() {
        statusMessage.update { null }
    }
}

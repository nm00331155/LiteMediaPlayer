package com.example.litemediaplayer.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

enum class Sensitivity {
    LOW,
    MEDIUM,
    HIGH
}

enum class RotationSetting(val displayName: String) {
    AUTO("自動回転"),
    LANDSCAPE("横画面固定"),
    PORTRAIT("縦画面固定"),
    SENSOR_LANDSCAPE("横画面（センサー回転あり）"),
    LOCKED("現在の向きで固定")
}

enum class PlayerRotation {
    FORCE_LANDSCAPE,
    FOLLOW_GLOBAL
}

enum class PlayerResizeMode {
    FIT,
    ZOOM
}

enum class ResumeBehavior {
    START_FROM_BEGINNING,
    CONTINUE_FROM_LAST
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class AppLanguage {
    SYSTEM,
    JA,
    EN
}

data class AppSettingsState(
    val seekIntervalSeconds: Int = 10,
    val volumeSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val brightnessSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val defaultPlaybackSpeed: Float = 1f,
    val rotationSetting: RotationSetting = RotationSetting.AUTO,
    val playerRotation: PlayerRotation = PlayerRotation.FORCE_LANDSCAPE,
    val playerResizeMode: PlayerResizeMode = PlayerResizeMode.FIT,
    val resumeBehavior: ResumeBehavior = ResumeBehavior.CONTINUE_FROM_LAST,
    val lockAuthMethod: String = "PIN",
    val relockTimeoutMinutes: Int = 5,
    val hiddenLockContentVisible: Boolean = false,
    val memoryThreshold: Float = 0.7f,
    val cleanupIntervalMinutes: Int = 15,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val subtitleAutoLoad: Boolean = true,
    val playerPanelLocked: Boolean = false,
    val playerOrientationLocked: Boolean = false,
    val maxConcurrentDownloads: Int = 2,
    val wifiOnlyDownload: Boolean = false,
    val downloadLocationUri: String? = null,
    val autoAddToLibrary: Boolean = true,
    val gestureSeekEnabled: Boolean = true,
    val gestureVolumeEnabled: Boolean = true,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureDoubleTapPlayPause: Boolean = true,
    val gestureBrightnessZoneEnd: Float = 0.3f,
    val gestureVolumeZoneStart: Float = 0.7f,
    val fiveTapEnabled: Boolean = true,
    val fiveTapAuthRequired: Boolean = true
)

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settingsFlow: Flow<AppSettingsState> = context.appSettingsDataStore.data
        .map { prefs ->
            AppSettingsState(
                seekIntervalSeconds = prefs[Keys.SEEK_INTERVAL] ?: 10,
                volumeSensitivity = prefs[Keys.VOLUME_SENSITIVITY]
                    ?.toEnumOrDefault(Sensitivity.MEDIUM)
                    ?: Sensitivity.MEDIUM,
                brightnessSensitivity = prefs[Keys.BRIGHTNESS_SENSITIVITY]
                    ?.toEnumOrDefault(Sensitivity.MEDIUM)
                    ?: Sensitivity.MEDIUM,
                defaultPlaybackSpeed = prefs[Keys.DEFAULT_PLAYBACK_SPEED] ?: 1f,
                rotationSetting = prefs[Keys.ROTATION_SETTING]
                    ?.toEnumOrDefault(RotationSetting.AUTO)
                    ?: RotationSetting.AUTO,
                playerRotation = prefs[Keys.PLAYER_ROTATION]
                    ?.toEnumOrDefault(PlayerRotation.FORCE_LANDSCAPE)
                    ?: PlayerRotation.FORCE_LANDSCAPE,
                playerResizeMode = prefs[Keys.PLAYER_RESIZE_MODE]
                    ?.toEnumOrDefault(PlayerResizeMode.FIT)
                    ?: PlayerResizeMode.FIT,
                resumeBehavior = prefs[Keys.RESUME_BEHAVIOR]
                    ?.toEnumOrDefault(ResumeBehavior.CONTINUE_FROM_LAST)
                    ?: ResumeBehavior.CONTINUE_FROM_LAST,
                lockAuthMethod = prefs[Keys.LOCK_AUTH_METHOD] ?: "PIN",
                relockTimeoutMinutes = prefs[Keys.RELOCK_TIMEOUT] ?: 5,
                hiddenLockContentVisible = (prefs[Keys.HIDDEN_LOCK_VISIBLE] ?: 0) == 1,
                memoryThreshold = prefs[Keys.MEMORY_THRESHOLD] ?: 0.7f,
                cleanupIntervalMinutes = prefs[Keys.CLEANUP_INTERVAL] ?: 15,
                themeMode = prefs[Keys.THEME_MODE]
                    ?.toEnumOrDefault(ThemeMode.SYSTEM)
                    ?: ThemeMode.SYSTEM,
                language = prefs[Keys.LANGUAGE]
                    ?.toEnumOrDefault(AppLanguage.SYSTEM)
                    ?: AppLanguage.SYSTEM,
                subtitleAutoLoad = prefs[Keys.SUBTITLE_AUTO_LOAD] ?: true,
                playerPanelLocked = prefs[Keys.PLAYER_PANEL_LOCKED] ?: false,
                playerOrientationLocked = prefs[Keys.PLAYER_ORIENTATION_LOCKED] ?: false,
                maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 2).coerceIn(1, 5),
                wifiOnlyDownload = prefs[Keys.WIFI_ONLY_DOWNLOAD] ?: false,
                downloadLocationUri = prefs[Keys.DOWNLOAD_LOCATION_URI],
                autoAddToLibrary = prefs[Keys.AUTO_ADD_TO_LIBRARY] ?: true,
                gestureSeekEnabled = prefs[Keys.GESTURE_SEEK_ENABLED] ?: true,
                gestureVolumeEnabled = prefs[Keys.GESTURE_VOLUME_ENABLED] ?: true,
                gestureBrightnessEnabled = prefs[Keys.GESTURE_BRIGHTNESS_ENABLED] ?: true,
                gestureDoubleTapPlayPause = prefs[Keys.GESTURE_DOUBLE_TAP_PP] ?: true,
                gestureBrightnessZoneEnd = prefs[Keys.GESTURE_BRIGHTNESS_ZONE_END] ?: 0.3f,
                gestureVolumeZoneStart = prefs[Keys.GESTURE_VOLUME_ZONE_START] ?: 0.7f,
                fiveTapEnabled = prefs[Keys.FIVE_TAP_ENABLED] ?: true,
                fiveTapAuthRequired = prefs[Keys.FIVE_TAP_AUTH_REQUIRED] ?: true
            )
        }

    suspend fun updateSeekInterval(seconds: Int) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.SEEK_INTERVAL] = seconds.coerceIn(1, 300)
        }
    }

    suspend fun updateVolumeSensitivity(value: Sensitivity) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.VOLUME_SENSITIVITY] = value.name
        }
    }

    suspend fun updateBrightnessSensitivity(value: Sensitivity) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.BRIGHTNESS_SENSITIVITY] = value.name
        }
    }

    suspend fun updateDefaultPlaybackSpeed(speed: Float) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2f)
        }
    }

    suspend fun updateRotationSetting(setting: RotationSetting) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.ROTATION_SETTING] = setting.name
        }
    }

    suspend fun updatePlayerRotation(playerRotation: PlayerRotation) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.PLAYER_ROTATION] = playerRotation.name
        }
    }

    suspend fun updatePlayerResizeMode(playerResizeMode: PlayerResizeMode) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.PLAYER_RESIZE_MODE] = playerResizeMode.name
        }
    }

    suspend fun updateResumeBehavior(behavior: ResumeBehavior) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.RESUME_BEHAVIOR] = behavior.name
        }
    }

    suspend fun updateLockAuthMethod(authMethod: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.LOCK_AUTH_METHOD] = authMethod
        }
    }

    suspend fun updateRelockTimeout(minutes: Int) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.RELOCK_TIMEOUT] = minutes
        }
    }

    suspend fun updateHiddenLockContentVisible(visible: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.HIDDEN_LOCK_VISIBLE] = if (visible) 1 else 0
        }
    }

    suspend fun updateMemoryThreshold(threshold: Float) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.MEMORY_THRESHOLD] = threshold.coerceIn(0.5f, 0.9f)
        }
    }

    suspend fun updateCleanupInterval(minutes: Int) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.CLEANUP_INTERVAL] = minutes.coerceIn(5, 60)
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.name
        }
    }

    suspend fun updateSubtitleAutoLoad(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.SUBTITLE_AUTO_LOAD] = enabled
        }
    }

    suspend fun updatePlayerPanelLocked(locked: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.PLAYER_PANEL_LOCKED] = locked
        }
    }

    suspend fun updatePlayerOrientationLocked(locked: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.PLAYER_ORIENTATION_LOCKED] = locked
        }
    }

    suspend fun updateMaxConcurrentDownloads(value: Int) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.MAX_CONCURRENT_DOWNLOADS] = value.coerceIn(1, 5)
        }
    }

    suspend fun updateWifiOnlyDownload(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.WIFI_ONLY_DOWNLOAD] = enabled
        }
    }

    suspend fun updateDownloadLocationUri(uri: String?) {
        context.appSettingsDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(Keys.DOWNLOAD_LOCATION_URI)
            } else {
                prefs[Keys.DOWNLOAD_LOCATION_URI] = uri
            }
        }
    }

    suspend fun updateAutoAddToLibrary(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_ADD_TO_LIBRARY] = enabled
        }
    }

    suspend fun updateGestureSeekEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.GESTURE_SEEK_ENABLED] = enabled
        }
    }

    suspend fun updateGestureVolumeEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.GESTURE_VOLUME_ENABLED] = enabled
        }
    }

    suspend fun updateGestureBrightnessEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.GESTURE_BRIGHTNESS_ENABLED] = enabled
        }
    }

    suspend fun updateGestureDoubleTapPlayPause(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.GESTURE_DOUBLE_TAP_PP] = enabled
        }
    }

    suspend fun updateGestureBrightnessZoneEnd(value: Float) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.GESTURE_BRIGHTNESS_ZONE_END] = value.coerceIn(0.1f, 0.5f)
        }
    }

    suspend fun updateGestureVolumeZoneStart(value: Float) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.GESTURE_VOLUME_ZONE_START] = value.coerceIn(0.5f, 0.9f)
        }
    }

    suspend fun updateFiveTapEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.FIVE_TAP_ENABLED] = enabled
        }
    }

    suspend fun updateFiveTapAuthRequired(required: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.FIVE_TAP_AUTH_REQUIRED] = required
        }
    }
}

private object Keys {
    val SEEK_INTERVAL: Preferences.Key<Int> = intPreferencesKey("seek_interval_seconds")
    val VOLUME_SENSITIVITY: Preferences.Key<String> = stringPreferencesKey("volume_sensitivity")
    val BRIGHTNESS_SENSITIVITY: Preferences.Key<String> = stringPreferencesKey("brightness_sensitivity")
    val DEFAULT_PLAYBACK_SPEED: Preferences.Key<Float> = floatPreferencesKey("default_playback_speed")
    val ROTATION_SETTING: Preferences.Key<String> =
        stringPreferencesKey("rotation_setting")
    val PLAYER_ROTATION: Preferences.Key<String> = stringPreferencesKey("player_rotation")
    val PLAYER_RESIZE_MODE: Preferences.Key<String> = stringPreferencesKey("player_resize_mode")
    val RESUME_BEHAVIOR: Preferences.Key<String> = stringPreferencesKey("resume_behavior")
    val LOCK_AUTH_METHOD: Preferences.Key<String> = stringPreferencesKey("lock_auth_method")
    val RELOCK_TIMEOUT: Preferences.Key<Int> = intPreferencesKey("relock_timeout")
    val HIDDEN_LOCK_VISIBLE: Preferences.Key<Int> = intPreferencesKey("hidden_lock_visible")
    val MEMORY_THRESHOLD: Preferences.Key<Float> = floatPreferencesKey("memory_threshold")
    val CLEANUP_INTERVAL: Preferences.Key<Int> = intPreferencesKey("cleanup_interval")
    val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("theme_mode")
    val LANGUAGE: Preferences.Key<String> = stringPreferencesKey("language")
    val SUBTITLE_AUTO_LOAD: Preferences.Key<Boolean> = booleanPreferencesKey("subtitle_auto_load")
    val PLAYER_PANEL_LOCKED: Preferences.Key<Boolean> = booleanPreferencesKey("player_panel_locked")
    val PLAYER_ORIENTATION_LOCKED: Preferences.Key<Boolean> = booleanPreferencesKey("player_orientation_locked")
    val MAX_CONCURRENT_DOWNLOADS: Preferences.Key<Int> = intPreferencesKey("max_concurrent_downloads")
    val WIFI_ONLY_DOWNLOAD: Preferences.Key<Boolean> = booleanPreferencesKey("wifi_only_download")
    val DOWNLOAD_LOCATION_URI: Preferences.Key<String> = stringPreferencesKey("download_location_uri")
    val AUTO_ADD_TO_LIBRARY: Preferences.Key<Boolean> = booleanPreferencesKey("auto_add_to_library")
    val GESTURE_SEEK_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("gesture_seek_enabled")
    val GESTURE_VOLUME_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("gesture_volume_enabled")
    val GESTURE_BRIGHTNESS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("gesture_brightness_enabled")
    val GESTURE_DOUBLE_TAP_PP: Preferences.Key<Boolean> = booleanPreferencesKey("gesture_double_tap_pp")
    val GESTURE_BRIGHTNESS_ZONE_END: Preferences.Key<Float> = floatPreferencesKey("gesture_brightness_zone_end")
    val GESTURE_VOLUME_ZONE_START: Preferences.Key<Float> = floatPreferencesKey("gesture_volume_zone_start")
    val FIVE_TAP_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("five_tap_enabled")
    val FIVE_TAP_AUTH_REQUIRED: Preferences.Key<Boolean> = booleanPreferencesKey("five_tap_auth_required")
}

private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T {
    return enumValues<T>().firstOrNull { it.name == this } ?: default
}

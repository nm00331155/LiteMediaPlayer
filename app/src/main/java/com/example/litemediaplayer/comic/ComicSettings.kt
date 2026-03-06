package com.example.litemediaplayer.comic

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

private val Context.comicDataStore by preferencesDataStore(name = "comic_settings")

enum class ReadingDirection {
    RTL,
    LTR
}

enum class PageAnimation {
    SLIDE,
    CURL,
    FADE,
    NONE
}

enum class ReaderMode {
    PAGE,
    VERTICAL,
    SPREAD
}

enum class TrimSensitivity(val threshold: Float) {
    LOW(0.03f),
    MEDIUM(0.02f),
    HIGH(0.01f)
}

data class ComicReaderSettings(
    val readingDirection: ReadingDirection = ReadingDirection.RTL,
    val animation: PageAnimation = PageAnimation.SLIDE,
    val animationSpeedMs: Int = 300,
    val mode: ReaderMode = ReaderMode.PAGE,
    val backgroundColorArgb: Int = 0xFF000000.toInt(),
    val pagePaddingDp: Int = 0,
    val blueLightFilterEnabled: Boolean = false,
    val zoomMax: Float = 5f,
    val autoSplitEnabled: Boolean = true,
    val splitThreshold: Float = 1.3f,
    val smartSplitEnabled: Boolean = true,
    val splitOffsetPercent: Float = 0f,
    val autoTrimEnabled: Boolean = false,
    val trimTolerance: Int = 30,
    val trimSafetyMargin: Int = 2,
    val trimSensitivity: TrimSensitivity = TrimSensitivity.MEDIUM,
    val verticalScrollSpeed: Float = 1f,
    val pageSnapEnabled: Boolean = false
)

@Singleton
class ComicSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settingsFlow: Flow<ComicReaderSettings> = context.comicDataStore.data
        .map { prefs ->
            ComicReaderSettings(
                readingDirection = prefs[Keys.READING_DIRECTION]
                    ?.toEnumOrDefault(ReadingDirection.RTL)
                    ?: ReadingDirection.RTL,
                animation = prefs[Keys.PAGE_ANIMATION]
                    ?.toEnumOrDefault(PageAnimation.SLIDE)
                    ?: PageAnimation.SLIDE,
                animationSpeedMs = prefs[Keys.ANIMATION_SPEED_MS] ?: 300,
                mode = prefs[Keys.READER_MODE]
                    ?.toEnumOrDefault(ReaderMode.PAGE)
                    ?: ReaderMode.PAGE,
                backgroundColorArgb = prefs[Keys.BACKGROUND_COLOR_ARGB] ?: 0xFF000000.toInt(),
                pagePaddingDp = prefs[Keys.PAGE_PADDING_DP] ?: 0,
                blueLightFilterEnabled = prefs[Keys.BLUE_LIGHT_FILTER] ?: false,
                zoomMax = prefs[Keys.ZOOM_MAX] ?: 5f,
                autoSplitEnabled = prefs[Keys.AUTO_SPLIT_ENABLED] ?: true,
                splitThreshold = prefs[Keys.SPLIT_THRESHOLD] ?: 1.3f,
                smartSplitEnabled = prefs[Keys.SMART_SPLIT_ENABLED] ?: true,
                splitOffsetPercent = prefs[Keys.SPLIT_OFFSET_PERCENT] ?: 0f,
                autoTrimEnabled = prefs[Keys.AUTO_TRIM_ENABLED] ?: false,
                trimTolerance = prefs[Keys.TRIM_TOLERANCE] ?: 30,
                trimSafetyMargin = prefs[Keys.TRIM_SAFETY_MARGIN] ?: 2,
                trimSensitivity = prefs[Keys.TRIM_SENSITIVITY]
                    ?.toEnumOrDefault(TrimSensitivity.MEDIUM)
                    ?: TrimSensitivity.MEDIUM,
                verticalScrollSpeed = prefs[Keys.VERTICAL_SCROLL_SPEED] ?: 1f,
                pageSnapEnabled = prefs[Keys.PAGE_SNAP_ENABLED] ?: false
            )
        }

    suspend fun updateMode(mode: ReaderMode) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.READER_MODE] = mode.name
        }
    }

    suspend fun updateReadingDirection(direction: ReadingDirection) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.READING_DIRECTION] = direction.name
        }
    }

    suspend fun updateAutoSplit(enabled: Boolean) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.AUTO_SPLIT_ENABLED] = enabled
        }
    }

    suspend fun updateSplitThreshold(threshold: Float) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.SPLIT_THRESHOLD] = threshold
        }
    }

    suspend fun updateSmartSplit(enabled: Boolean) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.SMART_SPLIT_ENABLED] = enabled
        }
    }

    suspend fun updateSplitOffset(offsetPercent: Float) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.SPLIT_OFFSET_PERCENT] = offsetPercent
        }
    }

    suspend fun updateAutoTrim(enabled: Boolean) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.AUTO_TRIM_ENABLED] = enabled
        }
    }

    suspend fun updateTrimTolerance(tolerance: Int) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.TRIM_TOLERANCE] = tolerance
        }
    }

    suspend fun updateTrimSafetyMargin(margin: Int) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.TRIM_SAFETY_MARGIN] = margin
        }
    }

    suspend fun updateTrimSensitivity(sensitivity: TrimSensitivity) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.TRIM_SENSITIVITY] = sensitivity.name
        }
    }

    suspend fun updateAnimation(animation: PageAnimation) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.PAGE_ANIMATION] = animation.name
        }
    }

    suspend fun updateAnimationSpeed(speedMs: Int) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.ANIMATION_SPEED_MS] = speedMs.coerceIn(100, 1000)
        }
    }

    suspend fun updateBackgroundColor(argb: Int) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_COLOR_ARGB] = argb
        }
    }

    suspend fun updatePagePadding(paddingDp: Int) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.PAGE_PADDING_DP] = paddingDp.coerceIn(0, 64)
        }
    }

    suspend fun updateBlueLightFilter(enabled: Boolean) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.BLUE_LIGHT_FILTER] = enabled
        }
    }

    suspend fun updateZoomMax(maxZoom: Float) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.ZOOM_MAX] = maxZoom.coerceIn(2f, 5f)
        }
    }

    suspend fun updateVerticalScrollSpeed(speed: Float) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.VERTICAL_SCROLL_SPEED] = speed.coerceIn(0.5f, 3f)
        }
    }

    suspend fun updatePageSnapEnabled(enabled: Boolean) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.PAGE_SNAP_ENABLED] = enabled
        }
    }
}

private object Keys {
    val READING_DIRECTION: Preferences.Key<String> = stringPreferencesKey("reading_direction")
    val PAGE_ANIMATION: Preferences.Key<String> = stringPreferencesKey("page_animation")
    val ANIMATION_SPEED_MS: Preferences.Key<Int> = intPreferencesKey("animation_speed_ms")
    val READER_MODE: Preferences.Key<String> = stringPreferencesKey("reader_mode")
    val BACKGROUND_COLOR_ARGB: Preferences.Key<Int> = intPreferencesKey("background_color_argb")
    val PAGE_PADDING_DP: Preferences.Key<Int> = intPreferencesKey("page_padding_dp")
    val BLUE_LIGHT_FILTER: Preferences.Key<Boolean> = booleanPreferencesKey("blue_light_filter")
    val ZOOM_MAX: Preferences.Key<Float> = floatPreferencesKey("zoom_max")
    val AUTO_SPLIT_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("auto_split_enabled")
    val SPLIT_THRESHOLD: Preferences.Key<Float> = floatPreferencesKey("split_threshold")
    val SMART_SPLIT_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("smart_split_enabled")
    val SPLIT_OFFSET_PERCENT: Preferences.Key<Float> = floatPreferencesKey("split_offset_percent")
    val AUTO_TRIM_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("auto_trim_enabled")
    val TRIM_TOLERANCE: Preferences.Key<Int> = intPreferencesKey("trim_tolerance")
    val TRIM_SAFETY_MARGIN: Preferences.Key<Int> = intPreferencesKey("trim_safety_margin")
    val TRIM_SENSITIVITY: Preferences.Key<String> = stringPreferencesKey("trim_sensitivity")
    val VERTICAL_SCROLL_SPEED: Preferences.Key<Float> = floatPreferencesKey("vertical_scroll_speed")
    val PAGE_SNAP_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("page_snap_enabled")
}

private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T {
    return enumValues<T>().firstOrNull { it.name == this } ?: default
}

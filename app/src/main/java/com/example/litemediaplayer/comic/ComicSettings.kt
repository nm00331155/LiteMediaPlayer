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

enum class GridSize(val minDp: Int, val label: String) {
    SMALL(90, "小"),
    MEDIUM(120, "中"),
    LARGE(170, "大")
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
    val gridSize: GridSize = GridSize.MEDIUM,
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
    val pageSnapEnabled: Boolean = false,
    val touchZone: TouchZoneConfig = TouchZoneConfig()
)

object ComicSettingsDefaults {
    val values = ComicReaderSettings()

    const val ANIMATION_SPEED_MIN = 100
    const val ANIMATION_SPEED_MAX = 2000
    const val ZOOM_MAX_MIN = 2f
    const val ZOOM_MAX_MAX = 10f
    const val SPLIT_THRESHOLD_MIN = 1f
    const val SPLIT_THRESHOLD_MAX = 4f
    const val SPLIT_OFFSET_MIN = -0.10f
    const val SPLIT_OFFSET_MAX = 0.10f
    const val TRIM_TOLERANCE_MIN = 10
    const val TRIM_TOLERANCE_MAX = 120
    const val TRIM_SAFETY_MARGIN_MIN = 0
    const val TRIM_SAFETY_MARGIN_MAX = 40
    const val TOUCH_LONG_PRESS_MIN = 200
    const val TOUCH_LONG_PRESS_MAX = 3000
    const val TOUCH_SKIP_PAGE_MIN = 1
    const val TOUCH_SKIP_PAGE_MAX = 100
}

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
                animation = (prefs[Keys.PAGE_ANIMATION]
                    ?.toEnumOrDefault(PageAnimation.SLIDE)
                    ?: PageAnimation.SLIDE)
                    .let { animation ->
                        if (animation == PageAnimation.CURL) {
                            PageAnimation.SLIDE
                        } else {
                            animation
                        }
                    },
                animationSpeedMs = (prefs[Keys.ANIMATION_SPEED_MS] ?: 300).coerceIn(
                    ComicSettingsDefaults.ANIMATION_SPEED_MIN,
                    ComicSettingsDefaults.ANIMATION_SPEED_MAX
                ),
                mode = prefs[Keys.READER_MODE]
                    ?.toEnumOrDefault(ReaderMode.PAGE)
                    ?: ReaderMode.PAGE,
                gridSize = prefs[Keys.GRID_SIZE]
                    ?.toEnumOrDefault(GridSize.MEDIUM)
                    ?: GridSize.MEDIUM,
                backgroundColorArgb = prefs[Keys.BACKGROUND_COLOR_ARGB] ?: 0xFF000000.toInt(),
                pagePaddingDp = prefs[Keys.PAGE_PADDING_DP] ?: 0,
                blueLightFilterEnabled = prefs[Keys.BLUE_LIGHT_FILTER] ?: false,
                zoomMax = (prefs[Keys.ZOOM_MAX] ?: 5f).coerceIn(
                    ComicSettingsDefaults.ZOOM_MAX_MIN,
                    ComicSettingsDefaults.ZOOM_MAX_MAX
                ),
                autoSplitEnabled = prefs[Keys.AUTO_SPLIT_ENABLED] ?: true,
                splitThreshold = (prefs[Keys.SPLIT_THRESHOLD] ?: 1.3f).coerceIn(
                    ComicSettingsDefaults.SPLIT_THRESHOLD_MIN,
                    ComicSettingsDefaults.SPLIT_THRESHOLD_MAX
                ),
                smartSplitEnabled = prefs[Keys.SMART_SPLIT_ENABLED] ?: true,
                splitOffsetPercent = (prefs[Keys.SPLIT_OFFSET_PERCENT] ?: 0f).coerceIn(
                    ComicSettingsDefaults.SPLIT_OFFSET_MIN,
                    ComicSettingsDefaults.SPLIT_OFFSET_MAX
                ),
                autoTrimEnabled = prefs[Keys.AUTO_TRIM_ENABLED] ?: false,
                trimTolerance = (prefs[Keys.TRIM_TOLERANCE] ?: 30).coerceIn(
                    ComicSettingsDefaults.TRIM_TOLERANCE_MIN,
                    ComicSettingsDefaults.TRIM_TOLERANCE_MAX
                ),
                trimSafetyMargin = (prefs[Keys.TRIM_SAFETY_MARGIN] ?: 2).coerceIn(
                    ComicSettingsDefaults.TRIM_SAFETY_MARGIN_MIN,
                    ComicSettingsDefaults.TRIM_SAFETY_MARGIN_MAX
                ),
                trimSensitivity = prefs[Keys.TRIM_SENSITIVITY]
                    ?.toEnumOrDefault(TrimSensitivity.MEDIUM)
                    ?: TrimSensitivity.MEDIUM,
                verticalScrollSpeed = prefs[Keys.VERTICAL_SCROLL_SPEED] ?: 1f,
                pageSnapEnabled = prefs[Keys.PAGE_SNAP_ENABLED] ?: false,
                touchZone = TouchZoneConfig(
                    layout = prefs[Keys.TOUCH_ZONE_LAYOUT]
                        ?.toEnumOrDefault(TouchZoneLayout.THREE_COLUMN)
                        ?: TouchZoneLayout.THREE_COLUMN,
                    leftTap = prefs[Keys.TOUCH_LEFT_TAP]
                        ?.toEnumOrDefault(TouchAction.NEXT_PAGE)
                        ?: TouchAction.NEXT_PAGE,
                    centerTap = prefs[Keys.TOUCH_CENTER_TAP]
                        ?.toEnumOrDefault(TouchAction.TOGGLE_CONTROLS)
                        ?: TouchAction.TOGGLE_CONTROLS,
                    rightTap = prefs[Keys.TOUCH_RIGHT_TAP]
                        ?.toEnumOrDefault(TouchAction.PREV_PAGE)
                        ?: TouchAction.PREV_PAGE,
                    leftLongPress = prefs[Keys.TOUCH_LEFT_LONG]
                        ?.toEnumOrDefault(TouchAction.SKIP_FORWARD)
                        ?: TouchAction.SKIP_FORWARD,
                    centerLongPress = prefs[Keys.TOUCH_CENTER_LONG]
                        ?.toEnumOrDefault(TouchAction.JUMP_TO_PAGE)
                        ?: TouchAction.JUMP_TO_PAGE,
                    rightLongPress = prefs[Keys.TOUCH_RIGHT_LONG]
                        ?.toEnumOrDefault(TouchAction.SKIP_BACKWARD)
                        ?: TouchAction.SKIP_BACKWARD,
                    topLeftTap = prefs[Keys.TOUCH_TOP_LEFT_TAP]
                        ?.toEnumOrDefault(TouchAction.PREV_PAGE)
                        ?: TouchAction.PREV_PAGE,
                    topCenterTap = prefs[Keys.TOUCH_TOP_CENTER_TAP]
                        ?.toEnumOrDefault(TouchAction.TOGGLE_CONTROLS)
                        ?: TouchAction.TOGGLE_CONTROLS,
                    topRightTap = prefs[Keys.TOUCH_TOP_RIGHT_TAP]
                        ?.toEnumOrDefault(TouchAction.NEXT_PAGE)
                        ?: TouchAction.NEXT_PAGE,
                    topLeftLongPress = prefs[Keys.TOUCH_TOP_LEFT_LONG]
                        ?.toEnumOrDefault(TouchAction.FIRST_PAGE)
                        ?: TouchAction.FIRST_PAGE,
                    topCenterLongPress = prefs[Keys.TOUCH_TOP_CENTER_LONG]
                        ?.toEnumOrDefault(TouchAction.JUMP_TO_PAGE)
                        ?: TouchAction.JUMP_TO_PAGE,
                    topRightLongPress = prefs[Keys.TOUCH_TOP_RIGHT_LONG]
                        ?.toEnumOrDefault(TouchAction.LAST_PAGE)
                        ?: TouchAction.LAST_PAGE,
                    bottomLeftTap = prefs[Keys.TOUCH_BOTTOM_LEFT_TAP]
                        ?.toEnumOrDefault(TouchAction.SKIP_BACKWARD)
                        ?: TouchAction.SKIP_BACKWARD,
                    bottomCenterTap = prefs[Keys.TOUCH_BOTTOM_CENTER_TAP]
                        ?.toEnumOrDefault(TouchAction.TOGGLE_FULLSCREEN)
                        ?: TouchAction.TOGGLE_FULLSCREEN,
                    bottomRightTap = prefs[Keys.TOUCH_BOTTOM_RIGHT_TAP]
                        ?.toEnumOrDefault(TouchAction.SKIP_FORWARD)
                        ?: TouchAction.SKIP_FORWARD,
                    bottomLeftLongPress = prefs[Keys.TOUCH_BOTTOM_LEFT_LONG]
                        ?.toEnumOrDefault(TouchAction.NONE)
                        ?: TouchAction.NONE,
                    bottomCenterLongPress = prefs[Keys.TOUCH_BOTTOM_CENTER_LONG]
                        ?.toEnumOrDefault(TouchAction.NONE)
                        ?: TouchAction.NONE,
                    bottomRightLongPress = prefs[Keys.TOUCH_BOTTOM_RIGHT_LONG]
                        ?.toEnumOrDefault(TouchAction.NONE)
                        ?: TouchAction.NONE,
                    longPressMs = (prefs[Keys.TOUCH_LONG_PRESS_MS] ?: 500).coerceIn(
                        ComicSettingsDefaults.TOUCH_LONG_PRESS_MIN,
                        ComicSettingsDefaults.TOUCH_LONG_PRESS_MAX
                    ),
                    skipPageCount = (prefs[Keys.TOUCH_SKIP_PAGE_COUNT] ?: 10).coerceIn(
                        ComicSettingsDefaults.TOUCH_SKIP_PAGE_MIN,
                        ComicSettingsDefaults.TOUCH_SKIP_PAGE_MAX
                    ),
                    volumeUpAction = prefs[Keys.VOLUME_UP_ACTION]
                        ?.toEnumOrDefault(TouchAction.NEXT_PAGE)
                        ?: TouchAction.NEXT_PAGE,
                    volumeDownAction = prefs[Keys.VOLUME_DOWN_ACTION]
                        ?.toEnumOrDefault(TouchAction.PREV_PAGE)
                        ?: TouchAction.PREV_PAGE
                )
            )
        }

    suspend fun updateMode(mode: ReaderMode) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.READER_MODE] = mode.name
        }
    }

    suspend fun updateGridSize(size: GridSize) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.GRID_SIZE] = size.name
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
            prefs[Keys.SPLIT_THRESHOLD] = threshold.coerceIn(
                ComicSettingsDefaults.SPLIT_THRESHOLD_MIN,
                ComicSettingsDefaults.SPLIT_THRESHOLD_MAX
            )
        }
    }

    suspend fun updateSmartSplit(enabled: Boolean) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.SMART_SPLIT_ENABLED] = enabled
        }
    }

    suspend fun updateSplitOffset(offsetPercent: Float) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.SPLIT_OFFSET_PERCENT] = offsetPercent.coerceIn(
                ComicSettingsDefaults.SPLIT_OFFSET_MIN,
                ComicSettingsDefaults.SPLIT_OFFSET_MAX
            )
        }
    }

    suspend fun updateAutoTrim(enabled: Boolean) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.AUTO_TRIM_ENABLED] = enabled
        }
    }

    suspend fun updateTrimTolerance(tolerance: Int) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.TRIM_TOLERANCE] = tolerance.coerceIn(
                ComicSettingsDefaults.TRIM_TOLERANCE_MIN,
                ComicSettingsDefaults.TRIM_TOLERANCE_MAX
            )
        }
    }

    suspend fun updateTrimSafetyMargin(margin: Int) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.TRIM_SAFETY_MARGIN] = margin.coerceIn(
                ComicSettingsDefaults.TRIM_SAFETY_MARGIN_MIN,
                ComicSettingsDefaults.TRIM_SAFETY_MARGIN_MAX
            )
        }
    }

    suspend fun updateTrimSensitivity(sensitivity: TrimSensitivity) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.TRIM_SENSITIVITY] = sensitivity.name
        }
    }

    suspend fun updateAnimation(animation: PageAnimation) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.PAGE_ANIMATION] = if (animation == PageAnimation.CURL) {
                PageAnimation.SLIDE.name
            } else {
                animation.name
            }
        }
    }

    suspend fun updateAnimationSpeed(speedMs: Int) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.ANIMATION_SPEED_MS] = speedMs.coerceIn(
                ComicSettingsDefaults.ANIMATION_SPEED_MIN,
                ComicSettingsDefaults.ANIMATION_SPEED_MAX
            )
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
            prefs[Keys.ZOOM_MAX] = maxZoom.coerceIn(
                ComicSettingsDefaults.ZOOM_MAX_MIN,
                ComicSettingsDefaults.ZOOM_MAX_MAX
            )
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

    suspend fun updateTouchZoneConfig(config: TouchZoneConfig) {
        context.comicDataStore.edit { prefs ->
            prefs[Keys.TOUCH_ZONE_LAYOUT] = config.layout.name
            prefs[Keys.TOUCH_LEFT_TAP] = config.leftTap.name
            prefs[Keys.TOUCH_CENTER_TAP] = config.centerTap.name
            prefs[Keys.TOUCH_RIGHT_TAP] = config.rightTap.name
            prefs[Keys.TOUCH_LEFT_LONG] = config.leftLongPress.name
            prefs[Keys.TOUCH_CENTER_LONG] = config.centerLongPress.name
            prefs[Keys.TOUCH_RIGHT_LONG] = config.rightLongPress.name
            prefs[Keys.TOUCH_TOP_LEFT_TAP] = config.topLeftTap.name
            prefs[Keys.TOUCH_TOP_CENTER_TAP] = config.topCenterTap.name
            prefs[Keys.TOUCH_TOP_RIGHT_TAP] = config.topRightTap.name
            prefs[Keys.TOUCH_TOP_LEFT_LONG] = config.topLeftLongPress.name
            prefs[Keys.TOUCH_TOP_CENTER_LONG] = config.topCenterLongPress.name
            prefs[Keys.TOUCH_TOP_RIGHT_LONG] = config.topRightLongPress.name
            prefs[Keys.TOUCH_BOTTOM_LEFT_TAP] = config.bottomLeftTap.name
            prefs[Keys.TOUCH_BOTTOM_CENTER_TAP] = config.bottomCenterTap.name
            prefs[Keys.TOUCH_BOTTOM_RIGHT_TAP] = config.bottomRightTap.name
            prefs[Keys.TOUCH_BOTTOM_LEFT_LONG] = config.bottomLeftLongPress.name
            prefs[Keys.TOUCH_BOTTOM_CENTER_LONG] = config.bottomCenterLongPress.name
            prefs[Keys.TOUCH_BOTTOM_RIGHT_LONG] = config.bottomRightLongPress.name
            prefs[Keys.TOUCH_LONG_PRESS_MS] = config.longPressMs.coerceIn(
                ComicSettingsDefaults.TOUCH_LONG_PRESS_MIN,
                ComicSettingsDefaults.TOUCH_LONG_PRESS_MAX
            )
            prefs[Keys.TOUCH_SKIP_PAGE_COUNT] = config.skipPageCount.coerceIn(
                ComicSettingsDefaults.TOUCH_SKIP_PAGE_MIN,
                ComicSettingsDefaults.TOUCH_SKIP_PAGE_MAX
            )
            prefs[Keys.VOLUME_UP_ACTION] = config.volumeUpAction.name
            prefs[Keys.VOLUME_DOWN_ACTION] = config.volumeDownAction.name
        }
    }
}

private object Keys {
    val READING_DIRECTION: Preferences.Key<String> = stringPreferencesKey("reading_direction")
    val PAGE_ANIMATION: Preferences.Key<String> = stringPreferencesKey("page_animation")
    val ANIMATION_SPEED_MS: Preferences.Key<Int> = intPreferencesKey("animation_speed_ms")
    val READER_MODE: Preferences.Key<String> = stringPreferencesKey("reader_mode")
    val GRID_SIZE: Preferences.Key<String> = stringPreferencesKey("comic_grid_size")
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
    val TOUCH_ZONE_LAYOUT: Preferences.Key<String> = stringPreferencesKey("touch_zone_layout")
    val TOUCH_LEFT_TAP: Preferences.Key<String> = stringPreferencesKey("touch_left_tap")
    val TOUCH_CENTER_TAP: Preferences.Key<String> = stringPreferencesKey("touch_center_tap")
    val TOUCH_RIGHT_TAP: Preferences.Key<String> = stringPreferencesKey("touch_right_tap")
    val TOUCH_LEFT_LONG: Preferences.Key<String> = stringPreferencesKey("touch_left_long")
    val TOUCH_CENTER_LONG: Preferences.Key<String> = stringPreferencesKey("touch_center_long")
    val TOUCH_RIGHT_LONG: Preferences.Key<String> = stringPreferencesKey("touch_right_long")
    val TOUCH_TOP_LEFT_TAP: Preferences.Key<String> = stringPreferencesKey("touch_top_left_tap")
    val TOUCH_TOP_CENTER_TAP: Preferences.Key<String> =
        stringPreferencesKey("touch_top_center_tap")
    val TOUCH_TOP_RIGHT_TAP: Preferences.Key<String> = stringPreferencesKey("touch_top_right_tap")
    val TOUCH_TOP_LEFT_LONG: Preferences.Key<String> = stringPreferencesKey("touch_top_left_long")
    val TOUCH_TOP_CENTER_LONG: Preferences.Key<String> =
        stringPreferencesKey("touch_top_center_long")
    val TOUCH_TOP_RIGHT_LONG: Preferences.Key<String> =
        stringPreferencesKey("touch_top_right_long")
    val TOUCH_BOTTOM_LEFT_TAP: Preferences.Key<String> =
        stringPreferencesKey("touch_bottom_left_tap")
    val TOUCH_BOTTOM_CENTER_TAP: Preferences.Key<String> =
        stringPreferencesKey("touch_bottom_center_tap")
    val TOUCH_BOTTOM_RIGHT_TAP: Preferences.Key<String> =
        stringPreferencesKey("touch_bottom_right_tap")
    val TOUCH_BOTTOM_LEFT_LONG: Preferences.Key<String> =
        stringPreferencesKey("touch_bottom_left_long")
    val TOUCH_BOTTOM_CENTER_LONG: Preferences.Key<String> =
        stringPreferencesKey("touch_bottom_center_long")
    val TOUCH_BOTTOM_RIGHT_LONG: Preferences.Key<String> =
        stringPreferencesKey("touch_bottom_right_long")
    val TOUCH_LONG_PRESS_MS: Preferences.Key<Int> = intPreferencesKey("touch_long_press_ms")
    val TOUCH_SKIP_PAGE_COUNT: Preferences.Key<Int> = intPreferencesKey("touch_skip_page_count")
    val VOLUME_UP_ACTION: Preferences.Key<String> = stringPreferencesKey("volume_up_action")
    val VOLUME_DOWN_ACTION: Preferences.Key<String> = stringPreferencesKey("volume_down_action")
}

private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T {
    return enumValues<T>().firstOrNull { it.name == this } ?: default
}

package com.example.litemediaplayer.settings

import android.content.pm.ActivityInfo
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OrientationController(
    private val activity: FragmentActivity,
    private val appSettingsStore: AppSettingsStore
) {
    fun observe() {
        activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appSettingsStore.settingsFlow
                    .map { it.rotationSetting }
                    .distinctUntilChanged()
                    .collect { setting ->
                        applyRotation(setting)
                    }
            }
        }
    }

    fun applyRotation(setting: RotationSetting) {
        activity.requestedOrientation = when (setting) {
            RotationSetting.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            RotationSetting.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            RotationSetting.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            RotationSetting.SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            RotationSetting.LOCKED -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }
}

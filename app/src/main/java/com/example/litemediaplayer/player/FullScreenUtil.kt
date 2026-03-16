package com.example.litemediaplayer.player

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.litemediaplayer.core.AppLogger
import com.example.litemediaplayer.settings.PlayerRotation

data class FullScreenRestoreState(
    val requestedOrientation: Int,
    val cutoutMode: Int
)

object FullScreenUtil {
    fun enter(
        activity: ComponentActivity,
        playerRotation: PlayerRotation
    ): FullScreenRestoreState {
        val window = activity.window
        val decorView = window.decorView

        val previousOrientation = activity.requestedOrientation
        val previousCutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode
        } else {
            0
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        when (playerRotation) {
            PlayerRotation.FORCE_LANDSCAPE -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            PlayerRotation.FORCE_PORTRAIT -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            PlayerRotation.FOLLOW_GLOBAL -> Unit
        }

        AppLogger.d(
            "PlayerRotation",
            "enter rotation=$playerRotation requestedOrientation=${activity.requestedOrientation}"
        )

        return FullScreenRestoreState(
            requestedOrientation = previousOrientation,
            cutoutMode = previousCutout
        )
    }

    fun exit(activity: ComponentActivity, restoreState: FullScreenRestoreState) {
        val window = activity.window
        val decorView = window.decorView

        WindowCompat.getInsetsController(window, decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)

        activity.requestedOrientation = restoreState.requestedOrientation

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = restoreState.cutoutMode
            window.attributes = params
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        AppLogger.d(
            "PlayerRotation",
            "exit restoreRequestedOrientation=${restoreState.requestedOrientation}"
        )
    }
}

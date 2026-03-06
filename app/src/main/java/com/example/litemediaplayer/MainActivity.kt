package com.example.litemediaplayer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.litemediaplayer.navigation.AppNavigation
import com.example.litemediaplayer.settings.AppSettingsStore
import com.example.litemediaplayer.settings.AppSettingsState
import com.example.litemediaplayer.settings.LocaleHelper
import com.example.litemediaplayer.settings.OrientationController
import com.example.litemediaplayer.settings.ThemeMode
import com.example.litemediaplayer.ui.theme.LiteMediaTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var appSettingsStore: AppSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OrientationController(
            activity = this,
            appSettingsStore = appSettingsStore
        ).observe()

        enableEdgeToEdge()
        setContent {
            MainRoot(appSettingsStore = appSettingsStore)
        }
    }
}

@Composable
private fun MainRoot(appSettingsStore: AppSettingsStore) {
    val appSettings by appSettingsStore.settingsFlow.collectAsStateWithLifecycle(
        initialValue = AppSettingsState()
    )
    val context = LocalContext.current
    val darkTheme = when (appSettings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    LaunchedEffect(appSettings.language) {
        LocaleHelper.applyLanguage(context, appSettings.language)
    }

    LiteMediaTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavigation()
        }
    }
}

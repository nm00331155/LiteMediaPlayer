package com.example.litemediaplayer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.litemediaplayer.data.LockConfigDao
import com.example.litemediaplayer.navigation.AppNavigation
import com.example.litemediaplayer.lock.LockAuthMethod
import com.example.litemediaplayer.settings.AppSettingsStore
import com.example.litemediaplayer.settings.AppSettingsState
import com.example.litemediaplayer.settings.LocaleHelper
import com.example.litemediaplayer.settings.OrientationController
import com.example.litemediaplayer.settings.ThemeMode
import com.example.litemediaplayer.ui.theme.LiteMediaTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var appSettingsStore: AppSettingsStore

    @Inject
    lateinit var lockConfigDao: LockConfigDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            runBlocking {
                val settings = appSettingsStore.settingsFlow.first()

                if (settings.lockAuthMethod == LockAuthMethod.FACE.name) {
                    appSettingsStore.updateLockAuthMethod(LockAuthMethod.BIOMETRIC.name)
                }

                lockConfigDao.findAll()
                    .filter { it.authMethod == LockAuthMethod.FACE.name }
                    .forEach { config ->
                        lockConfigDao.upsert(
                            config.copy(authMethod = LockAuthMethod.BIOMETRIC.name)
                        )
                    }

                if (savedInstanceState == null) {
                    LocaleHelper.applyLanguageIfNeeded(this@MainActivity, settings.language)
                }
            }
        }

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
    val darkTheme = when (appSettings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    LiteMediaTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavigation(appSettingsStore = appSettingsStore)
        }
    }
}

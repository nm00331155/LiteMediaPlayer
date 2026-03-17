package com.example.litemediaplayer.comic

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.comicProgressSyncDataStore by preferencesDataStore(name = "comic_progress_sync")

data class ComicProgressSyncSettings(
    val localDeviceId: String = "",
    val localDeviceName: String = defaultComicSyncDeviceName()
)

@Singleton
class ComicProgressSyncStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settingsFlow: Flow<ComicProgressSyncSettings> = context.comicProgressSyncDataStore.data
        .map { prefs ->
            ComicProgressSyncSettings(
                localDeviceId = prefs[SyncKeys.LOCAL_DEVICE_ID].orEmpty(),
                localDeviceName = prefs[SyncKeys.LOCAL_DEVICE_NAME]
                    ?.takeIf { it.isNotBlank() }
                    ?: defaultComicSyncDeviceName()
            )
        }

    suspend fun ensureInitialized() {
        context.comicProgressSyncDataStore.edit { prefs ->
            if (prefs[SyncKeys.LOCAL_DEVICE_ID].isNullOrBlank()) {
                prefs[SyncKeys.LOCAL_DEVICE_ID] = UUID.randomUUID().toString()
            }
            if (prefs[SyncKeys.LOCAL_DEVICE_NAME].isNullOrBlank()) {
                prefs[SyncKeys.LOCAL_DEVICE_NAME] = defaultComicSyncDeviceName()
            }
        }
    }

    suspend fun updateLocalDeviceName(name: String) {
        val resolved = name.trim().ifBlank { defaultComicSyncDeviceName() }
        context.comicProgressSyncDataStore.edit { prefs ->
            prefs[SyncKeys.LOCAL_DEVICE_NAME] = resolved
        }
    }
}

private object SyncKeys {
    val LOCAL_DEVICE_ID: Preferences.Key<String> = stringPreferencesKey("local_device_id")
    val LOCAL_DEVICE_NAME: Preferences.Key<String> = stringPreferencesKey("local_device_name")
}

private fun defaultComicSyncDeviceName(): String {
    return listOf(Build.MANUFACTURER, Build.MODEL)
        .mapNotNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
        .distinct()
        .joinToString(" ")
        .ifBlank { "Android端末" }
}

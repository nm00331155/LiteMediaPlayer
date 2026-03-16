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
import org.json.JSONArray
import org.json.JSONObject

private val Context.comicProgressSyncDataStore by preferencesDataStore(name = "comic_progress_sync")

data class ComicSyncDevice(
    val deviceId: String,
    val name: String,
    val host: String,
    val port: Int
)

data class ComicProgressSyncSettings(
    val localDeviceId: String = "",
    val localDeviceName: String = defaultComicSyncDeviceName(),
    val registeredDevices: List<ComicSyncDevice> = emptyList()
)

data class ComicSyncLocalEndpoint(
    val deviceId: String,
    val deviceName: String,
    val host: String?,
    val port: Int
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
                    ?: defaultComicSyncDeviceName(),
                registeredDevices = prefs[SyncKeys.REGISTERED_DEVICES]
                    ?.toComicSyncDevices()
                    .orEmpty()
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
            if (prefs[SyncKeys.REGISTERED_DEVICES].isNullOrBlank()) {
                prefs[SyncKeys.REGISTERED_DEVICES] = "[]"
            }
        }
    }

    suspend fun updateLocalDeviceName(name: String) {
        val resolved = name.trim().ifBlank { defaultComicSyncDeviceName() }
        context.comicProgressSyncDataStore.edit { prefs ->
            prefs[SyncKeys.LOCAL_DEVICE_NAME] = resolved
        }
    }

    suspend fun upsertRegisteredDevice(device: ComicSyncDevice) {
        context.comicProgressSyncDataStore.edit { prefs ->
            val current = prefs[SyncKeys.REGISTERED_DEVICES]
                ?.toComicSyncDevices()
                .orEmpty()
                .filterNot { it.deviceId == device.deviceId }

            prefs[SyncKeys.REGISTERED_DEVICES] = (current + device).toJsonString()
        }
    }

    suspend fun removeRegisteredDevice(deviceId: String) {
        context.comicProgressSyncDataStore.edit { prefs ->
            val updated = prefs[SyncKeys.REGISTERED_DEVICES]
                ?.toComicSyncDevices()
                .orEmpty()
                .filterNot { it.deviceId == deviceId }

            prefs[SyncKeys.REGISTERED_DEVICES] = updated.toJsonString()
        }
    }
}

private object SyncKeys {
    val LOCAL_DEVICE_ID: Preferences.Key<String> = stringPreferencesKey("local_device_id")
    val LOCAL_DEVICE_NAME: Preferences.Key<String> = stringPreferencesKey("local_device_name")
    val REGISTERED_DEVICES: Preferences.Key<String> = stringPreferencesKey("registered_devices")
}

private fun defaultComicSyncDeviceName(): String {
    return listOf(Build.MANUFACTURER, Build.MODEL)
        .mapNotNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
        .distinct()
        .joinToString(" ")
        .ifBlank { "Android端末" }
}

private fun String.toComicSyncDevices(): List<ComicSyncDevice> {
    return runCatching {
        val array = JSONArray(this)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val deviceId = item.optString("deviceId").trim()
                val name = item.optString("name").trim()
                val host = item.optString("host").trim()
                val port = item.optInt("port", ComicProgressLanSyncManager.DEFAULT_HTTP_PORT)
                if (deviceId.isBlank() || name.isBlank() || host.isBlank() || port <= 0) {
                    continue
                }
                add(
                    ComicSyncDevice(
                        deviceId = deviceId,
                        name = name,
                        host = host,
                        port = port
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun List<ComicSyncDevice>.toJsonString(): String {
    return JSONArray().apply {
        this@toJsonString.forEach { device ->
            put(
                JSONObject().apply {
                    put("deviceId", device.deviceId)
                    put("name", device.name)
                    put("host", device.host)
                    put("port", device.port)
                }
            )
        }
    }.toString()
}
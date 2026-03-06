package com.example.litemediaplayer.player

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litemediaplayer.data.LockConfig
import com.example.litemediaplayer.data.LockConfigDao
import com.example.litemediaplayer.data.VideoFolder
import com.example.litemediaplayer.data.VideoFolderDao
import com.example.litemediaplayer.lock.LockManager
import com.example.litemediaplayer.lock.LockTargetType
import com.example.litemediaplayer.settings.AppSettingsState
import com.example.litemediaplayer.settings.AppSettingsStore
import com.example.litemediaplayer.settings.PlayerResizeMode
import com.example.litemediaplayer.settings.PlayerRotation
import com.example.litemediaplayer.settings.ResumeBehavior
import com.example.litemediaplayer.settings.Sensitivity
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

private val SUPPORTED_VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "webm", "mov", "flv", "wmv", "m4v", "3gp"
)

data class VideoItem(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val durationMs: Long,
    val resolution: String?,
    val dateModified: Long
)

data class VideoFolderUi(
    val id: Long,
    val displayName: String,
    val treeUri: String,
    val videos: List<VideoItem>,
    val isLocked: Boolean,
    val isLockEnabled: Boolean,
    val isHidden: Boolean
) {
    val videoCount: Int
        get() = videos.size
}

data class PlayerUiState(
    val folders: List<VideoFolderUi> = emptyList(),
    val seekIntervalSeconds: Int = 10,
    val volumeSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val brightnessSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val defaultPlaybackSpeed: Float = 1f,
    val playerRotation: PlayerRotation = PlayerRotation.FORCE_LANDSCAPE,
    val playerResizeMode: PlayerResizeMode = PlayerResizeMode.FIT,
    val resumeBehavior: ResumeBehavior = ResumeBehavior.CONTINUE_FROM_LAST,
    val errorMessage: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoFolderDao: VideoFolderDao,
    private val lockConfigDao: LockConfigDao,
    private val lockManager: LockManager,
    private val appSettingsStore: AppSettingsStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlayerUiState> = combine(
        videoFolderDao.observeAll(),
        lockConfigDao.getConfigsByType(LockTargetType.VIDEO_FOLDER.name),
        appSettingsStore.settingsFlow,
        errorMessage
    ) { folders, lockConfigs, settings, error ->
        PlayerUiState(
            folders = buildFolderUiState(
                folders = folders,
                lockConfigs = lockConfigs,
                showHiddenLocked = settings.hiddenLockContentVisible
            ),
            seekIntervalSeconds = settings.seekIntervalSeconds,
            volumeSensitivity = settings.volumeSensitivity,
            brightnessSensitivity = settings.brightnessSensitivity,
            defaultPlaybackSpeed = settings.defaultPlaybackSpeed,
            playerRotation = settings.playerRotation,
            playerResizeMode = settings.playerResizeMode,
            resumeBehavior = settings.resumeBehavior,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerUiState()
    )

    fun addVideoFolder(folderUri: Uri, resolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                resolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                val treeUri = folderUri.toString()
                if (videoFolderDao.countByTreeUri(treeUri) > 0) {
                    errorMessage.value = "同じフォルダは既に登録されています"
                    return@launch
                }

                val name = DocumentFile.fromTreeUri(context, folderUri)
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: "動画フォルダ"

                videoFolderDao.insert(
                    VideoFolder(
                        treeUri = treeUri,
                        displayName = name
                    )
                )
            }.onFailure {
                errorMessage.value = "フォルダ登録に失敗しました"
            }
        }
    }

    fun deleteFolder(folder: VideoFolderUi) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder.treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            runCatching {
                videoFolderDao.deleteById(folder.id)
                lockConfigDao.findByTarget(
                    targetType = LockTargetType.VIDEO_FOLDER.name,
                    targetId = folder.id
                )?.let { lockConfigDao.delete(it) }
            }.onFailure {
                errorMessage.value = "フォルダ登録の解除に失敗しました"
            }
        }
    }

    fun setFolderLockEnabled(folderId: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = lockConfigDao.findByTarget(
                targetType = LockTargetType.VIDEO_FOLDER.name,
                targetId = folderId
            )

            if (existing != null) {
                lockConfigDao.upsert(existing.copy(isEnabled = enabled))
                return@launch
            }

            if (!enabled) {
                return@launch
            }

            val globalLock = lockConfigDao.findByTarget(
                targetType = LockTargetType.APP_GLOBAL.name,
                targetId = null
            )

            if (globalLock == null) {
                errorMessage.value = "先に設定タブでグローバルロックを設定してください"
                return@launch
            }

            lockConfigDao.upsert(
                LockConfig(
                    targetType = LockTargetType.VIDEO_FOLDER.name,
                    targetId = folderId,
                    authMethod = globalLock.authMethod,
                    pinHash = globalLock.pinHash,
                    patternHash = globalLock.patternHash,
                    autoLockMinutes = globalLock.autoLockMinutes,
                    isHidden = globalLock.isHidden,
                    isEnabled = true
                )
            )
        }
    }

    suspend fun isFolderCurrentlyLocked(folderId: Long): Boolean {
        return lockManager.isLocked(LockTargetType.VIDEO_FOLDER, folderId)
    }

    fun clearError() {
        errorMessage.value = null
    }

    fun updateSeekInterval(seconds: Int) {
        viewModelScope.launch {
            appSettingsStore.updateSeekInterval(seconds)
        }
    }

    fun updateVolumeSensitivity(value: Sensitivity) {
        viewModelScope.launch {
            appSettingsStore.updateVolumeSensitivity(value)
        }
    }

    fun updateBrightnessSensitivity(value: Sensitivity) {
        viewModelScope.launch {
            appSettingsStore.updateBrightnessSensitivity(value)
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            appSettingsStore.updateDefaultPlaybackSpeed(speed)
        }
    }

    fun updateResumeBehavior(value: ResumeBehavior) {
        viewModelScope.launch {
            appSettingsStore.updateResumeBehavior(value)
        }
    }

    fun updatePlayerRotation(value: PlayerRotation) {
        viewModelScope.launch {
            appSettingsStore.updatePlayerRotation(value)
        }
    }

    private suspend fun buildFolderUiState(
        folders: List<VideoFolder>,
        lockConfigs: List<com.example.litemediaplayer.data.LockConfig>,
        showHiddenLocked: Boolean
    ): List<VideoFolderUi> {
        return folders.mapNotNull { folder ->
            val lockConfig = lockConfigs.firstOrNull { it.targetId == folder.id }
            val lockEnabled = lockConfig?.isEnabled == true
            val isLocked = if (lockEnabled) {
                lockManager.isLocked(LockTargetType.VIDEO_FOLDER, folder.id)
            } else {
                false
            }
            val isHidden = lockConfig?.isHidden == true

            if (isHidden && isLocked && !showHiddenLocked) {
                return@mapNotNull null
            }

            VideoFolderUi(
                id = folder.id,
                displayName = folder.displayName,
                treeUri = folder.treeUri,
                videos = resolveVideoItems(folder.treeUri),
                isLocked = isLocked,
                isLockEnabled = lockEnabled,
                isHidden = isHidden
            )
        }
    }

    private fun resolveVideoItems(treeUriString: String): List<VideoItem> {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return emptyList()

        return root
            .listFiles()
            .asSequence()
            .filter { it.isFile }
            .filter { document ->
                val ext = document.name
                    ?.substringAfterLast('.', "")
                    ?.lowercase()
                    ?: ""
                ext in SUPPORTED_VIDEO_EXTENSIONS
            }
            .map { document ->
                val metadata = resolveVideoMetadata(document.uri)
                VideoItem(
                    uri = document.uri,
                    displayName = document.name ?: "video",
                    size = document.length().coerceAtLeast(0L),
                    durationMs = metadata.first,
                    resolution = metadata.second,
                    dateModified = document.lastModified().coerceAtLeast(0L)
                )
            }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    private fun resolveVideoMetadata(uri: Uri): Pair<Long, String?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L

            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()

            val resolution = if (width != null && height != null && width > 0 && height > 0) {
                "${width}x${height}"
            } else {
                null
            }

            duration to resolution
        } catch (_: Exception) {
            0L to null
        } finally {
            runCatching { retriever.release() }
        }
    }

    val appSettings: StateFlow<AppSettingsState> = appSettingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettingsState()
    )
}

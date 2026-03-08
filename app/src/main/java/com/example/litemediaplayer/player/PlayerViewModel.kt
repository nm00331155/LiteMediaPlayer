package com.example.litemediaplayer.player

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litemediaplayer.core.AppLogger
import com.example.litemediaplayer.data.LockConfigDao
import com.example.litemediaplayer.data.VideoFolder
import com.example.litemediaplayer.data.VideoFolderDao
import com.example.litemediaplayer.lock.LockManager
import com.example.litemediaplayer.lock.LockTargetType
import com.example.litemediaplayer.settings.AppSettingsStore
import com.example.litemediaplayer.settings.PlayerResizeMode
import com.example.litemediaplayer.settings.PlayerRotation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val SUPPORTED_VIDEO_EXTENSIONS = setOf(
    "mp4",
    "mkv",
    "webm",
    "avi",
    "mov",
    "m4v",
    "ts",
    "flv",
    "3gp"
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
    val selectedFolderId: Long? = null,
    val videos: List<VideoItem> = emptyList(),
    val selectedVideoUri: Uri? = null,
    val deleteConfirmTarget: VideoFolderUi? = null,
    val seekIntervalSeconds: Int = 10,
    val playerRotation: PlayerRotation = PlayerRotation.FORCE_LANDSCAPE,
    val playerResizeMode: PlayerResizeMode = PlayerResizeMode.FIT,
    val subtitleAutoLoad: Boolean = true,
    val isPlayerPanelLocked: Boolean = false,
    val isPlayerOrientationLocked: Boolean = false,
    val gestureSeekEnabled: Boolean = true,
    val gestureVolumeEnabled: Boolean = true,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureDoubleTapPlayPause: Boolean = true,
    val gestureBrightnessZoneEnd: Float = 0.3f,
    val gestureVolumeZoneStart: Float = 0.7f,
    val errorMessage: String? = null
)

private data class UiSelectionState(
    val selectedFolderId: Long?,
    val selectedVideoUri: Uri?,
    val deleteConfirmTarget: VideoFolderUi?,
    val errorMessage: String?
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val folderDao: VideoFolderDao,
    private val lockConfigDao: LockConfigDao,
    private val lockManager: LockManager,
    private val appSettingsStore: AppSettingsStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val selectedFolderId = MutableStateFlow<Long?>(null)
    private val selectedVideoUri = MutableStateFlow<Uri?>(null)
    private val deleteConfirmTarget = MutableStateFlow<VideoFolderUi?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val refreshTick = MutableStateFlow(0L)
    private val videoItemsCache = ConcurrentHashMap<String, List<VideoItem>>()
    private val metadataCache = ConcurrentHashMap<Uri, Pair<Long, String?>>()
    private val metadataLoadingFolders = ConcurrentHashMap<String, Boolean>()
    private val scanningFolders = ConcurrentHashMap<String, Boolean>()

    private val selectionState = combine(
        selectedFolderId,
        selectedVideoUri,
        deleteConfirmTarget,
        errorMessage
    ) { selectedId, selectedUri, deleteTarget, error ->
        UiSelectionState(
            selectedFolderId = selectedId,
            selectedVideoUri = selectedUri,
            deleteConfirmTarget = deleteTarget,
            errorMessage = error
        )
    }

    private val folderUiFlow: StateFlow<List<VideoFolderUi>> = combine(
        folderDao.observeAll(),
        lockConfigDao.getConfigsByType(LockTargetType.VIDEO_FOLDER.name),
        appSettingsStore.settingsFlow,
        refreshTick
    ) { folders, lockConfigs, settings, _ ->
        buildFolderShells(
            folders = folders,
            showHiddenLocked = settings.hiddenLockContentVisible,
            lockConfigs = lockConfigs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<PlayerUiState> = combine(
        folderUiFlow,
        appSettingsStore.settingsFlow,
        selectionState
    ) { folders, settings, selection ->
        val resolvedFolderId = when {
            selection.selectedFolderId != null &&
                folders.any { it.id == selection.selectedFolderId } -> selection.selectedFolderId

            else -> folders.firstOrNull()?.id
        }

        val selectedVideos = folders
            .firstOrNull { it.id == resolvedFolderId }
            ?.videos
            .orEmpty()

        PlayerUiState(
            folders = folders,
            selectedFolderId = resolvedFolderId,
            videos = selectedVideos,
            selectedVideoUri = selection.selectedVideoUri,
            deleteConfirmTarget = selection.deleteConfirmTarget,
            seekIntervalSeconds = settings.seekIntervalSeconds,
            playerRotation = settings.playerRotation,
            playerResizeMode = settings.playerResizeMode,
            subtitleAutoLoad = settings.subtitleAutoLoad,
            isPlayerPanelLocked = settings.playerPanelLocked,
            isPlayerOrientationLocked = settings.playerOrientationLocked,
            gestureSeekEnabled = settings.gestureSeekEnabled,
            gestureVolumeEnabled = settings.gestureVolumeEnabled,
            gestureBrightnessEnabled = settings.gestureBrightnessEnabled,
            gestureDoubleTapPlayPause = settings.gestureDoubleTapPlayPause,
            gestureBrightnessZoneEnd = settings.gestureBrightnessZoneEnd,
            gestureVolumeZoneStart = settings.gestureVolumeZoneStart,
            errorMessage = selection.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerUiState()
    )

    init {
        viewModelScope.launch {
            selectedFolderId.collect { folderId ->
                if (folderId == null) {
                    return@collect
                }

                val folder = folderDao.findById(folderId) ?: return@collect
                if (!videoItemsCache.containsKey(folder.treeUri)) {
                    scanFolderAsync(folder)
                }
            }
        }

        viewModelScope.launch {
            folderDao.observeAll().collect { folders ->
                if (folders.isEmpty()) {
                    selectedFolderId.value = null
                    return@collect
                }

                val selectedId = selectedFolderId.value
                val hasSelected = selectedId != null && folders.any { it.id == selectedId }
                if (!hasSelected) {
                    selectedFolderId.value = folders.first().id
                }
            }
        }
    }

    fun addVideoFolder(folderUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                if (folderDao.countByTreeUri(folderUri.toString()) > 0) {
                    errorMessage.value = "同じフォルダは既に登録されています"
                    return@launch
                }

                val displayName = DocumentFile.fromTreeUri(context, folderUri)
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: "動画フォルダ"

                val newFolder = VideoFolder(
                    treeUri = folderUri.toString(),
                    displayName = displayName
                )
                val newId = folderDao.insert(newFolder)
                AppLogger.i("PlayerVM", "Added video folder: $displayName (id=$newId)")

                val inserted = folderDao.findById(newId)
                if (inserted != null) {
                    scanFolderAsync(inserted)
                }

                selectedFolderId.value = newId
                refresh()
            }.onFailure { error ->
                AppLogger.e("PlayerVM", "Failed to add folder: $folderUri", error)
                errorMessage.value = "フォルダ追加に失敗しました"
            }
        }
    }

    fun selectFolder(folderId: Long) {
        selectedFolderId.value = folderId
    }

    fun selectVideo(videoUri: Uri) {
        selectedVideoUri.value = videoUri
    }

    fun clearSelectedVideo() {
        selectedVideoUri.value = null
    }

    fun requestFolderUnlock(folderId: Long): Boolean {
        val folder = uiState.value.folders.firstOrNull { it.id == folderId } ?: return true
        if (!folder.isLocked) {
            return true
        }

        errorMessage.value = "このフォルダはロック中です。設定タブで解除してください"
        return false
    }

    fun showDeleteConfirm(folder: VideoFolderUi) {
        deleteConfirmTarget.value = folder
    }

    fun dismissDeleteConfirm() {
        deleteConfirmTarget.value = null
    }

    fun deleteFolder(folder: VideoFolderUi) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(folder.treeUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                runCatching {
                    lockConfigDao.findByTarget(LockTargetType.VIDEO_FOLDER.name, folder.id)
                        ?.let { lockConfigDao.delete(it) }
                }

                folderDao.deleteById(folder.id)

                videoItemsCache.remove(folder.treeUri)
                deleteConfirmTarget.value = null

                if (selectedFolderId.value == folder.id) {
                    selectedFolderId.value = null
                }

                AppLogger.i("PlayerVM", "Removed video folder: ${folder.displayName}")
                refresh()
            } catch (error: Exception) {
                AppLogger.e("PlayerVM", "deleteFolder failed", error)
                errorMessage.value = "フォルダ削除に失敗しました: ${error.message}"
                deleteConfirmTarget.value = null
            }
        }
    }

    fun toggleHiddenFolderVisibility(show: Boolean) {
        viewModelScope.launch {
            appSettingsStore.updateHiddenLockContentVisible(show)
        }
    }

    fun syncFolder(folder: VideoFolderUi) {
        videoItemsCache.remove(folder.treeUri)
        AppLogger.d("PlayerVM", "Force sync: ${folder.displayName}")

        if (selectedFolderId.value != folder.id) {
            selectedFolderId.value = folder.id
        }

        viewModelScope.launch(Dispatchers.IO) {
            val target = folderDao.findById(folder.id) ?: return@launch
            scanFolderAsync(target)
        }
    }

    fun consumeError() {
        errorMessage.value = null
    }

    fun setSeekInterval(seconds: Int) {
        viewModelScope.launch {
            appSettingsStore.updateSeekInterval(seconds)
        }
    }

    fun setPlayerResizeMode(mode: PlayerResizeMode) {
        viewModelScope.launch {
            appSettingsStore.updatePlayerResizeMode(mode)
        }
    }

    fun setPlayerRotation(rotation: PlayerRotation) {
        viewModelScope.launch {
            appSettingsStore.updatePlayerRotation(rotation)
        }
    }

    fun toggleSubtitleAutoLoad() {
        val next = !uiState.value.subtitleAutoLoad
        viewModelScope.launch {
            appSettingsStore.updateSubtitleAutoLoad(next)
        }
    }

    fun togglePlayerPanelLock() {
        val next = !uiState.value.isPlayerPanelLocked
        viewModelScope.launch {
            appSettingsStore.updatePlayerPanelLocked(next)
        }
    }

    fun togglePlayerOrientationLock() {
        val next = !uiState.value.isPlayerOrientationLocked
        viewModelScope.launch {
            appSettingsStore.updatePlayerOrientationLocked(next)
        }
    }

    fun updateGestureSeekEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsStore.updateGestureSeekEnabled(enabled)
        }
    }

    fun updateGestureVolumeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsStore.updateGestureVolumeEnabled(enabled)
        }
    }

    fun updateGestureBrightnessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsStore.updateGestureBrightnessEnabled(enabled)
        }
    }

    fun updateGestureDoubleTapPlayPause(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsStore.updateGestureDoubleTapPlayPause(enabled)
        }
    }

    fun updateGestureBrightnessZoneEnd(value: Float) {
        viewModelScope.launch {
            appSettingsStore.updateGestureBrightnessZoneEnd(value)
        }
    }

    fun updateGestureVolumeZoneStart(value: Float) {
        viewModelScope.launch {
            appSettingsStore.updateGestureVolumeZoneStart(value)
        }
    }

    fun clearAllFolderLocks() {
        lockManager.clearUnlockRecordsForType(LockTargetType.VIDEO_FOLDER)
        errorMessage.value = "フォルダの解除状態をリセットしました"
        refresh()
    }

    private suspend fun buildFolderShells(
        folders: List<VideoFolder>,
        showHiddenLocked: Boolean,
        lockConfigs: List<com.example.litemediaplayer.data.LockConfig>
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

            val cachedVideos = videoItemsCache[folder.treeUri]
                ?.map { item ->
                    val cachedMetadata = metadataCache[item.uri]
                    if (cachedMetadata == null) {
                        item
                    } else {
                        item.copy(
                            durationMs = cachedMetadata.first,
                            resolution = cachedMetadata.second
                        )
                    }
                }
                .orEmpty()

            VideoFolderUi(
                id = folder.id,
                displayName = folder.displayName,
                treeUri = folder.treeUri,
                videos = cachedVideos,
                isLocked = isLocked,
                isLockEnabled = lockEnabled,
                isHidden = isHidden
            )
        }
    }

    private fun scanFolderAsync(folder: VideoFolder) {
        if (scanningFolders.putIfAbsent(folder.treeUri, true) != null) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val scanStart = System.currentTimeMillis()
            try {
                AppLogger.d("PlayerVM", "Async scan start: ${folder.displayName}")
                resolveVideoItems(folder.treeUri)
                val elapsed = System.currentTimeMillis() - scanStart
                AppLogger.d("PlayerVM", "Async scan done: ${folder.displayName} (${elapsed}ms)")
                refresh()
                loadMetadataForFolder(folder.treeUri)
            } catch (error: Exception) {
                AppLogger.e("PlayerVM", "Async scan failed: ${folder.displayName}", error)
            } finally {
                scanningFolders.remove(folder.treeUri)
            }
        }
    }

    private fun loadMetadataForFolder(treeUri: String) {
        if (metadataLoadingFolders.putIfAbsent(treeUri, true) != null) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var hasUpdates = false
                val items = videoItemsCache[treeUri].orEmpty()
                items.forEach { item ->
                    if (!metadataCache.containsKey(item.uri)) {
                        val start = System.currentTimeMillis()
                        metadataCache[item.uri] = resolveVideoMetadata(item.uri)
                        val elapsed = System.currentTimeMillis() - start
                        AppLogger.d(
                            "PlayerVM",
                            "Metadata loaded: ${item.displayName} (${elapsed}ms)"
                        )
                        hasUpdates = true
                    }
                }

                if (hasUpdates) {
                    val currentItems = videoItemsCache[treeUri].orEmpty()
                    videoItemsCache[treeUri] = currentItems.map { item ->
                        val metadata = metadataCache[item.uri]
                        if (metadata == null) {
                            item
                        } else {
                            item.copy(durationMs = metadata.first, resolution = metadata.second)
                        }
                    }
                    refresh()
                }
            } finally {
                metadataLoadingFolders.remove(treeUri)
            }
        }
    }

    private fun resolveVideoItems(treeUriString: String): List<VideoItem> {
        videoItemsCache[treeUriString]?.let { return it }

        AppLogger.d("PlayerVM", "Scanning folder: $treeUriString")
        val scanStart = System.currentTimeMillis()
        val treeUri = Uri.parse(treeUriString)
        val treeDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrElse {
            AppLogger.w("PlayerVM", "Invalid tree uri: $treeUriString", it)
            return emptyList()
        }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val queue = ArrayDeque<Uri>()
        queue.add(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId))
        val videos = mutableListOf<VideoItem>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            runCatching {
                context.contentResolver.query(current, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val docId = if (idCol >= 0 && !cursor.isNull(idCol)) {
                            cursor.getString(idCol)
                        } else {
                            continue
                        }

                        val name = if (nameCol >= 0 && !cursor.isNull(nameCol)) {
                            cursor.getString(nameCol)
                        } else {
                            ""
                        }

                        val mimeType = if (mimeCol >= 0 && !cursor.isNull(mimeCol)) {
                            cursor.getString(mimeCol)
                        } else {
                            ""
                        }

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(
                                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                            )
                        } else if (isSupportedVideoName(name)) {
                            val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) {
                                cursor.getLong(sizeCol)
                            } else {
                                0L
                            }

                            val modified = if (modifiedCol >= 0 && !cursor.isNull(modifiedCol)) {
                                cursor.getLong(modifiedCol)
                            } else {
                                0L
                            }

                            videos += VideoItem(
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                                displayName = name.ifBlank { "video" },
                                size = size.coerceAtLeast(0L),
                                durationMs = 0L,
                                resolution = null,
                                dateModified = modified.coerceAtLeast(0L)
                            )
                        }
                    }
                }
            }.onFailure { error ->
                AppLogger.w("PlayerVM", "Folder query failed: $current", error)
            }
        }

        val scanned = videos.sortedBy { it.displayName.lowercase() }

        val elapsed = System.currentTimeMillis() - scanStart
        AppLogger.d(
            "PlayerVM",
            "Folder scan complete: ${scanned.size} files (${elapsed}ms)"
        )
        videoItemsCache[treeUriString] = scanned
        return scanned
    }

    private fun isSupportedVideoName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_VIDEO_EXTENSIONS
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
        } catch (error: Exception) {
            AppLogger.w("PlayerVM", "Failed to read metadata: $uri", error)
            0L to null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun refresh() {
        refreshTick.update { it + 1 }
    }
}

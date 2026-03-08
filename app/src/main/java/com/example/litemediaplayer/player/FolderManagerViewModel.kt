package com.example.litemediaplayer.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litemediaplayer.data.LockConfig
import com.example.litemediaplayer.data.LockConfigDao
import com.example.litemediaplayer.data.VideoFolder
import com.example.litemediaplayer.data.VideoFolderDao
import com.example.litemediaplayer.lock.LockTargetType
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

@HiltViewModel
class FolderManagerViewModel @Inject constructor(
    private val folderDao: VideoFolderDao,
    private val lockConfigDao: LockConfigDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class FolderState(
        val folder: VideoFolder,
        val isLocked: Boolean,
        val videoCount: Int
    )

    private val _deleteTarget = MutableStateFlow<VideoFolder?>(null)
    val deleteTarget: StateFlow<VideoFolder?> = _deleteTarget

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val folders: StateFlow<List<FolderState>> = combine(
        folderDao.observeAll(),
        lockConfigDao.getConfigsByType(LockTargetType.VIDEO_FOLDER.name)
    ) { folders, locks ->
        folders.map { folder ->
            val lockConfig = locks.find { it.targetId == folder.id }
            FolderState(
                folder = folder,
                isLocked = lockConfig?.isEnabled == true,
                videoCount = countVideosInFolder(folder)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addFolder(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                val displayName = DocumentFile.fromTreeUri(context, uri)
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: "動画フォルダ"

                if (folderDao.countByTreeUri(uri.toString()) > 0) {
                    _message.value = "同じフォルダは既に登録されています"
                    return@launch
                }

                folderDao.insert(
                    VideoFolder(
                        treeUri = uri.toString(),
                        displayName = displayName
                    )
                )
            }.onFailure {
                _message.value = "フォルダ追加に失敗しました"
            }
        }
    }

    fun showDeleteConfirm(folder: VideoFolder) {
        _deleteTarget.value = folder
    }

    fun dismissDeleteConfirm() {
        _deleteTarget.value = null
    }

    fun deleteFolder(folder: VideoFolder) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder.treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            runCatching {
                lockConfigDao.findByTarget(LockTargetType.VIDEO_FOLDER.name, folder.id)
                    ?.let { lockConfigDao.delete(it) }
                folderDao.deleteById(folder.id)
                _deleteTarget.value = null
            }.onFailure {
                _message.value = "フォルダ削除に失敗しました"
            }
        }
    }

    fun toggleLock(folder: VideoFolder) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = lockConfigDao.findByTarget(
                LockTargetType.VIDEO_FOLDER.name,
                folder.id
            )

            if (existing != null) {
                val enable = !existing.isEnabled
                lockConfigDao.upsert(
                    existing.copy(
                        isEnabled = enable,
                        isHidden = if (enable) true else existing.isHidden
                    )
                )
                return@launch
            }

            val global = lockConfigDao.findByTarget(
                LockTargetType.APP_GLOBAL.name,
                null
            )

            if (global == null) {
                _message.value = "先に設定タブでグローバルロックを設定してください"
                return@launch
            }

            lockConfigDao.upsert(
                LockConfig(
                    targetType = LockTargetType.VIDEO_FOLDER.name,
                    targetId = folder.id,
                    authMethod = global.authMethod,
                    pinHash = global.pinHash,
                    patternHash = global.patternHash,
                    autoLockMinutes = global.autoLockMinutes,
                    isHidden = true,
                    isEnabled = true
                )
            )
        }
    }

    fun openLockSettings() {
        _message.update { "ロック詳細は設定タブで変更してください" }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun countVideosInFolder(folder: VideoFolder): Int {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.treeUri)) ?: return 0
        return root.listFiles().count { file ->
            file.isFile && file.name
                ?.substringAfterLast('.', "")
                ?.lowercase() in SUPPORTED_VIDEO_EXTENSIONS
        }
    }
}

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

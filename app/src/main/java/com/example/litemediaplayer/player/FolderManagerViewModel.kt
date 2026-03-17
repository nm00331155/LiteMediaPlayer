package com.example.litemediaplayer.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litemediaplayer.core.AppLogger
import com.example.litemediaplayer.data.LockConfig
import com.example.litemediaplayer.data.LockConfigDao
import com.example.litemediaplayer.data.VideoFolder
import com.example.litemediaplayer.data.VideoFolderDao
import com.example.litemediaplayer.lock.LockAuthMethod
import com.example.litemediaplayer.lock.LockTargetType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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

    private val _videoCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    val folders: StateFlow<List<FolderState>> = combine(
        folderDao.observeAll(),
        lockConfigDao.getConfigsByType(LockTargetType.VIDEO_FOLDER.name),
        _videoCounts
    ) { folders, locks, videoCounts ->
        folders.map { folder ->
            val lockConfig = locks.find { it.targetId == folder.id }
            FolderState(
                folder = folder,
                isLocked = lockConfig?.isEnabled == true,
                videoCount = videoCounts[folder.id] ?: -1
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            folderDao.observeAll().collectLatest { folders ->
                refreshVideoCounts(folders)
            }
        }
    }

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
                        isHidden = true
                    )
                )
                return@launch
            }

            lockConfigDao.upsert(
                LockConfig(
                    targetType = LockTargetType.VIDEO_FOLDER.name,
                    targetId = folder.id,
                    authMethod = LockAuthMethod.PIN.name,
                    pinHash = null,
                    patternHash = null,
                    autoLockMinutes = 5,
                    isHidden = true,
                    isEnabled = true
                )
            )
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun countVideosInFolder(folder: VideoFolder): Int {
        val treeUri = Uri.parse(folder.treeUri)
        val treeDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrElse {
            AppLogger.w("FolderManager", "Invalid tree uri: ${folder.treeUri}", it)
            return 0
        }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val queue = ArrayDeque<Uri>()
        queue.add(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId))
        var count = 0

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            runCatching {
                context.contentResolver.query(current, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val documentId = if (idCol >= 0 && !cursor.isNull(idCol)) {
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
                                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
                            )
                        } else if (name.substringAfterLast('.', "").lowercase() in SUPPORTED_VIDEO_EXTENSIONS) {
                            count += 1
                        }
                    }
                }
            }.onFailure { error ->
                AppLogger.w("FolderManager", "Folder count query failed: $current", error)
            }
        }

        return count
    }

    private fun refreshVideoCounts(folders: List<VideoFolder>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (folders.isEmpty()) {
                _videoCounts.value = emptyMap()
                return@launch
            }

            val start = SystemClock.elapsedRealtime()
            val counts = coroutineScope {
                folders.map { folder ->
                    async {
                        folder.id to countVideosInFolder(folder)
                    }
                }.awaitAll().toMap()
            }
            _videoCounts.value = counts

            AppLogger.d(
                "FolderManager",
                "Folder counts refreshed in ${SystemClock.elapsedRealtime() - start}ms for ${folders.size} folders"
            )
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

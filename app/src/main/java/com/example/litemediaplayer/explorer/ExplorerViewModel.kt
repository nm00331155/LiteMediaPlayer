package com.example.litemediaplayer.explorer

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.litemediaplayer.core.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExplorerRootUi(
    val label: String,
    val path: String,
    val description: String
)

data class ExplorerItemUi(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

enum class ExplorerClipboardMode {
    COPY,
    MOVE
}

data class ExplorerClipboard(
    val sourcePath: String,
    val mode: ExplorerClipboardMode
)

data class ExplorerUiState(
    val hasAllFilesAccess: Boolean = true,
    val roots: List<ExplorerRootUi> = emptyList(),
    val currentPath: String? = null,
    val items: List<ExplorerItemUi> = emptyList(),
    val clipboard: ExplorerClipboard? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            reloadState(
                requestedPath = _uiState.value.currentPath,
                clipboard = _uiState.value.clipboard
            )
        }
    }

    fun openRoot(path: String) {
        openDirectory(path)
    }

    fun openDirectory(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            reloadState(
                requestedPath = path,
                clipboard = _uiState.value.clipboard
            )
        }
    }

    fun goUp() {
        val currentPath = _uiState.value.currentPath ?: return
        val parent = canonicalFile(File(currentPath)).parentFile?.absolutePath
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            reloadState(
                requestedPath = parent,
                clipboard = _uiState.value.clipboard
            )
        }
    }

    fun goHome() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            reloadState(
                requestedPath = null,
                clipboard = _uiState.value.clipboard
            )
        }
    }

    fun refreshCurrentDirectory() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            reloadState(
                requestedPath = _uiState.value.currentPath,
                clipboard = _uiState.value.clipboard
            )
        }
    }

    fun queueCopy(path: String) {
        val source = File(path)
        _uiState.update {
            it.copy(
                clipboard = ExplorerClipboard(path, ExplorerClipboardMode.COPY),
                statusMessage = "${source.name.ifBlank { path }} をコピー待ちにしました",
                errorMessage = null
            )
        }
    }

    fun queueMove(path: String) {
        val source = File(path)
        _uiState.update {
            it.copy(
                clipboard = ExplorerClipboard(path, ExplorerClipboardMode.MOVE),
                statusMessage = "${source.name.ifBlank { path }} を移動待ちにしました",
                errorMessage = null
            )
        }
    }

    fun clearClipboard() {
        _uiState.update {
            it.copy(
                clipboard = null,
                statusMessage = "クリップボードをクリアしました",
                errorMessage = null
            )
        }
    }

    fun pasteIntoCurrentDirectory() {
        val clipboard = _uiState.value.clipboard ?: return
        val destinationPath = _uiState.value.currentPath ?: return

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                requireAllFilesAccess()

                val source = canonicalFile(File(clipboard.sourcePath))
                val destinationDir = canonicalFile(File(destinationPath))
                if (!source.exists()) {
                    error("元のアイテムが見つかりません")
                }
                if (!destinationDir.exists() || !destinationDir.isDirectory) {
                    error("貼り付け先のフォルダにアクセスできません")
                }

                val target = File(destinationDir, source.name)
                if (target.exists()) {
                    error("同名のアイテムが既に存在します")
                }
                if (
                    source.isDirectory &&
                    destinationDir.absolutePath.startsWith(source.absolutePath + File.separator)
                ) {
                    error("フォルダ自身の中には貼り付けできません")
                }

                when (clipboard.mode) {
                    ExplorerClipboardMode.COPY -> copyPath(source, target)
                    ExplorerClipboardMode.MOVE -> movePath(source, target)
                }

                reloadState(
                    requestedPath = destinationDir.absolutePath,
                    clipboard = if (clipboard.mode == ExplorerClipboardMode.MOVE) {
                        null
                    } else {
                        clipboard
                    },
                    statusMessage = when (clipboard.mode) {
                        ExplorerClipboardMode.COPY -> "${source.name} をコピーしました"
                        ExplorerClipboardMode.MOVE -> "${source.name} を移動しました"
                    }
                )
            }.onFailure { error ->
                AppLogger.e("Explorer", "pasteIntoCurrentDirectory failed", error)
                reloadState(
                    requestedPath = _uiState.value.currentPath,
                    clipboard = clipboard,
                    errorMessage = error.message ?: "貼り付けに失敗しました"
                )
            }
        }
    }

    fun renameItem(path: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                requireAllFilesAccess()

                val source = canonicalFile(File(path))
                if (!source.exists()) {
                    error("対象が見つかりません")
                }

                val sanitizedName = newName.trim()
                if (sanitizedName.isBlank()) {
                    error("名前を入力してください")
                }
                if (sanitizedName.contains(File.separatorChar)) {
                    error("名前にフォルダ区切り文字は使えません")
                }

                val destination = File(source.parentFile, sanitizedName)
                if (destination.exists()) {
                    error("同名のアイテムが既に存在します")
                }
                if (!source.renameTo(destination)) {
                    error("名前変更に失敗しました")
                }

                val updatedClipboard = _uiState.value.clipboard?.let { clipboard ->
                    if (clipboard.sourcePath == source.absolutePath) {
                        clipboard.copy(sourcePath = destination.absolutePath)
                    } else {
                        clipboard
                    }
                }

                reloadState(
                    requestedPath = destination.parentFile?.absolutePath,
                    clipboard = updatedClipboard,
                    statusMessage = "${source.name} の名前を変更しました"
                )
            }.onFailure { error ->
                AppLogger.e("Explorer", "renameItem failed", error)
                reloadState(
                    requestedPath = _uiState.value.currentPath,
                    clipboard = _uiState.value.clipboard,
                    errorMessage = error.message ?: "名前変更に失敗しました"
                )
            }
        }
    }

    fun deleteItem(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                requireAllFilesAccess()

                val source = canonicalFile(File(path))
                if (!source.exists()) {
                    error("対象が見つかりません")
                }
                if (!source.deleteRecursively()) {
                    error("削除に失敗しました")
                }

                val updatedClipboard = _uiState.value.clipboard?.takeUnless {
                    it.sourcePath == source.absolutePath ||
                        it.sourcePath.startsWith(source.absolutePath + File.separator)
                }

                reloadState(
                    requestedPath = source.parentFile?.absolutePath,
                    clipboard = updatedClipboard,
                    statusMessage = "${source.name} を削除しました"
                )
            }.onFailure { error ->
                AppLogger.e("Explorer", "deleteItem failed", error)
                reloadState(
                    requestedPath = _uiState.value.currentPath,
                    clipboard = _uiState.value.clipboard,
                    errorMessage = error.message ?: "削除に失敗しました"
                )
            }
        }
    }

    fun consumeStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun reloadState(
        requestedPath: String?,
        clipboard: ExplorerClipboard?,
        statusMessage: String? = null,
        errorMessage: String? = null
    ) {
        val hasAccess = hasAllFilesAccess()
        val roots = if (hasAccess) resolveRoots() else emptyList()
        val resolvedPath = if (hasAccess) {
            requestedPath?.let { path -> resolveDirectoryPath(path) }
        } else {
            null
        }
        val items = if (hasAccess && resolvedPath != null) {
            listDirectoryItems(resolvedPath)
        } else {
            emptyList()
        }

        _uiState.update {
            it.copy(
                hasAllFilesAccess = hasAccess,
                roots = roots,
                currentPath = resolvedPath,
                items = items,
                clipboard = clipboard,
                isLoading = false,
                statusMessage = statusMessage,
                errorMessage = if (!hasAccess) {
                    errorMessage ?: "全ファイルアクセスを許可すると端末内のファイル管理が有効になります"
                } else {
                    errorMessage
                }
            )
        }
    }

    private fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun requireAllFilesAccess() {
        if (!hasAllFilesAccess()) {
            error("全ファイルアクセス権限が必要です")
        }
    }

    private fun resolveRoots(): List<ExplorerRootUi> {
        val rootsByPath = linkedMapOf<String, ExplorerRootUi>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            storageManager?.storageVolumes.orEmpty().forEach { volume ->
                val directory = volume.directory ?: return@forEach
                if (!directory.exists() || !directory.canRead()) {
                    return@forEach
                }

                val path = canonicalFile(directory).absolutePath
                rootsByPath[path] = ExplorerRootUi(
                    label = volume.getDescription(context).ifBlank { directory.name.ifBlank { path } },
                    path = path,
                    description = path
                )
            }
        }

        context.getExternalFilesDirs(null).orEmpty().forEach { externalDir ->
            val root = externalDir?.let(::resolveExternalRootFromAppDir) ?: return@forEach
            if (!root.exists() || !root.canRead()) {
                return@forEach
            }

            val path = canonicalFile(root).absolutePath
            rootsByPath.putIfAbsent(
                path,
                ExplorerRootUi(
                    label = if (rootsByPath.isEmpty()) {
                        "内部ストレージ"
                    } else {
                        root.name.ifBlank { path }
                    },
                    path = path,
                    description = path
                )
            )
        }

        val primaryRoot = canonicalFile(Environment.getExternalStorageDirectory())
        if (primaryRoot.exists() && primaryRoot.canRead()) {
            rootsByPath.putIfAbsent(
                primaryRoot.absolutePath,
                ExplorerRootUi(
                    label = "内部ストレージ",
                    path = primaryRoot.absolutePath,
                    description = primaryRoot.absolutePath
                )
            )
        }

        return rootsByPath.values.sortedWith(
            compareBy<ExplorerRootUi> { if (it.label == "内部ストレージ") 0 else 1 }
                .thenBy { it.label.lowercase(Locale.ROOT) }
        )
    }

    private fun resolveExternalRootFromAppDir(appDir: File): File? {
        val marker = "${File.separator}Android${File.separator}"
        val path = canonicalFile(appDir).absolutePath
        val markerIndex = path.indexOf(marker)
        if (markerIndex <= 0) {
            return null
        }
        return File(path.substring(0, markerIndex))
    }

    private fun resolveDirectoryPath(path: String): String? {
        val file = canonicalFile(File(path))
        return if (file.exists() && file.isDirectory && file.canRead()) {
            file.absolutePath
        } else {
            null
        }
    }

    private fun listDirectoryItems(path: String): List<ExplorerItemUi> {
        val directory = canonicalFile(File(path))
        val children = directory.listFiles().orEmpty()
        return children.map { child ->
            ExplorerItemUi(
                path = canonicalFile(child).absolutePath,
                name = child.name.ifBlank { child.absolutePath },
                isDirectory = child.isDirectory,
                sizeBytes = if (child.isFile) child.length() else 0L,
                lastModified = child.lastModified()
            )
        }.sortedWith(
            compareBy<ExplorerItemUi> { !it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    private fun copyPath(source: File, destination: File) {
        if (source.isDirectory) {
            if (!source.copyRecursively(destination, overwrite = false)) {
                error("コピーに失敗しました")
            }
            return
        }

        source.copyTo(destination, overwrite = false)
    }

    private fun movePath(source: File, destination: File) {
        if (source.renameTo(destination)) {
            return
        }

        copyPath(source, destination)
        if (!source.deleteRecursively()) {
            throw IOException("元のアイテムを削除できませんでした")
        }
    }

    private fun canonicalFile(file: File): File {
        return runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
    }
}
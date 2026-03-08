package com.example.litemediaplayer.comic

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.junrar.Archive
import com.example.litemediaplayer.core.AppLogger
import com.example.litemediaplayer.data.ComicBook
import com.example.litemediaplayer.data.ComicBookDao
import com.example.litemediaplayer.data.ComicFolder
import com.example.litemediaplayer.data.ComicFolderDao
import com.example.litemediaplayer.data.LockConfig
import com.example.litemediaplayer.data.LockConfigDao
import com.example.litemediaplayer.lock.LockManager
import com.example.litemediaplayer.lock.LockTargetType
import com.example.litemediaplayer.settings.AppSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("zip", "cbz", "cbr", "rar")

data class ComicPage(
    val index: Int,
    val model: Any,
    val sourceName: String
)

data class ComicFolderUi(
    val folder: ComicFolder,
    val isLockEnabled: Boolean = false,
    val isHidden: Boolean = false
) {
    val id: Long get() = folder.id
    val displayName: String get() = folder.displayName
    val coverUri: String? get() = folder.coverUri
    val bookCount: Int get() = folder.bookCount
    val treeUri: String get() = folder.treeUri
}

data class ComicReaderUiState(
    val folders: List<ComicFolderUi> = emptyList(),
    val allFolders: List<ComicFolderUi> = emptyList(),
    val selectedFolderId: Long? = null,
    val books: List<ComicBook> = emptyList(),
    val currentBookId: Long? = null,
    val pages: List<ComicPage> = emptyList(),
    val currentPage: Int = 0,
    val isLoadingBook: Boolean = false,
    val settings: ComicReaderSettings = ComicReaderSettings(),
    val fiveTapEnabled: Boolean = true,
    val fiveTapAuthRequired: Boolean = true,
    val showHiddenLocked: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    private val comicFolderDao: ComicFolderDao,
    private val comicBookDao: ComicBookDao,
    private val comicSettings: ComicSettings,
    private val pageProcessor: PageProcessor,
    private val lockConfigDao: LockConfigDao,
    private val lockManager: LockManager,
    private val appSettingsStore: AppSettingsStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(ComicReaderUiState())
    val uiState: StateFlow<ComicReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                comicBookDao.observeAll(),
                lockConfigDao.getConfigsByType(LockTargetType.COMIC_SHELF.name),
                appSettingsStore.settingsFlow.map { settings -> settings.hiddenLockContentVisible }
            ) { books, lockConfigs, showHiddenLocked ->
                books.filter { book ->
                    val lockConfig = lockConfigs.firstOrNull { config ->
                        config.targetId == book.id && config.isEnabled
                    } ?: return@filter true

                    val locked = lockManager.isLocked(LockTargetType.COMIC_SHELF, book.id)
                    !(lockConfig.isHidden && locked && !showHiddenLocked)
                }
            }.collect { books ->
                _uiState.update { it.copy(books = books) }
            }
        }
        viewModelScope.launch {
            combine(
                comicFolderDao.observeAll(),
                lockConfigDao.getConfigsByType(LockTargetType.COMIC_FOLDER.name),
                appSettingsStore.settingsFlow.map { it.hiddenLockContentVisible }
            ) { folders, lockConfigs, showHidden ->
                Triple(folders, lockConfigs, showHidden)
            }.collect { (folders, lockConfigs, showHidden) ->
                val folderUis = folders.map { folder ->
                    val lockConfig = lockConfigs.firstOrNull { it.targetId == folder.id }
                    ComicFolderUi(
                        folder = folder,
                        isLockEnabled = lockConfig?.isEnabled == true,
                        isHidden = lockConfig?.isHidden == true
                    )
                }
                val visibleFolders = folderUis.filterNot {
                    it.isLockEnabled && it.isHidden && !showHidden
                }
                _uiState.update { state ->
                    val selected = state.selectedFolderId
                    val resolvedSelected = if (
                        selected != null &&
                        visibleFolders.none { it.id == selected }
                    ) {
                        null
                    } else {
                        selected
                    }
                    state.copy(
                        folders = visibleFolders,
                        allFolders = folderUis,
                        selectedFolderId = resolvedSelected,
                        showHiddenLocked = showHidden
                    )
                }
            }
        }
        viewModelScope.launch {
            comicSettings.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.settingsFlow.collect { appSettings ->
                _uiState.update {
                    it.copy(
                        fiveTapEnabled = appSettings.fiveTapEnabled,
                        fiveTapAuthRequired = appSettings.fiveTapAuthRequired
                    )
                }
            }
        }
    }

    fun registerComicFolder(folderUri: Uri, resolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                resolver.takePersistableUriPermission(
                    folderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                AppLogger.w("ComicReader", "takePersistableUriPermission failed", e)
            }

            val root = DocumentFile.fromTreeUri(context, folderUri)
            if (root == null || !root.canRead()) {
                _uiState.update { it.copy(errorMessage = "このフォルダは読み取れません") }
                return@launch
            }

            val treeUriString = folderUri.toString()
            val existingFolder = comicFolderDao.findByTreeUri(treeUriString)
            if (existingFolder != null) {
                _uiState.update { it.copy(errorMessage = "このフォルダは既に登録されています") }
                return@launch
            }

            val folderName = root.name ?: "コミックフォルダ"
            val folderId = comicFolderDao.upsert(
                ComicFolder(
                    displayName = folderName,
                    treeUri = treeUriString
                )
            )

            val archives = mutableListOf<DocumentFile>()
            val imageFolders = mutableListOf<DocumentFile>()
            scanFolderRecursively(root, archives, imageFolders)

            if (archives.isEmpty() && imageFolders.isEmpty()) {
                comicFolderDao.deleteById(folderId)
                _uiState.update { it.copy(errorMessage = "アーカイブや画像が見つかりません") }
                return@launch
            }

            var registeredCount = 0
            var firstCover: String? = null

            for (archive in archives) {
                val uriString = archive.uri.toString()
                if (comicBookDao.findBySourceUri(uriString) != null) {
                    continue
                }
                val title = archive.name ?: "comic"
                val coverUri = try {
                    extractFirstImageFromArchive(archive.uri)
                } catch (e: Exception) {
                    AppLogger.w("ComicReader", "Cover extract failed: $title", e)
                    null
                }
                if (firstCover == null) {
                    firstCover = coverUri
                }
                comicBookDao.upsert(
                    ComicBook(
                        title = title,
                        sourceUri = uriString,
                        sourceType = "ARCHIVE",
                        coverUri = coverUri,
                        totalPages = 0,
                        folderId = folderId
                    )
                )
                registeredCount++
            }

            for (folder in imageFolders) {
                val uriString = folder.uri.toString()
                if (comicBookDao.findBySourceUri(uriString) != null) {
                    continue
                }
                val title = folder.name ?: "comic"
                val images = folder.listFiles()
                    .filter { it.isFile && it.name.hasImageExtension() }
                val coverUri = images
                    .minByOrNull { it.name?.lowercase(Locale.getDefault()) ?: "" }
                    ?.uri
                    ?.toString()
                if (firstCover == null) {
                    firstCover = coverUri
                }
                comicBookDao.upsert(
                    ComicBook(
                        title = title,
                        sourceUri = uriString,
                        sourceType = "FOLDER",
                        coverUri = coverUri,
                        totalPages = images.size,
                        folderId = folderId
                    )
                )
                registeredCount++
            }

            comicFolderDao.updateMeta(folderId, firstCover, registeredCount)

            _uiState.update { it.copy(selectedFolderId = folderId) }

            if (registeredCount == 0) {
                comicFolderDao.deleteById(folderId)
                _uiState.update { it.copy(errorMessage = "新しい書籍はありませんでした（全て登録済み）") }
            } else {
                AppLogger.i("ComicReader", "Registered $registeredCount books from folder")
            }
        }
    }

    private fun scanFolderRecursively(
        folder: DocumentFile,
        archives: MutableList<DocumentFile>,
        imageFolders: MutableList<DocumentFile>
    ) {
        val children = folder.listFiles()
        var hasImages = false

        for (child in children) {
            if (child.isDirectory) {
                scanFolderRecursively(child, archives, imageFolders)
            } else if (child.isFile) {
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
                when {
                    ext in SUPPORTED_ARCHIVE_EXTENSIONS -> archives.add(child)
                    ext in SUPPORTED_IMAGE_EXTENSIONS -> hasImages = true
                }
            }
        }

        // このフォルダ自体に画像ファイルがあり、かつアーカイブが無い場合は画像フォルダとして登録
        if (hasImages) {
            val hasArchivesInThisFolder = children.any { child ->
                child.isFile && child.name?.substringAfterLast('.', "")
                    ?.lowercase(Locale.getDefault()) in SUPPORTED_ARCHIVE_EXTENSIONS
            }
            if (!hasArchivesInThisFolder) {
                imageFolders.add(folder)
            }
        }
    }

    fun registerComicArchive(fileUri: Uri, resolver: ContentResolver) {
        registerSource(
            sourceUri = fileUri,
            sourceType = "ARCHIVE",
            resolver = resolver
        )
    }

    fun openBook(bookId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBook = true) }
            try {
                val book = withContext(Dispatchers.IO) {
                    comicBookDao.findById(bookId)
                }
                if (book == null) {
                    _uiState.update { it.copy(errorMessage = "書籍が見つかりません") }
                    return@launch
                }

                if (lockManager.isLocked(LockTargetType.COMIC_SHELF, bookId)) {
                    _uiState.update { it.copy(errorMessage = "この書籍はロックされています") }
                    return@launch
                }

                val settings = _uiState.value.settings
                val pages = withContext(Dispatchers.IO) {
                    when (book.sourceType) {
                        "FOLDER" -> loadFolderPages(book.sourceUri, settings)
                        "ARCHIVE" -> loadArchivePages(book.sourceUri, settings)
                        else -> emptyList()
                    }
                }

                if (pages.isEmpty()) {
                    _uiState.update { it.copy(errorMessage = "ページが見つかりません") }
                    return@launch
                }

                val initialPage = book.lastReadPage.coerceIn(0, pages.lastIndex)
                _uiState.update {
                    it.copy(
                        currentBookId = bookId,
                        pages = pages,
                        currentPage = initialPage,
                        errorMessage = null
                    )
                }

                saveProgress(bookId = bookId, currentPage = initialPage, totalPages = pages.size)
            } catch (_: OutOfMemoryError) {
                AppLogger.e("ComicReader", "openBook OOM: $bookId")
                _uiState.update { it.copy(errorMessage = "メモリ不足です。画像サイズが大きすぎます") }
            } catch (e: Exception) {
                AppLogger.e("ComicReader", "openBook failed", e)
                _uiState.update { it.copy(errorMessage = "読み込みエラー: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoadingBook = false) }
            }
        }
    }

    fun nextPage() {
        val state = _uiState.value
        if (state.pages.isEmpty()) {
            return
        }

        val nextIndex = (state.currentPage + 1).coerceAtMost(state.pages.lastIndex)
        _uiState.update { it.copy(currentPage = nextIndex) }
        saveProgress(
            bookId = state.currentBookId ?: return,
            currentPage = nextIndex,
            totalPages = state.pages.size
        )
    }

    fun previousPage() {
        val state = _uiState.value
        if (state.pages.isEmpty()) {
            return
        }

        val previousIndex = (state.currentPage - 1).coerceAtLeast(0)
        _uiState.update { it.copy(currentPage = previousIndex) }
        saveProgress(
            bookId = state.currentBookId ?: return,
            currentPage = previousIndex,
            totalPages = state.pages.size
        )
    }

    fun setCurrentPage(pageIndex: Int) {
        val state = _uiState.value
        if (state.pages.isEmpty()) {
            return
        }

        val safeIndex = pageIndex.coerceIn(0, state.pages.lastIndex)
        _uiState.update { it.copy(currentPage = safeIndex) }
        saveProgress(
            bookId = state.currentBookId ?: return,
            currentPage = safeIndex,
            totalPages = state.pages.size
        )
    }

    fun skipForward(count: Int) {
        val state = _uiState.value
        if (state.pages.isEmpty()) {
            return
        }

        val safeCount = count.coerceAtLeast(1)
        val target = (state.currentPage + safeCount).coerceAtMost(state.pages.lastIndex)
        _uiState.update { it.copy(currentPage = target) }
        saveProgress(
            bookId = state.currentBookId ?: return,
            currentPage = target,
            totalPages = state.pages.size
        )
    }

    fun skipBackward(count: Int) {
        val state = _uiState.value
        if (state.pages.isEmpty()) {
            return
        }

        val safeCount = count.coerceAtLeast(1)
        val target = (state.currentPage - safeCount).coerceAtLeast(0)
        _uiState.update { it.copy(currentPage = target) }
        saveProgress(
            bookId = state.currentBookId ?: return,
            currentPage = target,
            totalPages = state.pages.size
        )
    }

    fun goToFirstPage() {
        setCurrentPage(0)
    }

    fun goToLastPage() {
        val lastIndex = _uiState.value.pages.lastIndex
        setCurrentPage(lastIndex.coerceAtLeast(0))
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun selectComicFolder(folderId: Long?) {
        if (folderId != null) {
            val folderUi = _uiState.value.folders.firstOrNull { it.id == folderId }
            if (
                folderUi != null &&
                folderUi.isLockEnabled &&
                folderUi.isHidden &&
                !_uiState.value.showHiddenLocked
            ) {
                // ロック+非表示のフォルダにはアクセスさせない
                return
            }
        }
        _uiState.update { it.copy(selectedFolderId = folderId) }
    }

    fun deleteComicFolder(folderId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                lockConfigDao.findByTarget(LockTargetType.COMIC_FOLDER.name, folderId)
                    ?.let { lockConfigDao.delete(it) }
            }
            comicBookDao.deleteByFolder(folderId)
            comicFolderDao.deleteById(folderId)
            if (_uiState.value.selectedFolderId == folderId) {
                _uiState.update { it.copy(selectedFolderId = null) }
            }
        }
    }

    fun toggleComicFolderLock(folderId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = lockConfigDao.findByTarget(
                LockTargetType.COMIC_FOLDER.name,
                folderId
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
                _uiState.update {
                    it.copy(errorMessage = "先に設定タブでグローバルロックを設定してください")
                }
                return@launch
            }

            lockConfigDao.upsert(
                LockConfig(
                    targetType = LockTargetType.COMIC_FOLDER.name,
                    targetId = folderId,
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

    fun toggleHiddenComicVisibility(show: Boolean) {
        viewModelScope.launch {
            appSettingsStore.updateHiddenLockContentVisible(show)
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            comicBookDao.deleteById(bookId)
            if (_uiState.value.currentBookId == bookId) {
                _uiState.update {
                    it.copy(currentBookId = null, pages = emptyList(), currentPage = 0)
                }
            }
        }
    }

    fun updateReadingDirection(direction: ReadingDirection) {
        viewModelScope.launch {
            comicSettings.updateReadingDirection(direction)
        }
    }

    fun updateReaderMode(mode: ReaderMode) {
        viewModelScope.launch {
            comicSettings.updateMode(mode)
        }
    }

    fun updateGridSize(size: GridSize) {
        viewModelScope.launch {
            comicSettings.updateGridSize(size)
        }
    }

    fun updatePageAnimation(animation: PageAnimation) {
        viewModelScope.launch {
            comicSettings.updateAnimation(animation)
        }
    }

    fun updateAnimationSpeed(speedMs: Int) {
        viewModelScope.launch {
            comicSettings.updateAnimationSpeed(speedMs)
        }
    }

    fun updateBlueLightFilter(enabled: Boolean) {
        viewModelScope.launch {
            comicSettings.updateBlueLightFilter(enabled)
        }
    }

    fun updateZoomMax(maxZoom: Float) {
        viewModelScope.launch {
            comicSettings.updateZoomMax(maxZoom)
        }
    }

    fun updateAutoSplit(enabled: Boolean) {
        viewModelScope.launch {
            comicSettings.updateAutoSplit(enabled)
        }
    }

    fun updateSplitThreshold(threshold: Float) {
        viewModelScope.launch {
            comicSettings.updateSplitThreshold(threshold)
        }
    }

    fun updateSmartSplit(enabled: Boolean) {
        viewModelScope.launch {
            comicSettings.updateSmartSplit(enabled)
        }
    }

    fun updateSplitOffset(offset: Float) {
        viewModelScope.launch {
            comicSettings.updateSplitOffset(offset)
        }
    }

    fun updateAutoTrim(enabled: Boolean) {
        viewModelScope.launch {
            comicSettings.updateAutoTrim(enabled)
        }
    }

    fun updateTrimTolerance(tolerance: Int) {
        viewModelScope.launch {
            comicSettings.updateTrimTolerance(tolerance)
        }
    }

    fun updateTrimSafetyMargin(margin: Int) {
        viewModelScope.launch {
            comicSettings.updateTrimSafetyMargin(margin)
        }
    }

    fun updateTrimSensitivity(sensitivity: TrimSensitivity) {
        viewModelScope.launch {
            comicSettings.updateTrimSensitivity(sensitivity)
        }
    }

    fun updateTouchZoneConfig(config: TouchZoneConfig) {
        viewModelScope.launch {
            comicSettings.updateTouchZoneConfig(config)
        }
    }

    private fun registerSource(
        sourceUri: Uri,
        sourceType: String,
        resolver: ContentResolver
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                resolver.takePersistableUriPermission(
                    sourceUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val sourceString = sourceUri.toString()
            val existing = comicBookDao.findBySourceUri(sourceString)
            if (existing != null) {
                _uiState.update { it.copy(errorMessage = "同じ書籍は既に登録されています") }
                return@launch
            }

            val title = resolveDisplayName(sourceUri)
            val coverUri = resolveCoverUri(sourceUri, sourceType)
            val totalPages = estimatePageCount(sourceUri, sourceType)

            comicBookDao.upsert(
                ComicBook(
                    title = title,
                    sourceUri = sourceString,
                    sourceType = sourceType,
                    coverUri = coverUri,
                    totalPages = totalPages
                )
            )
        }
    }

    private suspend fun loadFolderPages(
        folderUriString: String,
        settings: ComicReaderSettings
    ): List<ComicPage> {
        val folderUri = folderUriString.toUri()
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val imageFiles = root.listFiles()
            .filter { file ->
                file.isFile && file.name.hasImageExtension()
            }
            .sortedBy { file -> file.name?.lowercase(Locale.getDefault()) }

        val needsProcessing = settings.autoTrimEnabled || settings.autoSplitEnabled
        if (!needsProcessing) {
            return imageFiles.mapIndexed { index, file ->
                ComicPage(
                    index = index,
                    model = file.uri,
                    sourceName = file.name ?: "page"
                )
            }
        }

        return buildPageModelsFromDocuments(imageFiles, settings)
    }

    private suspend fun loadArchivePages(
        archiveUriString: String,
        settings: ComicReaderSettings
    ): List<ComicPage> {
        val archiveUri = archiveUriString.toUri()
        val fileName = resolveDisplayName(archiveUri).lowercase(Locale.getDefault())

        val cacheDir = File(context.cacheDir, "comic_archive_pages")
        cacheDir.mkdirs()
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }

        if (fileName.endsWith(".cbr") || fileName.endsWith(".rar")) {
            extractRarImages(archiveUri, cacheDir)
        } else {
            extractZipImages(archiveUri, cacheDir)
        }

        val pages = cacheDir.listFiles()
            ?.filter { file -> file.isFile && file.name.hasImageExtension() }
            ?.sortedBy { file -> file.name.lowercase(Locale.getDefault()) }
            .orEmpty()

        AppLogger.i("ComicReader", "Archive extracted: ${pages.size} pages from $fileName")

        val needsProcessing = settings.autoTrimEnabled || settings.autoSplitEnabled
        if (!needsProcessing) {
            return pages.mapIndexed { index, file ->
                ComicPage(
                    index = index,
                    model = file.toUri(),
                    sourceName = file.name
                )
            }
        }

        return buildPageModelsFromFiles(pages, settings)
    }

    private suspend fun buildPageModelsFromDocuments(
        documents: List<DocumentFile>,
        settings: ComicReaderSettings
    ): List<ComicPage> {
        val output = mutableListOf<ComicPage>()
        var index = 0

        for (document in documents) {
            val processedUris = processAndMaterializePages(
                sourceName = document.name ?: "page",
                openStream = { context.contentResolver.openInputStream(document.uri) },
                settings = settings
            )

            for (processedUri in processedUris) {
                output += ComicPage(
                    index = index,
                    model = processedUri,
                    sourceName = document.name ?: "page"
                )
                index += 1
            }
        }

        return output
    }

    private suspend fun buildPageModelsFromFiles(
        files: List<File>,
        settings: ComicReaderSettings
    ): List<ComicPage> {
        val output = mutableListOf<ComicPage>()
        var index = 0

        for (file in files) {
            val processedUris = processAndMaterializePages(
                sourceName = file.name,
                openStream = { file.inputStream() },
                settings = settings
            )

            for (processedUri in processedUris) {
                output += ComicPage(
                    index = index,
                    model = processedUri,
                    sourceName = file.name
                )
                index += 1
            }
        }

        return output
    }

    private suspend fun processAndMaterializePages(
        sourceName: String,
        openStream: () -> java.io.InputStream?,
        settings: ComicReaderSettings
    ): List<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                var sourceBytes: ByteArray? = openStream()?.use { it.readBytes() }
                    ?: return@withContext emptyList()
                val bytes = sourceBytes ?: return@withContext emptyList()
                AppLogger.d("ComicReader", "Processing page: $sourceName (${bytes.size} bytes)")

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    AppLogger.w(
                        "ComicReader",
                        "Invalid image: $sourceName (${bounds.outWidth}x${bounds.outHeight})"
                    )
                    sourceBytes = null
                    return@withContext emptyList()
                }

                AppLogger.d("ComicReader", "Image size: ${bounds.outWidth}x${bounds.outHeight}")

                if (!settings.autoTrimEnabled && !settings.autoSplitEnabled) {
                    val result = writeSingleTempPage(bytes, sourceName)
                    sourceBytes = null
                    return@withContext result
                }

                val processed = runCatching {
                    pageProcessor.process(bytes = bytes, settings = settings)
                }.getOrElse { error ->
                    AppLogger.w("ComicReader", "PageProcessor failed for $sourceName", error)
                    emptyList()
                }

                if (processed.isEmpty()) {
                    val result = writeSingleTempPage(bytes, sourceName)
                    sourceBytes = null
                    return@withContext result
                }

                val fullRect = android.graphics.Rect(0, 0, bounds.outWidth, bounds.outHeight)
                if (processed.size == 1 && processed.first().rect == fullRect) {
                    val result = writeSingleTempPage(bytes, sourceName)
                    sourceBytes = null
                    return@withContext result
                }

                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                val sampleSize = when {
                    maxDim > 8_000 -> 4
                    maxDim > 4_000 -> 2
                    else -> 1
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val sourceBitmap = BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    decodeOptions
                ) ?: return@withContext emptyList()
                sourceBytes = null

                val scaleX = sourceBitmap.width.toFloat() / bounds.outWidth.toFloat().coerceAtLeast(1f)
                val scaleY = sourceBitmap.height.toFloat() / bounds.outHeight.toFloat().coerceAtLeast(1f)
                val outputUris = mutableListOf<Uri>()

                processed.forEachIndexed { index, page ->
                    val rect = page.rect

                    val left = (rect.left * scaleX).toInt().coerceIn(0, sourceBitmap.width - 1)
                    val top = (rect.top * scaleY).toInt().coerceIn(0, sourceBitmap.height - 1)
                    val cropWidth = (rect.width() * scaleX).toInt()
                        .coerceIn(1, sourceBitmap.width - left)
                    val cropHeight = (rect.height() * scaleY).toInt()
                        .coerceIn(1, sourceBitmap.height - top)

                    val cropped = Bitmap.createBitmap(sourceBitmap, left, top, cropWidth, cropHeight)
                    val file = File(
                        ensureProcessedCacheDir(),
                        "${sourceName.hashCode()}_${System.nanoTime()}_${index}.jpg"
                    )
                    FileOutputStream(file).use { output ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, 85, output)
                    }
                    cropped.recycle()
                    outputUris += file.toUri()
                }

                sourceBitmap.recycle()
                outputUris
            } catch (_: OutOfMemoryError) {
                AppLogger.e("ComicReader", "OOM processing page: $sourceName")
                System.gc()
                emptyList()
            } catch (error: Exception) {
                AppLogger.e("ComicReader", "Error processing page: $sourceName", error)
                emptyList()
            }
        }
    }

    private fun writeSingleTempPage(bytes: ByteArray, sourceName: String): List<Uri> {
        val file = File(
            ensureProcessedCacheDir(),
            "${sourceName.hashCode()}_${System.nanoTime()}_single.jpg"
        )
        FileOutputStream(file).use { output ->
            output.write(bytes)
        }
        return listOf(file.toUri())
    }

    private fun ensureProcessedCacheDir(): File {
        val dir = File(context.cacheDir, "comic_processed_pages")
        dir.mkdirs()
        return dir
    }

    private suspend fun extractZipImages(
        archiveUri: Uri,
        outputDir: File
    ) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                ZipInputStream(input).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        try {
                            val name = entry.name
                            if (!entry.isDirectory && name.hasImageExtension()) {
                                val outFile = File(outputDir, sanitizeFileName(name))
                                FileOutputStream(outFile).use { output ->
                                    val buffer = ByteArray(8_192)
                                    var bytesRead: Int
                                    while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                                        output.write(buffer, 0, bytesRead)
                                    }
                                    output.flush()
                                }
                            }
                            zipStream.closeEntry()
                        } catch (e: Exception) {
                            AppLogger.w("ComicReader", "Skip zip entry: ${entry.name}", e)
                        }
                        entry = try {
                            zipStream.nextEntry
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ComicReader", "extractZipImages failed", e)
            _uiState.update { it.copy(errorMessage = "ZIP展開エラー: ${e.message}") }
        }
    }

    private suspend fun extractRarImages(
        archiveUri: Uri,
        outputDir: File
    ) = withContext(Dispatchers.IO) {
        val tempRar = File(outputDir, "source_${System.nanoTime()}.rar")
        val copied = runCatching {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                FileOutputStream(tempRar).use { output ->
                    input.copyTo(output)
                }
            }
        }.isSuccess

        if (!copied || !tempRar.exists()) {
            _uiState.update { it.copy(errorMessage = "CBRの読み込みに失敗しました") }
            return@withContext
        }

        runCatching {
            Archive(tempRar).use { archive ->
                var header = archive.nextFileHeader()
                var index = 0

                while (header != null) {
                    val entryName = header.fileNameString ?: "entry_$index"
                    if (!header.isDirectory && entryName.hasImageExtension()) {
                        val outFile = File(
                            outputDir,
                            sanitizeFileName("$index-$entryName")
                        )
                        FileOutputStream(outFile).use { output ->
                            archive.extractFile(header, output)
                        }
                        index += 1
                    }
                    header = archive.nextFileHeader()
                }
            }
        }.onFailure {
            _uiState.update { it.copy(errorMessage = "CBRの展開に失敗しました") }
        }

        runCatching { tempRar.delete() }
    }

    private fun resolveDisplayName(uri: Uri): String {
        runCatching {
            DocumentFile.fromSingleUri(context, uri)?.name
        }.getOrNull()?.let { name ->
            return name
        }

        runCatching {
            DocumentFile.fromTreeUri(context, uri)?.name
        }.getOrNull()?.let { name ->
            return name
        }

        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        cursor.getString(index)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }.getOrNull()?.let { name ->
            return name
        }

        return "comic"
    }

    private suspend fun resolveCoverUri(uri: Uri, sourceType: String): String? {
        return when (sourceType) {
            "FOLDER" -> {
                try {
                    val root = DocumentFile.fromTreeUri(context, uri) ?: return null
                    root.listFiles()
                        .filter { file -> file.isFile && file.name.hasImageExtension() }
                        .minByOrNull { file -> file.name?.lowercase(Locale.getDefault()) ?: "" }
                        ?.uri
                        ?.toString()
                } catch (_: Exception) {
                    null
                }
            }

            "ARCHIVE" -> {
                try {
                    extractFirstImageFromArchive(uri)
                } catch (error: Exception) {
                    AppLogger.w("ComicReader", "Cover extract failed", error)
                    null
                }
            }

            else -> null
        }
    }

    private suspend fun extractFirstImageFromArchive(archiveUri: Uri): String? =
        withContext(Dispatchers.IO) {
            val coverDir = File(context.cacheDir, "comic_covers")
            coverDir.mkdirs()

            val fileName = resolveDisplayName(archiveUri)
            val coverFile = File(coverDir, "${fileName.hashCode()}_cover.jpg")
            if (coverFile.exists() && coverFile.length() > 0L) {
                return@withContext coverFile.toUri().toString()
            }

            val archiveName = fileName.lowercase(Locale.getDefault())
            if (archiveName.endsWith(".cbr") || archiveName.endsWith(".rar")) {
                val tempRar = File(coverDir, "temp_${System.nanoTime()}.rar")
                try {
                    context.contentResolver.openInputStream(archiveUri)?.use { input ->
                        FileOutputStream(tempRar).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@withContext null

                    Archive(tempRar).use { archive ->
                        var header = archive.nextFileHeader()
                        while (header != null) {
                            val entryName = header.fileNameString ?: ""
                            if (!header.isDirectory && entryName.hasImageExtension()) {
                                FileOutputStream(coverFile).use { output ->
                                    archive.extractFile(header, output)
                                }
                                break
                            }
                            header = archive.nextFileHeader()
                        }
                    }
                } finally {
                    runCatching { tempRar.delete() }
                }
            } else {
                context.contentResolver.openInputStream(archiveUri)?.use { input ->
                    ZipInputStream(input).use { zipStream ->
                        var entry = zipStream.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.hasImageExtension()) {
                                FileOutputStream(coverFile).use { output ->
                                    val buffer = ByteArray(8_192)
                                    var bytesRead: Int
                                    while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                                        output.write(buffer, 0, bytesRead)
                                    }
                                    output.flush()
                                }
                                zipStream.closeEntry()
                                break
                            }
                            zipStream.closeEntry()
                            entry = try {
                                zipStream.nextEntry
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }
            }

            if (coverFile.exists() && coverFile.length() > 0L) {
                coverFile.toUri().toString()
            } else {
                null
            }
        }

    private suspend fun estimatePageCount(uri: Uri, sourceType: String): Int {
        return when (sourceType) {
            "FOLDER" -> {
                val root = DocumentFile.fromTreeUri(context, uri)
                root?.listFiles()?.count { it.isFile && it.name.hasImageExtension() } ?: 0
            }

            else -> 0
        }
    }

    private fun saveProgress(bookId: Long, currentPage: Int, totalPages: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            comicBookDao.updateProgress(
                bookId = bookId,
                lastReadPage = currentPage,
                totalPages = totalPages,
                readStatus = calcReadStatus(currentPage, totalPages)
            )
        }
    }

    private fun calcReadStatus(currentPage: Int, totalPages: Int): String {
        if (totalPages <= 0) {
            return "UNREAD"
        }
        return when {
            currentPage <= 0 -> "UNREAD"
            currentPage >= totalPages - 1 -> "READ"
            else -> "IN_PROGRESS"
        }
    }
}

private fun String?.hasImageExtension(): Boolean {
    val ext = this
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.getDefault())
        ?: return false
    return ext in SUPPORTED_IMAGE_EXTENSIONS
}

private fun sanitizeFileName(original: String): String {
    return original.replace("/", "_").replace("\\", "_")
}

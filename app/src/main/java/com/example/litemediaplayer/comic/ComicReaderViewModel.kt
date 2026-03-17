package com.example.litemediaplayer.comic

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
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
import com.example.litemediaplayer.lock.LockAuthMethod
import com.example.litemediaplayer.lock.LockManager
import com.example.litemediaplayer.lock.LockTargetType
import com.example.litemediaplayer.settings.AppSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("zip", "cbz", "cbr", "rar")
private const val COMIC_PROGRESS_EXPORT_PREFIX = "comic_progress_sync_"
private const val INITIAL_PAGE_PREFETCH_COUNT = 6
private const val BACKGROUND_PAGE_SOURCE_BATCH_SIZE = 12

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
    val totalPageCount: Int = 0,
    val isLoadingBook: Boolean = false,
    val isBookFullyLoaded: Boolean = true,
    val settings: ComicReaderSettings = ComicReaderSettings(),
    val fiveTapEnabled: Boolean = true,
    val fiveTapAuthRequired: Boolean = true,
    val hiddenUnlockMethod: LockAuthMethod = LockAuthMethod.PIN,
    val showHiddenLocked: Boolean = false,
    val registeredSyncDeviceCount: Int = 0,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

private data class ComicPageSource(
    val sourceName: String,
    val directUri: Uri,
    val signature: ContentCacheSignature,
    val openStream: () -> java.io.InputStream?
)

private data class ProgressivePageLoadResult(
    val initialPages: List<ComicPage>,
    val remainingSources: List<ComicPageSource>,
    val totalPageHint: Int
)

private data class ComicFolderRegistrationTarget(
    val treeUri: Uri,
    val displayName: String,
    val directArchives: List<DocumentFile>,
    val directImages: List<DocumentFile>
)

@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    private val comicFolderDao: ComicFolderDao,
    private val comicBookDao: ComicBookDao,
    private val comicSettings: ComicSettings,
    private val comicProgressRepository: ComicProgressRepository,
    private val comicProgressSyncStore: ComicProgressSyncStore,
    private val comicProgressLanSyncManager: ComicProgressLanSyncManager,
    private val pageProcessor: PageProcessor,
    private val lockConfigDao: LockConfigDao,
    private val lockManager: LockManager,
    private val appSettingsStore: AppSettingsStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(ComicReaderUiState())
    val uiState: StateFlow<ComicReaderUiState> = _uiState.asStateFlow()
    private var backgroundPageLoadJob: Job? = null
    private var bookProgressSyncJob: Job? = null
    private var pageLoadGeneration: Long = 0L

    init {
        viewModelScope.launch {
            // アプリ起動時は必ず非表示状態に戻す
            appSettingsStore.updateHiddenLockContentVisible(false)
        }

        _uiState.update { state ->
            state.copy(
                showHiddenLocked = false,
                folders = state.allFolders.filterNot { it.isLockEnabled }
            )
        }

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

                    showHiddenLocked || !lockConfig.isEnabled
                }
            }.collect { books ->
                _uiState.update { it.copy(books = books) }
                bookProgressSyncJob?.cancel()
                bookProgressSyncJob = viewModelScope.launch(Dispatchers.IO) {
                    comicProgressRepository.syncProgressToBooks(books)
                }
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
                        isHidden = lockConfig?.isEnabled == true || lockConfig?.isHidden == true
                    )
                }
                val visibleFolders = folderUis.filterNot {
                    it.isLockEnabled && !showHidden
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
                        fiveTapAuthRequired = appSettings.fiveTapAuthRequired,
                        hiddenUnlockMethod = LockAuthMethod.fromStoredValue(
                            appSettings.lockAuthMethod
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            comicProgressSyncStore.settingsFlow.collect { syncSettings ->
                _uiState.update {
                    it.copy(registeredSyncDeviceCount = syncSettings.registeredDevices.size)
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

            val normalizedRootUri = normalizeFolderTreeUri(folderUri) ?: folderUri
            val root = resolveFolderDocument(normalizedRootUri)
            if (root == null || !root.canRead()) {
                _uiState.update { it.copy(errorMessage = "このフォルダは読み取れません") }
                return@launch
            }

            val targets = mutableListOf<ComicFolderRegistrationTarget>()
            collectComicFolderRegistrationTargets(root, targets)

            if (targets.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "アーカイブや画像が見つかりません") }
                return@launch
            }

            val rootTreeUriString = normalizedRootUri.toString()
            comicFolderDao.findByTreeUri(rootTreeUriString)
                ?.takeIf { folder ->
                    targets.none { target -> target.treeUri.toString() == folder.treeUri }
                }
                ?.let { legacyFolder ->
                    lockConfigDao.findByTarget(LockTargetType.COMIC_FOLDER.name, legacyFolder.id)
                        ?.let { lockConfigDao.delete(it) }
                    comicBookDao.deleteByFolder(legacyFolder.id)
                    comicFolderDao.deleteById(legacyFolder.id)
                    if (_uiState.value.selectedFolderId == legacyFolder.id) {
                        _uiState.update { it.copy(selectedFolderId = null) }
                    }
                    AppLogger.i(
                        "ComicReader",
                        "Removed legacy flattened comic folder: $rootTreeUriString"
                    )
                }

            var addedFolderCount = 0
            var refreshedFolderCount = 0
            var changedBookCount = 0
            var removedBookCount = 0
            val allowedSourceUrisByFolderId = mutableMapOf<Long, MutableSet<String>>()

            for (target in targets) {
                val targetTreeUriString = target.treeUri.toString()
                val existingFolder = comicFolderDao.findByTreeUri(targetTreeUriString)
                val folderId = if (existingFolder != null) {
                    refreshedFolderCount++
                    comicFolderDao.upsert(existingFolder.copy(displayName = target.displayName))
                    existingFolder.id
                } else {
                    addedFolderCount++
                    comicFolderDao.upsert(
                        ComicFolder(
                            displayName = target.displayName,
                            treeUri = targetTreeUriString
                        )
                    )
                }

                val allowedSources = allowedSourceUrisByFolderId
                    .getOrPut(folderId) { mutableSetOf() }

                for (archive in target.directArchives) {
                    val archiveSourceUri = archive.uri.toString()
                    allowedSources += archiveSourceUri

                    val existingBook = comicBookDao.findBySourceUri(archiveSourceUri)
                    val title = archive.name ?: existingBook?.title ?: "comic"
                    val coverUri = existingBook?.coverUri ?: try {
                        extractFirstImageFromArchive(archive.uri)
                    } catch (e: Exception) {
                        AppLogger.w("ComicReader", "Cover extract failed: $title", e)
                        null
                    }

                    if (existingBook == null || existingBook.folderId != folderId) {
                        changedBookCount++
                    }

                    comicBookDao.upsert(
                        existingBook?.copy(
                            title = title,
                            sourceType = "ARCHIVE",
                            coverUri = coverUri ?: existingBook.coverUri,
                            folderId = folderId
                        ) ?: ComicBook(
                            title = title,
                            sourceUri = archiveSourceUri,
                            sourceType = "ARCHIVE",
                            coverUri = coverUri,
                            totalPages = 0,
                            folderId = folderId
                        )
                    )
                }

                if (target.directImages.isNotEmpty()) {
                    val folderSourceUri = targetTreeUriString
                    allowedSources += folderSourceUri

                    val coverUri = target.directImages
                        .minWithOrNull { left, right ->
                            compareNaturally(left.name.orEmpty(), right.name.orEmpty())
                        }
                        ?.uri
                        ?.toString()
                    val existingBook = comicBookDao.findBySourceUri(folderSourceUri)

                    if (existingBook == null || existingBook.folderId != folderId) {
                        changedBookCount++
                    }

                    comicBookDao.upsert(
                        existingBook?.copy(
                            title = target.displayName,
                            sourceType = "FOLDER",
                            coverUri = coverUri ?: existingBook.coverUri,
                            totalPages = target.directImages.size,
                            folderId = folderId
                        ) ?: ComicBook(
                            title = target.displayName,
                            sourceUri = folderSourceUri,
                            sourceType = "FOLDER",
                            coverUri = coverUri,
                            totalPages = target.directImages.size,
                            folderId = folderId
                        )
                    )
                }

                AppLogger.i(
                    "ComicReader",
                    "Prepared comic folder target: ${target.displayName}, " +
                        "archives=${target.directArchives.size}, images=${target.directImages.size}, " +
                        "treeUri=$targetTreeUriString"
                )
            }

            for ((folderId, allowedSources) in allowedSourceUrisByFolderId) {
                comicBookDao.findByFolder(folderId).forEach { book ->
                    if (book.sourceUri !in allowedSources) {
                        comicBookDao.deleteById(book.id)
                        removedBookCount++
                    }
                }

                val booksInFolder = comicBookDao.findByFolder(folderId)
                val coverUri = booksInFolder.firstOrNull { !it.coverUri.isNullOrBlank() }?.coverUri
                comicFolderDao.updateMeta(folderId, coverUri, booksInFolder.size)
            }

            val statusMessage = buildString {
                if (addedFolderCount > 0) {
                    append("${addedFolderCount}件のコミックフォルダを登録しました")
                }
                if (refreshedFolderCount > 0) {
                    if (isNotEmpty()) append(" / ")
                    append("${refreshedFolderCount}件を再走査しました")
                }
                if (changedBookCount > 0) {
                    if (isNotEmpty()) append(" / ")
                    append("${changedBookCount}件の書籍を追加・再配置しました")
                }
                if (removedBookCount > 0) {
                    if (isNotEmpty()) append(" / ")
                    append("${removedBookCount}件の古い書籍を整理しました")
                }
                if (isEmpty()) {
                    append("登録済みフォルダを確認しました（追加の変更はありません）")
                }
            }

            _uiState.update {
                it.copy(
                    selectedFolderId = null,
                    errorMessage = null,
                    statusMessage = statusMessage
                )
            }

            AppLogger.i(
                "ComicReader",
                "Comic folder registration finished: targets=${targets.size}, " +
                    "addedFolders=$addedFolderCount, refreshedFolders=$refreshedFolderCount, " +
                    "changedBooks=$changedBookCount, removedBooks=$removedBookCount"
            )
        }
    }

    private fun collectComicFolderRegistrationTargets(
        folder: DocumentFile,
        targets: MutableList<ComicFolderRegistrationTarget>
    ) {
        val children = folder.listFiles()
            .sortedWith { left, right ->
                compareNaturally(left.name.orEmpty(), right.name.orEmpty())
            }
        val directArchives = children.filter { child ->
            child.isFile && child.name.hasArchiveExtension()
        }
        val directImages = children.filter { child ->
            child.isFile && child.name.hasImageExtension()
        }

        if (directArchives.isNotEmpty() || directImages.isNotEmpty()) {
            val treeUri = normalizeFolderTreeUri(folder.uri)
            if (treeUri != null) {
                targets += ComicFolderRegistrationTarget(
                    treeUri = treeUri,
                    displayName = folder.name ?: "コミックフォルダ",
                    directArchives = directArchives,
                    directImages = directImages
                )
            }
        }

        children.filter { it.isDirectory }.forEach { child ->
            collectComicFolderRegistrationTargets(child, targets)
        }
    }

    private fun normalizeFolderTreeUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val documentId = runCatching {
            DocumentsContract.getDocumentId(uri)
        }.getOrElse {
            runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        } ?: return null

        return runCatching {
            DocumentsContract.buildTreeDocumentUri(authority, documentId)
        }.getOrNull()
    }

    private fun resolveFolderDocument(uri: Uri): DocumentFile? {
        val normalizedUri = normalizeFolderTreeUri(uri) ?: uri
        return DocumentFile.fromTreeUri(context, normalizedUri)
    }

    fun registerComicArchive(fileUri: Uri, resolver: ContentResolver) {
        registerSource(
            sourceUri = fileUri,
            sourceType = "ARCHIVE",
            resolver = resolver
        )
    }

    fun openBook(bookId: Long) {
        backgroundPageLoadJob?.cancel()
        val loadGeneration = ++pageLoadGeneration
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentBookId = null,
                    pages = emptyList(),
                    currentPage = 0,
                    totalPageCount = 0,
                    isLoadingBook = true,
                    isBookFullyLoaded = false,
                    errorMessage = null
                )
            }
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

                val storedProgress = withContext(Dispatchers.IO) {
                    comicProgressRepository.resolveProgressForBook(book)
                }
                val storedPage = storedProgress?.lastReadPage ?: book.lastReadPage
                val storedStatus = storedProgress?.readStatus ?: book.readStatus
                val storedTotalPages = storedProgress?.totalPages ?: book.totalPages

                val settings = _uiState.value.settings
                if (shouldPreprocessPages(settings)) {
                    val progressiveResult = withContext(Dispatchers.IO) {
                        openBookProgressively(book = book, settings = settings)
                    }

                    if (loadGeneration != pageLoadGeneration) {
                        return@launch
                    }

                    if (progressiveResult.initialPages.isEmpty()) {
                        _uiState.update { it.copy(errorMessage = "ページが見つかりません") }
                        return@launch
                    }

                    val initialPage = normalizeProgressPage(
                        currentPage = storedPage,
                        totalPages = maxOf(progressiveResult.totalPageHint, storedTotalPages),
                        readStatus = storedStatus
                    ).coerceIn(0, progressiveResult.initialPages.lastIndex)
                    _uiState.update {
                        it.copy(
                            currentBookId = bookId,
                            pages = progressiveResult.initialPages,
                            currentPage = initialPage,
                            totalPageCount = progressiveResult.totalPageHint,
                            isBookFullyLoaded = progressiveResult.remainingSources.isEmpty(),
                            errorMessage = null
                        )
                    }

                    saveProgress(
                        book = book,
                        currentPage = initialPage,
                        totalPages = progressiveResult.totalPageHint
                    )

                    if (progressiveResult.remainingSources.isNotEmpty()) {
                        startBackgroundPageLoad(
                            bookId = bookId,
                            loadGeneration = loadGeneration,
                            settings = settings,
                            remainingSources = progressiveResult.remainingSources,
                            startingPageIndex = progressiveResult.initialPages.size,
                            totalPageHint = progressiveResult.totalPageHint
                        )
                    }
                } else {
                    val pages = withContext(Dispatchers.IO) {
                        when (book.sourceType) {
                            "FOLDER" -> loadFolderPages(book.sourceUri, settings)
                            "ARCHIVE" -> loadArchivePages(book.sourceUri, settings)
                            else -> emptyList()
                        }
                    }

                    if (loadGeneration != pageLoadGeneration) {
                        return@launch
                    }

                    if (pages.isEmpty()) {
                        _uiState.update { it.copy(errorMessage = "ページが見つかりません") }
                        return@launch
                    }

                    val initialPage = normalizeProgressPage(
                        currentPage = storedPage,
                        totalPages = maxOf(pages.size, storedTotalPages),
                        readStatus = storedStatus
                    ).coerceIn(0, pages.lastIndex)
                    _uiState.update {
                        it.copy(
                            currentBookId = bookId,
                            pages = pages,
                            currentPage = initialPage,
                            totalPageCount = pages.size,
                            isBookFullyLoaded = true,
                            errorMessage = null
                        )
                    }

                    saveProgress(book = book, currentPage = initialPage, totalPages = pages.size)
                }
            } catch (_: OutOfMemoryError) {
                AppLogger.e("ComicReader", "openBook OOM: $bookId")
                if (loadGeneration == pageLoadGeneration) {
                    _uiState.update { it.copy(errorMessage = "メモリ不足です。画像サイズが大きすぎます") }
                }
            } catch (e: Exception) {
                AppLogger.e("ComicReader", "openBook failed", e)
                if (loadGeneration == pageLoadGeneration) {
                    _uiState.update { it.copy(errorMessage = "読み込みエラー: ${e.message}") }
                }
            } finally {
                if (loadGeneration == pageLoadGeneration) {
                    _uiState.update { it.copy(isLoadingBook = false) }
                }
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
            book = resolveCurrentBook(state) ?: return,
            currentPage = nextIndex,
            totalPages = state.totalPageCount.coerceAtLeast(state.pages.size)
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
            book = resolveCurrentBook(state) ?: return,
            currentPage = previousIndex,
            totalPages = state.totalPageCount.coerceAtLeast(state.pages.size)
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
            book = resolveCurrentBook(state) ?: return,
            currentPage = safeIndex,
            totalPages = state.totalPageCount.coerceAtLeast(state.pages.size)
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
            book = resolveCurrentBook(state) ?: return,
            currentPage = target,
            totalPages = state.totalPageCount.coerceAtLeast(state.pages.size)
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
            book = resolveCurrentBook(state) ?: return,
            currentPage = target,
            totalPages = state.totalPageCount.coerceAtLeast(state.pages.size)
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

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun consumeStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    suspend fun buildProgressShareIntent(): Intent? = withContext(Dispatchers.IO) {
        runCatching {
            val syncSettings = comicProgressSyncStore.settingsFlow.first()
            val payload = comicProgressRepository.buildPayload(
                sourceDeviceId = syncSettings.localDeviceId,
                sourceDeviceName = syncSettings.localDeviceName
            )

            if (payload == null) {
                _uiState.update {
                    it.copy(
                        statusMessage = "共有できる読書進捗がありません",
                        errorMessage = null
                    )
                }
                return@withContext null
            }

            var switchedFromLanFallback = false
            if (syncSettings.registeredDevices.isNotEmpty()) {
                val pushResult = comicProgressLanSyncManager.pushPayloadToRegisteredDevices(
                    payloadText = payload.toString()
                )
                if (pushResult.successCount > 0) {
                    val suffix = if (pushResult.failureCount > 0) {
                        "（${pushResult.failureCount}台は未到達）"
                    } else {
                        ""
                    }
                    _uiState.update {
                        it.copy(
                            statusMessage = "登録端末へ進捗を送信しました$suffix",
                            errorMessage = null
                        )
                    }
                    return@withContext null
                }

                if (pushResult.attemptedCount > 0) {
                    switchedFromLanFallback = true
                }
            }

            val exportDir = ensureProgressShareDirectory()
            exportDir.listFiles()
                ?.filter { file -> file.isFile && file.name.startsWith(COMIC_PROGRESS_EXPORT_PREFIX) }
                ?.forEach { file -> runCatching { file.delete() } }

            val exportFile = File(
                exportDir,
                "${COMIC_PROGRESS_EXPORT_PREFIX}${System.currentTimeMillis()}.json"
            )

            exportFile.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val exportUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.logprovider",
                exportFile
            )

            _uiState.update {
                it.copy(
                    statusMessage = if (switchedFromLanFallback) {
                        "LAN共有に失敗したため、進捗共有ファイルを作成しました"
                    } else {
                        "進捗共有ファイルを作成しました"
                    },
                    errorMessage = null
                )
            }

            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, exportUri)
                putExtra(Intent.EXTRA_SUBJECT, "コミック進捗")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "コミックの読書進捗です。受信側で取り込みからJSONを選択してください。"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrElse { error ->
            _uiState.update {
                it.copy(
                    statusMessage = null,
                    errorMessage = "進捗共有の準備に失敗しました: ${error.message ?: "不明なエラー"}"
                )
            }
            null
        }
    }

    fun importProgress(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                val jsonText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { reader -> reader.readText() }
                    ?: error("ファイルを読み込めませんでした")

                val result = comicProgressRepository.importPayload(jsonText)

                if (result.updatedCount == 0) {
                    _uiState.update {
                        it.copy(
                            statusMessage = null,
                            errorMessage = if (result.skippedCount > 0) {
                                "一致するコミックが見つからず、進捗は更新されませんでした"
                            } else {
                                "進捗は更新されませんでした"
                            }
                        )
                    }
                } else {
                    val suffix = if (result.skippedCount > 0) {
                        "（${result.skippedCount}件は未一致または更新不要）"
                    } else {
                        ""
                    }
                    _uiState.update {
                        it.copy(
                            statusMessage = "進捗を${result.updatedCount}件取り込みました$suffix",
                            errorMessage = null
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        statusMessage = null,
                        errorMessage = "進捗の取込に失敗しました: ${error.message ?: "不明なエラー"}"
                    )
                }
            }
        }
    }

    fun selectComicFolder(folderId: Long?) {
        if (folderId != null) {
            val folderUi = _uiState.value.folders.firstOrNull { it.id == folderId }
            if (
                folderUi != null &&
                folderUi.isLockEnabled &&
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
                        isHidden = true
                    )
                )
                return@launch
            }

            lockConfigDao.upsert(
                LockConfig(
                    targetType = LockTargetType.COMIC_FOLDER.name,
                    targetId = folderId,
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

    fun toggleHiddenComicVisibility(show: Boolean) {
        _uiState.update { state ->
            if (!show) {
                val filtered = state.allFolders.filterNot { it.isLockEnabled }
                val resolvedSelected = if (
                    state.selectedFolderId != null &&
                    filtered.none { it.id == state.selectedFolderId }
                ) {
                    null
                } else {
                    state.selectedFolderId
                }

                state.copy(
                    showHiddenLocked = false,
                    folders = filtered,
                    selectedFolderId = resolvedSelected
                )
            } else {
                state.copy(
                    showHiddenLocked = true,
                    folders = state.allFolders
                )
            }
        }

        viewModelScope.launch {
            appSettingsStore.updateHiddenLockContentVisible(show)
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            comicBookDao.deleteById(bookId)
            if (_uiState.value.currentBookId == bookId) {
                backgroundPageLoadJob?.cancel()
                _uiState.update {
                    it.copy(
                        currentBookId = null,
                        pages = emptyList(),
                        currentPage = 0,
                        totalPageCount = 0,
                        isBookFullyLoaded = true
                    )
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
        val pageSources = loadFolderPageSources(folderUriString)

        val needsProcessing = shouldPreprocessPages(settings)
        if (!needsProcessing) {
            AppLogger.i(
                "ComicReader",
                "Skipping preprocessing for ${pageSources.size} folder pages " +
                    "(mode=${settings.mode}, trim=${settings.autoTrimEnabled}, split=${settings.autoSplitEnabled})"
            )
            return pageSources.mapIndexed { index, source ->
                ComicPage(
                    index = index,
                    model = source.directUri,
                    sourceName = source.sourceName
                )
            }
        }

        return buildPageModelsFromSources(
            sources = pageSources,
            settings = settings,
            logDescription = "folder"
        )
    }

    private suspend fun loadArchivePages(
        archiveUriString: String,
        settings: ComicReaderSettings
    ): List<ComicPage> {
        val pageSources = loadArchivePageSources(archiveUriString)
        val fileName = resolveDisplayName(archiveUriString.toUri()).lowercase(Locale.getDefault())

        AppLogger.i("ComicReader", "Archive extracted: ${pageSources.size} pages from $fileName")

        val needsProcessing = shouldPreprocessPages(settings)
        if (!needsProcessing) {
            AppLogger.i(
                "ComicReader",
                "Skipping preprocessing for ${pageSources.size} extracted pages " +
                    "(mode=${settings.mode}, trim=${settings.autoTrimEnabled}, split=${settings.autoSplitEnabled})"
            )
            return pageSources.mapIndexed { index, source ->
                ComicPage(
                    index = index,
                    model = source.directUri,
                    sourceName = source.sourceName
                )
            }
        }

        return buildPageModelsFromSources(
            sources = pageSources,
            settings = settings,
            logDescription = "extracted"
        )
    }

    private suspend fun loadFolderPageSources(folderUriString: String): List<ComicPageSource> {
        val folderUri = folderUriString.toUri()
        val root = resolveFolderDocument(folderUri) ?: return emptyList()
        return root.listFiles()
            .filter { file ->
                file.isFile && file.name.hasImageExtension()
            }
            .sortedWith { left, right ->
                compareNaturally(left.name.orEmpty(), right.name.orEmpty())
            }
            .map { document ->
                ComicPageSource(
                    sourceName = document.name ?: "page",
                    directUri = document.uri,
                    signature = resolveContentSignature(document),
                    openStream = { context.contentResolver.openInputStream(document.uri) }
                )
            }
    }

    private suspend fun loadArchivePageSources(archiveUriString: String): List<ComicPageSource> {
        val archiveUri = archiveUriString.toUri()
        val fileName = resolveDisplayName(archiveUri).lowercase(Locale.getDefault())
        val archiveSignature = resolveContentSignature(archiveUri)

        val cacheDir = ensureArchiveCacheDir(archiveSignature)
        var pages = cacheDir.listFiles()
            ?.filter { file -> file.isFile && file.name.hasImageExtension() }
            ?.sortedWith { left, right -> compareNaturally(left.name, right.name) }
            .orEmpty()

        if (pages.isEmpty()) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()

            if (fileName.endsWith(".cbr") || fileName.endsWith(".rar")) {
                extractRarImages(archiveUri, cacheDir)
            } else {
                extractZipImages(archiveUri, cacheDir)
            }

            pages = cacheDir.listFiles()
                ?.filter { file -> file.isFile && file.name.hasImageExtension() }
                ?.sortedWith { left, right -> compareNaturally(left.name, right.name) }
                .orEmpty()
        }

        AppLogger.i("ComicReader", "Archive extracted: ${pages.size} pages from $fileName")

        return pages.map { file ->
            ComicPageSource(
                sourceName = file.name,
                directUri = file.toUri(),
                signature = resolveContentSignature(file),
                openStream = { file.inputStream() }
            )
        }
    }

    private suspend fun buildPageModelsFromSources(
        sources: List<ComicPageSource>,
        settings: ComicReaderSettings,
        logDescription: String? = null,
        startingPageIndex: Int = 0
    ): List<ComicPage> {
        if (sources.isEmpty()) {
            return emptyList()
        }

        val parallelism = comicProcessingParallelism(sources.size)
        if (logDescription != null) {
            AppLogger.i(
                "ComicReader",
                "Materializing ${sources.size} $logDescription pages with concurrency=$parallelism"
            )
        }

        val semaphore = Semaphore(parallelism)
        val results = coroutineScope {
            sources.mapIndexed { sourceIndex, source ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val processedUris = materializePageSource(source, settings)
                        IndexedValue(sourceIndex, source.sourceName to processedUris)
                    }
                }
            }.awaitAll().sortedBy { it.index }
        }

        val output = mutableListOf<ComicPage>()
        var index = startingPageIndex

        for (result in results) {
            val (sourceName, processedUris) = result.value
            for (processedUri in processedUris) {
                output += ComicPage(
                    index = index,
                    model = processedUri,
                    sourceName = sourceName
                )
                index += 1
            }
        }

        return output
    }

    private suspend fun openBookProgressively(
        book: ComicBook,
        settings: ComicReaderSettings
    ): ProgressivePageLoadResult {
        val sources = when (book.sourceType) {
            "FOLDER" -> loadFolderPageSources(book.sourceUri)
            "ARCHIVE" -> loadArchivePageSources(book.sourceUri)
            else -> emptyList()
        }

        if (sources.isEmpty()) {
            return ProgressivePageLoadResult(
                initialPages = emptyList(),
                remainingSources = emptyList(),
                totalPageHint = 0
            )
        }

        val targetLoadedPages = (book.lastReadPage.coerceAtLeast(0) + INITIAL_PAGE_PREFETCH_COUNT + 1)
            .coerceAtLeast(1)
        val initialPages = mutableListOf<ComicPage>()
        var nextIndex = 0
        var consumedSources = 0

        for (source in sources) {
            val processedUris = materializePageSource(source, settings)
            for (processedUri in processedUris) {
                initialPages += ComicPage(
                    index = nextIndex,
                    model = processedUri,
                    sourceName = source.sourceName
                )
                nextIndex += 1
            }
            consumedSources += 1
            if (initialPages.size >= targetLoadedPages) {
                break
            }
        }

        val totalPageHint = maxOf(book.totalPages, sources.size, initialPages.size)
        AppLogger.i(
            "ComicReader",
            "Prepared ${initialPages.size} initial pages from $consumedSources/${sources.size} sources; " +
                "continuingRemaining=${sources.size - consumedSources}"
        )

        return ProgressivePageLoadResult(
            initialPages = initialPages,
            remainingSources = sources.drop(consumedSources),
            totalPageHint = totalPageHint
        )
    }

    private fun startBackgroundPageLoad(
        bookId: Long,
        loadGeneration: Long,
        settings: ComicReaderSettings,
        remainingSources: List<ComicPageSource>,
        startingPageIndex: Int,
        totalPageHint: Int
    ) {
        backgroundPageLoadJob = viewModelScope.launch(Dispatchers.IO) {
            var nextPageIndex = startingPageIndex
            try {
                AppLogger.i(
                    "ComicReader",
                    "Continuing background materialization for ${remainingSources.size} remaining sources"
                )

                for (chunk in remainingSources.chunked(BACKGROUND_PAGE_SOURCE_BATCH_SIZE)) {
                    val batchPages = buildPageModelsFromSources(
                        sources = chunk,
                        settings = settings,
                        startingPageIndex = nextPageIndex
                    )
                    nextPageIndex += batchPages.size
                    publishLoadedPageBatch(
                        bookId = bookId,
                        loadGeneration = loadGeneration,
                        batchPages = batchPages,
                        totalPageCount = maxOf(totalPageHint, nextPageIndex),
                        isComplete = false
                    )
                }

                publishLoadedPageBatch(
                    bookId = bookId,
                    loadGeneration = loadGeneration,
                    batchPages = emptyList(),
                    totalPageCount = maxOf(totalPageHint, nextPageIndex),
                    isComplete = true
                )

                val state = _uiState.value
                if (state.currentBookId == bookId && loadGeneration == pageLoadGeneration) {
                    saveProgress(
                        book = resolveCurrentBook(state) ?: return@launch,
                        currentPage = state.currentPage.coerceAtMost((state.totalPageCount - 1).coerceAtLeast(0)),
                        totalPages = state.totalPageCount.coerceAtLeast(state.pages.size)
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: OutOfMemoryError) {
                AppLogger.e("ComicReader", "Background page materialization OOM: $bookId")
                publishBackgroundLoadFailure(bookId, loadGeneration, "残りのページ読み込みでメモリ不足が発生しました")
            } catch (error: Exception) {
                AppLogger.e("ComicReader", "Background page materialization failed: $bookId", error)
                publishBackgroundLoadFailure(bookId, loadGeneration, "残りのページ読み込みに失敗しました")
            }
        }
    }

    private fun publishLoadedPageBatch(
        bookId: Long,
        loadGeneration: Long,
        batchPages: List<ComicPage>,
        totalPageCount: Int,
        isComplete: Boolean
    ) {
        _uiState.update { state ->
            if (state.currentBookId != bookId || loadGeneration != pageLoadGeneration) {
                state
            } else {
                val mergedPages = if (batchPages.isEmpty()) {
                    state.pages
                } else {
                    state.pages + batchPages
                }
                state.copy(
                    pages = mergedPages,
                    totalPageCount = maxOf(totalPageCount, mergedPages.size),
                    isBookFullyLoaded = if (isComplete) true else state.isBookFullyLoaded,
                    errorMessage = if (isComplete) state.errorMessage else null
                )
            }
        }
    }

    private fun publishBackgroundLoadFailure(
        bookId: Long,
        loadGeneration: Long,
        message: String
    ) {
        _uiState.update { state ->
            if (state.currentBookId != bookId || loadGeneration != pageLoadGeneration) {
                state
            } else {
                state.copy(
                    isBookFullyLoaded = true,
                    errorMessage = message
                )
            }
        }
    }

    private suspend fun materializePageSource(
        source: ComicPageSource,
        settings: ComicReaderSettings
    ): List<Uri> {
        return processAndMaterializePages(
            cacheKey = buildProcessedPageCacheKey(source.signature, settings),
            sourceName = source.sourceName,
            openStream = source.openStream,
            settings = settings
        )
    }

    private suspend fun processAndMaterializePages(
        cacheKey: String,
        sourceName: String,
        openStream: () -> java.io.InputStream?,
        settings: ComicReaderSettings
    ): List<Uri> {
        val cacheDir = ensureProcessedCacheDir(cacheKey)
        val cachedPages = cacheDir.listFiles()
            ?.filter { file -> file.isFile && file.name.hasImageExtension() }
            ?.sortedWith { left, right -> compareNaturally(left.name, right.name) }
            .orEmpty()
        if (cachedPages.isNotEmpty()) {
            return cachedPages.map { it.toUri() }
        }

        return try {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()

            val bytes = openStream()?.use { it.readBytes() }
                ?: return emptyList()
            AppLogger.d("ComicReader", "Processing page: $sourceName (${bytes.size} bytes)")

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                AppLogger.w(
                    "ComicReader",
                    "Invalid image: $sourceName (${bounds.outWidth}x${bounds.outHeight})"
                )
                return emptyList()
            }

            AppLogger.d("ComicReader", "Image size: ${bounds.outWidth}x${bounds.outHeight}")

            if (!shouldPreprocessPages(settings)) {
                return writeSingleTempPage(bytes, cacheDir, "single")
            }

            val processed = runCatching {
                pageProcessor.process(bytes = bytes, settings = settings)
            }.getOrElse { error ->
                AppLogger.w("ComicReader", "PageProcessor failed for $sourceName", error)
                emptyList()
            }

            if (processed.isEmpty()) {
                return writeSingleTempPage(bytes, cacheDir, "fallback")
            }

            val fullRect = android.graphics.Rect(0, 0, bounds.outWidth, bounds.outHeight)
            if (processed.size == 1 && processed.first().rect == fullRect) {
                return writeSingleTempPage(bytes, cacheDir, "full")
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
            ) ?: return emptyList()

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
                    cacheDir,
                    index.toString().padStart(3, '0') + ".jpg"
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
            cacheDir.deleteRecursively()
            emptyList()
        } catch (error: Exception) {
            AppLogger.e("ComicReader", "Error processing page: $sourceName", error)
            cacheDir.deleteRecursively()
            emptyList()
        }
    }

    private fun comicProcessingParallelism(sourceCount: Int): Int {
        val processors = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        return minOf(sourceCount.coerceAtLeast(1), processors.coerceAtMost(3))
    }

    private fun shouldPreprocessPages(settings: ComicReaderSettings): Boolean {
        return settings.autoTrimEnabled ||
            (settings.mode == ReaderMode.PAGE && settings.autoSplitEnabled)
    }

    private fun writeSingleTempPage(bytes: ByteArray, outputDir: File, fileName: String): List<Uri> {
        outputDir.mkdirs()
        val file = File(outputDir, "$fileName.jpg")
        FileOutputStream(file).use { output ->
            output.write(bytes)
        }
        return listOf(file.toUri())
    }

    private fun ensureProcessedCacheDir(cacheKey: String): File {
        val dir = File(File(context.cacheDir, "comic_processed_pages"), cacheKey)
        dir.mkdirs()
        return dir
    }

    private fun ensureArchiveCacheDir(signature: ContentCacheSignature): File {
        val dir = File(File(context.cacheDir, "comic_archive_pages"), signature.cacheKey)
        dir.mkdirs()
        return dir
    }

    private fun resolveContentSignature(document: DocumentFile): ContentCacheSignature {
        return ContentCacheSignature(
            identifier = document.uri.toString(),
            lastModified = document.lastModified(),
            sizeBytes = document.length()
        )
    }

    private fun resolveContentSignature(file: File): ContentCacheSignature {
        return ContentCacheSignature(
            identifier = file.absolutePath,
            lastModified = file.lastModified(),
            sizeBytes = file.length()
        )
    }

    private fun resolveContentSignature(uri: Uri): ContentCacheSignature {
        val document = runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
        return if (document != null) {
            resolveContentSignature(document)
        } else {
            ContentCacheSignature(
                identifier = uri.toString(),
                lastModified = 0L,
                sizeBytes = 0L
            )
        }
    }

    private fun buildProcessedPageCacheKey(
        signature: ContentCacheSignature,
        settings: ComicReaderSettings
    ): String {
        return hashComicCacheKey(
            buildString {
                append(signature.cacheKey)
                append('|')
                append(settings.mode.name)
                append('|')
                append(settings.readingDirection.name)
                append('|')
                append(settings.autoTrimEnabled)
                append('|')
                append(settings.autoSplitEnabled)
                append('|')
                append(settings.smartSplitEnabled)
                append('|')
                append(settings.splitThreshold)
                append('|')
                append(settings.splitOffsetPercent)
                append('|')
                append(settings.trimTolerance)
                append('|')
                append(settings.trimSafetyMargin)
                append('|')
                append(settings.trimSensitivity.name)
                append('|')
                append(settings.zoomMax)
            }
        )
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
                    @Suppress("DEPRECATION")
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
            resolveFolderDocument(uri)?.name
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
                    val root = resolveFolderDocument(uri) ?: return null
                    root.listFiles()
                        .filter { file -> file.isFile && file.name.hasImageExtension() }
                        .minWithOrNull { left, right ->
                            compareNaturally(left.name.orEmpty(), right.name.orEmpty())
                        }
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
                            @Suppress("DEPRECATION")
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
                val root = resolveFolderDocument(uri)
                root?.listFiles()?.count { it.isFile && it.name.hasImageExtension() } ?: 0
            }

            else -> 0
        }
    }

    private fun resolveCurrentBook(state: ComicReaderUiState): ComicBook? {
        val currentBookId = state.currentBookId ?: return null
        return state.books.firstOrNull { it.id == currentBookId }
    }

    private fun saveProgress(book: ComicBook, currentPage: Int, totalPages: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            comicProgressRepository.saveProgress(
                book = book,
                currentPage = currentPage,
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

    private fun ensureProgressShareDirectory(): File {
        val dir = File(context.filesDir, "logs")
        dir.mkdirs()
        return dir
    }
}

private data class ContentCacheSignature(
    val identifier: String,
    val lastModified: Long,
    val sizeBytes: Long
) {
    val cacheKey: String = hashComicCacheKey("$identifier|$lastModified|$sizeBytes")
}

private fun hashComicCacheKey(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun String?.hasImageExtension(): Boolean {
    val ext = this
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.getDefault())
        ?: return false
    return ext in SUPPORTED_IMAGE_EXTENSIONS
}

private fun String?.hasArchiveExtension(): Boolean {
    val ext = this
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.getDefault())
        ?: return false
    return ext in SUPPORTED_ARCHIVE_EXTENSIONS
}

private fun sanitizeFileName(original: String): String {
    return original.replace("/", "_").replace("\\", "_")
}

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
import com.example.litemediaplayer.data.ComicBook
import com.example.litemediaplayer.data.ComicBookDao
import com.example.litemediaplayer.data.LockConfigDao
import com.example.litemediaplayer.lock.LockManager
import com.example.litemediaplayer.lock.LockTargetType
import com.example.litemediaplayer.settings.AppSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
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

data class ComicPage(
    val index: Int,
    val model: Any,
    val sourceName: String
)

data class ComicReaderUiState(
    val books: List<ComicBook> = emptyList(),
    val currentBookId: Long? = null,
    val pages: List<ComicPage> = emptyList(),
    val currentPage: Int = 0,
    val settings: ComicReaderSettings = ComicReaderSettings(),
    val errorMessage: String? = null
)

@HiltViewModel
class ComicReaderViewModel @Inject constructor(
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
            comicSettings.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun registerComicFolder(folderUri: Uri, resolver: ContentResolver) {
        registerSource(
            sourceUri = folderUri,
            sourceType = "FOLDER",
            resolver = resolver
        )
    }

    fun registerComicArchive(fileUri: Uri, resolver: ContentResolver) {
        registerSource(
            sourceUri = fileUri,
            sourceType = "ARCHIVE",
            resolver = resolver
        )
    }

    fun openBook(bookId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = comicBookDao.findById(bookId)
            if (book == null) {
                _uiState.update { it.copy(errorMessage = "書籍が見つかりません") }
                return@launch
            }

            if (lockManager.isLocked(LockTargetType.COMIC_SHELF, bookId)) {
                _uiState.update { it.copy(errorMessage = "この書籍はロックされています") }
                return@launch
            }

            val settings = _uiState.value.settings
            val pages = when (book.sourceType) {
                "FOLDER" -> loadFolderPages(book.sourceUri, settings)
                "ARCHIVE" -> loadArchivePages(book.sourceUri, settings)
                else -> emptyList()
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

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
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

        return buildPageModelsFromDocuments(imageFiles, settings)
    }

    private suspend fun loadArchivePages(
        archiveUriString: String,
        settings: ComicReaderSettings
    ): List<ComicPage> {
        val archiveUri = archiveUriString.toUri()
        val fileName = resolveDisplayName(archiveUri).lowercase(Locale.getDefault())
        if (fileName.endsWith(".cbr")) {
            _uiState.update { it.copy(errorMessage = "CBR はこの実装では未対応です") }
            return emptyList()
        }

        val cacheDir = File(context.cacheDir, "comic_archive_pages")
        cacheDir.mkdirs()
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }

        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            ZipInputStream(input).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name.hasImageExtension()) {
                        val outFile = File(cacheDir, sanitizeFileName(name))
                        FileOutputStream(outFile).use { output ->
                            zipStream.copyTo(output)
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
        }

        val pages = cacheDir.listFiles()
            ?.filter { file -> file.isFile && file.name.hasImageExtension() }
            ?.sortedBy { file -> file.name.lowercase(Locale.getDefault()) }
            .orEmpty()

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
        val sourceBytes = withContext(Dispatchers.IO) {
            openStream()?.use { it.readBytes() }
        } ?: return emptyList()

        val processed = pageProcessor.process(
            inputStream = ByteArrayInputStream(sourceBytes),
            settings = settings
        )

        if (processed.isEmpty()) {
            return emptyList()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
        val fullRect = android.graphics.Rect(0, 0, bounds.outWidth, bounds.outHeight)

        if (processed.size == 1 && processed.first().rect == fullRect) {
            return writeSingleTempPage(sourceBytes, sourceName)
        }

        val sourceBitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return emptyList()
        val outputUris = mutableListOf<Uri>()

        processed.forEachIndexed { index, page ->
            val rect = page.rect
            val cropWidth = rect.width().coerceAtLeast(1)
            val cropHeight = rect.height().coerceAtLeast(1)
            val cropped = Bitmap.createBitmap(sourceBitmap, rect.left, rect.top, cropWidth, cropHeight)
            val file = File(
                ensureProcessedCacheDir(),
                "${sourceName.hashCode()}_${System.nanoTime()}_${index}.jpg"
            )
            FileOutputStream(file).use { output ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            cropped.recycle()
            outputUris += file.toUri()
        }

        sourceBitmap.recycle()
        return outputUris
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

    private fun resolveDisplayName(uri: Uri): String {
        return DocumentFile.fromTreeUri(context, uri)?.name
            ?: DocumentFile.fromSingleUri(context, uri)?.name
            ?: "comic"
    }

    private fun resolveCoverUri(uri: Uri, sourceType: String): String? {
        return when (sourceType) {
            "FOLDER" -> {
                val root = DocumentFile.fromTreeUri(context, uri) ?: return null
                root.listFiles()
                    .firstOrNull { file -> file.isFile && file.name.hasImageExtension() }
                    ?.uri
                    ?.toString()
            }

            else -> null
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

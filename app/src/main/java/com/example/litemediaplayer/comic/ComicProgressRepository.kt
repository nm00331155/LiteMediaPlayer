package com.example.litemediaplayer.comic

import com.example.litemediaplayer.data.ComicBook
import com.example.litemediaplayer.data.ComicBookDao
import com.example.litemediaplayer.data.ComicProgressDao
import com.example.litemediaplayer.data.ComicProgressEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

@Singleton
class ComicProgressRepository @Inject constructor(
    private val comicProgressDao: ComicProgressDao,
    private val comicBookDao: ComicBookDao
) {
    private val backfillMutex = Mutex()
    private var initialBackfillDone = false

    suspend fun ensureBackfilledFromBooks() {
        if (initialBackfillDone) {
            return
        }

        backfillMutex.withLock {
            if (initialBackfillDone) {
                return
            }

            comicBookDao.findAll().forEach { book ->
                val item = book.toProgressTransferItemOrNull() ?: return@forEach
                mergeImportedProgress(
                    sourceUri = book.sourceUri,
                    title = book.title,
                    sourceType = book.sourceType,
                    totalPages = book.totalPages,
                    incoming = item
                )
            }

            initialBackfillDone = true
        }
    }

    internal suspend fun buildPayload(
        sourceDeviceId: String? = null,
        sourceDeviceName: String? = null
    ): JSONObject? {
        ensureBackfilledFromBooks()

        val items = comicProgressDao.findAll()
            .mapNotNull { entry -> entry.toProgressTransferItemOrNull() }
        if (items.isEmpty()) {
            return null
        }

        return buildComicProgressPayload(
            items = items,
            sourceDeviceId = sourceDeviceId,
            sourceDeviceName = sourceDeviceName
        )
    }

    internal suspend fun importPayload(payloadText: String): ComicProgressImportResult {
        return importItems(parseComicProgressPayload(payloadText))
    }

    internal suspend fun importItems(items: List<ComicProgressTransferItem>): ComicProgressImportResult {
        ensureBackfilledFromBooks()

        val remainingBooks = comicBookDao.findAll().toMutableList()
        var updatedCount = 0
        var skippedCount = 0

        items.forEach { item ->
            val target = findBestProgressTarget(remainingBooks, item)
            if (target != null) {
                remainingBooks.remove(target)
            }

            val entryChanged = if (target != null) {
                mergeImportedProgress(
                    sourceUri = target.sourceUri,
                    title = target.title,
                    sourceType = target.sourceType,
                    totalPages = maxOf(target.totalPages, item.totalPages),
                    incoming = item
                )
            } else {
                mergeImportedProgress(
                    sourceUri = null,
                    title = item.title,
                    sourceType = item.sourceType,
                    totalPages = item.totalPages,
                    incoming = item
                )
            }

            val bookChanged = if (target != null) {
                val merged = mergeProgress(target, item)
                if (merged != null) {
                    comicBookDao.updateProgress(
                        bookId = target.id,
                        lastReadPage = merged.lastReadPage,
                        totalPages = merged.totalPages,
                        readStatus = merged.readStatus
                    )
                    true
                } else {
                    false
                }
            } else {
                false
            }

            if (entryChanged || bookChanged) {
                updatedCount += 1
            } else {
                skippedCount += 1
            }
        }

        return ComicProgressImportResult(
            updatedCount = updatedCount,
            skippedCount = skippedCount
        )
    }

    suspend fun resolveProgressForBook(book: ComicBook): ComicProgressEntry? {
        ensureBackfilledFromBooks()

        val candidate = findMatchingEntry(
            sourceUri = book.sourceUri,
            title = book.title,
            sourceType = book.sourceType,
            totalPages = book.totalPages
        )

        if (candidate == null) {
            val legacyItem = book.toProgressTransferItemOrNull() ?: return null
            mergeImportedProgress(
                sourceUri = book.sourceUri,
                title = book.title,
                sourceType = book.sourceType,
                totalPages = book.totalPages,
                incoming = legacyItem
            )
            return comicProgressDao.findBySourceUri(book.sourceUri)
        }

        val needsLink =
            candidate.sourceUri != book.sourceUri ||
                candidate.title != book.title ||
                candidate.totalPages < book.totalPages
        if (!needsLink) {
            return candidate
        }

        mergeImportedProgress(
            sourceUri = book.sourceUri,
            title = book.title,
            sourceType = book.sourceType,
            totalPages = maxOf(book.totalPages, candidate.totalPages),
            incoming = candidate.toTransferItem()
        )
        return comicProgressDao.findBySourceUri(book.sourceUri)
            ?: findMatchingEntry(
                sourceUri = book.sourceUri,
                title = book.title,
                sourceType = book.sourceType,
                totalPages = maxOf(book.totalPages, candidate.totalPages)
            )
    }

    suspend fun syncProgressToBooks(books: List<ComicBook>) {
        ensureBackfilledFromBooks()

        books.forEach { book ->
            val entry = resolveProgressForBook(book) ?: return@forEach
            val merged = mergeProgress(book, entry.toTransferItem()) ?: return@forEach
            comicBookDao.updateProgress(
                bookId = book.id,
                lastReadPage = merged.lastReadPage,
                totalPages = merged.totalPages,
                readStatus = merged.readStatus
            )
        }
    }

    suspend fun saveProgress(
        book: ComicBook,
        currentPage: Int,
        totalPages: Int,
        readStatus: String
    ) {
        ensureBackfilledFromBooks()

        upsertCurrentProgress(
            sourceUri = book.sourceUri,
            title = book.title,
            sourceType = book.sourceType,
            totalPages = totalPages,
            lastReadPage = currentPage,
            readStatus = readStatus
        )

        comicBookDao.updateProgress(
            bookId = book.id,
            lastReadPage = currentPage,
            totalPages = totalPages,
            readStatus = readStatus
        )
    }

    private suspend fun mergeImportedProgress(
        sourceUri: String?,
        title: String,
        sourceType: String,
        totalPages: Int,
        incoming: ComicProgressTransferItem
    ): Boolean {
        val existing = findMatchingEntry(sourceUri, title, sourceType, totalPages)
        val merged = when {
            existing != null -> mergeProgress(existing, incoming)
            incoming.hasProgress() -> MergedComicProgress(
                lastReadPage = normalizeProgressPage(
                    currentPage = incoming.lastReadPage,
                    totalPages = maxOf(totalPages, incoming.totalPages),
                    readStatus = incoming.readStatus
                ),
                totalPages = maxOf(totalPages, incoming.totalPages),
                readStatus = incoming.readStatus.ifBlank { "UNREAD" }
            )

            else -> null
        }

        if (existing == null && merged == null) {
            return false
        }

        val resolvedTotalPages = merged?.totalPages
            ?: maxOf(existing?.totalPages ?: 0, totalPages, incoming.totalPages)
        val resolvedLastReadPage = merged?.lastReadPage
            ?: normalizeProgressPage(
                currentPage = existing?.lastReadPage ?: incoming.lastReadPage,
                totalPages = resolvedTotalPages,
                readStatus = existing?.readStatus ?: incoming.readStatus
            )
        val resolvedReadStatus = merged?.readStatus
            ?: (existing?.readStatus ?: incoming.readStatus.ifBlank { "UNREAD" })

        val updated = ComicProgressEntry(
            id = existing?.id ?: 0,
            sourceUri = sourceUri ?: existing?.sourceUri,
            title = title,
            normalizedTitle = normalizeComicTitle(title),
            sourceType = sourceType,
            totalPages = resolvedTotalPages,
            lastReadPage = resolvedLastReadPage,
            readStatus = resolvedReadStatus,
            updatedAt = if (existing == null || merged != null) {
                System.currentTimeMillis()
            } else {
                existing.updatedAt
            }
        )

        if (!hasEntryChanged(existing, updated)) {
            return false
        }

        comicProgressDao.upsert(updated)
        return true
    }

    private suspend fun upsertCurrentProgress(
        sourceUri: String,
        title: String,
        sourceType: String,
        totalPages: Int,
        lastReadPage: Int,
        readStatus: String
    ) {
        val existing = findMatchingEntry(sourceUri, title, sourceType, totalPages)
        val resolvedTotalPages = maxOf(existing?.totalPages ?: 0, totalPages)
        val updated = ComicProgressEntry(
            id = existing?.id ?: 0,
            sourceUri = sourceUri,
            title = title,
            normalizedTitle = normalizeComicTitle(title),
            sourceType = sourceType,
            totalPages = resolvedTotalPages,
            lastReadPage = normalizeProgressPage(
                currentPage = lastReadPage,
                totalPages = resolvedTotalPages,
                readStatus = readStatus
            ),
            readStatus = readStatus,
            updatedAt = System.currentTimeMillis()
        )

        if (!hasEntryChanged(existing, updated)) {
            return
        }

        comicProgressDao.upsert(updated)
    }

    private suspend fun findMatchingEntry(
        sourceUri: String?,
        title: String,
        sourceType: String,
        totalPages: Int
    ): ComicProgressEntry? {
        if (!sourceUri.isNullOrBlank()) {
            comicProgressDao.findBySourceUri(sourceUri)?.let { return it }
        }

        val candidates = comicProgressDao.findCandidates(normalizeComicTitle(title), sourceType)
        return candidates.firstOrNull { !sourceUri.isNullOrBlank() && it.sourceUri == sourceUri }
            ?: candidates.firstOrNull {
                it.sourceUri.isNullOrBlank() && pageCountsMatch(it.totalPages, totalPages)
            }
            ?: candidates.firstOrNull { it.sourceUri.isNullOrBlank() }
            ?: candidates.firstOrNull { pageCountsMatch(it.totalPages, totalPages) }
            ?: candidates.firstOrNull()
    }

    private fun hasEntryChanged(
        existing: ComicProgressEntry?,
        updated: ComicProgressEntry
    ): Boolean {
        if (existing == null) {
            return true
        }

        return existing.sourceUri != updated.sourceUri ||
            existing.title != updated.title ||
            existing.normalizedTitle != updated.normalizedTitle ||
            existing.sourceType != updated.sourceType ||
            existing.totalPages != updated.totalPages ||
            existing.lastReadPage != updated.lastReadPage ||
            existing.readStatus != updated.readStatus
    }
}

private fun ComicProgressEntry.toTransferItem(): ComicProgressTransferItem {
    return ComicProgressTransferItem(
        title = title,
        sourceType = sourceType,
        totalPages = totalPages,
        lastReadPage = lastReadPage,
        readStatus = readStatus
    )
}
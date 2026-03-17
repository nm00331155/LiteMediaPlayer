package com.example.litemediaplayer.comic

import com.example.litemediaplayer.data.ComicBook
import com.example.litemediaplayer.data.ComicBookDao
import com.example.litemediaplayer.data.ComicProgressDao
import com.example.litemediaplayer.data.ComicProgressEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ComicProgressRepositoryTest {

    @Test
    fun importItems_keepsStandaloneProgressWhenBookDoesNotExist() = runBlocking {
        val bookDao = FakeRepositoryComicBookDao()
        val progressDao = FakeComicProgressDao()
        val repository = ComicProgressRepository(progressDao, bookDao)

        val result = repository.importItems(
            listOf(
                ComicProgressTransferItem(
                    title = "Standalone",
                    sourceType = "ARCHIVE",
                    totalPages = 8,
                    lastReadPage = 4,
                    readStatus = "IN_PROGRESS"
                )
            )
        )

        val stored = progressDao.findAll().single()
        assertEquals(1, result.updatedCount)
        assertEquals(0, result.skippedCount)
        assertEquals("Standalone", stored.title)
        assertEquals(4, stored.lastReadPage)
        assertEquals("IN_PROGRESS", stored.readStatus)
    }

    @Test
    fun syncProgressToBooks_appliesStandaloneProgressToMatchingBook() = runBlocking {
        val bookDao = FakeRepositoryComicBookDao(
            mutableListOf(
                ComicBook(
                    id = 1,
                    title = "Sample.cbz",
                    sourceUri = "content://sample",
                    sourceType = "ARCHIVE",
                    coverUri = null,
                    lastReadPage = 0,
                    totalPages = 12,
                    readStatus = "UNREAD"
                )
            )
        )
        val progressDao = FakeComicProgressDao(
            mutableListOf(
                ComicProgressEntry(
                    id = 1,
                    sourceUri = null,
                    title = "Sample",
                    normalizedTitle = normalizeComicTitle("Sample"),
                    sourceType = "ARCHIVE",
                    totalPages = 12,
                    lastReadPage = 7,
                    readStatus = "IN_PROGRESS",
                    updatedAt = 10L
                )
            )
        )
        val repository = ComicProgressRepository(progressDao, bookDao)

        repository.syncProgressToBooks(bookDao.findAll())

        val updatedBook = bookDao.findById(1) ?: error("book not found")
        val linkedEntry = progressDao.findBySourceUri("content://sample")

        assertEquals(7, updatedBook.lastReadPage)
        assertEquals("IN_PROGRESS", updatedBook.readStatus)
        assertNotNull(linkedEntry)
        assertEquals("Sample.cbz", linkedEntry?.title)
    }
}

private class FakeComicProgressDao(
    private val entries: MutableList<ComicProgressEntry> = mutableListOf()
) : ComicProgressDao {
    override fun observeAll(): Flow<List<ComicProgressEntry>> {
        return flowOf(entries.sortedByDescending { it.updatedAt })
    }

    override suspend fun findAll(): List<ComicProgressEntry> {
        return entries.sortedByDescending { it.updatedAt }
    }

    override suspend fun findBySourceUri(sourceUri: String): ComicProgressEntry? {
        return entries.firstOrNull { it.sourceUri == sourceUri }
    }

    override suspend fun findCandidates(
        normalizedTitle: String,
        sourceType: String
    ): List<ComicProgressEntry> {
        return entries.filter {
            it.normalizedTitle == normalizedTitle && it.sourceType == sourceType
        }.sortedByDescending { it.updatedAt }
    }

    override suspend fun upsert(entry: ComicProgressEntry): Long {
        val existingIndex = when {
            entry.id != 0L -> entries.indexOfFirst { it.id == entry.id }
            !entry.sourceUri.isNullOrBlank() -> {
                entries.indexOfFirst { it.sourceUri == entry.sourceUri }
            }

            else -> entries.indexOfFirst {
                it.sourceUri.isNullOrBlank() &&
                    it.normalizedTitle == entry.normalizedTitle &&
                    it.sourceType == entry.sourceType &&
                    pageCountsMatch(it.totalPages, entry.totalPages)
            }
        }

        return if (existingIndex >= 0) {
            entries[existingIndex] = entry.copy(id = entries[existingIndex].id)
            entries[existingIndex].id
        } else {
            val newId = (entries.maxOfOrNull { it.id } ?: 0L) + 1L
            entries += entry.copy(id = newId)
            newId
        }
    }

    override suspend fun delete(entry: ComicProgressEntry) {
        entries.removeAll { it.id == entry.id }
    }
}

private class FakeRepositoryComicBookDao(
    private val books: MutableList<ComicBook> = mutableListOf()
) : ComicBookDao {
    override fun observeAll(): Flow<List<ComicBook>> = flowOf(books.toList())

    override suspend fun findAll(): List<ComicBook> = books.toList()

    override suspend fun findById(bookId: Long): ComicBook? {
        return books.firstOrNull { it.id == bookId }
    }

    override suspend fun findBySourceUri(sourceUri: String): ComicBook? {
        return books.firstOrNull { it.sourceUri == sourceUri }
    }

    override fun observeByFolder(folderId: Long): Flow<List<ComicBook>> {
        return flowOf(books.filter { it.folderId == folderId })
    }

    override suspend fun findByFolder(folderId: Long): List<ComicBook> {
        return books.filter { it.folderId == folderId }
    }

    override suspend fun upsert(book: ComicBook): Long {
        val existingIndex = books.indexOfFirst { it.id == book.id && book.id != 0L }
        return if (existingIndex >= 0) {
            books[existingIndex] = book
            book.id
        } else {
            val newId = (books.maxOfOrNull { it.id } ?: 0L) + 1L
            books += book.copy(id = newId)
            newId
        }
    }

    override suspend fun updateProgress(
        bookId: Long,
        lastReadPage: Int,
        totalPages: Int,
        readStatus: String
    ) {
        val index = books.indexOfFirst { it.id == bookId }
        if (index >= 0) {
            books[index] = books[index].copy(
                lastReadPage = lastReadPage,
                totalPages = totalPages,
                readStatus = readStatus
            )
        }
    }

    override suspend fun deleteById(bookId: Long) {
        books.removeAll { it.id == bookId }
    }

    override suspend fun deleteByFolder(folderId: Long) {
        books.removeAll { it.folderId == folderId }
    }
}
package com.example.litemediaplayer.comic

import com.example.litemediaplayer.data.ComicBook
import com.example.litemediaplayer.data.ComicBookDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicProgressTransferTest {

    @Test
    fun threeColumnDefaults_matchRequestedComicNavigation() {
        val config = TouchZoneConfig()

        assertEquals(TouchAction.NEXT_PAGE, config.leftTap)
        assertEquals(TouchAction.TOGGLE_CONTROLS, config.centerTap)
        assertEquals(TouchAction.PREV_PAGE, config.rightTap)
        assertEquals(TouchAction.SKIP_FORWARD, config.leftLongPress)
        assertEquals(TouchAction.JUMP_TO_PAGE, config.centerLongPress)
        assertEquals(TouchAction.SKIP_BACKWARD, config.rightLongPress)
    }

    @Test
    fun importPayload_updatesMatchingBookAndSkipsUnmatchedItems() = runBlocking {
        val dao = FakeComicBookDao(
            mutableListOf(
                ComicBook(
                    id = 1,
                    title = "Sample.cbz",
                    sourceUri = "content://sample",
                    sourceType = "ARCHIVE",
                    coverUri = null,
                    lastReadPage = 3,
                    totalPages = 12,
                    readStatus = "IN_PROGRESS"
                )
            )
        )

        val result = importComicProgressItems(
            comicBookDao = dao,
            items = listOf(
                ComicProgressTransferItem(
                    title = "Sample",
                    sourceType = "ARCHIVE",
                    totalPages = 12,
                    lastReadPage = 8,
                    readStatus = "IN_PROGRESS"
                ),
                ComicProgressTransferItem(
                    title = "Unknown",
                    sourceType = "ARCHIVE",
                    totalPages = 5,
                    lastReadPage = 4,
                    readStatus = "READ"
                )
            )
        )
        val updated = dao.findById(1) ?: error("book not found")

        assertEquals(1, result.updatedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(8, updated.lastReadPage)
        assertEquals(12, updated.totalPages)
        assertEquals("IN_PROGRESS", updated.readStatus)
    }
}

private class FakeComicBookDao(
    private val books: MutableList<ComicBook>
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
package com.example.litemediaplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicBookDao {
    @Query("SELECT * FROM comic_books ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ComicBook>>

    @Query("SELECT * FROM comic_books WHERE id = :bookId LIMIT 1")
    suspend fun findById(bookId: Long): ComicBook?

    @Query("SELECT * FROM comic_books WHERE sourceUri = :sourceUri LIMIT 1")
    suspend fun findBySourceUri(sourceUri: String): ComicBook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: ComicBook): Long

    @Query(
        "UPDATE comic_books SET lastReadPage = :lastReadPage, totalPages = :totalPages, readStatus = :readStatus WHERE id = :bookId"
    )
    suspend fun updateProgress(
        bookId: Long,
        lastReadPage: Int,
        totalPages: Int,
        readStatus: String
    )
}

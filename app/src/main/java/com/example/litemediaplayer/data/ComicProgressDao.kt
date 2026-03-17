package com.example.litemediaplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicProgressDao {
    @Query("SELECT * FROM comic_progress_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ComicProgressEntry>>

    @Query("SELECT * FROM comic_progress_entries ORDER BY updatedAt DESC")
    suspend fun findAll(): List<ComicProgressEntry>

    @Query("SELECT * FROM comic_progress_entries WHERE sourceUri = :sourceUri LIMIT 1")
    suspend fun findBySourceUri(sourceUri: String): ComicProgressEntry?

    @Query(
        "SELECT * FROM comic_progress_entries " +
            "WHERE normalizedTitle = :normalizedTitle AND sourceType = :sourceType " +
            "ORDER BY updatedAt DESC"
    )
    suspend fun findCandidates(normalizedTitle: String, sourceType: String): List<ComicProgressEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ComicProgressEntry): Long

    @Delete
    suspend fun delete(entry: ComicProgressEntry)
}
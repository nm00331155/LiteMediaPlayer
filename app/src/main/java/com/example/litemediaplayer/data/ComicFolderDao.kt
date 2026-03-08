package com.example.litemediaplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicFolderDao {
    @Query("SELECT * FROM comic_folders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ComicFolder>>

    @Query("SELECT * FROM comic_folders WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ComicFolder?

    @Query("SELECT * FROM comic_folders WHERE treeUri = :treeUri LIMIT 1")
    suspend fun findByTreeUri(treeUri: String): ComicFolder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: ComicFolder): Long

    @Query("UPDATE comic_folders SET coverUri = :coverUri, bookCount = :bookCount WHERE id = :id")
    suspend fun updateMeta(id: Long, coverUri: String?, bookCount: Int)

    @Query("DELETE FROM comic_folders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

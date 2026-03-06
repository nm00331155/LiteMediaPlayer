package com.example.litemediaplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoFolderDao {
    @Query("SELECT * FROM video_folders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VideoFolder>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: VideoFolder): Long

    @Query("SELECT COUNT(*) FROM video_folders WHERE treeUri = :treeUri")
    suspend fun countByTreeUri(treeUri: String): Int

    @Delete
    suspend fun delete(folder: VideoFolder)

    @Query("DELETE FROM video_folders WHERE id = :folderId")
    suspend fun deleteById(folderId: Long)
}

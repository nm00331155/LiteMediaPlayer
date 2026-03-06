package com.example.litemediaplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LockConfigDao {
    @Query("SELECT * FROM lock_configs")
    fun observeAll(): Flow<List<LockConfig>>

    @Query(
        """
        SELECT * FROM lock_configs
        WHERE targetType = :targetType
          AND ((targetId = :targetId) OR (:targetId IS NULL AND targetId IS NULL))
        LIMIT 1
        """
    )
    suspend fun findByTarget(targetType: String, targetId: Long?): LockConfig?

    @Query("SELECT * FROM lock_configs WHERE targetType = :targetType")
    fun getConfigsByType(targetType: String): Flow<List<LockConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: LockConfig): Long

    @androidx.room.Delete
    suspend fun delete(config: LockConfig)
}

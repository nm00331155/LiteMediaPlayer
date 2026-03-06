package com.example.litemediaplayer.network

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkServerDao {
    @Query("SELECT * FROM network_servers ORDER BY addedDate DESC")
    fun observeAll(): Flow<List<NetworkServer>>

    @Query("SELECT * FROM network_servers WHERE id = :serverId LIMIT 1")
    suspend fun findById(serverId: Long): NetworkServer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: NetworkServer): Long

    @Delete
    suspend fun delete(server: NetworkServer)

    @Query("DELETE FROM network_servers WHERE id = :serverId")
    suspend fun deleteById(serverId: Long)
}

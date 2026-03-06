package com.example.litemediaplayer.network

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Protocol {
    SMB,
    HTTP,
    WEBDAV
}

@Entity(tableName = "network_servers")
data class NetworkServer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int?,
    val shareName: String?,
    val basePath: String?,
    val username: String?,
    val password: String?,
    val addedDate: Long = System.currentTimeMillis()
)

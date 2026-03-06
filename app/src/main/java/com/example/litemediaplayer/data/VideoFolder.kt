package com.example.litemediaplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_folders",
    indices = [Index(value = ["treeUri"], unique = true)]
)
data class VideoFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis()
)

package com.example.litemediaplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comic_folders",
    indices = [Index(value = ["treeUri"], unique = true)]
)
data class ComicFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val treeUri: String,
    val coverUri: String? = null,
    val bookCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

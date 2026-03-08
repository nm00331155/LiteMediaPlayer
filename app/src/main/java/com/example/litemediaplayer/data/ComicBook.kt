package com.example.litemediaplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comic_books",
    indices = [Index(value = ["sourceUri"], unique = true)]
)
data class ComicBook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUri: String,
    val sourceType: String,
    val coverUri: String?,
    val lastReadPage: Int = 0,
    val totalPages: Int = 0,
    val readStatus: String = "UNREAD",
    val folderId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

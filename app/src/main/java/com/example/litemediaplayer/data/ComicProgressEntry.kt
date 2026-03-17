package com.example.litemediaplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comic_progress_entries",
    indices = [
        Index(value = ["sourceUri"], unique = true),
        Index(value = ["normalizedTitle", "sourceType"])
    ]
)
data class ComicProgressEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String? = null,
    val title: String,
    val normalizedTitle: String,
    val sourceType: String,
    val totalPages: Int = 0,
    val lastReadPage: Int = 0,
    val readStatus: String = "UNREAD",
    val updatedAt: Long = System.currentTimeMillis()
)
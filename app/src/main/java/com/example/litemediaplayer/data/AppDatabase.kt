package com.example.litemediaplayer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.litemediaplayer.network.NetworkServer
import com.example.litemediaplayer.network.NetworkServerDao

@Database(
    entities = [
        VideoFolder::class,
        ComicFolder::class,
        ComicBook::class,
        ComicProgressEntry::class,
        LockConfig::class,
        NetworkServer::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoFolderDao(): VideoFolderDao
    abstract fun comicFolderDao(): ComicFolderDao
    abstract fun comicBookDao(): ComicBookDao
    abstract fun comicProgressDao(): ComicProgressDao
    abstract fun lockConfigDao(): LockConfigDao
    abstract fun networkServerDao(): NetworkServerDao
}

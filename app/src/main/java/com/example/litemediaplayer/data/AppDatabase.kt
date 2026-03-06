package com.example.litemediaplayer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.litemediaplayer.network.NetworkServer
import com.example.litemediaplayer.network.NetworkServerDao

@Database(
    entities = [
        VideoFolder::class,
        ComicBook::class,
        LockConfig::class,
        NetworkServer::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoFolderDao(): VideoFolderDao
    abstract fun comicBookDao(): ComicBookDao
    abstract fun lockConfigDao(): LockConfigDao
    abstract fun networkServerDao(): NetworkServerDao
}

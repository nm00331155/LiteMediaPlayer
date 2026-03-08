package com.example.litemediaplayer.data

import android.content.Context
import androidx.room.Room
import com.example.litemediaplayer.network.NetworkServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lite_media_player.db"
        )
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3,
                DatabaseMigrations.MIGRATION_3_4,
                DatabaseMigrations.MIGRATION_4_5,
                DatabaseMigrations.MIGRATION_5_6
            )
            .build()
    }

    @Provides
    fun provideVideoFolderDao(database: AppDatabase): VideoFolderDao {
        return database.videoFolderDao()
    }

    @Provides
    fun provideComicBookDao(database: AppDatabase): ComicBookDao {
        return database.comicBookDao()
    }

    @Provides
    fun provideComicFolderDao(database: AppDatabase): ComicFolderDao {
        return database.comicFolderDao()
    }

    @Provides
    fun provideLockConfigDao(database: AppDatabase): LockConfigDao {
        return database.lockConfigDao()
    }

    @Provides
    fun provideNetworkServerDao(database: AppDatabase): NetworkServerDao {
        return database.networkServerDao()
    }
}

package com.example.litemediaplayer.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val dbName = "migration-test.db"

    @Test
    fun v5to6_addsComicFolderTableAndBookFolderIdColumn() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        createLegacyVersion5Database(context)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(DatabaseMigrations.MIGRATION_5_6)
            .build()
        val migrated = db.openHelper.writableDatabase
        try {
            assertTrue(hasTable(migrated, "comic_folders"))
            assertTrue(hasColumn(migrated, "comic_books", "folderId"))

            migrated.query("SELECT folderId FROM comic_books WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun v6to7_addsComicProgressTable() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        createLegacyVersion6Database(context)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(DatabaseMigrations.MIGRATION_6_7)
            .build()
        val migrated = db.openHelper.writableDatabase
        try {
            assertTrue(hasTable(migrated, "comic_progress_entries"))
            assertTrue(hasColumn(migrated, "comic_progress_entries", "normalizedTitle"))
            assertTrue(hasColumn(migrated, "comic_progress_entries", "updatedAt"))
        } finally {
            db.close()
        }
    }

    private fun createLegacyVersion5Database(context: Context) {
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            createVersion5Schema(sqlite)
            insertVersion5SeedData(sqlite)
            sqlite.version = 5
        } finally {
            sqlite.close()
        }
    }

    private fun createLegacyVersion6Database(context: Context) {
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            createVersion6Schema(sqlite)
            insertVersion6SeedData(sqlite)
            sqlite.version = 6
        } finally {
            sqlite.close()
        }
    }

    private fun createVersion5Schema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `video_folders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `treeUri` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_video_folders_treeUri` ON `video_folders` (`treeUri`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `comic_books` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `sourceUri` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `coverUri` TEXT,
                `lastReadPage` INTEGER NOT NULL,
                `totalPages` INTEGER NOT NULL,
                `readStatus` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_comic_books_sourceUri` ON `comic_books` (`sourceUri`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lock_configs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `targetType` TEXT NOT NULL,
                `targetId` INTEGER,
                `authMethod` TEXT NOT NULL,
                `pinHash` TEXT,
                `patternHash` TEXT,
                `autoLockMinutes` INTEGER NOT NULL,
                `isHidden` INTEGER NOT NULL,
                `isEnabled` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_lock_configs_targetType_targetId` ON `lock_configs` (`targetType`, `targetId`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `network_servers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `protocol` TEXT NOT NULL,
                `host` TEXT NOT NULL,
                `port` INTEGER,
                `shareName` TEXT,
                `basePath` TEXT,
                `username` TEXT,
                `password` TEXT,
                `addedDate` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createVersion6Schema(db: SQLiteDatabase) {
        createVersion5Schema(db)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `comic_folders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `displayName` TEXT NOT NULL,
                `treeUri` TEXT NOT NULL,
                `coverUri` TEXT,
                `bookCount` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_comic_folders_treeUri` ON `comic_folders` (`treeUri`)"
        )
        db.execSQL(
            "ALTER TABLE `comic_books` ADD COLUMN `folderId` INTEGER NOT NULL DEFAULT 0"
        )
    }

    private fun insertVersion5SeedData(db: SQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO `video_folders` (`id`, `treeUri`, `displayName`, `createdAt`)
            VALUES (1, 'content://video-tree', 'Videos', 1700000000000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `comic_books` (
                `id`, `title`, `sourceUri`, `sourceType`, `coverUri`,
                `lastReadPage`, `totalPages`, `readStatus`, `createdAt`
            )
            VALUES (
                1, 'Legacy Book', 'content://comic-book', 'ARCHIVE', NULL,
                0, 10, 'UNREAD', 1700000000000
            )
            """.trimIndent()
        )
    }

    private fun insertVersion6SeedData(db: SQLiteDatabase) {
        insertVersion5SeedData(db)
        db.execSQL(
            """
            INSERT INTO `comic_folders` (`id`, `displayName`, `treeUri`, `coverUri`, `bookCount`, `createdAt`)
            VALUES (1, 'Books', 'content://comic-tree', NULL, 1, 1700000000000)
            """.trimIndent()
        )
        db.execSQL(
            "UPDATE `comic_books` SET `folderId` = 1 WHERE `id` = 1"
        )
    }

    private fun hasTable(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                    return true
                }
            }
        }
        return false
    }
}

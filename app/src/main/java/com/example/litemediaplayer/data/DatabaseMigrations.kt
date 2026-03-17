package com.example.litemediaplayer.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateVideoFolders(db)
            createComicBooksTable(db)
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateVideoFolders(db)
            createComicBooksTable(db)
            createLockConfigTable(db)
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateVideoFolders(db)
            createComicBooksTable(db)
            createLockConfigTable(db)
            createNetworkServerTable(db)
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateVideoFolders(db)
            createComicBooksTable(db)
            createLockConfigTable(db)
            createNetworkServerTable(db)
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateVideoFolders(db)
            createComicBooksTable(db)
            createLockConfigTable(db)
            createNetworkServerTable(db)
            createComicFoldersTable(db)
            ensureComicBookFolderIdColumn(db)
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateVideoFolders(db)
            createComicBooksTable(db)
            createLockConfigTable(db)
            createNetworkServerTable(db)
            createComicFoldersTable(db)
            ensureComicBookFolderIdColumn(db)
            createComicProgressTable(db)
        }
    }

    private fun migrateVideoFolders(database: SupportSQLiteDatabase) {
        if (!hasTable(database, "video_folders")) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `video_folders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `treeUri` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_video_folders_treeUri` ON `video_folders` (`treeUri`)"
            )
            return
        }

        val columns = getColumns(database, "video_folders")
        if ("treeUri" in columns && "displayName" in columns && "createdAt" in columns) {
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_video_folders_treeUri` ON `video_folders` (`treeUri`)"
            )
            return
        }

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `video_folders_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `treeUri` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        val treeUriExpr = when {
            "treeUri" in columns -> "treeUri"
            "uri" in columns -> "uri"
            else -> "''"
        }
        val displayNameExpr = when {
            "displayName" in columns -> "displayName"
            "name" in columns -> "name"
            else -> "'動画フォルダ'"
        }
        val createdAtExpr = when {
            "createdAt" in columns -> "createdAt"
            "addedDate" in columns -> "addedDate"
            else -> "(strftime('%s','now') * 1000)"
        }

        database.execSQL(
            """
            INSERT INTO `video_folders_new` (`id`, `treeUri`, `displayName`, `createdAt`)
            SELECT `id`, $treeUriExpr, $displayNameExpr, $createdAtExpr
            FROM `video_folders`
            """.trimIndent()
        )

        database.execSQL("DROP TABLE `video_folders`")
        database.execSQL("ALTER TABLE `video_folders_new` RENAME TO `video_folders`")
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_video_folders_treeUri` ON `video_folders` (`treeUri`)"
        )
    }

    private fun createComicBooksTable(database: SupportSQLiteDatabase) {
        database.execSQL(
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
                `folderId` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_comic_books_sourceUri` ON `comic_books` (`sourceUri`)"
        )
        ensureComicBookFolderIdColumn(database)
    }

    private fun createComicFoldersTable(database: SupportSQLiteDatabase) {
        database.execSQL(
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
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_comic_folders_treeUri` ON `comic_folders` (`treeUri`)"
        )
    }

    private fun createComicProgressTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `comic_progress_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sourceUri` TEXT,
                `title` TEXT NOT NULL,
                `normalizedTitle` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `totalPages` INTEGER NOT NULL,
                `lastReadPage` INTEGER NOT NULL,
                `readStatus` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_comic_progress_entries_sourceUri` ON `comic_progress_entries` (`sourceUri`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_comic_progress_entries_normalizedTitle_sourceType` ON `comic_progress_entries` (`normalizedTitle`, `sourceType`)"
        )
    }

    private fun ensureComicBookFolderIdColumn(database: SupportSQLiteDatabase) {
        if (!hasTable(database, "comic_books")) {
            return
        }

        val columns = getColumns(database, "comic_books")
        if ("folderId" !in columns) {
            database.execSQL(
                "ALTER TABLE `comic_books` ADD COLUMN `folderId` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private fun createLockConfigTable(database: SupportSQLiteDatabase) {
        database.execSQL(
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
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_lock_configs_targetType_targetId` ON `lock_configs` (`targetType`, `targetId`)"
        )
    }

    private fun createNetworkServerTable(database: SupportSQLiteDatabase) {
        database.execSQL(
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

    private fun hasTable(database: SupportSQLiteDatabase, tableName: String): Boolean {
        database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun getColumns(database: SupportSQLiteDatabase, tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) {
                    columns += cursor.getString(nameIndex)
                }
            }
        }
        return columns
    }
}

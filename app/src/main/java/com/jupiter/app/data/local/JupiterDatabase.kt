package com.jupiter.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaHistoryEntity::class,
        NetworkStreamEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        VideoBookmarkEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class JupiterDatabase : RoomDatabase() {
    abstract fun mediaHistoryDao(): MediaHistoryDao
    abstract fun networkStreamDao(): NetworkStreamDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun videoBookmarkDao(): VideoBookmarkDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlistId` INTEGER NOT NULL,
                        `mediaUri` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `isNetworkStream` INTEGER NOT NULL,
                        `headersJson` TEXT,
                        `durationMs` INTEGER NOT NULL,
                        `positionOrder` INTEGER NOT NULL,
                        `addedAtEpochMs` INTEGER NOT NULL,
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId` ON `playlist_items` (`playlistId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uriHash` TEXT NOT NULL,
                        `mediaUri` TEXT NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_video_bookmarks_uriHash` ON `video_bookmarks` (`uriHash`)",
                )
            }
        }
    }
}

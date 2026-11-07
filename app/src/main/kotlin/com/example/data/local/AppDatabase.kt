/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SavedItemEntity::class, CustomFolderEntity::class, NoteSearchEntity::class, TrashEntryEntity::class, LinkPreviewEntity::class, TraktOutboxEntity::class, TraktEpisodeEntity::class, MediaTombstoneEntity::class], version = 14, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaTombstoneDao(): MediaTombstoneDao
    abstract fun traktEpisodeDao(): TraktEpisodeDao
    abstract fun traktOutboxDao(): TraktOutboxDao
    abstract fun linkPreviewDao(): LinkPreviewDao
    abstract fun trashDao(): TrashDao
    abstract fun savedItemDao(): SavedItemDao
    abstract fun customFolderDao(): CustomFolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_items_timestamp` ON `saved_items` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_items_type` ON `saved_items` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_items_isArchived` ON `saved_items` (`isArchived`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_items_updatedAt` ON `saved_items` (`updatedAt`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_items` ADD COLUMN `traktId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `saved_items` ADD COLUMN `tmdbId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `saved_items` ADD COLUMN `imdbId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `saved_items` ADD COLUMN `userRating` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `saved_items` ADD COLUMN `lastWatchedAt` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `saved_items` ADD COLUMN `showProgressJson` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_items_traktId` ON `saved_items` (`traktId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_items_tmdbId` ON `saved_items` (`tmdbId`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_items ADD COLUMN bodyLocation TEXT NOT NULL DEFAULT 'INLINE'")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN contentHash TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN byteLength INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN formatVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN preview TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_items ADD COLUMN bodyMissing INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `note_search` USING FTS4(`noteId` TEXT NOT NULL, `title` TEXT NOT NULL, `bodyPrefix` TEXT NOT NULL, tokenize=unicode61)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS trash_entries (itemId TEXT NOT NULL PRIMARY KEY, deletedAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL, undoOnly INTEGER NOT NULL, purgeStarted INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_entries_expiresAt ON trash_entries(expiresAt)")
                // Give any previously soft-deleted rows a fresh recovery window, never expire them on upgrade.
                val now = System.currentTimeMillis()
                db.execSQL("INSERT OR IGNORE INTO trash_entries SELECT id, ?, ?, 0, 0 FROM saved_items WHERE isDeleted = 1", arrayOf(now, now + com.example.notes.TrashPolicy.RETENTION_MS))
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS link_preview_queue (itemId TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL, replaceFallback INTEGER NOT NULL, pending INTEGER NOT NULL, attempts INTEGER NOT NULL, generation TEXT NOT NULL)")
                // No descriptions are copied or split. Existing bookmarks do not trigger network requests.
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_items ADD COLUMN inWatchlist INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS trakt_episode_cache (itemId TEXT NOT NULL, season INTEGER NOT NULL, number INTEGER NOT NULL, title TEXT, overview TEXT, airDate TEXT, runtime INTEGER, rating REAL, PRIMARY KEY(itemId, season, number))")
                db.execSQL("CREATE TABLE IF NOT EXISTS trakt_outbox (`key` TEXT NOT NULL PRIMARY KEY, session TEXT NOT NULL, itemId TEXT NOT NULL, action TEXT NOT NULL, payload TEXT NOT NULL, generation TEXT NOT NULL, created INTEGER NOT NULL)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureMediaMetadata(db)
            }
        }

        // Early device-validation APKs already used v13 with thumbnails only.
        // Advance the version and inspect columns so both v13 shapes upgrade safely.
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureMediaMetadata(db)
        }

        private fun ensureMediaMetadata(db: SupportSQLiteDatabase) {
            fun addIfMissing(table: String, column: String) {
                val exists = db.query("PRAGMA table_info(`$table`)").use { cursor ->
                    val name = cursor.getColumnIndexOrThrow("name")
                    var found = false
                    while (cursor.moveToNext()) if (cursor.getString(name) == column) found = true
                    found
                }
                if (!exists) db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` ${if (column == "playbackProgress") "REAL" else "TEXT"}")
            }
            addIfMissing("trakt_episode_cache", "thumbnailUrl")
            addIfMissing("trakt_episode_cache", "playbackProgress")
            addIfMissing("saved_items", "mediaFactsJson")
            addIfMissing("saved_items", "watchStatusOverride")
            db.execSQL("CREATE TABLE IF NOT EXISTS media_tombstones (`key` TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, kind TEXT NOT NULL, provider TEXT NOT NULL, providerId TEXT NOT NULL, deletedAt INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_tombstones_groupId ON media_tombstones(groupId)")
            // Existing Trash records must also stay removed after this upgrade.
            val kind = "CASE WHEN lower(mediaType) IN ('tv','tv show','tv_show','show','series') THEN 'tv' ELSE 'movie' END"
            for ((provider, column) in listOf("trakt" to "traktId", "tmdb" to "tmdbId", "imdb" to "imdbId")) {
                val valid = if (provider == "imdb") "$column IS NOT NULL AND $column != ''" else "$column > 0"
                db.execSQL("INSERT OR IGNORE INTO media_tombstones SELECT ($kind) || ':$provider:' || $column, id, ($kind), '$provider', CAST($column AS TEXT), updatedAt FROM saved_items WHERE isDeleted = 1 AND upper(type) = 'MEDIA' AND lower(mediaType) IN ('movie','tv','tv show','tv_show','show','series') AND $valid")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cobalt_db"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

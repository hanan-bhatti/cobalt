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

@Database(entities = [SavedItemEntity::class, CustomFolderEntity::class, NoteSearchEntity::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cobalt_db"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

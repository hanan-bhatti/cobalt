package com.example.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TraktMigrationTest {
    @Test fun upgradeAddsEmptyQueuesAndPreservesExistingBodiesAndRatings() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE saved_items(id TEXT PRIMARY KEY, content TEXT, userRating INTEGER, type TEXT DEFAULT 'TEXT', mediaType TEXT, traktId INTEGER, tmdbId INTEGER, imdbId TEXT, updatedAt INTEGER NOT NULL DEFAULT 0, isDeleted INTEGER NOT NULL DEFAULT 0)")
                    db.execSQL("INSERT INTO saved_items(id, content, userRating) VALUES ('old', 'original body', 8)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_11_12.migrate(db)
            db.execSQL("INSERT INTO trakt_episode_cache VALUES ('old',1,1,'Pilot','Overview','2026-11-07',42,8.5)")
            db.execSQL("INSERT INTO saved_items(id, content, type, mediaType, traktId, tmdbId, isDeleted) VALUES ('trashed-media', '', 'MEDIA', 'movie', 123, 456, 1)")
            AppDatabase.MIGRATION_12_13.migrate(db)
            AppDatabase.MIGRATION_13_14.migrate(db)
            db.query("SELECT count(*) FROM media_tombstones WHERE groupId = 'trashed-media'").use {
                assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0))
            }
            db.query("SELECT title, thumbnailUrl FROM trakt_episode_cache").use {
                assertTrue(it.moveToFirst()); assertEquals("Pilot", it.getString(0)); assertTrue(it.isNull(1))
            }
            db.execSQL("DELETE FROM trakt_episode_cache")
            db.query("SELECT content, userRating, inWatchlist FROM saved_items WHERE id = 'old'").use {
                assertTrue(it.moveToFirst()); assertEquals("original body", it.getString(0)); assertEquals(8, it.getInt(1)); assertEquals(0, it.getInt(2))
            }
            for (table in listOf("trakt_outbox", "trakt_episode_cache")) db.query("SELECT count(*) FROM $table").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        }
    }
    @Test fun installedThumbnailOnlyV13UpgradesWithFullRoomValidation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "legacy-media-v13-test"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val source = requireNotNull(javaClass.getResourceAsStream("/migrations/legacy-media-v13.sql"))
                        .bufferedReader().use { it.readText() }
                    source.lineSequence().filter { it.isNotBlank() }.forEach { db.execSQL(it.removeSuffix(";")) }
                    db.execSQL("INSERT INTO room_master_table VALUES(42, '29ac126ea99179e970e5ae25a5747d93')")
                    val values = android.content.ContentValues()
                    db.query("PRAGMA table_info(saved_items)").use { c ->
                        while (c.moveToNext()) {
                            val field = c.getString(c.getColumnIndexOrThrow("name"))
                            if (c.getInt(c.getColumnIndexOrThrow("notnull")) == 1) {
                                if (c.getString(c.getColumnIndexOrThrow("type")) == "TEXT") values.put(field, "")
                                else values.put(field, 0)
                            }
                        }
                    }
                    values.put("id", "preserved"); values.put("type", "TEXT")
                    values.put("content", "original body"); values.put("userRating", 8)
                    db.insert("saved_items", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
                    db.execSQL("INSERT INTO trakt_episode_cache VALUES('preserved',1,1,'Pilot',NULL,NULL,42,8.5,'https://example.org/pilot.jpg')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { it.writableDatabase }
        val room = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_13_14).allowMainThreadQueries().build()
        try {
            val db = room.openHelper.writableDatabase // Runs Room's complete schema validation.
            assertEquals(14, db.version)
            db.query("SELECT content, userRating, mediaFactsJson FROM saved_items WHERE id='preserved'").use {
                assertTrue(it.moveToFirst()); assertEquals("original body", it.getString(0))
                assertEquals(8, it.getInt(1)); assertTrue(it.isNull(2))
            }
            db.query("SELECT title, thumbnailUrl, playbackProgress FROM trakt_episode_cache").use {
                assertTrue(it.moveToFirst()); assertEquals("Pilot", it.getString(0))
                assertEquals("https://example.org/pilot.jpg", it.getString(1)); assertTrue(it.isNull(2))
            }
        } finally { room.close(); context.deleteDatabase(name) }
    }

}

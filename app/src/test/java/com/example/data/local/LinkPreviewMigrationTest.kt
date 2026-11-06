package com.example.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
class LinkPreviewMigrationTest {
    @Test fun migrationPreservesLegacyDescriptionsWithoutQueueingOldLinks() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE saved_items(id TEXT PRIMARY KEY, content TEXT, linkDescription TEXT, type TEXT)")
                    db.execSQL("INSERT INTO saved_items VALUES ('a', 'https://example.com', 'My description', 'LINK')")
                    db.execSQL("INSERT INTO saved_items VALUES ('b', 'private body', NULL, 'TEXT')")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_10_11.migrate(db)
            db.query("PRAGMA table_info(link_preview_queue)").use { cursor ->
                val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertFalse(columns.any { it.contains("description", ignoreCase = true) })
            }
            db.query("SELECT count(*) FROM link_preview_queue").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
            db.query("SELECT linkDescription FROM saved_items WHERE id = 'a'").use {
                assertTrue(it.moveToFirst()); assertEquals("My description", it.getString(0))
            }
            db.query("SELECT content FROM saved_items WHERE id = 'b'").use {
                assertTrue(it.moveToFirst()); assertEquals("private body", it.getString(0))
            }
        }
    }
}

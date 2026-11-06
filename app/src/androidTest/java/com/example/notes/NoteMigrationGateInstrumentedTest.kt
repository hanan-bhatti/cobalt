package com.example.notes

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Separate DB/preferences namespace: never clear or replace the installed debug user's library. */
@RunWith(AndroidJUnit4::class)
class NoteMigrationGateInstrumentedTest {
    @Test fun freshAndAlreadyOpenedEmptyDatabasesSkipButRealLegacyBodiesRequireMigration() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "migration-gate-test-${UUID.randomUUID()}-"
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences(prefix + name, mode)
            override fun getDatabasePath(name: String): File = base.getDatabasePath(prefix + name)
        }
        var database: AppDatabase? = null
        try {
            assertFalse(NoteMigrationManager.needsStartupMigration(isolated))
            database = Room.databaseBuilder(isolated, AppDatabase::class.java, "cobalt_db").build()
            database.openHelper.writableDatabase
            isolated.getSharedPreferences("note_migration", 0).edit().clear().commit()
            assertFalse(NoteMigrationManager.evaluateStartupMigration(isolated) {
                database.savedItemDao().hasNotesNeedingBodyMigration()
            })
            assertFalse(NoteMigrationManager.shouldPauseBackground(isolated))
            val legacy = SavedItem(type = SavedItemType.TEXT, title = "Migration fixture",
                content = "x".repeat(2_500_000), bodyLocation = "INLINE")
            database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(legacy))
            assertTrue(NoteMigrationManager.evaluateStartupMigration(isolated) {
                database.savedItemDao().hasNotesNeedingBodyMigration()
            })
            database.openHelper.readableDatabase.query("SELECT length(content) FROM saved_items").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(2_500_000, cursor.getInt(0))
            }
        } finally {
            database?.close()
            base.deleteDatabase(prefix + "cobalt_db")
            base.deleteSharedPreferences(prefix + "note_migration")
            base.deleteSharedPreferences(prefix + "draft_prefs")
            NoteMigrationManager.refreshStatus(base)
        }
    }
}

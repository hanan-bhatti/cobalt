package com.example.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NoteMigrationGateTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("note_migration", 0).edit().clear().commit()
        context.getSharedPreferences("draft_prefs", 0).edit().clear().commit()
        database = Room.databaseBuilder(context, AppDatabase::class.java, "cobalt_db").allowMainThreadQueries().build()
        database.openHelper.writableDatabase // Reproduce Room being opened before the gate.
    }
    @After fun close() { database.close(); context.deleteDatabase("cobalt_db") }
    private fun needed() = runBlocking { NoteMigrationManager.evaluateStartupMigration(context) {
        database.savedItemDao().hasNotesNeedingBodyMigration()
    } }
    @Test fun alreadyCreatedEmptyDatabaseSkipsMigrationAndMarksCurrentFormat() {
        assertFalse(needed())
        assertFalse(NoteMigrationManager.shouldPauseBackground(context))
        assertEquals(NoteMigrationManager.BODY_FORMAT_VERSION, context.getSharedPreferences("note_migration",0).getInt("completed_format",0))
        assertFalse(needed())
    }
    @Test fun emptyLegacyDraftAndStaleRequiredFlagDoNotShowMigration() {
        context.getSharedPreferences("draft_prefs",0).edit().putString("quick_note", "").commit()
        context.getSharedPreferences("note_migration",0).edit().putBoolean("migration_required",true).commit()
        assertFalse(needed()); assertFalse(context.getSharedPreferences("draft_prefs",0).contains("quick_note"))
    }
    @Test fun realLegacyDraftIsPreservedAndRequiresConversion() {
        context.getSharedPreferences("draft_prefs",0).edit().putString("quick_note", "Unsaved original😀").commit()
        assertTrue(needed())
        assertEquals("Unsaved original😀", context.getSharedPreferences("draft_prefs",0).getString("quick_note",null))
    }
    @Test fun oversizedInlineBodyRequiresMigrationThroughScalarQueryOnly() {
        val note = SavedItem(type = SavedItemType.TEXT, title = "Fixture", content = "x".repeat(2_500_000), bodyLocation = "INLINE")
        runBlocking { database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(note)) }
        assertTrue(needed())
    }
    @Test fun currentFileBodiesDoNotRequireMigrationAgain() {
        val note = SavedItem(type = SavedItemType.TEXT, title = "Fixture", content = "", bodyLocation = "FILE", contentHash = "a".repeat(64), formatVersion = 1)
        runBlocking { database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(note)) }
        assertFalse(needed())
    }
    @Test fun interruptedOrFailedRunsRemainVisibleForRecovery() {
        for (phase in listOf("INTERRUPTED","ATTENTION","RUNNING")) {
            context.getSharedPreferences("note_migration",0).edit().putString("phase",phase).commit()
            assertTrue(needed())
        }
    }
}

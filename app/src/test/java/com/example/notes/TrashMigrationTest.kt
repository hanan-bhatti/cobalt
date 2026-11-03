package com.example.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TrashMigrationTest {
    @Test fun v9UpgradeKeepsActiveBodiesAndGivesOldSoftDeletesAFreshThirtyDays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            // v9 and v10 have identical saved_items columns. Recreate only the v9 absence of Trash.
            val sql = db.openHelper.writableDatabase
            sql.execSQL("DROP TABLE trash_entries")
            val active = SavedItem(type = SavedItemType.TEXT, title = "Active", content = "Original active😀", bodyLocation = "INLINE")
            val deleted = active.copy(id = java.util.UUID.randomUUID().toString(), content = "Original deleted body", isDeleted = true)
            db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(active))
            db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(deleted))
            val before = System.currentTimeMillis()
            AppDatabase.MIGRATION_9_10.migrate(sql)
            val after = System.currentTimeMillis()
            val entry = db.trashDao().entry(deleted.id)!!
            assertTrue(entry.deletedAt in before..after)
            assertEquals(entry.deletedAt + TrashPolicy.RETENTION_MS, entry.expiresAt)
            assertFalse(entry.purgeStarted || entry.undoOnly)
            assertNull(db.trashDao().entry(active.id))
            assertEquals("Original active😀", LegacyBodyInputStream(db.savedItemDao(), active.id).bufferedReader().use { it.readText() })
            assertEquals("Original deleted body", LegacyBodyInputStream(db.savedItemDao(), deleted.id).bufferedReader().use { it.readText() })
            assertEquals(listOf(active.id), db.savedItemDao().getAllItems().map { it.id })
        } finally { db.close() }
    }
}

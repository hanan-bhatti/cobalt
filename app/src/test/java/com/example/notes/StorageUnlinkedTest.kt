package com.example.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StorageUnlinkedTest {
    @Test fun orphanMediaCanBeRecoveredAndThenPermanentlyDeletedWithItsItem() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        val root = java.io.File(context.filesDir, "media/audio").also { it.mkdirs() }
        val file = java.io.File(root, "orphan-${UUID.randomUUID()}.mp4").also {
            it.writeText("Recovered audio bytes"); assertTrue(it.setLastModified(System.currentTimeMillis() - 120_000))
        }
        try {
            val manager = NoteStorageManager(context, db, store)
            val copy = manager.unlinkedCopies().single { it.media?.relativePath?.endsWith(file.name) == true }
            assertEquals(file.length(), copy.bytes)
            assertTrue(manager.recoverUnlinked(copy))
            val item = db.savedItemDao().getAllItems().single()
            assertEquals("AUDIO", item.type.uppercase())
            assertEquals(file.path, item.thumbnailPath)
            assertTrue(file.exists())
            assertTrue(manager.unlinkedCopies().none { it.key == copy.key })
            val trash = TrashManager(context, db, store, scheduleWork = false, fileOperations = HostTestNoteFileOperations)
            assertTrue(trash.delete(item.id))
            assertEquals(file.length(), trash.ownedSizes()[item.id])
            trash.deleteForever(item.id)
            assertFalse(file.exists())
        } finally { file.delete(); db.close() }
    }
    @Test fun recoveringAnUnlinkedNoteCreatesAVerifiedManagedBodyBeforeRemovingTheOldFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        val orphan = UUID.randomUUID().toString()
        var recoveredId: String? = null
        try {
            val text = "# Recover me\r\n\nComplete text😀"
            val ref = text.byteInputStream().use { store.writeAtomic(orphan, it) }
            store.committed(orphan, ref.hash)
            val manager = NoteStorageManager(context, db, store)
            assertTrue(manager.recoverUnlinked(manager.unlinkedCopies().single()))
            val item = db.savedItemDao().getAllItems().single()
            recoveredId = item.id
            assertEquals("Recover me", item.title)
            assertEquals(text, store.open(item.id, item.contentHash!!).bufferedReader().use { it.readText() })
            assertNull(store.size(orphan, ref.hash))
        } finally { recoveredId?.let(store::deleteAll); store.deleteAll(orphan); db.close() }
    }
    @Test fun unlinkedFilesAreVisibleExportableAndExplicitCleanupKeepsCurrentNotes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        val owner = SavedItem(type = SavedItemType.TEXT, title = "Keep me", content = "")
        val orphan = UUID.randomUUID().toString()
        val late = UUID.randomUUID().toString()
        try {
            val saved = "current body".byteInputStream().use { NotePersistence(context, db, store).saveStream(owner, it) }
            val lost = "# Retained note\n\nOriginal text😀".byteInputStream().use { store.writeAtomic(orphan, it) }
            store.committed(orphan, lost.hash)
            val manager = NoteStorageManager(context, db, store)
            val report = manager.scan()
            assertEquals(lost.bytes, report.unattached)
            val copy = manager.unlinkedCopies().single()
            assertEquals("Retained note", copy.title)
            assertEquals("# Retained note\n\nOriginal text😀", store.openMaintenance(copy.entry!!).bufferedReader().use { it.readText() })
            val reviewed = manager.reviewUnlinked()
            val newer = "Unreviewed retained bytes".byteInputStream().use { store.writeAtomic(late, it) }
            store.committed(late, newer.hash)
            val result = manager.cleanupUnlinked(reviewed.bytes, reviewed)
            assertEquals(lost.bytes, result.bytes)
            assertNull(store.size(orphan, lost.hash))
            assertTrue(store.verify(late, newer.hash, newer.bytes))
            assertTrue(store.verify(owner.id, saved.contentHash!!, saved.byteLength))
        } finally {
            listOf(owner.id, orphan, late).forEach(store::deleteAll); db.close()
        }
    }
}

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
class NoteStorageManagerTest {
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PrivateMarkdownStore(context)
        val item = SavedItem(type = SavedItemType.TEXT, title = "Storage safety", content = "")
        val old = TextUtf8InputStream("older complete body").use { store.writeAtomic(item.id, it) }
        val current = TextUtf8InputStream("current complete body😀").use { store.writeAtomic(item.id, it) }
        val manager = NoteStorageManager(context, database, store)
        suspend fun prepare() {
            store.committed(item.id, old.hash); store.committed(item.id, current.hash)
            database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item.copy(content = "", bodyLoaded = false,
                bodyLocation = "FILE", contentHash = current.hash, byteLength = current.bytes,
                sizeBytes = current.bytes, preview = current.preview, formatVersion = 1)))
            manager.setRetention(0)
        }
        override fun close() {
            context.getSharedPreferences("capture_file_sessions", Context.MODE_PRIVATE).edit().remove(item.id).commit()
            store.deleteAll(item.id); database.close()
        }
    }
    @Test fun explicitCleanupRemovesOnlyTheUnreferencedOldBody() = runBlocking {
        Fixture().use { f ->
            f.prepare()
            val report = f.manager.scan()
            assertEquals(f.current.bytes, report.current)
            assertEquals(f.old.bytes, report.removable)
            val removed = f.manager.cleanup(report.removable)
            assertEquals(1, removed.files)
            assertNull(f.store.size(f.item.id, f.old.hash))
            assertTrue(f.store.verify(f.item.id, f.current.hash, f.current.bytes))
            assertEquals(f.current.hash, f.database.savedItemDao().getNoteBodyMetadata(f.item.id)!!.contentHash)
        }
    }
    @Test fun aRecoverableEditorDraftProtectsAllOfItsGenerations() = runBlocking {
        Fixture().use { f ->
            f.prepare()
            f.context.getSharedPreferences("capture_file_sessions", Context.MODE_PRIVATE).edit()
                .putString(f.item.id, "{\"hash\":\"${f.old.hash}\"}").commit()
            assertEquals(0, f.manager.scan().removableFiles)
            assertEquals(0, f.manager.cleanup(0).files)
            assertTrue(f.store.verify(f.item.id, f.old.hash, f.old.bytes))
        }
    }
    @Test fun interruptedOperationLogsPreventHistoryCleanup() = runBlocking {
        Fixture().use { f ->
            f.prepare()
            f.store.createEditJournal(f.item.id, java.util.UUID.randomUUID().toString(), f.old)
            assertEquals(0, f.manager.scan().removableFiles)
            assertEquals(0, f.manager.cleanup(0).files)
        }
    }
    @Test fun aMissingCurrentBodyDoesNotAuthorizeRemovingRecoveryCopies() = runBlocking {
        Fixture().use { f ->
            f.prepare(); f.store.delete(f.item.id, f.current.hash)
            assertEquals(0, f.manager.scan().removableFiles)
            assertEquals(0, f.manager.cleanup(0).files)
            assertTrue(f.store.verify(f.item.id, f.old.hash, f.old.bytes))
        }
    }
    @Test fun maintenanceCannotUnlinkAnOpenGeneration() = runBlocking {
        Fixture().use { f ->
            f.prepare()
            f.store.open(f.item.id, f.old.hash).use { input ->
                val report = f.manager.scan()
                assertEquals(0, f.manager.cleanup(report.removable).files)
                assertEquals("older complete body", input.bufferedReader().readText())
                assertNotNull(f.store.size(f.item.id, f.old.hash))
            }
        }
    }
    @Test fun restoreChangesTheReferenceButKeepsThePreviousCurrentCopy() = runBlocking {
        Fixture().use { f ->
            f.prepare()
            val copy = f.manager.olderCopies().single()
            f.manager.restore(copy)
            assertEquals(f.old.hash, f.database.savedItemDao().getNoteBodyMetadata(f.item.id)!!.contentHash)
            assertTrue(f.store.verify(f.item.id, f.current.hash, f.current.bytes))
        }
    }
}

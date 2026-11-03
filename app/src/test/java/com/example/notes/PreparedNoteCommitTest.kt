package com.example.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
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
class PreparedNoteCommitTest {
    @Test fun preparedCommitDoesNotSerializeAProjectionOrRewriteTheBody() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val files = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        var writes = 0
        val store = object : NoteBodyStore by files {
            override fun writeAtomic(id: String, input: java.io.InputStream, allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef {
                writes++; return files.writeAtomic(id, input, allowOversize, progress)
            }
        }
        val persistence = NotePersistence(context, database, store)
        val note = SavedItem(type = SavedItemType.TEXT, title = "Original", content = "")
        try {
            val saved = TextUtf8InputStream("original body").use { persistence.saveStream(note, it) }
            val updatedBody = "updated😀\r\n".repeat(10_000)
            val prepared = TextUtf8InputStream(updatedBody).use { store.writeAtomic(saved.id, it) }
            val before = writes
            val projection = saved.copy(title = "Metadata from projection", content = "", bodyLoaded = false)
            val result = persistence.commitPrepared(projection, prepared)
            assertEquals(before, writes)
            assertEquals("Metadata from projection", result.title)
            assertFalse(result.bodyLoaded)
            val summary = database.savedItemDao().getItemById(saved.id)!!
            assertEquals(result.preview, summary.content)
            assertTrue(summary.content.length <= NoteSizePolicy.PREVIEW_CHARACTERS)
            database.openHelper.readableDatabase.query(
                "SELECT length(CAST(content AS BLOB)) FROM saved_items WHERE id = ?", arrayOf(saved.id)
            ).use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0L, cursor.getLong(0)) }
            assertEquals(updatedBody, store.open(result.id, result.contentHash!!).bufferedReader().use { it.readText() })
        } finally { files.deleteAll(note.id); database.close() }
    }
    @Test fun failedVerificationLeavesTheSavedReferenceAndBodyUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        val persistence = NotePersistence(context, database, store)
        val note = SavedItem(type = SavedItemType.TEXT, title = "Retained", content = "")
        try {
            val saved = TextUtf8InputStream("keep this body").use { persistence.saveStream(note, it) }
            var refused = false
            try { persistence.commitPrepared(saved, NoteBodyRef("0".repeat(64), 123, "invalid")) }
            catch (_: IllegalStateException) { refused = true }
            assertTrue(refused)
            assertEquals(saved.contentHash, database.savedItemDao().getNoteBodyMetadata(saved.id)!!.contentHash)
            assertEquals("keep this body", store.open(saved.id, saved.contentHash!!).bufferedReader().use { it.readText() })
        } finally { store.deleteAll(note.id); database.close() }
    }
    @Test fun savingAListProjectionCannotReplaceTheRealBodyWithItsPreview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val files = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        var writes = 0
        val counting = object : NoteBodyStore by files {
            override fun writeAtomic(id: String, input: java.io.InputStream, allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef {
                writes++; return files.writeAtomic(id, input, allowOversize, progress)
            }
        }
        val persistence = NotePersistence(context, database, counting)
        val note = SavedItem(type = SavedItemType.TEXT, title = "Projection safety", content = "")
        val body = "# Complete body\r\n😀 [link](https://example.org/)\n".repeat(2000)
        try {
            val saved = body.byteInputStream().use { persistence.saveStream(note, it) }
            val projection = database.savedItemDao().getAllItems().single().toDomainModel()
            assertFalse(projection.bodyLoaded)
            assertTrue(projection.content.length <= NoteSizePolicy.PREVIEW_CHARACTERS)
            val before = writes
            val updated = persistence.save(projection.copy(title = "Changed title", folders = listOf("Moved")))
            assertEquals(before, writes)
            assertEquals(saved.contentHash, updated.contentHash)
            assertEquals("Changed title", updated.title)
            assertEquals(listOf("Moved"), updated.folders)
            assertEquals(body, files.open(note.id, updated.contentHash!!).bufferedReader().use { it.readText() })
        } finally { files.deleteAll(note.id); database.close() }
    }

}

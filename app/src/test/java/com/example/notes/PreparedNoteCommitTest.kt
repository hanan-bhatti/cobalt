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
        val files = PrivateMarkdownStore(context)
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
            assertEquals("", database.savedItemDao().getItemById(saved.id)!!.content)
            assertEquals(updatedBody, store.open(result.id, result.contentHash!!).bufferedReader().use { it.readText() })
        } finally { files.deleteAll(note.id); database.close() }
    }
    @Test fun failedVerificationLeavesTheSavedReferenceAndBodyUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PrivateMarkdownStore(context)
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
}

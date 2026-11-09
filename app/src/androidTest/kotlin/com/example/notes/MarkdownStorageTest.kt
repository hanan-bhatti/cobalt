package com.example.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only on the isolated .debug application; no production database is opened. */
@RunWith(AndroidJUnit4::class)
class MarkdownStorageTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var store: PrivateMarkdownStore
    private lateinit var persistence: NotePersistence
    private val ids = mutableListOf<String>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName.endsWith(".debug"))
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = PrivateMarkdownStore(context, database = database)
        persistence = NotePersistence(context, database, store)
    }
    @After fun tearDown() {
        ids.forEach { store.deleteAll(it) }
        database.close()
    }
    private fun id() = UUID.randomUUID().toString().also { ids.add(it) }
    private fun note(text: String, id: String = id()) = SavedItem(id = id, type = SavedItemType.TEXT, title = "Storage test", content = text)

    @Test fun oneLineAndEmptyNotesAlwaysUseVerifiedFiles() = runBlocking {
        for (text in listOf("", "one line", "😀漢字\u0000\r\n")) {
            val saved = persistence.save(note(text))
            assertEquals("FILE", saved.bodyLocation)
            assertNotNull(saved.contentHash)
            assertEquals(text.toByteArray(Charsets.UTF_8).size.toLong(), saved.byteLength)
            assertTrue(withContext(Dispatchers.IO) { store.verify(saved.id, saved.contentHash!!, saved.byteLength) })
            assertEquals(text, withContext(Dispatchers.IO) { store.open(saved.id, saved.contentHash!!).bufferedReader().use { it.readText() } })
            assertTrue(withContext(Dispatchers.IO) { database.savedItemDao().readRecoveryBytes(saved.id, 1, 16_384)!!.isEmpty() })
        }
    }

    @Test fun projectionMetadataSaveNeverOverwritesTheRealBody() = runBlocking {
        val text = "# Original\n" + "keep this body\n".repeat(5_000)
        val saved = persistence.save(note(text))
        val projection = database.savedItemDao().getAllItems().single().toDomainModel()
        assertFalse(projection.bodyLoaded)
        assertTrue(projection.content.length <= NoteSizePolicy.PREVIEW_CHARACTERS)
        val moved = persistence.save(projection.copy(folders = listOf("Moved")))
        assertEquals(saved.contentHash, moved.contentHash)
        assertEquals(text, withContext(Dispatchers.IO) { store.open(saved.id, moved.contentHash!!).bufferedReader().use { it.readText() } })
        assertEquals(listOf("Moved"), database.savedItemDao().getItemById(saved.id)!!.toDomainModel().folders)
    }

    @Test fun migrationStreamsAnExistingBodyLargerThanCursorWindow() = runBlocking {
        val id = id()
        val text = "[link](https://example.org/)\n".repeat(110_000)
        database.savedItemDao().insertItem(SavedItemEntity(
            id = id, type = "TEXT", title = "Existing note", content = text,
            timestamp = 1, foldersJson = "[]", extractedText = null, thumbnailPath = null
        ))
        val migrated = withContext(Dispatchers.IO) {
            NoteWriteLock.mutex.withLock { persistence.migrateOneLocked(id) }
        }
        assertEquals("FILE", migrated.bodyLocation)
        assertEquals(text.toByteArray(Charsets.UTF_8).size.toLong(), migrated.byteLength)
        assertEquals(text, withContext(Dispatchers.IO) { store.open(id, migrated.contentHash!!).bufferedReader().use { it.readText() } })
        assertTrue(database.savedItemDao().getAllItems().single().content.length <= NoteSizePolicy.PREVIEW_CHARACTERS)
        assertTrue(withContext(Dispatchers.IO) { database.savedItemDao().readRecoveryBytes(id, 1, 16_384)!!.isEmpty() })
    }

    @Test fun aFailedFileWriteKeepsTheLegacyOriginal() = runBlocking {
        val id = id()
        val original = "untouched 😀\r\n".repeat(100)
        database.savedItemDao().insertItem(SavedItemEntity(
            id = id, type = "TEXT", title = "Keep me", content = original,
            timestamp = 1, foldersJson = "[]", extractedText = null, thumbnailPath = null
        ))
        val failingStore = object : NoteBodyStore by store {
            override fun writeAtomic(id: String, input: InputStream, allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef {
                throw IOException("Simulated storage failure")
            }
        }
        val failed = withContext(Dispatchers.IO) {
            NoteWriteLock.mutex.withLock { runCatching { NotePersistence(context, database, failingStore).migrateOneLocked(id) } }
        }
        assertTrue(failed.isFailure)
        assertEquals("INLINE", withContext(Dispatchers.IO) { database.savedItemDao().getNoteBodyMetadata(id)!!.bodyLocation })
        assertEquals(original, withContext(Dispatchers.IO) { LegacyBodyInputStream(database.savedItemDao(), id).bufferedReader().use { it.readText() } })
    }

    @Test fun faultsAtEveryAtomicStageKeepTheCommittedBody() = runBlocking {
        withContext(Dispatchers.IO) {
            for (stage in PrivateMarkdownStore.WriteStage.entries) {
                val saved = persistence.save(note("Original $stage"))
                val faulty = PrivateMarkdownStore(context, database = database, checkpoint = { current -> if (current == stage) throw IOException("Injected write failure") })
                assertTrue(runCatching {
                    NotePersistence(context, database, faulty).save(saved.copy(content = "Replacement $stage", bodyLoaded = true))
                }.isFailure)
                assertEquals(saved.contentHash, database.savedItemDao().getNoteBodyMetadata(saved.id)!!.contentHash)
                assertTrue(store.verify(saved.id, saved.contentHash!!, saved.byteLength))
            }
        }
    }
}

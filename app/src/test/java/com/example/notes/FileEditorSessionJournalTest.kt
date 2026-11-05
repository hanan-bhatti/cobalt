package com.example.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FileEditorSessionJournalTest {
    @Test fun unchangedSaveReusesTheVerifiedBodyAfterOpenOrCheckpoint() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val files = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        var writes = 0
        val counting = object : NoteBodyStore by files {
            override fun writeAtomic(id: String, input: java.io.InputStream, allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef {
                writes++
                return files.writeAtomic(id, input, allowOversize, progress)
            }
        }
        val persistence = NotePersistence(context, database, counting)
        try {
            for (edit in listOf(false, true)) {
                val item = SavedItem(type = SavedItemType.TEXT, title = "Checkpoint reuse", content = "")
                val owner = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                var document: ContinuousNoteDocument? = null
                try {
                    val saved = "before".byteInputStream().use { persistence.saveStream(item, it) }
                    val initialWrites = writes
                    val session = FileEditorSession(context, owner, persistence)
                    session.open(saved); session.enableContinuous()
                    val opened = session.state.first { !it.busy && (it.document != null || it.error != null) }
                    val doc = requireNotNull(opened.document) { opened.error.orEmpty() }
                    document = doc
                    if (edit) {
                        requireNotNull(doc.disk).execute { it.replace(6, 6, " after") }
                        session.flushJournal()
                        assertEquals(initialWrites + 1, writes)
                    }
                    val beforeSave = writes
                    val committed = session.save(saved.copy(title = "Metadata changed"))
                    assertEquals("Unchanged save streamed the full body", beforeSave, writes)
                    assertEquals("Metadata changed", committed.title)
                    assertEquals(if (edit) "before after" else "before",
                        files.open(item.id, committed.contentHash!!).bufferedReader().use { it.readText() })
                } finally {
                    owner.cancel(); owner.coroutineContext[Job]!!.join()
                    document?.closeSession(); document?.disk?.awaitClosed()
                    context.getSharedPreferences("capture_file_sessions", Context.MODE_PRIVATE).edit().remove(item.id).commit()
                    files.deleteAll(item.id)
                }
            }
        } finally { database.close(); Dispatchers.resetMain() }
    }

    @Test fun previewEditSurvivesAFullCheckpointFailureAndNewSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val files = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        var refuseFullCopy = false
        val failing = object : NoteBodyStore by files {
            override fun writeAtomic(id: String, input: java.io.InputStream, allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef {
                if (refuseFullCopy) throw IOException("Simulated storage full")
                return files.writeAtomic(id, input, allowOversize, progress)
            }
        }
        val persistence = NotePersistence(context, database, failing)
        val item = SavedItem(type = SavedItemType.TEXT, title = "Preview recovery", content = "")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var firstDocument: ContinuousNoteDocument? = null
        var secondDocument: ContinuousNoteDocument? = null
        try {
            val saved = "before".byteInputStream().use { persistence.saveStream(item, it) }
            val savedHash = requireNotNull(saved.contentHash)
            val session = FileEditorSession(context, firstScope, persistence)
            session.open(saved); session.enableContinuous()
            val opened = session.state.first { !it.busy && (it.document != null || it.error != null) }
            val firstDoc = requireNotNull(opened.document) { opened.error.orEmpty() }
            firstDocument = firstDoc
            refuseFullCopy = true
            // A preview checkbox/range edit runs while no native editor View is attached.
            session.replaceContinuousRange(0, 6, savedHash, "after😀")
            val failed = session.state.first { !it.busy && it.error != null }
            val failedDocument = requireNotNull(failed.document)
            assertEquals("after😀", requireNotNull(failedDocument.disk).openRecoveryUtf8().bufferedReader().use { it.readText() })
            assertTrue(failedDocument.frozen)
            assertEquals("before", files.open(saved.id, savedHash).bufferedReader().use { it.readText() })
            // Discard the first live session; only the durable base + journal are used below.
            firstScope.cancel(); firstScope.coroutineContext[Job]!!.join()
            firstDoc.closeSession(); firstDoc.disk?.awaitClosed()
            refuseFullCopy = false
            val reopened = FileEditorSession(context, secondScope, persistence)
            reopened.open(saved); reopened.enableContinuous()
            val restored = reopened.state.first { !it.busy && (it.document != null || it.error != null) }
            val secondDoc = requireNotNull(restored.document) { restored.error.orEmpty() }
            secondDocument = secondDoc
            assertEquals("after😀", requireNotNull(secondDoc.disk).openUtf8().bufferedReader().use { it.readText() })
            assertEquals(saved.contentHash, database.savedItemDao().getNoteBodyMetadata(item.id)!!.contentHash)
            val committed = reopened.save(saved)
            assertEquals("after😀", files.open(item.id, committed.contentHash!!).bufferedReader().use { it.readText() })
        } finally {
            firstScope.cancel(); secondScope.cancel()
            firstScope.coroutineContext[Job]!!.join(); secondScope.coroutineContext[Job]!!.join()
            firstDocument?.closeSession(); secondDocument?.closeSession()
            firstDocument?.disk?.awaitClosed(); secondDocument?.disk?.awaitClosed()
            context.getSharedPreferences("capture_file_sessions", Context.MODE_PRIVATE).edit().remove(item.id).commit()
            files.deleteAll(item.id); database.close(); Dispatchers.resetMain()
        }
    }
}

package com.example.notes

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CaptureDraftRecoveryTest {
    @Test fun orphanLiveStateRecoversANeverSavedNoteWithoutLosingUndoText() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations, sharedStorage = false)
        val id = UUID.randomUUID().toString()
        var recovered: String? = null
        try {
            val ref = store.writeAtomic(id, "original😀".byteInputStream())
            store.openLiveEditor(id, UUID.randomUUID().toString(), ref).use { model ->
                model.replace(0, 0, "unsaved ")
            }
            val recovery = CaptureDraftRecovery(context, database, store)
            val entry = recovery.list().single { it.id == id }
            assertEquals("unsaved original😀".toByteArray().size.toLong(), entry.bytes)
            recovered = recovery.recover(entry)
            val saved = requireNotNull(database.savedItemDao().getNoteBodyMetadata(recovered))
            assertEquals("unsaved original😀", store.open(recovered, requireNotNull(saved.contentHash)).bufferedReader().use { it.readText() })
            assertNull(database.savedItemDao().getNoteBodyMetadata(id))
            assertTrue(recovery.list().none { it.id == id })
        } finally {
            recovered?.let { store.deleteAll(it) }; store.deleteAll(id); database.close()
        }
    }

    @Test fun recoveryRejectsAnActiveEditorThenKeepsTheOriginalAndUsesCurrentDraftSize() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations, sharedStorage = false)
        val prefs = context.getSharedPreferences("capture_file_sessions", Context.MODE_PRIVATE)
        val item = SavedItem(title = "Existing", type = SavedItemType.TEXT, content = "")
        var recovered: String? = null
        val output = File.createTempFile("cobalt-active-draft", ".md", context.cacheDir)
        try {
            val saved = NotePersistence(context, database, store).saveStream(item, "original".byteInputStream())
            val ref = NoteBodyRef(requireNotNull(saved.contentHash), saved.byteLength, saved.preview)
            val token = UUID.randomUUID().toString()
            val model = store.openLiveEditor(item.id, token, ref)
            try {
                model.replace(0, 0, "unsaved😀 ")
                prefs.edit().putString(item.id, JSONObject().put("hash", ref.hash).put("bytes", ref.bytes)
                    .put("preview", "").put("diskToken", token).put("patch", JSONObject.NULL).toString()).commit()
                val recovery = CaptureDraftRecovery(context, database, store)
                val entry = recovery.list().single { it.id == item.id }
                assertEquals("unsaved😀 original".toByteArray().size.toLong(), entry.bytes)
                try { recovery.export(entry, Uri.fromFile(output)); fail("An active editor must retain exclusive ownership") }
                catch (_: IllegalStateException) {}
                assertEquals(0L, output.length())
            } finally { model.close() }
            val recovery = CaptureDraftRecovery(context, database, store)
            recovered = recovery.recover(recovery.list().single { it.id == item.id })
            assertEquals(ref.hash, database.savedItemDao().getNoteBodyMetadata(item.id)!!.contentHash)
            assertEquals("original", store.open(item.id, ref.hash).bufferedReader().use { it.readText() })
            assertFalse(prefs.contains(item.id))
            assertTrue(recovery.list().none { it.id == item.id })
        } finally {
            prefs.edit().remove(item.id).commit(); output.delete()
            recovered?.let { store.deleteAll(it) }; store.deleteAll(item.id); database.close()
        }
    }
}

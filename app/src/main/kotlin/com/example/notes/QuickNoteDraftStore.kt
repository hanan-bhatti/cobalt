package com.example.notes

import android.content.Context
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A draft's preferences contain only a file pointer, never the body. */
class QuickNoteDraftStore(context: Context, private val persistenceFactory: (Context) -> NotePersistence = { NotePersistence(it) }) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("quick_note_reference", Context.MODE_PRIVATE)
    private val store = PrivateMarkdownStore(this.context)
    data class Snapshot(val hash: String, val bytes: Long, val preview: String, val noteId: String, val title: String)
    data class Loaded(val snapshot: Snapshot?, val text: String, val title: String = "")

    private fun reference(): Snapshot? = prefs.getString("hash", null)?.let {
        Snapshot(it, prefs.getLong("bytes", 0), prefs.getString("preview", "") ?: "",
            prefs.getString("note_id", null) ?: throw IOException("Draft identity is missing"), prefs.getString("title", "") ?: "")
    }

    /** Called by the migration gate on IO while holding NoteWriteLock. */
    internal fun migrateLegacyLocked() {
        val legacy = context.getSharedPreferences("draft_prefs", Context.MODE_PRIVATE)
        if (!legacy.contains("quick_note")) return
        val original = legacy.getString("quick_note", "") ?: ""
        if (original.isNotEmpty()) {
            val previous = reference()
            if (previous == null) writeLocked(original, allowOversize = true)
            else {
                val legacyHash = TextUtf8InputStream(original).use { PrivateMarkdownStore.digest(it) }
                check(previous.hash == legacyHash && store.verify(DRAFT_ID, previous.hash, previous.bytes)) {
                    "Both legacy and new drafts exist; originals retained"
                }
            }
        }
        check(legacy.edit().remove("quick_note").commit()) { "Cannot acknowledge draft conversion" }
    }

    private fun writeLocked(text: String, allowOversize: Boolean = false, checkCancelled: () -> Unit = {}): Snapshot? {
        val previous = reference()
        val ref = TextUtf8InputStream(text).use { input ->
            store.writeAtomic(DRAFT_ID, input, allowOversize) { checkCancelled() }
        }
        checkCancelled()
        val snapshot = Snapshot(ref.hash, ref.bytes, ref.preview,
            previous?.takeIf { it.hash == ref.hash }?.noteId ?: UUID.randomUUID().toString(), prefs.getString("title", "") ?: "")
        check(prefs.edit().putString("hash", ref.hash).putLong("bytes", ref.bytes)
            .putString("preview", ref.preview).putString("note_id", snapshot.noteId).commit()) {
            "Cannot commit draft reference; previous draft retained"
        }
        runCatching { store.committed(DRAFT_ID, ref.hash) }
        previous?.takeIf { it.hash != ref.hash }?.let { runCatching { store.delete(DRAFT_ID, it.hash) } }
        return snapshot
    }

    suspend fun write(text: String): Snapshot? = withContext(Dispatchers.IO) {
        val cancellation = currentCoroutineContext()
        NoteWriteLock.mutex.withLock { writeLocked(text) { cancellation.ensureActive() } }
    }

    suspend fun writeTitle(title: String) = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            check(prefs.edit().putString("title", title).commit()) { "Cannot persist draft title" }
        }
    }

    suspend fun load(): Loaded = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            migrateLegacyLocked()
            val ref = reference() ?: return@withLock Loaded(null, "", prefs.getString("title", "") ?: "")
            check(store.size(DRAFT_ID, ref.hash) == ref.bytes) { "Draft body is unavailable; reference retained" }
            // Large drafts are restored explicitly; startup never reads their complete bodies.
            val text = if (ref.bytes <= NoteEditorWindows.MAX_CHARACTERS) {
                store.readRange(DRAFT_ID, ref.hash, 0, ref.bytes.toInt()).toString(Charsets.UTF_8)
                    .takeUnless { NoteEditorWindows.requiresWindow(it) } ?: ""
            } else ""
            Loaded(ref, text, ref.title)
        }
    }

    suspend fun promote(folders: List<String>): SavedItem = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val ref = reference() ?: throw IOException("No saved draft is available")
            check(store.verify(DRAFT_ID, ref.hash, ref.bytes)) { "Draft verification failed; original retained" }
            val saved = store.open(DRAFT_ID, ref.hash).use { input ->
                persistenceFactory(context).importLocked(
                    SavedItem(id = ref.noteId, title = ref.title.ifBlank { "Quick Note" }, type = SavedItemType.TEXT,
                        content = "", folders = folders), input, allowOversize = true)
            }
            // If acknowledgement fails, the stable note ID makes retry idempotent.
            check(prefs.edit().clear().commit()) { "Note saved; draft acknowledgement needs retry" }
            saved
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            prefs.edit().clear().commit()
        }
    }

    companion object { private const val DRAFT_ID = "quick-note-draft" }
}

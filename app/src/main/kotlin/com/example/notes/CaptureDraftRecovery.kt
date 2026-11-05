package com.example.notes

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Explicit recovery of live editor state, including notes that were never inserted into Room. */
// Recovery must report preference commit failures; KTX edit does not return commit's Boolean.
@android.annotation.SuppressLint("UseKtx")
class CaptureDraftRecovery(context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context.applicationContext),
    private val store: NoteBodyStore = PrivateMarkdownStore(context.applicationContext)) {
    data class Entry(val id: String, val title: String, val bytes: Long, val record: String,
        val token: String? = null, val problem: Boolean = false)
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("capture_file_sessions", Context.MODE_PRIVATE)
    suspend fun list(): List<Entry> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, Entry>()
        for ((id, value) in prefs.all) {
            if (value !is String || database.savedItemDao().isItemDeleted(id) == true) continue
            val record = runCatching { JSONObject(value) }.getOrNull()
            val item = database.savedItemDao().getItemById(id)
            val title = item?.title?.ifBlank { null } ?: record?.optString("preview")?.take(100)?.ifBlank { null } ?: "Unsaved note"
            result[id] = Entry(id, title, record?.optLong("bytes", 0)?.coerceAtLeast(0) ?: 0, value,
                record?.optString("diskToken")?.takeUnless { it.isBlank() || it == "null" }, problem = record == null)
        }
        for (editor in store.liveEditors()) {
            val existing = result[editor.id]
            if (existing != null) {
                if (existing.token == editor.token) result[editor.id] = existing.copy(bytes = editor.bytes)
                continue
            }
            if (database.savedItemDao().isItemDeleted(editor.id) == true) continue
            val item = database.savedItemDao().getItemById(editor.id)
            val record = JSONObject().put("hash", editor.base.hash).put("bytes", editor.base.bytes)
                .put("preview", "").put("diskToken", editor.token).put("start", 0).put("end", 0).put("patch", JSONObject.NULL).toString()
            result[editor.id] = Entry(editor.id, item?.title?.ifBlank { null } ?: "Recovered editor state", editor.bytes, record, editor.token)
        }
        result.values.toList()
    }
    private fun unchanged(entry: Entry) {
        val value = prefs.getString(entry.id, null)
        check(value == null && entry.token != null || value == entry.record) { "This draft changed. Close Capture and refresh recovery." }
    }
    private fun input(entry: Entry, checkCancelled: () -> Unit): Pair<java.io.InputStream, (() -> Unit)> {
        unchanged(entry)
        val record = JSONObject(entry.record)
        val ref = NoteBodyRef(record.getString("hash"), record.getLong("bytes"), record.optString("preview"))
        val token = entry.token
        if (token != null) {
            val model = store.openLiveEditor(entry.id, token, ref, reopen = true, checkCancelled = checkCancelled)
            try { return model.openRecoveryUtf8() to { model.close() } }
            catch (error: Throwable) { model.close(); throw error }
        }
        val legacy = record.optString("editToken").takeUnless { it.isBlank() || it == "null" }
        val recovered = if (legacy != null) store.recoverEditJournal(entry.id, legacy, ref, checkCancelled) else ref
        val patch = record.optString("patch").takeUnless { it.isBlank() || it == "null" }
        if (patch != null) {
            val length = record.getInt("patchBytes"); require(length in 0..16_384)
            val data = store.readRange("capture-window:${entry.id}", patch, 0, length)
            val table = PieceTable(FileNoteByteSource(store, entry.id, recovered.hash, recovered.bytes), keepUndo = false)
            table.replace(record.getLong("start"), record.getLong("end"), object : NoteByteSource {
                override val size = data.size.toLong()
                override fun read(offset: Long, count: Int) = data.copyOfRange(offset.toInt(), offset.toInt() + count)
            })
            return table.openStream() to {}
        }
        return store.open(entry.id, recovered.hash) to {}
    }
    suspend fun export(entry: Entry, destination: Uri) = withContext(Dispatchers.IO) {
        val cancellation = currentCoroutineContext()
        val (source, release) = input(entry) { cancellation.ensureActive() }
        try { source.use { input ->
            context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                NoteExport.writeDraft(SavedItem(title = entry.title, type = SavedItemType.TEXT, content = ""), input, output) { cancellation.ensureActive() }
            } ?: error("Export destination unavailable")
        } } finally { release() }
    }
    suspend fun recover(entry: Entry): String = withContext(Dispatchers.IO) {
        val cancellation = currentCoroutineContext()
        NoteWriteLock.mutex.withLock {
            unchanged(entry)
            val original = database.savedItemDao().getItemById(entry.id)?.toDomainModel()
            check(original?.isDeleted != true) { "Restore the item from Trash before recovering its draft" }
            val (source, release) = input(entry) { cancellation.ensureActive() }
            val recovered = try {
                val saved = source.use { input ->
                NotePersistence(context, database, store).importLocked(SavedItem(
                    title = entry.title + " (recovered)", type = if (original?.type == SavedItemType.CODE) SavedItemType.CODE else SavedItemType.TEXT,
                    content = "", folders = original?.folders.orEmpty()), input, allowOversize = true)
                }
                // Retain the exclusive live-editor lease until the reference is cleared.
                // Another Capture session must not reopen between verification and removal.
                unchanged(entry)
                check(prefs.edit().remove(entry.id).commit()) { "Recovered note is saved, but the draft reference could not be cleared" }
                saved
            } finally { release() }
            entry.token?.let { runCatching { store.retireLiveEditor(entry.id, it) } }
            recovered.id
        }
    }
}

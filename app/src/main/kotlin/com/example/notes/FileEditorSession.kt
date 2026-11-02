package com.example.notes

import android.content.Context
import com.example.data.model.SavedItem
import java.io.InputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** File-backed editing state shared by the original capture screen and its toolbar. */
class FileEditorSession(context: Context, private val scope: CoroutineScope,
    private val persistence: NotePersistence = NotePersistence(context.applicationContext)) {
    data class State(val id: String? = null, val originalHash: String? = null, val generation: NoteBodyRef? = null,
        val window: EditableNoteWindow = EditableNoteWindow(0, 0, ""), val text: String = "", val dirty: Boolean = false,
        val busy: Boolean = false, val error: String? = null, val previous: List<Long> = emptyList())
    private val context = context.applicationContext
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val store = persistence.store
    private val prefs = this.context.getSharedPreferences("capture_file_sessions", Context.MODE_PRIVATE)
    private var journal: Job? = null
    private var operation: Job? = null
    private var failedPaste: String? = null
    private var closing: Job? = null

    private fun snapshot(s: State, patch: NoteBodyRef? = null) {
        val id = s.id ?: return
        val ref = s.generation ?: return
        val json = JSONObject().put("original", s.originalHash ?: JSONObject.NULL).put("hash", ref.hash)
            .put("bytes", ref.bytes).put("preview", ref.preview).put("start", s.window.start).put("end", s.window.end)
            .put("patch", patch?.hash ?: JSONObject.NULL).put("patchBytes", patch?.bytes ?: 0)
        check(prefs.edit().putString(id, json.toString()).commit()) { "Could not journal edits; retry before closing" }
    }
    private suspend fun readWindow(id: String, ref: NoteBodyRef, start: Long) = persistence.window(id, ref.hash, start)

    suspend fun open(item: SavedItem) {
        closing?.join(); operation?.cancelAndJoin(); journal?.cancelAndJoin()
        failedPaste = null
        mutable.value = State(id = item.id, originalHash = item.contentHash, busy = true)
        try {
            val loaded = withContext(Dispatchers.IO) {
                val hash = item.contentHash ?: error("Body needs migration")
                var ref = NoteBodyRef(hash, item.byteLength, item.preview)
                var start = 0L
                var patchText: String? = null
                var patchEnd: Long? = null
                val saved = prefs.getString(item.id, null)?.let { JSONObject(it) }
                if (saved != null) {
                    val original = saved.optString("original").takeUnless { it == "null" || it.isBlank() }
                    check(original == hash || (saved.getString("hash") == hash && saved.isNull("patch"))) {
                        "A draft belongs to another saved version. Recover it from Data Migration before editing."
                    }
                    ref = NoteBodyRef(saved.getString("hash"), saved.getLong("bytes"), saved.getString("preview"))
                    check(store.verify(item.id, ref.hash, ref.bytes)) { "Draft body unavailable; reference retained" }
                    start = saved.getLong("start")
                    if (!saved.isNull("patch")) {
                        check(saved.getInt("patchBytes") <= 16_384) { "Invalid draft window" }
                        patchText = store.readRange("capture-window:${item.id}", saved.getString("patch"),
                            0, saved.getInt("patchBytes")).toString(Charsets.UTF_8)
                        patchEnd = saved.getLong("end")
                    }
                }
                val window = readWindow(item.id, ref, start)
                State(item.id, hash, ref, if (patchEnd != null) window.copy(end = patchEnd) else window, patchText ?: window.text, patchText != null)
            }
            if (mutable.value.id == item.id) mutable.value = loaded
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = "Could not open the editable body. Original and recovery files are retained.") }
    }

    /** A large paste is staged privately; it does not save over the original note. */
    suspend fun stage(item: SavedItem, input: InputStream) = withContext(Dispatchers.IO) {
        closing?.join(); journal?.cancelAndJoin()
        failedPaste = null
        val cancellation = currentCoroutineContext()
        val ref = NoteWriteLock.mutex.withLock { store.writeAtomic(item.id, input) { cancellation.ensureActive() } }
        val window = readWindow(item.id, ref, 0)
        val next = State(item.id, item.contentHash, ref, window, window.text)
        snapshot(next)
        // Closing capture is serialized on Main. Never reopen its state after cancellation during fsync.
        withContext(Dispatchers.Main.immediate) {
            cancellation.ensureActive()
            mutable.value = next
        }
    }

    fun edit(text: String) {
        val current = mutable.value
        if (current.busy || current.generation == null || text == current.text) return
        journal?.cancel()
        if (!NoteEditorWindows.sectionNeedsStreaming(text)) {
            mutable.value = current.copy(text = text, dirty = true, error = null)
            journal = scope.launch { delay(300); flushJournal() }
        } else {
            failedPaste = text
            mutable.value = current.copy(busy = true, error = null)
            operation = scope.launch {
                try { journal?.join(); mutable.value = replace(current, text); failedPaste = null }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { mutable.value = current.copy(error = "Paste could not be staged. Retry Save before closing; the saved note is unchanged.") }
            }
        }
    }

    suspend fun flushJournal() {
        val current = mutable.value
        if (current.id == null || current.generation == null || current.busy) return
        try { withContext(Dispatchers.IO) {
            val cancellation = currentCoroutineContext()
            val patch = if (current.dirty) TextUtf8InputStream(current.text).use { input ->
                store.writeAtomic("capture-window:${current.id}", input) { cancellation.ensureActive() }
            } else null
            cancellation.ensureActive(); if (mutable.value === current) snapshot(current, patch)
        } } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { mutable.value = mutable.value.copy(error = "Could not journal edits. Keep the editor open and retry Save.") }
    }

    private suspend fun replace(current: State, text: String): State = withContext(Dispatchers.IO) {
        val id = current.id ?: error("No editor")
        val ref = current.generation ?: error("No body")
        val cancellation = currentCoroutineContext()
        val updated = NoteWriteLock.mutex.withLock {
            val patch = TextUtf8InputStream(text).use { store.writeAtomic("capture-window:$id", it) { cancellation.ensureActive() } }
            val table = PieceTable(FileNoteByteSource(store, id, ref.hash, ref.bytes))
            table.replace(current.window.start, current.window.end, FileNoteByteSource(store, "capture-window:$id", patch.hash, patch.bytes))
            check(NoteSizePolicy.acceptsEdit(table.size, ref.bytes)) { "Note exceeds 55 MB" }
            table.openStream().use { store.writeAtomic(id, it, ref.bytes > NoteSizePolicy.IMPORT_MAX_BYTES) { cancellation.ensureActive() } }
        }
        val window = readWindow(id, updated, current.window.start)
        current.copy(generation = updated, window = window, text = window.text, dirty = false, busy = false, error = null).also { snapshot(it) }
    }

    /** Switch the internally focused range when the user taps another part of the scrolling document. */
    fun focus(start: Long, end: Long, expectedHash: String) {
        val current = mutable.value
        val ref = current.generation ?: return
        if (current.busy || ref.hash != expectedHash || start < 0 || end < start || end > ref.bytes) return
        if (current.window.start == start && current.window.end == end) return
        journal?.cancel()
        mutable.value = current.copy(busy = true)
        operation = scope.launch {
            try {
                journal?.join()
                val staged = if (current.dirty) replace(current, current.text) else current
                val updated = staged.generation!!
                val delta = updated.bytes - ref.bytes
                val shifted = if (start >= current.window.end) delta else 0L
                val from = start + shifted
                val until = end + shifted
                val text = withContext(Dispatchers.IO) {
                    store.readRange(current.id!!, updated.hash, from, (until - from).toInt()).toString(Charsets.UTF_8)
                }
                val next = staged.copy(window = EditableNoteWindow(from, until, text), text = text,
                    dirty = false, busy = false)
                withContext(Dispatchers.IO) { snapshot(next) }
                mutable.value = next
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.value = current.copy(error = "Could not focus this part. Draft retained; retry Save.") }
        }
    }

    suspend fun save(item: SavedItem): SavedItem {
        operation?.join(); journal?.cancelAndJoin()
        var current = mutable.value
        check(current.id == item.id && current.generation != null) { "Editable body unavailable" }
        failedPaste?.let { current = replace(current, it); failedPaste = null }
        if (current.dirty) current = replace(current, current.text)
        mutable.value = current
        val ref = current.generation!!
        val saved = withContext(Dispatchers.IO) { store.open(item.id, ref.hash).use {
            persistence.saveStream(item.copy(content = "", contentHash = current.originalHash, bodyLoaded = false), it)
        } }
        withContext(Dispatchers.IO) { prefs.edit().remove(item.id).commit() }
        mutable.value = State()
        return saved
    }

    fun close() {
        operation?.cancel()
        val current = mutable.value
        journal?.cancel()
        if (current.id != null && current.generation != null) closing = scope.launch {
            operation?.join(); journal?.join(); flushJournalSnapshot(current)
        }
        mutable.value = State()
    }
    private suspend fun flushJournalSnapshot(current: State) = withContext(Dispatchers.IO) {
        runCatching {
            val patch = if (current.dirty) TextUtf8InputStream(current.text).use { store.writeAtomic("capture-window:${current.id}", it) } else null
            snapshot(current, patch)
        }
    }
}

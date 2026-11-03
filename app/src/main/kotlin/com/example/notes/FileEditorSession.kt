package com.example.notes

import android.content.Context
import com.example.data.model.SavedItem
import java.io.InputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import io.github.rosemoe.sora.text.ContentIO
import org.json.JSONObject

/** File-backed editing state shared by the original capture screen and its toolbar. */
class FileEditorSession(context: Context, private val scope: CoroutineScope,
    private val persistence: NotePersistence = NotePersistence(context.applicationContext)) {
    data class State(val id: String? = null, val originalHash: String? = null, val generation: NoteBodyRef? = null,
        val window: EditableNoteWindow = EditableNoteWindow(0, 0, ""), val text: String = "", val dirty: Boolean = false,
        val busy: Boolean = false, val error: String? = null, val previous: List<Long> = emptyList(),
        val document: ContinuousNoteDocument? = null, val caretStart: Int = 0, val caretEnd: Int = 0)
    private val context = context.applicationContext
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val store = persistence.store
    private val prefs = this.context.getSharedPreferences("capture_file_sessions", Context.MODE_PRIVATE)
    private var journal: Job? = null
    private var operation: Job? = null
    private var failedPaste: String? = null
    private var closing: Job? = null
    private val checkpointMutex = Mutex()

    /** Called once per session. Editing no longer changes focus between separate text fields. */
    fun enableContinuous() {
        val current = mutable.value
        if (current.id == null || current.generation == null || current.busy || current.document != null) return
        journal?.cancel()
        mutable.value = current.copy(busy = true)
        operation = scope.launch {
            var opening = current
            try {
                journal?.join()
                // Promote a recovered legacy window to a verified private draft before admission.
                // Plain-text/export fallback must include that edit, not only the saved original.
                if (current.dirty) {
                    opening = replace(current, current.text)
                    if (mutable.value.id == current.id && mutable.value.generation?.hash == current.generation.hash) {
                        mutable.value = opening.copy(busy = true)
                    }
                }
                val source = opening
                val loaded = withContext(Dispatchers.IO) {
                    val ref = source.generation ?: error("Body unavailable")
                    val table = PieceTable(FileNoteByteSource(store, source.id!!, ref.hash, ref.bytes))
                    val cancellation = currentCoroutineContext()
                    val runtime = Runtime.getRuntime()
                    val budget = ContinuousEditorCapacity.budget(runtime.maxMemory(), runtime.totalMemory() - runtime.freeMemory())
                    table.openStream().use { input -> ContinuousEditorCapacity.check(input, budget) { cancellation.ensureActive() } }
                    table.openStream().use { input ->
                        val checked = object : java.io.FilterInputStream(input) {
                            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                                cancellation.ensureActive(); return super.read(bytes, off, len)
                            }
                            override fun read(): Int { cancellation.ensureActive(); return super.read() }
                        }
                        ContinuousNoteDocument(ContentIO.createFrom(checked, Charsets.UTF_8), table.size).also {
                            it.selectionStart = source.caretStart.coerceIn(0, it.content.length)
                            it.selectionEnd = source.caretEnd.coerceIn(it.selectionStart, it.content.length)
                        }
                    }
                }
                if (mutable.value.id == opening.id && mutable.value.generation?.hash == opening.generation?.hash) {
                    mutable.value = opening.copy(document = loaded, busy = false)
                } else withContext(Dispatchers.IO) { loaded.content.release() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: EditorCapacityException) { continuousFailure(opening, error.message.orEmpty()) }
            catch (_: OutOfMemoryError) { continuousFailure(opening, "There is not enough memory to edit this note. Read it as plain text or export it. Original and draft files are retained.") }
            catch (_: Exception) { continuousFailure(opening, "Could not open the continuous editor. The saved note and draft are retained.") }
        }
    }

    private fun continuousFailure(owner: State, message: String) {
        if (mutable.value.id == owner.id && mutable.value.generation?.hash == owner.generation?.hash) {
            mutable.value = owner.copy(busy = false, error = message)
        }
    }

    /** Native editor events are already incremental; never send the entire document through Compose. */
    fun continuousChanged(document: ContinuousNoteDocument) {
        val current = mutable.value
        if (current.document !== document) return
        document.changed()
        mutable.value = current.copy(dirty = true, error = null)
        journal?.cancel()
        journal = if (current.busy) null else scope.launch {
            // A large body checkpoints after an idle pause, not on every key or cursor movement.
            delay(if ((current.generation?.bytes ?: 0) >= NoteSizePolicy.TIER_B_MAX_BYTES) 3000 else 1000)
            flushJournal()
        }
    }

    fun continuousBusy(document: ContinuousNoteDocument, busy: Boolean) {
        val current = mutable.value
        if (current.document !== document) return
        mutable.value = current.copy(busy = busy)
        if (busy) journal?.cancel()
        if (!busy && current.dirty) {
            journal?.cancel()
            journal = scope.launch { delay(1000); flushJournal() }
        }
    }

    /** A checkbox edit from the formatted preview uses the same document and undo history. */
    fun replaceContinuousRange(start: Long, end: Long, expectedHash: String, replacement: String) {
        val current = mutable.value
        val document = current.document ?: return
        if (current.busy || current.generation?.hash != expectedHash) return
        val revision = document.revision
        val wasFrozen = document.frozen
        document.freeze(true)
        mutable.value = current.copy(busy = true)
        journal?.cancel()
        operation = scope.launch {
            try {
                journal?.join()
                val range = withContext(Dispatchers.Default) {
                    val cancellation = currentCoroutineContext()
                    val content = document.snapshot()
                    try { ContinuousTextSearch.utf16Range(content, start, end) { cancellation.ensureActive() } }
                    finally { content.release() }
                }
                check(document.revision == revision && mutable.value.document === document) { "The preview changed. Retry this edit." }
                document.content.replace(range.first, range.second, replacement)
                continuousChanged(document)
                val (ref, savedRevision) = checkpointContinuous(mutable.value)
                if (document.revision == savedRevision) document.byteLength = ref.bytes
                mutable.value = mutable.value.copy(generation = ref, busy = false,
                    dirty = document.revision != savedRevision, error = null)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false,
                error = "Preview edit could not be saved. Original and draft retained; retry Save.") }
            finally { document.freeze(wasFrozen) }
        }
    }

    private suspend fun checkpointContinuous(current: State): Pair<NoteBodyRef, Long> = checkpointMutex.withLock {
        withContext(Dispatchers.IO) {
            val document = current.document ?: error("No continuous document")
            val revision = document.revision
            val frozenContent = document.snapshot()
            val cancellation = currentCoroutineContext()
            val ref = try {
                ContinuousNoteDocument.stream(frozenContent).use { input ->
                    store.writeAtomic(current.id!!, input,
                        document.permittedBytes > NoteSizePolicy.IMPORT_MAX_BYTES) { cancellation.ensureActive() }
                }
            } finally { frozenContent.release() }
            check(ref.bytes <= document.permittedBytes) { "Note exceeds 55 MB. Draft and original retained." }
            // The same recovery preference format is understood by the existing startup path.
            val previousJournal = prefs.getString(current.id, null)?.let { JSONObject(it) }
            val previousDraft = previousJournal?.optString("hash")
            snapshot(current.copy(generation = ref, window = EditableNoteWindow(0, 0, ""), dirty = false),
                previousCheckpoint = previousDraft)
            // The preference now owns a verified recovery generation. Do not infer that an older
            // content hash is disposable: it may also be a saved undo/history generation.
            runCatching { store.committed(current.id!!, ref.hash) }
            ref to revision
        }
    }

    private fun snapshot(s: State, patch: NoteBodyRef? = null, previousCheckpoint: String? = null) {
        val id = s.id ?: return
        val ref = s.generation ?: return
        val json = JSONObject().put("original", s.originalHash ?: JSONObject.NULL).put("hash", ref.hash)
            .put("bytes", ref.bytes).put("preview", ref.preview).put("start", s.window.start).put("end", s.window.end)
            .put("patch", patch?.hash ?: JSONObject.NULL).put("patchBytes", patch?.bytes ?: 0)
            .put("previousCheckpoint", previousCheckpoint ?: JSONObject.NULL)
            .put("caretStart", s.document?.selectionStart ?: s.caretStart)
            .put("caretEnd", s.document?.selectionEnd ?: s.caretEnd)
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
                State(item.id, hash, ref, if (patchEnd != null) window.copy(end = patchEnd) else window, patchText ?: window.text, patchText != null,
                    caretStart = saved?.optInt("caretStart", 0) ?: 0, caretEnd = saved?.optInt("caretEnd", 0) ?: 0)
            }
            if (mutable.value.id == item.id) mutable.value = loaded
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = "Could not open the editable body. Original and recovery files are retained.") }
    }

    /** A large paste is staged privately; it does not save over the original note. */
    suspend fun stage(item: SavedItem, input: InputStream, caretStart: Int = 0, caretEnd: Int = caretStart) = withContext(Dispatchers.IO) {
        closing?.join(); journal?.cancelAndJoin()
        failedPaste = null
        val cancellation = currentCoroutineContext()
        val ref = NoteWriteLock.mutex.withLock { store.writeAtomic(item.id, input) { cancellation.ensureActive() } }
        val window = readWindow(item.id, ref, 0)
        val next = State(item.id, item.contentHash, ref, window, window.text,
            caretStart = caretStart.coerceAtLeast(0), caretEnd = maxOf(caretStart, caretEnd, 0))
        snapshot(next)
        // Closing capture is serialized on Main. Never reopen its state after cancellation during fsync.
        withContext(Dispatchers.Main.immediate) {
            cancellation.ensureActive()
            mutable.value = next
        }
    }

    fun edit(text: String) {
        val current = mutable.value
        if (current.document != null) return // Continuous documents accept native range operations only.
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
        if (current.document != null) {
            if (!current.dirty) return
            try {
                val (ref, revision) = checkpointContinuous(current)
                if (mutable.value.document === current.document) {
                    current.document.savedRevision = revision
                    if (current.document.revision == revision) current.document.byteLength = ref.bytes
                    mutable.value = mutable.value.copy(generation = ref,
                        dirty = current.document.revision != revision, error = null)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Could not checkpoint edits. Keep this note open and retry Save; the original is retained.") }
            return
        }
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
        if (current.document != null) {
            val document = current.document
            document.freeze(true)
            mutable.value = current.copy(busy = true)
            try {
                document.pendingEdit?.join()
                val (ref, _) = checkpointContinuous(current)
                val saved = persistence.commitPrepared(item.copy(content = "", contentHash = current.originalHash,
                    bodyLoaded = false), ref)
                withContext(Dispatchers.IO) { check(prefs.edit().remove(item.id).commit()) }
                mutable.value = State()
                return saved
            } catch (error: Exception) {
                document.freeze(false)
                mutable.value = current.copy(error = "Save failed. Original and draft retained; retry before closing.")
                throw error
            }
        }
        failedPaste?.let { current = replace(current, it); failedPaste = null }
        if (current.dirty) current = replace(current, current.text)
        mutable.value = current
        val ref = current.generation!!
        val saved = persistence.commitPrepared(item.copy(content = "", contentHash = current.originalHash,
            bodyLoaded = false), ref)
        withContext(Dispatchers.IO) { prefs.edit().remove(item.id).commit() }
        mutable.value = State()
        return saved
    }

    fun close() {
        operation?.cancel()
        val current = mutable.value
        current.document?.pendingEdit?.cancel()
        journal?.cancel()
        if (current.id != null && current.generation != null) closing = scope.launch {
            operation?.join(); journal?.join(); current.document?.pendingEdit?.join(); flushJournalSnapshot(current)
        }
        mutable.value = State()
    }
    private suspend fun flushJournalSnapshot(current: State) = withContext(Dispatchers.IO) {
        runCatching {
            if (current.document != null) { checkpointContinuous(current); return@runCatching }
            val patch = if (current.dirty) TextUtf8InputStream(current.text).use { store.writeAtomic("capture-window:${current.id}", it) } else null
            snapshot(current, patch)
        }
    }
}

package com.example.notes

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemDao
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import androidx.room.withTransaction
import java.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class NoteBodyMetadata(
    val id: String, val bodyLocation: String, val contentHash: String?, val byteLength: Long,
    val preview: String, val formatVersion: Int, val updatedAt: Long
)

data class EditableNoteWindow(val start: Long, val end: Long, val text: String)

internal object NoteWriteLock { val mutex = Mutex() }

class LegacyBodyInputStream(private val dao: SavedItemDao, private val id: String) : InputStream() {
    private var offset = 0L
    private var buffer = ByteArray(0)
    private var position = 0
    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xff
    }
    override fun read(bytes: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
        if (len == 0) return 0
        if (position >= buffer.size) {
            buffer = dao.readRecoveryBytes(id, offset + 1, NoteSizePolicy.IO_CHUNK_BYTES)
                ?: throw IOException("Original note is missing")
            position = 0
            if (buffer.isEmpty()) return -1
        }
        val count = minOf(len, buffer.size - position)
        buffer.copyInto(bytes, off, position, position + count)
        offset += count
        position += count
        return count
    }
}

class NotePersistence(
    context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context.applicationContext),
    val store: NoteBodyStore = PrivateMarkdownStore(context.applicationContext)
) {
    private val dao = database.savedItemDao()

    /** Caller holds NoteWriteLock; imports never materialize the body in a String. */
    internal suspend fun importLocked(item: SavedItem, input: InputStream, allowOversize: Boolean = false,
        progress: (Long) -> Unit = {}, replaceExisting: Boolean = false): SavedItem {
        require(isNote(item))
        val cancellation = currentCoroutineContext()
        val previous = dao.getNoteBodyMetadata(item.id)
        if (replaceExisting) {
            check(previous?.contentHash == item.contentHash) { "Note changed or was deleted; original retained" }
        }
        val ref = store.writeAtomic(item.id, input, allowOversize) {
            cancellation.ensureActive(); progress(it)
        }
        if (previous != null && !replaceExisting) {
            check(previous.bodyLocation == "FILE" && previous.contentHash == ref.hash) {
                "Import identifier conflicts with an existing note; original retained"
            }
            return dao.getItemById(item.id)!!.toDomainModel()
        }
        cancellation.ensureActive()
        val saved = item.copy(content = "", bodyLoaded = false, bodyLocation = "FILE", contentHash = ref.hash,
            byteLength = ref.bytes, sizeBytes = ref.bytes, preview = ref.preview, formatVersion = 1, bodyMissing = false)
        database.withTransaction {
            dao.insertItem(SavedItemEntity.fromDomainModel(saved))
            index(item.id, ref.hash)
        }
        runCatching { store.committed(item.id, ref.hash) }
        // Retain generations for undo and interrupted-edit recovery; reconciliation never guesses which to delete.
        return saved
    }

    suspend fun importMarkdown(item: SavedItem, input: InputStream, progress: (Long) -> Unit = {}): SavedItem =
        withContext(Dispatchers.IO) { NoteWriteLock.mutex.withLock { importLocked(item, input, progress = progress) } }

    suspend fun saveStream(item: SavedItem, input: InputStream, progress: (Long) -> Unit = {}): SavedItem =
        withContext(Dispatchers.IO) { NoteWriteLock.mutex.withLock {
            importLocked(item, input, progress = progress, replaceExisting = true)
        } }

    companion object {
        fun isNote(item: SavedItem) = item.type == SavedItemType.TEXT || item.type == SavedItemType.CODE
    }

    suspend fun restoreGeneration(id: String, expectedHash: String, target: NoteGeneration): SavedItem = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val metadata = dao.getItemById(id) ?: throw IOException("Note was deleted; recovery file retained")
            check(metadata.contentHash == expectedHash) { "Note changed; reopen before undo" }
            check(store.verify(id, target.hash, target.bytes)) { "Undo generation unavailable; saved note retained" }
            database.withTransaction {
                check(dao.commitFileBody(id, target.hash, target.bytes, target.preview, metadata.updatedAt,
                    maxOf(System.currentTimeMillis(), metadata.updatedAt + 1)) == 1)
                index(id, target.hash)
            }
            dao.getItemById(id)!!.toDomainModel()
        }
    }

    private suspend fun index(id: String, hash: String) {
        val entity = dao.getItemById(id) ?: throw IOException("Note metadata is missing")
        val prefix = ByteArrayOutputStream(NoteSizePolicy.SEARCH_PREFIX_BYTES)
        var offset = 0L
        while (offset < NoteSizePolicy.SEARCH_PREFIX_BYTES) {
            val bytes = store.readRange(id, hash, offset,
                minOf(NoteSizePolicy.IO_CHUNK_BYTES.toLong(), NoteSizePolicy.SEARCH_PREFIX_BYTES - offset).toInt())
            if (bytes.isEmpty()) break
            prefix.write(bytes)
            offset += bytes.size
        }
        dao.deleteNoteSearch(id)
        dao.insertNoteSearch(com.example.data.local.NoteSearchEntity(id, entity.title,
            prefix.toString("UTF-8").replace("\u0000", " ")))
    }

    /** Caller holds NoteWriteLock. The SQLite original stays authoritative until verification succeeds. */
    suspend fun migrateOneLocked(id: String, progress: (Long) -> Unit = {}): NoteBodyMetadata {
        val cancellation = currentCoroutineContext()
        val result = database.withTransaction {
            val original = dao.getNoteBodyMetadata(id) ?: throw IOException("Original note is missing")
            if (original.bodyLocation == "FILE") {
                check(original.formatVersion == 1) { "Unsupported note body format; original retained" }
                if (original.contentHash == null || !store.verify(id, original.contentHash, original.byteLength)) {
                    dao.markBodyMissing(id, true)
                    throw IOException("A Markdown body is missing or corrupt; the note has been retained")
                }
                dao.markBodyMissing(id, false)
                index(id, original.contentHash)
                return@withTransaction original
            }
            val ref = LegacyBodyInputStream(dao, id).use { source ->
                store.writeAtomic(id, source, allowOversize = true) {
                    cancellation.ensureActive()
                    progress(it)
                }
            }
            check(ref.bytes == original.byteLength) { "Body length verification failed; original retained" }
            // Independent second read of the SQLite source, not just of the newly written file.
            val originalHash = LegacyBodyInputStream(dao, id).use { PrivateMarkdownStore.digest(it) }
            check(originalHash == ref.hash) { "Body hash verification failed; original retained" }
            cancellation.ensureActive()
            check(dao.commitFileBody(id, ref.hash, ref.bytes, ref.preview, original.updatedAt) == 1)
            index(id, ref.hash)
            original.copy(bodyLocation = "FILE", contentHash = ref.hash, byteLength = ref.bytes,
                preview = ref.preview, formatVersion = 1)
        }
        result.contentHash?.let { hash -> runCatching { store.committed(id, hash) } }
        return result
    }

    suspend fun save(item: SavedItem, progress: (Long) -> Unit = {}): SavedItem = withContext(Dispatchers.IO) {
        if (!isNote(item)) {
            dao.insertItem(SavedItemEntity.fromDomainModel(item))
            return@withContext item
        }
        NoteWriteLock.mutex.withLock {
            var old = dao.getNoteBodyMetadata(item.id)
            if (old != null && old.bodyLocation != "FILE") old = migrateOneLocked(item.id)
            if (!item.bodyLoaded) {
                val current = old ?: if (item.contentHash != null && store.verify(item.id, item.contentHash, item.byteLength)) {
                    // Restoring a deleted metadata row must relink its intact generation, not its preview.
                    NoteBodyMetadata(item.id, "FILE", item.contentHash, item.byteLength, item.preview, 1, item.updatedAt)
                } else throw IOException("Cannot save a preview as a note body")
                val entity = SavedItemEntity.fromDomainModel(item).copy(
                    content = "", bodyLocation = "FILE", contentHash = current.contentHash,
                    byteLength = current.byteLength, sizeBytes = current.byteLength,
                    preview = current.preview, formatVersion = 1
                )
                database.withTransaction {
                    dao.insertItem(entity)
                    if (old == null) index(item.id, current.contentHash!!)
                    else dao.updateNoteSearchTitle(item.id, entity.title)
                }
                return@withLock entity.toDomainModel()
            }
            if (item.contentHash != null && old?.contentHash != item.contentHash) {
                throw IOException("This note changed in another editor. Reopen it before saving.")
            }
            val cancellation = currentCoroutineContext()
            check(NoteSizePolicy.acceptsEdit(NoteEditorWindows.utf8Bytes(item.content) { cancellation.ensureActive() }, old?.byteLength ?: 0)) {
                "Note exceeds 55 MB. Existing oversized notes may be edited without increasing their size."
            }
            val ref = TextUtf8InputStream(item.content).use { input ->
                store.writeAtomic(item.id, input, allowOversize = (old?.byteLength ?: 0) > NoteSizePolicy.IMPORT_MAX_BYTES) {
                    cancellation.ensureActive(); progress(it)
                }
            }
            cancellation.ensureActive()
            val saved = item.copy(bodyLocation = "FILE", contentHash = ref.hash, byteLength = ref.bytes,
                sizeBytes = ref.bytes, preview = ref.preview, formatVersion = 1, bodyMissing = false)
            database.withTransaction {
                dao.insertItem(SavedItemEntity.fromDomainModel(saved))
                index(item.id, ref.hash)
            }
            runCatching { store.committed(item.id, ref.hash) }

            saved
        }
    }

    suspend fun load(id: String, includeSmallBody: Boolean = true): SavedItem? = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val metadata = dao.getItemById(id) ?: return@withLock null
            if (!isNote(metadata.toDomainModel())) return@withLock metadata.toDomainModel()
            val ref = try {
                val reference = dao.getNoteBodyMetadata(id) ?: throw IOException("Note is missing")
                if (reference.bodyLocation == "FILE" && reference.formatVersion == 1 && reference.contentHash != null &&
                    store.size(id, reference.contentHash) == reference.byteLength) reference
                else migrateOneLocked(id)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                dao.markBodyMissing(id, true)
                return@withLock metadata.toDomainModel().copy(bodyMissing = true)
            }
            val summary = dao.getItemById(id)!!.toDomainModel()
            if (!includeSmallBody || ref.byteLength >= NoteSizePolicy.TIER_A_MAX_BYTES) return@withLock summary
            val text = store.open(id, ref.contentHash!!).use { input ->
                val bytes = ByteArrayOutputStream(ref.byteLength.toInt())
                val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > NoteSizePolicy.TIER_A_MAX_BYTES) throw IOException("Body changed while reading")
                    bytes.write(buffer, 0, count)
                }
                bytes.toString("UTF-8")
            }
            summary.copy(content = text, bodyLoaded = true)
        }
    }

    suspend fun window(id: String, hash: String, start: Long): EditableNoteWindow = withContext(Dispatchers.IO) {
        val bytes = store.readRange(id, hash, start, NoteEditorWindows.MAX_CHARACTERS + 4)
        var end = minOf(NoteEditorWindows.MAX_CHARACTERS, bytes.size)
        while (end > 0 && end < bytes.size && (bytes[end].toInt() and 0xC0) == 0x80) end--
        var lines = 0
        for (i in 0 until end) if (bytes[i] == '\n'.code.toByte() && ++lines >= 24) { end = i + 1; break }
        EditableNoteWindow(start, start + end, String(bytes, 0, end, Charsets.UTF_8))
    }

    suspend fun replaceWindow(id: String, hash: String, window: EditableNoteWindow, replacement: NoteByteSource): SavedItem =
        withContext(Dispatchers.IO) {
            NoteWriteLock.mutex.withLock {
                val current = dao.getNoteBodyMetadata(id) ?: throw IOException("Note is missing")
                check(current.contentHash == hash) { "Note changed; reload this section before saving" }
                require(window.start >= 0 && window.end in window.start..current.byteLength)
                val table = PieceTable(FileNoteByteSource(store, id, hash, current.byteLength))
                table.replace(window.start, window.end, replacement)
                check(NoteSizePolicy.acceptsEdit(table.size, current.byteLength)) {
                    "Note exceeds 55 MB. Split it before adding more content."
                }
                val cancellation = currentCoroutineContext()
                val ref = table.openStream().use { input ->
                    store.writeAtomic(id, input, current.byteLength > NoteSizePolicy.IMPORT_MAX_BYTES) {
                        cancellation.ensureActive()
                    }
                }
                cancellation.ensureActive()
                database.withTransaction {
                    check(dao.commitFileBody(id, ref.hash, ref.bytes, ref.preview, current.updatedAt,
                        maxOf(System.currentTimeMillis(), current.updatedAt + 1)) == 1)
                    index(id, ref.hash)
                }
                runCatching { store.committed(id, ref.hash) }
                // Previous verified generation remains available for undo.
                dao.getItemById(id)!!.toDomainModel()
            }
        }
}

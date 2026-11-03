package com.example.notes

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Temporary recovery path for v8 SQLite bodies, before NoteBodyStore migration. */
class LegacyNoteRecovery(
    context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context.applicationContext)
) {
    private val context = context.applicationContext
    private val dao = database.savedItemDao()
    private val mutex = Mutex()
    private val store = PrivateMarkdownStore(this.context)
    private val pages = LinkedHashMap<Pair<String, String>, ByteArray>(8, 0.75f, true)
    @Volatile private var cachedPages: Map<Pair<String, String>, ByteArray> = emptyMap()

    fun observeNotes() = dao.observeRecoveryNotes()

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        TrashManager(context, database, store).delete(id)
        mutex.withLock { pages.keys.removeAll { it.first == id }; cachedPages = pages.toMap() }
    }

    suspend fun bodyMetadata(id: String): NoteBodyMetadata = withContext(Dispatchers.IO) {
        dao.getNoteBodyMetadata(id) ?: throw IOException("Note is missing")
    }
    private fun pageKey(id: String, metadata: NoteBodyMetadata, index: Long) =
        id to "${metadata.contentHash ?: "INLINE:${metadata.updatedAt}"}:$index"

    /** Memory-only lookup for first composition; never waits for disk or a worker lock. */
    fun cachedChunk(note: RecoveryNote, index: Int, chunkBytes: Int, metadata: NoteBodyMetadata): String? {
        require(chunkBytes in 1..128)
        val start = index.toLong() * chunkBytes
        val end = minOf(metadata.byteLength, start + chunkBytes)
        if (start >= end) return ""
        val from = (start - 3).coerceAtLeast(0)
        val until = minOf(metadata.byteLength, end + 3)
        val result = ByteArray((until - from).toInt())
        val snapshot = cachedPages
        var position = from
        while (position < until) {
            val page = snapshot[pageKey(note.id, metadata, position / NoteSizePolicy.IO_CHUNK_BYTES)] ?: return null
            val offset = (position % NoteSizePolicy.IO_CHUNK_BYTES).toInt()
            val count = minOf(page.size - offset, (until - position).toInt())
            if (count <= 0) return null
            page.copyInto(result, (position - from).toInt(), offset, offset + count)
            position += count
        }
        var first = (start - from).toInt()
        var last = (end - from).toInt()
        while (first < result.size && (result[first].toInt() and 0xC0) == 0x80) first++
        while (last < result.size && (result[last].toInt() and 0xC0) == 0x80) last++
        return if (first >= last) "" else String(result, first, last - first, Charsets.UTF_8)
    }
    private suspend fun loadPage(id: String, metadata: NoteBodyMetadata, index: Long) {
        currentCoroutineContext().ensureActive()
        val key = pageKey(id, metadata, index)
        if (pages[key] != null) return
        pages[key] = if (metadata.bodyLocation == "FILE") {
            store.readRange(id, metadata.contentHash ?: throw IOException("Body reference missing"),
                index * NoteSizePolicy.IO_CHUNK_BYTES, NoteSizePolicy.IO_CHUNK_BYTES)
        } else dao.readRecoveryBytes(id, index * NoteSizePolicy.IO_CHUNK_BYTES + 1, NoteSizePolicy.IO_CHUNK_BYTES)
            ?: throw IOException("Note is no longer available")
        while (pages.size > 8) pages.remove(pages.keys.first())
        cachedPages = pages.toMap() // Immutable arrays/map published after IO; UI reads never acquire the IO mutex.
    }
    suspend fun prefetchDisplay(note: RecoveryNote, metadata: NoteBodyMetadata, byteOffset: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val page = byteOffset.coerceAtLeast(0) / NoteSizePolicy.IO_CHUNK_BYTES
            for (index in page..page + 2) if (index * NoteSizePolicy.IO_CHUNK_BYTES < metadata.byteLength) loadPage(note.id, metadata, index)
        }
    }
    suspend fun displayChunk(note: RecoveryNote, index: Int, chunkBytes: Int, snapshot: NoteBodyMetadata? = null): String =
        withContext(Dispatchers.IO) {
            val metadata = snapshot ?: bodyMetadata(note.id)
            cachedChunk(note, index, chunkBytes, metadata)?.let { return@withContext it }
            val start = index.toLong() * chunkBytes
            mutex.withLock {
                val from = (start - 3).coerceAtLeast(0) / NoteSizePolicy.IO_CHUNK_BYTES
                val until = minOf(metadata.byteLength, start + chunkBytes + 3) / NoteSizePolicy.IO_CHUNK_BYTES
                for (page in from..until) if (page * NoteSizePolicy.IO_CHUNK_BYTES < metadata.byteLength) loadPage(note.id, metadata, page)
            }
            cachedChunk(note, index, chunkBytes, metadata) ?: throw IOException("Note changed while it was being read")
        }

    /** Raw Markdown export; original SQLite content is never modified. */
    suspend fun export(note: RecoveryNote, destination: Uri, progress: (Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val jobContext = currentCoroutineContext()
            database.runInTransaction {
                val metadata = dao.getNoteBodyMetadata(note.id) ?: throw IOException("Note is missing")
                val input = if (metadata.bodyLocation == "FILE")
                    store.open(note.id, metadata.contentHash ?: throw IOException("Body reference is missing"))
                else LegacyBodyInputStream(dao, note.id)
                input.use { source ->
                    context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                        val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        var offset = 0L
                        while (true) {
                            jobContext.ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            offset += count
                            progress(offset)
                        }
                        check(offset == metadata.byteLength) { "Body length verification failed" }
                        if (metadata.bodyLocation == "FILE") {
                            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                            check(hash == metadata.contentHash) { "Body hash verification failed" }
                        }
                    } ?: throw IOException("Could not open the selected document")
                }
            }
        }
}

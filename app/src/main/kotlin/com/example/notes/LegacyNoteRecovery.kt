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

    fun observeNotes() = dao.observeRecoveryNotes()

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            database.withTransaction {
                dao.deleteRecoveryNote(id)
                dao.deleteNoteSearch(id)
            }
            store.deleteAll(id)
        }
        mutex.withLock { pages.keys.removeAll { it.first == id } }
    }

    suspend fun displayChunk(note: RecoveryNote, index: Int, chunkBytes: Int): String =
        withContext(Dispatchers.IO) {
            require(chunkBytes in 1..128)
            val metadata = dao.getNoteBodyMetadata(note.id) ?: throw IOException("Note is missing")
            val start = index.toLong() * chunkBytes
            val end = minOf(note.byteLength, start + chunkBytes)
            if (start >= end) return@withContext ""
            val from = (start - 3).coerceAtLeast(0)
            val until = minOf(note.byteLength, end + 3)
            val bytes = mutex.withLock {
                val result = ByteArray((until - from).toInt())
                var position = from
                while (position < until) {
                    currentCoroutineContext().ensureActive()
                    val pageIndex = position / NoteSizePolicy.IO_CHUNK_BYTES
                    val key = note.id to "${metadata.contentHash ?: "INLINE"}:$pageIndex"
                    val page = pages[key] ?: (
                        if (metadata.bodyLocation == "FILE") {
                            store.readRange(note.id, metadata.contentHash ?: throw IOException("Body reference is missing"),
                                pageIndex * NoteSizePolicy.IO_CHUNK_BYTES, NoteSizePolicy.IO_CHUNK_BYTES)
                        } else dao.readRecoveryBytes(note.id, pageIndex * NoteSizePolicy.IO_CHUNK_BYTES + 1,
                            NoteSizePolicy.IO_CHUNK_BYTES) ?: throw IOException("Note is no longer available")
                    ).also {
                        pages[key] = it
                        while (pages.size > 8) pages.remove(pages.keys.first())
                    }
                    val offset = (position % NoteSizePolicy.IO_CHUNK_BYTES).toInt()
                    val size = minOf(page.size - offset, (until - position).toInt())
                    if (size <= 0) throw IOException("Note changed while it was being read")
                    page.copyInto(result, (position - from).toInt(), offset, offset + size)
                    position += size
                }
                result
            }
            // Extend only to a UTF-8 code point boundary, without duplicating its bytes.
            var first = (start - from).toInt()
            var last = (end - from).toInt()
            while (first < bytes.size && (bytes[first].toInt() and 0xC0) == 0x80) first++
            while (last < bytes.size && (bytes[last].toInt() and 0xC0) == 0x80) last++
            if (first >= last) "" else String(bytes, first, last - first, Charsets.UTF_8)
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

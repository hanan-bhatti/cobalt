package com.example.notes

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.*
import java.security.MessageDigest
import java.util.UUID

private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** The only component allowed to access private Markdown body files. All calls are IO work. */
interface NoteBodyStore {
    fun open(id: String, hash: String): InputStream
    fun readRange(id: String, hash: String, offset: Long, count: Int): ByteArray
    fun openRangeReader(id: String, hash: String): NoteRangeReader {
        val store = this
        return object : NoteRangeReader {
            override fun read(offset: Long, count: Int) = store.readRange(id, hash, offset, count)
            override fun close() = Unit
        }
    }
    fun writeAtomic(id: String, input: InputStream, allowOversize: Boolean = false, progress: (Long) -> Unit = {}): NoteBodyRef
    fun verify(id: String, hash: String, bytes: Long): Boolean
    fun delete(id: String, hash: String)
    fun size(id: String, hash: String): Long?
    fun committed(id: String, hash: String) {}
    fun deleteAll(id: String)
    fun createEditJournal(id: String, token: String, base: NoteBodyRef): NoteEditJournal =
        throw UnsupportedOperationException("Edit journals unavailable")
    fun editJournal(id: String, token: String): NoteEditJournal =
        throw UnsupportedOperationException("Edit journals unavailable")
    fun retireEditJournal(id: String, token: String, verified: NoteBodyRef) {}
    fun recoverEditJournal(id: String, token: String, base: NoteBodyRef, checkCancelled: () -> Unit = {}): NoteBodyRef =
        throw UnsupportedOperationException("Journal recovery unavailable")
    fun maintenanceFiles(): List<NoteStorageFile> = emptyList()
    fun removeMaintenanceFile(entry: NoteStorageFile): Boolean = false
}

/** A confined random reader; opening/reading/closing all happen off Main. */
interface NoteRangeReader : Closeable { fun read(offset: Long, count: Int): ByteArray }

data class NoteBodyRef(val hash: String, val bytes: Long, val preview: String)

class PrivateMarkdownStore(context: Context, private val checkpoint: (WriteStage) -> Unit = {}) : NoteBodyStore {
    enum class WriteStage { JOURNAL_SYNCED, CHUNK_WRITTEN, DATA_SYNCED, RENAMED, VERIFIED }
    private val root = File(context.applicationContext.filesDir, "notes")
    private val work = File(context.applicationContext.cacheDir, "note-body-work")
    private fun key(id: String) = sha256(id.toByteArray(Charsets.UTF_8))
    private fun directory(id: String) = File(root, key(id))
    private fun file(id: String, hash: String): File {
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid body reference" }
        return File(directory(id), "$hash.md")
    }

    private fun journalFile(id: String, token: String): File {
        require(token.matches(Regex("[0-9a-f-]{36}"))) { "Invalid journal identity" }
        return File(directory(id), "$token.edits")
    }

    override fun createEditJournal(id: String, token: String, base: NoteBodyRef): NoteEditJournal {
        val dir = directory(id)
        check(dir.isDirectory) { "Journal base directory missing" }
        return NoteEditJournal.create(journalFile(id, token), base.hash, base.bytes).also { syncDirectory(dir) }
    }
    override fun editJournal(id: String, token: String): NoteEditJournal = NoteEditJournal(journalFile(id, token))

    override fun recoverEditJournal(id: String, token: String, base: NoteBodyRef, checkCancelled: () -> Unit): NoteBodyRef {
        check(verify(id, base.hash, base.bytes)) { "Recovery base verification failed; originals retained" }
        val log = editJournal(id, token)
        if (log.bytes() == 80L) {
            log.replay(base.hash, base.bytes, { _, _ -> error("Unexpected journal operation") }, {})
            return base
        }
        return open(id, base.hash).use { input ->
            DiskJournalRecovery.replay(input, log, base.hash, base.bytes, work, checkCancelled) { recovered ->
                writeAtomic(id, recovered, allowOversize = base.bytes > NoteSizePolicy.IMPORT_MAX_BYTES) { checkCancelled() }
            }
        }
    }

    override fun retireEditJournal(id: String, token: String, verified: NoteBodyRef) {
        check(verify(id, verified.hash, verified.bytes)) { "Cannot retire recovery before verification" }
        val source = journalFile(id, token)
        if (!source.isFile) return
        // Rename only after a durable preference/DB reference owns the verified full replacement.
        // A crash before this step merely retains extra recovery bytes.
        Os.rename(source.absolutePath, File(source.parentFile, source.name + ".retired").absolutePath)
        syncDirectory(source.parentFile!!)
    }

    override fun maintenanceFiles(): List<NoteStorageFile> = root.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.matches(Regex("[0-9a-f]{64}")) }
        .flatMap { dir -> dir.listFiles().orEmpty().filter { it.isFile }.map {
            NoteStorageFile(dir.name, it.name, it.length(), it.lastModified())
        } }

    override fun removeMaintenanceFile(entry: NoteStorageFile): Boolean {
        require(entry.directoryKey.matches(Regex("[0-9a-f]{64}")))
        require(entry.hash != null || (entry.isRetiredJournal && entry.name.matches(Regex("[0-9a-f-]{36}\\.edits\\.retired"))))
        val dir = File(root, entry.directoryKey)
        val target = File(dir, entry.name)
        // A reviewed cleanup plan cannot remove a file that changed after the scan.
        val removed = NoteBodyLeases.guarded {
            if (entry.hash != null && (NoteBodyLeases.active(entry.directoryKey, entry.hash!!) ||
                entry.hash in NoteGenerationHistory.protectedHashes())) return@guarded false
            if (!target.isFile || target.length() != entry.bytes || target.lastModified() != entry.modified) return@guarded false
            if (!target.delete()) throw IOException("Cannot remove the reviewed storage copy")
            true
        }
        if (!removed) return false
        syncDirectory(dir)
        return true
    }

    override fun size(id: String, hash: String): Long? = file(id, hash).takeIf { it.isFile }?.length()

    override fun open(id: String, hash: String): InputStream = NoteBodyLeases.guarded {
        val input = FileInputStream(file(id, hash))
        val identity = key(id)
        NoteBodyLeases.acquire(identity, hash)
        object : FilterInputStream(input) {
            private var closed = false
            override fun close() {
                if (closed) return
                closed = true
                try { super.close() } finally { NoteBodyLeases.release(identity, hash) }
            }
        }
    }

    override fun openRangeReader(id: String, hash: String): NoteRangeReader {
        val identity = key(id)
        val input = NoteBodyLeases.guarded {
            RandomAccessFile(file(id, hash), "r").also { NoteBodyLeases.acquire(identity, hash) }
        }
        return object : NoteRangeReader {
            override fun read(offset: Long, count: Int): ByteArray {
                require(offset >= 0 && count in 0..NoteSizePolicy.IO_CHUNK_BYTES)
                input.seek(offset)
                return ByteArray(minOf(count.toLong(), (input.length() - offset).coerceAtLeast(0)).toInt()).also { input.readFully(it) }
            }
            private var closed = false
            override fun close() {
                if (closed) return
                closed = true
                try { input.close() } finally { NoteBodyLeases.release(identity, hash) }
            }
        }
    }

    override fun readRange(id: String, hash: String, offset: Long, count: Int): ByteArray {
        require(offset >= 0 && count in 0..NoteSizePolicy.IO_CHUNK_BYTES)
        return RandomAccessFile(file(id, hash), "r").use { input ->
            input.seek(offset)
            val result = ByteArray(minOf(count.toLong(), (input.length() - offset).coerceAtLeast(0)).toInt())
            input.readFully(result)
            result
        }
    }

    override fun writeAtomic(id: String, input: InputStream, allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef {
        val dir = directory(id)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create private note directory")
        syncDirectory(root.parentFile!!)
        syncDirectory(root)
        val token = UUID.randomUUID().toString()
        val temp = File(dir, "$token.tmp")
        val journal = File(dir, "$token.journal")
        FileOutputStream(journal).use { it.write("WRITING\n$token.tmp\n".toByteArray()); it.fd.sync() }
        syncDirectory(dir)
        checkpoint(WriteStage.JOURNAL_SYNCED)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val prefix = ByteArrayOutputStream(4096)
            var bytes = 0L
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    bytes += count
                    if (!allowOversize && bytes > NoteSizePolicy.IMPORT_MAX_BYTES) {
                        throw NoteSizeExceededException()
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    if (prefix.size() < 4096) prefix.write(buffer, 0, minOf(count, 4096 - prefix.size()))
                    progress(bytes)
                    checkpoint(WriteStage.CHUNK_WRITTEN)
                }
                output.fd.sync()
                checkpoint(WriteStage.DATA_SYNCED)
            }
            val hash = digest.digest().hex()
            val destination = file(id, hash)
            if (destination.exists()) {
                if (!verify(id, hash, bytes)) throw IOException("Existing body generation failed verification")
                temp.delete()
            } else {
                Os.rename(temp.absolutePath, destination.absolutePath)
                syncDirectory(dir)
                checkpoint(WriteStage.RENAMED)
            }
            if (!verify(id, hash, bytes)) throw IOException("Markdown verification failed; original retained")
            // This durable journal is removed only after Room commits the new reference.
            FileOutputStream(journal).use { it.write("VERIFIED\n$hash\n$bytes\n".toByteArray()); it.fd.sync() }
            syncDirectory(dir)
            checkpoint(WriteStage.VERIFIED)
            val preview = prefix.toByteArray().toString(Charsets.UTF_8)
                .replace(Regex("[\\r\\n\\t]+"), " ").replace("\u0000", "")
                .replace(Regex("^\\s*[#>*-]+\\s*"), "").take(NoteSizePolicy.PREVIEW_CHARACTERS)
            return NoteBodyRef(hash, bytes, preview)
        } catch (error: Throwable) {
            temp.delete() // Never delete any pre-existing body generation on failure.
            throw error
        }
    }

    override fun verify(id: String, hash: String, bytes: Long): Boolean {
        val body = file(id, hash)
        if (!body.isFile || body.length() != bytes) return false
        return body.inputStream().use { digest(it) } == hash
    }

    /** Called after the DB commit; journals never authorize changing a DB reference. */
    override fun committed(id: String, hash: String) {
        var changed = false
        directory(id).listFiles()?.filter { it.extension == "journal" }?.forEach { journal ->
            val lines = journal.readLines()
            if (lines.getOrNull(0) == "VERIFIED" && lines.getOrNull(1) == hash && journal.delete()) changed = true
        }
        if (changed) syncDirectory(directory(id))
    }

    override fun delete(id: String, hash: String) {
        val body = file(id, hash)
        if (body.exists() && !body.delete()) throw IOException("Cannot remove superseded body")
        syncDirectory(directory(id))
    }

    override fun deleteAll(id: String) {
        val dir = directory(id)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { if (!it.delete()) throw IOException("Could not remove a private body generation") }
        if (!dir.delete()) throw IOException("Could not remove private note directory")
        syncDirectory(root)
    }

    /** Interrupted writes remain recoverable; never promote or discard them automatically. */
    fun retainedInterruptedWrites(): Int = root.listFiles()?.filter { it.isDirectory }?.sumOf { dir ->
        dir.listFiles()?.count { it.extension in listOf("tmp", "journal") } ?: 0
    } ?: 0

    private fun syncDirectory(dir: File) {
        val descriptor = Os.open(dir.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }

    companion object {
        fun digest(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
            return digest.digest().hex()
        }
        fun storageKey(id: String): String = sha256(id.toByteArray(Charsets.UTF_8))
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    }
}

/** Encode an existing editor value without allocating a second, whole-body byte array. */
class TextUtf8InputStream(private val text: CharSequence) : InputStream() {
    private var offset = 0
    private var buffer = ByteArray(0)
    private var position = 0
    private fun availableChunk(): Boolean {
        if (position < buffer.size) return true
        if (offset >= text.length) return false
        var end = minOf(offset + 1024, text.length)
        if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        buffer = text.subSequence(offset, end).toString().toByteArray(Charsets.UTF_8)
        offset = end
        position = 0
        return true
    }
    override fun read(): Int = if (availableChunk()) buffer[position++].toInt() and 0xff else -1
    override fun read(bytes: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
        if (len == 0) return 0
        if (!availableChunk()) return -1
        val count = minOf(len, buffer.size - position)
        buffer.copyInto(bytes, off, position, position + count)
        position += count
        return count
    }
}

/** A non-copying UTF-16 slice; the encoder materializes only its 1024-character chunks. */
class NoteTextSlice(private val text: CharSequence, private val start: Int, private val end: Int) : CharSequence {
    init { require(start in 0..end && end <= text.length) }
    override val length get() = end - start
    override fun get(index: Int): Char { require(index in 0 until length); return text[start + index] }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex in 0..endIndex && endIndex <= length)
        return NoteTextSlice(text, start + startIndex, start + endIndex)
    }
    override fun toString() = buildString(length) { for (index in start until end) append(text[index]) }
}

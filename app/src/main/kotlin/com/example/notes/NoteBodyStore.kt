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
    fun writeAtomic(id: String, input: InputStream, allowOversize: Boolean = false, progress: (Long) -> Unit = {}): NoteBodyRef
    fun verify(id: String, hash: String, bytes: Long): Boolean
    fun delete(id: String, hash: String)
    fun size(id: String, hash: String): Long?
    fun committed(id: String, hash: String) {}
    fun deleteAll(id: String)
}

data class NoteBodyRef(val hash: String, val bytes: Long, val preview: String)

class PrivateMarkdownStore(context: Context) : NoteBodyStore {
    private val root = File(context.applicationContext.filesDir, "notes")
    private fun key(id: String) = sha256(id.toByteArray(Charsets.UTF_8))
    private fun directory(id: String) = File(root, key(id))
    private fun file(id: String, hash: String): File {
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid body reference" }
        return File(directory(id), "$hash.md")
    }

    override fun size(id: String, hash: String): Long? = file(id, hash).takeIf { it.isFile }?.length()

    override fun open(id: String, hash: String): InputStream = FileInputStream(file(id, hash))

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
                        throw IOException("Note exceeds 55 MB. Split it into multiple notes.")
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    if (prefix.size() < 4096) prefix.write(buffer, 0, minOf(count, 4096 - prefix.size()))
                    progress(bytes)
                }
                output.fd.sync()
            }
            val hash = digest.digest().hex()
            val destination = file(id, hash)
            if (destination.exists()) {
                if (!verify(id, hash, bytes)) throw IOException("Existing body generation failed verification")
                temp.delete()
            } else {
                Os.rename(temp.absolutePath, destination.absolutePath)
                syncDirectory(dir)
            }
            if (!verify(id, hash, bytes)) throw IOException("Markdown verification failed; original retained")
            // This durable journal is removed only after Room commits the new reference.
            FileOutputStream(journal).use { it.write("VERIFIED\n$hash\n$bytes\n".toByteArray()); it.fd.sync() }
            syncDirectory(dir)
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
        directory(id).listFiles()?.filter { it.extension == "journal" }?.forEach { journal ->
            val lines = journal.readLines()
            if (lines.getOrNull(0) == "VERIFIED" && lines.getOrNull(1) == hash) journal.delete()
        }
        syncDirectory(directory(id))
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

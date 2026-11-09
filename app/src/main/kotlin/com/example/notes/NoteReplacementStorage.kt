package com.example.notes

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

/** Stream-only clients also share unchanged prefixes/suffixes, including shifted insertions.
 * The disposable spool is never authoritative; failures leave the saved head untouched.
 */
internal object NoteReplacementStorage {
    fun write(store: NoteBodyStore, work: File, id: String, baseHash: String, input: InputStream,
        allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef {
        NoteCacheWork.acquire(work)
        val scratch = File(work, "${UUID.randomUUID()}.scratch")
        try {
            check(work.isDirectory || work.mkdirs()) { "Note save workspace unavailable" }
            var bytes = 0L
            scratch.outputStream().use { output ->
                val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    bytes = Math.addExact(bytes, n.toLong())
                    if (!allowOversize && bytes > NoteSizePolicy.IMPORT_MAX_BYTES) throw NoteSizeExceededException()
                    progress(bytes)
                    output.write(buffer, 0, n)
                }
            }
            val oldBytes = requireNotNull(store.size(id, baseHash)) { "Original note unavailable" }
            store.openRangeReader(id, baseHash).use { original ->
                RandomAccessFile(scratch, "r").use { incoming ->
                    val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                    fun match(oldAt: Long, newAt: Long, count: Int, reverse: Boolean): Int {
                        progress(bytes)
                        val old = original.read(oldAt, count)
                        check(old.size == count) { "Original note is incomplete" }
                        incoming.seek(newAt); incoming.readFully(buffer, 0, count)
                        var found = 0
                        while (found < count) {
                            val i = if (reverse) count - 1 - found else found
                            if (old[i] != buffer[i]) break
                            found++
                        }
                        return found
                    }
                    var prefix = 0L
                    val common = minOf(oldBytes, bytes)
                    while (prefix < common) {
                        val count = minOf(buffer.size.toLong(), common - prefix).toInt()
                        val found = match(prefix, prefix, count, false)
                        prefix += found
                        if (found < count) break
                    }
                    var suffix = 0L
                    while (suffix < common - prefix) {
                        val count = minOf(buffer.size.toLong(), common - prefix - suffix).toInt()
                        val found = match(oldBytes - suffix - count, bytes - suffix - count, count, true)
                        suffix += found
                        if (found < count) break
                    }
                    val middle = bytes - prefix - suffix
                    val pieces = buildList {
                        if (prefix > 0) add(SharedNotePiece(baseHash, 0, prefix))
                        if (middle > 0) add(SharedNotePiece(null, 0, middle) {
                            val source = object : NoteByteSource {
                                override val size = bytes
                                override fun read(offset: Long, count: Int): ByteArray = RandomAccessFile(scratch, "r").use {
                                    it.seek(offset); ByteArray(count).also(it::readFully)
                                }
                            }
                            source.openRange(prefix, middle)
                        })
                        if (suffix > 0) add(SharedNotePiece(baseHash, oldBytes - suffix, suffix))
                    }
                    return scratch.inputStream().use { store.writePieces(id, it, pieces, allowOversize, progress) }
                }
            }
        } finally { scratch.delete(); NoteCacheWork.release(work) }
    }
}

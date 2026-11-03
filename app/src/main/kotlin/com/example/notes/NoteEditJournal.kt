package com.example.notes

import java.io.*
import java.util.zip.CRC32

/** Checksummed UTF-16 edit transactions. IO only; files are obtained through NoteBodyStore. */
class NoteEditJournal internal constructor(private val file: File) {
    data class Replay(val operations: Int, val validBytes: Long, val tornTail: Boolean)
    data class Edit(val start: Int, val removed: Int, val inserted: CharSequence)
    fun append(start: Int, removed: Int, inserted: CharSequence) = appendBatch(listOf(Edit(start, removed, inserted)))

    /** A native delete+insert replacement is one frame: recovery cannot apply only its deletion. */
    fun appendBatch(edits: List<Edit>) = appendTransactions(listOf(edits))
    fun appendTransactions(transactions: List<List<Edit>>) {
        FileOutputStream(file, true).use { raw ->
            val output = DataOutputStream(BufferedOutputStream(raw, 16 * 1024))
            for (edits in transactions) {
                require(edits.size in 1..MAX_EDITS)
                var payload = 4L
                edits.forEach {
                    require(it.start >= 0 && it.removed >= 0 && it.inserted.length <= MAX_CHARACTERS)
                    payload = Math.addExact(payload, 12L + it.inserted.length.toLong() * 2)
                }
                output.writeInt(FRAME); output.writeLong(payload)
                val crc = CRC32()
                fun checked(value: Int) { output.writeByte(value); crc.update(value and 255) }
                fun integer(value: Int) { checked(value ushr 24); checked(value ushr 16); checked(value ushr 8); checked(value) }
                integer(edits.size)
                for ((start, removed, inserted) in edits) {
                    integer(start); integer(removed); integer(inserted.length)
                    for (index in inserted.indices) {
                        val value = inserted[index].code
                        checked(value ushr 8); checked(value)
                    }
                }
                output.writeLong(crc.value)
            }
            output.flush(); raw.fd.sync()
        }
    }

    /** Validate an entire transaction before applying it, streaming insertions in bounded chunks. */
    fun replay(expectedHash: String, expectedBytes: Long,
        begin: (Int, Int) -> Unit, insert: (String) -> Unit, finish: () -> Unit = {}): Replay {
        RandomAccessFile(file, "r").use { input ->
            check(input.readInt() == MAGIC && input.readInt() == VERSION) { "Unknown edit journal; retained" }
            val base = ByteArray(64).also { input.readFully(it) }.toString(Charsets.US_ASCII)
            check(base == expectedHash && input.readLong() == expectedBytes) { "Edit journal belongs to another body" }
            var valid = input.filePointer
            var operations = 0
            while (input.filePointer < input.length()) {
                if (input.length() - input.filePointer < 12) return Replay(operations, valid, true)
                check(input.readInt() == FRAME) { "Invalid journal frame; retained" }
                val bytes = input.readLong()
                check(bytes in 16..MAX_FRAME_BYTES) { "Invalid journal frame length; retained" }
                val payload = input.filePointer
                if (bytes > input.length() - payload - 8) return Replay(operations, valid, true)
                val end = payload + bytes
                val crc = CRC32()
                val buffer = ByteArray(16 * 1024)
                while (input.filePointer < end) {
                    val count = minOf(buffer.size.toLong(), end - input.filePointer).toInt()
                    input.readFully(buffer, 0, count); crc.update(buffer, 0, count)
                }
                check(input.readLong() == crc.value) { "Journal checksum failed; retained" }
                val next = input.filePointer
                input.seek(payload)
                val count = input.readInt()
                check(count in 1..MAX_EDITS) { "Invalid operation count; retained" }
                repeat(count) {
                    check(end - input.filePointer >= 12) { "Incomplete operation; retained" }
                    val start = input.readInt(); val removed = input.readInt(); val chars = input.readInt()
                    check(start >= 0 && removed >= 0 && chars in 0..MAX_CHARACTERS && chars.toLong() * 2 <= end - input.filePointer) {
                        "Invalid journal operation; retained"
                    }
                    begin(start, removed)
                    var remaining = chars
                    while (remaining > 0) {
                        val length = minOf(2048, remaining)
                        insert(String(CharArray(length) { input.readChar() })); remaining -= length
                    }
                    finish()
                    operations++
                }
                check(input.filePointer == end) { "Unexpected journal payload; retained" }
                input.seek(next); valid = next
            }
            return Replay(operations, valid, false)
        }
    }

    internal fun bytes(): Long = file.length()
    companion object {
        private const val MAGIC = 0x434E454A
        private const val FRAME = 0x45444954
        private const val VERSION = 2
        private const val MAX_CHARACTERS = 110_000_000
        private const val MAX_EDITS = 64
        private const val MAX_FRAME_BYTES = 1_000_000_000L
        internal fun create(file: File, baseHash: String, baseBytes: Long): NoteEditJournal {
            require(baseHash.matches(Regex("[0-9a-f]{64}")) && baseBytes >= 0)
            check(file.createNewFile()) { "Edit journal already exists" }
            FileOutputStream(file).use { raw ->
                val output = DataOutputStream(raw)
                output.writeInt(MAGIC); output.writeInt(VERSION)
                output.write(baseHash.toByteArray(Charsets.US_ASCII)); output.writeLong(baseBytes)
                raw.fd.sync()
            }
            return NoteEditJournal(file)
        }
    }
}

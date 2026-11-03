package com.example.notes

import java.io.*
import java.nio.charset.CodingErrorAction

/** Virtual UTF-16 bytes over an immutable UTF-8 original. Index bookmarks, not body copies, live in RAM. */
internal class IndexedUtf8Units private constructor(
    private val source: NoteByteSource,
    private val units: LongArray,
    private val bytes: LongArray,
    val characters: Int
) : NoteByteSource {
    override val size = characters.toLong() * 2
    private fun bookmark(character: Long): Int {
        val found = units.binarySearch(character)
        return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
    }
    override fun read(offset: Long, count: Int): ByteArray {
        require(offset in 0..size && count >= 0)
        return openRange(offset, minOf(count.toLong(), size - offset)).use { it.readBytes() }
    }
    override fun openRange(offset: Long, length: Long): InputStream {
        require(offset in 0..size && length in 0..(size - offset))
        val character = offset / 2
        val mark = bookmark(character)
        val raw = source.openRange(bytes[mark], source.size - bytes[mark])
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val reader = BufferedReader(InputStreamReader(raw, decoder), 4096)
        try {
            var skip = character - units[mark]
            while (skip > 0) {
                val count = reader.skip(skip)
                if (count == 0L) throw EOFException("UTF-8 source changed")
                skip -= count
            }
        } catch (error: Throwable) { reader.close(); throw error }
        return object : InputStream() {
            var remaining = length
            var low = -1
            var initialLow = offset % 2 == 1L
            override fun read(): Int {
                if (remaining == 0L) return -1
                if (low >= 0) return low.also { low = -1; remaining-- }
                val next = reader.read()
                if (next < 0) throw EOFException("UTF-8 source is incomplete")
                if (initialLow) { initialLow = false; remaining--; return next and 255 }
                low = next and 255; remaining--; return next ushr 8
            }
            override fun read(target: ByteArray, off: Int, len: Int): Int {
                if (off < 0 || len < 0 || off > target.size - len) throw IndexOutOfBoundsException()
                if (len == 0) return 0
                if (remaining == 0L) return -1
                val count = minOf(len.toLong(), remaining, NoteSizePolicy.IO_CHUNK_BYTES.toLong()).toInt()
                repeat(count) { target[off + it] = read().toByte() }
                return count
            }
            override fun close() = reader.close()
        }
    }
    companion object {
        fun build(source: NoteByteSource, checkCancelled: () -> Unit = {}): IndexedUtf8Units {
            val marks = ArrayList<Long>(); val offsets = ArrayList<Long>()
            marks.add(0); offsets.add(0)
            var chars = 0L; var position = 0L; var nextMark = 4096L
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            source.openRange(0, source.size).use { raw ->
                BufferedReader(InputStreamReader(raw, decoder), NoteSizePolicy.IO_CHUNK_BYTES).use { reader ->
                    while (true) {
                        if (chars % 4096 == 0L) checkCancelled()
                        val value = reader.read()
                        if (value < 0) break
                        when {
                            Character.isHighSurrogate(value.toChar()) -> {
                                val low = reader.read()
                                check(low >= 0 && Character.isLowSurrogate(low.toChar())) { "Invalid UTF-8 scalar" }
                                chars += 2; position += 4
                            }
                            value < 0x80 -> { chars++; position++ }
                            value < 0x800 -> { chars++; position += 2 }
                            else -> { chars++; position += 3 }
                        }
                        require(chars <= Int.MAX_VALUE) { "This file exceeds Android text-position capacity; export is still available" }
                        if (chars >= nextMark) {
                            marks.add(chars); offsets.add(position); nextMark = chars + 4096
                            checkCancelled()
                        }
                    }
                }
            }
            check(position == source.size) { "Original body length changed" }
            return IndexedUtf8Units(source, marks.toLongArray(), offsets.toLongArray(), chars.toInt())
        }
    }
}

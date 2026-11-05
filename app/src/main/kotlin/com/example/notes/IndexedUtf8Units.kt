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
            private val chars = CharArray(4096)
            private val buffer = ByteArray(8192)
            private var position = 0
            private var available = 0
            private var initialLow = offset % 2 == 1L
            private fun fill(): Boolean {
                if (remaining == 0L) return false
                if (position < available) return true
                val count = reader.read(chars, 0, minOf(chars.size.toLong(), (remaining + 2) / 2).toInt())
                if (count < 0) throw EOFException("UTF-8 source is incomplete")
                if (count == 0) return fill()
                repeat(count) { buffer[it * 2] = (chars[it].code ushr 8).toByte(); buffer[it * 2 + 1] = chars[it].code.toByte() }
                position = if (initialLow) 1 else 0; initialLow = false; available = count * 2
                return true
            }
            override fun read(): Int {
                if (!fill()) return -1
                remaining--; return buffer[position++].toInt() and 255
            }
            override fun read(target: ByteArray, off: Int, len: Int): Int {
                if (off < 0 || len < 0 || off > target.size - len) throw IndexOutOfBoundsException()
                if (len == 0) return 0
                if (!fill()) return -1
                val count = minOf(len.toLong(), remaining, (available - position).toLong()).toInt()
                buffer.copyInto(target, off, position, position + count); position += count; remaining -= count
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
                    val buffer = CharArray(4096)
                    var pendingHigh = false
                    while (true) {
                        checkCancelled()
                        val count = reader.read(buffer)
                        if (count < 0) break
                        var index = 0
                        while (index < count) {
                            val value = buffer[index++].code
                            when {
                                pendingHigh -> { check(Character.isLowSurrogate(value.toChar())); chars += 2; position += 4; pendingHigh = false }
                                Character.isHighSurrogate(value.toChar()) -> { pendingHigh = true; continue }
                                value < 0x80 -> { chars++; position++ }
                                value < 0x800 -> { chars++; position += 2 }
                                else -> { chars++; position += 3 }
                            }
                            require(chars <= Int.MAX_VALUE) { "This file exceeds Android text-position capacity; export is still available" }
                            if (chars >= nextMark) { marks.add(chars); offsets.add(position); nextMark = chars + 4096 }
                        }
                    }
                    check(!pendingHigh) { "Incomplete UTF-8 scalar" }
                }
            }
            check(position == source.size) { "Original body length changed" }
            return IndexedUtf8Units(source, marks.toLongArray(), offsets.toLongArray(), chars.toInt())
        }
    }
}

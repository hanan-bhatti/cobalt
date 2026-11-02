package com.example.notes

import java.io.InputStream
import java.io.PushbackInputStream

/** Sequential bounded streams; concatenating their bytes reproduces the source exactly. */
class Utf8NoteParts(input: InputStream, private val maxBytes: Long = NoteSizePolicy.SPLIT_TARGET_BYTES) {
    private val source = PushbackInputStream(input, 4)
    private var active = false
    init { require(maxBytes >= 4) }

    fun next(): InputStream? {
        check(!active) { "Consume the previous note part before requesting the next" }
        val first = source.read()
        if (first < 0) return null
        source.unread(first)
        active = true
        return object : InputStream() {
            private var remaining = maxBytes
            private var buffer = ByteArray(0)
            private var position = 0
            private var finished = false

            private fun fill(): Boolean {
                if (position < buffer.size) return true
                if (finished || remaining == 0L) { active = false; return false }
                val bytes = ByteArray(minOf(NoteSizePolicy.IO_CHUNK_BYTES.toLong(), remaining).toInt())
                var count = 0
                var eof = false
                while (count < bytes.size) {
                    val read = source.read(bytes, count, bytes.size - count)
                    if (read < 0) { eof = true; break }
                    if (read == 0) {
                        val one = source.read()
                        if (one < 0) { eof = true; break }
                        bytes[count++] = one.toByte()
                    } else count += read
                }
                var end = count
                if (!eof && count > 0) {
                    var start = count - 1
                    while (start > 0 && (bytes[start].toInt() and 0xC0) == 0x80) start--
                    val lead = bytes[start].toInt() and 0xff
                    val needed = when (lead) { in 0xC2..0xDF -> 2; in 0xE0..0xEF -> 3; in 0xF0..0xF4 -> 4; else -> 1 }
                    if (count - start < needed) { source.unread(bytes, start, count - start); end = start }
                }
                if (end == 0) { finished = true; active = false; return false }
                buffer = if (end == bytes.size) bytes else bytes.copyOf(end)
                position = 0
                remaining -= end
                if (eof) finished = true
                return true
            }
            override fun read(): Int = if (fill()) buffer[position++].toInt() and 0xff else -1
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
                if (len == 0) return 0
                if (!fill()) return -1
                val count = minOf(len, buffer.size - position)
                buffer.copyInto(bytes, off, position, position + count)
                position += count
                return count
            }
        }
    }
}

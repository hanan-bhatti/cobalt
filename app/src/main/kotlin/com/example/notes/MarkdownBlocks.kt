package com.example.notes

import java.io.Closeable
import java.io.InputStream

/** Formatting is allowed only for complete blocks ending outside a fence. */
object MarkdownBlocks {
    data class Block(val start: Long, val end: Long, val markdown: Boolean)

    /** Stateful scanner: a caller can show the first block without scanning the rest of the file. */
    class Cursor(source: InputStream, private val maxBytes: Int = 1024, private val maxLines: Int = 16,
        private val checkCancelled: () -> Unit = {}) : Closeable {
        private val input = source.buffered(NoteSizePolicy.IO_CHUNK_BYTES)
        private val prefix = StringBuilder(128)
        private var offset = 0L
        private var start = 0L
        private var lines = 0
        private var safeStart = true
        private var nonBlank = false
        private var blankLine = true
        private var fence: Char? = null
        private var fenceLength = 0
        private var pending = -1
        private var finished = false
        init { require(maxBytes in 16..4096 && maxLines in 1..48) }
        private fun emit(safeEnd: Boolean): Block {
            val block = Block(start, offset, safeStart && safeEnd)
            start = offset; lines = 0; nonBlank = false; safeStart = safeEnd
            return block
        }
        fun next(): Block? {
            if (finished) return null
            while (true) {
                if (offset % NoteSizePolicy.IO_CHUNK_BYTES == 0L) checkCancelled()
                val byte = if (pending >= 0) pending.also { pending = -1 } else input.read()
                if (byte < 0) {
                    finished = true
                    return if (offset > start) emit(fence == null) else null
                }
                if ((offset - start >= maxBytes || lines >= maxLines) && (byte and 0xC0) != 0x80) {
                    pending = byte
                    return emit(false)
                }
                offset++
                if (byte == 10) {
                    val text = prefix.toString().removeSuffix("\r")
                    val indent = text.takeWhile { it == ' ' }.length
                    if (indent <= 3) {
                        val body = text.drop(indent)
                        val first = body.firstOrNull()
                        val run = if (first == '`' || first == '~') body.takeWhile { it == first }.length else 0
                        if (run >= 3) {
                            if (fence == null) { fence = first; fenceLength = run }
                            else if (first == fence && run >= fenceLength && body.drop(run).isBlank()) fence = null
                        }
                    }
                    lines++
                    val safeEnd = blankLine && fence == null && nonBlank
                    prefix.setLength(0); blankLine = true
                    if (safeEnd) return emit(true)
                } else {
                    if (prefix.length < 128) prefix.append(byte.toChar())
                    if (byte != 32 && byte != 9 && byte != 13) { blankLine = false; nonBlank = true }
                }
            }
        }
        override fun close() = input.close()
    }

    fun scan(source: InputStream, maxBytes: Int = 1024, maxLines: Int = 16,
        checkCancelled: () -> Unit = {}): List<Block> = Cursor(source, maxBytes, maxLines, checkCancelled).use { cursor ->
        buildList { while (true) add(cursor.next() ?: break) }
    }
}

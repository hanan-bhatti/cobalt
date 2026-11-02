package com.example.notes

import java.io.InputStream

/** Formatting is allowed only for complete blocks ending outside a fence. */
object MarkdownBlocks {
    data class Block(val start: Long, val end: Long, val markdown: Boolean)

    fun scan(source: InputStream, maxBytes: Int = 1024, maxLines: Int = 16,
        checkCancelled: () -> Unit = {}): List<Block> {
        require(maxBytes in 16..4096 && maxLines in 1..48)
        val blocks = mutableListOf<Block>()
        val input = source.buffered(NoteSizePolicy.IO_CHUNK_BYTES)
        val prefix = StringBuilder(128)
        var offset = 0L
        var start = 0L
        var lines = 0
        var safeStart = true
        var nonBlank = false
        var blankLine = true
        var fence: Char? = null
        var fenceLength = 0
        fun emit(safeEnd: Boolean) {
            if (offset > start) blocks.add(Block(start, offset, safeStart && safeEnd))
            start = offset; lines = 0; nonBlank = false; safeStart = safeEnd
        }
        while (true) {
            if (offset % NoteSizePolicy.IO_CHUNK_BYTES == 0L) checkCancelled()
            val byte = input.read()
            if (byte < 0) break
            // Never split a UTF-8 sequence at a hard boundary.
            if ((offset - start >= maxBytes || lines >= maxLines) && (byte and 0xC0) != 0x80) emit(false)
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
                if (blankLine && fence == null && nonBlank) emit(true)
                prefix.setLength(0); blankLine = true
            } else {
                if (prefix.length < 128) prefix.append(byte.toChar())
                if (byte != 32 && byte != 9 && byte != 13) { blankLine = false; nonBlank = true }
            }
        }
        emit(fence == null)
        return blocks
    }
}

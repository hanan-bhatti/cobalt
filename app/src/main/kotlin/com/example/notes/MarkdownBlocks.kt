package com.example.notes

import java.io.Closeable
import java.io.InputStream

/** Bounded source ranges with rendering context; the stored source is never rewritten. */
object MarkdownBlocks {
    data class Block(val start: Long, val end: Long, val markdown: Boolean,
        val leading: String = "", val trailing: String = "", val lines: Int = 1,
        val tableHeader: String? = null, val visible: Boolean = true) {
        fun renderSource(text: String): String = tableHeader.orEmpty() + leading + text + trailing
    }

    /** Track common inline delimiters across a bounded range without retaining a paragraph. */
    private class InlineContext {
        private val opened = linkedSetOf<String>()
        private var marker = -1
        private var run = 0
        private var escaped = false
        private var code = ""
        fun reset() { opened.clear(); marker = -1; run = 0; escaped = false; code = "" }
        fun finish(next: Int) {
            if (marker < 0) return
            val char = marker.toChar()
            val token = when (char) {
                '`' -> "`".repeat(run.coerceAtMost(32))
                '~' -> if (run >= 2) "~~" else ""
                else -> char.toString().repeat(run.coerceAtMost(3))
            }
            if (char == '`') {
                if (code == token) code = "" else if (code.isEmpty() && next > 32) code = token
            } else if (code.isEmpty() && token.isNotEmpty()) {
                if (!opened.remove(token) && next > 32) opened.add(token)
            }
            marker = -1; run = 0
        }
        fun accept(value: Int) {
            if (value == marker) { run++; return }
            finish(value)
            if (escaped) { escaped = false; return }
            if (value == 92 && code.isEmpty()) { escaped = true; return }
            if (value == 96 || (code.isEmpty() && (value == 42 || value == 95 || value == 126))) { marker = value; run = 1 }
        }
        fun leading(): String = if (code.isNotEmpty()) code else opened.joinToString("")
        fun trailing(): String = if (code.isNotEmpty()) code else opened.toList().asReversed().joinToString("")
    }

    class Cursor(source: InputStream, private val maxBytes: Int = 1024, private val maxLines: Int = 16,
        private val checkCancelled: () -> Unit = {}) : Closeable {
        private val input = source.buffered(NoteSizePolicy.IO_CHUNK_BYTES)
        private val prefix = StringBuilder(1024)
        private val inline = InlineContext()
        private var offset = 0L
        private var start = 0L
        private var lines = 0
        private var renderLines = 0
        private var hasHeading = false
        private var safeStart = true
        private var nonBlank = false
        private var blankLine = true
        private var fence: Char? = null
        private var fenceLength = 0
        private var fenceInfo = ""
        private var table: String? = null
        private var tableCandidate: String? = null
        private var leading = ""
        private var leadingTable: String? = null
        private var fenceOpening = ""
        private var fenceClosing = ""
        private var prefixFull = false
        private var pending = -1
        private var pendingCr = false
        private var finished = false
        init { require(maxBytes in 16..4096 && maxLines in 1..48) }

        private fun fencePrefix() = fenceOpening
        private fun lineText(): String = ByteArray(prefix.length) { prefix[it].code.toByte() }.toString(Charsets.UTF_8)
        private fun lineContext(): String {
            val text = lineText()
            if (text.startsWith("    ")) return "    "
            val quote = QUOTE.find(text)?.value.orEmpty()
            val list = LIST.find(text.drop(quote.length))?.value.orEmpty()
            val heading = HEADING.find(text.drop(quote.length + list.length))?.value.orEmpty()
            return when { quote.isEmpty() && list.isEmpty() -> heading; heading.isEmpty() -> quote + list; else -> quote + list + heading }
        }
        private fun emit(safeEnd: Boolean): Block {
            val insideLine = prefix.isNotEmpty()
            val closing = when {
                safeStart && safeEnd -> ""
                fence != null -> fenceClosing
                table != null && insideLine -> inline.trailing() + "|"
                else -> inline.trailing()
            }
            val block = Block(start, offset, safeStart && safeEnd, leading, closing, renderLines.coerceAtLeast(1),
                leadingTable, nonBlank || fence != null)
            leading = when {
                fence != null -> fencePrefix()
                table != null -> if (insideLine) "|" + inline.leading() else ""
                insideLine -> lineContext() + inline.leading()
                else -> inline.leading()
            }
            leadingTable = table
            start = offset; lines = 0; renderLines = 0; hasHeading = false; nonBlank = false; safeStart = safeEnd
            return block
        }
        private fun finishLine() {
            inline.finish(10)
            if (!blankLine || fence != null) renderLines++
            val text = lineText().removeSuffix("\r")
            val indent = text.takeWhile { it == ' ' }.length
            if (indent <= 3) {
                val body = text.drop(indent)
                val first = body.firstOrNull()
                val run = if (first == '`' || first == '~') body.takeWhile { it == first }.length else 0
                if (run >= 3) {
                    if (fence == null) {
                        fence = first; fenceLength = run; fenceInfo = body.drop(run).take(128)
                        val marker = first.toString().repeat(run.coerceAtMost(128))
                        fenceOpening = marker + fenceInfo + "\n"; fenceClosing = "\n" + marker
                    }
                    else if (first == fence && run >= fenceLength && body.drop(run).isBlank()) fence = null
                    table = null; tableCandidate = null; inline.reset()
                } else if (fence == null) {
                    if (table == null && tableCandidate != null && TABLE_DIVIDER.matches(body)) table = tableCandidate + "\n" + text + "\n"
                    else if (table != null && (body.isBlank() || '|' !in body)) table = null
                    tableCandidate = if ('|' in text && text.isNotBlank()) text else null
                    if (HEADING.containsMatchIn(body)) hasHeading = true
                }
            }
            if (blankLine) { inline.reset(); table = null; tableCandidate = null }
            prefix.setLength(0); prefixFull = false; blankLine = true
        }
        private fun endLine(): Boolean {
            val endedBlank = blankLine
            lines++
            finishLine()
            return endedBlank && fence == null && nonBlank &&
                (hasHeading || offset - start >= maxBytes * 3 / 4 || renderLines >= maxLines * 3 / 4)
        }
        fun next(): Block? {
            if (finished) return null
            while (true) {
                if (offset % NoteSizePolicy.IO_CHUNK_BYTES == 0L) checkCancelled()
                val byte = if (pending >= 0) pending.also { pending = -1 } else input.read()
                if (pendingCr && byte != 10) {
                    pendingCr = false
                    if (endLine()) { pending = byte; return emit(true) }
                }
                if (byte < 0) {
                    inline.finish(-1)
                    if (prefix.isNotEmpty()) finishLine()
                    finished = true
                    return if (offset > start) emit(fence == null) else null
                }
                val measuredLines = if (fence == null && nonBlank) renderLines else lines
                if (!(pendingCr && byte == 10) && (offset - start >= maxBytes || measuredLines >= maxLines) && (byte and 0xC0) != 0x80) {
                    inline.finish(byte)
                    pending = byte
                    return emit(false)
                }
                offset++
                if (byte == 13) {
                    pendingCr = true
                } else if (byte == 10) {
                    pendingCr = false
                    if (endLine()) return emit(true)
                } else {
                    if (fence == null) inline.accept(byte)
                    if (!prefixFull) {
                        if (prefix.length < 1024 || (byte and 0xC0) == 0x80) prefix.append(byte.toChar())
                        else prefixFull = true
                    }
                    if (byte != 32 && byte != 9 && byte != 13) { blankLine = false; nonBlank = true }
                }
            }
        }
        override fun close() = input.close()
    }

    private val QUOTE = Regex("""^ {0,3}(?:>\s*)+""")
    private val HEADING = Regex("""^ {0,3}#{1,6}\s+""")
    private val LIST = Regex("""^(?:[-+*]|\d+[.)])\s+""")
    private val TABLE_DIVIDER = Regex("""^\|?\s*:?-+:?\s*(?:\|\s*:?-+:?\s*)+\|?\s*$""")

    fun scan(source: InputStream, maxBytes: Int = 1024, maxLines: Int = 16,
        checkCancelled: () -> Unit = {}): List<Block> = Cursor(source, maxBytes, maxLines, checkCancelled).use { cursor ->
        buildList { while (true) add(cursor.next() ?: break) }
    }
}

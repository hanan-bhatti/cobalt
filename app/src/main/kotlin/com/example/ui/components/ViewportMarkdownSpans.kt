package com.example.ui.components

import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.example.notes.NoteSizePolicy

/** Bounded, source-mode Markdown styling. The renderer only looks up already prepared spans. */
internal class ViewportMarkdownSpans(private val content: Content) : Spans {
    private data class Prepared(val version: Long, val lines: Map<Int, List<Span>>)
    @Volatile private var prepared: Prepared? = null
    private val plain = listOf(Span.obtain(0, EditorColorScheme.TEXT_NORMAL.toLong()))

    fun clear() { prepared = null }
    /** Background worker only; copy at most a small visible range, never the whole document. */
    fun prepare(first: Int, last: Int) {
        var version = 0L
        val lines = mutableListOf<Pair<Int, String>>()
        try {
            // Bounds and the bounded copy must share the same read lock. A deletion can
            // otherwise remove the requested lines between lineCount and the bulk read.
            // Content's read lock is reentrant; line zero exists for every live document.
            content.runReadActionsOnLines(0, 0, Content.ContentLineConsumer2 { _, _, _ ->
                version = content.documentVersion
                val from = first.coerceIn(0, content.lineCount - 1)
                val end = last.coerceIn(from, minOf(content.lineCount - 1, from + 160))
                content.runReadActionsOnLines(from, end, Content.ContentLineConsumer2 { index, text, _ ->
                    lines.add(index to text.subSequence(0, minOf(text.length, 4096)).toString())
                })
            })
        } catch (_: IndexOutOfBoundsException) {
            // Disposal may release the model before this cancelled worker obtains its lock.
            clear()
            return
        }
        var links = 0
        val next = HashMap<Int, List<Span>>()
        for ((line, text) in lines) {
            val changes = sortedMapOf(0 to EditorColorScheme.TEXT_NORMAL.toLong())
            if (text.startsWith('#') && text.takeWhile { it == '#' }.length in 1..6) {
                changes[0] = TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
            }
            for (match in TOKENS.findAll(text)) {
                val token = match.value
                val color = when {
                    token.startsWith('`') -> EditorColorScheme.LITERAL
                    token.startsWith('[') || token.startsWith("http") -> {
                        if (++links > NoteSizePolicy.DECORATED_LINK_LIMIT) continue
                        EditorColorScheme.KEYWORD
                    }
                    else -> EditorColorScheme.TEXT_NORMAL
                }
                changes[match.range.first] = TextStyle.makeStyle(color, 0,
                    token.startsWith("**"), token.startsWith('_'), token.startsWith("~~"))
                changes[match.range.last + 1] = EditorColorScheme.TEXT_NORMAL.toLong()
            }
            next[line] = changes.map { (column, style) -> Span.obtain(column, style) }
        }
        if (content.documentVersion == version) prepared = Prepared(version, next)
    }
    private fun spans(line: Int): List<Span> {
        val current = prepared
        return if (current != null && current.version == content.documentVersion) current.lines[line] ?: plain else plain
    }
    override fun adjustOnInsert(start: CharPosition, end: CharPosition) = clear()
    override fun adjustOnDelete(start: CharPosition, end: CharPosition) = clear()
    override fun getLineCount(): Int = content.lineCount
    override fun supportsModify() = false
    override fun modify(): Spans.Modifier = throw UnsupportedOperationException("Viewport spans are immutable")
    override fun read(): Spans.Reader = object : Spans.Reader {
        private var current = plain
        override fun moveToLine(line: Int) { current = spans(line) }
        override fun getSpanCount() = current.size
        override fun getSpanAt(index: Int): Span = current[index]
        override fun getSpansOnLine(line: Int): List<Span> = spans(line)
    }
    companion object {
        private val TOKENS = Regex("""`[^`\r\n]+`|\*\*[^*\r\n]+\*\*|_[^_\r\n]+_|~~[^~\r\n]+~~|\[[^\]\r\n]+\]\([^\s)]+\)|https?://[^\s]+""")
    }
}

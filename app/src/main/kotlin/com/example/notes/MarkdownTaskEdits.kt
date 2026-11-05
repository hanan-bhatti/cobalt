package com.example.notes

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.*
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/** A checkbox changes exactly one source code unit; line endings and all other bytes survive. */
object MarkdownTaskEdits {
    /** Only this edit shape can reuse existing byte offsets and reader layout. */
    fun isMarkerToggle(before: String, after: String): Boolean {
        if (before.length != after.length) return false
        var changed = -1
        for (index in before.indices) if (before[index] != after[index]) {
            if (changed >= 0) return false
            changed = index
        }
        return changed > 0 && changed < before.lastIndex && before[changed - 1] == '[' && before[changed + 1] == ']' &&
            ((before[changed] == ' ' && after[changed] in "xX") || (before[changed] in "xX" && after[changed] == ' '))
    }
    private val parsers = object : ThreadLocal<Parser>() {
        override fun initialValue(): Parser = Parser.builder()
            .extensions(listOf(StrikethroughExtension.create(), TablesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()
    }
    fun toggle(markdown: String, target: Int): String {
        if (target < 0) return markdown
        val starts = ArrayList<Int>().also { it.add(0) }
        var index = 0
        while (index < markdown.length) {
            val char = markdown[index++]
            if (char == '\r') {
                if (index < markdown.length && markdown[index] == '\n') index++
                starts.add(index)
            } else if (char == '\n') starts.add(index)
        }
        val root = parsers.get()!!.parse(markdown)
        var task = 0
        var status = -1
        val pending = ArrayDeque<Node>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (node is ListItem) {
                val prefix = StringBuilder(3)
                var firstLiteral: Node? = null
                val children = ArrayDeque<Node>()
                children.add(node)
                while (children.isNotEmpty() && prefix.length < 3) {
                    val child = children.removeLast()
                    val literal = when (child) { is Text -> child.literal; is Code -> child.literal; else -> null }
                    if (literal != null) {
                        for (char in literal) {
                            if (prefix.isEmpty() && char.isWhitespace()) continue
                            if (firstLiteral == null) firstLiteral = child
                            prefix.append(char)
                            if (prefix.length == 3) break
                        }
                    } else {
                        var next = child.lastChild
                        while (next != null) { children.add(next); next = next.previous }
                    }
                }
                if (prefix.toString() in listOf("[ ]", "[x]", "[X]")) {
                    if (task++ == target) {
                        for (span in firstLiteral?.sourceSpans.orEmpty()) {
                            val start = (starts.getOrNull(span.lineIndex) ?: continue) + span.columnIndex
                            val end = minOf(markdown.length, start + span.length)
                            if (start !in 0..end) continue
                            val match = Regex("\\[([ xX])]").find(markdown.substring(start, end)) ?: continue
                            status = start + match.range.first + 1
                            break
                        }
                        break
                    }
                }
            }
            var child = node.lastChild
            while (child != null) { pending.add(child); child = child.previous }
        }
        if (status < 0) return markdown
        return buildString(markdown.length) {
            append(markdown, 0, status); append(if (markdown[status] == ' ') 'x' else ' ')
            append(markdown, status + 1, markdown.length)
        }
    }
}

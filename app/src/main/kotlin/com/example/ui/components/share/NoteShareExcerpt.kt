package com.example.ui.components.share

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.*
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

internal data class DocumentTruncation(val content: String, val hasContinuation: Boolean)
internal data class MinimalTruncation(val truncatedMarkdown: String, val hasContinuation: Boolean)
internal data class CompactNoteExcerpt(val text: String, val isQuote: Boolean)

private val excerptParser = object : ThreadLocal<Parser>() {
    override fun initialValue(): Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS).build()
}

/** Keep whole Markdown blocks where possible; partial code/table previews stay valid blocks. */
internal fun computeA4Content(noteBody: String, maxLines: Int = 18): DocumentTruncation {
    require(maxLines > 0)
    val clean = noteBody.trim()
    if (clean.isEmpty()) return DocumentTruncation("", false)
    val lines = clean.lines()
    val document = excerptParser.get()!!.parse(clean)
    val parts = mutableListOf<String>()
    var remaining = maxLines
    var block = document.firstChild
    while (block != null) {
        val spans = block.sourceSpans
        val source = if (spans.isEmpty()) plainText(block) else
            lines.subList(spans.first().lineIndex, spans.last().lineIndex + 1).joinToString("\n")
        // Wrapped lines matter too. This is a content budget; layout overflow is checked by the card.
        val cost = source.lines().sumOf { ((it.length + 49) / 50).coerceAtLeast(1) }
        if (cost <= remaining) {
            parts.add(source)
            remaining -= cost + 1
            block = block.next
            if (block == null) return DocumentTruncation(clean, false)
        } else {
            if (remaining > 0) {
                when (block) {
                    is FencedCodeBlock -> if (remaining >= 3) {
                        val fence = block.fenceChar.toString().repeat(block.fenceLength)
                        parts.add("$fence${block.info.orEmpty()}\n" +
                            block.literal.trimEnd().lines().take(remaining - 2).joinToString("\n") + "\n$fence")
                    }
                    is TableBlock -> if (remaining >= 3) parts.add(source.lines().take(remaining).joinToString("\n"))
                    else -> parts.add(shorten(plainText(block), remaining * 50))
                }
            }
            return DocumentTruncation(parts.joinToString("\n\n"), true)
        }
        if (remaining <= 0) return DocumentTruncation(parts.joinToString("\n\n"), block != null)
    }
    return DocumentTruncation(clean, false)
}

internal fun computeMinimalContent(noteBody: String, maxLines: Int = 8): MinimalTruncation {
    val result = computeA4Content(noteBody, maxLines)
    return MinimalTruncation(result.content, result.hasContinuation)
}

/** Prose stays prose; a fenced code line is never mistaken for a quotation. */
internal fun compactNoteExcerpt(noteBody: String): CompactNoteExcerpt {
    val document = excerptParser.get()!!.parse(noteBody.trim())
    val blocks = generateSequence(document.firstChild) { it.next }.toList()
    val quote = blocks.filterIsInstance<BlockQuote>().firstOrNull { plainText(it).isNotBlank() }
    if (quote != null) return CompactNoteExcerpt(shorten(plainText(quote), 180), true)
    val prose = blocks.filterIsInstance<Paragraph>().firstOrNull { plainText(it).isNotBlank() }
    if (prose != null) return CompactNoteExcerpt(shorten(plainText(prose), 180), false)
    val fallback = blocks.firstOrNull { it !is FencedCodeBlock && it !is IndentedCodeBlock && plainText(it).isNotBlank() }
    val text = fallback?.let(::plainText) ?: blocks.firstOrNull()?.let {
        when (it) { is FencedCodeBlock -> it.literal; is IndentedCodeBlock -> it.literal; else -> "" }
    }.orEmpty()
    return CompactNoteExcerpt(shorten(text.trim(), 180), false)
}

internal fun extractCompactNoteContent(noteBody: String): String = compactNoteExcerpt(noteBody).text

private fun plainText(root: Node): String = buildString {
    fun visit(node: Node) {
        when (node) {
            is FencedCodeBlock, is IndentedCodeBlock, is HtmlBlock -> return
            is Text -> { append(node.literal); return }
            is Code -> { append(node.literal); return }
            is SoftLineBreak, is HardLineBreak -> { append(' '); return }
        }
        var child = node.firstChild
        while (child != null) { visit(child); child = child.next }
        if (node is Paragraph || node is Heading || node is ListItem) append(' ')
    }
    visit(root)
}.trim()

/** Do not cut surrogate pairs; prefer a word boundary and make shortening visible. */
private fun shorten(text: String, maxChars: Int): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.codePointCount(0, clean.length) <= maxChars) return clean
    val end = clean.offsetByCodePoints(0, (maxChars - 1).coerceAtLeast(0))
    val prefix = clean.substring(0, end)
    val boundary = prefix.lastIndexOf(' ').takeIf { it > prefix.length / 2 } ?: prefix.length
    return prefix.substring(0, boundary).trimEnd() + "…"
}

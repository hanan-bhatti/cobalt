package com.example.ui.components.share

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.FencedCodeBlock
import org.commonmark.parser.Parser
import org.junit.Assert.*
import org.junit.Test

class NoteShareExcerptTest {
    @Test fun compactExcerptSkipsCodeAndUsesActualProse() {
        val body = "# Heading\n\n```python\nprint(\"migration intact\")\n```\n\nActual note prose."
        val result = compactNoteExcerpt(body)
        assertEquals("Actual note prose.", result.text)
        assertFalse(result.isQuote)
    }
    @Test fun quoteLikeTextInsideCodeIsNotAQuote() {
        val result = compactNoteExcerpt("~~~text\n> fake quotation\n~~~\n\nA real paragraph.")
        assertEquals("A real paragraph.", result.text)
        assertFalse(result.isQuote)
    }
    @Test fun realMultilineQuoteIsPreservedAsAQuote() {
        val result = compactNoteExcerpt("# Title\n\n> A **real** quote\n> across lines.\n\nOther prose")
        assertEquals("A real quote across lines.", result.text)
        assertTrue(result.isQuote)
    }
    @Test fun oversizedCodeExcerptClosesItsFenceAndSignalsContinuation() {
        val source = "```kotlin\n" + (1..40).joinToString("\n") { "val x$it = $it" } + "\n```"
        val result = computeA4Content(source, 6)
        assertTrue(result.hasContinuation)
        assertTrue(result.content.endsWith("```"))
        val code = Parser.builder().build().parse(result.content).firstChild as FencedCodeBlock
        assertTrue(code.literal.contains("val x1"))
        assertFalse(code.literal.contains("val x40"))
    }
    @Test fun partialTableRetainsItsHeaderSeparatorAndCompleteRows() {
        val source = "| Item | Value |\n| --- | --- |\n" + (1..40).joinToString("\n") { "| item$it | value |" }
        val result = computeA4Content(source, 6)
        assertTrue(result.hasContinuation)
        assertEquals(6, result.content.lines().size)
        assertTrue(result.content.lines().all { it.startsWith("|") && it.endsWith("|") })
        val parsed = Parser.builder().extensions(listOf(TablesExtension.create())).build().parse(result.content)
        assertTrue(parsed.firstChild is TableBlock)
    }
    @Test fun longSingleParagraphIsBoundedAndMakesShorteningVisible() {
        val result = computeMinimalContent("This is a long paragraph. ".repeat(300), 8)
        assertTrue(result.hasContinuation)
        assertTrue(result.truncatedMarkdown.length <= 400)
        assertTrue(result.truncatedMarkdown.endsWith("…"))
    }
    @Test fun shortNotesKeepTheirMarkdownAndDoNotClaimContinuation() {
        val source = "# Title\n\n**Short paragraph**\n\n- one\n- two"
        val result = computeA4Content(source, 18)
        assertEquals(source, result.content)
        assertFalse(result.hasContinuation)
        assertFalse(computeMinimalContent("", 8).hasContinuation)
    }
    @Test fun unicodeExcerptDoesNotCutASurrogatePair() {
        val result = extractCompactNoteContent("😀".repeat(400))
        assertTrue(result.endsWith("…"))
        assertEquals(320, result.codePointCount(0, result.length))
        assertFalse(result.dropLast(1).last().isHighSurrogate())
    }
}

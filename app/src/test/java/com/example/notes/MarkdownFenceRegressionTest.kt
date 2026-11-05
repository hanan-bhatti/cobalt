package com.example.notes

import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.junit.Assert.*
import org.junit.Test

class MarkdownFenceRegressionTest {
    private fun codes(source: String): List<String> {
        val found = mutableListOf<String>()
        Parser.builder().build().parse(source).accept(object : AbstractVisitor() {
            override fun visit(node: FencedCodeBlock) { found += node.literal }
        })
        return found
    }
    @Test fun shortExamplesWithBlankLinesStayInOneCodeNodeAcrossTheOrdinaryRowLimit() {
        val body = "Karachi Harbour (KHI-01)\nMon 3 Nov 2026\n\nHigh  05:12   1.9 m\nLow   11:34   0.4 m\n\nHigh  17:48   2.1 m\nLow   23:59   0.3 m\n"
        for (padding in 0..80) {
            val source = "Intro ${"x".repeat(padding)}\n\n```text\n$body```\n\nAfter\n"
            val bytes = source.toByteArray()
            val blocks = MarkdownBlocks.scan(bytes.inputStream(), 128, 8)
            val rendered = blocks.flatMap { codes(it.renderSource(String(bytes, it.start.toInt(), (it.end - it.start).toInt()))) }
            assertEquals("padding=$padding", listOf(body), rendered)
        }
    }
    @Test fun hugeFencesAreBoundedAndContinuationFlagsJoinTheirSurfaces() {
        val source = "```kotlin\n" + "    println(\"value\")\n\n".repeat(1_000) + "```\n"
        val bytes = source.toByteArray()
        val blocks = MarkdownBlocks.scan(bytes.inputStream(), 256, 8)
        assertTrue(blocks.size > 10)
        assertFalse(blocks.first().codeContinuesBefore)
        assertFalse(blocks.last().codeContinuesAfter)
        blocks.zipWithNext().forEach { (a,b) ->
            assertEquals(a.end, b.start)
            assertTrue(a.codeContinuesAfter && b.codeContinuesBefore)
            assertEquals('\n'.code.toByte(), bytes[a.end.toInt() - 1])
        }
        assertEquals(source.substringAfter('\n').substringBeforeLast("```"), blocks.joinToString("") {
            codes(it.renderSource(String(bytes, it.start.toInt(), (it.end - it.start).toInt()))).joinToString("")
        })
        assertTrue(blocks.all { it.end - it.start <= 8192 && it.lines <= 25 })
    }
    @Test fun fencesInsideListsRetainBlankLines() {
        val source = "1. Clone the project:\n\n    ```sh\n    git clone https://example.com/repo\n\n    cd repo\n    ```\n\n2. Run tests.\n"
        val bytes = source.toByteArray()
        val rendered = MarkdownBlocks.scan(bytes.inputStream(), 128, 8).flatMap {
            codes(it.renderSource(String(bytes, it.start.toInt(), (it.end - it.start).toInt())))
        }
        assertEquals(listOf("git clone https://example.com/repo\n\ncd repo\n"), rendered)
    }
    @Test fun reconstructedReadmeKeepsEachOrdinaryCodeExampleWhole() {
        val source = javaClass.getResource("/markdown/tideline-regression.md")!!.readText()
        val bytes = source.toByteArray()
        val expected = codes(source)
        val actual = MarkdownBlocks.scan(bytes.inputStream(), 256, 8).flatMap {
            codes(it.renderSource(String(bytes, it.start.toInt(), (it.end - it.start).toInt())))
        }
        assertEquals(expected, actual)
        assertEquals(7, actual.size)
    }
    @Test fun largeFencesInListsAndQuotesPreserveLiteralIndentationAcrossContinuations() {
        val literal = "first\n\n    indented\n\tkeep-tab\n".repeat(100)
        for (prefix in listOf("    ", "> ")) {
            val intro = if (prefix == "    ") "1. Example:\n\n" else ""
            val source = intro + prefix + "```text\n" + literal.lineSequence().dropLastEmpty()
                .joinToString("\n", postfix = "\n") { prefix + it } + prefix + "```\n"
            val bytes = source.toByteArray()
            val blocks = MarkdownBlocks.scan(bytes.inputStream(), 256, 8)
            assertTrue(blocks.size > 2)
            val actual = blocks.flatMap { codes(it.renderSource(String(bytes, it.start.toInt(), (it.end - it.start).toInt()))) }.joinToString("")
            assertEquals("container=$prefix", codes(source).joinToString(""), actual)
        }
    }
    private fun Sequence<String>.dropLastEmpty(): List<String> = toList().dropLast(1)
    @Test fun emailAndWebLinksKeepPunctuationOutsideDestinations() {
        val text = "Write hello@example.com. Visit https://example.com/doc_(a)."
        val matches = MarkdownAutoLinks.find(text).toList()
        assertEquals(listOf("mailto:hello@example.com", "https://example.com/doc_(a)"), matches.map { it.destination })
        matches.forEach { assertEquals(it.label, text.substring(it.start, it.end)) }
    }
}

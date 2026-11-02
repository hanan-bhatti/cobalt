package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class MarkdownBlocksTest {
    @Test fun blocksPreserveEveryByteAndOnlyFormatWholeParagraphs() {
        val text = "# Heading\n\n" + "[Link](https://example.org)\n\n".repeat(1_000) + "```kt\n" + "x\n".repeat(100) + "```\n\nEnd 😀"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val blocks = MarkdownBlocks.scan(bytes.inputStream(), 128, 8)
        assertEquals(0L, blocks.first().start)
        assertEquals(bytes.size.toLong(), blocks.last().end)
        blocks.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        blocks.forEach {
            assertTrue(it.end - it.start <= 132)
            val part = bytes.copyOfRange(it.start.toInt(), it.end.toInt())
            assertArrayEquals(part, part.toString(Charsets.UTF_8).toByteArray(Charsets.UTF_8))
        }
        assertTrue(blocks.any { !it.markdown })
        assertTrue(blocks.any { it.markdown })
    }
    @Test fun fiftyThousandBlankLinesStayBounded() {
        val blocks = MarkdownBlocks.scan("\n".repeat(50_000).byteInputStream(), 128, 8)
        assertTrue(blocks.size <= 6_251)
        assertTrue(blocks.all { it.end - it.start <= 8 })
    }
    @Test fun generationUndoUsesReferencesAndRejectsWrongOrder() {
        val history = NoteGenerationHistory()
        val before = NoteGeneration("a", 50_000_000, "Before")
        val after = NoteGeneration("b", 50_000_001, "After")
        history.record(before, after)
        val change = history.undoChange!!
        history.didUndo(change)
        assertNull(history.undoChange)
        assertEquals(change, history.redoChange)
        history.didRedo(change)
        assertEquals(change, history.undoChange)
    }
    @Test fun firstBlockDoesNotReadTheWholeLargeDocument() {
        val prefix = "# First heading\n\n".toByteArray()
        var consumed = 0
        val source = object : java.io.InputStream() {
            override fun read(): Int {
                if (consumed >= 55_000_000) return -1
                val index = consumed++
                return if (index < prefix.size) prefix[index].toInt() and 255 else 'x'.code
            }
        }
        MarkdownBlocks.Cursor(source, 128, 8).use { cursor ->
            val first = cursor.next()!!
            assertEquals(0L, first.start)
            assertEquals(prefix.size.toLong(), first.end)
            assertTrue(first.markdown)
            assertTrue("Only a buffered prefix should be read", consumed <= NoteSizePolicy.IO_CHUNK_BYTES)
        }
    }

}

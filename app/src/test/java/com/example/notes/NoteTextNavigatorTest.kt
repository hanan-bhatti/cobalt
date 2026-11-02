package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class NoteTextNavigatorTest {
    @Test fun findsUnicodeAcrossBufferBoundariesAndSupportsNext() {
        val prefix = "x".repeat(NoteSizePolicy.IO_CHUNK_BYTES - 2)
        val text = prefix + "😀漢字 first 😀漢字"
        val first = text.byteInputStream().use { NoteTextNavigator.find(it, "😀漢字") }
        assertEquals(prefix.length.toLong(), first)
        val next = text.byteInputStream().use { NoteTextNavigator.find(it, "😀漢字", first!! + 1) }
        assertTrue(next!! > first!!)
        assertNull(text.byteInputStream().use { NoteTextNavigator.find(it, "missing") })
    }

    @Test fun repeatedPrefixMatchesCorrectly() {
        assertEquals(2L, "aaaab".byteInputStream().use { NoteTextNavigator.find(it, "aab") })
    }

    @Test fun outlinesIgnoreFencesAndKeepByteOffsets() {
        val text = "😀\r\n# First\r\n~~~md\n# Ignored\n~~~\n  ## Second\n"
        val headings = text.byteInputStream().use { NoteTextNavigator.headings(it) }
        assertEquals(listOf("First", "Second"), headings.map { it.title })
        assertEquals("😀\r\n".toByteArray().size.toLong(), headings.first().offset)
    }

    @Test fun headingCountAndLineMemoryAreBounded() {
        val text = (1..1_000).joinToString("\n") { "# Heading $it" }
        assertEquals(NoteTextNavigator.MAX_HEADINGS, text.byteInputStream().use { NoteTextNavigator.headings(it) }.size)
        assertTrue("x".repeat(100_000).byteInputStream().use { NoteTextNavigator.headings(it) }.isEmpty())
    }
}

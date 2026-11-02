package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class NoteTextReplacementTest {
    @Test fun streamedReplacementMatchesStandardEncodingWithoutMergingStrings() {
        val source = "# Original 😀\r\n" + "漢字 paragraph\n".repeat(1_000)
        val start = source.indexOf("paragraph")
        val end = start + "paragraph".length
        val replacement = "[Link](https://example.org/)\n\n".repeat(5_000)
        val expected = source.replaceRange(start, end, replacement).toByteArray(Charsets.UTF_8)
        val plan = NoteTextReplacement(source, start, end, replacement)
        assertEquals(expected.size.toLong(), plan.byteLength)
        assertArrayEquals(expected, plan.openStream().use { it.readBytes() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToSplitAnExistingSurrogatePair() { NoteTextReplacement("a😀b", 2, 2, "x") }

    @Test fun unicodeByteCountingRemainsCancellable() {
        var checks = 0
        NoteEditorWindows.utf8Bytes("a" + "😀".repeat(20_000)) { checks++ }
        assertTrue(checks >= 9)
    }
}

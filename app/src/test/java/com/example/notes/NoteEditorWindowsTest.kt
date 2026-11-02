package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class NoteEditorWindowsTest {
    @Test fun windowsPreserveAllCharactersAndRespectBounds() {
        val fixtures = listOf(
            "", "one line", "😀漢字\r\n".repeat(500), "\n".repeat(50_000), "x".repeat(100_000),
            (1..5_000).joinToString("\n\n") { "[Link $it](https://example.org/$it)" }
        )
        for (text in fixtures) {
            val rebuilt = StringBuilder()
            var start = 0
            while (start < text.length) {
                val window = NoteEditorWindows.at(text, start)
                assertEquals(start, window.start)
                assertTrue(window.end > start)
                assertTrue(window.text.length <= NoteEditorWindows.MAX_CHARACTERS)
                assertTrue(window.text.count { it == '\n' } <= NoteEditorWindows.MAX_LINES)
                if (window.end < text.length) assertFalse(Character.isHighSurrogate(window.text.last()))
                rebuilt.append(window.text)
                start = window.end
            }
            assertEquals(text, rebuilt.toString())
        }
    }

    @Test fun utf8CounterAndStreamingEncoderMatchStandardEncoding() {
        val fixtures = listOf("", "one line", "a😀漢字\u0000\r\n".repeat(400), "a\uD800b")
        for (text in fixtures) {
            val expected = text.toByteArray(Charsets.UTF_8)
            assertEquals(expected.size.toLong(), NoteEditorWindows.utf8Bytes(text))
            assertArrayEquals(expected, TextUtf8InputStream(text).use { it.readBytes() })
        }
    }
}

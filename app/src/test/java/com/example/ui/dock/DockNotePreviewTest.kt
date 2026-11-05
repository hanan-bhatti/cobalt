package com.example.ui.dock

import org.junit.Assert.*
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class DockNotePreviewTest {
    @Test fun onlyReadsABoundedPrefixOfAHugeSource() {
        var read = 0
        val source = object : Reader() {
            override fun read(chars: CharArray, off: Int, len: Int): Int {
                chars.fill('a', off, off + len); read += len; return len
            }
            override fun close() = Unit
        }
        assertEquals(DOCK_NOTE_PREVIEW_CHARACTERS, readDockPreview(source).length)
        assertEquals(DOCK_NOTE_PREVIEW_CHARACTERS, read)
    }
    @Test fun doesNotCutAnEmojiInHalf() {
        val text = "a".repeat(DOCK_NOTE_PREVIEW_CHARACTERS - 1) + "😀"
        assertEquals("a".repeat(DOCK_NOTE_PREVIEW_CHARACTERS - 1), readDockPreview(StringReader(text)))
    }
    @Test fun showsMoreThanTwoLinesButBoundsPathologicalNewlineNotes() {
        val text = "first\r\nsecond\nthird\rfourth\n"
        assertEquals(text, readDockPreview(StringReader(text)))
        assertEquals("x\n".repeat(DOCK_NOTE_PREVIEW_LINES), readDockPreview(StringReader("x\n".repeat(1000))))
    }
}

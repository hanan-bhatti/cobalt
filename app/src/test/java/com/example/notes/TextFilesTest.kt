package com.example.notes

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class TextFilesTest {
    @Test fun textExtensionsAndMimeTypesAreRecognized() {
        for (extension in listOf("md", "MDX", "markdown", "txt", "csv", "json", "yaml", "kt", "srt", "diff")) {
            assertTrue(extension, TextFiles.accepts("document.$extension", "application/octet-stream"))
        }
        assertTrue(TextFiles.accepts(null, "text/plain; charset=utf-8"))
        assertTrue(TextFiles.accepts("README", "text/plain"))
        assertTrue(TextFiles.accepts(null, "application/json"))
        assertFalse(TextFiles.accepts(null, "application/octet-stream"))
        assertFalse(TextFiles.accepts("photo.png", "text/plain"))
        assertFalse(TextFiles.accepts("document.pdf", "application/pdf"))
        assertFalse(TextFiles.accepts("archive.zip", "text/markdown"))
    }

    @Test fun utf8AndBomLabelledUtf16RoundTripAcrossEmojiBoundaries() {
        val text = "a".repeat(2047) + "😀漢字\r\n".repeat(2000)
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val bom = when (charset) {
                Charsets.UTF_16LE -> byteArrayOf(0xff.toByte(), 0xfe.toByte())
                Charsets.UTF_16BE -> byteArrayOf(0xfe.toByte(), 0xff.toByte())
                else -> byteArrayOf()
            }
            TextFiles.openUtf8(ByteArrayInputStream(bom + text.toByteArray(charset))).use {
                assertEquals(text, it.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    @Test fun emptyAndUtf8BomDocumentsArePreserved() {
        for (text in listOf("", "\uFEFF# heading\n", "<Component execute={false} />\nexport const value = 42")) {
            TextFiles.openUtf8(ByteArrayInputStream(text.toByteArray())).use {
                assertEquals(text, it.readBytes().toString(Charsets.UTF_8))
            }
        }
        assertEquals("draft.notes", TextFiles.title("draft.notes.mdx"))
    }

    @Test fun malformedInputFailsInsteadOfReplacingUserCharacters() {
        val fixtures = listOf(byteArrayOf(0xc3.toByte(), 0x28),
            byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x00, 0xd8.toByte()))
        for (bytes in fixtures) {
            try {
                TextFiles.openUtf8(ByteArrayInputStream(bytes)).use { it.readBytes() }
                fail("Malformed text must not be saved as corrupted text")
            } catch (_: IOException) { }
        }
    }
}

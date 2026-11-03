package com.example.notes

import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ContinuousNoteDocumentTest {
    @Test fun utf8StreamingPreservesEmptyUnicodeAndMixedLineEndings() {
        for (text in listOf("", "one line", "a\r\nb\rc\n", "😀漢字\u0000\n".repeat(3000), "x".repeat(2047) + "😀" + "z".repeat(8000))) {
            val content = ContentIO.createFrom(text.byteInputStream(Charsets.UTF_8), Charsets.UTF_8)
            val snapshot = content.copyTextShallow()
            try {
                val actual = ContinuousNoteDocument.stream(snapshot).use { it.readBytes() }
                assertArrayEquals(text.toByteArray(Charsets.UTF_8), actual)
            } finally { snapshot.release(); content.release() }
        }
    }
    @Test fun checkpointSnapshotStaysStableDuringFurtherEdits() {
        val document = ContinuousNoteDocument(Content("old😀\r\nsecond line"))
        val snapshot = document.snapshot()
        try {
            document.content.replace(0, 3, "new")
            document.changed()
            assertEquals("old😀\r\nsecond line", ContinuousNoteDocument.stream(snapshot).bufferedReader().use { it.readText() })
            assertEquals("new😀\r\nsecond line", document.content.toString())
            assertEquals(1L, document.revision)
        } finally { snapshot.release(); document.content.release() }
    }
    @Test fun oneUndoRestoresAnEditAcrossFormerBlockBoundaries() {
        val original = "a".repeat(500) + "\n" + "b".repeat(700) + "\n😀"
        val document = ContinuousNoteDocument(Content(original))
        document.content.beginBatchEdit()
        document.content.replace(490, 530, "cross-boundary text\n")
        document.content.endBatchEdit()
        val changed = document.content.toString()
        assertNotEquals(original, changed)
        document.content.undo()
        assertEquals(original, document.content.toString())
        document.content.redo()
        assertEquals(changed, document.content.toString())
        document.content.release()
    }
}

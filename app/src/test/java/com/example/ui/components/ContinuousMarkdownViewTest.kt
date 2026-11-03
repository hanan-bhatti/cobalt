package com.example.ui.components

import android.app.Activity
import android.view.inputmethod.EditorInfo
import com.example.notes.ContinuousNoteDocument
import io.github.rosemoe.sora.text.Content
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ContinuousMarkdownViewTest {
    @Test fun selectionAndImeEditingCrossFormerBlockBoundaries() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val editor = ContinuousMarkdownView(activity.get())
        editor.setWordwrap(false) // Deterministic cursor/IME checks; wrapped viewport needs device tests.
        activity.get().setContentView(editor)
        editor.layout(0, 0, 1080, 1500)
        val original = "a".repeat(500) + "\n" + "b".repeat(600)
        val document = ContinuousNoteDocument(Content(original), original.toByteArray(Charsets.UTF_8).size.toLong())
        try {
            editor.bind(document) { document.changed() }
            editor.select(490, 530)
            assertEquals(490, editor.cursor.left)
            assertEquals(530, editor.cursor.right)
            val ime = editor.onCreateInputConnection(EditorInfo())!!
            assertTrue(ime.commitText("composed😀", 1))
            assertEquals(original.replaceRange(490, 530, "composed😀"), editor.text.toString())
            editor.undo()
            assertEquals(original, editor.text.toString())
        } finally { editor.dispose(); document.content.release(); activity.pause().stop().destroy() }
    }
    @Test fun frozenDocumentsRejectImeEdits() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val editor = ContinuousMarkdownView(activity.get())
        editor.setWordwrap(false) // Deterministic cursor/IME checks; wrapped viewport needs device tests.
        activity.get().setContentView(editor)
        val document = ContinuousNoteDocument(Content("retained"))
        try {
            editor.bind(document) { document.changed() }
            val ime = editor.onCreateInputConnection(EditorInfo())!!
            document.freeze(true)
            assertFalse(ime.commitText("replacement", 1))
            assertEquals("retained", editor.text.toString())
        } finally { editor.dispose(); document.content.release(); activity.pause().stop().destroy() }
    }
}

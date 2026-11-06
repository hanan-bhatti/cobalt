package com.example.ui.components

import android.app.Activity
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import java.time.Duration
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
            document.selectionStart = 490
            document.selectionEnd = 530
            editor.bind(document) { document.changed() }
            assertEquals(490, editor.cursor.left)
            assertEquals(530, editor.cursor.right)
            awaitReady(editor)
            val ime = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
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
            awaitReady(editor)
            val ime = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
            document.freeze(true)
            assertFalse(ime.commitText("replacement", 1))
            assertEquals("retained", editor.text.toString())
        } finally { editor.dispose(); document.content.release(); activity.pause().stop().destroy() }
    }
    @Test fun tableEditIsOneUndoStepInResidentEditor() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val editor = ContinuousMarkdownView(activity.get())
        editor.setWordwrap(false)
        activity.get().setContentView(editor)
        val original = "| A | B |\n| --- | --- |\n| x | y |"
        val document = ContinuousNoteDocument(Content(original), original.toByteArray().size.toLong())
        try {
            document.selectionStart = original.indexOf('x'); document.selectionEnd = document.selectionStart
            editor.bind(document) { document.changed() }
            awaitReady(editor)
            document.onFormat!!.invoke("table:row")
            val looper = shadowOf(Looper.getMainLooper())
            repeat(300) {
                looper.idleFor(Duration.ofMillis(16))
                if (document.pendingEdit?.isActive == true) Thread.sleep(5)
            }
            assertTrue(editor.text.toString().endsWith("\n|  |  |"))
            document.onFormat!!.invoke("undo")
            assertEquals(original, editor.text.toString())
            document.onFormat!!.invoke("redo")
            assertTrue(editor.text.toString().endsWith("\n|  |  |"))
        } finally { editor.dispose(); document.content.release(); activity.pause().stop().destroy() }
    }
    private fun awaitReady(editor: ContinuousMarkdownView) {
        editor.layout(0, 0, 1080, 1500)
        editor.requestFocus()
        val looper = shadowOf(Looper.getMainLooper())
        repeat(300) {
            looper.idleFor(Duration.ofMillis(16))
            if (editor.isEditable) return
            Thread.sleep(5) // Let Sora's background layout publish its result to the paused main looper.
        }
        assertTrue("Native editor layout did not become ready", editor.isEditable)
    }

}

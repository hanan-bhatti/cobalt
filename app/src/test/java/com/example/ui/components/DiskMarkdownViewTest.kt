package com.example.ui.components

import android.app.Activity
import android.os.Looper
import android.view.inputmethod.EditorInfo
import com.example.notes.*
import io.github.rosemoe.sora.text.Content
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DiskMarkdownViewTest {
    private class Fixture(val body: String) : AutoCloseable {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val directory = Files.createTempDirectory("cobalt-viewport-test-").toFile()
        val source = object : NoteByteSource {
            val bytes = body.toByteArray()
            override val size = bytes.size.toLong()
            override fun read(offset: Long, count: Int) = bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
        }
        val ref = NoteBodyRef("a".repeat(64), source.size, "")
        val released = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val model = DiskTextModel.open(source, ref, File(directory, "append"), File(directory, "state"), HostTestNoteFileOperations,
            release = { released.set(true) })
        val session = DiskEditorSession(scope, model)
        val document = ContinuousNoteDocument(Content(""), source.size, session)
        val view = DiskMarkdownView(activity.get())
        init {
            session.onChanged = { document.changed() }
            view.setWordwrap(false)
            activity.get().setContentView(view)
            view.layout(0, 0, 1080, 1500)
        }
        fun open(position: Int = 0) {
            document.selectionStart = position; document.selectionEnd = position
            document.viewportStart = position; view.bind(document)
            pump { view.isEditable && view.text.length > 0 }
            view.requestFocus()
        }
        fun text(): String = await {
            withContext(Dispatchers.IO) { session.openUtf8().bufferedReader().use { it.readText() } }
        }
        fun <T> await(action: suspend () -> T): T {
            var result: Result<T>? = null
            scope.launch { result = runCatching { action() } }
            pump { result != null }
            return result!!.getOrThrow()
        }
        override fun close() {
            view.dispose(); document.closeSession()
            pump { released.get() }
            scope.cancel(); directory.deleteRecursively(); activity.pause().stop().destroy()
        }
    }
    @Test fun tableEditUsesGlobalDiskCoordinatesAndUndoRestoresTable() {
        val original = "Intro\n\n| A | B |\n| --- | --- |\n| x | y |\n\nEnd"
        Fixture(original).use { f ->
            f.open(original.indexOf('x'))
            f.document.onFormat!!.invoke("table:column")
            pump { f.view.isEditable && f.document.pendingEdit?.isActive != true }
            assertTrue(f.text().contains("| x |  | y |"))
            f.view.undo()
            pump { f.view.isEditable && f.document.pendingEdit?.isActive != true }
            assertEquals(original, f.text())
            f.view.redo()
            pump { f.view.isEditable && f.document.pendingEdit?.isActive != true }
            assertTrue(f.text().contains("| x |  | y |"))
        }
    }
    @Test fun imeEditingAtAGlobalPositionUsesTheDiskDocumentAndUndoRestoresIt() {
        val body = (1..10_000).joinToString("\n") { "Line $it text" }
        Fixture(body).use { f ->
            val position = 70_000
            f.open(position)
            assertTrue(f.view.text.length <= DiskTextModel.MAX_VIEW_CHARACTERS)
            val ime = requireNotNull(f.view.onCreateInputConnection(EditorInfo()))
            assertTrue(ime.commitText("typed😀", 1))
            assertEquals(body.replaceRange(position, position, "typed😀"), f.text())
            f.view.undo()
            pump { f.document.pendingEdit?.isCompleted == true && f.view.isEditable }
            assertEquals(body, f.text())
        }
    }
    @Test fun globalSelectAllReplacementDoesNotLeaveHiddenTextOutsideTheViewport() {
        val body = (1..8_000).joinToString("\n") { "Paragraph $it" }
        Fixture(body).use { f ->
            f.open(50_000)
            f.view.selectAll()
            val ime = requireNotNull(f.view.onCreateInputConnection(EditorInfo()))
            assertTrue(ime.commitText("Replacement😀", 1))
            pump { f.document.pendingEdit?.isCompleted == true && f.view.isEditable }
            assertEquals("Replacement😀", f.text())
            f.view.undo()
            pump { f.document.pendingEdit?.isCompleted == true && f.view.isEditable }
            assertEquals(body, f.text())
        }
    }
    @Test fun rewindowingRejectsAnOldImeConnectionAndKeepsGlobalComposition() {
        val body = (1..9_000).joinToString("\n") { "Line $it" }
        Fixture(body).use { f ->
            f.open()
            val stale = requireNotNull(f.view.onCreateInputConnection(EditorInfo()))
            f.view.jumpToCharacter(60_000)
            pump { f.document.pendingEdit?.isCompleted == true && f.view.isEditable }
            assertFalse(stale.commitText("old window", 1))
            val ime = requireNotNull(f.view.onCreateInputConnection(EditorInfo()))
            assertTrue(ime.setComposingText("नम", 1))
            assertTrue(ime.commitText("नमस्ते😀", 1))
            assertEquals(body.replaceRange(60_000, 60_000, "नमस्ते😀"), f.text())
        }
    }
    @Test fun closingImmediatelyAfterTypingFlushesTheNativeBatchAndStaleImeIsRejected() {
        val body = "Original\n".repeat(6_000)
        val f = Fixture(body)
        try {
            f.open()
            val ime = requireNotNull(f.view.onCreateInputConnection(EditorInfo()))
            assertTrue(ime.commitText("last edit", 1))
            f.view.dispose(); f.document.closeSession()
            assertFalse(ime.commitText("stale", 1))
            pump { f.released.get() }
            val restored = DiskTextModel.open(f.source, f.ref, File(f.directory, "append"), File(f.directory, "state"), HostTestNoteFileOperations)
            try { assertEquals("last edit$body", restored.openUtf8().bufferedReader().use { it.readText() }) }
            finally { restored.close() }
        } finally { f.scope.cancel(); f.directory.deleteRecursively(); f.activity.pause().stop().destroy() }
    }
    @Test fun deletionAtViewportStartUsesGlobalCodePointsAndUndoRestoresEmoji() {
        val body = "😀line\n".repeat(10_000)
        Fixture(body).use { f ->
            f.open(50_000)
            val ime = requireNotNull(f.view.onCreateInputConnection(EditorInfo()))
            assertTrue(ime.setSelection(0, 0))
            val position = f.document.selectionStart
            assertTrue(position > 0)
            assertFalse(ime.setSelection(-1, 0))
            assertFalse(ime.deleteSurroundingText(-1, 0))
            assertTrue(ime.deleteSurroundingTextInCodePoints(1, 0))
            pump { f.document.pendingEdit?.isCompleted == true && f.view.isEditable }
            val previous = body.offsetByCodePoints(position, -1)
            assertEquals(body.removeRange(previous, position), f.text())
            f.view.undo()
            pump { f.document.pendingEdit?.isCompleted == true && f.view.isEditable }
            assertEquals(body, f.text())
        }
    }
    @Test fun closingWithMoreAcceptedEditsThanQueueCapacityDrainsEveryEdit() {
        Fixture("original").use { f ->
            repeat(100) {
                assertTrue(f.session.record(listOf(DiskTextModel.Replacement(0, 0, "x")),
                    DiskTextModel.Position(0, 0), DiskTextModel.Position(1, 1), final = true))
            }
            f.document.closeSession()
            pump { f.released.get() }
            val restored = DiskTextModel.open(f.source, f.ref, File(f.directory, "append"), File(f.directory, "state"), HostTestNoteFileOperations)
            try { assertEquals("x".repeat(100) + "original", restored.text(0, 200)) }
            finally { restored.close() }
        }
    }
    @Test fun hardwareArrowCrossesTheViewportWithoutChangingText() {
        val body = "😀line\n".repeat(10_000)
        Fixture(body).use { f ->
            f.open(50_000)
            val ime = requireNotNull(f.view.onCreateInputConnection(EditorInfo()))
            assertTrue(ime.setSelection(0, 0))
            val position = f.document.selectionStart
            assertTrue(position > 0)
            assertTrue(f.view.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT)))
            pump { f.document.pendingEdit?.isCompleted == true && f.view.isEditable }
            assertEquals(body.offsetByCodePoints(position, -1), f.document.selectionStart)
            assertEquals(body, f.text())
        }
    }
    companion object {
        private fun pump(ready: () -> Boolean) {
            repeat(1_200) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                if (ready()) return
                Thread.sleep(5)
            }
            assertTrue("Viewport/background operation did not complete", ready())
        }
    }
}

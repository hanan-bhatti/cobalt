package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CobaltSelectionMenusTest {
    @get:Rule val compose = createComposeRule()
    @Test fun readonlyLegacyPathOnlyOffersSupportedActions() {
        var copies = 0
        compose.setContent { MaterialTheme { CobaltSelectionMenuHost {
            val toolbar = LocalTextToolbar.current
            Button(onClick = { toolbar.showMenu(Rect(0f, 150f, 200f, 190f),
                onCopyRequested = { copies++ }, onPasteRequested = null,
                onCutRequested = null, onSelectAllRequested = {}) }) { Text("Select") }
        } } }
        compose.onNodeWithText("Select").performClick()
        compose.onNodeWithContentDescription("Cut").assertDoesNotExist()
        compose.onNodeWithContentDescription("Paste").assertDoesNotExist()
        compose.onNodeWithContentDescription("Bold").assertDoesNotExist()
        compose.onNodeWithContentDescription("Select all").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy").performClick()
        compose.runOnIdle { assertEquals(1, copies) }
        compose.onNodeWithContentDescription("Copy").assertDoesNotExist()
    }
    @Test fun editorFormatsReversedSelectionAndDismisses() {
        var value = TextFieldValue("hello world", TextRange(11, 6))
        compose.setContent { MaterialTheme { CobaltSelectionMenuHost {
            CobaltSelectionFormattingScope(value, { value = it }) {
                val toolbar = LocalTextToolbar.current
                Button(onClick = { toolbar.showMenu(Rect(0f,150f,200f,190f),
                    {}, {}, {}, {}) }) { Text("Select") }
            }
        } } }
        compose.onNodeWithText("Select").performClick()
        compose.onNodeWithContentDescription("Bold").performClick()
        compose.runOnIdle { assertEquals("hello **world**", value.text) }
        compose.onNodeWithContentDescription("Bold").assertDoesNotExist()
    }
    @Test fun replacingAndHidingOwnersDoesNotDismissNewSelection() {
        val controller = CobaltSelectionController()
        val old = Any(); val next = Any()
        var dismissed = 0
        controller.show(old, { Rect.Zero }, { emptyList() }) { dismissed++ }
        controller.show(next, { Rect.Zero }, { emptyList() })
        controller.hide(old)
        assertTrue(controller.owns(next))
        assertEquals(1, dismissed)
        controller.dismiss()
        assertFalse(controller.owns(next))
    }
    @Test fun holdingAnIconShowsLabelWithoutRunningAction() {
        var calls = 0
        compose.setContent { MaterialTheme {
            SelectionMenuContent(listOf(SelectionAction("Copy", com.example.R.drawable.ic_custom_copy) { calls++ }))
        } }
        compose.onNodeWithContentDescription("Copy").performTouchInput { longClick() }
        compose.onNodeWithText("Copy").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun moreCreatesATableThroughTheDimensionDialog() {
        var value by mutableStateOf(TextFieldValue("before\n", TextRange(7)))
        compose.setContent { MaterialTheme { CobaltSelectionMenuHost {
            CobaltSelectionFormattingScope(value, { value = it }) {
                val toolbar = LocalTextToolbar.current
                Button(onClick = { toolbar.showMenu(Rect(0f,150f,200f,190f), {}, {}, {}, {}) }) { Text("Select") }
            }
        } } }
        compose.onNodeWithText("Select").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Insert table").performScrollTo().performClick()
        compose.onNodeWithText("Rows").assertIsDisplayed()
        compose.onNodeWithText("Columns").assertIsDisplayed()
        compose.onNodeWithText("Insert").performClick()
        compose.waitUntil(10_000) { value.text.contains("Column 1") }
        compose.runOnIdle {
            assertTrue(value.text.startsWith("before\n| Column 1 | Column 2 | Column 3 |"))
            assertEquals(5, value.text.lines().count { it.startsWith('|') })
        }
    }
    @Test fun nativeEditorUsesSameMenuAndActualCutOperation() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val editor = io.github.rosemoe.sora.widget.CodeEditor(activity.get())
        val controller = CobaltSelectionController()
        var formatted: String? = null
        try {
            activity.get().setContentView(editor)
            editor.setWordwrap(false)
            editor.setText("hello world")
            editor.layout(0, 0, 1080, 1500)
            editor.requestFocus()
            val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            repeat(300) {
                if (!editor.isEditable) {
                    looper.idleFor(java.time.Duration.ofMillis(16))
                    Thread.sleep(5)
                }
            }
            assertTrue("Wait for native background layout", editor.isEditable)
            editor.setSelectionRegion(0, 0, 0, 5)
            editor.useCobaltSelectionMenu(controller) { formatted = it }
            val window = editor.getComponent(io.github.rosemoe.sora.widget.component.EditorTextActionWindow::class.java)
            window.displayWindow()
            assertTrue(window.isShowing)
            requireNotNull(controller.menu).actions().first { it.label == "Bold" }.run()
            assertEquals("Bold", formatted)
            assertFalse(window.isShowing)
            window.displayWindow()
            requireNotNull(controller.menu).actions().first { it.label == "Cut" }.run()
            assertEquals(" world", editor.text.toString())
            assertFalse(window.isShowing)
        } finally {
            editor.release()
            activity.pause().stop().destroy()
        }
    }

}

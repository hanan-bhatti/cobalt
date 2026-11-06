package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.ui.viewmodel.CobaltViewModel.MarkdownImportState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TextDocumentImportSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun acceptedFiftyFiveMegabyteNoteStaysAsSuccessUntilTheUserChoosesOpen() {
        var opened = 0
        var dismissed = 0
        val state = MarkdownImportState(bytes = 55_000_000, canSplit = true,
            importedItem = SavedItem(type = SavedItemType.TEXT, title = "Boundary", content = ""),
            message = "Your note is saved.", documentsSaved = 1)
        compose.setContent { MaterialTheme {
            TextDocumentImportSheet(state, { dismissed++ }, {}, { opened++ }, {}, {})
        } }
        compose.onNodeWithText("Import complete").assertIsDisplayed()
        compose.onNodeWithText("Document too large").assertDoesNotExist()
        compose.onNodeWithText("55 MB").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertEquals(0, opened)
        assertEquals(0, dismissed)
        compose.onNodeWithText("Open note").performClick()
        assertEquals(1, opened)
    }

    @Test fun oversizedErrorRemainsReadableAndSplittingNeedsConfirmation() {
        var splits = 0
        var dismissed = 0
        val state = MarkdownImportState(bytes = 55_010_000, failed = true, canSplit = true,
            message = "This document is larger than the 55 MB limit.")
        compose.setContent { MaterialTheme {
            TextDocumentImportSheet(state, { dismissed++ }, {}, {}, {}, { splits++ })
        } }
        compose.onNodeWithText("Document too large").assertIsDisplayed()
        compose.onNodeWithTag("import_result_message").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertEquals(0, dismissed)
        compose.onNodeWithText("Split into notes").performClick()
        assertEquals(0, splits)
        compose.onNodeWithText("Split now").performClick()
        assertEquals(1, splits)
    }

    @Test fun runningImportShowsProgressAndAnExplicitCancelAction() {
        var cancelled = 0
        compose.setContent { MaterialTheme {
            TextDocumentImportSheet(MarkdownImportState(running = true, bytes = 100_000), {}, { cancelled++ }, {}, {}, {})
        } }
        compose.onNodeWithTag("import_progress").assertIsDisplayed()
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithText("Cancel import").performClick()
        assertEquals(1, cancelled)
    }
}

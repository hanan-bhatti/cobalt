package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise real Popup window focus; Robolectric cannot dispose this native Surface reliably. */
@RunWith(AndroidJUnit4::class)
class CobaltSelectionInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun moreKeepsRealTextFieldSelectionAlive() {
        var value by mutableStateOf(TextFieldValue("alpha beta gamma"))
        compose.setContent { MaterialTheme { CobaltSelectionMenuHost {
            CobaltSelectionFormattingScope(value, { value = it }) {
                androidx.compose.foundation.text.BasicTextField(value, { value = it },
                    modifier = Modifier.size(280.dp, 120.dp))
            }
        } } }
        compose.onNodeWithText("alpha beta gamma").performTouchInput { longClick(androidx.compose.ui.geometry.Offset(30f, 10f)) }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Strikethrough").assertIsDisplayed()
        compose.runOnIdle { assertFalse(value.selection.collapsed) }
        compose.onNodeWithText("Strikethrough").performClick()
        compose.waitForIdle()
    }
    @Test fun sourceTypingAndRenderedPreviewRemainSeparate() {
        var value by mutableStateOf(TextFieldValue("- item", androidx.compose.ui.text.TextRange(6)))
        var preview by mutableStateOf(false)
        compose.setContent { MaterialTheme { CobaltSelectionMenuHost {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Button(onClick = { preview = !preview }) {
                    androidx.compose.material3.Text(if (preview) "Edit source" else "Preview note")
                }
                if (preview) MarkdownText(value.text)
                else androidx.compose.foundation.text.BasicTextField(value, { incoming ->
                    value = com.example.notes.MarkdownListEditing.adapt(value, incoming)
                }, visualTransformation = com.example.ui.screens.MarkdownVisualTransformation(),
                    modifier = Modifier.testTag("literal-source"))
            }
        } } }
        compose.onNodeWithTag("literal-source").performTextInput("\n")
        compose.runOnIdle { assertEquals("- item\n- ", value.text) }
        compose.onNodeWithTag("literal-source").performTextInput("**bold** and `code`")
        compose.onNodeWithTag("literal-source").assertTextContains("**bold** and `code`", substring = true)
        compose.onNodeWithText("Preview note").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("bold and code").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("bold and code").assertIsDisplayed()
    }

}

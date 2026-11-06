package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
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
}

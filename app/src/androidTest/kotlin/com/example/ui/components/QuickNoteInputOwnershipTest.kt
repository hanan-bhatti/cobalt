package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuickNoteInputOwnershipTest {
    @get:Rule val compose = createComposeRule()
    @Test fun delayedPersistenceEchoCannotReplaceTextOrMoveSelection() {
        val echo = mutableStateOf(""); var received = ""
        compose.setContent {
            MaterialTheme {
                QuickNoteBottomSheet(true, {}, "Fixture", {}, echo.value, { received = it }, false, {}, false, HazeState())
            }
        }
        val body = compose.onNodeWithTag("quick_note_body")
        body.performClick(); body.performTextInput("abc 🙂 اردو")
        assertEquals("abc 🙂 اردو", received)
        compose.runOnIdle { echo.value = "a" }
        body.assertTextEquals("abc 🙂 اردو")
        body.performTextInputSelection(TextRange(1, 1))
        compose.runOnIdle { echo.value = "abc" }
        body.performTextInput("X")
        body.assertTextEquals("aXbc 🙂 اردو")
        assertEquals("aXbc 🙂 اردو", received)
        body.performTextInputSelection(TextRange(received.length))
        repeat(125) { body.performTextInput(('a'.code + it % 26).toChar().toString()) }
        assertEquals("aXbc 🙂 اردو" + (0 until 125).map { ('a'.code + it % 26).toChar() }.joinToString(""), received)
        compose.runOnIdle { echo.value = "outdated durability completion" }
        body.assertTextEquals(received)
    }
}

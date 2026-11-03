package com.example.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.components.MarkdownText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MarkdownCodeTypographyTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fencedCodeIsMonospaceAndPreservesBlankLinesSpacesAndTabs() {
        val code = "Wi  value\n\n\tindented\n"
        compose.setContent { MaterialTheme { MarkdownText("```text\n$code```\n") } }
        val node = compose.onNodeWithTag("markdown_code", useUnmergedTree = true)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("markdown_code", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        node.assertTextEquals(code.removeSuffix("\n"))
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(layout.getBoundingBox(0).width, layout.getBoundingBox(1).width, 0.1f)
        assertEquals(3, layout.lineCount)
    }
}

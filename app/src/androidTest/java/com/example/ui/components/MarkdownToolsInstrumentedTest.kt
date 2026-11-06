package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the real shared controls/callbacks, then the real Markdown renderer. */
@RunWith(AndroidJUnit4::class)
class MarkdownToolsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var value by mutableStateOf(TextFieldValue(""))
    private var preview by mutableStateOf(false)
    private fun screen() {
        compose.setContent { MaterialTheme { CobaltSelectionMenuHost {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Button(onClick = { preview = !preview }) { Text(if (preview) "Edit source" else "Preview note") }
                if (preview) MarkdownText(value.text)
                else {
                    CobaltTableToolbarActions(value, { value = it })
                    CobaltSelectionFormattingScope(value, { value = it }) {
                        val toolbar = LocalTextToolbar.current
                        Button(onClick = { toolbar.showMenu(Rect(20f, 200f, 240f, 260f), {}, {}, {}, {}) }) { Text("Tools") }
                        BasicTextField(value, { value = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("source"))
                    }
                }
            }
        } } }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "editor-tools-validation").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
    private fun more(label: String) {
        compose.onNodeWithText("Tools").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText(label).performScrollTo().performClick()
    }
    private fun previewTable(name: String) {
        compose.onNodeWithText("Preview note").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Column 1").fetchSemanticsNodes().isNotEmpty() }
        capture(name)
        compose.onNodeWithText("Edit source").performClick()
    }
    private fun expect(predicate: (String) -> Boolean) { compose.waitUntil(10_000) { predicate(value.text) } }
    private fun at(word: String) { compose.runOnIdle { value = value.copy(selection = TextRange(value.text.indexOf(word).also { assertTrue(it >= 0) })) } }

    @Test fun everyFormattingControlChangesSourceAndRendersInPreview() {
        screen()
        val formats = listOf(
            Triple("Bold", "**sample**", true), Triple("Italic", "_sample_", true),
            Triple("Strikethrough", "~~sample~~", false), Triple("Heading 1", "# sample", false),
            Triple("Heading 2", "## sample", false), Triple("Heading 3", "### sample", false),
            Triple("Bulleted list", "- sample", false), Triple("Numbered list", "1. sample", false),
            Triple("Checklist", "- [ ] sample", false), Triple("Quote", "> sample", false),
            Triple("Inline code", "`sample`", false), Triple("Code block", "```\nsample\n```", false),
            Triple("Link", "[sample](https://example.com)", false))
        for ((label, expected, primary) in formats) {
            compose.runOnIdle { value = TextFieldValue("sample", TextRange(0, 6)); preview = false }
            if (primary) {
                compose.onNodeWithText("Tools").performClick()
                compose.onNodeWithContentDescription(label).performClick()
            } else more(label)
            compose.runOnIdle { assertEquals(label, expected, value.text) }
            compose.onNodeWithText("Preview note").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("sample").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("sample").assertIsDisplayed()
            capture("format-" + label.lowercase().replace(' ', '-'))
            compose.onNodeWithText("Edit source").performClick()
        }
    }

    @Test fun completeTableCreationEditingAndPreviewWorkflow() {
        screen()
        compose.onNodeWithContentDescription("Insert table").performClick()
        compose.onNodeWithText("Rows").assertIsDisplayed(); compose.onNodeWithText("Columns").assertIsDisplayed()
        compose.onNodeWithText("Insert").performClick()
        expect { it.lines().count { line -> line.startsWith('|') } == 5 }
        val created = value.text
        at("Column 1")
        compose.onNodeWithContentDescription("Add column right").performClick()
        expect { it.contains("| Column 1 |  | Column 2 | Column 3 |") }
        previewTable("table-column-added")
        at("Column 1")
        compose.onNodeWithContentDescription("Add row below").performClick()
        expect { it.lines().count { line -> line.startsWith('|') } == 6 }
        previewTable("table-row-added")
        val glyphPositions = mutableListOf<Float>()
        for ((action, marker) in listOf("Align column left" to ":---", "Align column center" to ":---:", "Align column right" to "---:")) {
            at("Column 1"); more(action)
            expect { it.lines()[1].startsWith("| $marker |") }
            compose.onNodeWithText("Preview note").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Column 1").fetchSemanticsNodes().isNotEmpty() }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Column 1").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            glyphPositions += layouts.single().getBoundingBox(0).left
            capture("table-" + action.lowercase().replace(' ', '-'))
            compose.onNodeWithText("Edit source").performClick()
        }
        assertTrue("Center alignment must move the rendered glyph", glyphPositions[1] > glyphPositions[0] + 1f)
        assertTrue("Right alignment must move the rendered glyph further", glyphPositions[2] > glyphPositions[1] + 1f)
        at("Column 1"); more("Next cell")
        compose.runOnIdle { assertTrue(value.selection.start > value.text.indexOf("Column 1")) }
        more("Remove column")
        expect { it.lines()[0] == "| Column 1 | Column 2 | Column 3 |" }
        compose.runOnIdle {
            val row = value.text.lines().take(2).sumOf { it.length + 1 } + 2
            value = value.copy(selection = TextRange(row))
        }
        more("Remove row")
        expect { it.lines().count { line -> line.startsWith('|') } == 5 }
        compose.onNodeWithText("Preview note").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Column 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Column 1").assertIsDisplayed()
        compose.onNodeWithText("Column 2").assertIsDisplayed()
        compose.onNodeWithText("Column 3").assertIsDisplayed()
        capture("table-final-preview")
        compose.onNodeWithText("Edit source").performClick()
        // Cancel never edits the existing table.
        val beforeCancel = value.text
        compose.onNodeWithContentDescription("Insert table").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(beforeCancel, value.text); assertNotEquals("", created) }
    }
}

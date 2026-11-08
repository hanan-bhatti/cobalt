/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui.components.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class NoteShareCardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMarkdownNote = SavedItem(
        id = "note-test-1",
        type = SavedItemType.TEXT,
        title = "Architecture & Performance Plan",
        content = """
            # Executive Summary
            Offline-first architecture with strict zero-cloud privacy.

            ## Core Deliverables
            - **Local SQLite Engine**: Low latency, in-memory testability
            - **4K Share Cards**: Vector sharpness on high-density displays
            - **Compact Tables**: Automatic responsiveness across 3-4 columns

            > "Simplicity is the prerequisite for reliability."

            | Module | Status | Priority | Target |
            | :--- | :--- | :--- | :--- |
            | SQLite DB | Completed | High | v2.2.0 |
            | 4K Exporter | Active | Critical | v2.3.0 |
            | Table Grid | Verified | Medium | v2.3.0 |
        """.trimIndent(),
        timestamp = 1731000000000L
    )

    private val sampleShortNote = SavedItem(
        id = "note-test-2",
        type = SavedItemType.TEXT,
        title = "Night Thoughts",
        content = "The second brain isn't about collecting information—it is about freeing your mind to actually think.",
        timestamp = 1731000000000L
    )

    @Test
    fun note_share_document_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    NoteShareCard(
                        item = sampleMarkdownNote,
                        noteBody = sampleMarkdownNote.content,
                        style = NoteShareStyle.DOCUMENT,
                        showAttribution = true,
                        tagline = "Archived in Cobalt"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/note_share_editorial_light.png")
    }

    @Test
    fun note_share_document_dark_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    NoteShareCard(
                        item = sampleMarkdownNote,
                        noteBody = sampleMarkdownNote.content,
                        style = NoteShareStyle.DOCUMENT,
                        showAttribution = true,
                        tagline = "Saved with Cobalt (future me says thanks)"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/note_share_editorial_dark.png")
    }

    @Test
    fun note_share_minimal_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                    NoteShareCard(
                        item = sampleShortNote,
                        noteBody = sampleShortNote.content,
                        style = NoteShareStyle.MINIMAL,
                        showAttribution = true,
                        tagline = "Cobalt: A quiet corner for curious minds"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/note_share_minimal_quote_light.png")
    }

    @Test
    fun note_share_minimal_dark_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                    NoteShareCard(
                        item = sampleShortNote,
                        noteBody = sampleShortNote.content,
                        style = NoteShareStyle.MINIMAL,
                        showAttribution = true,
                        tagline = "Saved with Cobalt • Second brain satisfied"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/note_share_minimal_quote_dark.png")
    }

    @Test
    fun note_share_minimal_truncated_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                    NoteShareCard(
                        item = sampleMarkdownNote,
                        noteBody = sampleMarkdownNote.content,
                        style = NoteShareStyle.MINIMAL,
                        showAttribution = true,
                        tagline = "Archived in Cobalt"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/note_share_minimal_truncated_light.png")
    }

    @Test
    fun note_share_compact_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                    NoteShareCard(
                        item = sampleShortNote,
                        noteBody = sampleShortNote.content,
                        style = NoteShareStyle.COMPACT,
                        showAttribution = true,
                        tagline = "Archived in Cobalt"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/note_share_compact_light.png")
    }
}

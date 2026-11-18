package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class NotesAndFoldersSettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var suggestionsEnabled by mutableStateOf(true)
    private var archiveStyle by mutableStateOf("heatmap")

    private fun showScreen(fontScale: Float = 1f) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    NotesAndFoldersSettingsContent(
                        suggestionsEnabled, archiveStyle,
                        onFolderSuggestionsChanged = { suggestionsEnabled = it },
                        onArchiveStyleChanged = { archiveStyle = it },
                        onNavigateBack = {}
                    )
                }
            }
        }
    }

    private fun reveal(label: String) {
        composeTestRule.onNodeWithTag("notes_folders_settings_list").performScrollToNode(hasText(label))
    }

    @Test
    fun disablingSuggestionsChangesTheExampleToAlphabeticalOrder() {
        showScreen()
        composeTestRule.onNodeWithTag("notes_folder_order_preview")
            .assertContentDescriptionEquals("Example: Work is suggested first, followed by Ideas and Reading.")
        reveal("Use folder suggestions")
        composeTestRule.onNodeWithText("Use folder suggestions").assertIsOn().performClick()
        composeTestRule.onNodeWithText("Use folder suggestions").assertIsOff()
        assertFalse(suggestionsEnabled)
        composeTestRule.onNodeWithTag("notes_folder_order_preview")
            .assertContentDescriptionEquals("Example folders sorted by name: Ideas, Reading, Work.")
    }

    @Test
    fun archiveChoiceUpdatesTheSettingAndDismissesTheNativeSheet() {
        showScreen()
        reveal("Archive view")
        composeTestRule.onNodeWithText("Archive view").performClick()
        composeTestRule.onNodeWithText("Choose how saved items are summarized in Settings.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Category totals").performClick()
        assertEquals("pills", archiveStyle)
        composeTestRule.onNodeWithText("Choose how saved items are summarized in Settings.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Category totals · Shown at the top of Settings.").assertIsDisplayed()
    }

    @Test
    fun helpExplainsPinnedFoldersWithoutChangingEitherPreference() {
        showScreen()
        reveal("How suggestions work")
        composeTestRule.onNodeWithText("How suggestions work").performClick()
        composeTestRule.onNodeWithText("Pinned folders stay first. With suggestions off, the remaining folders are sorted alphabetically.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close settings sheet").performClick()
        assertEquals(true, suggestionsEnabled)
        assertEquals("heatmap", archiveStyle)
    }

    @Test
    @Config(qualifiers = "w340dp-h760dp-port", sdk = [34])
    fun bothSettingsAndChoicesRemainReachableWithLargerText() {
        showScreen(fontScale = 1.3f)
        reveal("Use folder suggestions")
        composeTestRule.onNodeWithText("Use folder suggestions").assertIsDisplayed().performClick()
        assertFalse(suggestionsEnabled)
        reveal("Archive view")
        composeTestRule.onNodeWithText("Archive view").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Category totals").assertIsDisplayed().performClick()
        assertEquals("pills", archiveStyle)
    }
}

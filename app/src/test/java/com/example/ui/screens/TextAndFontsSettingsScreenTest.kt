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

package com.example.ui.screens

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.example.data.model.CodeFontFamily
import com.example.data.model.TextFontFamily
import com.example.data.model.TypographyPrefs
import com.example.data.model.TypographyPreset
import com.example.ui.theme.CobaltTypography
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class TextAndFontsSettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var currentPrefs by mutableStateOf(TypographyPrefs.DEFAULT)
    private var resetInvoked = false

    private fun showControls(prefs: TypographyPrefs = TypographyPrefs.DEFAULT) {
        currentPrefs = prefs
        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn(Modifier.testTag("font_controls")) {
                    textFontsSettingsItems(
                        localPrefs = currentPrefs,
                        onUpdatePrefs = { currentPrefs = it },
                        onResetDefaults = {
                            resetInvoked = true
                            currentPrefs = TypographyPrefs.DEFAULT
                        }
                    )
                }
            }
        }
    }

    private fun showControl(label: String) {
        composeTestRule.onNodeWithTag("font_controls").performScrollToNode(hasText(label))
    }

    @Test
    fun previewCard_rendersContentWithCurrentTypography() {
        val testPrefs = TypographyPrefs(
            textFont = TextFontFamily.INTER,
            fontSize = 16,
            preset = TypographyPreset.COMFORTABLE
        )
        val typography = CobaltTypography.fromPrefs(testPrefs)

        composeTestRule.setContent {
            MaterialTheme {
                TextFontsPreviewCard(typography = typography)
            }
        }

        composeTestRule.onNodeWithText("Reading preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inter · 16 sp").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review a saved idea").assertIsDisplayed()
        composeTestRule.onNodeWithText("val ideas = savedIdeas.filter { it.isFavorite }").assertIsDisplayed()
    }

    @Test
    fun previewShowsEffectiveSystemFontWhenCustomFontsAreOff() {
        val typography = CobaltTypography.fromPrefs(TypographyPrefs.DEFAULT.copy(useCustomFont = false, fontSize = 22))
        composeTestRule.setContent {
            MaterialTheme {
                TextFontsPreviewCard(typography)
            }
        }
        composeTestRule.onNodeWithText("System · 16 sp").assertIsDisplayed()
    }

    @Test
    fun switchingOffCustomFontsDisablesControlsAndPreservesChoices() {
        val original = TypographyPrefs.DEFAULT.copy(textFont = TextFontFamily.LITERATA, fontSize = 20).validated()
        showControls(original)
        composeTestRule.onNodeWithText("Use custom fonts").performClick().assertIsOff()
        composeTestRule.onNodeWithText("Use font for app text").assertIsNotEnabled()
        showControl("Text font")
        composeTestRule.onNodeWithText("Text font").assertIsNotEnabled()
        composeTestRule.runOnIdle {
            assertFalse(currentPrefs.useCustomFont)
            assertEquals(original.textFont, currentPrefs.textFont)
            assertEquals(original.fontSize, currentPrefs.fontSize)
        }
        showControl("Use custom fonts")
        composeTestRule.onNodeWithText("Use custom fonts").performClick().assertIsOn()
        composeTestRule.onNodeWithText("Use font for app text").performClick().assertIsOn()
        composeTestRule.runOnIdle { assertTrue(currentPrefs.applyToAppUi) }
    }

    @Test
    fun bottomSheetChoicesApplyReadingMetricsAndFontFamilies() {
        showControls()
        showControl("Reading preset")
        composeTestRule.onNodeWithText("Reading preset").performClick()
        composeTestRule.onNodeWithText("Reading").performClick()
        composeTestRule.runOnIdle {
            assertEquals(18, currentPrefs.fontSize)
            assertEquals(1.7f, currentPrefs.lineHeight)
            assertEquals(12, currentPrefs.paragraphSpacing)
            assertEquals(TypographyPreset.READING, currentPrefs.preset)
        }
        showControl("Text font")
        composeTestRule.onNodeWithText("Text font").performClick()
        composeTestRule.onNodeWithText("Lexend").performClick()
        composeTestRule.runOnIdle { assertEquals(TextFontFamily.LEXEND, currentPrefs.textFont) }
        showControl("Code font")
        composeTestRule.onNodeWithText("Code font").performClick()
        composeTestRule.onNodeWithText("System monospace").performClick()
        composeTestRule.runOnIdle { assertEquals(CodeFontFamily.SYSTEM_MONOSPACE, currentPrefs.codeFont) }
    }

    @Test
    fun slidersUpdateValidatedMetricsAndResetCallsTheAction() {
        showControls()
        val values = listOf(
            "Font size" to 22f,
            "Font weight" to 600f,
            "Line spacing" to 1.8f,
            "Letter spacing" to 0.04f,
            "Paragraph spacing" to 14f
        )
        values.forEach { (label, value) ->
            composeTestRule.onNodeWithTag("font_controls").performScrollToNode(hasContentDescription(label))
            composeTestRule.onNodeWithContentDescription(label).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        }
        composeTestRule.runOnIdle {
            assertEquals(22, currentPrefs.fontSize)
            assertEquals(600, currentPrefs.fontWeight)
            assertEquals(1.8f, currentPrefs.lineHeight)
            assertEquals(0.04f, currentPrefs.letterSpacing)
            assertEquals(14, currentPrefs.paragraphSpacing)
            assertEquals(TypographyPreset.CUSTOM, currentPrefs.preset)
        }
        showControl("Reset text settings")
        composeTestRule.onNodeWithText("Reset text settings").performClick()
        composeTestRule.runOnIdle {
            assertTrue(resetInvoked)
            assertEquals(TypographyPrefs.DEFAULT, currentPrefs)
        }
    }
}

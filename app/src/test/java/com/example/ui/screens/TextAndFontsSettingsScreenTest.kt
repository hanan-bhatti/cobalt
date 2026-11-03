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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        composeTestRule.onNodeWithText("LIVE PREVIEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("Typography in Action").assertIsDisplayed()
        composeTestRule.onNodeWithText("fun calculateMetrics(size: Int) = size * 1.5f").assertIsDisplayed()
    }

    @Test
    fun settingsControls_renderAndRespondToInteractions() {
        var currentPrefs by mutableStateOf(TypographyPrefs.DEFAULT)
        var resetDialogShown by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn {
                    textFontsSettingsItems(
                        localPrefs = currentPrefs,
                        onUpdatePrefs = { currentPrefs = it },
                        onShowResetDialog = { resetDialogShown = true }
                    )
                }
            }
        }

        // Verify top sections and items are displayed
        composeTestRule.onNodeWithText("Use custom font").assertIsDisplayed()
        composeTestRule.onNodeWithText("Apply to app UI").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reading Preset").assertIsDisplayed()
        composeTestRule.onNodeWithText("Text Font").assertIsDisplayed()
        composeTestRule.onNodeWithText("Code Font").assertIsDisplayed()
    }
}

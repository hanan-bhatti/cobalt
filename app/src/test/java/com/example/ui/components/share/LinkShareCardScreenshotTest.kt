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
import androidx.compose.foundation.layout.fillMaxWidth
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
class LinkShareCardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleItemWithImage = SavedItem(
        id = "test-link-1",
        type = SavedItemType.LINK,
        title = "Inter — The Typeface for Screens",
        content = "https://rsms.me/inter/",
        linkTitle = "Inter — The Typeface for Screens",
        linkDescription = "Inter is a variable font family crafted for computer screens, with high x-height and clear glyphs.",
        linkImage = "https://rsms.me/inter/img/cover.jpg"
    )

    private val sampleItemWithoutImage = SavedItem(
        id = "test-link-2",
        type = SavedItemType.LINK,
        title = "Kotlin Programming Language",
        content = "https://kotlinlang.org/",
        linkTitle = "Kotlin Programming Language",
        linkDescription = "A modern programming language that makes developers happier. Concise, safe, and multiplatform."
    )

    @Test
    fun link_share_card_style_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    LinkShareCard(
                        item = sampleItemWithImage,
                        style = LinkShareStyle.CARD,
                        showAttribution = true,
                        tagline = "Saved with Cobalt before the tab was closed forever"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_card_style_light.png")
    }

    @Test
    fun link_share_card_style_dark_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    LinkShareCard(
                        item = sampleItemWithImage,
                        style = LinkShareStyle.CARD,
                        showAttribution = true,
                        tagline = "Saved with Cobalt before the tab was closed forever"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_card_style_dark.png")
    }

    @Test
    fun link_share_list_style_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    LinkShareCard(
                        item = sampleItemWithImage,
                        style = LinkShareStyle.LIST,
                        showAttribution = true,
                        tagline = "Tucked into the second brain via Cobalt"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_list_style_light.png")
    }

    @Test
    fun link_share_list_style_dark_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    LinkShareCard(
                        item = sampleItemWithImage,
                        style = LinkShareStyle.LIST,
                        showAttribution = true,
                        tagline = "Tucked into the second brain via Cobalt"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_list_style_dark.png")
    }

    @Test
    fun link_share_card_style_no_image_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    LinkShareCard(
                        item = sampleItemWithoutImage,
                        style = LinkShareStyle.CARD,
                        showAttribution = true
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_card_style_no_image.png")
    }
}

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

    private val sampleItemWithFavicon = SavedItem(
        id = "test-link-3",
        type = SavedItemType.LINK,
        title = "GitHub: Where the world builds software",
        content = "https://github.com/",
        linkTitle = "GitHub: Where the world builds software",
        linkDescription = "GitHub is where over 100 million developers shape the future of software, together.",
        linkImage = "https://github.githubassets.com/favicons/favicon.png"
    )

    private val sampleItemWithLongDomain = SavedItem(
        id = "test-link-4",
        type = SavedItemType.LINK,
        title = "Cloud Infrastructure Architecture Design Guidelines",
        content = "https://subdomain.analytics.internal-production-monitoring.service.corp.example.com/deep/path",
        linkTitle = "Cloud Infrastructure Architecture Design Guidelines",
        linkDescription = "An exhaustive whitepaper on microservices telemetry, autoscaling policies, and network isolation.",
        linkImage = "https://subdomain.analytics.internal-production-monitoring.service.corp.example.com/favicons/favicon.ico"
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
                        showAttribution = true,
                        tagline = "Cobalt: Digital hoarding, but make it aesthetic"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_card_style_no_image.png")
    }

    @Test
    fun link_share_card_style_favicon_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    LinkShareCard(
                        item = sampleItemWithFavicon,
                        style = LinkShareStyle.CARD,
                        showAttribution = true,
                        tagline = "Cobalt caught this one"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_card_style_favicon.png")
    }

    @Test
    fun link_share_list_style_favicon_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    LinkShareCard(
                        item = sampleItemWithFavicon,
                        style = LinkShareStyle.LIST,
                        showAttribution = true,
                        tagline = "Saved with Cobalt (future me says thanks)"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_list_style_favicon.png")
    }

    @Test
    fun link_share_card_style_long_domain_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    LinkShareCard(
                        item = sampleItemWithLongDomain,
                        style = LinkShareStyle.CARD,
                        showAttribution = true,
                        tagline = "Saved with Cobalt before the tab was closed forever"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_card_style_long_domain.png")
    }

    @Test
    fun link_share_list_style_long_domain_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    LinkShareCard(
                        item = sampleItemWithLongDomain,
                        style = LinkShareStyle.LIST,
                        showAttribution = true,
                        tagline = "Another one for the collection"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/link_share_list_style_long_domain.png")
    }
}

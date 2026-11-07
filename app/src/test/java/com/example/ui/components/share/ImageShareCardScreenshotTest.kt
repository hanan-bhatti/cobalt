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
class ImageShareCardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val samplePhotoItem = SavedItem(
        id = "test-image-1",
        type = SavedItemType.IMAGE,
        title = "Afternoon in the park with Barnaby",
        content = "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
        thumbnailPath = "https://images.unsplash.com/photo-1543466835-00a7907e9de1",
        extractedText = "A warm golden hour snapshot."
    )

    private val samplePortraitPhotoItem = SavedItem(
        id = "test-image-2",
        type = SavedItemType.IMAGE,
        title = "Add caption here...",
        content = "https://images.unsplash.com/photo-1517841905240-472988babdf9",
        thumbnailPath = "https://images.unsplash.com/photo-1517841905240-472988babdf9"
    )

    private val samplePanoramaItem = SavedItem(
        id = "test-image-3",
        type = SavedItemType.IMAGE,
        title = "Alpine Mountain Ridge Panorama",
        content = "https://images.unsplash.com/photo-1506744038136-46273834b3fb",
        thumbnailPath = "https://images.unsplash.com/photo-1506744038136-46273834b3fb"
    )

    private val sampleMissingItem = SavedItem(
        id = "test-image-4",
        type = SavedItemType.IMAGE,
        title = "Corrupt / Missing File Sample",
        content = "/non/existent/path/deleted_image.jpg",
        thumbnailPath = null
    )

    @Test
    fun image_share_polaroid_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    ImageShareCard(
                        item = samplePhotoItem,
                        style = ImageShareStyle.POLAROID,
                        showAttribution = true,
                        tagline = "Saved with Cobalt before the tab was closed forever"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/image_share_polaroid_light.png")
    }

    @Test
    fun image_share_polaroid_dark_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    ImageShareCard(
                        item = samplePhotoItem,
                        style = ImageShareStyle.POLAROID,
                        showAttribution = true,
                        tagline = "Archived in Cobalt"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/image_share_polaroid_dark.png")
    }

    @Test
    fun image_share_polaroid_caption_style_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    ImageShareCard(
                        item = samplePortraitPhotoItem,
                        style = ImageShareStyle.POLAROID,
                        showAttribution = false
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/image_share_polaroid_caption_style.png")
    }

    @Test
    fun image_share_minimal_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    ImageShareCard(
                        item = samplePhotoItem,
                        style = ImageShareStyle.MINIMAL,
                        showAttribution = true,
                        tagline = "Cobalt caught this one"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/image_share_minimal_light.png")
    }

    @Test
    fun image_share_minimal_dark_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    ImageShareCard(
                        item = samplePhotoItem,
                        style = ImageShareStyle.MINIMAL,
                        showAttribution = true,
                        tagline = "Another one for the collection"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/image_share_minimal_dark.png")
    }

    @Test
    fun image_share_panorama_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    ImageShareCard(
                        item = samplePanoramaItem,
                        style = ImageShareStyle.POLAROID,
                        showAttribution = true,
                        tagline = "Saved with Cobalt so you can close that tab"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/image_share_panorama.png")
    }

    @Test
    fun image_share_missing_fallback_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    ImageShareCard(
                        item = sampleMissingItem,
                        style = ImageShareStyle.POLAROID,
                        showAttribution = true
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/image_share_missing_fallback.png")
    }
}

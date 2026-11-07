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
class MovieShareCardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovie = SavedItem(
        id = "movie-test-1",
        type = SavedItemType.MEDIA,
        mediaType = "movie",
        title = "Dune: Part Two",
        content = "https://www.themoviedb.org/movie/693134",
        thumbnailPath = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s520yNe.jpg",
        releaseYear = "2024",
        rating = 8.6,
        runtime = 166,
        genres = listOf("Science Fiction", "Adventure", "Action", "Drama", "Fantasy"),
        tagline = "Long live the fighters",
        extractedText = "Follow the mythic journey of Paul Atreides as he unites with Chani and the Fremen while on a warpath of revenge."
    )

    private val sampleLongTitleMovie = SavedItem(
        id = "movie-test-2",
        type = SavedItemType.MEDIA,
        mediaType = "anime",
        title = "The Lord of the Rings: The Fellowship of the Ring - Extended Collector Edition",
        content = "https://www.themoviedb.org/movie/120",
        thumbnailPath = "https://image.tmdb.org/t/p/w500/6oom5QYQ2yQTMJIbnvbkBL9cDK6.jpg",
        backdropUrl = null,
        releaseYear = "2001",
        rating = 8.8,
        runtime = 228,
        genres = listOf("Adventure", "Fantasy", "Action", "Drama", "Mystery", "Epic"),
        tagline = "The legend comes to life",
        extractedText = "A meek Hobbit from the Shire and eight companions set out on a journey to destroy the powerful One Ring."
    )

    @Test
    fun movie_share_theatrical_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    MovieShareCard(
                        item = sampleMovie,
                        style = MovieShareStyle.THEATRICAL,
                        showAttribution = true,
                        tagline = "Archived in Cobalt"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/movie_share_theatrical_light.png")
    }

    @Test
    fun movie_share_theatrical_dark_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    MovieShareCard(
                        item = sampleMovie,
                        style = MovieShareStyle.THEATRICAL,
                        showAttribution = true,
                        tagline = "Saved with Cobalt before the tab was closed forever"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/movie_share_theatrical_dark.png")
    }

    @Test
    fun movie_share_backdrop_light_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    MovieShareCard(
                        item = sampleMovie,
                        style = MovieShareStyle.BACKDROP,
                        showAttribution = true,
                        tagline = "Cobalt caught this one"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/movie_share_backdrop_light.png")
    }

    @Test
    fun movie_share_backdrop_dark_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    MovieShareCard(
                        item = sampleMovie,
                        style = MovieShareStyle.BACKDROP,
                        showAttribution = true,
                        tagline = "Another one for the collection"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/movie_share_backdrop_dark.png")
    }

    @Test
    fun movie_share_long_title_and_many_genres_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    MovieShareCard(
                        item = sampleLongTitleMovie,
                        style = MovieShareStyle.THEATRICAL,
                        showAttribution = true,
                        tagline = "Archived in Cobalt"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/movie_share_long_title_genres.png")
    }

    @Test
    fun movie_share_no_backdrop_fallback_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(380.dp).padding(16.dp)) {
                    MovieShareCard(
                        item = sampleLongTitleMovie,
                        style = MovieShareStyle.BACKDROP,
                        showAttribution = false
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/movie_share_no_backdrop_fallback.png")
    }
}

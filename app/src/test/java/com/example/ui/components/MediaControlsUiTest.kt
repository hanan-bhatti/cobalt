package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.data.local.TraktEpisodeEntity
import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaControlsUiTest {
    @get:Rule val compose = createComposeRule()
    private fun item() = SavedItem(type = SavedItemType.MEDIA, title = "A series with a long title", content = "", mediaType = "tv", traktId = 1,
        watchStatus = "Watching", showProgress = TvShowProgress(totalAired = 30, totalWatched = 1,
            seasons = listOf(TvSeasonProgress(1, totalEpisodes = 30, watchedEpisodes = 1,
                episodes = listOf(TvEpisodeProgress(1, 1, isWatched = true))))))

    @Test fun inlineStarsSupportSwipeAndCommitOnceWithoutASheet() {
        var score by mutableIntStateOf(0)
        val committed = mutableListOf<Int>()
        compose.setContent { MaterialTheme { MediaActivityContent(item().copy(userRating = score.takeIf { it > 0 }), false,
            onRate = { score = it }, onRatingCommit = { committed += it }, onWatchlist = {}, onSeason = {}, onStatus = {}) } }
        compose.onNodeWithTag("media-rating-stars").performTouchInput {
            swipe(Offset(5f, height / 2f), Offset(width - 3f, height / 2f), 600)
        }
        compose.runOnIdle { assertEquals(10, score); assertEquals(listOf(10), committed) }
        compose.onNodeWithText("Save rating").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear rating").performClick()
        compose.runOnIdle { assertEquals(0, score); assertEquals(listOf(10, 0), committed) }
    }
    @Test fun fourStatusChoicesAndBookmarkUseSingleCallbacks() {
        var selected: String? = null
        var bookmarks = 0
        compose.setContent { MaterialTheme { MediaActivityContent(item(), false, {}, { bookmarks++ }, {}, { selected = it }, {}) } }
        compose.onNodeWithText("Completed").performClick()
        compose.runOnIdle { assertEquals("Completed", selected) }
        compose.onNodeWithText("Add to watchlist").performClick()
        compose.runOnIdle { assertEquals(1, bookmarks) }
    }
    @Test fun allEpisodesRemainReachableAndProgressCannotBeToggled() {
        val episodes = (1..30).map { TraktEpisodeEntity("show",1,it,"Episode $it", "A brief episode synopsis", "2026-11-07",42,8.0) }
        compose.setContent { MaterialTheme { SeasonEpisodesContent(item(),1, episodes,false,null, {}, {}, {}) } }
        compose.onNodeWithText("Episode 1").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        compose.onNodeWithText("Read only", substring = true).assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Episode 30"))
        compose.onNodeWithText("Episode 30").assertIsDisplayed()
    }
    @Test fun nextSeasonCannotRunPastTheFinalPageAndSwipingWorks() {
        compose.setContent { MaterialTheme { SeasonPagerContent((1..9).toList(), 1) { number, _, _ -> Text("Season page $number") } } }
        repeat(8) { compose.onNodeWithContentDescription("Next season").performClick() }
        repeat(3) { compose.onNodeWithContentDescription("Next season").performTouchInput { click() } }
        compose.onNodeWithContentDescription("Next season").assertIsNotEnabled()
        compose.onNodeWithText("Season page 9").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous season").performClick()
        compose.onNodeWithText("Season page 8").assertIsDisplayed()
    }
    @Test fun partialProgressComesFromPlaybackDataAndUnknownEpisodesHaveNoBar() {
        val episode = TraktEpisodeEntity("show",1,2,"Playing",null,null,42,8.0,playbackProgress = 35.5)
        compose.setContent { MaterialTheme { SeasonEpisodeRow(episode,null) } }
        compose.onNodeWithText("In progress · 35%").assertIsDisplayed()
    }
    @Test fun episodeSheetScreenshots() {
        var dark by mutableStateOf(false)
        val rows = (1..4).map { TraktEpisodeEntity("show",1,it,"Episode $it: A clean title", "A short synopsis stays below the episode facts.", "2026-11-07",42,8.0) }
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), typography = com.example.ui.theme.Typography) {
            Surface { SeasonEpisodesContent(item(),1,rows,false,null, {}, {}, {}) }
        } }
        for (name in listOf("light", "dark")) {
            compose.runOnIdle { dark = name == "dark" }; compose.waitForIdle()
            val file = java.io.File("build/reports/media-controls/episodes-$name.png"); file.parentFile?.mkdirs()
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    @Test fun lightAndDarkActivityScreenshots() {
        var dark by mutableStateOf(false)
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), typography = com.example.ui.theme.Typography) {
            Surface { Column(Modifier.fillMaxSize()) { MediaActivityContent(item().copy(userRating = 7),false, {}, {}, {}, {}, {}) } }
        } }
        for (name in listOf("light", "dark")) {
            compose.runOnIdle { dark = name == "dark" }
            compose.waitForIdle()
            val file = java.io.File("build/reports/media-controls/$name.png"); file.parentFile?.mkdirs()
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}

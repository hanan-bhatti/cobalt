package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.media.recommendation.*
import com.example.ui.theme.Typography
import com.example.widget.ScreeningSource
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class ScreeningTasteUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_799_999_000_000L
    private fun saved(id: String = "fixture", genres: List<String> = listOf("Drama"), rating: Int? = 10) = SavedItem(
        id = id, type = SavedItemType.MEDIA, title = "Saved $id", content = "", mediaType = "movie",
        genres = genres, userRating = rating, watchStatus = "Plan to Watch", timestamp = now - 45 * SCREENING_DAY, updatedAt = now)
    private fun insights(items: List<SavedItem>, signals: ScreeningSignals = ScreeningSignals(), publications: List<ScreeningDecisionSnapshot> = emptyList()): ScreeningInsights {
        val profile = ScreeningTaste.profile(items, signals, now)
        return ScreeningInsights(items, signals, profile, ScreeningRecommendationEngine.rank(items, signals, now, includeCooldowns = false, profile = profile), emptyList(), publications)
    }
    private fun scrollTo(text: String) {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText(text))
    }
    private fun openEvidence(id: String) {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasTestTag("taste_evidence_$id"))
        compose.onNodeWithTag("taste_evidence_$id").performClick()
    }
    private fun sheetAction(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("taste_detail_sheet")))
        .performScrollTo().performClick()
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screening-redesign/$name.png")
        file.parentFile?.mkdirs()
        val target = if (compose.onAllNodesWithTag("taste_detail_sheet").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("taste_detail_sheet") else compose.onRoot()
        file.outputStream().use { target.captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun overviewShowsFiveAndOthersOpensRemainingGenresAndTheirEvidence() {
        val item = saved(genres = (1..30).map { "Genre ${it.toString().padStart(2, '0')}" })
        compose.setContent { MaterialTheme(typography = Typography) { Surface { ScreeningTasteContent(insights(listOf(item)), onBack = {}) } } }
        compose.onNodeWithText("Genre 05").assertExists()
        compose.onNodeWithText("Genre 06").assertDoesNotExist()
        scrollTo("Others"); compose.onNodeWithText("25").assertIsDisplayed()
        compose.onNodeWithText("Others").performClick()
        compose.onNodeWithText("Other genres").assertIsDisplayed()
        compose.onNodeWithText("Genre 06").performClick()
        compose.onNodeWithText("Learned weight").assertDoesNotExist()
        sheetAction("Learning details")
        compose.onNodeWithText("Learned weight").assertExists()
        compose.onNodeWithText("Your rating 10/10").assertExists()
        compose.onNodeWithTag("taste_evidence_rating:${ScreeningRecommendationEngine.key(item)}").performClick()
        compose.onNodeWithText("What this changes").assertIsDisplayed()
        compose.onNodeWithText("Ignore rating").assertExists()
        compose.onNodeWithContentDescription("Back to genre").performClick()
        compose.onNode(hasText("Genre 06") and hasAnyAncestor(hasTestTag("taste_detail_sheet"))).assertIsDisplayed()
        compose.onNodeWithContentDescription("Close details").performClick()
        compose.onNodeWithText("Other genres").assertIsDisplayed()
    }

    @Test fun correctionsAreLabelledAndResetRequiresItsConfirmation() {
        val item = saved(); val key = ScreeningRecommendationEngine.key(item)
        var signals = ScreeningSignals(items = mapOf(key to ScreeningItemSignal(notInterested = true)),
            reactions = mapOf(key to ScreeningReaction(key, ScreeningPreference.DISLIKED, now)),
            feedback = listOf(ScreeningFeedbackEvent(key, ScreeningFeedback.NOT_INTERESTED, now - 1, item.genres, "feedback:1")))
        var data by mutableStateOf(insights(listOf(item), signals))
        var ignored = ""; var cleared = ""; var allowed = ""; var resets = 0
        compose.setContent { MaterialTheme(typography = Typography) { Surface { ScreeningTasteContent(data, onBack = {},
            onIgnore = { evidence, ignore -> ignored = evidence.id
                signals = signals.copy(ignoredEvidence = if (ignore) setOf(evidence.id) else emptySet())
                data = insights(listOf(item), signals) },
            onClearReaction = { cleared = it
                signals = signals.copy(reactions = mapOf(key to ScreeningReaction(key, null, now + 1)))
                data = insights(listOf(item), signals) },
            onRecommendAgain = { allowed = it }, onReset = { resets++ }) } } }
        scrollTo("See all feedback"); compose.onNodeWithText("See all feedback").performClick()
        compose.onNodeWithText("Source").assertDoesNotExist()
        capture("feedback-light")
        openEvidence("rating:$key"); sheetAction("Ignore rating")
        compose.runOnIdle { assertEquals("rating:$key", ignored) }
        compose.onNodeWithText("Use rating again").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close details").performClick()
        compose.onNodeWithTag("taste_evidence_rating:$key").assertDoesNotExist()
        openEvidence("reaction:$key"); sheetAction("Clear thumb")
        compose.runOnIdle { assertEquals(key, cleared) }
        compose.onNodeWithText("Thumb cleared").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close details").performClick()
        compose.onNodeWithTag("taste_evidence_reaction:$key").assertDoesNotExist()
        compose.onNodeWithText("All feedback").performClick()
        openEvidence("rating:$key"); sheetAction("Use rating again")
        compose.runOnIdle { assertTrue(signals.ignoredEvidence.isEmpty()) }
        compose.onNodeWithContentDescription("Close details").performClick()
        scrollTo("Hidden titles"); compose.onNodeWithText("Hidden titles").performClick()
        compose.onNodeWithText(item.title).performClick()
        scrollTo("Recommend again"); compose.onNodeWithText("Recommend again").performClick()
        compose.runOnIdle { assertEquals(key, allowed) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        scrollTo("Reset feedback"); compose.onNodeWithText("Reset feedback").performClick()
        compose.onNodeWithText("Your saved ratings and watch progress will stay.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Keep feedback").performClick()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithText("Reset feedback").performClick()
        compose.onNodeWithText("Clear Screening feedback").performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }

    @Test fun whyDisclosesRealScoreTotalsAndRetainedPublication() {
        val item = saved(); val base = insights(listOf(item))
        val pick = ScreeningRecommendationEngine.choose(base.ranked, "published")!!
        val publication = ScreeningDecisionSnapshot("one", "Widget 77", "ALL", pick.key, item.id, item.title, now,
            ScreeningScoreTrace.from(pick))
        val data = insights(listOf(item), publications = listOf(publication))
        compose.setContent { MaterialTheme(typography = Typography) { Surface { ScreeningTasteContent(data, initialItemId = item.id, onBack = {}) } } }
        compose.onNodeWithText(item.title).assertIsDisplayed()
        compose.onNodeWithText("Why it fits").assertExists()
        compose.onNodeWithText("Starting points").assertDoesNotExist()
        capture("why-light")
        scrollTo("Score details"); compose.onNodeWithText("Score details").performClick()
        compose.onNodeWithText("Starting points").assertExists()
        val actual = String.format(java.util.Locale.getDefault(), "%.2f", pick.score)
        compose.onAllNodesWithText(actual).assertAny(hasText(actual))
        compose.onNodeWithText("Selection points").performScrollTo().assertIsDisplayed()
        capture("score-sheet-light")
        compose.onNodeWithContentDescription("Close details").performClick()
        scrollTo("Last suggestion"); compose.onNodeWithText("Last suggestion").performClick()
        compose.onNodeWithText("Home screen card").assertIsDisplayed()
        compose.onNodeWithText("Starting points").assertDoesNotExist()
        sheetAction("Score details")
        compose.onNodeWithText("Starting points").assertExists()
        compose.onNodeWithText("52%", substring = true).assertDoesNotExist()
    }

    @Test fun savedTitleSearchExplainsCompletionAndUnsupportedManga() {
        val done = saved("Completed title", rating = null).copy(watchStatus = "Completed")
        val manga = saved("Manga title", rating = null).copy(mediaType = "manga")
        var target by mutableStateOf(done.id)
        var source by mutableStateOf(ScreeningSource.ALL)
        val data = insights(listOf(done, manga))
        compose.setContent { MaterialTheme(typography = Typography) { Surface { key(target, source) {
            ScreeningTasteContent(data, initialItemId = target, source = source, onBack = {})
        } } } }
        compose.onNodeWithText("Already marked finished").assertIsDisplayed()
        scrollTo("Saved titles"); compose.onNodeWithText("Saved titles").performClick()
        capture("search-light")
        compose.onNodeWithTag("taste_title_search").performTextInput("Completed")
        compose.onNodeWithText(done.title).assertIsDisplayed(); compose.onNodeWithText(manga.title).assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithTag("taste_title_search").performTextInput("No such title")
        compose.onNodeWithText("No matching titles").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Ready to suggest").performClick()
        compose.onNodeWithText("No titles here").assertIsDisplayed()
        compose.onNodeWithText("All", substring = false).performClick()
        compose.onNodeWithText(done.title).performClick()
        compose.onNodeWithText("Already marked finished").assertIsDisplayed()
        compose.runOnIdle { target = manga.id; source = ScreeningSource.MANGA }
        compose.onNodeWithText("This format is not supported by Screening yet").assertIsDisplayed()
    }

    @Test fun emptyAndRefreshErrorStatesOfferUsefulActions() {
        var media = 0; var retries = 0
        compose.setContent { MaterialTheme(typography = Typography) { Surface {
            ScreeningTasteContent(insights(emptyList()), error = "Couldn't refresh your taste. Try again.", onBack = {}, onOpenMedia = { media++ }, onRetry = { retries++ })
        } } }
        compose.onNodeWithText("Try again").performClick()
        scrollTo("Open Media"); compose.onNodeWithText("Open Media").performClick()
        compose.runOnIdle { assertEquals(1, media); assertEquals(1, retries) }
        compose.onNodeWithText("Your taste starts here").assertExists()
        compose.onNodeWithText("Others").assertDoesNotExist()
    }

    @Test fun savedTitleNavigationRestoresThePreviousTitleAndSearchAfterRecreation() {
        val first = saved("first"); val second = saved("second")
        val restoration = StateRestorationTester(compose)
        var backCalls = 0
        restoration.setContent { MaterialTheme(typography = Typography) { Surface {
            ScreeningTasteContent(insights(listOf(first, second)), initialItemId = first.id, onBack = { backCalls++ })
        } } }
        scrollTo("Saved titles"); compose.onNodeWithText("Saved titles").performClick()
        compose.onNodeWithTag("taste_title_search").performTextInput("second")
        compose.onNodeWithText(second.title).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(second.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("taste_title_search").assertTextEquals("second")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText(first.title).assertExists()
        compose.onNodeWithText(second.title).assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test fun undatedThumbWithoutGenresExplainsOnlyItsActualTitleInfluence() {
        val item = saved(genres = emptyList(), rating = null)
        val key = ScreeningRecommendationEngine.key(item)
        val data = insights(listOf(item), ScreeningSignals(reactions = mapOf(key to ScreeningReaction(key, ScreeningPreference.LIKED))))
        compose.setContent { MaterialTheme(typography = Typography) { Surface { ScreeningTasteContent(data, onBack = {}) } } }
        scrollTo("See all feedback"); compose.onNodeWithText("See all feedback").performClick()
        openEvidence("reaction:$key")
        compose.onNodeWithText("Raises this title's priority. Genre information is not recorded yet.").assertIsDisplayed()
        compose.onNodeWithText("Date not recorded").assertExists()
        compose.onNodeWithText("Current influence").assertDoesNotExist()
        sheetAction("Learning details")
        compose.onNodeWithText("Current influence").assertExists()
        capture("sparse-feedback-sheet-light")
    }

    @Test fun chartsAndOthersRenderInBothThemesAndAtLargeTextOnANarrowScreen() {
        val item = saved(genres = listOf("Action & Adventure", "Science Fiction", "Psychological Thriller", "Historical Drama", "Documentary", "Fantasy", "Crime", "Mystery"))
        val data = insights(listOf(item))
        var dark by mutableStateOf(false); var large by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.6f else density.fontScale)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), typography = com.example.ui.theme.Typography) {
                    Surface { Box(Modifier.width(if (large) 320.dp else 360.dp)) { ScreeningTasteContent(data, onBack = {}) } }
                }
            }
        }
        capture("overview-light")
        compose.runOnIdle { dark = true }; capture("overview-dark")
        compose.runOnIdle { large = true }; capture("overview-dark-large-320dp")
        // Exercise a real scroll gesture. The lazy-list semantic scroll driver can
        // loop when the expanded header remeasures after a font-scale change.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performTouchInput { swipeUp() }
        compose.onNodeWithText("Others").assertIsDisplayed().performClick()
        compose.onNodeWithText("Other genres").assertIsDisplayed()
        capture("others-dark-large-320dp")
        compose.runOnIdle { dark = false }; capture("others-light-large-320dp")
    }
}

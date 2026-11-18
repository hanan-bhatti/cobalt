package com.example.ui.screens

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CustomFolderEntity
import com.example.data.local.toEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.media.recommendation.*
import com.example.ui.theme.Typography
import com.example.ui.viewmodel.CobaltViewModel
import com.example.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Actual screen wrappers, not populated presentation-only fixtures. Entirely host-side. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreeningEntryUiTest {
    @get:Rule val compose = createComposeRule()
    private fun noLoader() {
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
            useUnmergedTree = true).assertCountEquals(0)
    }
    private fun scrollTo(text: String) {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText(text))
    }

    @Test fun nativeCardKeepsReadableContentAndBothThumbTargetsInsideAt320DpWithLargeFonts() {
        val title = "A very long saved title with a subtitle"
        val entry = ScreeningWidgetEntry(pick = ScreeningPick("large", "movie:local:large", title, "Movie · 2 h 4 min", "Saved pick", null, "movie"))
        compose.setContent { MaterialTheme(typography = Typography) { Surface {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Box(Modifier.width(320.dp)) { ScreeningCardPreview(entry, onOpen = {}) }
            }
        } } }
        compose.onNodeWithText(title).assertIsDisplayed()
        val card = compose.onNodeWithContentDescription("View details.", substring = true).fetchSemanticsNode().boundsInRoot
        val like = compose.onNodeWithContentDescription("Like $title", substring = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dislike = compose.onNodeWithContentDescription("Not for me $title", substring = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(like.left >= card.left && dislike.right <= card.right)
        assertTrue(like.right <= dislike.left && like.bottom <= card.bottom)
        assertTrue(compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.right < like.left)
        noLoader()
        val out = File("build/reports/screening-entry/preview-320dp-large-font.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun freshScreensUseReadyContentDuringBlockedStorageAndRemovalNeverChangesNotes() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("cobalt_settings", 0).edit().putBoolean("dynamic_color", false).putString("theme_mode", "Light").commit()
        context.getSharedPreferences("note_migration", 0).edit().putInt("completed_format", 1).commit()
        val now = System.currentTimeMillis()
        val movie = SavedItem(id = "entry-movie", type = SavedItemType.MEDIA, title = "Moonlight", content = "", mediaType = "movie",
            watchStatus = "Plan to Watch", userRating = 10, genres = (1..60).map { "Genre ${it.toString().padStart(2, '0')}" }, timestamp = now - 40 * SCREENING_DAY)
        val other = movie.copy(id = "entry-other", title = "Arrival", userRating = null)
        val note = SavedItem(id = "protected-note", type = SavedItemType.TEXT, title = "Keep my note", content = "This note must survive all widget operations.", folders = listOf("Ideas"))
        val dao = AppDatabase.getDatabase(context).savedItemDao()
        val store = ScreeningStore(context)
        val question = runBlocking(Dispatchers.IO) {
            dao.insertItem(movie.toEntity()); dao.insertItem(other.toEntity()); dao.insertItem(note.toEntity())
            AppDatabase.getDatabase(context).customFolderDao().insertFolder(CustomFolderEntity("Ideas"))
            val pool = ScreeningRecommendationEngine.rank(listOf(movie, other), store.observe(listOf(movie, other), now), now, includeCooldowns = false)
            store.createDelivery(pool.first { it.item.id == movie.id }, now, "fixture-question", false)!!
        }
        val manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).addBoundWidget(301, AppWidgetProviderInfo().apply { provider = ComponentName(context, FolioWidgetProvider::class.java) })
        shadowOf(manager).addBoundWidget(302, AppWidgetProviderInfo().apply { provider = ComponentName(context, ScreeningWidgetProvider::class.java) })
        FolioConfigStore(context).save(301, FolioConfig("Ideas")); FolioConfigStore(context).save(-1, FolioConfig("Ideas"))
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), ScreeningRecommendationEngine.rank(listOf(movie), now = now, includeCooldowns = false), 302, ScreeningSchedule.day(now))
        ScreeningWidgetStore(context).save(302, entry); ScreeningWidgetStore(context).save(-1, entry)
        lateinit var viewModel: CobaltViewModel
        compose.runOnUiThread { viewModel = CobaltViewModel(context) }
        runBlocking { withTimeout(15_000) {
            viewModel.screening.state.first { it.ready && movie.id in it.insights.itemsById && question.token in it.insights.deliveries }
            viewModel.widgets.state.first { 301 in it.folioIds && 302 in it.screeningIds }
        } }
        compose.waitUntil(15_000) {
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.folioItems.value.any { it.id == note.id } && viewModel.customFolderEntities.value.any { it.name == "Ideas" }
        }
        val prepared = viewModel.screening.state.value.insights
        val beforeReads = store.changes.value
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val blocker = executor.submit {
            context.openOrCreateDatabase("screening_learning.db", Context.MODE_PRIVATE, null).use { db ->
                db.beginTransaction()
                try { locked.countDown(); release.await(90, TimeUnit.SECONDS) } finally { db.endTransaction() }
            }
        }
        var screen by mutableStateOf("taste")
        try {
            assertTrue(locked.await(10, TimeUnit.SECONDS))
            viewModel.screening.refresh()
            compose.mainClock.autoAdvance = false
            compose.setContent { MaterialTheme(typography = Typography) { Surface {
                when (screen) {
                    "taste" -> ScreeningTasteScreen(viewModel, {})
                    "why" -> ScreeningTasteScreen(viewModel, {}, initialItemId = movie.id)
                    "review" -> ScreeningRecommendationsScreen(viewModel, {}, initialItemId = movie.id)
                    "question" -> ScreeningQuestionScreen(question.token, viewModel, {})
                    "screening-widget" -> ScreeningWidgetSettingsScreen(viewModel, {})
                    "folio-widget" -> WidgetSettingsScreen(viewModel, {})
                }
            } } }
            fun open(destination: String) {
                compose.runOnIdle { screen = destination }
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                noLoader()
            }
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.onNodeWithText("Your genres").assertIsDisplayed(); noLoader()
            assertEquals(beforeReads, store.changes.value)
            assertSame(prepared, viewModel.screening.state.value.insights)
            compose.mainClock.autoAdvance = true
            scrollTo("Others"); compose.onNodeWithText("Others").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Other genres").assertIsDisplayed(); noLoader()
            open("why")
            compose.onNodeWithText(movie.title).assertExists()
            scrollTo("Saved titles"); compose.onNodeWithText("Saved titles").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("taste_title_search").performTextInput("Arrival")
            compose.onNode(hasText(other.title) and !hasTestTag("taste_title_search")).assertExists(); noLoader()
            open("review")
            compose.onNodeWithText(movie.title).assertIsDisplayed()
            val preview = File("build/reports/screening-entry/review-native.png").apply { parentFile?.mkdirs() }
            preview.outputStream().use { compose.onNodeWithContentDescription("View details.", substring = true).captureToImage()
                .asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            scrollTo("What do you think?")
            compose.onNodeWithText("What do you think?").assertIsEnabled().performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("Good pick").assertIsDisplayed(); noLoader()
            open("question"); compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("About ${movie.title}").assertIsDisplayed(); noLoader()
            open("screening-widget")
            compose.onNodeWithText("Card 1 of 1").assertExists()
            scrollTo("Remove from Cobalt"); compose.onNodeWithText("Remove from Cobalt").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Remove saved card").performClick()
            compose.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); viewModel.widgets.state.value.screeningIds.isEmpty() }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Create a card").assertExists()
            compose.onNodeWithText("Card 1 of 1").assertDoesNotExist()
            assertTrue(ScreeningWidgetProvider.ids(context).contains(302))
            compose.onNodeWithText("Create a card").performClick(); compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Preview · not added").assertExists()
            assertTrue(viewModel.widgets.state.value.screeningIds.isEmpty())
            open("folio-widget")
            compose.onNodeWithText("Folio 1 of 1").assertExists()
            FolioWidgetProvider().onDeleted(context, intArrayOf(301))
            compose.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); viewModel.widgets.state.value.folioIds.isEmpty() }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Create a Folio").assertExists()
            compose.onNodeWithText("Folio 1 of 1").assertDoesNotExist(); noLoader()
            val out = File("build/reports/screening-entry/widget-removed.png").apply { parentFile?.mkdirs() }
            out.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            release.countDown(); blocker.get(10, TimeUnit.SECONDS); executor.shutdownNow()
            compose.mainClock.autoAdvance = true
        }
        val after = runBlocking(Dispatchers.IO) { dao.getItemById(note.id)!! }
        assertEquals(note.content, after.content); assertEquals(note.title, after.title)
        assertFalse(after.isDeleted); assertFalse(after.isArchived)
        assertEquals(listOf("Ideas"), after.toDomainModel().folders)
        assertEquals(60, viewModel.screening.state.value.insights.profile.genres.size)
        assertEquals(55, ScreeningGenres.overview(viewModel.screening.state.value.insights.profile.genres).others.size)
    }
}

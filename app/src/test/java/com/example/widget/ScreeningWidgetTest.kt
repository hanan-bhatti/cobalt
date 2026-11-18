package com.example.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.FrameLayout
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.media.recommendation.ScreeningRecommendationEngine
import com.example.media.recommendation.SCREENING_DAY
import com.example.ui.theme.DarkColorScheme
import com.example.ui.theme.LightColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreeningWidgetTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L
    private val day = "2026-11-12"
    private fun item(id: String, kind: String = "movie") = SavedItem(id, SavedItemType.MEDIA, id,
        "Private synopsis", timestamp = now - 30 * SCREENING_DAY, mediaType = kind, watchStatus = "Plan to Watch")
    private fun ranked(vararg items: SavedItem) = ScreeningRecommendationEngine.rank(items.toList(), now = now, includeCooldowns = false)
    private fun bind(id: Int) {
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(id,
            AppWidgetProviderInfo().apply { provider = ComponentName(context, ScreeningWidgetProvider::class.java) })
    }
    @Before fun clear() {
        for (name in listOf("cobalt_screening_widgets", "cobalt_settings", "note_migration", "note_startup_guard")) {
            context.getSharedPreferences(name, 0).edit().clear().commit()
        }
        context.getSharedPreferences("note_migration", 0).edit().putInt("completed_format", 1).commit()
        context.getSharedPreferences("cobalt_settings", 0).edit().putBoolean("dynamic_color", false).putString("theme_mode", "Light").commit()
    }
    @Test fun refreshKeepsPickAndNextCyclesWithoutImmediateRepeats() {
        val pool = ranked(item("one"), item("two"), item("three"))
        var entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), pool, 10, day)
        val visited = mutableSetOf(entry.pick!!.key)
        assertEquals(entry, ScreeningWidgetSelection.update(entry, pool, 10, day))
        repeat(2) {
            val before = entry
            entry = ScreeningWidgetSelection.update(entry, pool, 10, day, true)
            assertNotEquals(before.pick!!.key, entry.pick!!.key)
            assertTrue(visited.add(entry.pick!!.key))
        }
        assertEquals(3, visited.size)
        assertNotEquals(entry.pick!!.key, ScreeningWidgetSelection.update(entry, pool, 10, day, true).pick!!.key)
    }
    @Test fun sourceFiltersAndEligibilityChangesReplaceCurrentPick() {
        val movie = item("movie"); val show = item("show", "tv")
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(config = ScreeningWidgetConfig(ScreeningSource.MOVIES)), ranked(movie, show), 10, day)
        assertEquals(movie.id, entry.pick!!.itemId); assertFalse(entry.canAdvance)
        val shows = ScreeningWidgetSelection.update(entry.copy(config = ScreeningWidgetConfig(ScreeningSource.SHOWS)), ranked(movie, show), 10, day)
        assertEquals(show.id, shows.pick!!.itemId)
        val empty = ScreeningWidgetSelection.update(shows, ranked(movie, show.copy(watchStatus = "Completed")), 10, day)
        assertNull(empty.pick); assertFalse(empty.canAdvance)
    }
    @Test fun animeHasItsOwnSourceWithoutChangingTheEverythingPool() {
        val movie = item("movie"); val show = item("show", "tv"); val anime = item("anime", "anime")
        val pool = ranked(movie, show, anime)
        val all = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), pool, 10, day)
        assertEquals(3, all.poolSize)
        val tv = ScreeningWidgetSelection.update(all.copy(config = ScreeningWidgetConfig(ScreeningSource.SHOWS)), pool, 10, day)
        assertEquals(show.id, tv.pick!!.itemId); assertEquals(1, tv.poolSize)
        val animated = ScreeningWidgetSelection.update(tv.copy(config = ScreeningWidgetConfig(ScreeningSource.ANIME)), pool, 10, day)
        assertEquals(anime.id, animated.pick!!.itemId); assertEquals("anime", animated.pick!!.kind)
        assertTrue(animated.pick!!.metadata.contains("Anime")); assertEquals(1, animated.poolSize)
    }
    @Test fun newSourceChoicesPersistIndependentlyAndEmptyMangaCannotAdvance() {
        val store = ScreeningWidgetStore(context)
        store.saveConfig(10, ScreeningWidgetConfig(ScreeningSource.ANIME))
        store.saveConfig(11, ScreeningWidgetConfig(ScreeningSource.MANGA))
        val restored = ScreeningWidgetStore(context)
        assertEquals(ScreeningSource.ANIME, restored.get(10).config.source)
        assertEquals(ScreeningSource.MANGA, restored.get(11).config.source)
        val empty = ScreeningWidgetSelection.update(restored.get(11), ranked(item("movie"), item("show", "tv")), 11, day)
        assertNull(empty.pick); assertFalse(empty.canAdvance)
        assertTrue(ScreeningSource.MANGA.accepts(item("manga", "manga")))
        assertFalse(ScreeningSource.ANIME.accepts(item("manga", "manga")))
    }
    @Test fun oneTitleCannotAdvanceAndNewDayStartsFreshCycle() {
        val pool = ranked(item("one"))
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), pool, 10, day)
        assertFalse(entry.canAdvance)
        assertEquals(entry, ScreeningWidgetSelection.update(entry, pool, 10, day, true))
        val tomorrow = ScreeningWidgetSelection.update(entry, pool, 10, "2026-11-13")
        assertEquals(entry.pick, tomorrow.pick); assertEquals(entry.generation + 1, tomorrow.generation)
        assertEquals(listOf(entry.pick!!.key), tomorrow.seen)
    }
    @Test fun durablePinAndSettingsRemainIndependentAndCanceledAllocationsStayHidden() {
        bind(51); bind(52)
        val store = ScreeningWidgetStore(context)
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), ranked(item("one")), 51, day)
        val token = store.stagePin(entry)
        assertFalse(ScreeningWidgetProvider.placedIds(context).contains(51))
        assertFalse(store.finishPin(token, -1)); assertTrue(store.finishPin(token, 51))
        assertFalse(store.finishPin(token, 52))
        store.saveConfig(52, ScreeningWidgetConfig(ScreeningSource.SHOWS))
        assertEquals(entry, ScreeningWidgetStore(context).get(51))
        assertEquals(ScreeningSource.SHOWS, store.get(52).config.source)
        store.delete(52); assertEquals(listOf(51), ScreeningWidgetProvider.placedIds(context))
        assertFalse(store.preferences.getString("widget_51", "")!!.contains("Private synopsis"))
    }
    @Test fun expiredPinsCorruptDataAndConcurrentSettingsCannotOverwriteNewerState() {
        val store = ScreeningWidgetStore(context)
        val token = store.stagePin(ScreeningWidgetEntry())
        store.preferences.edit().putLong("pin_${token}_at", System.currentTimeMillis() - 86_400_001).commit()
        assertFalse(store.finishPin(token, 10)); assertFalse(store.finishPin("unknown", 10))
        store.preferences.edit().putString("widget_10", "broken json").commit()
        assertEquals(ScreeningWidgetEntry(), store.get(10))
        val before = store.get(10)
        store.saveConfig(10, ScreeningWidgetConfig(ScreeningSource.MOVIES))
        assertFalse(store.compareAndSave(10, before, before.copy(day = day)))
        assertEquals(ScreeningSource.MOVIES, store.get(10).config.source)
    }
    @Test fun timeoutFailureAndStaleNextPreserveLastGoodPick() = runBlocking {
        bind(53)
        val pool = ranked(item("one"), item("two"))
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), pool, 53, day)
        val store = ScreeningWidgetStore(context); store.save(53, entry)
        assertFalse(ScreeningWidgetProvider.updateWithLoader(context, timeoutMillis = 20) { delay(1_000); pool })
        assertEquals(entry, store.get(53))
        assertFalse(ScreeningWidgetProvider.updateWithLoader(context) { error("Database unavailable") })
        val view = shadowOf(AppWidgetManager.getInstance(context)).getViewFor(53)
        assertEquals(View.GONE, view.findViewById<View>(R.id.screening_initial_message).visibility)
        assertTrue(view.findViewById<View>(R.id.screening_body).contentDescription.contains(entry.pick!!.title))
        // The worker's local date may differ from this fixture's date. Capture its first update.
        assertTrue(ScreeningWidgetProvider.updateWithLoader(context) { pool })
        val current = store.get(53)
        assertTrue(ScreeningWidgetProvider.updateWithLoader(context, advanceId = 53, expectedGeneration = current.generation) { pool })
        val advanced = store.get(53)
        assertNotEquals(current.pick!!.key, advanced.pick!!.key)
        assertTrue(ScreeningWidgetProvider.updateWithLoader(context, advanceId = 53, expectedGeneration = current.generation) { pool })
        assertEquals(advanced, store.get(53))
    }
    @Test fun emptyCardOpensMediaAndSavedTitlesHaveReversibleFeedbackTargets() {
        val empty = ScreeningWidgetProvider.views(context, 10, ScreeningWidgetEntry()).apply(context, FrameLayout(context))
        assertEquals(View.GONE, empty.findViewById<View>(R.id.screening_initial_message).visibility)
        assertTrue(empty.findViewById<View>(R.id.screening_body).contentDescription.contains("Open Media"))
        assertEquals(View.GONE, empty.findViewById<View>(R.id.screening_feedback).visibility)
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), ranked(item("one")), 10, day)
        val single = ScreeningWidgetProvider.views(context, 10, entry).apply(context, FrameLayout(context))
        assertTrue(single.findViewById<View>(R.id.screening_body).contentDescription.contains("one"))
        assertEquals("Like one.", single.findViewById<View>(R.id.screening_like).contentDescription)
        assertEquals("Not for me one.", single.findViewById<View>(R.id.screening_dislike).contentDescription)
        assertTrue(single.findViewById<View>(R.id.screening_like).isEnabled)
    }
    @Test fun bothWidgetsUseAppLightDarkAndDynamicPalettes() {
        val prefs = context.getSharedPreferences("cobalt_settings", 0)
        for (dynamic in listOf(false, true)) for (dark in listOf(false, true)) {
            prefs.edit().putBoolean("dynamic_color", dynamic).putString("theme_mode", if (dark) "Dark" else "Light").commit()
            val colors = if (dynamic) { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
                else if (dark) DarkColorScheme else LightColorScheme
            assertEquals(dark, WidgetAppearance.isDark(context))
            assertEquals(colors.surfaceContainer.toArgb(), WidgetAppearance.colors(context).surfaceContainer.toArgb())
            assertEquals(colors.surfaceContainer.toArgb(), ScreeningRenderer.render(context, ScreeningWidgetEntry(), 316, 300).getPixel(10, 205))
            assertEquals(dark, FolioRenderer.isDark(context))
        }
    }
    @Test fun noteOnlyFontChangesDoNotChangeCardButUiFontChangesDo() {
        val prefs = context.getSharedPreferences("cobalt_settings", 0)
        fun render() = ScreeningRenderer.render(context, ScreeningWidgetEntry(), 316, 300)
        val before = render()
        prefs.edit().putString("pref_text_font", "lexend").putBoolean("pref_apply_to_app_ui", false).commit()
        assertTrue(before.sameAs(render()))
        prefs.edit().putBoolean("pref_apply_to_app_ui", true).commit()
        assertFalse(before.sameAs(render()))
    }
    @Test fun renderFixturesForApprovedGeometryLargeTextAndMissingArtwork() {
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), ranked(item("Your Name.").copy(releaseYear = "2016", genres = listOf("Anime"), runtime = 106)), 10, day).copy(poolSize = 2)
        val directory = File("build/screening-validation").apply { mkdirs() }
        val artwork = BitmapFactory.decodeFile(File("../design/widgets/assets/screening-source-artwork.png").path)
        assertNotNull(artwork)
        for (theme in listOf("Light", "Dark")) {
            context.getSharedPreferences("cobalt_settings", 0).edit().putString("theme_mode", theme).commit()
            File(directory, "screening-${theme.lowercase()}.png").outputStream().use {
                ScreeningRenderer.render(context, entry, 672, 528, artwork).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        context.resources.configuration.fontScale = 1.4f
        File(directory, "screening-large-text.png").outputStream().use {
            ScreeningRenderer.render(context, entry.copy(pick = entry.pick!!.copy(title = "A long show title with emoji 📺 and extra words")), 672, 528).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun thumbsSwitchClearPersistAndRejectOldCardActionsWithoutAdvancing() {
        val store = ScreeningWidgetStore(context)
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), ranked(item("one")),10,day)
        store.save(10,entry); store.save(11,entry)
        assertTrue(store.toggleThumb(10,entry.generation,"one",ScreeningThumb.LIKED))
        assertEquals(ScreeningThumb.LIKED,ScreeningWidgetStore(context).get(11).thumb)
        assertEquals(entry.pick,store.get(10).pick); assertEquals(entry.generation,store.get(10).generation)
        assertTrue(store.toggleThumb(10,entry.generation,"one",ScreeningThumb.DISLIKED))
        assertEquals(ScreeningThumb.DISLIKED,store.get(10).thumb)
        assertTrue(store.toggleThumb(10,entry.generation,"one",ScreeningThumb.DISLIKED))
        assertNull(store.get(10).thumb)
        assertFalse(store.toggleThumb(10,entry.generation + 1,"one",ScreeningThumb.LIKED))
        assertFalse(store.toggleThumb(10,entry.generation,"different",ScreeningThumb.LIKED))
        assertNull(store.get(10).thumb)
    }
    @Test fun actualMoviePlaybackCreatesOnlyAnAccentEdgeAndMissingPositionsClearIt() {
        val movie = item("one").copy(traktId = 42)
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(),ranked(movie),10,day)
        val row = com.example.data.local.TraktPlaybackEntity("account","movie:trakt:42","movie",42,null,null,null,null,35.5,null)
        val progress = ScreeningWidgetSelection.withPlayback(entry,movie,"account",listOf(row))
        assertEquals(35.5,progress.pick!!.playbackProgress!!,.00001)
        assertEquals(entry.pick!!.metadata,progress.pick.metadata)
        val bitmap = ScreeningRenderer.render(context,progress,336,264)
        val accent = WidgetAppearance.colors(context).primary.toArgb()
        assertEquals(accent,bitmap.getPixel(80,262)); assertNotEquals(accent,bitmap.getPixel(180,262))
        val cleared = ScreeningWidgetSelection.withPlayback(progress,movie,"account",emptyList())
        assertNull(cleared.pick!!.playbackProgress)
        assertNotEquals(accent,ScreeningRenderer.render(context,cleared,336,264).getPixel(80,262))
        assertNull(ScreeningWidgetSelection.withPlayback(progress,movie,"different",listOf(row)).pick!!.playbackProgress)
        File("build/screening-validation").mkdirs()
        File("build/screening-validation/screening-movie-progress.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun moviePositionSnapshotSurvivesRestartAndHidesAfterAccountReplacement() {
        val auth = com.example.data.remote.trakt.TraktAuthManager(context,
            { com.example.data.remote.trakt.FakeTraktApiService() }, com.example.data.remote.trakt.FakeTraktCredentialStore())
        auth.saveTokens(com.example.data.remote.trakt.TraktTokenResponse(access_token = "test",refresh_token = "test",expires_in = 864000))
        com.example.data.remote.trakt.TraktAuthManager.setInstanceForTesting(auth)
        try {
            val session = auth.playbackSession.value!!
            val store = ScreeningWidgetStore(context)
            val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(),ranked(item("one")),10,day)
                .let { it.copy(pick = it.pick!!.copy(playbackProgress = 35.5,playbackSession = session)) }
            store.save(10,entry)
            assertEquals(entry,ScreeningWidgetStore(context).get(10))
            auth.saveTokens(com.example.data.remote.trakt.TraktTokenResponse(access_token = "other",refresh_token = "other",expires_in = 864000))
            assertNull(store.get(10).pick!!.playbackProgress)
            assertEquals(entry.pick!!.itemId,store.get(10).pick!!.itemId)
            val root = ScreeningWidgetProvider.views(context,10,entry).apply(context,FrameLayout(context))
            val image = root.findViewById<android.widget.ImageView>(R.id.screening_image).drawable as android.graphics.drawable.BitmapDrawable
            assertNotEquals(WidgetAppearance.colors(context).primary.toArgb(),image.bitmap.getPixel(80,262))
        } finally { com.example.data.remote.trakt.TraktAuthManager.setInstanceForTesting(null) }
    }
    @Test fun wideAndShortHostsHaveNoCenteringGapsAndThumbTargetsStay48Dp() {
        val entry = ScreeningWidgetSelection.update(ScreeningWidgetEntry(),ranked(item("one")),10,day)
        for ((w,h) in listOf(250 to 238,336 to 180,420 to 264)) {
            val options = android.os.Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,w);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,h)
            }
            val root = ScreeningWidgetProvider.views(context,10,entry,options).apply(context,FrameLayout(context))
            assertEquals(0,root.paddingLeft); assertEquals(0,root.paddingRight)
            val density = context.resources.displayMetrics.density
            root.measure(View.MeasureSpec.makeMeasureSpec((w*density).toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((h*density).toInt(),View.MeasureSpec.EXACTLY))
            root.layout(0,0,root.measuredWidth,root.measuredHeight)
            for (id in listOf(R.id.screening_like,R.id.screening_dislike)) {
                val target = root.findViewById<View>(id)
                assertEquals((48*density+.5f).toInt(),target.width);assertEquals((48*density+.5f).toInt(),target.height)
            }
        }
    }
}

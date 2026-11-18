package com.example.media.recommendation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScreeningInsightsServiceTest {
    @Test fun insightsAndCorrectionsPreserveArchiveRatingsProgressAndNotes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getDatabase(context).savedItemDao()
        val now = System.currentTimeMillis()
        val saved = SavedItem("taste-service-media", SavedItemType.MEDIA, "Service fixture", "", mediaType = "tv",
            watchStatus = "Watching", userRating = 9, genres = (1..30).map { "Genre $it" },
            showProgress = TvShowProgress(totalAired = 5, totalWatched = 3), timestamp = now - 40 * SCREENING_DAY)
        val note = SavedItem("taste-service-note", SavedItemType.TEXT, "Untouched note", "Private note body")
        val store = ScreeningStore(context); store.resetLearning()
        dao.insertItem(SavedItemEntity.fromDomainModel(saved)); dao.insertItem(SavedItemEntity.fromDomainModel(note))
        try {
            store.observe(listOf(saved), now)
            val key = store.canonicalKey(saved)
            store.toggleReaction(saved.id, key, ScreeningPreference.LIKED, now = now)
            val pick = ScreeningRecommendationEngine.choose(ScreeningRecommendationEngine.rank(listOf(saved), store.snapshot(), now, includeCooldowns = false), "service")!!
            store.markPosted(store.createDelivery(pick, now, "today", false)!!.token, now, pick)
            val beforeMedia = dao.getItemById(saved.id); val beforeNote = dao.getItemById(note.id)
            val beforeLearning = store.learningSnapshot()
            val prefs = context.getSharedPreferences("cobalt_screening_widgets", 0).all.toMap()
            repeat(4) {
                val data = ScreeningService.insights(context)
                assertTrue(data.items.all { it.type == SavedItemType.MEDIA })
                assertEquals(30, data.profile.genres.size)
                assertEquals(5, ScreeningGenres.overview(data.profile.genres).visible.size)
            }
            assertEquals(beforeLearning, store.learningSnapshot()); assertEquals(prefs, context.getSharedPreferences("cobalt_screening_widgets", 0).all)
            assertTrue(ScreeningService.ignoreEvidence(context, "rating:$key", true))
            assertTrue(ScreeningService.insights(context).profile.evidence.single { it.kind == ScreeningEvidenceKind.RATING }.ignored)
            assertTrue(ScreeningService.ignoreEvidence(context, "rating:$key", false))
            ScreeningService.clearReaction(context, key); ScreeningService.resetFeedback(context)
            val reset = ScreeningService.insights(context)
            assertTrue(reset.signals.reactions.isEmpty()); assertTrue(reset.signals.feedback.isEmpty())
            assertTrue(reset.profile.genres.all { it.weight != null && it.weight!! > 0 })
            assertEquals(beforeMedia, dao.getItemById(saved.id)); assertEquals(beforeNote, dao.getItemById(note.id))
        } finally { dao.deleteById(saved.id); dao.deleteById(note.id) }
    }
}

package com.example.media.recommendation

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.TvShowProgress
import com.example.data.local.AppDatabase
import com.example.data.local.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScreeningReadInvalidationTest {
    @Test fun deferredReviewAcceptsAnEnrichedAliasButRejectsAnUnrelatedIdentity() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()
        val item = SavedItem(id = "deferred-alias", type = SavedItemType.MEDIA, title = "Saved movie", content = "", mediaType = "movie",
            traktId = 99, watchStatus = "Plan to Watch", timestamp = now - 30 * SCREENING_DAY)
        val dao = AppDatabase.getDatabase(context).savedItemDao()
        val store = ScreeningStore(context)
        dao.insertItem(item.toEntity())
        val displayed = ScreeningRecommendationEngine.rank(listOf(item), store.observe(listOf(item), now), now, includeCooldowns = false).single()
        val enriched = item.copy(tmdbId = 88)
        dao.insertItem(enriched.toEntity()); store.observe(listOf(enriched), now + 1)
        val delivery = ScreeningService.reviewDelivery(context, displayed, "ALL")!!
        assertEquals(ScreeningRecommendationEngine.key(enriched), delivery.key)
        assertTrue(delivery.posted || store.delivery(delivery.token)!!.posted)
        assertNull(ScreeningService.reviewDelivery(context, displayed.copy(key = "movie:tmdb:123456"), "ALL"))
    }

    @Test fun repeatedRankingDoesNotRewriteProgressButResetAndNewProgressAreObserved() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()
        val item = SavedItem(id = "rank-cache", type = SavedItemType.MEDIA, title = "Watching", content = "", mediaType = "tv",
            watchStatus = "Watching", showProgress = TvShowProgress(totalAired = 10, totalWatched = 2), timestamp = now - 30 * SCREENING_DAY)
        val dao = AppDatabase.getDatabase(context).savedItemDao()
        val store = ScreeningStore(context)
        dao.insertItem(item.toEntity())
        ScreeningService.ranked(context, cooldowns = false)
        val baseline = store.changes.value
        ScreeningService.ranked(context, cooldowns = false)
        assertEquals(baseline, store.changes.value)
        store.resetLearning()
        ScreeningService.ranked(context, cooldowns = false)
        assertEquals(2, store.snapshot().items.getValue(ScreeningRecommendationEngine.key(item)).watched)
        dao.insertItem(item.copy(showProgress = TvShowProgress(totalAired = 10, totalWatched = 3), updatedAt = now + 1).toEntity())
        ScreeningService.ranked(context, cooldowns = false)
        assertEquals(3, store.snapshot().items.getValue(ScreeningRecommendationEngine.key(item)).watched)
    }

    @Test fun consistentReadsAndRepeatedPublicationDoNotInvalidateButRealChangesAndAnswersDo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ScreeningStore(context)
        val now = System.currentTimeMillis()
        val item = SavedItem(id = "shared", type = SavedItemType.MEDIA, title = "Shared title", content = "", mediaType = "movie",
            watchStatus = "Plan to Watch", timestamp = now - 30 * SCREENING_DAY)
        val before = store.changes.value
        store.learningSnapshot(now)
        assertEquals(before, store.changes.value)
        val pick = ScreeningRecommendationEngine.rank(listOf(item), store.observe(listOf(item), now), now, includeCooldowns = false).single()
        assertTrue(store.changes.value > before)
        val delivery = store.createDelivery(pick, now, "today", false)!!
        store.markPosted(delivery.token, now, pick)
        val posted = store.changes.value
        store.markPosted(delivery.token, now, pick)
        val read = store.learningSnapshot(now)
        assertEquals(posted, store.changes.value)
        assertTrue(read.deliveries.getValue(delivery.token).posted)
        assertFalse(read.deliveries.getValue(delivery.token).answered)
        assertTrue(store.answer(delivery.token, item, ScreeningFeedback.USEFUL, now + 1))
        assertTrue(store.changes.value > posted)
        assertTrue(store.learningSnapshot(now + 1).deliveries.getValue(delivery.token).answered)
        val answered = store.changes.value
        assertFalse(store.answer(delivery.token, item, ScreeningFeedback.NOT_INTERESTED, now + 2))
        assertEquals(answered, store.changes.value)
    }
}

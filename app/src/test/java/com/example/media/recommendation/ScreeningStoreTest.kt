package com.example.media.recommendation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ScreeningStoreTest {
    private lateinit var store: ScreeningStore
    private val now = 1_799_999_000_000L
    private fun item(id: String = "test") = SavedItem(id, SavedItemType.MEDIA, id, "", timestamp = now - 40 * SCREENING_DAY,
        mediaType = "tv", watchStatus = "Watching", showProgress = TvShowProgress(totalAired = 5, totalWatched = 2))
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = ScreeningStore(context); store.resetLearning()
        context.openOrCreateDatabase("screening_learning.db", 0, null).use { it.execSQL("DELETE FROM deliveries") }
        context.getSharedPreferences("cobalt_screening", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun pick(i: SavedItem, at: Long = now) = ScreeningRecommendationEngine.rank(listOf(i), store.observe(listOf(i), at), at, includeCooldowns = false).single()
    @Test fun defaultOffAndPreferencesPersist() {
        assertFalse(store.enabled); assertEquals(9, store.hour)
        store.enabled = true; store.hour = 8; store.minute = 30; store.weekends = false
        val next = ScreeningStore(ApplicationProvider.getApplicationContext())
        assertTrue(next.enabled); assertEquals(8, next.hour); assertEquals(30, next.minute); assertFalse(next.weekends)
    }
    @Test fun firstImportNotPaceAndMetadataNotViewing() {
        val i = item(); val key = ScreeningRecommendationEngine.key(i)
        val first = store.observe(listOf(i), now).items.getValue(key)
        val next = store.observe(listOf(i.copy(updatedAt = now + SCREENING_DAY, rating = 8.0)), now + SCREENING_DAY).items.getValue(key)
        assertNull(first.lastProgressAt); assertEquals(first.samples, next.samples); assertEquals(first.watchingSince, next.watchingSince)
    }
    @Test fun realCountUpdatesPersistAndUndoResetsPaceSegment() {
        val i = item(); val key = ScreeningRecommendationEngine.key(i)
        store.observe(listOf(i), now)
        store.observe(listOf(i.copy(showProgress = i.showProgress!!.copy(totalWatched = 3), lastWatchedAt = now + SCREENING_DAY)), now + SCREENING_DAY)
        val next = store.snapshot().items.getValue(key)
        assertEquals(now + SCREENING_DAY, next.lastProgressAt); assertEquals(2, next.samples.size)
        store.observe(listOf(i), now + 2 * SCREENING_DAY)
        assertEquals(1, store.snapshot().items.getValue(key).samples.size)
    }
    @Test fun feedbackFirstAnswerWinsAndPersistsAcrossInstances() {
        val i = item(); val p = pick(i); val d = store.createDelivery(p, now, "today", false)!!
        assertTrue(store.answer(d.token, i, ScreeningFeedback.NOT_INTERESTED, now + 1))
        assertFalse(store.answer(d.token, i, ScreeningFeedback.USEFUL, now + 2))
        val persisted = ScreeningStore(ApplicationProvider.getApplicationContext()).snapshot()
        assertTrue(persisted.items.getValue(p.key).notInterested); assertEquals(1, persisted.feedback.size)
    }
    @Test fun staleDeletedReplacedAndAnsweredTokensCannotTrain() {
        val i = item(); val d = store.createDelivery(pick(i), now, "today", false)!!
        assertFalse(store.answer(d.token, i.copy(isDeleted = true), ScreeningFeedback.USEFUL, now + 1))
        assertFalse(store.answer(d.token, i.copy(tmdbId = 3), ScreeningFeedback.USEFUL, now + 1))
        assertFalse(store.answer(d.token, i, ScreeningFeedback.USEFUL, now + 15 * SCREENING_DAY))
        assertTrue(store.snapshot().feedback.isEmpty())
    }
    @Test fun localIdUpgradeKeepsSuppressionAndLiveToken() {
        val i = item(); val d = store.createDelivery(pick(i), now, "today", false)!!
        val upgraded = i.copy(tmdbId = 88)
        store.observe(listOf(upgraded), now + 1)
        assertEquals(ScreeningRecommendationEngine.key(upgraded), store.delivery(d.token, now + 2)!!.key)
        assertTrue(store.answer(d.token, upgraded, ScreeningFeedback.NOT_NOW, now + 2))
        assertTrue(store.snapshot().items.getValue(ScreeningRecommendationEngine.key(upgraded)).suppressedUntil > now)
    }
    @Test fun providerIdEnrichmentKeepsLiveQuestionAndPreference() {
        val i = item().copy(traktId = 99); val d = store.createDelivery(pick(i), now, "today", false)!!
        val upgraded = i.copy(tmdbId = 88)
        store.observe(listOf(upgraded), now + 1)
        assertEquals(ScreeningRecommendationEngine.key(upgraded), store.delivery(d.token, now + 2)!!.key)
        assertTrue(store.answer(d.token, upgraded, ScreeningFeedback.USEFUL, now + 2))
    }
    @Test fun morningReservationSurvivesCrashBeforePosting() {
        val i = item(); val d = store.createDelivery(pick(i), now, "today", true)!!
        assertEquals(d.token, store.pendingMorning("today", now + 1)!!.token)
        assertNull(store.createDelivery(pick(i), now + 1, "today", true))
        store.markPosted(d.token); assertNull(store.pendingMorning("today", now + 2))
    }
    @Test fun silenceOnlyReducesQuestionFrequencyNotPreferenceOrHistory() {
        val i = item()
        repeat(3) { n -> val at = now + n * SCREENING_DAY; assertNotNull(store.createDelivery(pick(i, at), at, "day$n", true)) }
        assertNull(store.createDelivery(pick(i, now + 3 * SCREENING_DAY), now + 3 * SCREENING_DAY, "day3", true))
        assertNotNull(store.createDelivery(pick(i, now + 5 * SCREENING_DAY), now + 5 * SCREENING_DAY, "day5", true))
        assertTrue(store.snapshot().feedback.isEmpty()); assertFalse(store.snapshot().items.values.single().notInterested)
        assertEquals(2, i.showProgress!!.totalWatched)
    }
    @Test fun resetDoesNotEraseWatchHistoryOrAllowExtraDailyDelivery() {
        val i = item(); val d = store.createDelivery(pick(i), now, "today", true)!!
        assertTrue(store.answer(d.token, i, ScreeningFeedback.PAUSED, now + 1))
        store.resetLearning()
        assertTrue(store.snapshot().feedback.isEmpty()); assertTrue(store.snapshot().items.isEmpty())
        assertNull(store.createDelivery(pick(i), now + 2, "today", true))
        assertFalse(store.answer(d.token, i, ScreeningFeedback.USEFUL, now + 3)); assertEquals("Watching", i.watchStatus)
    }
}

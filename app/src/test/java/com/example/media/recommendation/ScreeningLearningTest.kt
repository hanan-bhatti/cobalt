package com.example.media.recommendation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.widget.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ScreeningLearningTest {
    private lateinit var context: Context
    private lateinit var store: ScreeningStore
    private val now = 1_799_999_000_000L
    private val tables = listOf("signals", "feedback", "deliveries", "taste_aliases", "taste_titles", "taste_reactions",
        "taste_imports", "taste_ignored", "taste_history", "taste_publications", "taste_surface_heads")
    private fun item(id: String = "saved", rating: Int? = null) = SavedItem(id = id, type = SavedItemType.MEDIA,
        title = "Saved $id", content = "", mediaType = "movie", genres = listOf("Drama"), userRating = rating,
        watchStatus = "Plan to Watch", timestamp = now - 45 * SCREENING_DAY, updatedAt = now)
    private fun pick(saved: SavedItem, at: Long = now) = ScreeningRecommendationEngine.choose(
        ScreeningRecommendationEngine.rank(listOf(saved), store.observe(listOf(saved), at), at, includeCooldowns = false), "test")!!
    private fun profile(saved: SavedItem, at: Long = now) = ScreeningTaste.profile(listOf(saved), store.snapshot(at), at)
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cobalt_screening_widgets", 0).edit().clear().commit()
        store = ScreeningStore(context); store.resetLearning()
        context.openOrCreateDatabase("screening_learning.db", 0, null).use { db -> tables.forEach { db.delete(it, null, null) } }
    }
    private fun legacy(saved: SavedItem, value: ScreeningPreference, at: Long) {
        val raw = JSONObject().put("key", ScreeningRecommendationEngine.key(saved)).put("value", value.name).put("at", at).toString()
        context.getSharedPreferences("cobalt_screening_widgets", 0).edit().putString("thumb_${saved.id}", raw).commit()
    }
    private fun dump(): Map<String, List<String>> = context.openOrCreateDatabase("screening_learning.db", 0, null).use { db ->
        tables.associateWith { table -> db.rawQuery("SELECT * FROM $table", null).use { c -> buildList {
            while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { if (c.isNull(it)) "<null>" else c.getString(it) })
        }.sorted() } }
    }

    @Test fun legacyDuplicateThumbsUseLatestStateAndMigrateOnlyOnce() {
        val a = item("a").copy(tmdbId = 42); val b = item("b").copy(tmdbId = 42)
        legacy(a, ScreeningPreference.LIKED, now - 2); legacy(b, ScreeningPreference.DISLIKED, now - 1)
        val signals = store.observe(listOf(a, b), now)
        assertEquals(1, signals.reactions.size); assertEquals(ScreeningPreference.DISLIKED, signals.reactions.values.single().value)
        assertEquals(1, ScreeningTaste.profile(listOf(a, b), signals, now).titleCount)
        val persisted = ScreeningStore(context).observe(listOf(a, b), now + 1)
        assertEquals(signals.reactions, persisted.reactions)
        assertEquals(ScreeningThumb.DISLIKED, ScreeningWidgetStore(context).thumb(a.id))
        assertEquals(ScreeningThumb.DISLIKED, ScreeningWidgetStore(context).thumb(b.id))
    }

    @Test fun clearBeforeMigrationCannotResurrectLegacyThumb() {
        val saved = item(); val key = ScreeningRecommendationEngine.key(saved)
        legacy(saved, ScreeningPreference.LIKED, now - 1)
        assertEquals(ScreeningPreference.LIKED, store.snapshot(now).reactions.getValue(key).value)
        store.clearReaction(key, now)
        assertEquals(0, profile(saved).titleCount)
        assertNull(ScreeningStore(context).snapshot(now).reactions.getValue(key).value)
        store.observe(listOf(saved), now + 1)
        assertEquals(0, profile(saved, now + 1).titleCount)
    }

    @Test fun switchAndClearReplaceOnePreferenceAcrossCopiesAndRestart() {
        val a = item("a").copy(tmdbId = 42); val b = item("b").copy(tmdbId = 42)
        store.observe(listOf(a, b), now)
        val key = store.canonicalKey(a)
        store.toggleReaction(a.id, key, ScreeningPreference.LIKED, "Widget", now)
        assertTrue(profile(a).genres.single().weight!! > 0)
        assertEquals(ScreeningThumb.LIKED, ScreeningWidgetStore(context).thumb(b.id))
        ScreeningStore(context).toggleReaction(b.id, key, ScreeningPreference.DISLIKED, "Review screen", now + 1)
        assertEquals(1, profile(a, now + 1).titleCount); assertTrue(profile(a, now + 1).genres.single().weight!! < 0)
        ScreeningStore(context).toggleReaction(a.id, key, ScreeningPreference.DISLIKED, "Widget", now + 2)
        assertNull(profile(a, now + 2).genres.single().weight)
        assertNull(ScreeningWidgetStore(context).thumb(b.id))
    }

    @Test fun importedRatingsAreUndatedAndMetadataDoesNotCreateRatingEvents() {
        val saved = item(rating = 7); val key = ScreeningRecommendationEngine.key(saved)
        store.observe(listOf(saved), now)
        assertNull(profile(saved).evidence.single().at)
        val enriched = saved.copy(updatedAt = now + SCREENING_DAY, rating = 9.0, genres = listOf("Drama", "Crime"))
        store.observe(listOf(enriched), now + SCREENING_DAY)
        assertNull(profile(enriched, now + SCREENING_DAY).evidence.single().at)
        assertTrue(store.history().isEmpty())
        val changed = enriched.copy(userRating = 9)
        store.observe(listOf(changed), now + 2 * SCREENING_DAY)
        assertEquals(now + 2 * SCREENING_DAY, store.snapshot(now + 2 * SCREENING_DAY).ratings.getValue(key).at)
        assertEquals(now + 2 * SCREENING_DAY, profile(changed, now + 2 * SCREENING_DAY).evidence.single().at)
        assertEquals("RATING_OBSERVED", store.history().single().action)
    }

    @Test fun ignoredRatingSurvivesAliasEnrichmentAndCanBeRestored() {
        val saved = item(rating = 10); val key = ScreeningRecommendationEngine.key(saved)
        store.observe(listOf(saved), now); store.ignoreEvidence("rating:$key", true, now + 1)
        assertNull(profile(saved, now + 1).genres.single().weight)
        val upgraded = saved.copy(tmdbId = 42, traktId = 8)
        val signals = ScreeningStore(context).observe(listOf(upgraded), now + 2)
        val canonical = ScreeningRecommendationEngine.key(upgraded)
        assertTrue("rating:$canonical" in signals.ignoredEvidence)
        assertNull(profile(upgraded, now + 2).genres.single().weight)
        store.ignoreEvidence("rating:$key", false, now + 3)
        assertTrue(profile(upgraded, now + 3).genres.single().weight!! > 0)
        assertEquals(10, upgraded.userRating); assertNull(profile(upgraded, now + 3).evidence.single().at)
    }

    @Test fun ignoringAndRestoringNotInterestedUpdatesItsActualEligibility() {
        val saved = item(); val p = pick(saved); val d = store.createDelivery(p, now, "today", false)!!
        assertTrue(store.answer(d.token, saved, ScreeningFeedback.NOT_INTERESTED, now + 1))
        val event = store.snapshot(now + 1).feedback.single()
        store.ignoreEvidence(event.id, true, now + 2)
        val cleared = store.snapshot(now + 2).items.getValue(p.key)
        assertFalse(cleared.notInterested); assertEquals(0, cleared.suppressedUntil)
        assertTrue(ScreeningRecommendationEngine.eligibility(saved, cleared, now + 2).eligible)
        store.ignoreEvidence(event.id, false, now + 3)
        assertTrue(store.snapshot(now + 3).items.getValue(p.key).notInterested)
    }

    @Test fun recommendAgainInvalidatesOutstandingQuestionsAndPreservesArchiveFacts() {
        val saved = item(rating = 8); val p = pick(saved)
        val old = store.createDelivery(p, now, "day1", false)!!
        val other = store.createDelivery(p, now + 1, "day2", false)!!
        store.answer(old.token, saved, ScreeningFeedback.NOT_INTERESTED, now + 2)
        store.recommendAgain(p.key, now + 3)
        assertFalse(store.answer(other.token, saved, ScreeningFeedback.NOT_INTERESTED, now + 4))
        assertTrue(ScreeningRecommendationEngine.eligibility(saved, store.snapshot(now + 4).items.getValue(p.key), now + 4).eligible)
        assertEquals(8, saved.userRating); assertEquals("Plan to Watch", saved.watchStatus)
    }

    @Test fun resetCoversLegacyAndCurrentFeedbackButSavedRatingsStillTrain() {
        val saved = item(rating = 10); legacy(saved, ScreeningPreference.DISLIKED, now - 1)
        val p = pick(saved); val old = store.createDelivery(p, now, "day1", false)!!
        store.answer(old.token, saved, ScreeningFeedback.NOT_INTERESTED, now + 1)
        val pending = store.createDelivery(p, now + 2, "day2", false)!!
        store.ignoreEvidence("rating:${p.key}", true, now + 2)
        store.resetLearning()
        val signals = ScreeningStore(context).snapshot(now + 3)
        assertTrue(signals.feedback.isEmpty()); assertTrue(signals.reactions.isEmpty()); assertTrue(signals.ignoredEvidence.isEmpty())
        assertFalse(store.answer(pending.token, saved, ScreeningFeedback.NOT_INTERESTED, now + 3))
        assertEquals(10, saved.userRating); assertTrue(profile(saved, now + 3).genres.single().weight!! > 0)
        assertFalse(context.getSharedPreferences("cobalt_screening_widgets", 0).contains("thumb_${saved.id}"))
    }

    @Test fun reservingQuestionsDoesNotAddFatigueAndSuccessfulPublicationCountsOnce() {
        val saved = item(); val p = pick(saved); val d = store.createDelivery(p, now, "today", true)!!
        assertEquals(0, store.snapshot(now).items.getValue(p.key).shownCount)
        assertTrue(store.publications().isEmpty())
        store.markPosted(d.token, now + 1, p)
        assertEquals(1, store.snapshot(now + 1).items.getValue(p.key).shownCount)
        store.markPosted(d.token, now + 2, p)
        assertEquals(1, store.snapshot(now + 2).items.getValue(p.key).shownCount)
        val publication = store.publications().single()
        assertEquals(now + 1, publication.at); assertEquals(ScreeningScoreTrace.from(p), publication.trace)
    }

    @Test fun reopeningSameReviewReusesItsQuestionAndPublication() {
        val saved = item(); val p = pick(saved)
        val first = store.createDelivery(p, now, "today", false)!!
        store.markPosted(first.token, now + 1, p)
        val reopened = store.createDelivery(p, now + 2, "today", false)!!
        assertEquals(first.token, reopened.token); assertTrue(reopened.posted)
        store.markPosted(reopened.token, now + 3, p)
        assertEquals(1, store.snapshot(now + 3).items.getValue(p.key).shownCount)
        assertEquals(1, store.publications().size)
    }

    @Test fun retainedCardKeepsOriginalTraceAndAliasEnrichmentDoesNotAdvanceGeneration() {
        val saved = item(); val firstPick = pick(saved)
        val first = ScreeningWidgetSelection.update(ScreeningWidgetEntry(), listOf(firstPick), 77, "today")
        val upgraded = saved.copy(tmdbId = 42)
        val newPick = pick(upgraded, now + 1)
        val retained = ScreeningWidgetSelection.update(first, listOf(newPick), 77, "today")
        assertEquals(first.generation, retained.generation)
        assertEquals(first.pick!!.trace, retained.pick!!.trace); assertEquals(first.pick.reason, retained.pick.reason)
        val id = "widget:77:today:${first.generation}"
        fun publication(entry: ScreeningWidgetEntry, at: Long) = ScreeningDecisionSnapshot(id, "Widget 77", "ALL",
            entry.pick!!.key, entry.pick.itemId, entry.pick.title, at, entry.pick.trace)
        assertTrue(store.publish(publication(first, now))); assertFalse(store.publish(publication(retained, now + 1)))
        assertEquals(1, store.snapshot(now + 1).items.getValue(newPick.key).shownCount)
        assertEquals(first.pick.trace, store.publications().single().trace)
    }

    @Test fun repeatedInsightsReadsDoNotWriteLearningOrChangeTheSuggestion() {
        val saved = item(rating = 9); val p = pick(saved)
        store.markPosted(store.createDelivery(p, now, "today", false)!!.token, now + 1, p)
        val before = dump(); val learning = store.learningSnapshot(now + 2)
        repeat(8) {
            assertEquals(learning, store.learningSnapshot(now + 2))
            val profile = ScreeningTaste.profile(listOf(saved), learning.signals, now + 2)
            ScreeningRecommendationEngine.rank(listOf(saved), learning.signals, now + 2, includeCooldowns = false, profile = profile)
        }
        assertEquals(before, dump())
    }

    @Test fun expiredResponsesAreNotResurrectedByReadOnlyInsights() {
        val saved = item(); val p = pick(saved)
        store.answer(store.createDelivery(p, now, "today", false)!!.token, saved, ScreeningFeedback.USEFUL, now + 1)
        val before = dump(); val after = store.learningSnapshot(now + 181 * SCREENING_DAY)
        assertTrue(after.signals.feedback.isEmpty())
        assertEquals(0, ScreeningTaste.profile(listOf(saved), after.signals, now + 181 * SCREENING_DAY).titleCount)
        assertEquals(before, dump())
    }

    @Test fun serializedTracesReconcileAndRejectCorruptedTotals() {
        val saved = item(rating = 9); val chosen = pick(saved); val trace = ScreeningScoreTrace.from(chosen)
        assertEquals(trace, ScreeningDecisionCodec.decode(JSONObject(ScreeningDecisionCodec.encode(trace).toString())))
        assertNull(ScreeningDecisionCodec.decode(ScreeningDecisionCodec.encode(trace).put("raw", trace.rawPoints + 1)))
        assertNull(ScreeningDecisionCodec.decode(ScreeningDecisionCodec.encode(trace).put("utility", trace.selectionPoints + 1)))
    }

    @Test fun prunedResponseIdsCannotExcludeNewFeedbackWhenSQLiteReusesThem() {
        val saved = item(); val first = pick(saved)
        store.answer(store.createDelivery(first, now, "old", false)!!.token, saved, ScreeningFeedback.USEFUL, now + 1)
        store.ignoreEvidence(store.snapshot(now + 1).feedback.single().id, true, now + 2)
        val later = now + 181 * SCREENING_DAY
        val next = pick(saved, later)
        assertTrue(store.snapshot(later).ignoredEvidence.isEmpty())
        store.answer(store.createDelivery(next, later, "new", false)!!.token, saved, ScreeningFeedback.USEFUL, later + 1)
        assertTrue(profile(saved, later + 1).genres.single().weight!! > 0)
    }

    @Test fun addingLearningSchemaPreservesLegacyRecordsAndIsSafeToRepeat() {
        android.database.sqlite.SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE signals (k TEXT PRIMARY KEY, item_id TEXT NOT NULL, data TEXT NOT NULL, at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE feedback (k TEXT NOT NULL, kind TEXT NOT NULL, at INTEGER NOT NULL, genres TEXT NOT NULL)")
            db.execSQL("CREATE TABLE deliveries (token TEXT PRIMARY KEY, k TEXT NOT NULL, item_id TEXT NOT NULL, at INTEGER NOT NULL, answered INTEGER NOT NULL, posted INTEGER NOT NULL DEFAULT 0, day TEXT NOT NULL, daily INTEGER NOT NULL)")
            db.execSQL("INSERT INTO feedback VALUES ('movie:local:old', 'USEFUL', ?, '[\"Drama\"]')", arrayOf(now))
            db.execSQL("INSERT INTO deliveries VALUES ('old-token', 'movie:local:old', 'old', ?, 0, 1, 'old-day', 1)", arrayOf(now))
            ScreeningLearningRecords.install(db); ScreeningLearningRecords.install(db)
            db.rawQuery("SELECT k, origin FROM feedback", null).use { c -> assertTrue(c.moveToFirst()); assertEquals("movie:local:old", c.getString(0)); assertTrue(c.isNull(1)) }
            db.rawQuery("SELECT token, posted, trace, published_at FROM deliveries", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("old-token", c.getString(0)); assertEquals(1, c.getInt(1)); assertTrue(c.isNull(2)); assertTrue(c.isNull(3))
            }
            assertTrue(ScreeningLearningRecords(db).augment(ScreeningSignals(), emptyMap<String, String>(), now).reactions.isEmpty())
        }
    }
}

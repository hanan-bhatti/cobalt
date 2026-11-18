package com.example.media.recommendation

import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ScreeningRecommendationEngineTest {
    private val now = Instant.parse("2026-11-12T04:00:00Z").toEpochMilli()
    private fun item(id: String = "one", status: String? = "Plan to Watch", watched: Int = 0, aired: Int = 5) = SavedItem(
        id = id, type = SavedItemType.MEDIA, title = id, content = "", mediaType = "tv", watchStatus = status,
        timestamp = now - 45 * SCREENING_DAY, showProgress = TvShowProgress(totalWatched = watched, totalAired = aired))
    private fun rank(vararg items: SavedItem, signals: ScreeningSignals = ScreeningSignals()) = ScreeningRecommendationEngine.rank(items.toList(), signals, now, includeCooldowns = false)
    @Test fun fiveEpisodesAndNineDaysAskNeverComplete() {
        val i = item(status = "Watching", watched = 3).copy(lastWatchedAt = now - 9 * SCREENING_DAY)
        val pick = rank(i).single()
        assertTrue(pick.question.contains("Still watching")); assertEquals(2, pick.progress.remaining)
        assertEquals("Watching", i.watchStatus); assertEquals(3, i.showProgress!!.totalWatched)
    }
    @Test fun unknownTotalsStayUnknown() {
        val p = rank(item(status = "Watching", watched = 3, aired = 0)).single()
        assertNull(p.progress.remaining); assertNull(p.progress.paceDaysPerEpisode); assertTrue(p.reason.contains("unknown"))
    }
    @Test fun countContradictionsCannotClaimAlmostFinished() {
        val p = rank(item(status = "Watching", watched = 9, aired = 5)).single()
        assertTrue(p.progress.inconsistent); assertNull(p.progress.remaining); assertFalse(p.reason.contains("left"))
    }
    @Test fun terminalStatusesAndOverridesExcluded() {
        assertTrue(rank(item(status = "Completed"), item("two", "Dropped"), item("three", "Paused"), item("four", "Watching").copy(watchStatusOverride = "Completed")).isEmpty())
    }
    @Test fun caughtUpOngoingShowWaitsForReleasedEpisodes() {
        assertTrue(rank(item(status = "Watching", watched = 5).copy(status = "Returning Series")).isEmpty())
        assertEquals(1, rank(item(status = "Watching", watched = 5, aired = 6)).size)
    }
    @Test fun futureEpisodesAndPremiereNotPushed() {
        val ep = TvEpisodeProgress(1, 1, airDate = "2027-01-01")
        val i = item().copy(showProgress = TvShowProgress(totalAired = 1, seasons = listOf(TvSeasonProgress(1, episodes = listOf(ep)))))
        assertTrue(rank(i).isEmpty())
        assertTrue(rank(item().copy(showProgress = TvShowProgress(premiered = "2027-01-01"))).isEmpty())
    }
    @Test fun newlyReleasedEpisodeRecordsCorrectAStaleLowerAiredCount() {
        val eps = (1..6).map { TvEpisodeProgress(it, 1, isWatched = it <= 5, airDate = "2026-11-01") }
        val i = item(status = "Watching", watched = 5, aired = 5).copy(showProgress = TvShowProgress(totalAired = 5, totalWatched = 5, seasons = listOf(TvSeasonProgress(1, episodes = eps))))
        assertEquals(1, rank(i).single().progress.remaining)
    }
    @Test fun missingAirDatesDoNotInventReleasedTotals() {
        val i = item(aired = 0).copy(showProgress = TvShowProgress(seasons = listOf(TvSeasonProgress(1, totalEpisodes = 5, episodes = listOf(TvEpisodeProgress(1, 1))))))
        assertNull(rank(i).single().progress.aired)
    }
    @Test fun futurePartialMetadataDoesNotEraseKnownRelease() {
        val i = item(aired = 10).copy(showProgress = TvShowProgress(totalAired = 10, seasons = listOf(TvSeasonProgress(1, episodes = listOf(TvEpisodeProgress(11, 1, airDate = "2027-01-01"))))))
        assertEquals(1, rank(i).size)
    }
    @Test fun specialsAndDuplicateEpisodesDoNotInflateProgress() {
        val i = item(aired = 4).copy(showProgress = TvShowProgress(totalAired = 4, seasons = listOf(
            TvSeasonProgress(0, watchedEpisodes = 8, episodes = listOf(TvEpisodeProgress(1, 0, isWatched = true))),
            TvSeasonProgress(1, watchedEpisodes = 1, episodes = listOf(TvEpisodeProgress(1, 1), TvEpisodeProgress(1, 1, isWatched = true))),
            TvSeasonProgress(1, watchedEpisodes = 1))))
        assertEquals(1, rank(i).single().progress.watched)
    }
    @Test fun moviesNeverShowEpisodeLanguageAndWatchedMoviesExcluded() {
        val i = item(status = "Watching").copy(mediaType = "movie", showProgress = null)
        assertFalse(rank(i).single().reason.contains("episode"))
        assertTrue(rank(i.copy(watchStatus = "Plan to Watch", lastWatchedAt = now - SCREENING_DAY)).isEmpty())
        assertEquals(1, rank(i.copy(watchStatusOverride = "Watching", lastWatchedAt = now - SCREENING_DAY)).size)
    }
    @Test fun unsupportedAndInactiveMediaExcluded() {
        assertTrue(rank(item().copy(mediaType = "manga"), item().copy(isDeleted = true), item().copy(isArchived = true), item().copy(isUnavailable = true)).isEmpty())
    }
    @Test fun duplicateProviderIdsNamespacedByKind() {
        val i = item().copy(tmdbId = 42)
        val picks = rank(i, i.copy(id = "copy"), i.copy(id = "movie", mediaType = "movie", showProgress = null))
        assertEquals(2, picks.size); assertEquals(2, picks.map { it.key }.distinct().size)
    }
    @Test fun partiallyLinkedDuplicatesShareOneRecommendationAndFeedback() {
        val a = item("a").copy(tmdbId = 42, traktId = 10)
        val b = item("b").copy(traktId = 10)
        assertEquals(1, rank(a, b).size)
        val signals = ScreeningSignals(mapOf(ScreeningRecommendationEngine.key(b) to ScreeningItemSignal(notInterested = true)))
        assertTrue(rank(a, b, signals = signals).isEmpty())
    }
    @Test fun explicitCompletedDuplicateWinsOverEnrichment() {
        val done = item().copy(tmdbId = 42, watchStatusOverride = "Completed")
        assertTrue(rank(done, item("copy").copy(tmdbId = 42, updatedAt = now + 1)).isEmpty())
    }
    @Test fun pauseAndNotInterestedSuppressButSilenceIsNeutral() {
        val i = item(); val k = ScreeningRecommendationEngine.key(i)
        assertTrue(rank(i, signals = ScreeningSignals(mapOf(k to ScreeningItemSignal(suppressedUntil = now + SCREENING_DAY)))).isEmpty())
        assertTrue(rank(i, signals = ScreeningSignals(mapOf(k to ScreeningItemSignal(notInterested = true)))).isEmpty())
        val silent = rank(i, signals = ScreeningSignals(mapOf(k to ScreeningItemSignal(shownCount = 4, lastShownAt = now - 9 * SCREENING_DAY)))).single()
        assertEquals(0.0, silent.features.getValue("preference"), .0001); assertTrue(silent.score > 0)
    }
    @Test fun cooldownAppliedInMorningAndIgnoredOnlyInPreview() {
        val i = item(); val signals = ScreeningSignals(mapOf(ScreeningRecommendationEngine.key(i) to ScreeningItemSignal(lastShownAt = now - SCREENING_DAY)))
        assertTrue(ScreeningRecommendationEngine.rank(listOf(i), signals, now).isEmpty())
        assertEquals(1, rank(i, signals = signals).size)
    }
    @Test fun explicitFeedbackLearnsGenreWithDecayAndSparseSmoothing() {
        val i = item().copy(genres = listOf("Drama")); val k = ScreeningRecommendationEngine.key(i)
        fun score(kind: ScreeningFeedback, at: Long) = rank(i, signals = ScreeningSignals(feedback = listOf(ScreeningFeedbackEvent(k, kind, at, i.genres)))).single().score
        assertTrue(score(ScreeningFeedback.USEFUL, now) > score(ScreeningFeedback.NOT_INTERESTED, now))
        assertTrue(score(ScreeningFeedback.USEFUL, now) > score(ScreeningFeedback.USEFUL, now - 120 * SCREENING_DAY))
        assertTrue(score(ScreeningFeedback.NOT_INTERESTED, now) > 0)
    }
    @Test fun realPaceNeedsTwoIntervals() {
        val i = item(status = "Watching", watched = 3)
        fun progress(samples: List<ProgressSample>) = ScreeningRecommendationEngine.progress(i, ScreeningItemSignal(samples = samples), now)
        val samples = listOf(ProgressSample(now - 6 * SCREENING_DAY, 1), ProgressSample(now - 4 * SCREENING_DAY, 2), ProgressSample(now - 2 * SCREENING_DAY, 3))
        assertNull(progress(samples.take(2)).paceDaysPerEpisode); assertEquals(2.0, progress(samples).paceDaysPerEpisode!!, .01)
    }
    @Test fun shuffleStableVariedAndNextDoesNotRepeatOrDislike() {
        val ranked = rank(*(1..8).map { item("$it") }.toTypedArray())
        assertEquals(ScreeningRecommendationEngine.choose(ranked, "same"), ScreeningRecommendationEngine.choose(ranked, "same"))
        assertTrue((1..100).map { ScreeningRecommendationEngine.choose(ranked, "seed:$it")!!.key }.distinct().size >= 6)
        val used = mutableSetOf<String>()
        repeat(8) { val next = ScreeningRecommendationEngine.choose(ranked, "next:$it", used)!!; assertTrue(used.add(next.key)) }
        assertNull(ScreeningRecommendationEngine.choose(ranked, "done", used))
    }
}

package com.example.media.recommendation

import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import org.junit.Assert.*
import org.junit.Test

class ScreeningTasteTest {
    private val now = 1_799_999_000_000L
    private fun item(id: String, genres: List<String> = listOf("Drama"), rating: Int? = null) = SavedItem(
        id = id, type = SavedItemType.MEDIA, title = id, content = "", mediaType = "movie",
        watchStatus = "Plan to Watch", genres = genres, userRating = rating, timestamp = now - 45 * SCREENING_DAY, updatedAt = now)
    private fun profile(items: List<SavedItem>, signals: ScreeningSignals = ScreeningSignals()) = ScreeningTaste.profile(items, signals, now)

    @Test fun sixtyGenresKeepTheirLearningAndOverviewOnlyGroupsThePresentation() {
        val all = (1..60).map { "Genre $it" }
        val learned = profile(listOf(item("rated", all, 10)))
        val overview = ScreeningGenres.overview(learned.genres)
        assertEquals(60, learned.genres.size)
        assertEquals(5, overview.visible.size); assertEquals(55, overview.others.size)
        assertEquals(learned.genres.map { it.key }.toSet(), (overview.visible + overview.others).map { it.key }.toSet())
        assertTrue(learned.genres.all { it.weight != null && it.titleCount == 1 })
        assertTrue(learned.affinity(listOf(all.last())) > 0)
        val pick = ScreeningRecommendationEngine.rank(listOf(item("rated", all, 10), item("last", listOf(all.last()))), now = now)
            .first { it.item.id == "last" }
        assertTrue(pick.features.getValue("preference") > 0)
    }

    @Test fun smallVocabularyNeedsNoOthersAndBothDirectionsRemainVisible() {
        val learned = profile(listOf(item("positive", listOf("Drama"), 10), item("negative", listOf("Horror"), 1)))
        val overview = ScreeningGenres.overview(learned.genres)
        assertTrue(overview.others.isEmpty())
        assertEquals(setOf("More often", "Less often"), overview.visible.map { it.direction }.toSet())
    }

    @Test fun linkedDuplicateRatingsContributeOnceAcrossProviderAliases() {
        val a = item("a", rating = 10).copy(tmdbId = 42, traktId = 8, updatedAt = now - 1)
        val b = item("b", rating = 7).copy(traktId = 8)
        val duplicate = profile(listOf(a, b, a.copy(id = "c")))
        val single = profile(listOf(b.copy(tmdbId = 42)))
        assertEquals(1, duplicate.titleCount); assertEquals(1, duplicate.evidence.size)
        assertEquals(single.genres.single().weight, duplicate.genres.single().weight)
        assertEquals("Your rating 7/10", duplicate.evidence.single().value)
        assertEquals(1, ScreeningRecommendationEngine.rank(listOf(a, b), now = now).size)
    }

    @Test fun savedAliasesStillJoinCopiesWhenProviderMetadataIsNoLongerPresent() {
        val a = item("a", rating = 9); val b = item("b", rating = 9)
        val canonical = "movie:tmdb:42"
        val signals = ScreeningSignals(aliases = mapOf("movie:local:a" to canonical, "movie:local:b" to canonical))
        assertEquals(1, profile(listOf(a, b), signals).titleCount)
        assertEquals(canonical, ScreeningIdentity.resolve(listOf(a, b), signals.aliases).single().key)
    }

    @Test fun movieAndShowProviderIdsDoNotShareAnIdentity() {
        val movie = item("movie", rating = 10).copy(tmdbId = 42)
        val show = item("show", listOf("Horror"), 1).copy(mediaType = "tv", tmdbId = 42)
        assertEquals(2, profile(listOf(movie, show)).titleCount)
    }

    @Test fun genreSpellingAliasesDoNotBecomeIndependentEvidence() {
        val learned = profile(listOf(item("science", listOf(" Sci-Fi ", "SCI FI", "Science Fiction", "  "), 10)))
        assertEquals(listOf("science fiction"), learned.genres.map { it.key })
        assertEquals(1, learned.genres.single().titleCount)
    }

    @Test fun noEvidenceAndConflictingEvidenceHaveDifferentStates() {
        val learned = profile(listOf(item("unknown", listOf("Mystery")), item("up", rating = 10), item("down", rating = 1)))
        val unknown = learned.genres.first { it.key == "mystery" }; val balanced = learned.genres.first { it.key == "drama" }
        assertNull(unknown.weight); assertEquals("No feedback yet", unknown.direction); assertEquals(0, unknown.titleCount)
        assertEquals(0.0, balanced.weight!!, .000001); assertEquals("Balanced feedback", balanced.direction); assertEquals(2, balanced.titleCount)
    }

    @Test fun latestReactionReplacesPriorAliasesAndClearRemovesItsInfluence() {
        val saved = item("saved").copy(tmdbId = 42)
        val local = "movie:local:saved"; val key = ScreeningRecommendationEngine.key(saved)
        val old = ScreeningReaction(local, ScreeningPreference.LIKED, now - 2)
        val switched = ScreeningReaction(key, ScreeningPreference.DISLIKED, now - 1)
        fun taste(latest: ScreeningReaction) = profile(listOf(saved), ScreeningSignals(reactions = mapOf(local to old, key to latest)))
        assertEquals(1, taste(switched).titleCount); assertEquals(1, taste(switched).evidence.size)
        assertTrue(taste(switched).genres.single().weight!! < 0)
        assertEquals(0, taste(switched.copy(value = null, at = now)).titleCount)
        assertNull(taste(switched.copy(value = null, at = now)).genres.single().weight)
    }

    @Test fun repeatedQuestionResponsesDoNotOverpowerDistinctTitles() {
        val saved = item("saved"); val key = ScreeningRecommendationEngine.key(saved)
        val responses = (1..40).map { ScreeningFeedbackEvent(key, ScreeningFeedback.USEFUL, now - 40 + it, saved.genres, id = "response:$it") }
        val repeated = profile(listOf(saved), ScreeningSignals(feedback = responses))
        val single = profile(listOf(saved), ScreeningSignals(feedback = responses.takeLast(1)))
        assertEquals(single.genres.single().weight, repeated.genres.single().weight)
        assertEquals(1, repeated.evidence.count { it.contributes }); assertEquals(1, repeated.titleCount)
        val ignored = profile(listOf(saved), ScreeningSignals(feedback = responses, ignoredEvidence = setOf(responses.last().id)))
        assertEquals(responses[responses.lastIndex - 1].id, ignored.evidence.single { it.contributes }.id)
    }

    @Test fun ratingAndThumbAreTwoSignalsAboutOneTitleAndIgnoredRatingDoesNotEditIt() {
        val saved = item("saved", rating = 10); val key = ScreeningRecommendationEngine.key(saved)
        val signals = ScreeningSignals(reactions = mapOf(key to ScreeningReaction(key, ScreeningPreference.DISLIKED, now)))
        val learned = profile(listOf(saved), signals)
        assertEquals(2, learned.evidence.size); assertEquals(1, learned.titleCount); assertEquals(1, learned.genres.single().titleCount)
        val ignored = profile(listOf(saved), signals.copy(ignoredEvidence = setOf("rating:$key")))
        assertTrue(ignored.genres.single().weight!! < learned.genres.single().weight!!)
        assertEquals(10, saved.userRating)
    }

    @Test fun globalRatingsAndSilenceAddNoPersonalPreferenceAndCompletionIsWeak() {
        val saved = item("saved").copy(rating = 9.8)
        val key = ScreeningRecommendationEngine.key(saved)
        val silent = ScreeningSignals(items = mapOf(key to ScreeningItemSignal(shownCount = 50, lastShownAt = now)))
        assertEquals(0, profile(listOf(saved), silent).titleCount)
        val completion = profile(listOf(saved.copy(watchStatus = "Completed")))
        val rating = profile(listOf(saved.copy(userRating = 10)))
        assertTrue(completion.genres.single().weight!! in 0.0..rating.genres.single().weight!!)
        assertNull(completion.evidence.single().at)
    }

    @Test fun oldExplicitPreferencesHaveLessInfluenceAndFutureEventsAddNone() {
        val saved = item("saved"); val key = ScreeningRecommendationEngine.key(saved)
        fun taste(at: Long) = profile(listOf(saved), ScreeningSignals(reactions = mapOf(key to ScreeningReaction(key, ScreeningPreference.LIKED, at))))
        assertTrue(taste(now).genres.single().weight!! > taste(now - 120 * SCREENING_DAY).genres.single().weight!!)
        assertNull(taste(now + 1).genres.single().weight)
    }

    @Test fun selectionWeightsUseVarietyAndRecomputeAfterExclusions() {
        val library = (1..8).map { item("title$it") }
        val ranked = ScreeningRecommendationEngine.rank(library, now = now, includeCooldowns = false)
        val chosen = ScreeningRecommendationEngine.choose(ranked, "variety")!!
        assertEquals(8, chosen.shortlist.size); assertEquals(8, chosen.eligiblePoolSize)
        assertTrue(ranked.any { it.diversityAdjustment < 0 })
        for (weight in chosen.shortlist) {
            val candidate = ranked.single { it.key == weight.key }
            assertEquals(maxOf(1.0, candidate.selectionScore), weight.weight, .000001)
        }
        assertEquals(chosen.features.values.sum(), chosen.score, .000001)
        assertEquals(chosen.score + chosen.diversityAdjustment, chosen.selectionScore, .000001)
        val remaining = ranked.last()
        val alone = ScreeningRecommendationEngine.choose(ranked, "alone", ranked.filter { it.key != remaining.key }.map { it.key }.toSet())!!
        assertEquals(0.0, alone.diversityAdjustment, .000001)
        assertEquals(alone.score, alone.shortlist.single().weight, .000001); assertEquals(1, alone.eligiblePoolSize)
    }

    @Test fun sourceFilteringNeverSamplesAnOutsideTitle() {
        val library = (1..12).map { item("movie$it") } + (1..12).map { item("show$it").copy(mediaType = "tv") }
        val source = ScreeningRecommendationEngine.rank(library, now = now).filter { ScreeningRecommendationEngine.kind(it.item) == "movie" }
        repeat(25) {
            val chosen = ScreeningRecommendationEngine.choose(source, "movie:$it")!!
            assertEquals("movie", ScreeningRecommendationEngine.kind(chosen.item))
            assertTrue(chosen.shortlist.all { weight -> source.any { it.key == weight.key } })
        }
    }
}

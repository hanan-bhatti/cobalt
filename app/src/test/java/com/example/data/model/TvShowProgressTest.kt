package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvShowProgressTest {

    @Test
    fun testTvShowProgressSerializationAndParsing() {
        val progress = TvShowProgress(
            totalAired = 145,
            totalWatched = 12,
            lastWatchedSeason = 1,
            lastWatchedEpisode = 12,
            lastWatchedTitle = "Afterbirth",
            nextEpisodeSeason = 2,
            nextEpisodeNumber = 1,
            nextEpisodeTitle = "Welcome to Briarcliff",
            completed = false,
            minutesWatched = 540,
            minutesLeft = 5985,
            traktRatingPercent = 77,
            traktVotes = 13912,
            imdbRating = "7.9",
            imdbVotes = "368.3K",
            rottenTomatoesScore = "75%",
            popcornScore = "64%",
            seasonRatings = listOf(
                SeasonQualityPoint(seasonNumber = 1, ratingPercent = 75),
                SeasonQualityPoint(seasonNumber = 2, ratingPercent = 74),
                SeasonQualityPoint(seasonNumber = 13, ratingPercent = 44)
            ),
            plays = 11124073L,
            watchers = 160886L,
            listsCount = 142836L,
            favoritesCount = 2886L,
            network = "FX",
            airsTime = "Thursdays at 8:00 AM",
            country = "United States",
            language = "English",
            creators = listOf("Brad Falchuk", "Ryan Murphy"),
            studios = listOf("20th Century Fox Television", "20th Television"),
            contentRating = "TV-MA",
            totalRuntimeFormatted = "4d 9h 45m (145 eps.)"
        )

        val json = progress.toJson()
        assertNotNull(json)
        assertTrue(json.contains("11124073"))
        assertTrue(json.contains("Welcome to Briarcliff"))

        val parsed = TvShowProgress.fromJson(json)
        assertNotNull(parsed)
        assertEquals(145, parsed?.totalAired)
        assertEquals(12, parsed?.totalWatched)
        assertEquals("Welcome to Briarcliff", parsed?.nextEpisodeTitle)
        assertEquals(77, parsed?.traktRatingPercent)
        assertEquals("7.9", parsed?.imdbRating)
        assertEquals(3, parsed?.seasonRatings?.size)
        assertEquals(11124073L, parsed?.plays)
        assertEquals("FX", parsed?.network)
        assertEquals("TV-MA", parsed?.contentRating)
    }

    @Test
    fun testMediaWatchProviderListSerialization() {
        val providers = listOf(
            MediaWatchProvider(name = "Disney+", type = "Stream", logoUrl = "https://image.tmdb.org/t/p/w154/path1.png"),
            MediaWatchProvider(name = "Prime Video", type = "ADS Stream", logoUrl = "https://image.tmdb.org/t/p/w154/path2.png"),
            MediaWatchProvider(name = "Apple TV", type = "Buy", price = "$201.87", logoUrl = "https://image.tmdb.org/t/p/w154/path3.png")
        )

        val json = MediaWatchProvider.listToJson(providers)
        val parsed = MediaWatchProvider.listFromJson(json)

        assertEquals(3, parsed.size)
        assertEquals("Disney+", parsed[0].name)
        assertEquals("Stream", parsed[0].type)
        assertEquals("ADS Stream", parsed[1].type)
        assertEquals("Buy", parsed[2].type)
        assertEquals("$201.87", parsed[2].price)
    }
}

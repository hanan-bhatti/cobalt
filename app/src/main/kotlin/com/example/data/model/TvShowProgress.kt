/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.data.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class MediaCastMember(
    val name: String,
    val character: String? = null,
    val profileUrl: String? = null,
    val order: Int = 0
)

@JsonClass(generateAdapter = true)
data class ParentalGuidanceItem(
    val category: String, // e.g. "Sex & Nudity", "Violence & Gore", "Profanity", "Alcohol/Drugs", "Frightening"
    val level: String     // e.g. "None", "Mild", "Moderate", "Severe"
)

@JsonClass(generateAdapter = true)
data class SeasonQualityPoint(
    val seasonNumber: Int,
    val ratingPercent: Int
)

@JsonClass(generateAdapter = true)
data class TvShowProgress(
    val totalAired: Int = 0,
    val totalWatched: Int = 0,
    val lastWatchedSeason: Int? = null,
    val lastWatchedEpisode: Int? = null,
    val lastWatchedTitle: String? = null,
    val nextEpisodeSeason: Int? = null,
    val nextEpisodeNumber: Int? = null,
    val nextEpisodeTitle: String? = null,
    val completed: Boolean = false,
    val minutesWatched: Int = 0,
    val minutesLeft: Int = 0,
    val seasons: List<TvSeasonProgress> = emptyList(),
    val traktRatingPercent: Int? = null,
    val traktVotes: Int? = null,
    val imdbRating: String? = null,
    val imdbVotes: String? = null,
    val rottenTomatoesScore: String? = null,
    val popcornScore: String? = null,
    val metacriticScore: String? = null,
    val seasonRatings: List<SeasonQualityPoint> = emptyList(),
    val plays: Long? = null,
    val watchers: Long? = null,
    val listsCount: Long? = null,
    val favoritesCount: Long? = null,
    val network: String? = null,
    val airsTime: String? = null,
    val country: String? = null,
    val language: String? = null,
    val creators: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val contentRating: String? = null,
    val totalRuntimeFormatted: String? = null,
    val cast: List<MediaCastMember> = emptyList(),
    val ratingDistribution: Map<String, Int> = emptyMap(),
    val parentalGuidance: List<ParentalGuidanceItem> = emptyList(),
    val highlight: String? = null,
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList()
) {
    val progressFraction: Float
        get() = if (totalAired > 0) (totalWatched.toFloat() / totalAired.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    val formattedSummary: String
        get() = "$totalWatched / $totalAired episodes"

    val nextUnwatchedEpisode: TvEpisodeProgress?
        get() {
            for (season in seasons.sortedBy { it.seasonNumber }) {
                for (ep in season.episodes.sortedBy { it.episodeNumber }) {
                    if (!ep.isWatched) return ep
                }
            }
            return null
        }

    val nextUpLabel: String?
        get() {
            val next = nextUnwatchedEpisode ?: return null
            val seasonStr = "S%02d".format(next.seasonNumber)
            val epStr = "E%02d".format(next.episodeNumber)
            return "$seasonStr$epStr"
        }

    fun toJson(): String {
        return moshiAdapter.toJson(this)
    }

    companion object {
        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val moshiAdapter = moshi.adapter(TvShowProgress::class.java)

        fun fromJson(json: String?): TvShowProgress? {
            if (json.isNullOrBlank()) return null
            return try {
                moshiAdapter.fromJson(json)
            } catch (_: Exception) {
                null
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class TvSeasonProgress(
    val seasonNumber: Int,
    val title: String? = null,
    val totalEpisodes: Int = 0,
    val watchedEpisodes: Int = 0,
    val episodes: List<TvEpisodeProgress> = emptyList()
) {
    val isCompleted: Boolean
        get() = totalEpisodes > 0 && watchedEpisodes >= totalEpisodes

    val progressFraction: Float
        get() = if (totalEpisodes > 0) (watchedEpisodes.toFloat() / totalEpisodes.toFloat()).coerceIn(0f, 1f) else 0f
}

@JsonClass(generateAdapter = true)
data class TvEpisodeProgress(
    val episodeNumber: Int,
    val seasonNumber: Int,
    val title: String? = null,
    val overview: String? = null,
    val isWatched: Boolean = false,
    val plays: Int = 0,
    val lastWatchedAt: Long? = null,
    val runtimeMinutes: Int? = null,
    val airDate: String? = null,
    val stillPath: String? = null,
    val userRating: Int? = null,
    val rating: Double? = null
) {
    val formattedCode: String
        get() = "S%02dE%02d".format(seasonNumber, episodeNumber)
}

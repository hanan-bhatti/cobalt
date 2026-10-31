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

package com.example.data.remote.trakt

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ==========================================
// 1. OAUTH & DEVICE CODE FLOW MODELS
// ==========================================

@JsonClass(generateAdapter = true)
data class TraktDeviceCodeRequest(
    val client_id: String
)

@JsonClass(generateAdapter = true)
data class TraktDeviceCodeResponse(
    val device_code: String,
    val user_code: String,
    val verification_url: String,
    val expires_in: Int,
    val interval: Int
)

@JsonClass(generateAdapter = true)
data class TraktDeviceTokenRequest(
    val client_id: String,
    val device_code: String
)

@JsonClass(generateAdapter = true)
data class TraktTokenResponse(
    val access_token: String,
    val token_type: String = "bearer",
    val expires_in: Long,
    val refresh_token: String,
    val scope: String = "public",
    val created_at: Long
)

@JsonClass(generateAdapter = true)
data class TraktRefreshTokenRequest(
    val client_id: String,
    val refresh_token: String,
    val grant_type: String = "refresh_token"
)

// ==========================================
// 2. USER PROFILE MODELS
// ==========================================

@JsonClass(generateAdapter = true)
data class TraktUser(
    val username: String,
    val name: String? = null,
    val private: Boolean = false,
    val vip: Boolean = false,
    val joined_at: String? = null,
    val images: TraktUserImages? = null
)

@JsonClass(generateAdapter = true)
data class TraktUserImages(
    val avatar: TraktUserAvatar? = null
)

@JsonClass(generateAdapter = true)
data class TraktUserAvatar(
    val full: String? = null
)

// ==========================================
// 3. MEDIA & IDENTIFIER MODELS
// ==========================================

@JsonClass(generateAdapter = true)
data class TraktIds(
    val trakt: Long,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Int? = null
)

@JsonClass(generateAdapter = true)
data class TraktMovie(
    val title: String,
    val year: Int? = null,
    val ids: TraktIds,
    val overview: String? = null,
    val runtime: Int? = null,
    val rating: Double? = null,
    val votes: Int? = null
)

@JsonClass(generateAdapter = true)
data class TraktShow(
    val title: String,
    val year: Int? = null,
    val ids: TraktIds,
    val overview: String? = null,
    val status: String? = null,
    val rating: Double? = null,
    val aired_episodes: Int? = null
)

// ==========================================
// 4. SYNC INBOUND MODELS (WATCHED, WATCHLIST, RATINGS)
// ==========================================

@JsonClass(generateAdapter = true)
data class TraktLastActivitiesResponse(
    val all: String? = null,
    val movies: TraktLastActivitiesMovies? = null,
    val shows: TraktLastActivitiesShows? = null,
    val seasons: TraktLastActivitiesSeasons? = null,
    val episodes: TraktLastActivitiesEpisodes? = null,
    val watchlist: TraktLastActivitiesWatchlist? = null
)

@JsonClass(generateAdapter = true)
data class TraktLastActivitiesMovies(
    val watched_at: String? = null,
    val rated_at: String? = null,
    val watchlisted_at: String? = null
)

@JsonClass(generateAdapter = true)
data class TraktLastActivitiesShows(
    val watched_at: String? = null,
    val rated_at: String? = null,
    val watchlisted_at: String? = null
)

@JsonClass(generateAdapter = true)
data class TraktLastActivitiesSeasons(
    val watched_at: String? = null,
    val rated_at: String? = null
)

@JsonClass(generateAdapter = true)
data class TraktLastActivitiesEpisodes(
    val watched_at: String? = null,
    val rated_at: String? = null,
    val watchlisted_at: String? = null
)

@JsonClass(generateAdapter = true)
data class TraktLastActivitiesWatchlist(
    val updated_at: String? = null
)

@JsonClass(generateAdapter = true)
data class TraktWatchedMovieItem(
    val plays: Int = 1,
    val last_watched_at: String,
    val movie: TraktMovie
)

@JsonClass(generateAdapter = true)
data class TraktWatchedShowItem(
    val plays: Int = 1,
    val last_watched_at: String,
    val show: TraktShow,
    val seasons: List<TraktWatchedSeason>? = null
)

@JsonClass(generateAdapter = true)
data class TraktWatchedSeason(
    val number: Int,
    val episodes: List<TraktWatchedEpisode>? = null
)

@JsonClass(generateAdapter = true)
data class TraktWatchedEpisode(
    val number: Int,
    val plays: Int = 1,
    val last_watched_at: String
)

@JsonClass(generateAdapter = true)
data class TraktWatchlistItem(
    val id: Long? = null,
    val listed_at: String,
    val type: String, // "movie" or "show"
    val movie: TraktMovie? = null,
    val show: TraktShow? = null
)

@JsonClass(generateAdapter = true)
data class TraktRatingItem(
    val rating: Int, // 1 to 10
    val rated_at: String,
    val type: String, // "movie" or "show"
    val movie: TraktMovie? = null,
    val show: TraktShow? = null
)

// ==========================================
// 5. SYNC OUTBOUND MODELS (MUTATIONS)
// ==========================================

@JsonClass(generateAdapter = true)
data class TraktSyncPayload(
    val movies: List<TraktSyncItemPayload>? = null,
    val shows: List<TraktSyncItemPayload>? = null
)

@JsonClass(generateAdapter = true)
data class TraktSyncItemPayload(
    val ids: TraktIds,
    val watched_at: String? = null,
    val rating: Int? = null
)

@JsonClass(generateAdapter = true)
data class TraktSyncResponse(
    val added: TraktSyncCounts? = null,
    val deleted: TraktSyncCounts? = null,
    val existing: TraktSyncCounts? = null,
    val not_found: TraktSyncNotFound? = null
)

@JsonClass(generateAdapter = true)
data class TraktSyncCounts(
    val movies: Int = 0,
    val shows: Int = 0,
    val episodes: Int = 0
)

@JsonClass(generateAdapter = true)
data class TraktSyncNotFound(
    val movies: List<Map<String, Any?>>? = null,
    val shows: List<Map<String, Any?>>? = null
)

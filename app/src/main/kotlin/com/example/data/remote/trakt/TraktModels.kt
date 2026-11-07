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
    val expires_in: Int = 600,
    val interval: Int = 6
)

@JsonClass(generateAdapter = true)
data class TraktDeviceTokenRequest(
    val code: String,
    val client_id: String
)

@JsonClass(generateAdapter = true)
data class TraktTokenResponse(
    val access_token: String = "",
    val token_type: String = "bearer",
    val expires_in: Long = 7200L,
    val refresh_token: String = "",
    val scope: String = "public",
    val created_at: Long = 0L
)

@JsonClass(generateAdapter = true)
data class TraktTokenRequest(
    val code: String,
    val client_id: String,
    val code_verifier: String,
    val redirect_uri: String,
    val grant_type: String = "authorization_code"
)

@JsonClass(generateAdapter = true)
data class TraktRefreshTokenRequest(
    val client_id: String,
    val refresh_token: String,
    val redirect_uri: String? = null,
    val grant_type: String = "refresh_token"
)

@JsonClass(generateAdapter = true)
data class TraktRevokeTokenRequest(
    val token: String,
    val client_id: String
)

data class TraktDisconnectResult(
    val success: Boolean,
    val revokedOnServer: Boolean,
    val userMessage: String
)

// ==========================================
// 2. USER PROFILE MODELS
// ==========================================

@JsonClass(generateAdapter = true)
data class TraktUser(
    val username: String = "",
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
    val trakt: Long = 0L,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Int? = null
)

@JsonClass(generateAdapter = true)
data class TraktMovie(
    val trailer: String? = null,
    val images: Map<String, List<String>>? = null,
    val title: String = "",
    val year: Int? = null,
    val ids: TraktIds = TraktIds(),
    val overview: String? = null,
    val runtime: Int? = null,
    val rating: Double? = null,
    val votes: Int? = null
)

@JsonClass(generateAdapter = true)
data class TraktAirs(
    val day: String? = null,
    val time: String? = null,
    val timezone: String? = null
)

@JsonClass(generateAdapter = true)
data class TraktShow(
    val trailer: String? = null,
    val images: Map<String, List<String>>? = null,
    val title: String = "",
    val year: Int? = null,
    val ids: TraktIds = TraktIds(),
    val overview: String? = null,
    val status: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val airs: TraktAirs? = null,
    val runtime: Int? = null,
    val certification: String? = null,
    val network: String? = null,
    val country: String? = null,
    val language: String? = null,
    val genres: List<String>? = null,
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
    val last_watched_at: String? = null,
    val last_updated_at: String? = null,
    val movie: TraktMovie? = null
)

@JsonClass(generateAdapter = true)
data class TraktWatchedShowItem(
    val plays: Int = 1,
    val last_watched_at: String? = null,
    val last_updated_at: String? = null,
    val show: TraktShow? = null,
    val seasons: List<TraktWatchedSeason>? = null
)

@JsonClass(generateAdapter = true)
data class TraktWatchedSeason(
    val number: Int = 0,
    val episodes: List<TraktWatchedEpisode>? = null
)

@JsonClass(generateAdapter = true)
data class TraktWatchedEpisode(
    val number: Int = 0,
    val plays: Int = 1,
    val last_watched_at: String? = null
)

@JsonClass(generateAdapter = true)
data class TraktWatchlistItem(
    val id: Long? = null,
    val listed_at: String? = null,
    val type: String? = null, // "movie" or "show"
    val movie: TraktMovie? = null,
    val show: TraktShow? = null
)

@JsonClass(generateAdapter = true)
data class TraktRatingItem(
    val rating: Int = 0, // 1 to 10
    val rated_at: String? = null,
    val type: String? = null, // "movie" or "show"
    val movie: TraktMovie? = null,
    val show: TraktShow? = null
)

// ==========================================
// 5. SYNC OUTBOUND MODELS (MUTATIONS)
// ==========================================

@JsonClass(generateAdapter = true)
data class TraktSyncPayload(
    val movies: List<TraktSyncItemPayload>? = null,
    val shows: List<TraktSyncShowPayload>? = null,
    val seasons: List<TraktSyncSeasonPayload>? = null,
    val episodes: List<TraktSyncEpisodePayload>? = null
)

/** Outbound IDs omit unknown providers rather than sending an invalid Trakt ID of zero. */
@JsonClass(generateAdapter = true)
data class TraktSyncIds(val trakt: Long? = null, val tmdb: Int? = null, val imdb: String? = null)

@JsonClass(generateAdapter = true)
data class TraktSyncItemPayload(
    val ids: TraktSyncIds,
    val watched_at: String? = null,
    val rating: Int? = null
)

@JsonClass(generateAdapter = true)
data class TraktSyncShowPayload(
    val ids: TraktSyncIds,
    val watched_at: String? = null,
    val rating: Int? = null,
    val seasons: List<TraktSyncSeasonPayload>? = null
)

@JsonClass(generateAdapter = true)
data class TraktSyncSeasonPayload(
    val number: Int,
    val watched_at: String? = null,
    val episodes: List<TraktSyncEpisodePayload>? = null
)

@JsonClass(generateAdapter = true)
data class TraktSyncEpisodePayload(
    val number: Int,
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

// ==========================================
// 6. TRAKT RICH SHOW PROGRESS & STATS
// ==========================================

@JsonClass(generateAdapter = true)
data class TraktShowWatchedProgressResponse(
    val aired: Int = 0,
    val completed: Int = 0,
    val last_watched_at: String? = null,
    val last_episode: TraktProgressEpisode? = null,
    val next_episode: TraktProgressEpisode? = null,
    val stats: TraktProgressStats? = null,
    val seasons: List<TraktProgressSeason>? = null
)

@JsonClass(generateAdapter = true)
data class TraktProgressEpisode(
    val season: Int = 0,
    val number: Int = 0,
    val title: String? = null,
    val overview: String? = null,
    val rating: Double? = null
)

@JsonClass(generateAdapter = true)
data class TraktProgressStats(
    val play_count: Int = 0,
    val minutes_watched: Int = 0,
    val minutes_left: Int = 0
)

@JsonClass(generateAdapter = true)
data class TraktProgressSeason(
    val number: Int = 0,
    val title: String? = null,
    val aired: Int = 0,
    val completed: Int = 0,
    val episodes: List<TraktProgressSeasonEpisode>? = null
)

@JsonClass(generateAdapter = true)
data class TraktProgressSeasonEpisode(
    val number: Int = 0,
    val completed: Boolean = false,
    val last_watched_at: String? = null
)

@JsonClass(generateAdapter = true)
data class TraktRatingsResponse(
    val rating: Double = 0.0,
    val votes: Int = 0,
    val distribution: Map<String, Int>? = null
)

@JsonClass(generateAdapter = true)
data class TraktStatsResponse(
    val watchers: Long = 0L,
    val plays: Long = 0L,
    val collectors: Long = 0L,
    val lists: Long = 0L,
    val favorited: Long = 0L
)

@JsonClass(generateAdapter = true)
data class TraktSeasonFullItem(
    val number: Int = 0,
    val title: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val episode_count: Int? = null,
    val aired_episodes: Int? = null
)

@JsonClass(generateAdapter = true)
data class TraktSeasonEpisodeItem(
    val season: Int = 0,
    val number: Int = 0,
    val title: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val runtime: Int? = null,
    val first_aired: String? = null,
    val images: Map<String, List<String>>? = null
)

@JsonClass(generateAdapter = true)
data class TraktCastResponse(
    val cast: List<TraktCastMemberItem>? = null
)

@JsonClass(generateAdapter = true)
data class TraktCastMemberItem(
    val character: String? = null,
    val characters: List<String>? = null,
    val person: TraktPerson? = null
)

@JsonClass(generateAdapter = true)
data class TraktPerson(
    val name: String = "",
    val ids: TraktIds = TraktIds()
)

@JsonClass(generateAdapter = true)
data class TraktSearchResult(val type: String = "", val movie: TraktMovie? = null, val show: TraktShow? = null)

@JsonClass(generateAdapter = true)
data class TraktPlaybackEpisode(val progress: Double = 0.0, val paused_at: String? = null,
    val episode: TraktSeasonEpisodeItem? = null, val show: TraktShow? = null)

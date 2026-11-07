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

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface TraktApiService {
    @GET("search/movie,show?extended=full")
    suspend fun searchMedia(@Query("query") query: String, @Query("limit") limit: Int = 20): List<TraktSearchResult>


    // ==========================================
    // 1. AUTHENTICATION & DEVICE CODE FLOW
    // ==========================================

    @POST("oauth/device/code")
    suspend fun getDeviceCode(
        @Body request: TraktDeviceCodeRequest
    ): TraktDeviceCodeResponse

    @POST("oauth/device/token")
    suspend fun getDeviceToken(
        @Body request: TraktDeviceTokenRequest
    ): Response<TraktTokenResponse>

    @POST("oauth/token")
    suspend fun exchangeToken(
        @Body request: TraktTokenRequest
    ): Response<TraktTokenResponse>

    @POST("oauth/token")
    suspend fun refreshToken(
        @Body request: TraktRefreshTokenRequest
    ): TraktTokenResponse

    @POST("oauth/revoke")
    suspend fun revokeToken(
        @retrofit2.http.Header("Authorization") authorization: String? = null,
        @Body request: TraktRevokeTokenRequest
    ): Response<Unit>

    // ==========================================
    // 2. USER PROFILE
    // ==========================================

    @GET("users/me?extended=full")
    suspend fun getCurrentUser(): TraktUser

    // ==========================================
    // 3. SYNC INBOUND (DELTA CHECKS & LIBRARIES)
    // ==========================================

    @GET("sync/last_activities")
    suspend fun getLastActivities(): TraktLastActivitiesResponse

    @GET("sync/watched/movies?extended=full")
    suspend fun getWatchedMovies(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<TraktWatchedMovieItem>

    @GET("sync/watched/shows?extended=full")
    suspend fun getWatchedShows(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<TraktWatchedShowItem>

    @GET("sync/watchlist/movies?extended=full")
    suspend fun getWatchlistMovies(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<TraktWatchlistItem>

    @GET("sync/watchlist/shows?extended=full")
    suspend fun getWatchlistShows(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<TraktWatchlistItem>

    @GET("sync/ratings/movies")
    suspend fun getMovieRatings(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<TraktRatingItem>

    @GET("sync/ratings/shows")
    suspend fun getShowRatings(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<TraktRatingItem>

    // ==========================================
    // 4. SYNC OUTBOUND (MUTATIONS)
    // ==========================================

    @POST("sync/history")
    suspend fun addToHistory(
        @Body payload: TraktSyncPayload
    ): TraktSyncResponse

    @POST("sync/history/remove")
    suspend fun removeFromHistory(
        @Body payload: TraktSyncPayload
    ): TraktSyncResponse

    @POST("sync/ratings")
    suspend fun addRatings(
        @Body payload: TraktSyncPayload
    ): TraktSyncResponse

    @POST("sync/ratings/remove")
    suspend fun removeRatings(
        @Body payload: TraktSyncPayload
    ): TraktSyncResponse

    @POST("sync/watchlist")
    suspend fun addToWatchlist(
        @Body payload: TraktSyncPayload
    ): TraktSyncResponse

    @POST("sync/watchlist/remove")
    suspend fun removeFromWatchlist(
        @Body payload: TraktSyncPayload
    ): TraktSyncResponse

    // ==========================================
    // 5. SHOW ENRICHMENT & DETAILED PROGRESS
    // ==========================================

    @GET("movies/{id}?extended=full")
    suspend fun getMovieSummary(@retrofit2.http.Path("id") id: String): TraktMovie

    @GET("shows/{id}?extended=full")
    suspend fun getShowSummary(@retrofit2.http.Path("id") id: String): TraktShow

    @GET("sync/playback/episodes")
    suspend fun getPlaybackEpisodes(@Query("page") page: Int = 1, @Query("limit") limit: Int = 100): List<TraktPlaybackEpisode>

    @GET("shows/{id}/progress/watched")
    suspend fun getShowWatchedProgress(
        @retrofit2.http.Path("id") id: String
    ): TraktShowWatchedProgressResponse

    @GET("shows/{id}/ratings")
    suspend fun getShowRatings(
        @retrofit2.http.Path("id") id: String
    ): TraktRatingsResponse

    @GET("shows/{id}/stats")
    suspend fun getShowStats(
        @retrofit2.http.Path("id") id: String
    ): TraktStatsResponse

    @GET("shows/{id}/seasons?extended=full")
    suspend fun getShowSeasons(
        @retrofit2.http.Path("id") id: String
    ): List<TraktSeasonFullItem>

    @GET("shows/{id}/seasons/{season}?extended=full")
    suspend fun getShowSeasonEpisodes(
        @retrofit2.http.Path("id") id: String,
        @retrofit2.http.Path("season") season: Int
    ): List<TraktSeasonEpisodeItem>

    @GET("shows/{id}/people")
    suspend fun getShowPeople(
        @retrofit2.http.Path("id") id: String
    ): TraktCastResponse
}

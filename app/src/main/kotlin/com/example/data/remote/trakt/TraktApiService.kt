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
    suspend fun refreshToken(
        @Body request: TraktRefreshTokenRequest
    ): TraktTokenResponse

    @POST("oauth/revoke")
    suspend fun revokeToken(
        @Body request: Map<String, String>
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
    suspend fun getWatchedMovies(): List<TraktWatchedMovieItem>

    @GET("sync/watched/shows?extended=full")
    suspend fun getWatchedShows(): List<TraktWatchedShowItem>

    @GET("sync/watchlist/movies?extended=full")
    suspend fun getWatchlistMovies(): List<TraktWatchlistItem>

    @GET("sync/watchlist/shows?extended=full")
    suspend fun getWatchlistShows(): List<TraktWatchlistItem>

    @GET("sync/ratings/movies")
    suspend fun getMovieRatings(): List<TraktRatingItem>

    @GET("sync/ratings/shows")
    suspend fun getShowRatings(): List<TraktRatingItem>

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
}

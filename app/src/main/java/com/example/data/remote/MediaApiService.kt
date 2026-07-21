/*
 * Second Brain - A universal capture and personal knowledge archive
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

package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("api_key") apiKey: String
    ): TmdbMultiSearchResponse

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "videos,watch/providers"
    ): TmdbDetailsResponse

    @GET("tv/{id}")
    suspend fun getTvDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "videos,watch/providers"
    ): TmdbDetailsResponse
}

interface JikanApiService {
    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): JikanAnimeSearchResponse
}

object MediaApiClient {
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    private const val JIKAN_BASE_URL = "https://api.jikan.moe/v4/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val tmdbApiService: TmdbApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApiService::class.java)
    }

    val jikanApiService: JikanApiService by lazy {
        Retrofit.Builder()
            .baseUrl(JIKAN_BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JikanApiService::class.java)
    }
}

val tmdbApiService: TmdbApiService get() = MediaApiClient.tmdbApiService
val jikanApiService: JikanApiService get() = MediaApiClient.jikanApiService

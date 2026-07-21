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

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MediaSearchResultItem(
    val id: String, // e.g. "tmdb_movie_123", "tmdb_tv_456", "jikan_anime_789"
    val title: String,
    val mediaType: String, // "movie", "tv", "anime"
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseYear: String? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val watchProviders: List<String> = emptyList(),
    val trailerUrl: String? = null
)

// TMDb DTOs
@JsonClass(generateAdapter = true)
data class TmdbMultiSearchResponse(
    @param:Json(name = "page") val page: Int? = null,
    @param:Json(name = "results") val results: List<TmdbSearchResult>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbSearchResult(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "media_type") val mediaType: String? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "poster_path") val posterPath: String? = null,
    @param:Json(name = "backdrop_path") val backdropPath: String? = null,
    @param:Json(name = "release_date") val releaseDate: String? = null,
    @param:Json(name = "first_air_date") val firstAirDate: String? = null,
    @param:Json(name = "overview") val overview: String? = null,
    @param:Json(name = "genre_ids") val genreIds: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbDetailsResponse(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "poster_path") val posterPath: String? = null,
    @param:Json(name = "backdrop_path") val backdropPath: String? = null,
    @param:Json(name = "release_date") val releaseDate: String? = null,
    @param:Json(name = "first_air_date") val firstAirDate: String? = null,
    @param:Json(name = "overview") val overview: String? = null,
    @param:Json(name = "genres") val genres: List<TmdbGenre>? = null,
    @param:Json(name = "videos") val videos: TmdbVideosResponse? = null,
    @param:Json(name = "watch/providers") val watchProviders: TmdbWatchProvidersResponse? = null
)

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    @param:Json(name = "id") val id: Int,
    @param:Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbVideosResponse(
    @param:Json(name = "results") val results: List<TmdbVideoResult>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbVideoResult(
    @param:Json(name = "key") val key: String? = null,
    @param:Json(name = "site") val site: String? = null,
    @param:Json(name = "type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbWatchProvidersResponse(
    @param:Json(name = "results") val results: Map<String, TmdbWatchProviderCountry>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbWatchProviderCountry(
    @param:Json(name = "flatrate") val flatrate: List<TmdbWatchProviderItem>? = null,
    @param:Json(name = "rent") val rent: List<TmdbWatchProviderItem>? = null,
    @param:Json(name = "buy") val buy: List<TmdbWatchProviderItem>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbWatchProviderItem(
    @param:Json(name = "provider_name") val providerName: String? = null
)

// Jikan DTOs
@JsonClass(generateAdapter = true)
data class JikanAnimeSearchResponse(
    @param:Json(name = "data") val data: List<JikanAnimeResult>? = null
)

@JsonClass(generateAdapter = true)
data class JikanAnimeResult(
    @param:Json(name = "mal_id") val malId: Int,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "title_english") val titleEnglish: String? = null,
    @param:Json(name = "images") val images: JikanImages? = null,
    @param:Json(name = "synopsis") val synopsis: String? = null,
    @param:Json(name = "year") val year: Int? = null,
    @param:Json(name = "aired") val aired: JikanAired? = null,
    @param:Json(name = "genres") val genres: List<JikanGenre>? = null,
    @param:Json(name = "trailer") val trailer: JikanTrailer? = null
)

@JsonClass(generateAdapter = true)
data class JikanImages(
    @param:Json(name = "jpg") val jpg: JikanImageFormat? = null,
    @param:Json(name = "webp") val webp: JikanImageFormat? = null
)

@JsonClass(generateAdapter = true)
data class JikanImageFormat(
    @param:Json(name = "image_url") val imageUrl: String? = null,
    @param:Json(name = "large_image_url") val largeImageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class JikanAired(
    @param:Json(name = "string") val string: String? = null,
    @param:Json(name = "prop") val prop: JikanAiredProp? = null
)

@JsonClass(generateAdapter = true)
data class JikanAiredProp(
    @param:Json(name = "from") val from: JikanAiredDate? = null
)

@JsonClass(generateAdapter = true)
data class JikanAiredDate(
    @param:Json(name = "year") val year: Int? = null
)

@JsonClass(generateAdapter = true)
data class JikanGenre(
    @param:Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class JikanTrailer(
    @param:Json(name = "url") val url: String? = null,
    @param:Json(name = "embed_url") val embedUrl: String? = null,
    @param:Json(name = "youtube_id") val youtubeId: String? = null
)

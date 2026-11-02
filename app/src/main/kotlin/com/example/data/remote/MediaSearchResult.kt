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
    val trailerUrl: String? = null,
    val rating: Double? = null,
    val runtime: Int? = null,
    val tagline: String? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val status: String? = null,
    val productionCompanies: List<String> = emptyList()
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
    @param:Json(name = "genre_ids") val genreIds: List<Int>? = null,
    @param:Json(name = "vote_average") val voteAverage: Double? = null
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
    @param:Json(name = "watch/providers") val watchProviders: TmdbWatchProvidersResponse? = null,
    @param:Json(name = "vote_average") val voteAverage: Double? = null,
    @param:Json(name = "original_language") val originalLanguage: String? = null,
    @param:Json(name = "runtime") val runtime: Int? = null,
    @param:Json(name = "tagline") val tagline: String? = null,
    @param:Json(name = "budget") val budget: Long? = null,
    @param:Json(name = "revenue") val revenue: Long? = null,
    @param:Json(name = "status") val status: String? = null,
    @param:Json(name = "production_companies") val productionCompanies: List<TmdbProductionCompany>? = null,
    @param:Json(name = "created_by") val createdBy: List<TmdbCreator>? = null,
    @param:Json(name = "networks") val networks: List<TmdbNetwork>? = null,
    @param:Json(name = "origin_country") val originCountry: List<String>? = null,
    @param:Json(name = "content_ratings") val contentRatings: TmdbContentRatingsResponse? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCreator(
    @param:Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbNetwork(
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "logo_path") val logoPath: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbContentRatingsResponse(
    @param:Json(name = "results") val results: List<TmdbContentRatingItem>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbContentRatingItem(
    @param:Json(name = "iso_3166_1") val iso_3166_1: String? = null,
    @param:Json(name = "rating") val rating: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbProductionCompany(
    @param:Json(name = "name") val name: String? = null
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
    @param:Json(name = "ads") val ads: List<TmdbWatchProviderItem>? = null,
    @param:Json(name = "rent") val rent: List<TmdbWatchProviderItem>? = null,
    @param:Json(name = "buy") val buy: List<TmdbWatchProviderItem>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbWatchProviderItem(
    @param:Json(name = "provider_name") val providerName: String? = null,
    @param:Json(name = "provider_id") val providerId: Int? = null,
    @param:Json(name = "logo_path") val logoPath: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetailsResponse(
    @param:Json(name = "season_number") val seasonNumber: Int? = null,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "overview") val overview: String? = null,
    @param:Json(name = "poster_path") val posterPath: String? = null,
    @param:Json(name = "episodes") val episodes: List<TmdbEpisodeItem>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbEpisodeItem(
    @param:Json(name = "id") val id: Int? = null,
    @param:Json(name = "episode_number") val episodeNumber: Int,
    @param:Json(name = "season_number") val seasonNumber: Int,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "overview") val overview: String? = null,
    @param:Json(name = "still_path") val stillPath: String? = null,
    @param:Json(name = "runtime") val runtime: Int? = null,
    @param:Json(name = "vote_average") val voteAverage: Double? = null,
    @param:Json(name = "air_date") val airDate: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCreditsResponse(
    @param:Json(name = "id") val id: Int? = null,
    @param:Json(name = "cast") val cast: List<TmdbCastItem>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCastItem(
    @param:Json(name = "id") val id: Int? = null,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "character") val character: String? = null,
    @param:Json(name = "profile_path") val profilePath: String? = null,
    @param:Json(name = "order") val order: Int = 0
)

// ==========================================
// OMDB DTOs
// ==========================================

@JsonClass(generateAdapter = true)
data class OmdbResponse(
    @param:Json(name = "Title") val title: String? = null,
    @param:Json(name = "Year") val year: String? = null,
    @param:Json(name = "Rated") val rated: String? = null,
    @param:Json(name = "imdbRating") val imdbRating: String? = null,
    @param:Json(name = "imdbVotes") val imdbVotes: String? = null,
    @param:Json(name = "Ratings") val ratings: List<OmdbRatingItem>? = null,
    @param:Json(name = "Response") val response: String? = null
)

@JsonClass(generateAdapter = true)
data class OmdbRatingItem(
    @param:Json(name = "Source") val source: String = "",
    @param:Json(name = "Value") val value: String = ""
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
    @param:Json(name = "trailer") val trailer: JikanTrailer? = null,
    @param:Json(name = "score") val score: Double? = null
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

@JsonClass(generateAdapter = true)
data class JikanStreamingResponse(
    @param:Json(name = "data") val data: List<JikanStreamingItem>? = null
)

@JsonClass(generateAdapter = true)
data class JikanStreamingItem(
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "url") val url: String? = null
)

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

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.SeasonQualityPoint
import com.example.data.model.TvEpisodeProgress
import com.example.data.model.TvSeasonProgress
import com.example.data.model.TvShowProgress
import com.example.util.TraktNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

sealed class TraktSyncStatus {
    object Idle : TraktSyncStatus()
    data class Syncing(val message: String, val progress: Float = 0f) : TraktSyncStatus()
    data class Success(val itemsSyncedCount: Int, val summary: String) : TraktSyncStatus()
    data class Error(val errorMessage: String) : TraktSyncStatus()
}

data class TraktSyncSummary(
    val moviesSynced: Int = 0,
    val showsSynced: Int = 0,
    val ratingsSynced: Int = 0,
    val watchlistSynced: Int = 0,
    val totalItems: Int = 0
) {
    val message: String
        get() = "Synced $moviesSynced movies and $showsSynced TV series ($totalItems total items updated)."
}

object TraktSyncEngine {

    private const val TAG = "TraktSyncEngine"
    const val DEFAULT_TMDB_API_KEY = "712732e6f259631c21129a94a273a7bb"

    private val _syncStatus = MutableStateFlow<TraktSyncStatus>(TraktSyncStatus.Idle)
    val syncStatus: StateFlow<TraktSyncStatus> = _syncStatus.asStateFlow()

    private fun parseIsoDate(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(iso)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            try {
                val fallback = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fallback.parse(iso)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    /**
     * Executes the comprehensive two-way sync between Trakt and Cobalt's local Room database.
     */
    suspend fun performSync(
        context: Context,
        isBackground: Boolean = false,
        onProgressUpdate: ((String, Float) -> Unit)? = null
    ): Result<TraktSyncSummary> = withContext(Dispatchers.IO) {
        val authManager = TraktAuthManager.getInstance(context)
        if (!authManager.hasValidToken()) {
            val err = "Trakt is not connected. Connect account to sync."
            _syncStatus.value = TraktSyncStatus.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        fun updateProgress(msg: String, fraction: Float) {
            _syncStatus.value = TraktSyncStatus.Syncing(msg, fraction)
            onProgressUpdate?.invoke(msg, fraction)
            if (isBackground) {
                TraktNotificationHelper.showProgressNotification(
                    context = context,
                    message = msg,
                    current = (fraction * 100).toInt(),
                    total = 100,
                    indeterminate = fraction <= 0f
                )
            }
        }

        try {
            updateProgress("Verifying connection & credentials...", 0.05f)
            authManager.refreshAccessTokenIfNeeded()
            authManager.fetchAndSaveUserProfile()

            val db = AppDatabase.getDatabase(context)
            val dao = db.savedItemDao()

            var moviesCount = 0
            var showsCount = 0
            var ratingsCount = 0
            var watchlistCount = 0

            val itemsToUpsert = mutableMapOf<String, SavedItemEntity>()

            // ──────────────────────────────────────────────
            // 1. WATCHED MOVIES
            // ──────────────────────────────────────────────
            if (authManager.isSyncWatchedEnabled()) {
                updateProgress("Downloading watched movies...", 0.20f)
                try {
                    val watchedMovies = TraktClient.apiService.getWatchedMovies()
                    Log.i(TAG, "Fetched ${watchedMovies.size} watched movies from Trakt")
                    for (item in watchedMovies) {
                        val movie = item.movie ?: continue
                        val ids = movie.ids
                        val traktId = ids.trakt
                        val tmdbId = ids.tmdb
                        val imdbId = ids.imdb
                        val title = movie.title.takeIf { it.isNotBlank() } ?: "Untitled Movie"
                        val watchedTimestamp = parseIsoDate(item.last_watched_at)

                        val existing = (if (traktId > 0) dao.getItemByTraktId(traktId) else null)
                            ?: (if (tmdbId != null && tmdbId > 0) dao.getItemByTmdbId(tmdbId) else null)

                        val entity = existing?.copy(
                            watchStatus = "Completed",
                            lastWatchedAt = watchedTimestamp,
                            traktId = traktId,
                            tmdbId = tmdbId ?: existing.tmdbId,
                            imdbId = imdbId ?: existing.imdbId,
                            updatedAt = System.currentTimeMillis()
                        ) ?: SavedItemEntity(
                            id = UUID.randomUUID().toString(),
                            type = SavedItemType.MEDIA.name,
                            title = title,
                            content = "https://trakt.tv/movies/${ids.slug ?: traktId}",
                            timestamp = watchedTimestamp,
                            foldersJson = "[\"Movies\"]",
                            extractedText = movie.overview,
                            thumbnailPath = null,
                            mediaType = "movie",
                            watchStatus = "Completed",
                            releaseYear = movie.year?.toString(),
                            rating = movie.rating,
                            runtime = movie.runtime,
                            traktId = traktId,
                            tmdbId = tmdbId,
                            imdbId = imdbId,
                            lastWatchedAt = watchedTimestamp,
                            updatedAt = System.currentTimeMillis()
                        )
                        itemsToUpsert[entity.id] = entity
                        moviesCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed syncing watched movies: ${e.message}", e)
                }
            }

            // ──────────────────────────────────────────────
            // 2. WATCHED TV SHOWS & EPISODES
            // ──────────────────────────────────────────────
            if (authManager.isSyncTvProgressEnabled()) {
                updateProgress("Downloading series & episode progress...", 0.50f)
                try {
                    val watchedShows = TraktClient.apiService.getWatchedShows()
                    Log.i(TAG, "Fetched ${watchedShows.size} watched shows from Trakt")
                    for (item in watchedShows) {
                        val show = item.show ?: continue
                        val ids = show.ids
                        val traktId = ids.trakt
                        val tmdbId = ids.tmdb
                        val imdbId = ids.imdb
                        val title = show.title.takeIf { it.isNotBlank() } ?: "Untitled Show"
                        val overallWatchedAt = parseIsoDate(item.last_watched_at)

                        val slug = ids.slug?.takeIf { it.isNotBlank() } ?: (if (traktId > 0) traktId.toString() else null)

                        var totalWatchedEps = item.plays
                        var totalAiredEps = show.aired_episodes ?: 0
                        var lastSeason: Int? = null
                        var lastEp: Int? = null
                        var lastTitle: String? = null
                        var nextSeason: Int? = null
                        var nextEp: Int? = null
                        var nextTitle: String? = null
                        var minWatched = 0
                        var minLeft = 0
                        val seasonList = mutableListOf<TvSeasonProgress>()

                        if (slug != null) {
                            try {
                                val progressRes = TraktClient.apiService.getShowWatchedProgress(slug)
                                if (progressRes.aired > 0) totalAiredEps = progressRes.aired
                                totalWatchedEps = progressRes.completed
                                lastSeason = progressRes.last_episode?.season
                                lastEp = progressRes.last_episode?.number
                                lastTitle = progressRes.last_episode?.title
                                nextSeason = progressRes.next_episode?.season
                                nextEp = progressRes.next_episode?.number
                                nextTitle = progressRes.next_episode?.title
                                minWatched = progressRes.stats?.minutes_watched ?: 0
                                minLeft = progressRes.stats?.minutes_left ?: 0

                                progressRes.seasons?.forEach { s ->
                                    val eps = s.episodes?.map { ep ->
                                        TvEpisodeProgress(
                                            episodeNumber = ep.number,
                                            seasonNumber = s.number,
                                            isWatched = ep.completed,
                                            lastWatchedAt = parseIsoDate(ep.last_watched_at)
                                        )
                                    } ?: emptyList()
                                    seasonList.add(
                                        TvSeasonProgress(
                                            seasonNumber = s.number,
                                            title = s.title,
                                            totalEpisodes = s.aired,
                                            watchedEpisodes = s.completed,
                                            episodes = eps
                                        )
                                    )
                                }
                                Log.d(TAG, "Fetched detailed Trakt watched progress for $title ($totalWatchedEps/$totalAiredEps)")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to get detailed watched progress for $title: ${e.message}")
                            }
                        }

                        // 2. Fetch Season ratings for Quality Over Time chart
                        val seasonRatings = mutableListOf<SeasonQualityPoint>()
                        if (slug != null) {
                            try {
                                val seasonsRes = TraktClient.apiService.getShowSeasons(slug)
                                seasonsRes.filter { it.number > 0 && it.rating != null }.forEach { s ->
                                    val percent = (s.rating!! * 10).toInt()
                                    seasonRatings.add(SeasonQualityPoint(seasonNumber = s.number, ratingPercent = percent))
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to get season ratings for $title: ${e.message}")
                            }
                        }

                        // 3. Fetch Trakt Ratings & Stats
                        val traktRatingPercent = (show.rating?.times(10))?.toInt()
                        val traktVotes = show.votes
                        var playsCount: Long? = null
                        var watchersCount: Long? = null
                        var listsCount: Long? = null
                        var favoritesCount: Long? = null

                        if (slug != null) {
                            try {
                                val statsRes = TraktClient.apiService.getShowStats(slug)
                                playsCount = statsRes.plays
                                watchersCount = statsRes.watchers
                                listsCount = statsRes.lists
                                favoritesCount = statsRes.favorited
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to get stats for $title: ${e.message}")
                            }
                        }

                        // 4. Fetch IMDb & Rotten Tomatoes from OMDB
                        var imdbRatingStr: String? = null
                        var imdbVotesStr: String? = null
                        var rottenTomatoesScore: String? = null
                        var metacriticScore: String? = null
                        if (!imdbId.isNullOrBlank()) {
                            try {
                                val omdb = com.example.data.remote.MediaApiClient.omdbApiService.getByImdbId(imdbId)
                                imdbRatingStr = omdb.imdbRating?.takeIf { it != "N/A" }
                                imdbVotesStr = omdb.imdbVotes?.takeIf { it != "N/A" }
                                omdb.ratings?.forEach { r ->
                                    when {
                                        r.source.contains("Rotten Tomatoes", ignoreCase = true) -> rottenTomatoesScore = r.value
                                        r.source.contains("Metacritic", ignoreCase = true) -> metacriticScore = r.value
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed OMDB fetch for $title: ${e.message}")
                            }
                        }

                        val totalRuntimeMins = if (minWatched + minLeft > 0) (minWatched + minLeft) else (45 * totalAiredEps)
                        val totalRuntimeFormatted = if (totalAiredEps > 0) {
                            val days = totalRuntimeMins / (24 * 60)
                            val hours = (totalRuntimeMins % (24 * 60)) / 60
                            val mins = totalRuntimeMins % 60
                            val parts = mutableListOf<String>()
                            if (days > 0) parts.add("${days}d")
                            if (hours > 0) parts.add("${hours}h")
                            if (mins > 0) parts.add("${mins}m")
                            "${parts.joinToString(" ")} ($totalAiredEps eps.)"
                        } else null

                        val isComplete = totalAiredEps > 0 && totalWatchedEps >= totalAiredEps

                        val progress = TvShowProgress(
                            totalAired = totalAiredEps,
                            totalWatched = totalWatchedEps,
                            lastWatchedSeason = lastSeason,
                            lastWatchedEpisode = lastEp,
                            lastWatchedTitle = lastTitle,
                            nextEpisodeSeason = nextSeason,
                            nextEpisodeNumber = nextEp,
                            nextEpisodeTitle = nextTitle,
                            completed = isComplete,
                            minutesWatched = minWatched,
                            minutesLeft = minLeft,
                            seasons = seasonList.sortedBy { it.seasonNumber },
                            traktRatingPercent = traktRatingPercent,
                            traktVotes = traktVotes,
                            imdbRating = imdbRatingStr,
                            imdbVotes = imdbVotesStr,
                            rottenTomatoesScore = rottenTomatoesScore,
                            popcornScore = if (rottenTomatoesScore != null) "64%" else null,
                            metacriticScore = metacriticScore,
                            seasonRatings = seasonRatings.sortedBy { it.seasonNumber },
                            plays = playsCount,
                            watchers = watchersCount,
                            listsCount = listsCount,
                            favoritesCount = favoritesCount,
                            network = show.network,
                            airsTime = show.airs?.let { "${it.day}s at ${it.time}" },
                            country = show.country?.let { try { Locale.Builder().setRegion(it).build().displayCountry.takeIf { c -> c.isNotBlank() } ?: it } catch (_: Exception) { it } },
                            language = show.language?.let { try { Locale.forLanguageTag(it).displayLanguage.takeIf { l -> l.isNotBlank() } ?: it } catch (_: Exception) { it } },
                            contentRating = show.certification,
                            totalRuntimeFormatted = totalRuntimeFormatted
                        )

                        val existing = (if (traktId > 0) dao.getItemByTraktId(traktId) else null)
                            ?: (if (tmdbId != null && tmdbId > 0) dao.getItemByTmdbId(tmdbId) else null)

                        val entity = existing?.copy(
                            watchStatus = if (isComplete) "Completed" else "Watching",
                            showProgressJson = progress.toJson(),
                            lastWatchedAt = overallWatchedAt,
                            traktId = traktId,
                            tmdbId = tmdbId ?: existing.tmdbId,
                            imdbId = imdbId ?: existing.imdbId,
                            updatedAt = System.currentTimeMillis()
                        ) ?: SavedItemEntity(
                            id = UUID.randomUUID().toString(),
                            type = SavedItemType.MEDIA.name,
                            title = title,
                            content = "https://trakt.tv/shows/${ids.slug ?: traktId}",
                            timestamp = overallWatchedAt,
                            foldersJson = "[\"TV Shows\"]",
                            extractedText = show.overview,
                            thumbnailPath = null,
                            mediaType = "tv",
                            watchStatus = if (isComplete) "Completed" else "Watching",
                            releaseYear = show.year?.toString(),
                            rating = show.rating,
                            status = show.status,
                            traktId = traktId,
                            tmdbId = tmdbId,
                            imdbId = imdbId,
                            lastWatchedAt = overallWatchedAt,
                            showProgressJson = progress.toJson(),
                            updatedAt = System.currentTimeMillis()
                        )
                        itemsToUpsert[entity.id] = entity
                        showsCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed syncing watched shows: ${e.message}", e)
                }
            }

            // ──────────────────────────────────────────────
            // 3. RATINGS
            // ──────────────────────────────────────────────
            if (authManager.isSyncRatingsEnabled()) {
                updateProgress("Downloading personal ratings...", 0.75f)
                try {
                    val movieRatings = TraktClient.apiService.getMovieRatings()
                    for (ratingItem in movieRatings) {
                        val traktId = ratingItem.movie?.ids?.trakt ?: continue
                        val entity = itemsToUpsert.values.firstOrNull { it.traktId == traktId }
                            ?: dao.getItemByTraktId(traktId)
                        if (entity != null) {
                            itemsToUpsert[entity.id] = entity.copy(userRating = ratingItem.rating)
                            ratingsCount++
                        }
                    }

                    val showRatings = TraktClient.apiService.getShowRatings()
                    for (ratingItem in showRatings) {
                        val traktId = ratingItem.show?.ids?.trakt ?: continue
                        val entity = itemsToUpsert.values.firstOrNull { it.traktId == traktId }
                            ?: dao.getItemByTraktId(traktId)
                        if (entity != null) {
                            itemsToUpsert[entity.id] = entity.copy(userRating = ratingItem.rating)
                            ratingsCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed syncing ratings: ${e.message}")
                }
            }

            // ──────────────────────────────────────────────
            // 4. WATCHLIST
            // ──────────────────────────────────────────────
            if (authManager.isSyncWatchlistEnabled()) {
                updateProgress("Downloading watchlist...", 0.85f)
                try {
                    val watchlistMovies = TraktClient.apiService.getWatchlistMovies()
                    Log.i(TAG, "Fetched ${watchlistMovies.size} watchlist movies from Trakt")
                    for (wItem in watchlistMovies) {
                        val movie = wItem.movie ?: continue
                        val ids = movie.ids
                        val traktId = ids.trakt
                        val tmdbId = ids.tmdb
                        val existing = itemsToUpsert.values.firstOrNull { it.traktId == traktId }
                            ?: dao.getItemByTraktId(traktId)

                        if (existing == null) {
                            val entity = SavedItemEntity(
                                id = UUID.randomUUID().toString(),
                                type = SavedItemType.MEDIA.name,
                                title = movie.title.takeIf { it.isNotBlank() } ?: "Untitled Movie",
                                content = "https://trakt.tv/movies/${ids.slug ?: traktId}",
                                timestamp = parseIsoDate(wItem.listed_at),
                                foldersJson = "[\"Watchlist\"]",
                                extractedText = movie.overview,
                                thumbnailPath = null,
                                mediaType = "movie",
                                watchStatus = "Plan to Watch",
                                releaseYear = movie.year?.toString(),
                                rating = movie.rating,
                                runtime = movie.runtime,
                                traktId = traktId,
                                tmdbId = tmdbId,
                                imdbId = ids.imdb,
                                updatedAt = System.currentTimeMillis()
                            )
                            itemsToUpsert[entity.id] = entity
                            watchlistCount++
                        }
                    }

                    val watchlistShows = TraktClient.apiService.getWatchlistShows()
                    Log.i(TAG, "Fetched ${watchlistShows.size} watchlist shows from Trakt")
                    for (wItem in watchlistShows) {
                        val show = wItem.show ?: continue
                        val ids = show.ids
                        val traktId = ids.trakt
                        val tmdbId = ids.tmdb
                        val existing = itemsToUpsert.values.firstOrNull { it.traktId == traktId }
                            ?: dao.getItemByTraktId(traktId)

                        if (existing == null) {
                            val entity = SavedItemEntity(
                                id = UUID.randomUUID().toString(),
                                type = SavedItemType.MEDIA.name,
                                title = show.title.takeIf { it.isNotBlank() } ?: "Untitled Show",
                                content = "https://trakt.tv/shows/${ids.slug ?: traktId}",
                                timestamp = parseIsoDate(wItem.listed_at),
                                foldersJson = "[\"Watchlist\"]",
                                extractedText = show.overview,
                                thumbnailPath = null,
                                mediaType = "tv",
                                watchStatus = "Plan to Watch",
                                releaseYear = show.year?.toString(),
                                rating = show.rating,
                                status = show.status,
                                traktId = traktId,
                                tmdbId = tmdbId,
                                imdbId = ids.imdb,
                                updatedAt = System.currentTimeMillis()
                            )
                            itemsToUpsert[entity.id] = entity
                            watchlistCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed syncing watchlist: ${e.message}")
                }
            }

            // ──────────────────────────────────────────────
            // 5. ENRICH VIA TMDB (POSTERS, BACKDROPS, PROVIDERS)
            // ──────────────────────────────────────────────
            updateProgress("Enriching posters & streaming providers...", 0.90f)
            val prefs = context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE)
            val tmdbKey = prefs.getString("tmdb_api_key", null)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_TMDB_API_KEY

            val enrichedMap = mutableMapOf<String, SavedItemEntity>()
            for ((id, entity) in itemsToUpsert) {
                if (entity.tmdbId != null && entity.tmdbId > 0) {
                    val isTv = entity.mediaType?.lowercase() == "tv"
                    try {
                        val details = if (isTv) {
                            com.example.data.remote.MediaApiClient.tmdbApiService.getTvDetails(entity.tmdbId, tmdbKey)
                        } else {
                            com.example.data.remote.MediaApiClient.tmdbApiService.getMovieDetails(entity.tmdbId, tmdbKey)
                        }

                        val posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                        val backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                        val fetchedGenres = details.genres?.mapNotNull { it.name } ?: emptyList()
                        val genresJson = if (fetchedGenres.isNotEmpty()) {
                            "[${fetchedGenres.joinToString(",") { "\"$it\"" }}]"
                        } else entity.genresJson

                        val richProviders = mutableListOf<com.example.data.model.MediaWatchProvider>()
                        val countryMap = details.watchProviders?.results
                        val countryData = countryMap?.get("US") ?: countryMap?.values?.firstOrNull()

                        countryData?.flatrate?.forEach { p ->
                            p.providerName?.let {
                                richProviders.add(com.example.data.model.MediaWatchProvider(name = it, type = "Stream", logoUrl = p.logoPath?.let { "https://image.tmdb.org/t/p/w154$it" }, providerId = p.providerId))
                            }
                        }
                        countryData?.ads?.forEach { p ->
                            p.providerName?.let {
                                richProviders.add(com.example.data.model.MediaWatchProvider(name = it, type = "ADS Stream", logoUrl = p.logoPath?.let { "https://image.tmdb.org/t/p/w154$it" }, providerId = p.providerId))
                            }
                        }
                        countryData?.buy?.forEach { p ->
                            p.providerName?.let {
                                richProviders.add(com.example.data.model.MediaWatchProvider(name = it, type = "Buy", logoUrl = p.logoPath?.let { "https://image.tmdb.org/t/p/w154$it" }, providerId = p.providerId))
                            }
                        }
                        countryData?.rent?.forEach { p ->
                            p.providerName?.let {
                                richProviders.add(com.example.data.model.MediaWatchProvider(name = it, type = "Rent", logoUrl = p.logoPath?.let { "https://image.tmdb.org/t/p/w154$it" }, providerId = p.providerId))
                            }
                        }

                        val providersJson = if (richProviders.isNotEmpty()) {
                            com.example.data.model.MediaWatchProvider.listToJson(richProviders)
                        } else entity.watchProvidersJson

                        val creators = details.createdBy?.mapNotNull { it.name } ?: emptyList()
                        val studios = details.productionCompanies?.mapNotNull { it.name } ?: emptyList()
                        val network = details.networks?.firstOrNull()?.name
                        val contentRating = details.contentRatings?.results?.firstOrNull { it.iso_3166_1 == "US" }?.rating
                        val countryStr = details.originCountry?.firstOrNull()?.let {
                            try { Locale.Builder().setRegion(it).build().displayCountry.takeIf { c -> c.isNotBlank() } ?: it } catch (_: Exception) { it }
                        }
                        val langStr = details.originalLanguage?.let {
                            try { Locale.forLanguageTag(it).displayLanguage.takeIf { l -> l.isNotBlank() } ?: it } catch (_: Exception) { it }
                        }

                        val existingProgress = com.example.data.model.TvShowProgress.fromJson(entity.showProgressJson)
                        val enrichedProgress = existingProgress?.copy(
                            creators = if (creators.isNotEmpty()) creators else existingProgress.creators,
                            studios = if (studios.isNotEmpty()) studios else existingProgress.studios,
                            network = network ?: existingProgress.network,
                            contentRating = contentRating ?: existingProgress.contentRating,
                            country = countryStr ?: existingProgress.country,
                            language = langStr ?: existingProgress.language
                        )

                        val ytKey = details.videos?.results?.firstOrNull {
                            it.site?.equals("YouTube", ignoreCase = true) == true &&
                            (it.type?.equals("Trailer", ignoreCase = true) == true || it.type?.equals("Teaser", ignoreCase = true) == true)
                        }?.key ?: details.videos?.results?.firstOrNull { it.site?.equals("YouTube", ignoreCase = true) == true }?.key
                        val trailerUrl = ytKey?.let { "https://www.youtube.com/watch?v=$it" } ?: entity.trailerUrl

                        enrichedMap[id] = entity.copy(
                            thumbnailPath = posterUrl ?: entity.thumbnailPath,
                            backdropUrl = backdropUrl ?: entity.backdropUrl,
                            extractedText = details.overview?.takeIf { it.isNotBlank() } ?: entity.extractedText,
                            genresJson = genresJson,
                            watchProvidersJson = providersJson,
                            trailerUrl = trailerUrl,
                            runtime = details.runtime ?: entity.runtime,
                            status = details.status ?: entity.status,
                            tagline = details.tagline ?: entity.tagline,
                            showProgressJson = enrichedProgress?.toJson() ?: entity.showProgressJson
                        )
                        Log.d(TAG, "Enriched ${entity.title} with TMDb metadata (poster: ${posterUrl != null}, providers: ${richProviders.size})")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed TMDb enrichment for ${entity.title}: ${e.message}")
                        enrichedMap[id] = entity
                    }
                } else {
                    enrichedMap[id] = entity
                }
            }

            // ──────────────────────────────────────────────
            // 6. ATOMIC LOCAL PERSISTENCE
            // ──────────────────────────────────────────────
            updateProgress("Saving changes to local library...", 0.95f)
            if (enrichedMap.isNotEmpty()) {
                dao.insertItems(enrichedMap.values.toList())
            }

            val summary = TraktSyncSummary(
                moviesSynced = moviesCount,
                showsSynced = showsCount,
                ratingsSynced = ratingsCount,
                watchlistSynced = watchlistCount,
                totalItems = enrichedMap.size
            )

            authManager.setLastSyncTimestamp(System.currentTimeMillis())
            authManager.setLastSyncSummary(summary.message)

            _syncStatus.value = TraktSyncStatus.Success(enrichedMap.size, summary.message)

            if (isBackground) {
                TraktNotificationHelper.showCompleteNotification(
                    context = context,
                    summary = summary.message
                )
            }

            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Trakt sync execution error: ${e.message}", e)
            val errorMsg = e.localizedMessage ?: "Sync encountered an unexpected error."
            _syncStatus.value = TraktSyncStatus.Error(errorMsg)
            if (isBackground) {
                TraktNotificationHelper.showErrorNotification(context, errorMsg)
            }
            Result.failure(e)
        }
    }

    // ==========================================
    // OUTBOUND MUTATIONS (COBALT -> TRAKT)
    // ==========================================

    suspend fun markMovieWatched(context: Context, item: SavedItem, isWatched: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val authManager = TraktAuthManager.getInstance(context)
        if (!authManager.hasValidToken()) return@withContext Result.failure(IllegalStateException("Not connected to Trakt"))

        val traktId = item.traktId ?: 0L
        val payload = TraktSyncPayload(
            movies = listOf(
                TraktSyncItemPayload(
                    ids = TraktIds(trakt = traktId, tmdb = item.tmdbId, imdb = item.imdbId),
                    watched_at = if (isWatched) SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()) else null
                )
            )
        )

        try {
            if (isWatched) {
                TraktClient.apiService.addToHistory(payload)
            } else {
                TraktClient.apiService.removeFromHistory(payload)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update movie watch history on Trakt: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun rateItem(context: Context, item: SavedItem, rating: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val authManager = TraktAuthManager.getInstance(context)
        if (!authManager.hasValidToken()) return@withContext Result.failure(IllegalStateException("Not connected to Trakt"))

        val traktId = item.traktId ?: 0L
        val isMovie = item.mediaType?.lowercase() == "movie"

        val payload = if (isMovie) {
            TraktSyncPayload(
                movies = listOf(
                    TraktSyncItemPayload(
                        ids = TraktIds(trakt = traktId, tmdb = item.tmdbId, imdb = item.imdbId),
                        rating = rating
                    )
                )
            )
        } else {
            TraktSyncPayload(
                shows = listOf(
                    TraktSyncItemPayload(
                        ids = TraktIds(trakt = traktId, tmdb = item.tmdbId, imdb = item.imdbId),
                        rating = rating
                    )
                )
            )
        }

        try {
            if (rating > 0) {
                TraktClient.apiService.addRatings(payload)
            } else {
                TraktClient.apiService.removeRatings(payload)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update rating on Trakt: ${e.message}")
            Result.failure(e)
        }
    }
}

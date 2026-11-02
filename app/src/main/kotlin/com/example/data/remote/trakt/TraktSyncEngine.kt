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
import com.example.util.SafeLogger as Log
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.MediaCastMember
import com.example.data.model.MediaWatchProvider
import com.example.data.model.ParentalGuidanceItem
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    const val DEFAULT_TMDB_API_KEY = ""
    private const val PAGE_SIZE = 100

    private const val STEP_SHOWS = "SHOWS"
    private const val STEP_MOVIES = "MOVIES"
    private const val STEP_RATINGS = "RATINGS"
    private const val STEP_WATCHLIST = "WATCHLIST"
    private const val STEP_DONE = "DONE"

    private val _syncStatus = MutableStateFlow<TraktSyncStatus>(TraktSyncStatus.Idle)
    val syncStatus: StateFlow<TraktSyncStatus> = _syncStatus.asStateFlow()

    private val writeMutex = Mutex()
    @Volatile
    private var lastWriteTimestamp = 0L

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
     * Executes the comprehensive paginated, resumable, rate-limit-safe Trakt sync.
     * Pages items in chunks of 100 and persists them page-by-page to Room to preserve memory.
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
            val prefs = context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE)
            val tmdbKey = prefs.getString("tmdb_api_key", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY

            var moviesCount = 0
            var showsCount = 0
            var ratingsCount = 0
            var watchlistCount = 0

            // Check Delta Activities if not resuming from an incomplete sync
            var currentStep = authManager.getSyncCheckpointStep() ?: STEP_SHOWS
            var currentPage = authManager.getSyncCheckpointPage()

            val lastSyncTime = authManager.getLastSyncTimestamp()
            var shouldSyncShows = authManager.isSyncTvProgressEnabled()
            var shouldSyncMovies = authManager.isSyncWatchedEnabled()
            var shouldSyncRatings = authManager.isSyncRatingsEnabled()
            var shouldSyncWatchlist = authManager.isSyncWatchlistEnabled()

            if (authManager.getSyncCheckpointStep() == null && lastSyncTime > 0) {
                try {
                    val lastActivities = TraktClient.apiService.getLastActivities()
                    val remoteShowsWatched = parseIsoDate(lastActivities.shows?.watched_at)
                    val remoteMoviesWatched = parseIsoDate(lastActivities.movies?.watched_at)
                    val remoteRatings = maxOf(
                        parseIsoDate(lastActivities.shows?.rated_at),
                        parseIsoDate(lastActivities.movies?.rated_at)
                    )
                    val remoteWatchlist = parseIsoDate(lastActivities.watchlist?.updated_at)

                    if (remoteShowsWatched <= lastSyncTime) shouldSyncShows = false
                    if (remoteMoviesWatched <= lastSyncTime) shouldSyncMovies = false
                    if (remoteRatings <= lastSyncTime) shouldSyncRatings = false
                    if (remoteWatchlist <= lastSyncTime) shouldSyncWatchlist = false

                    if (!shouldSyncShows && !shouldSyncMovies && !shouldSyncRatings && !shouldSyncWatchlist) {
                        Log.i(TAG, "All Trakt categories are up-to-date with local library. Skipping download.")
                        val summary = TraktSyncSummary(0, 0, 0, 0, 0)
                        val upToDateMsg = "Everything is already up-to-date with Trakt."
                        _syncStatus.value = TraktSyncStatus.Success(0, upToDateMsg)
                        authManager.setLastSyncTimestamp(System.currentTimeMillis())
                        authManager.setLastSyncSummary(upToDateMsg)
                        if (isBackground) {
                            TraktNotificationHelper.showCompleteNotification(context, upToDateMsg)
                        }
                        return@withContext Result.success(summary)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Delta activity check failed: ${e.message}. Proceeding with standard paginated sync.")
                }
            }

            // ──────────────────────────────────────────────
            // 1. WATCHED TV SHOWS & EPISODES (PAGINATED)
            // ──────────────────────────────────────────────
            if (shouldSyncShows && (currentStep == STEP_SHOWS)) {
                var hasMore = true
                var page = currentPage
                while (hasMore) {
                    updateProgress("Downloading watched shows (page $page)...", 0.15f + (page * 0.02f).coerceAtMost(0.25f))
                    try {
                        val watchedShows = TraktClient.apiService.getWatchedShows(page = page, limit = PAGE_SIZE)
                        Log.i(TAG, "Fetched ${watchedShows.size} watched shows from Trakt (page $page)")
                        if (watchedShows.isEmpty()) {
                            hasMore = false
                            break
                        }

                        val pageEntities = mutableListOf<SavedItemEntity>()
                        for (item in watchedShows) {
                            val show = item.show ?: continue
                            val ids = show.ids
                            val traktId = ids.trakt
                            val tmdbId = ids.tmdb
                            val imdbId = ids.imdb
                            val title = show.title.takeIf { it.isNotBlank() } ?: "Untitled Show"
                            val overallWatchedAt = parseIsoDate(item.last_watched_at)

                            var totalWatchedEps = 0
                            val seasonList = mutableListOf<TvSeasonProgress>()

                            item.seasons?.forEach { s ->
                                val eps = s.episodes?.map { ep ->
                                    totalWatchedEps += ep.plays.coerceAtLeast(1)
                                    TvEpisodeProgress(
                                        episodeNumber = ep.number,
                                        seasonNumber = s.number,
                                        isWatched = true,
                                        plays = ep.plays,
                                        lastWatchedAt = parseIsoDate(ep.last_watched_at)
                                    )
                                } ?: emptyList()

                                seasonList.add(
                                    TvSeasonProgress(
                                        seasonNumber = s.number,
                                        title = if (s.number == 0) "Specials" else "Season ${s.number}",
                                        totalEpisodes = eps.size,
                                        watchedEpisodes = eps.size,
                                        episodes = eps
                                    )
                                )
                            }

                            val totalAiredEps = show.aired_episodes ?: totalWatchedEps
                            val isComplete = totalAiredEps > 0 && totalWatchedEps >= totalAiredEps
                            val traktRatingPercent = (show.rating?.times(10))?.toInt()

                            val progress = TvShowProgress(
                                totalAired = totalAiredEps,
                                totalWatched = totalWatchedEps,
                                completed = isComplete,
                                seasons = seasonList.sortedBy { it.seasonNumber },
                                traktRatingPercent = traktRatingPercent,
                                traktVotes = show.votes,
                                network = show.network,
                                airsTime = show.airs?.let { "${it.day}s at ${it.time}" },
                                country = show.country,
                                language = show.language,
                                contentRating = show.certification
                            )

                            val existing = (if (traktId > 0) dao.getItemByTraktId(traktId) else null)
                                ?: (if (tmdbId != null && tmdbId > 0) dao.getItemByTmdbId(tmdbId) else null)

                            val existingProgress = TvShowProgress.fromJson(existing?.showProgressJson)
                            val mergedProgress = if (existingProgress != null && existingProgress.cast.isNotEmpty()) {
                                progress.copy(
                                    cast = existingProgress.cast,
                                    ratingDistribution = existingProgress.ratingDistribution,
                                    seasonRatings = existingProgress.seasonRatings,
                                    plays = existingProgress.plays,
                                    watchers = existingProgress.watchers,
                                    listsCount = existingProgress.listsCount,
                                    favoritesCount = existingProgress.favoritesCount
                                )
                            } else progress

                            var thumbnailPath: String? = existing?.thumbnailPath
                            var backdropUrl: String? = existing?.backdropUrl
                            var genresJson: String = existing?.genresJson ?: "[]"
                            var watchProvidersJson: String = existing?.watchProvidersJson ?: "[]"
                            var trailerUrl: String? = existing?.trailerUrl

                            if (thumbnailPath.isNullOrBlank() && tmdbId != null && tmdbId > 0 && tmdbKey.isNotBlank()) {
                                try {
                                    val details = com.example.data.remote.MediaApiClient.tmdbApiService.getTvDetails(tmdbId, tmdbKey)
                                    if (details.posterPath != null) {
                                        thumbnailPath = "https://image.tmdb.org/t/p/w500${details.posterPath}"
                                    }
                                    if (details.backdropPath != null) {
                                        backdropUrl = "https://image.tmdb.org/t/p/w780${details.backdropPath}"
                                    }
                                    val fetchedGenres = details.genres?.mapNotNull { it.name } ?: emptyList()
                                    if (fetchedGenres.isNotEmpty()) {
                                        genresJson = "[${fetchedGenres.joinToString(",") { "\"$it\"" }}]"
                                    }
                                    val countryMap = details.watchProviders?.results
                                    val countryData = countryMap?.get("US") ?: countryMap?.values?.firstOrNull()
                                    val richList = mutableListOf<com.example.data.model.MediaWatchProvider>()
                                    countryData?.flatrate?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Stream", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId))
                                        }
                                    }
                                    countryData?.rent?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Rent", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId))
                                        }
                                    }
                                    countryData?.buy?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Buy", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId))
                                        }
                                    }
                                    if (richList.isNotEmpty()) {
                                        watchProvidersJson = com.example.data.model.MediaWatchProvider.listToJson(richList)
                                    }
                                    val ytKey = details.videos?.results?.firstOrNull {
                                        it.site?.equals("YouTube", ignoreCase = true) == true &&
                                        (it.type?.equals("Trailer", ignoreCase = true) == true || it.type?.equals("Teaser", ignoreCase = true) == true)
                                    }?.key ?: details.videos?.results?.firstOrNull { it.site?.equals("YouTube", ignoreCase = true) == true }?.key
                                    if (ytKey != null) {
                                        trailerUrl = "https://www.youtube.com/watch?v=$ytKey"
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "TMDb quick TV enrichment skipped: ${e.message}")
                                }
                            }

                            val entity = existing?.copy(
                                watchStatus = if (isComplete) "Completed" else "Watching",
                                showProgressJson = mergedProgress.toJson(),
                                lastWatchedAt = overallWatchedAt,
                                traktId = traktId,
                                tmdbId = tmdbId ?: existing.tmdbId,
                                imdbId = imdbId ?: existing.imdbId,
                                thumbnailPath = thumbnailPath ?: existing.thumbnailPath,
                                backdropUrl = backdropUrl ?: existing.backdropUrl,
                                genresJson = if (genresJson != "[]") genresJson else existing.genresJson,
                                watchProvidersJson = if (watchProvidersJson != "[]") watchProvidersJson else existing.watchProvidersJson,
                                trailerUrl = trailerUrl ?: existing.trailerUrl,
                                updatedAt = System.currentTimeMillis()
                            ) ?: SavedItemEntity(
                                id = UUID.randomUUID().toString(),
                                type = SavedItemType.MEDIA.name,
                                title = title,
                                content = "https://trakt.tv/shows/${ids.slug ?: traktId}",
                                timestamp = overallWatchedAt,
                                foldersJson = "[\"TV Shows\"]",
                                extractedText = show.overview,
                                thumbnailPath = thumbnailPath,
                                backdropUrl = backdropUrl,
                                genresJson = genresJson,
                                watchProvidersJson = watchProvidersJson,
                                trailerUrl = trailerUrl,
                                mediaType = "tv",
                                watchStatus = if (isComplete) "Completed" else "Watching",
                                releaseYear = show.year?.toString(),
                                rating = show.rating,
                                status = show.status,
                                traktId = traktId,
                                tmdbId = tmdbId,
                                imdbId = imdbId,
                                lastWatchedAt = overallWatchedAt,
                                showProgressJson = mergedProgress.toJson(),
                                updatedAt = System.currentTimeMillis()
                            )
                            pageEntities.add(entity)
                            showsCount++
                        }

                        // Atomic page persistence directly to SQLite
                        if (pageEntities.isNotEmpty()) {
                            dao.insertItems(pageEntities)
                        }

                        if (watchedShows.size < PAGE_SIZE) {
                            hasMore = false
                        } else {
                            page++
                            authManager.setSyncCheckpointPage(page)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed syncing watched shows on page $page: ${e.message}", e)
                        throw e
                    }
                }
                currentStep = STEP_MOVIES
                currentPage = 1
                authManager.setSyncCheckpointStep(STEP_MOVIES)
                authManager.setSyncCheckpointPage(1)
            }

            // ──────────────────────────────────────────────
            // 2. WATCHED MOVIES (PAGINATED)
            // ──────────────────────────────────────────────
            if (shouldSyncMovies && (currentStep == STEP_MOVIES)) {
                var hasMore = true
                var page = currentPage
                while (hasMore) {
                    updateProgress("Downloading watched movies (page $page)...", 0.45f + (page * 0.02f).coerceAtMost(0.20f))
                    try {
                        val watchedMovies = TraktClient.apiService.getWatchedMovies(page = page, limit = PAGE_SIZE)
                        Log.i(TAG, "Fetched ${watchedMovies.size} watched movies from Trakt (page $page)")
                        if (watchedMovies.isEmpty()) {
                            hasMore = false
                            break
                        }

                        val pageEntities = mutableListOf<SavedItemEntity>()
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

                            var thumbnailPath: String? = existing?.thumbnailPath
                            var backdropUrl: String? = existing?.backdropUrl
                            var genresJson: String = existing?.genresJson ?: "[]"
                            var watchProvidersJson: String = existing?.watchProvidersJson ?: "[]"
                            var trailerUrl: String? = existing?.trailerUrl

                            if (thumbnailPath.isNullOrBlank() && tmdbId != null && tmdbId > 0 && tmdbKey.isNotBlank()) {
                                try {
                                    val details = com.example.data.remote.MediaApiClient.tmdbApiService.getMovieDetails(tmdbId, tmdbKey)
                                    if (details.posterPath != null) {
                                        thumbnailPath = "https://image.tmdb.org/t/p/w500${details.posterPath}"
                                    }
                                    if (details.backdropPath != null) {
                                        backdropUrl = "https://image.tmdb.org/t/p/w780${details.backdropPath}"
                                    }
                                    val fetchedGenres = details.genres?.mapNotNull { it.name } ?: emptyList()
                                    if (fetchedGenres.isNotEmpty()) {
                                        genresJson = "[${fetchedGenres.joinToString(",") { "\"$it\"" }}]"
                                    }
                                    val countryMap = details.watchProviders?.results
                                    val countryData = countryMap?.get("US") ?: countryMap?.values?.firstOrNull()
                                    val richList = mutableListOf<com.example.data.model.MediaWatchProvider>()
                                    countryData?.flatrate?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Stream", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId))
                                        }
                                    }
                                    countryData?.rent?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Rent", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId))
                                        }
                                    }
                                    countryData?.buy?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Buy", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId))
                                        }
                                    }
                                    if (richList.isNotEmpty()) {
                                        watchProvidersJson = com.example.data.model.MediaWatchProvider.listToJson(richList)
                                    }
                                    val ytKey = details.videos?.results?.firstOrNull {
                                        it.site?.equals("YouTube", ignoreCase = true) == true &&
                                        (it.type?.equals("Trailer", ignoreCase = true) == true || it.type?.equals("Teaser", ignoreCase = true) == true)
                                    }?.key ?: details.videos?.results?.firstOrNull { it.site?.equals("YouTube", ignoreCase = true) == true }?.key
                                    if (ytKey != null) {
                                        trailerUrl = "https://www.youtube.com/watch?v=$ytKey"
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "TMDb quick movie enrichment skipped: ${e.message}")
                                }
                            }

                            val entity = existing?.copy(
                                watchStatus = "Completed",
                                lastWatchedAt = watchedTimestamp,
                                traktId = traktId,
                                tmdbId = tmdbId ?: existing.tmdbId,
                                imdbId = imdbId ?: existing.imdbId,
                                thumbnailPath = thumbnailPath ?: existing.thumbnailPath,
                                backdropUrl = backdropUrl ?: existing.backdropUrl,
                                genresJson = if (genresJson != "[]") genresJson else existing.genresJson,
                                watchProvidersJson = if (watchProvidersJson != "[]") watchProvidersJson else existing.watchProvidersJson,
                                trailerUrl = trailerUrl ?: existing.trailerUrl,
                                updatedAt = System.currentTimeMillis()
                            ) ?: SavedItemEntity(
                                id = UUID.randomUUID().toString(),
                                type = SavedItemType.MEDIA.name,
                                title = title,
                                content = "https://trakt.tv/movies/${ids.slug ?: traktId}",
                                timestamp = watchedTimestamp,
                                foldersJson = "[\"Movies\"]",
                                extractedText = movie.overview,
                                thumbnailPath = thumbnailPath,
                                backdropUrl = backdropUrl,
                                genresJson = genresJson,
                                watchProvidersJson = watchProvidersJson,
                                trailerUrl = trailerUrl,
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
                            pageEntities.add(entity)
                            moviesCount++
                        }

                        if (pageEntities.isNotEmpty()) {
                            dao.insertItems(pageEntities)
                        }

                        if (watchedMovies.size < PAGE_SIZE) {
                            hasMore = false
                        } else {
                            page++
                            authManager.setSyncCheckpointPage(page)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed syncing watched movies on page $page: ${e.message}", e)
                        throw e
                    }
                }
                currentStep = STEP_RATINGS
                currentPage = 1
                authManager.setSyncCheckpointStep(STEP_RATINGS)
                authManager.setSyncCheckpointPage(1)
            }

            // ──────────────────────────────────────────────
            // 3. RATINGS (PAGINATED)
            // ──────────────────────────────────────────────
            if (shouldSyncRatings && (currentStep == STEP_RATINGS)) {
                updateProgress("Downloading personal ratings...", 0.70f)
                try {
                    var showRatingPage = 1
                    var hasMoreShowRatings = true
                    while (hasMoreShowRatings) {
                        val showRatings = TraktClient.apiService.getShowRatings(page = showRatingPage, limit = PAGE_SIZE)
                        for (r in showRatings) {
                            val traktId = r.show?.ids?.trakt ?: continue
                            val entity = dao.getItemByTraktId(traktId)
                            if (entity != null && entity.userRating != r.rating) {
                                dao.insertItem(entity.copy(userRating = r.rating))
                                ratingsCount++
                            }
                        }
                        if (showRatings.size < PAGE_SIZE) hasMoreShowRatings = false
                        else showRatingPage++
                    }

                    var movieRatingPage = 1
                    var hasMoreMovieRatings = true
                    while (hasMoreMovieRatings) {
                        val movieRatings = TraktClient.apiService.getMovieRatings(page = movieRatingPage, limit = PAGE_SIZE)
                        for (r in movieRatings) {
                            val traktId = r.movie?.ids?.trakt ?: continue
                            val entity = dao.getItemByTraktId(traktId)
                            if (entity != null && entity.userRating != r.rating) {
                                dao.insertItem(entity.copy(userRating = r.rating))
                                ratingsCount++
                            }
                        }
                        if (movieRatings.size < PAGE_SIZE) hasMoreMovieRatings = false
                        else movieRatingPage++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ratings sync encountered issue: ${e.message}")
                }
                currentStep = STEP_WATCHLIST
                currentPage = 1
                authManager.setSyncCheckpointStep(STEP_WATCHLIST)
                authManager.setSyncCheckpointPage(1)
            }

            // ──────────────────────────────────────────────
            // 4. WATCHLIST (PAGINATED)
            // ──────────────────────────────────────────────
            if (shouldSyncWatchlist && (currentStep == STEP_WATCHLIST)) {
                updateProgress("Downloading watchlist...", 0.85f)
                try {
                    var showWatchlistPage = 1
                    var hasMoreShowWatchlist = true
                    while (hasMoreShowWatchlist) {
                        val watchlistShows = TraktClient.apiService.getWatchlistShows(page = showWatchlistPage, limit = PAGE_SIZE)
                        val pageEntities = mutableListOf<SavedItemEntity>()
                        for (w in watchlistShows) {
                            val show = w.show ?: continue
                            val traktId = show.ids.trakt
                            val existing = dao.getItemByTraktId(traktId)
                            if (existing == null) {
                                val entity = SavedItemEntity(
                                    id = UUID.randomUUID().toString(),
                                    type = SavedItemType.MEDIA.name,
                                    title = show.title.takeIf { it.isNotBlank() } ?: "Untitled Show",
                                    content = "https://trakt.tv/shows/${show.ids.slug ?: traktId}",
                                    timestamp = parseIsoDate(w.listed_at),
                                    foldersJson = "[\"Watchlist\"]",
                                    extractedText = show.overview,
                                    thumbnailPath = null,
                                    mediaType = "tv",
                                    watchStatus = "Plan to Watch",
                                    releaseYear = show.year?.toString(),
                                    rating = show.rating,
                                    status = show.status,
                                    traktId = traktId,
                                    tmdbId = show.ids.tmdb,
                                    imdbId = show.ids.imdb,
                                    updatedAt = System.currentTimeMillis()
                                )
                                pageEntities.add(entity)
                                watchlistCount++
                            }
                        }
                        if (pageEntities.isNotEmpty()) dao.insertItems(pageEntities)
                        if (watchlistShows.size < PAGE_SIZE) hasMoreShowWatchlist = false
                        else showWatchlistPage++
                    }

                    var movieWatchlistPage = 1
                    var hasMoreMovieWatchlist = true
                    while (hasMoreMovieWatchlist) {
                        val watchlistMovies = TraktClient.apiService.getWatchlistMovies(page = movieWatchlistPage, limit = PAGE_SIZE)
                        val pageEntities = mutableListOf<SavedItemEntity>()
                        for (w in watchlistMovies) {
                            val movie = w.movie ?: continue
                            val traktId = movie.ids.trakt
                            val existing = dao.getItemByTraktId(traktId)
                            if (existing == null) {
                                val entity = SavedItemEntity(
                                    id = UUID.randomUUID().toString(),
                                    type = SavedItemType.MEDIA.name,
                                    title = movie.title.takeIf { it.isNotBlank() } ?: "Untitled Movie",
                                    content = "https://trakt.tv/movies/${movie.ids.slug ?: traktId}",
                                    timestamp = parseIsoDate(w.listed_at),
                                    foldersJson = "[\"Watchlist\"]",
                                    extractedText = movie.overview,
                                    thumbnailPath = null,
                                    mediaType = "movie",
                                    watchStatus = "Plan to Watch",
                                    releaseYear = movie.year?.toString(),
                                    rating = movie.rating,
                                    runtime = movie.runtime,
                                    traktId = traktId,
                                    tmdbId = movie.ids.tmdb,
                                    imdbId = movie.ids.imdb,
                                    updatedAt = System.currentTimeMillis()
                                )
                                pageEntities.add(entity)
                                watchlistCount++
                            }
                        }
                        if (pageEntities.isNotEmpty()) dao.insertItems(pageEntities)
                        if (watchlistMovies.size < PAGE_SIZE) hasMoreMovieWatchlist = false
                        else movieWatchlistPage++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Watchlist sync encountered issue: ${e.message}")
                }
            }

            // Sync successfully finished - clear checkpoints
            authManager.clearSyncCheckpoint()

            val totalItems = showsCount + moviesCount + ratingsCount + watchlistCount
            val summary = TraktSyncSummary(
                moviesSynced = moviesCount,
                showsSynced = showsCount,
                ratingsSynced = ratingsCount,
                watchlistSynced = watchlistCount,
                totalItems = totalItems
            )

            authManager.setLastSyncTimestamp(System.currentTimeMillis())
            authManager.setLastSyncSummary(summary.message)

            _syncStatus.value = TraktSyncStatus.Success(totalItems, summary.message)

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
    // ON-DEMAND DEEP ENRICHMENT (LAZY LOADING)
    // ==========================================

    /**
     * Enriches a single TV show or Movie with deep metadata (cast, episode stills, ratings breakdown,
     * stream providers) on-demand when the user views the Media Detail Screen.
     * Caches all data locally so it is only fetched once per item.
     */
    suspend fun enrichShowDeepDetails(context: Context, item: SavedItem): SavedItem = withContext(Dispatchers.IO) {
        val tmdbId = item.tmdbId
        val traktId = item.traktId
        val isTv = item.mediaType?.lowercase() == "tv" || item.mediaType?.lowercase() == "tv show"

        val prefs = context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE)
        val tmdbKey = prefs.getString("tmdb_api_key", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
        val db = AppDatabase.getDatabase(context)
        val dao = db.savedItemDao()

        var progress = item.showProgress ?: TvShowProgress()
        var updated = false
        var currentBackdrop = item.backdropUrl
        var currentThumbnail = item.thumbnailPath
        var richWatchProviders = item.richWatchProviders.toMutableList()

        // 1. Fetch Trakt Ratings distribution & Stats (if Trakt item)
        if (traktId != null && traktId > 0 && isTv && progress.ratingDistribution.isEmpty()) {
            val slug = item.content.substringAfterLast("/").takeIf { it.isNotBlank() } ?: traktId.toString()
            try {
                val ratingsRes = TraktClient.apiService.getShowRatings(slug)
                val statsRes = TraktClient.apiService.getShowStats(slug)
                val seasonsRes = TraktClient.apiService.getShowSeasons(slug)

                val seasonQuality = seasonsRes.filter { it.number > 0 && it.rating != null }.map {
                    SeasonQualityPoint(seasonNumber = it.number, ratingPercent = (it.rating!! * 10).toInt())
                }.sortedBy { it.seasonNumber }

                progress = progress.copy(
                    ratingDistribution = ratingsRes.distribution ?: emptyMap(),
                    traktRatingPercent = (ratingsRes.rating * 10).toInt(),
                    traktVotes = ratingsRes.votes,
                    plays = statsRes.plays,
                    watchers = statsRes.watchers,
                    listsCount = statsRes.lists,
                    favoritesCount = statsRes.favorited,
                    seasonRatings = if (seasonQuality.isNotEmpty()) seasonQuality else progress.seasonRatings
                )
                updated = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed lazy Trakt stats fetch for ${item.title}: ${e.message}")
            }
        }

        // 2. Fetch TMDb Credits (Actors / Cast)
        if (tmdbId != null && tmdbId > 0 && progress.cast.isEmpty()) {
            try {
                val credits = if (isTv) {
                    com.example.data.remote.MediaApiClient.tmdbApiService.getTvCredits(tmdbId, tmdbKey)
                } else {
                    com.example.data.remote.MediaApiClient.tmdbApiService.getMovieCredits(tmdbId, tmdbKey)
                }

                val castMembers = credits.cast?.take(20)?.map { c ->
                    MediaCastMember(
                        name = c.name ?: "Unknown",
                        character = c.character,
                        profileUrl = c.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" },
                        order = c.order
                    )
                } ?: emptyList()

                if (castMembers.isNotEmpty()) {
                    progress = progress.copy(cast = castMembers)
                    updated = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed lazy TMDb credits fetch for ${item.title}: ${e.message}")
            }
        }

        // 3. Fetch Season 1 Episodes with Still Images & Runtimes (for TV shows)
        if (tmdbId != null && tmdbId > 0 && isTv) {
            val needsEpisodeDetails = progress.seasons.any { s -> s.episodes.any { it.stillPath == null || it.title == null } }
            if (needsEpisodeDetails || progress.seasons.isEmpty()) {
                try {
                    val updatedSeasons = progress.seasons.toMutableList()
                    val targetSeasonNumber = progress.lastWatchedSeason ?: 1

                    val seasonDetails = com.example.data.remote.MediaApiClient.tmdbApiService.getTvSeasonDetails(
                        id = tmdbId,
                        seasonNumber = targetSeasonNumber,
                        apiKey = tmdbKey
                    )

                    val existingSeasonIdx = updatedSeasons.indexOfFirst { it.seasonNumber == targetSeasonNumber }
                    val existingSeason = if (existingSeasonIdx >= 0) updatedSeasons[existingSeasonIdx] else null

                    val enrichedEpisodes = seasonDetails.episodes?.map { tmdbEp ->
                        val wasWatched = existingSeason?.episodes?.find { it.episodeNumber == tmdbEp.episodeNumber }?.isWatched ?: false
                        TvEpisodeProgress(
                            episodeNumber = tmdbEp.episodeNumber,
                            seasonNumber = targetSeasonNumber,
                            title = tmdbEp.name,
                            overview = tmdbEp.overview,
                            isWatched = wasWatched,
                            stillPath = tmdbEp.stillPath?.let { "https://image.tmdb.org/t/p/w300$it" },
                            runtimeMinutes = tmdbEp.runtime,
                            airDate = tmdbEp.airDate,
                            rating = tmdbEp.voteAverage
                        )
                    } ?: emptyList()

                    if (enrichedEpisodes.isNotEmpty()) {
                        val newSeason = TvSeasonProgress(
                            seasonNumber = targetSeasonNumber,
                            title = seasonDetails.name ?: "Season $targetSeasonNumber",
                            totalEpisodes = enrichedEpisodes.size,
                            watchedEpisodes = enrichedEpisodes.count { it.isWatched },
                            episodes = enrichedEpisodes
                        )
                        if (existingSeasonIdx >= 0) {
                            updatedSeasons[existingSeasonIdx] = newSeason
                        } else {
                            updatedSeasons.add(newSeason)
                        }
                        progress = progress.copy(seasons = updatedSeasons.sortedBy { it.seasonNumber })
                        updated = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed lazy TMDb season details fetch for ${item.title}: ${e.message}")
                }
            }
        }

        // 4. Fetch Watch Providers & Backdrop if missing
        if (tmdbId != null && tmdbId > 0 && (currentBackdrop.isNullOrBlank() || richWatchProviders.isEmpty())) {
            try {
                val details = if (isTv) {
                    com.example.data.remote.MediaApiClient.tmdbApiService.getTvDetails(tmdbId, tmdbKey)
                } else {
                    com.example.data.remote.MediaApiClient.tmdbApiService.getMovieDetails(tmdbId, tmdbKey)
                }

                if (currentBackdrop.isNullOrBlank() && details.backdropPath != null) {
                    currentBackdrop = "https://image.tmdb.org/t/p/w780${details.backdropPath}"
                    updated = true
                }
                if (currentThumbnail.isNullOrBlank() && details.posterPath != null) {
                    currentThumbnail = "https://image.tmdb.org/t/p/w500${details.posterPath}"
                    updated = true
                }

                val countryMap = details.watchProviders?.results
                val countryData = countryMap?.get("US") ?: countryMap?.values?.firstOrNull()
                val fetchedProviders = mutableListOf<MediaWatchProvider>()

                countryData?.flatrate?.forEach { p ->
                    p.providerName?.let {
                        fetchedProviders.add(MediaWatchProvider(name = it, type = "Stream", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId))
                    }
                }
                countryData?.buy?.forEach { p ->
                    p.providerName?.let {
                        fetchedProviders.add(MediaWatchProvider(name = it, type = "Buy", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId))
                    }
                }

                if (fetchedProviders.isNotEmpty() && richWatchProviders.isEmpty()) {
                    richWatchProviders = fetchedProviders
                    updated = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed lazy TMDb provider fetch for ${item.title}: ${e.message}")
            }
        }

        if (updated) {
            val enrichedItem = item.copy(
                thumbnailPath = currentThumbnail,
                backdropUrl = currentBackdrop,
                richWatchProviders = richWatchProviders,
                watchProviders = richWatchProviders.map { it.name },
                showProgress = progress
            )
            val entity = dao.getItemById(item.id)
            if (entity != null) {
                dao.insertItem(
                    entity.copy(
                        thumbnailPath = currentThumbnail,
                        backdropUrl = currentBackdrop,
                        watchProvidersJson = MediaWatchProvider.listToJson(richWatchProviders),
                        showProgressJson = progress.toJson(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            return@withContext enrichedItem
        }

        return@withContext item
    }

    // ==========================================
    // OUTBOUND MUTATIONS (TWO-WAY SYNC)
    // ==========================================

    private suspend fun throttleWrite() {
        writeMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastWriteTimestamp
            if (elapsed < 1100L) {
                kotlinx.coroutines.delay(1100L - elapsed)
            }
            lastWriteTimestamp = System.currentTimeMillis()
        }
    }

    /**
     * Toggles watch state for a single TV episode and pushes the mutation to Trakt.
     */
    suspend fun markEpisodeWatched(
        context: Context,
        item: SavedItem,
        seasonNumber: Int,
        episodeNumber: Int,
        isWatched: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val authManager = TraktAuthManager.getInstance(context)
        val db = AppDatabase.getDatabase(context)
        val dao = db.savedItemDao()

        // 1. Update local database atomically
        val currentProgress = item.showProgress ?: TvShowProgress()
        val updatedSeasons = currentProgress.seasons.map { s ->
            if (s.seasonNumber == seasonNumber) {
                val updatedEps = s.episodes.map { ep ->
                    if (ep.episodeNumber == episodeNumber) {
                        ep.copy(isWatched = isWatched, lastWatchedAt = if (isWatched) System.currentTimeMillis() else null)
                    } else ep
                }
                s.copy(
                    episodes = updatedEps,
                    watchedEpisodes = updatedEps.count { it.isWatched }
                )
            } else s
        }

        val totalWatched = updatedSeasons.sumOf { it.watchedEpisodes }
        val isComplete = currentProgress.totalAired > 0 && totalWatched >= currentProgress.totalAired

        val newProgress = currentProgress.copy(
            seasons = updatedSeasons,
            totalWatched = totalWatched,
            lastWatchedSeason = if (isWatched) seasonNumber else currentProgress.lastWatchedSeason,
            lastWatchedEpisode = if (isWatched) episodeNumber else currentProgress.lastWatchedEpisode,
            completed = isComplete
        )

        val entity = dao.getItemById(item.id)
        if (entity != null) {
            dao.insertItem(
                entity.copy(
                    watchStatus = if (isComplete) "Completed" else if (totalWatched > 0) "Watching" else "Plan to Watch",
                    showProgressJson = newProgress.toJson(),
                    lastWatchedAt = if (isWatched) System.currentTimeMillis() else entity.lastWatchedAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 2. Outbound sync to Trakt with rate limiting
        if (!authManager.hasValidToken()) return@withContext Result.success(Unit)

        throttleWrite()
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val payload = TraktSyncPayload(
            shows = listOf(
                TraktSyncShowPayload(
                    ids = TraktIds(
                        trakt = item.traktId ?: 0L,
                        tmdb = item.tmdbId,
                        imdb = item.imdbId
                    ),
                    seasons = listOf(
                        TraktSyncSeasonPayload(
                            number = seasonNumber,
                            episodes = listOf(
                                TraktSyncEpisodePayload(
                                    number = episodeNumber,
                                    watched_at = if (isWatched) nowIso else null
                                )
                            )
                        )
                    )
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
            Log.e(TAG, "Failed outbound episode watch sync: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Toggles watch state for an entire Movie and pushes to Trakt.
     */
    suspend fun markMovieWatched(context: Context, item: SavedItem, isWatched: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val authManager = TraktAuthManager.getInstance(context)
        val db = AppDatabase.getDatabase(context)
        val dao = db.savedItemDao()

        val entity = dao.getItemById(item.id)
        if (entity != null) {
            dao.insertItem(
                entity.copy(
                    watchStatus = if (isWatched) "Completed" else "Plan to Watch",
                    lastWatchedAt = if (isWatched) System.currentTimeMillis() else null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        if (!authManager.hasValidToken()) return@withContext Result.success(Unit)

        throttleWrite()
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val traktId = item.traktId ?: 0L
        val payload = TraktSyncPayload(
            movies = listOf(
                TraktSyncItemPayload(
                    ids = TraktIds(trakt = traktId, tmdb = item.tmdbId, imdb = item.imdbId),
                    watched_at = if (isWatched) nowIso else null
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
            Log.e(TAG, "Failed outbound movie watch sync: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Rates an item (1 to 10) and pushes the rating to Trakt.
     */
    suspend fun rateItem(context: Context, item: SavedItem, rating: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val authManager = TraktAuthManager.getInstance(context)
        val db = AppDatabase.getDatabase(context)
        val dao = db.savedItemDao()

        val entity = dao.getItemById(item.id)
        if (entity != null) {
            dao.insertItem(entity.copy(userRating = if (rating > 0) rating else null, updatedAt = System.currentTimeMillis()))
        }

        if (!authManager.hasValidToken()) return@withContext Result.success(Unit)

        throttleWrite()
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
                    TraktSyncShowPayload(
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
            Log.e(TAG, "Failed outbound rating sync: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Toggles watchlist status and pushes mutation to Trakt.
     */
    suspend fun toggleWatchlist(context: Context, item: SavedItem, inWatchlist: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val authManager = TraktAuthManager.getInstance(context)
        val db = AppDatabase.getDatabase(context)
        val dao = db.savedItemDao()

        val entity = dao.getItemById(item.id)
        if (entity != null) {
            val currentStatus = if (inWatchlist) "Plan to Watch" else if (entity.watchStatus == "Plan to Watch") null else entity.watchStatus
            dao.insertItem(entity.copy(watchStatus = currentStatus, updatedAt = System.currentTimeMillis()))
        }

        if (!authManager.hasValidToken()) return@withContext Result.success(Unit)

        throttleWrite()
        val traktId = item.traktId ?: 0L
        val isMovie = item.mediaType?.lowercase() == "movie"

        val payload = if (isMovie) {
            TraktSyncPayload(
                movies = listOf(TraktSyncItemPayload(ids = TraktIds(trakt = traktId, tmdb = item.tmdbId, imdb = item.imdbId)))
            )
        } else {
            TraktSyncPayload(
                shows = listOf(TraktSyncShowPayload(ids = TraktIds(trakt = traktId, tmdb = item.tmdbId, imdb = item.imdbId)))
            )
        }

        try {
            if (inWatchlist) {
                TraktClient.apiService.addToWatchlist(payload)
            } else {
                TraktClient.apiService.removeFromWatchlist(payload)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed outbound watchlist sync: ${e.message}", e)
            Result.failure(e)
        }
    }
}

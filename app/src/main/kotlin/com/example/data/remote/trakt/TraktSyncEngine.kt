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
import androidx.room.withTransaction
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
    internal fun storedTmdbKey(context: Context): String = com.example.util.SecretPreferences(
        context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE), "cobalt_prefs"
    ).get("tmdb_api_key")?.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY

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

    var apiServiceProvider: (() -> TraktApiService)? = null

    fun getApiService(): TraktApiService {
        return apiServiceProvider?.invoke() ?: TraktClient.apiService
    }

    fun onSessionInvalidated() {
        _syncStatus.value = TraktSyncStatus.Idle
    }

    private val syncMutex = Mutex()
    private val writeMutex = Mutex()
    @Volatile
    private var lastWriteTimestamp = 0L

    private fun imageUrl(images: Map<String, List<String>>?, type: String): String? {
        val raw = images?.get(type)?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val uri = android.net.Uri.parse(if (raw.startsWith("https://")) raw else "https://$raw")
        return uri.toString().takeIf { uri.scheme == "https" && uri.host?.endsWith(".trakt.tv") == true }
    }

    private suspend fun locallyRemoved(db: AppDatabase, kind: String, ids: TraktIds) =
        db.mediaTombstoneDao().blocked(kind, ids.trakt.toString(), ids.tmdb?.toString().orEmpty(), ids.imdb.orEmpty())

    internal fun activityTimestamp(iso: String?): Long = try {
        if (iso.isNullOrBlank()) 0L else java.time.Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) { 0L }

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
    ): Result<TraktSyncSummary> = syncMutex.withLock {
        performSyncLocked(context, isBackground, onProgressUpdate)
    }

    private suspend fun performSyncLocked(
        context: Context,
        isBackground: Boolean = false,
        onProgressUpdate: ((String, Float) -> Unit)? = null
    ): Result<TraktSyncSummary> = withContext(Dispatchers.IO) {
        val authManager = TraktAuthManager.getInstance(context)
        val sessionEpoch = authManager.currentSessionEpoch()
        val dirtyCategories = listOf("movies", "shows", "ratings", "watchlist").associateWith { authManager.dirtyCategory(it) }
        if (authManager.isSessionInvalid(sessionEpoch) || !authManager.hasValidToken()) {
            val err = "Trakt is not connected. Connect account to sync."
            _syncStatus.value = TraktSyncStatus.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        suspend fun checkpoint(step: String, page: Int) {
            check(authManager.runIfSessionValid(sessionEpoch) { authManager.setSyncCheckpoint(step, page) }) { "Trakt account changed" }
            TraktOutbox.drain(context, getApiService())
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
            authManager.fetchAndSaveUserProfile().getOrThrow()
            TraktOutbox.drain(context, getApiService())

            val db = AppDatabase.getDatabase(context)
            val dao = db.savedItemDao()
            val prefs = context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE)
            val tmdbKey = storedTmdbKey(context)

            var moviesCount = 0
            var showsCount = 0
            var ratingsCount = 0
            var watchlistCount = 0

            // Check Delta Activities if not resuming from an incomplete sync
            var currentStep = authManager.getSyncCheckpointStep() ?: STEP_SHOWS
            var currentPage = authManager.getSyncCheckpointPage()
            fun phaseRank(step: String) = when {
                step.startsWith(STEP_SHOWS) -> 0
                step.startsWith(STEP_MOVIES) -> 1
                step.startsWith(STEP_RATINGS) -> 2
                step.startsWith(STEP_WATCHLIST) -> 3
                else -> 4
            }
            val enabledDirty = listOf(
                Triple("shows", 0, authManager.isSyncTvProgressEnabled()),
                Triple("movies", 1, authManager.isSyncWatchedEnabled()),
                Triple("ratings", 2, authManager.isSyncRatingsEnabled()),
                Triple("watchlist", 3, authManager.isSyncWatchlistEnabled()))
            if (enabledDirty.any { (category, rank, enabled) -> enabled && dirtyCategories[category] != null && rank < phaseRank(currentStep) }) {
                currentStep = STEP_SHOWS; currentPage = 1
                checkpoint(STEP_SHOWS, 1)
            }


            val lastSyncTime = authManager.getLastSyncTimestamp()
            var shouldSyncShows = authManager.isSyncTvProgressEnabled()
            var shouldSyncMovies = authManager.isSyncWatchedEnabled()
            var shouldSyncRatings = authManager.isSyncRatingsEnabled()
            var shouldSyncWatchlist = authManager.isSyncWatchlistEnabled()

            if (authManager.getSyncCheckpointStep() == null && lastSyncTime > 0) {
                try {
                    val lastActivities = getApiService().getLastActivities()
                    val remoteShowsWatched = activityTimestamp(lastActivities.shows?.watched_at)
                    val remoteMoviesWatched = activityTimestamp(lastActivities.movies?.watched_at)
                    val remoteRatings = maxOf(
                        activityTimestamp(lastActivities.shows?.rated_at),
                        activityTimestamp(lastActivities.movies?.rated_at)
                    )
                    val remoteWatchlist = activityTimestamp(lastActivities.watchlist?.updated_at)

                    if (dirtyCategories["shows"] == null && remoteShowsWatched <= lastSyncTime) shouldSyncShows = false
                    if (dirtyCategories["movies"] == null && remoteMoviesWatched <= lastSyncTime) shouldSyncMovies = false
                    if (dirtyCategories["ratings"] == null && remoteRatings <= lastSyncTime) shouldSyncRatings = false
                    if (dirtyCategories["watchlist"] == null && remoteWatchlist <= lastSyncTime) shouldSyncWatchlist = false

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
                        val watchedShows = getApiService().getWatchedShows(page = page, limit = PAGE_SIZE)
                        Log.i(TAG, "Fetched ${watchedShows.size} watched shows from Trakt (page $page)")
                        if (watchedShows.isEmpty()) {
                            hasMore = false
                            break
                        }

                        val pageEntities = mutableListOf<SavedItemEntity>()
                        for (item in watchedShows) {
                            val show = item.show ?: continue
                            if (locallyRemoved(db, "tv", show.ids)) continue
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
                                    if (s.number > 0) totalWatchedEps++
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

                            val existing = (if (traktId > 0) dao.getItemByTraktId(traktId, "tv") else null)
                                ?: (if (tmdbId != null && tmdbId > 0) dao.getItemByTmdbId(tmdbId, "tv") else null)

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

                            var thumbnailPath: String? = existing?.thumbnailPath ?: imageUrl(show.images, "poster")
                            var backdropUrl: String? = existing?.backdropUrl ?: imageUrl(show.images, "fanart")
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
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Stream", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId, url = countryData?.link))
                                        }
                                    }
                                    countryData?.rent?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Rent", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId, url = countryData?.link))
                                        }
                                    }
                                    countryData?.buy?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Buy", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId, url = countryData?.link))
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

                        // Atomic page persistence directly to SQLite guarded by session lock
                        if (pageEntities.isNotEmpty()) {
                            val persisted = authManager.runIfSessionValid(sessionEpoch) {
                                TraktOutbox.persistInbound(db, pageEntities, authManager.syncSessionId())
                            }
                            if (!persisted) {
                                Log.i(TAG, "Session invalidated during shows sync; aborting.")
                                _syncStatus.value = TraktSyncStatus.Idle
                                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                            }
                        }

                        if (watchedShows.size < PAGE_SIZE) {
                            hasMore = false
                        } else {
                            page++
                            checkpoint(currentStep, page)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed syncing watched shows on page $page: ${e.message}", e)
                        throw e
                    }
                }
                if (authManager.isSyncTvProgressEnabled()) authManager.acknowledgeCategory("shows", dirtyCategories["shows"])
                currentStep = STEP_MOVIES
                currentPage = 1
                checkpoint(STEP_MOVIES, 1)
            }

            // Disabled or unchanged categories must not strand later enabled phases.
            if (currentStep == STEP_SHOWS) {
                currentStep = STEP_MOVIES; currentPage = 1
                checkpoint(STEP_MOVIES, 1)
            }

            // ──────────────────────────────────────────────
            // 2. WATCHED MOVIES (PAGINATED)
            // ──────────────────────────────────────────────
            if (authManager.isSessionInvalid(sessionEpoch)) {
                Log.i(TAG, "Session invalidated before movies sync; aborting.")
                _syncStatus.value = TraktSyncStatus.Idle
                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
            }

            if (shouldSyncMovies && (currentStep == STEP_MOVIES)) {
                var hasMore = true
                var page = currentPage
                while (hasMore) {
                    updateProgress("Downloading watched movies (page $page)...", 0.45f + (page * 0.02f).coerceAtMost(0.20f))
                    try {
                        val watchedMovies = getApiService().getWatchedMovies(page = page, limit = PAGE_SIZE)
                        Log.i(TAG, "Fetched ${watchedMovies.size} watched movies from Trakt (page $page)")
                        if (watchedMovies.isEmpty()) {
                            hasMore = false
                            break
                        }

                        val pageEntities = mutableListOf<SavedItemEntity>()
                        for (item in watchedMovies) {
                            val movie = item.movie ?: continue
                            if (locallyRemoved(db, "movie", movie.ids)) continue
                            val ids = movie.ids
                            val traktId = ids.trakt
                            val tmdbId = ids.tmdb
                            val imdbId = ids.imdb
                            val title = movie.title.takeIf { it.isNotBlank() } ?: "Untitled Movie"
                            val watchedTimestamp = parseIsoDate(item.last_watched_at)

                            val existing = (if (traktId > 0) dao.getItemByTraktId(traktId, "movie") else null)
                                ?: (if (tmdbId != null && tmdbId > 0) dao.getItemByTmdbId(tmdbId, "movie") else null)

                            var thumbnailPath: String? = existing?.thumbnailPath ?: imageUrl(movie.images, "poster")
                            var backdropUrl: String? = existing?.backdropUrl ?: imageUrl(movie.images, "fanart")
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
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Stream", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId, url = countryData?.link))
                                        }
                                    }
                                    countryData?.rent?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Rent", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId, url = countryData?.link))
                                        }
                                    }
                                    countryData?.buy?.forEach { p ->
                                        p.providerName?.let {
                                            richList.add(com.example.data.model.MediaWatchProvider(name = it, type = "Buy", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId, url = countryData?.link))
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
                            val persisted = authManager.runIfSessionValid(sessionEpoch) {
                                TraktOutbox.persistInbound(db, pageEntities, authManager.syncSessionId())
                            }
                            if (!persisted) {
                                Log.i(TAG, "Session invalidated during movies sync; aborting.")
                                _syncStatus.value = TraktSyncStatus.Idle
                                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                            }
                        }

                        if (watchedMovies.size < PAGE_SIZE) {
                            hasMore = false
                        } else {
                            page++
                            checkpoint(currentStep, page)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed syncing watched movies on page $page: ${e.message}", e)
                        throw e
                    }
                }
                if (authManager.isSyncWatchedEnabled()) authManager.acknowledgeCategory("movies", dirtyCategories["movies"])
                currentStep = STEP_RATINGS
                currentPage = 1
                checkpoint(STEP_RATINGS, 1)
            }

            // Disabled or unchanged categories must not strand later enabled phases.
            if (currentStep == STEP_MOVIES) {
                currentStep = STEP_RATINGS; currentPage = 1
                checkpoint(STEP_RATINGS, 1)
            }

            // ──────────────────────────────────────────────
            // 3. RATINGS (PAGINATED)
            // ──────────────────────────────────────────────
            if (authManager.isSessionInvalid(sessionEpoch)) {
                Log.i(TAG, "Session invalidated before ratings sync; aborting.")
                _syncStatus.value = TraktSyncStatus.Idle
                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
            }

            if (shouldSyncRatings && currentStep.startsWith(STEP_RATINGS)) {
                updateProgress("Downloading personal ratings...", 0.70f)
                try {
                    var showRatingPage = if (currentStep == "RATINGS_SHOWS") currentPage else 1
                    var hasMoreShowRatings = currentStep != "RATINGS_MOVIES"
                    while (hasMoreShowRatings) {
                        if (authManager.isSessionInvalid(sessionEpoch)) {
                            Log.i(TAG, "Session invalidated during show ratings sync; aborting.")
                            _syncStatus.value = TraktSyncStatus.Idle
                            return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                        }
                        checkpoint("RATINGS_SHOWS", showRatingPage)
                        val showRatings = getApiService().getShowRatings(page = showRatingPage, limit = PAGE_SIZE)
                        val entitiesToUpdate = mutableListOf<SavedItemEntity>()
                        for (r in showRatings) {
                            if (authManager.isSessionInvalid(sessionEpoch)) break
                            val traktId = r.show?.ids?.trakt ?: continue
                            val entity = dao.getItemByTraktId(traktId, "tv")
                            if (entity != null && entity.userRating != r.rating) {
                                entitiesToUpdate.add(entity.copy(userRating = r.rating))
                                ratingsCount++
                            }
                        }
                        if (entitiesToUpdate.isNotEmpty()) {
                            val persisted = authManager.runIfSessionValid(sessionEpoch) {
                                TraktOutbox.persistInbound(db, entitiesToUpdate, authManager.syncSessionId())
                            }
                            if (!persisted) {
                                Log.i(TAG, "Session invalidated during show ratings sync; aborting.")
                                _syncStatus.value = TraktSyncStatus.Idle
                                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                            }
                        }
                        if (showRatings.size < PAGE_SIZE) hasMoreShowRatings = false
                        else showRatingPage++
                    }

                    var movieRatingPage = if (currentStep == "RATINGS_MOVIES") currentPage else 1
                    var hasMoreMovieRatings = true
                    while (hasMoreMovieRatings) {
                        if (authManager.isSessionInvalid(sessionEpoch)) {
                            Log.i(TAG, "Session invalidated during movie ratings sync; aborting.")
                            _syncStatus.value = TraktSyncStatus.Idle
                            return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                        }
                        checkpoint("RATINGS_MOVIES", movieRatingPage)
                        val movieRatings = getApiService().getMovieRatings(page = movieRatingPage, limit = PAGE_SIZE)
                        val entitiesToUpdate = mutableListOf<SavedItemEntity>()
                        for (r in movieRatings) {
                            if (authManager.isSessionInvalid(sessionEpoch)) break
                            val traktId = r.movie?.ids?.trakt ?: continue
                            val entity = dao.getItemByTraktId(traktId, "movie")
                            if (entity != null && entity.userRating != r.rating) {
                                entitiesToUpdate.add(entity.copy(userRating = r.rating))
                                ratingsCount++
                            }
                        }
                        if (entitiesToUpdate.isNotEmpty()) {
                            val persisted = authManager.runIfSessionValid(sessionEpoch) {
                                TraktOutbox.persistInbound(db, entitiesToUpdate, authManager.syncSessionId())
                            }
                            if (!persisted) {
                                Log.i(TAG, "Session invalidated during movie ratings sync; aborting.")
                                _syncStatus.value = TraktSyncStatus.Idle
                                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                            }
                        }
                        if (movieRatings.size < PAGE_SIZE) hasMoreMovieRatings = false
                        else movieRatingPage++
                    }
                } catch (e: Exception) {
                    throw e // Keep this phase checkpoint; never report a partially downloaded category as success.
                }
                if (authManager.isSyncRatingsEnabled()) authManager.acknowledgeCategory("ratings", dirtyCategories["ratings"])
                currentStep = STEP_WATCHLIST
                currentPage = 1
                checkpoint(STEP_WATCHLIST, 1)
            }

            // Disabled or unchanged categories must not strand later enabled phases.
            if (currentStep.startsWith(STEP_RATINGS)) {
                currentStep = STEP_WATCHLIST; currentPage = 1
                checkpoint(STEP_WATCHLIST, 1)
            }

            // ──────────────────────────────────────────────
            // 4. WATCHLIST (PAGINATED)
            // ──────────────────────────────────────────────
            if (authManager.isSessionInvalid(sessionEpoch)) {
                Log.i(TAG, "Session invalidated before watchlist sync; aborting.")
                _syncStatus.value = TraktSyncStatus.Idle
                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
            }

            if (shouldSyncWatchlist && currentStep.startsWith(STEP_WATCHLIST)) {
                updateProgress("Downloading watchlist...", 0.85f)
                try {
                    var showWatchlistPage = if (currentStep == "WATCHLIST_SHOWS") currentPage else 1
                    var hasMoreShowWatchlist = currentStep != "WATCHLIST_MOVIES"
                    while (hasMoreShowWatchlist) {
                        if (authManager.isSessionInvalid(sessionEpoch)) {
                            Log.i(TAG, "Session invalidated during show watchlist sync; aborting.")
                            _syncStatus.value = TraktSyncStatus.Idle
                            return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                        }
                        checkpoint("WATCHLIST_SHOWS", showWatchlistPage)
                        val watchlistShows = getApiService().getWatchlistShows(page = showWatchlistPage, limit = PAGE_SIZE)
                        val pageEntities = mutableListOf<SavedItemEntity>()
                        for (w in watchlistShows) {
                            val show = w.show ?: continue
                            if (locallyRemoved(db, "tv", show.ids)) continue
                            val traktId = show.ids.trakt
                            val existing = dao.getItemByTraktId(traktId, "tv") ?: show.ids.tmdb?.let { dao.getItemByTmdbId(it, "tv") }
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
                                    watchStatus = "Plan to Watch", inWatchlist = true,
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
                            } else {
                                pageEntities.add(existing.copy(inWatchlist = true, traktId = traktId, tmdbId = show.ids.tmdb ?: existing.tmdbId, imdbId = show.ids.imdb ?: existing.imdbId))
                                watchlistCount++
                            }
                        }
                        if (pageEntities.isNotEmpty()) {
                            val persisted = authManager.runIfSessionValid(sessionEpoch) {
                                TraktOutbox.persistInbound(db, pageEntities, authManager.syncSessionId())
                            }
                            if (!persisted) {
                                Log.i(TAG, "Session invalidated before inserting watchlist shows; aborting.")
                                _syncStatus.value = TraktSyncStatus.Idle
                                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                            }
                        }
                        if (watchlistShows.size < PAGE_SIZE) hasMoreShowWatchlist = false
                        else showWatchlistPage++
                    }

                    var movieWatchlistPage = if (currentStep == "WATCHLIST_MOVIES") currentPage else 1
                    var hasMoreMovieWatchlist = true
                    while (hasMoreMovieWatchlist) {
                        if (authManager.isSessionInvalid(sessionEpoch)) {
                            Log.i(TAG, "Session invalidated during movie watchlist sync; aborting.")
                            _syncStatus.value = TraktSyncStatus.Idle
                            return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                        }
                        checkpoint("WATCHLIST_MOVIES", movieWatchlistPage)
                        val watchlistMovies = getApiService().getWatchlistMovies(page = movieWatchlistPage, limit = PAGE_SIZE)
                        val pageEntities = mutableListOf<SavedItemEntity>()
                        for (w in watchlistMovies) {
                            val movie = w.movie ?: continue
                            if (locallyRemoved(db, "movie", movie.ids)) continue
                            val traktId = movie.ids.trakt
                            val existing = dao.getItemByTraktId(traktId, "movie") ?: movie.ids.tmdb?.let { dao.getItemByTmdbId(it, "movie") }
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
                                    watchStatus = "Plan to Watch", inWatchlist = true,
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
                            } else {
                                pageEntities.add(existing.copy(inWatchlist = true, traktId = traktId, tmdbId = movie.ids.tmdb ?: existing.tmdbId, imdbId = movie.ids.imdb ?: existing.imdbId))
                                watchlistCount++
                            }
                        }
                        if (pageEntities.isNotEmpty()) {
                            val persisted = authManager.runIfSessionValid(sessionEpoch) {
                                TraktOutbox.persistInbound(db, pageEntities, authManager.syncSessionId())
                            }
                            if (!persisted) {
                                Log.i(TAG, "Session invalidated before inserting watchlist movies; aborting.")
                                _syncStatus.value = TraktSyncStatus.Idle
                                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
                            }
                        }
                        if (watchlistMovies.size < PAGE_SIZE) hasMoreMovieWatchlist = false
                        else movieWatchlistPage++
                    }
                } catch (e: Exception) {
                    throw e // Retry this phase without losing already persisted pages.
                }
                if (authManager.isSyncWatchlistEnabled()) authManager.acknowledgeCategory("watchlist", dirtyCategories["watchlist"])
            }

            // Include edits made while inbound pages were downloading.
            TraktOutbox.drain(context, getApiService())

            // Sync successfully finished - clear checkpoints
            val finalized = authManager.runIfSessionValid(sessionEpoch) {
                authManager.clearSyncCheckpoint()
            }
            if (!finalized) {
                Log.i(TAG, "Session invalidated before finalizing sync; aborting.")
                _syncStatus.value = TraktSyncStatus.Idle
                return@withContext Result.failure(IllegalStateException("Session disconnected or invalidated"))
            }

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
            if (e is kotlinx.coroutines.CancellationException) throw e
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
        val tmdbKey = storedTmdbKey(context)
        val db = AppDatabase.getDatabase(context)
        val dao = db.savedItemDao()

        var progress = item.showProgress ?: TvShowProgress()
        var updated = false
        var currentBackdrop = item.backdropUrl
        var currentThumbnail = item.thumbnailPath
        var currentTrailer = item.trailerUrl
        var richWatchProviders = item.richWatchProviders.toMutableList()

        if (currentTrailer.isNullOrBlank() && traktId != null && traktId > 0) {
            try {
                currentTrailer = if (isTv) getApiService().getShowSummary(traktId.toString()).trailer
                    else getApiService().getMovieSummary(traktId.toString()).trailer
                updated = updated || !currentTrailer.isNullOrBlank()
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { /* Keep locally cached metadata when the provider is unavailable. */ }
        }

        // 1. Fetch Trakt Ratings distribution & Stats (if Trakt item)
        if (traktId != null && traktId > 0 && isTv && progress.ratingDistribution.isEmpty()) {
            val slug = traktId.toString()
            try {
                // Season navigation must not depend on optional ratings/stats succeeding.
                val seasonsRes = getApiService().getShowSeasons(slug)
                progress = progress.copy(seasons = seasonsRes.map { remote ->
                    val local = progress.seasons.firstOrNull { it.seasonNumber == remote.number }
                    (local ?: TvSeasonProgress(remote.number)).copy(title = remote.title ?: local?.title,
                        totalEpisodes = remote.aired_episodes ?: remote.episode_count ?: local?.totalEpisodes ?: 0)
                }.ifEmpty { progress.seasons })
                updated = updated || seasonsRes.isNotEmpty()
                val ratingsRes = getApiService().getShowRatings(slug)
                val statsRes = getApiService().getShowStats(slug)
                val people = getApiService().getShowPeople(slug)

                val seasonQuality = seasonsRes.filter { it.number > 0 && it.rating != null }.map {
                    SeasonQualityPoint(seasonNumber = it.number, ratingPercent = (it.rating!! * 10).toInt())
                }.sortedBy { it.seasonNumber }

                val seasons = seasonsRes.map { remote ->
                    val local = progress.seasons.firstOrNull { it.seasonNumber == remote.number }
                    (local ?: TvSeasonProgress(remote.number)).copy(title = remote.title ?: local?.title,
                        totalEpisodes = remote.aired_episodes ?: remote.episode_count ?: local?.totalEpisodes ?: 0)
                }
                progress = progress.copy(
                    seasons = seasons.ifEmpty { progress.seasons },
                    cast = people.cast.orEmpty().take(40).mapNotNull { credit ->
                        credit.person?.let { person -> MediaCastMember(person.name,
                            credit.character ?: credit.characters?.joinToString(", ")) }
                    }.ifEmpty { progress.cast },
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
        if (tmdbKey.isNotBlank() && tmdbId != null && tmdbId > 0 && progress.cast.isEmpty()) {
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
        if (tmdbKey.isNotBlank() && tmdbId != null && tmdbId > 0 && isTv) {
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
        if (tmdbKey.isNotBlank() && tmdbId != null && tmdbId > 0 && (currentBackdrop.isNullOrBlank() || richWatchProviders.isEmpty())) {
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
                        fetchedProviders.add(MediaWatchProvider(name = it, type = "Stream", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId, url = countryData?.link))
                    }
                }
                countryData?.buy?.forEach { p ->
                    p.providerName?.let {
                        fetchedProviders.add(MediaWatchProvider(name = it, type = "Buy", logoUrl = p.logoPath?.let { lp -> "https://image.tmdb.org/t/p/w154$lp" }, providerId = p.providerId, url = countryData?.link))
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
                trailerUrl = currentTrailer,
                thumbnailPath = currentThumbnail,
                backdropUrl = currentBackdrop,
                richWatchProviders = richWatchProviders,
                watchProviders = richWatchProviders.map { it.name },
                showProgress = progress
            )
            val entity = dao.getItemById(item.id)
            if (entity != null && !entity.isDeleted) {
                val latest = TvShowProgress.fromJson(entity.showProgressJson)
                if (latest != null && entity.updatedAt != item.updatedAt) {
                    progress = progress.copy(totalWatched = latest.totalWatched, completed = latest.completed,
                        lastWatchedSeason = latest.lastWatchedSeason, lastWatchedEpisode = latest.lastWatchedEpisode,
                        seasons = progress.seasons.map { metadata ->
                            val watched = latest.seasons.firstOrNull { it.seasonNumber == metadata.seasonNumber }
                            if (watched == null) metadata else metadata.copy(episodes = watched.episodes, watchedEpisodes = watched.watchedEpisodes)
                        })
                }
                dao.insertItem(
                    entity.copy(
                        trailerUrl = currentTrailer,
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

    internal fun ids(item: SavedItem): TraktSyncIds {
        require((item.traktId ?: 0) > 0 || (item.tmdbId ?: 0) > 0 || !item.imdbId.isNullOrBlank()) { "This item has no sync identity" }
        return TraktSyncIds(trakt = item.traktId?.takeIf { it > 0 }, tmdb = item.tmdbId?.takeIf { it > 0 }, imdb = item.imdbId?.takeIf { it.isNotBlank() })
    }
    private fun watchedTime() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:00'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
    private suspend fun mutate(action: suspend () -> Unit): Result<Unit> = try {
        action(); Result.success(Unit)
    } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
    catch (failure: Exception) { Result.failure(failure) }

    suspend fun markEpisodeWatched(context: Context, item: SavedItem, seasonNumber: Int,
        episodeNumber: Int, isWatched: Boolean): Result<Unit> = withContext(Dispatchers.IO) { mutate {
        require(seasonNumber >= 0 && episodeNumber > 0)
        val payload = TraktSyncPayload(shows = listOf(TraktSyncShowPayload(ids(item), seasons = listOf(
            TraktSyncSeasonPayload(seasonNumber, episodes = listOf(TraktSyncEpisodePayload(episodeNumber,
                watched_at = if (isWatched) watchedTime() else null)))))))
        TraktOutbox.change(context, item.id, "episode:$seasonNumber:$episodeNumber", if (isWatched) "history:add" else "history:remove", payload) { entity ->
            val progress = TvShowProgress.fromJson(entity.showProgressJson) ?: TvShowProgress()
            val seasons = progress.seasons.toMutableList()
            val index = seasons.indexOfFirst { it.seasonNumber == seasonNumber }
            val season = seasons.getOrNull(index) ?: TvSeasonProgress(seasonNumber = seasonNumber)
            val episodes = season.episodes.toMutableList()
            val epIndex = episodes.indexOfFirst { it.episodeNumber == episodeNumber }
            val old = episodes.getOrNull(epIndex) ?: TvEpisodeProgress(seasonNumber = seasonNumber, episodeNumber = episodeNumber)
            val changed = old.copy(isWatched = isWatched, plays = if (isWatched) old.plays.coerceAtLeast(1) else 0,
                lastWatchedAt = if (isWatched) System.currentTimeMillis() else null)
            if (epIndex >= 0) episodes[epIndex] = changed else episodes += changed
            val updated = season.copy(episodes = episodes.sortedBy { it.episodeNumber }, watchedEpisodes = episodes.count { it.isWatched })
            if (index >= 0) seasons[index] = updated else seasons += updated
            val watched = seasons.filter { it.seasonNumber > 0 }.sumOf { it.watchedEpisodes }
            val complete = progress.totalAired > 0 && watched >= progress.totalAired
            entity.copy(showProgressJson = progress.copy(seasons = seasons.sortedBy { it.seasonNumber }, totalWatched = watched,
                completed = complete, lastWatchedSeason = if (isWatched) seasonNumber else progress.lastWatchedSeason,
                lastWatchedEpisode = if (isWatched) episodeNumber else progress.lastWatchedEpisode).toJson(),
                watchStatus = if (complete) "Completed" else if (watched > 0) "Watching" else "Plan to Watch",
                lastWatchedAt = if (isWatched) System.currentTimeMillis() else entity.lastWatchedAt, updatedAt = System.currentTimeMillis())
        }
    } }

    suspend fun markMovieWatched(context: Context, item: SavedItem, isWatched: Boolean): Result<Unit> = withContext(Dispatchers.IO) { mutate {
        val payload = TraktSyncPayload(movies = listOf(TraktSyncItemPayload(ids(item), watched_at = if (isWatched) watchedTime() else null)))
        TraktOutbox.change(context, item.id, "movie:watched", if (isWatched) "history:add" else "history:remove", payload) { entity ->
            entity.copy(watchStatus = if (isWatched) "Completed" else "Plan to Watch", watchStatusOverride = if (isWatched) "Completed" else "Plan to Watch",
                lastWatchedAt = if (isWatched) System.currentTimeMillis() else null, updatedAt = System.currentTimeMillis())
        }
    } }

    suspend fun rateItem(context: Context, item: SavedItem, rating: Int): Result<Unit> = withContext(Dispatchers.IO) { mutate {
        require(rating in 0..10)
        val movie = item.mediaType.equals("movie", true)
        val payload = if (movie) TraktSyncPayload(movies = listOf(TraktSyncItemPayload(ids(item), rating = rating)))
            else TraktSyncPayload(shows = listOf(TraktSyncShowPayload(ids(item), rating = rating)))
        TraktOutbox.change(context, item.id, "rating", if (rating > 0) "rating:add" else "rating:remove", payload) { entity ->
            entity.copy(userRating = rating.takeIf { it > 0 }, updatedAt = System.currentTimeMillis())
        }
    } }

    suspend fun setWatchStatus(context: Context, item: SavedItem, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        require(status in listOf("Plan to Watch", "Watching", "Completed", "Dropped"))
        if (status == "Completed" && item.mediaType.equals("movie", true)) {
            val watched = markMovieWatched(context, item, true)
            if (watched.isFailure) return@withContext watched
            return@withContext toggleWatchlist(context, item, false)
        }
        val result = toggleWatchlist(context, item, status == "Plan to Watch")
        if (result.isSuccess) {
            val db = AppDatabase.getDatabase(context)
            db.withTransaction {
                val current = db.savedItemDao().getItemById(item.id) ?: error("Item no longer exists")
                check(!current.isDeleted) { "Restore the item from Trash first" }
                db.savedItemDao().insertItem(current.copy(watchStatus = status, watchStatusOverride = status, updatedAt = System.currentTimeMillis()))
            }
        }
        result
    }

    suspend fun toggleWatchlist(context: Context, item: SavedItem, inWatchlist: Boolean): Result<Unit> = withContext(Dispatchers.IO) { mutate {
        val movie = item.mediaType.equals("movie", true)
        val payload = if (movie) TraktSyncPayload(movies = listOf(TraktSyncItemPayload(ids(item))))
            else TraktSyncPayload(shows = listOf(TraktSyncShowPayload(ids(item))))
        TraktOutbox.change(context, item.id, "watchlist", if (inWatchlist) "watchlist:add" else "watchlist:remove", payload) { entity ->
            entity.copy(inWatchlist = inWatchlist, watchStatus = entity.watchStatusOverride ?: if (inWatchlist && entity.lastWatchedAt == null) "Plan to Watch" else entity.watchStatus,
                updatedAt = System.currentTimeMillis())
        }
    } }
}

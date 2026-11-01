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

                        val seasonList = mutableListOf<TvSeasonProgress>()
                        var totalWatchedEps = 0

                        item.seasons?.forEach { seasonItem ->
                            val epList = mutableListOf<TvEpisodeProgress>()
                            seasonItem.episodes?.forEach { epItem ->
                                val epWatchedAt = parseIsoDate(epItem.last_watched_at)
                                epList.add(
                                    TvEpisodeProgress(
                                        episodeNumber = epItem.number,
                                        seasonNumber = seasonItem.number,
                                        isWatched = true,
                                        plays = epItem.plays,
                                        lastWatchedAt = epWatchedAt
                                    )
                                )
                                totalWatchedEps++
                            }

                            seasonList.add(
                                TvSeasonProgress(
                                    seasonNumber = seasonItem.number,
                                    totalEpisodes = epList.size,
                                    watchedEpisodes = epList.size,
                                    episodes = epList.sortedBy { it.episodeNumber }
                                )
                            )
                        }

                        val totalAired = (show.aired_episodes ?: 0).coerceAtLeast(totalWatchedEps)
                        val isComplete = totalAired > 0 && totalWatchedEps >= totalAired

                        val progress = TvShowProgress(
                            totalAired = totalAired,
                            totalWatched = totalWatchedEps,
                            completed = isComplete,
                            seasons = seasonList.sortedBy { it.seasonNumber }
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
            // 5. ATOMIC LOCAL PERSISTENCE
            // ──────────────────────────────────────────────
            updateProgress("Saving changes to local library...", 0.95f)
            if (itemsToUpsert.isNotEmpty()) {
                dao.insertItems(itemsToUpsert.values.toList())
            }

            val summary = TraktSyncSummary(
                moviesSynced = moviesCount,
                showsSynced = showsCount,
                ratingsSynced = ratingsCount,
                watchlistSynced = watchlistCount,
                totalItems = itemsToUpsert.size
            )

            authManager.setLastSyncTimestamp(System.currentTimeMillis())
            authManager.setLastSyncSummary(summary.message)

            _syncStatus.value = TraktSyncStatus.Success(itemsToUpsert.size, summary.message)

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

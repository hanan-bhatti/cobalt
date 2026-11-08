package com.example.data.remote.trakt

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class TraktDrainResult(val uploaded: Int, val unmatched: Boolean)
internal data class TraktIntent(val slot: String, val action: String, val payload: TraktSyncPayload)

/** Local changes and durable intent commit together. Only acknowledged generations leave the queue. */
internal object TraktOutbox {
    private val drainLock = Mutex()
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(TraktSyncPayload::class.java)
    /** Seed once before any import can replace pre-login personal state. Retry preserves newer intents. */
    private suspend fun seedLibrary(auth: TraktAuthManager, db: AppDatabase, epoch: Long, session: String) {
        if (auth.librarySeeded(session)) return
        check(auth.runIfSessionValid(epoch) {
            db.withTransaction {
                var after = ""
                while (true) {
                    val page = db.savedItemDao().traktLibraryPage(after)
                    if (page.isEmpty()) break
                    for (item in page) {
                        val ids = TraktSyncEngine.resolveIds(item.mediaType, item.id, item.traktId, item.tmdbId, item.imdbId)
                        if (ids.trakt == null && ids.tmdb == null && ids.imdb == null) continue
                        if (ids.trakt != item.traktId || ids.tmdb != item.tmdbId) db.savedItemDao().recoverMediaIds(item.id, ids.trakt, ids.tmdb)
                        val movie = item.mediaType.equals("movie", true)
                        suspend fun queue(slot: String, action: String, payload: TraktSyncPayload) {
                            db.traktOutboxDao().putIfAbsent(TraktOutboxEntity("$session:${item.id}:$slot", session, item.id, action, adapter.toJson(payload)))
                        }
                        fun payload(rating: Int? = null, watched: String? = null) = if (movie)
                            TraktSyncPayload(movies = listOf(TraktSyncItemPayload(ids, watched_at = watched, rating = rating)))
                        else TraktSyncPayload(shows = listOf(TraktSyncShowPayload(ids, rating = rating)))
                        if (item.inWatchlist || item.watchStatus == "Plan to Watch" || item.watchStatus == null)
                            queue("watchlist", "watchlist:add", payload())
                        else if (item.watchStatus in listOf("Watching", "Completed", "Dropped"))
                            queue("watchlist", "watchlist:remove", payload())
                        item.userRating?.takeIf { it in 1..10 }?.let { queue("rating", "rating:add", payload(rating = it)) }
                        if (movie && (item.lastWatchedAt != null || item.watchStatus == "Completed")) {
                            val at = TraktDates.format(item.lastWatchedAt ?: item.timestamp)
                            queue("movie:watched", "history:add", payload(watched = at))
                        } else if (!movie) {
                            val progress = com.example.data.model.TvShowProgress.fromJson(item.showProgressJson)
                            progress?.seasons.orEmpty().forEach { season -> season.episodes.filter { it.isWatched }.forEach { episode ->
                                // A local completion label alone must never mark every TV episode watched.
                                val at = TraktDates.format(episode.lastWatchedAt ?: item.timestamp)
                                queue("episode:${season.seasonNumber}:${episode.episodeNumber}", "history:add", TraktSyncPayload(shows = listOf(
                                    TraktSyncShowPayload(ids, seasons = listOf(TraktSyncSeasonPayload(season.seasonNumber,
                                        episodes = listOf(TraktSyncEpisodePayload(episode.episodeNumber, watched_at = at))))))))
                            } }
                        }
                    }
                    after = page.last().id
                }
            }
            // Commit the marker before uploads; a process restart cannot seed an acknowledged history twice.
            auth.markLibrarySeeded(session)
        }) { "Trakt account changed; library sync retained" }
    }
    /** A planned search result and its outbound intent are one transaction, with no network in Save. */
    suspend fun savePlannedMedia(context: Context, item: com.example.data.model.SavedItem,
        db: AppDatabase = AppDatabase.getDatabase(context)) = saveCapturedMedia(context, item.copy(watchStatus = "Plan to Watch"), db)

    /** Every supported capture status commits with its intents, including a movie saved as completed. */
    suspend fun saveCapturedMedia(context: Context, item: com.example.data.model.SavedItem,
        db: AppDatabase = AppDatabase.getDatabase(context)): com.example.data.model.SavedItem {
        require(item.type == com.example.data.model.SavedItemType.MEDIA)
        require(item.mediaType in listOf("movie", "tv")) { "Only movies and TV sync with Trakt" }
        val auth = TraktAuthManager.getInstance(context)
        val epoch = auth.currentSessionEpoch()
        var saved = item
        suspend fun commit() = com.example.notes.NoteWriteLock.mutex.withLock {
            com.example.notes.NoteDeletionFence(context).requireAlive(item.id)
            db.withTransaction {
                check(db.savedItemDao().isItemDeleted(item.id) != true) { "Restore the item from Trash first" }
                db.savedItemDao().getItemById(item.id)?.let {
                    saved = it.toDomainModel()
                    return@withTransaction
                }
                val connected = auth.hasValidToken()
                db.mediaTombstoneDao().clearMatching(item.mediaType.orEmpty(), item.traktId?.toString().orEmpty(),
                    item.tmdbId?.toString().orEmpty(), item.imdbId.orEmpty())
                val planned = item.watchStatus == null || item.watchStatus == "Plan to Watch"
                saved = item.copy(watchStatus = item.watchStatus ?: "Plan to Watch", inWatchlist = planned,
                    lastWatchedAt = if (item.mediaType == "movie" && item.watchStatus == "Completed") item.lastWatchedAt ?: System.currentTimeMillis() else item.lastWatchedAt,
                    updatedAt = System.currentTimeMillis())
                db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(saved))
                if (connected) {
                    val ids = TraktSyncEngine.ids(saved)
                    val payload = if (item.mediaType.equals("movie", true))
                        TraktSyncPayload(movies = listOf(TraktSyncItemPayload(ids)))
                    else TraktSyncPayload(shows = listOf(TraktSyncShowPayload(ids)))
                    val session = auth.syncSessionId()
                    db.traktOutboxDao().put(TraktOutboxEntity("$session:${item.id}:watchlist", session,
                        item.id, if (planned) "watchlist:add" else "watchlist:remove", adapter.toJson(payload)))
                    if (item.mediaType == "movie" && item.watchStatus == "Completed") {
                        val watched = TraktSyncPayload(movies = listOf(TraktSyncItemPayload(ids, watched_at = TraktDates.format(saved.lastWatchedAt!!))))
                        db.traktOutboxDao().put(TraktOutboxEntity("$session:${item.id}:movie:watched", session, item.id, "history:add", adapter.toJson(watched)))
                    }
                }
            }
        }
        check(auth.runIfSessionValid(epoch) { commit() }) { "Trakt account changed; retry Save" }
        if (auth.hasValidToken())
            try { com.example.util.TraktSyncWorker.enqueueOneTimeSync(context, uploadOnly = true) } catch (_: Exception) { /* Durable queue retained. */ }
        return saved
    }

    suspend fun change(context: Context, itemId: String, slot: String, action: String, payload: TraktSyncPayload, db: AppDatabase = AppDatabase.getDatabase(context),
        additionalIntents: List<TraktIntent> = emptyList(),
        update: (SavedItemEntity) -> SavedItemEntity) {
        val auth = TraktAuthManager.getInstance(context)
        val epoch = auth.currentSessionEpoch()
        val connected = auth.hasValidToken() && !auth.isSessionInvalid(epoch)
        suspend fun commit() = db.withTransaction {
            val item = db.savedItemDao().getItemById(itemId) ?: error("Item no longer exists")
            check(!item.isDeleted) { "Restore the item from Trash before changing it" }
            db.savedItemDao().insertItem(update(item))
            if (connected) {
                val session = auth.syncSessionId()
                db.traktOutboxDao().put(TraktOutboxEntity("$session:$itemId:$slot", session, itemId, action, adapter.toJson(payload)))
                for (intent in additionalIntents) db.traktOutboxDao().put(TraktOutboxEntity("$session:$itemId:${intent.slot}", session, itemId, intent.action, adapter.toJson(intent.payload)))
            }
        }
        if (connected) check(auth.runIfSessionValid(epoch) { commit() }) { "Trakt account changed; retry the action" }
        else commit()
        if (connected) try { com.example.util.TraktSyncWorker.enqueueOneTimeSync(context, uploadOnly = true) }
        catch (_: Exception) { /* The committed outbox survives; manual/periodic sync retries it. */ }
    }

    suspend fun saveEditedMedia(context: Context, item: com.example.data.model.SavedItem, db: AppDatabase): com.example.data.model.SavedItem {
        val auth = TraktAuthManager.getInstance(context)
        val epoch = auth.currentSessionEpoch()
        var saved = item
        var queued = false
        check(auth.runIfSessionValid(epoch) {
            com.example.notes.NoteWriteLock.mutex.withLock {
                com.example.notes.NoteDeletionFence(context).requireAlive(item.id)
                db.withTransaction {
                    val before = db.savedItemDao().getItemById(item.id) ?: error("Item no longer exists")
                    check(!before.isDeleted) { "Restore the item from Trash first" }
                    val status = item.watchStatus ?: "Plan to Watch"
                    val changed = status != before.watchStatus
                    val completed = changed && item.mediaType == "movie" && status == "Completed"
                    saved = item.copy(watchStatus = if (changed) status else before.watchStatus, userRating = before.userRating,
                        showProgress = before.toDomainModel().showProgress, inWatchlist = if (changed) status == "Plan to Watch" else before.inWatchlist,
                        lastWatchedAt = if (completed) System.currentTimeMillis() else before.lastWatchedAt,
                        watchStatusOverride = if (changed) status else before.watchStatusOverride, updatedAt = System.currentTimeMillis())
                    db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(saved))
                    if (changed && auth.hasValidToken()) {
                        val ids = TraktSyncEngine.ids(saved)
                        val movie = item.mediaType == "movie"
                        val payload = if (movie) TraktSyncPayload(movies = listOf(TraktSyncItemPayload(ids))) else TraktSyncPayload(shows = listOf(TraktSyncShowPayload(ids)))
                        val session = auth.syncSessionId()
                        db.traktOutboxDao().put(TraktOutboxEntity("$session:${item.id}:watchlist", session, item.id,
                            if (status == "Plan to Watch") "watchlist:add" else "watchlist:remove", adapter.toJson(payload)))
                        if (completed) db.traktOutboxDao().put(TraktOutboxEntity("$session:${item.id}:movie:watched", session, item.id,
                            "history:add", adapter.toJson(TraktSyncPayload(movies = listOf(TraktSyncItemPayload(ids,
                                watched_at = TraktDates.format(saved.lastWatchedAt!!)))))))
                        queued = true
                    }
                }
            }
        }) { "Trakt account changed; retry Save" }
        if (queued) try { com.example.util.TraktSyncWorker.enqueueOneTimeSync(context, uploadOnly = true) } catch (_: Exception) { /* Queue retained. */ }
        return saved
    }

    suspend fun persistInbound(db: AppDatabase, incoming: List<SavedItemEntity>, session: String, category: String? = null) = db.withTransaction {
        for (row in incoming) {
            val local = db.savedItemDao().getItemById(row.id)
            if (local?.isDeleted == true || db.mediaTombstoneDao().blocked(row.mediaType.orEmpty(), row.traktId?.toString().orEmpty(),
                row.tmdbId?.toString().orEmpty(), row.imdbId.orEmpty())) continue
            val actions = if (local != null) db.traktOutboxDao().pendingActions(session, row.id) else emptyList()
            val historyPending = local != null && (category in listOf("ratings", "watchlist") || actions.any { it.startsWith("history:") })
            val ratingPending = local != null && (category in listOf("history", "watchlist") || actions.any { it.startsWith("rating:") })
            val watchlistPending = local != null && (category in listOf("history", "ratings") || actions.any { it.startsWith("watchlist:") })
            val override = local?.watchStatusOverride?.takeIf { it in listOf("Watching", "Dropped", "Plan to Watch") || historyPending }
            val refreshed = row.copy(watchStatusOverride = override, mediaFactsJson = row.mediaFactsJson ?: local?.mediaFactsJson,
                watchStatus = override ?: row.watchStatus,
                title = local?.title ?: row.title, content = local?.content ?: row.content,
                foldersJson = local?.foldersJson ?: row.foldersJson, timestamp = local?.timestamp ?: row.timestamp)
            db.savedItemDao().insertItem(refreshed.copy(
                watchStatus = if (historyPending) local!!.watchStatus else refreshed.watchStatus,
                inWatchlist = if (watchlistPending) local!!.inWatchlist else refreshed.inWatchlist,
                userRating = if (ratingPending) local!!.userRating else refreshed.userRating,
                lastWatchedAt = if (historyPending) local!!.lastWatchedAt else refreshed.lastWatchedAt,
                showProgressJson = if (historyPending) local!!.showProgressJson else refreshed.showProgressJson))
        }
    }

    /** Only a complete category snapshot can clear remote removals. Never clear a queued local edit. */
    suspend fun reconcileRemovals(db: AppDatabase, session: String, category: String, remoteIds: Set<Pair<String, Long>>) = db.withTransaction {
        var after = ""
        while (true) {
            val page = db.savedItemDao().traktLibraryPage(after)
            if (page.isEmpty()) break
            for (row in page) {
                val id = row.traktId ?: continue
                if ((row.mediaType.orEmpty().lowercase(java.util.Locale.ROOT) to id) in remoteIds) continue
                if (db.traktOutboxDao().pendingActions(session, row.id).any { it.startsWith("$category:") }) continue
                val current = db.savedItemDao().getItemById(row.id) ?: continue
                if (category == "rating" && current.userRating != null)
                    db.savedItemDao().insertItem(current.copy(userRating = null))
                if (category == "watchlist" && current.inWatchlist)
                    db.savedItemDao().insertItem(current.copy(inWatchlist = false))
            }
            after = page.last().id
        }
    }

    suspend fun reconcileHistoryRemovals(db: AppDatabase, session: String, kind: String, remoteIds: Set<Long>) = db.withTransaction {
        var after = ""
        while (true) {
            val page = db.savedItemDao().traktLibraryPage(after)
            if (page.isEmpty()) break
            for (row in page) {
                if (row.mediaType != kind || row.traktId == null || row.traktId in remoteIds) continue
                if (db.traktOutboxDao().pendingActions(session, row.id).any { it.startsWith("history:") }) continue
                val current = db.savedItemDao().getItemById(row.id) ?: continue
                val progress = com.example.data.model.TvShowProgress.fromJson(current.showProgressJson)
                if (current.lastWatchedAt == null && (progress?.totalWatched ?: 0) == 0) continue
                val reset = progress?.copy(totalWatched = 0, completed = false, minutesWatched = 0,
                    minutesLeft = progress.minutesWatched + progress.minutesLeft,
                    lastWatchedSeason = null, lastWatchedEpisode = null, lastWatchedTitle = null,
                    nextEpisodeSeason = null, nextEpisodeNumber = null, nextEpisodeTitle = null,
                    seasons = progress.seasons.map { season -> season.copy(watchedEpisodes = 0,
                        episodes = season.episodes.map { it.copy(isWatched = false, plays = 0, lastWatchedAt = null) }) })
                val override = current.watchStatusOverride?.takeIf { it != "Completed" }
                db.savedItemDao().insertItem(current.copy(lastWatchedAt = null, showProgressJson = reset?.toJson(),
                    watchStatus = override ?: "Plan to Watch", watchStatusOverride = override))
            }
            after = page.last().id
        }
    }

    suspend fun drain(context: Context, api: TraktApiService, db: AppDatabase = AppDatabase.getDatabase(context)) = drainLock.withLock {
        val auth = TraktAuthManager.getInstance(context)
        val epoch = auth.currentSessionEpoch()
        val session = auth.syncSessionId()
        seedLibrary(auth, db, epoch, session)
        var unmatched = false
        var uploaded = 0
        while (true) {
            check(!auth.isSessionInvalid(epoch) && auth.hasValidToken()) { "Trakt account changed" }
            val queued = db.traktOutboxDao().pending(session, ratings = auth.isSyncRatingsEnabled(),
                watchlist = auth.isSyncWatchlistEnabled(), movies = auth.isSyncWatchedEnabled(), shows = auth.isSyncTvProgressEnabled())
            if (queued.isEmpty()) break
            // Preserve operation ordering. Coalescing happens only for the same local intent slot.
            val batch = queued.filter { it.action == queued.first().action }
            val values = batch.map { requireNotNull(adapter.fromJson(it.payload)) }
            val movies = values.flatMap { it.movies.orEmpty() }
            val shows = values.flatMap { it.shows.orEmpty() }.groupBy { it.ids }.map { (ids, rows) ->
                val last = rows.last()
                val seasons = rows.flatMap { it.seasons.orEmpty() }.groupBy { it.number }.map { (number, rows) ->
                    TraktSyncSeasonPayload(number, episodes = rows.flatMap { it.episodes.orEmpty() }.distinctBy { it.number })
                }
                last.copy(ids = ids, seasons = seasons.takeIf { it.isNotEmpty() })
            }
            val payload = TraktSyncPayload(movies.takeIf { it.isNotEmpty() }, shows.takeIf { it.isNotEmpty() })
            val result = when (batch.first().action) {
                "history:add" -> api.addToHistory(payload)
                "history:remove" -> api.removeFromHistory(payload)
                "rating:add" -> api.addRatings(payload)
                "rating:remove" -> api.removeRatings(payload)
                "watchlist:add" -> api.addToWatchlist(payload)
                "watchlist:remove" -> api.removeFromWatchlist(payload)
                else -> error("Unsupported queued Trakt change")
            }
            fun matches(ids: TraktSyncIds, raw: Map<String, Any?>): Boolean {
                val remote = (raw["ids"] as? Map<*, *>) ?: raw
                fun number(key: String) = (remote[key] as? Number)?.toLong() ?: remote[key]?.toString()?.toLongOrNull()
                return (ids.trakt != null && ids.trakt == number("trakt")) ||
                    (ids.tmdb != null && ids.tmdb.toLong() == number("tmdb")) ||
                    (!ids.imdb.isNullOrBlank() && ids.imdb == remote["imdb"])
            }
            val rejected = values.map { value ->
                value.movies.orEmpty().any { movie -> result.not_found?.movies.orEmpty().any { matches(movie.ids, it) } } ||
                    value.shows.orEmpty().any { show -> result.not_found?.shows.orEmpty().any { matches(show.ids, it) } }
            }
            val hasUnmatched = !result.not_found?.movies.isNullOrEmpty() || !result.not_found?.shows.isNullOrEmpty()
            val unknownShape = hasUnmatched && rejected.none { it }
            check(auth.runIfSessionValid(epoch) {
                db.withTransaction {
                    batch.forEachIndexed { index, row ->
                        if (rejected[index] || unknownShape) {
                            // A later explicit edit replaces this generation and retries immediately.
                            // Defer an unmatched ID so it cannot block the rest of the durable queue.
                            db.traktOutboxDao().defer(row.key, row.generation, System.currentTimeMillis() + 3_600_000L)
                            unmatched = true
                        } else {
                            db.traktOutboxDao().acknowledge(row.key, row.generation)
                            uploaded++
                        }
                    }
                }
            }) { "Trakt account changed; queued changes retained" }

        }
        TraktDrainResult(uploaded, unmatched)
    }
}

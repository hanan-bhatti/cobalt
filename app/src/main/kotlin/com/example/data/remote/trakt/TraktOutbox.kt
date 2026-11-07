package com.example.data.remote.trakt

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local changes and durable intent commit together. Only acknowledged generations leave the queue. */
internal object TraktOutbox {
    private val drainLock = Mutex()
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(TraktSyncPayload::class.java)
    /** A planned search result and its outbound intent are one transaction, with no network in Save. */
    suspend fun savePlannedMedia(context: Context, item: com.example.data.model.SavedItem,
        db: AppDatabase = AppDatabase.getDatabase(context)): com.example.data.model.SavedItem {
        require(item.type == com.example.data.model.SavedItemType.MEDIA)
        val auth = TraktAuthManager.getInstance(context)
        val epoch = auth.currentSessionEpoch()
        var saved = item
        suspend fun commit() = com.example.notes.NoteWriteLock.mutex.withLock {
            com.example.notes.NoteDeletionFence(context).requireAlive(item.id)
            db.withTransaction {
                check(db.savedItemDao().isItemDeleted(item.id) != true) { "Restore the item from Trash first" }
                val connected = auth.hasValidToken()
                db.mediaTombstoneDao().clearMatching(item.mediaType.orEmpty(), item.traktId?.toString().orEmpty(),
                    item.tmdbId?.toString().orEmpty(), item.imdbId.orEmpty())
                saved = item.copy(inWatchlist = true, updatedAt = System.currentTimeMillis())
                db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(saved))
                if (connected) {
                    val ids = TraktSyncEngine.ids(saved)
                    val payload = if (item.mediaType.equals("movie", true))
                        TraktSyncPayload(movies = listOf(TraktSyncItemPayload(ids)))
                    else TraktSyncPayload(shows = listOf(TraktSyncShowPayload(ids)))
                    val session = auth.syncSessionId()
                    db.traktOutboxDao().put(TraktOutboxEntity("$session:${item.id}:watchlist", session,
                        item.id, "watchlist:add", adapter.toJson(payload)))
                }
            }
        }
        check(auth.runIfSessionValid(epoch) { commit() }) { "Trakt account changed; retry Save" }
        if (auth.hasValidToken() && auth.isSyncWatchlistEnabled())
            try { com.example.util.TraktSyncWorker.enqueueOneTimeSync(context, uploadOnly = true) } catch (_: Exception) { /* Durable queue retained. */ }
        return saved
    }

    suspend fun change(context: Context, itemId: String, slot: String, action: String, payload: TraktSyncPayload, db: AppDatabase = AppDatabase.getDatabase(context),
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
            }
        }
        if (connected) check(auth.runIfSessionValid(epoch) { commit() }) { "Trakt account changed; retry the action" }
        else commit()
        if (connected) try { com.example.util.TraktSyncWorker.enqueueOneTimeSync(context, uploadOnly = true) }
        catch (_: Exception) { /* The committed outbox survives; manual/periodic sync retries it. */ }
    }

    suspend fun persistInbound(db: AppDatabase, incoming: List<SavedItemEntity>, session: String) = db.withTransaction {
        for (row in incoming) {
            val local = db.savedItemDao().getItemById(row.id)
            if (local?.isDeleted == true || db.mediaTombstoneDao().blocked(row.mediaType.orEmpty(), row.traktId?.toString().orEmpty(),
                row.tmdbId?.toString().orEmpty(), row.imdbId.orEmpty())) continue
            val actions = if (local != null) db.traktOutboxDao().pendingActions(session, row.id) else emptyList()
            val historyPending = actions.any { it.startsWith("history:") }
            val ratingPending = actions.any { it.startsWith("rating:") }
            val watchlistPending = actions.any { it.startsWith("watchlist:") }
            val refreshed = row.copy(watchStatusOverride = local?.watchStatusOverride, mediaFactsJson = row.mediaFactsJson ?: local?.mediaFactsJson,
                watchStatus = local?.watchStatusOverride ?: row.watchStatus)
            db.savedItemDao().insertItem(refreshed.copy(
                watchStatus = if (historyPending) local!!.watchStatus else refreshed.watchStatus,
                inWatchlist = if (watchlistPending) local!!.inWatchlist else refreshed.inWatchlist,
                userRating = if (ratingPending) local!!.userRating else refreshed.userRating,
                lastWatchedAt = if (historyPending) local!!.lastWatchedAt else refreshed.lastWatchedAt,
                showProgressJson = if (historyPending) local!!.showProgressJson else refreshed.showProgressJson))
        }
    }

    suspend fun drain(context: Context, api: TraktApiService, db: AppDatabase = AppDatabase.getDatabase(context)) = drainLock.withLock {
        val auth = TraktAuthManager.getInstance(context)
        val epoch = auth.currentSessionEpoch()
        val session = auth.syncSessionId()
        var unmatched = false
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
                        } else db.traktOutboxDao().acknowledge(row.key, row.generation)
                    }
                }
            }) { "Trakt account changed; queued changes retained" }

        }
        check(!unmatched) { "Some titles are not yet recognized by Trakt. Their changes are saved and will be retried." }
    }
}

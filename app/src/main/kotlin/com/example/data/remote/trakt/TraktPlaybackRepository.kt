package com.example.data.remote.trakt

import android.content.Context
import android.os.SystemClock
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.TraktPlaybackEntity
import com.example.data.model.SavedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Complete snapshots replace only their media kind. Failed/partial fetches retain saved positions. */
internal object TraktPlaybackRepository {
    private const val PAGE_SIZE = 100
    private const val MAX_ENTRIES = 2_000
    private const val TTL_MS = 300_000L
    private val lock = Mutex()
    private var cachedDb: AppDatabase? = null
    private var cachedSession: String? = null
    private val fetchedAt = mutableMapOf<String, Long>()

    suspend fun refresh(context: Context, kind: String = "movie", force: Boolean = false,
        db: AppDatabase = AppDatabase.getDatabase(context)): Boolean = withContext(Dispatchers.IO) {
        require(kind in setOf("movie", "tv"))
        lock.withLock {
            val auth = TraktAuthManager.getInstance(context)
            val session = auth.playbackSession.value ?: return@withLock false
            val epoch = auth.currentSessionEpoch()
            if (auth.isSessionInvalid(epoch) || !auth.hasValidToken()) return@withLock false
            if (cachedDb !== db || cachedSession != session) {
                fetchedAt.clear(); cachedDb = db; cachedSession = session
            }
            if (!force && fetchedAt[kind]?.let { SystemClock.elapsedRealtime() - it < TTL_MS } == true) return@withLock true
            try {
                val rows = withTimeout(15_000) {
                    check(auth.refreshAccessTokenIfNeeded() != null) { "Trakt is disconnected" }
                    load(session, kind, TraktSyncEngine.getApiService())
                }
                val committed = auth.runIfSessionValid(epoch) {
                    check(auth.playbackSession.value == session && auth.hasValidToken()) { "Trakt account changed" }
                    db.withTransaction {
                        val dao = db.traktPlaybackDao()
                        dao.clearOtherSessions(session)
                        dao.clearKind(session, kind)
                        rows.chunked(100).forEach { dao.put(it) }
                    }
                }
                if (committed) fetchedAt[kind] = SystemClock.elapsedRealtime()
                committed
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                false
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun load(session: String, kind: String, api: TraktApiService): List<TraktPlaybackEntity> {
        val loaded = mutableListOf<TraktPlaybackEntity>()
        var page = 1
        var received = 0
        while (true) {
            val count: Int
            if (kind == "movie") {
                val entries = api.getPlaybackMovies(page, PAGE_SIZE)
                count = entries.size
                loaded += entries.mapNotNull { row -> row.movie?.ids?.let {
                    position(session, kind, it, row.progress, row.paused_at)
                } }
            } else {
                val entries = api.getPlaybackEpisodes(page, PAGE_SIZE)
                count = entries.size
                loaded += entries.mapNotNull { row -> row.show?.ids?.let {
                    position(session, kind, it, row.progress, row.paused_at, row.episode?.season, row.episode?.number)
                } }
            }
            received += count
            require(received <= MAX_ENTRIES) { "Playback snapshot is too large" }
            if (count < PAGE_SIZE) break
            // An extra empty page is allowed for an exact-size snapshot.
            page++
        }
        // Trakt returns the most recently paused position first. A rewatch must not sum positions.
        return loaded.distinctBy { it.key }
    }

    private fun position(session: String, kind: String, ids: TraktIds, progress: Double, pausedAt: String?,
        season: Int? = null, episode: Int? = null): TraktPlaybackEntity? {
        if (!progress.isFinite() || progress !in 0.0..100.0) return null
        if (kind == "tv" && (season == null || season < 0 || episode == null || episode <= 0)) return null
        val trakt = ids.trakt?.takeIf { it > 0 }
        val tmdb = ids.tmdb?.takeIf { it > 0 }
        val imdb = ids.imdb?.trim()?.lowercase()?.takeIf { it.matches(Regex("tt[0-9]+")) }
        val identity = trakt?.let { "trakt:$it" } ?: tmdb?.let { "tmdb:$it" } ?: imdb?.let { "imdb:$it" } ?: return null
        val key = "$kind:$identity" + if (kind == "tv") ":$season:$episode" else ""
        return TraktPlaybackEntity(session, key, kind, trakt, tmdb, imdb, season, episode, progress, pausedAt)
    }

    /** Match provider identities, never titles; episode progress is the latest episode's playback. */
    fun find(item: SavedItem, rows: List<TraktPlaybackEntity>): TraktPlaybackEntity? {
        val kind = when (item.mediaType?.trim()?.lowercase()) {
            "movie", "film" -> "movie"
            "tv", "tv show", "tv_show", "show", "series", "anime" -> "tv"
            else -> return null
        }
        val ids = TraktSyncEngine.resolveIds(kind, item.id, item.traktId, item.tmdbId, item.imdbId)
        return rows.firstOrNull { row ->
            row.kind == kind && when {
                ids.trakt != null && row.traktId != null -> ids.trakt == row.traktId
                ids.tmdb != null && row.tmdbId != null -> ids.tmdb == row.tmdbId
                !ids.imdb.isNullOrBlank() && row.imdbId != null -> ids.imdb.equals(row.imdbId, ignoreCase = true)
                else -> false
            }
        }?.takeIf { it.progress.isFinite() && it.progress > 0.0 && it.progress < 100.0 }
    }
}

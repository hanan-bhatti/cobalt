package com.example.data.remote.trakt

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Share one bounded account playback fetch across season pages, rather than fetching on every swipe. */
internal object TraktPlaybackCache {
    private const val PAGE_SIZE = 100
    private const val MAX_ENTRIES = 2_000
    private const val TTL_MS = 300_000L
    private val lock = Mutex()
    private var session: String? = null
    private var fetchedAt = 0L
    private var cached = emptyList<TraktPlaybackEpisode>()
    suspend fun positions(context: Context, refresh: Boolean = false): List<TraktPlaybackEpisode> = lock.withLock {
        val auth = TraktAuthManager.getInstance(context)
        auth.refreshAccessTokenIfNeeded()
        val identity = auth.syncSessionId()
        if (!refresh && session == identity && SystemClock.elapsedRealtime() - fetchedAt < TTL_MS) return@withLock cached
        val loaded = ArrayList<TraktPlaybackEpisode>()
        var page = 1
        while (true) {
            val entries = TraktSyncEngine.getApiService().getPlaybackEpisodes(page, PAGE_SIZE)
            loaded += entries.filter { it.progress.isFinite() && it.progress in 0.0..100.0 }
            require(loaded.size <= MAX_ENTRIES) { "Playback response exceeds the supported cache size" }
            if (entries.size < PAGE_SIZE) break
            require(page * PAGE_SIZE <= MAX_ENTRIES) { "Playback response exceeds the supported cache size" }
            page++
        }
        check(identity == auth.syncSessionId() && auth.hasValidToken()) { "Trakt account changed" }
        cached = loaded; session = identity; fetchedAt = SystemClock.elapsedRealtime()
        loaded
    }
}

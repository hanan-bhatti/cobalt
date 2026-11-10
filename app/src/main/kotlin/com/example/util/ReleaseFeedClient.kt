package com.example.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReleaseFeed(val releases: List<PublishedRelease>, val fetchedAt: Long?, val error: String? = null)
internal data class ReleaseFeedCache(val payload: String, val etag: String?, val fetchedAt: Long)
internal data class ReleaseFeedResponse(val status: Int, val payload: String = "", val etag: String? = null)

/** Shared fetch/cache policy; the transport and durable cache are injected for regression tests. */
internal class ReleaseFeedClient(
    private val fetch: suspend (String?) -> ReleaseFeedResponse,
    private val readCache: () -> ReleaseFeedCache?,
    private val writeCache: (ReleaseFeedCache) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private var memory: ReleaseFeedCache? = null
    private var loaded = false
    private var lastError: String? = null

    suspend fun load(allowNetwork: Boolean = false, forceRefresh: Boolean = false): ReleaseFeed = mutex.withLock {
        if (!loaded) {
            memory = try { readCache()?.also { parsePublishedReleases(it.payload) } } catch (_: Exception) { null }
            loaded = true
        }
        val cached = memory
        val age = cached?.let { now() - it.fetchedAt }
        if (!allowNetwork || (!forceRefresh && age != null && age in 0 until 6 * 60 * 60_000L))
            return@withLock ReleaseFeed(cached?.let { parsePublishedReleases(it.payload) } ?: emptyList(), cached?.fetchedAt, lastError)
        try {
            val response = fetch(cached?.etag)
            val record = when (response.status) {
                200 -> ReleaseFeedCache(response.payload, response.etag, now())
                304 -> (cached ?: error("No saved releases to revalidate")).copy(fetchedAt = now(), etag = response.etag ?: cached.etag)
                403, 429 -> error("GitHub rate limit reached. Try again later.")
                else -> error("Could not refresh releases (HTTP ${response.status}). Try again.")
            }
            val releases = parsePublishedReleases(record.payload)
            memory = record
            lastError = null
            try { writeCache(record) } catch (_: Exception) { /* Valid network data remains available for this session. */ }
            ReleaseFeed(releases, record.fetchedAt)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            lastError = when (error) {
                is java.io.IOException -> "Could not refresh releases. Check your connection and retry."
                is org.json.JSONException -> "GitHub returned invalid release data. Try again later."
                else -> error.message ?: "Could not refresh releases. Check your connection and retry."
            }
            ReleaseFeed(cached?.let { parsePublishedReleases(it.payload) } ?: emptyList(), cached?.fetchedAt,
                lastError)
        }
    }
}

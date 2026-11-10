package com.example.util

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseFeedTest {
    private fun asset(version: String = "2.3.0", name: String = "cobalt-v$version.apk", id: Long = 42) =
        JSONObject().put("id", id).put("name", name).put("state", "uploaded").put("size", 1024)
            .put("browser_download_url", "https://github.com/hanan-bhatti/cobalt/releases/download/v$version/$name")
    private fun release(version: String = "2.3.0", assets: JSONArray = JSONArray().put(asset(version))) =
        JSONObject().put("id", 12).put("tag_name", "v$version").put("name", "Cobalt v$version")
            .put("draft", false).put("prerelease", false).put("published_at", "2026-11-11T12:00:00Z")
            .put("html_url", "https://github.com/hanan-bhatti/cobalt/releases/tag/v$version")
            .put("body", "Published notes").put("assets", assets)
    private fun payload(item: JSONObject = release()) = JSONArray().put(item).toString()

    @Test fun exactUploadedApkWinsRegardlessOfAssetOrder() {
        val assets = JSONArray().put(asset(name = "debug.apk")).put(asset())
        val parsed = parsePublishedReleases(payload(release(assets = assets))).single()
        assertEquals("cobalt-v2.3.0.apk", parsed.apks.single().name)
        assertEquals("Published notes", parsed.notes)
    }
    @Test fun historicalFossAndPlayChoicesAreExplicitAndStable() {
        val assets = JSONArray().put(asset("2.2.2", "cobalt-play-v2.2.2.apk", 2))
            .put(asset("2.2.2", "cobalt-foss-v2.2.2.apk", 1))
        val choices = parsePublishedReleases(payload(release("2.2.2", assets))).single().apks
        assertEquals(listOf("FOSS APK", "Play APK"), choices.map { it.label })
        assertEquals(listOf(1L, 2L), choices.map { it.id })
    }
    @Test fun incompleteWrongVersionAndUnsafeUrlsNeverBecomeDownloads() {
        val bad = listOf(
            asset().put("state", "new"), asset().put("size", 0), asset().put("id", 0),
            asset().put("browser_download_url", "https://example.com/cobalt-v2.3.0.apk"),
            asset().put("browser_download_url", "https://github.com/other/repo/releases/download/v2.3.0/cobalt-v2.3.0.apk"),
            asset().put("browser_download_url", "https://github.com/hanan-bhatti/cobalt/releases/download/v2.2.2/cobalt-v2.3.0.apk"),
            asset().put("browser_download_url", "javascript:alert(1)"), asset(name = "cobalt-foss-v2.3.0.apk")
        )
        bad.forEach { assertTrue(parsePublishedReleases(payload(release(assets = JSONArray().put(it)))).single().apks.isEmpty()) }
    }
    @Test fun draftsPrereleasesAndAmbiguousAssetsAreRejected() {
        assertTrue(parsePublishedReleases(payload(release().put("draft", true))).isEmpty())
        assertTrue(parsePublishedReleases(payload(release().put("prerelease", true))).isEmpty())
        assertTrue(parsePublishedReleases(payload(release("2.3.0-beta"))).isEmpty())
        assertTrue(parsePublishedReleases(payload(release(assets = JSONArray().put(asset()).put(asset(id = 43))))).single().apks.isEmpty())
        assertTrue(parsePublishedReleases(JSONArray().put(release()).put(release()).toString()).isEmpty())
    }
    @Test fun invalidReleaseIdentityOrPublicationDateIsRejected() {
        listOf(release().put("id", 0), release().put("published_at", "2026-02-30T12:00:00Z"),
            release().put("published_at", "2026-11-11TinvalidZ"),
            release().put("html_url", "https://example.com/releases/tag/v2.3.0"))
            .forEach { assertTrue(parsePublishedReleases(payload(it)).isEmpty()) }
    }
    @Test fun cacheIsRevalidatedWithEtagAfterExpiry() = runBlocking {
        var time = 1L
        var saved: ReleaseFeedCache? = null
        var calls = 0
        val client = ReleaseFeedClient(fetch = { etag ->
            calls++
            if (calls == 1) ReleaseFeedResponse(200, payload(), "etag-a")
            else { assertEquals("etag-a", etag); ReleaseFeedResponse(304) }
        }, readCache = { null }, writeCache = { saved = it }, now = { time })
        assertEquals(1, client.load(true).releases.size)
        time = 1000
        client.load(true); assertEquals(1, calls)
        time = 7 * 60 * 60_000L
        assertEquals(1, client.load(true).releases.size)
        assertEquals(2, calls); assertEquals(time, saved?.fetchedAt)
    }
    @Test fun offlineModeNeverRequestsNetworkAndRecomputesFromSavedPayload() = runBlocking {
        val client = ReleaseFeedClient(fetch = { error("Must not contact network") },
            readCache = { ReleaseFeedCache(payload(), "old-etag", 1) }, writeCache = {})
        assertEquals("2.3.0", client.load().releases.single().version)
    }
    @Test fun failedRefreshPreservesHistoryButCannotConfirmAnApk() = runBlocking {
        val client = ReleaseFeedClient(fetch = { ReleaseFeedResponse(503) },
            readCache = { ReleaseFeedCache(payload(), "etag", 1) }, writeCache = {})
        val feed = client.load(true, true)
        assertEquals(1, feed.releases.size); assertNotNull(feed.error)
        assertEquals(feed.error, client.load().error)
        assertThrows(IllegalStateException::class.java) { requireReleaseApk(feed, "2.3.0", feed.releases.single().apks.single()) }
        Unit
    }
    @Test fun successfulRetryClearsPreviousRefreshFailure() = runBlocking {
        var calls = 0
        val client = ReleaseFeedClient(fetch = { if (++calls == 1) ReleaseFeedResponse(503) else ReleaseFeedResponse(200, payload()) },
            readCache = { ReleaseFeedCache(payload(), "etag", 1) }, writeCache = {})
        assertNotNull(client.load(true, true).error)
        assertNull(client.load(true, true).error)
        assertNull(client.load().error)
    }
    @Test fun malformedRefreshDoesNotOverwriteUsableCache() = runBlocking {
        var writes = 0
        val client = ReleaseFeedClient(fetch = { ReleaseFeedResponse(200, "<html>Error</html>") },
            readCache = { ReleaseFeedCache(payload(), "etag", 1) }, writeCache = { writes++ })
        val feed = client.load(true, true)
        assertNotNull(feed.error); assertEquals("2.3.0", feed.releases.single().version); assertEquals(0, writes)
    }
    @Test fun storageFailureDoesNotDiscardValidNetworkResponse() = runBlocking {
        var calls = 0
        val client = ReleaseFeedClient(fetch = { calls++; ReleaseFeedResponse(200, payload()) },
            readCache = { null }, writeCache = { error("Disk unavailable") })
        assertNull(client.load(true).error); assertEquals(1, client.load().releases.size); assertEquals(1, calls)
    }
    @Test fun changedOrRemovedAssetCannotBeLaunched() {
        val current = parsePublishedReleases(payload()).single()
        val apk = current.apks.single()
        assertEquals(apk, requireReleaseApk(ReleaseFeed(listOf(current), 1), "2.3.0", apk))
        assertThrows(IllegalStateException::class.java) { requireReleaseApk(ReleaseFeed(listOf(current), 1), "2.3.0", apk.copy(id = 999)) }
        assertThrows(IllegalStateException::class.java) { requireReleaseApk(ReleaseFeed(listOf(current.copy(apks = emptyList())), 1), "2.3.0", apk) }
    }
}

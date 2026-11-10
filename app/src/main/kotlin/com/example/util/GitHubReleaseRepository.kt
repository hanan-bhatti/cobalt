package com.example.util

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** One official release feed shared by changelog history, update checks and APK actions. */
object GitHubReleaseRepository {
    private val clients = mutableMapOf<String, ReleaseFeedClient>()
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private const val API = "https://api.github.com/repos/hanan-bhatti/cobalt/releases?per_page=100"

    private fun client(context: Context): ReleaseFeedClient = synchronized(clients) {
        val directory = context.applicationContext.filesDir
        clients.getOrPut(directory.absolutePath) {
            val cache = AtomicFile(File(directory, "cobalt_release_feed_v2.json"))
            ReleaseFeedClient(fetch = { etag ->
                val request = Request.Builder().url(API).header("User-Agent", "Cobalt-AndroidApp")
                    .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
                if (etag != null) request.header("If-None-Match", etag)
                http.newCall(request.build()).execute().use { response ->
                    val payload = if (response.code == 200) {
                        val source = response.body?.source() ?: error("Empty response from GitHub")
                        if (source.request(4 * 1024 * 1024L + 1)) error("Release response is too large")
                        source.readUtf8()
                    } else ""
                    ReleaseFeedResponse(response.code, payload, response.header("ETag"))
                }
            }, readCache = {
                try {
                    val bytes = cache.openRead().use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 8 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val json = JSONObject(bytes.toString(Charsets.UTF_8))
                    if (json.optInt("schema") != 2) null else ReleaseFeedCache(json.getString("payload"),
                        json.optString("etag").takeIf { it.isNotBlank() }, json.getLong("fetchedAt"))
                } catch (_: Exception) { null }
            }, writeCache = { record ->
                val bytes = JSONObject().put("schema", 2).put("fetchedAt", record.fetchedAt)
                    .put("payload", record.payload).put("etag", record.etag ?: "").toString().toByteArray(Charsets.UTF_8)
                val output = cache.startWrite()
                try { output.write(bytes); cache.finishWrite(output) }
                catch (error: Exception) { cache.failWrite(output); throw error }
            })
        }
    }

    suspend fun load(context: Context, allowNetwork: Boolean = false, forceRefresh: Boolean = false): ReleaseFeed =
        withContext(Dispatchers.IO) { client(context).load(allowNetwork, forceRefresh) }

    suspend fun verifyApk(context: Context, version: String, asset: ReleaseApk): ReleaseApk {
        val feed = load(context, allowNetwork = true, forceRefresh = true)
        return requireReleaseApk(feed, version, asset)
    }
}

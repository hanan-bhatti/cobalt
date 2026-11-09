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

package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Model representing a parsed release entry from dynamic GitHub changelog / releases.
 */
data class ChangelogRelease(
    val versionName: String,
    val releaseDate: String,
    val markdownContent: String,
    val isCurrent: Boolean = false,
    val isNewer: Boolean = false,
    val htmlUrl: String? = null,
    val downloadUrl: String? = null,
    val downloadSizeBytes: Long? = null
) {
    val formattedSize: String?
        get() = formatFileSize(downloadSizeBytes)
}

fun formatFileSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        "${bytes / 1024} KB"
    }
}

/**
 * Dynamic Changelog & Release Manager.
 * Fetches the living CHANGELOG.md directly from GitHub and merges official release assets
 * (download URLs, sizes, and links) from GitHub Releases API.
 * Uses persistent disk caching so release notes load instantly and remain 100% functional offline.
 */
object ChangelogManager {

    private const val GITHUB_CHANGELOG_URL =
        "https://raw.githubusercontent.com/hanan-bhatti/cobalt/main/CHANGELOG.md"
    private const val GITHUB_RELEASES_API =
        "https://api.github.com/repos/hanan-bhatti/cobalt/releases?per_page=25"
    private const val CACHE_FILE_NAME = "cobalt_changelog_cache.json"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var inMemoryCache: List<ChangelogRelease>? = null

    /**
     * Loads releases, trying memory cache, then network (if forceRefresh or cache missing),
     * then disk cache, and finally bundled assets fallback.
     */
    suspend fun getReleases(context: Context, forceRefresh: Boolean = false): List<ChangelogRelease> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh && inMemoryCache != null && inMemoryCache!!.isNotEmpty()) {
                return@withContext inMemoryCache!!
            }

            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)

            // 1. Try network if requested or if no local cache exists
            if (forceRefresh || !cacheFile.exists()) {
                val networkReleases = fetchFromGitHub(context)
                if (networkReleases != null && networkReleases.isNotEmpty()) {
                    inMemoryCache = networkReleases
                    saveToDisk(cacheFile, networkReleases)
                    return@withContext networkReleases
                }
            }

            // 2. Load from disk cache
            if (cacheFile.exists()) {
                val diskReleases = loadFromDisk(cacheFile)
                if (diskReleases != null && diskReleases.isNotEmpty()) {
                    inMemoryCache = diskReleases
                    return@withContext diskReleases
                }
            }

            // 3. Fallback to bundled asset
            val assetReleases = loadFromAssets(context)
            inMemoryCache = assetReleases
            assetReleases
        }

    /**
     * Synchronously returns cached releases or falls back to assets.
     */
    fun getCachedReleases(context: Context): List<ChangelogRelease> {
        inMemoryCache?.let { return it }
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (cacheFile.exists()) {
            loadFromDisk(cacheFile)?.let {
                inMemoryCache = it
                return it
            }
        }
        val fromAssets = loadFromAssets(context)
        inMemoryCache = fromAssets
        return fromAssets
    }

    private fun fetchFromGitHub(context: Context): List<ChangelogRelease>? {
        try {
            // Step 1: Fetch raw CHANGELOG.md
            val changelogReq = Request.Builder()
                .url(GITHUB_CHANGELOG_URL)
                .header("User-Agent", "Cobalt-AndroidApp")
                .build()

            val rawMarkdown = okHttpClient.newCall(changelogReq).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return null

            // Step 2: Parse sections
            val releases = parseChangelog(rawMarkdown).toMutableList()

            // Step 3: Fetch GitHub Releases API metadata (assets, sizes, URLs)
            try {
                val releasesApiReq = Request.Builder()
                    .url(GITHUB_RELEASES_API)
                    .header("User-Agent", "Cobalt-AndroidApp")
                    .header("Accept", "application/vnd.github+json")
                    .build()

                okHttpClient.newCall(releasesApiReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val jsonArray = JSONArray(body)
                            enrichWithGitHubMetadata(releases, jsonArray)
                        }
                    }
                }
            } catch (_: Exception) {
                // Non-fatal: even if GitHub Releases API fails or rate-limits, we keep the parsed CHANGELOG.md!
            }

            return releases
        } catch (_: Exception) {
            return null
        }
    }

    fun parseChangelog(markdownText: String): List<ChangelogRelease> {
        val sectionRegex = Regex("""^##\s+(?:\[([^\]]+)]|([^\n—\-]+))(?:\s*[-—]\s*([^\n]+))?""", RegexOption.MULTILINE)
        val matches = sectionRegex.findAll(markdownText).toList()
        val releases = mutableListOf<ChangelogRelease>()
        val currentVer = AppVersionManager.currentVersionName

        for (i in matches.indices) {
            val m = matches[i]
            val rawVer = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
            if (rawVer.isBlank()) continue
            val version = rawVer.removePrefix("v").trim()
            val date = m.groupValues[3].trim()

            val start = m.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else markdownText.length
            val body = markdownText.substring(start, end).trim()

            val isCurrent = version.equals(currentVer, ignoreCase = true)
            val isNewer = AppVersionManager.isVersionNewer(version, currentVer)

            releases.add(
                ChangelogRelease(
                    versionName = version,
                    releaseDate = date,
                    markdownContent = body,
                    isCurrent = isCurrent,
                    isNewer = isNewer
                )
            )
        }
        return releases
    }

    private fun enrichWithGitHubMetadata(releases: MutableList<ChangelogRelease>, jsonArray: JSONArray) {
        val metadataMap = mutableMapOf<String, Triple<String, String?, Long?>>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val tag = obj.optString("tag_name", "").removePrefix("v").trim()
            val htmlUrl = obj.optString("html_url", "")

            var downloadUrl: String? = null
            var sizeBytes: Long? = null

            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        sizeBytes = asset.optLong("size", 0L)
                        break
                    }
                }
            }
            metadataMap[tag] = Triple(htmlUrl, downloadUrl, sizeBytes)
        }

        for (i in releases.indices) {
            val rel = releases[i]
            val meta = metadataMap[rel.versionName]
            if (meta != null) {
                releases[i] = rel.copy(
                    htmlUrl = meta.first.ifBlank { rel.htmlUrl },
                    downloadUrl = meta.second ?: rel.downloadUrl,
                    downloadSizeBytes = if (meta.third != null && meta.third!! > 0) meta.third else rel.downloadSizeBytes
                )
            }
        }
    }

    private fun loadFromAssets(context: Context): List<ChangelogRelease> {
        return try {
            context.assets.open("CHANGELOG.md").bufferedReader().use { reader ->
                parseChangelog(reader.readText())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToDisk(file: File, releases: List<ChangelogRelease>) {
        try {
            val array = JSONArray()
            for (r in releases) {
                val obj = JSONObject()
                obj.put("versionName", r.versionName)
                obj.put("releaseDate", r.releaseDate)
                obj.put("markdownContent", r.markdownContent)
                obj.put("isCurrent", r.isCurrent)
                obj.put("isNewer", r.isNewer)
                if (r.htmlUrl != null) obj.put("htmlUrl", r.htmlUrl)
                if (r.downloadUrl != null) obj.put("downloadUrl", r.downloadUrl)
                if (r.downloadSizeBytes != null) obj.put("downloadSizeBytes", r.downloadSizeBytes)
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {}
    }

    private fun loadFromDisk(file: File): List<ChangelogRelease>? {
        return try {
            val text = file.readText()
            if (text.isBlank()) return null
            val array = JSONArray(text)
            val currentVer = AppVersionManager.currentVersionName
            val list = mutableListOf<ChangelogRelease>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val ver = obj.getString("versionName")
                list.add(
                    ChangelogRelease(
                        versionName = ver,
                        releaseDate = obj.optString("releaseDate", ""),
                        markdownContent = obj.optString("markdownContent", ""),
                        isCurrent = ver.equals(currentVer, ignoreCase = true),
                        isNewer = AppVersionManager.isVersionNewer(ver, currentVer),
                        htmlUrl = obj.optString("htmlUrl", "").takeIf { it.isNotBlank() },
                        downloadUrl = obj.optString("downloadUrl", "").takeIf { it.isNotBlank() },
                        downloadSizeBytes = if (obj.has("downloadSizeBytes")) obj.getLong("downloadSizeBytes") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            null
        }
    }
}

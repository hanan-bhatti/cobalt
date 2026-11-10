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

/** Installed notes come from this APK; published history and downloads come from GitHub Releases. */
object ChangelogManager {
    @Volatile private var bundledCache: List<ChangelogRelease>? = null
    @Volatile private var inMemoryCache: List<ChangelogRelease>? = null
    @Volatile var lastFeed: ReleaseFeed? = null
        private set

    suspend fun getReleases(context: Context, forceRefresh: Boolean = false, allowNetwork: Boolean = false): List<ChangelogRelease> = withContext(Dispatchers.IO) {
        val bundled = loadFromAssets(context)
        val feed = GitHubReleaseRepository.load(context, allowNetwork || forceRefresh, forceRefresh)
        lastFeed = feed
        val downloaded = feed.releases.map { release ->
            val current = release.version == AppVersionManager.currentVersionName
            val single = release.apks.singleOrNull()
            ChangelogRelease(release.version, release.publishedAt.substringBefore('T'), release.notes,
                isCurrent = current, isNewer = compareStableVersions(release.version, AppVersionManager.currentVersionName) > 0,
                htmlUrl = release.htmlUrl, downloadUrl = single?.url, downloadSizeBytes = single?.sizeBytes, apkAssets = release.apks)
        }
        mergeBundledReleaseNotes(bundled, downloaded).also { inMemoryCache = it }
    }

    suspend fun getCachedReleases(context: Context): List<ChangelogRelease> = withContext(Dispatchers.IO) {
        mergeBundledReleaseNotes(loadFromAssets(context), inMemoryCache ?: emptyList())
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

    private fun loadFromAssets(context: Context): List<ChangelogRelease> {
        bundledCache?.let { return it }
        return try {
            context.assets.open("CHANGELOG.md").bufferedReader().use {
                parseChangelog(it.readText()).also { notes -> if (notes.isNotEmpty()) bundledCache = notes }
            }
        } catch (_: Exception) { emptyList() }
    }
}

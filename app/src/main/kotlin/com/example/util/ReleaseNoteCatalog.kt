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

import java.util.Locale
import java.net.URI

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
    val downloadSizeBytes: Long? = null,
    val apkAssets: List<ReleaseApk> = emptyList()
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

/** Keep this APK's release text authoritative while retaining GitHub asset links. */
internal fun mergeBundledReleaseNotes(
    bundled: List<ChangelogRelease>,
    downloaded: List<ChangelogRelease>
): List<ChangelogRelease> {
    if (downloaded.isEmpty()) return bundled
    val installed = bundled.firstOrNull { it.isCurrent } ?: return downloaded
    val remote = downloaded.firstOrNull { it.versionName.equals(installed.versionName, ignoreCase = true) }
    val current = installed.copy(
        htmlUrl = remote?.htmlUrl ?: installed.htmlUrl,
        downloadUrl = remote?.downloadUrl ?: installed.downloadUrl,
        downloadSizeBytes = remote?.downloadSizeBytes ?: installed.downloadSizeBytes,
        apkAssets = remote?.apkAssets ?: installed.apkAssets
    )
    val history = downloaded.filterNot {
        it.versionName.equals(installed.versionName, ignoreCase = true) ||
            (!it.isNewer && it.releaseDate.equals("Unreleased", ignoreCase = true))
    }.toMutableList()
    if (history.isEmpty()) return bundled.map { if (it.isCurrent) current else it }
    val position = history.indexOfFirst { !it.isNewer }.takeIf { it >= 0 } ?: history.size
    history.add(position, current)
    return history
}

/** Identity of an uploaded asset supplied by the official GitHub release API. */
data class ReleaseApk(val id: Long, val name: String, val label: String, val url: String, val sizeBytes: Long)

data class PublishedRelease(
    val id: Long, val tag: String, val version: String, val name: String,
    val publishedAt: String, val notes: String, val htmlUrl: String, val apks: List<ReleaseApk>
)

internal fun stableVersionParts(version: String): List<Long>? {
    if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version)) return null
    return version.split('.').map { it.toLongOrNull() ?: return null }
}

internal fun compareStableVersions(a: String, b: String): Int {
    val left = stableVersionParts(a) ?: return -1
    val right = stableVersionParts(b.substringBefore('-')) ?: return 1
    for (i in left.indices) if (left[i] != right[i]) return left[i].compareTo(right[i])
    return 0
}

internal fun officialGitHubUrl(url: String, path: String): Boolean = try {
    val uri = URI(url)
    uri.scheme == "https" && uri.host.equals("github.com", true) && uri.port == -1 &&
        uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath == path
} catch (_: Exception) { false }

internal fun expectedApkNames(version: String): List<Pair<String, String>> =
    if (compareStableVersions(version, "2.3.0") >= 0) listOf("cobalt-v$version.apk" to "Download APK")
    else listOf("cobalt-foss-v$version.apk" to "FOSS APK", "cobalt-play-v$version.apk" to "Play APK", "cobalt-v$version.apk" to "Download APK")

internal fun requireReleaseApk(feed: ReleaseFeed, version: String, asset: ReleaseApk): ReleaseApk {
    if (feed.error != null) error(feed.error)
    return feed.releases.singleOrNull { it.version == version }?.apks?.singleOrNull { it == asset }
        ?: error("This APK is no longer available. Refresh the release and try again.")
}

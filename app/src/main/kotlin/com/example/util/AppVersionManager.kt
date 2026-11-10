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
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Result model for real-time online update checks against GitHub releases.
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val latestVersionName: String,
        val releaseName: String = "",
        val releaseDate: String = "",
        val releaseUrl: String = "",
        val releaseNotes: String = "",
        val apkDownloadUrl: String? = null,
        val apkSizeBytes: Long? = null,
        val apkAssets: List<ReleaseApk> = emptyList()
    ) : UpdateCheckResult() {
        val formattedSize: String?
            get() = formatFileSize(apkSizeBytes)
    }

    data class UpToDate(val latestVersionName: String = "") : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Supported release tags/channels with curated, high-contrast M3 colors.
 */
enum class AppVersionTag(
    val label: String,
    val description: String
) {
    ALPHA("ALPHA", "Experimental feature build"),
    BETA("BETA", "Preview build for testing new features"),
    NIGHTLY("NIGHTLY", "Bleeding-edge daily development build"),
    RC("RC", "Release candidate build"),
    STABLE("STABLE", "Official stable production release"),
    DEV("DEV", "Local developer build");

    @Composable
    fun containerColor(): Color {
        return when (this) {
            ALPHA -> Color(0xFFFCE4EC)
            BETA -> Color(0xFFFFF3E0)
            NIGHTLY -> Color(0xFFEDE7F6)
            RC -> Color(0xFFE0F7FA)
            STABLE -> Color(0xFFE8F5E9)
            DEV -> Color(0xFFFFF8E1)
        }
    }

    @Composable
    fun contentColor(): Color {
        return when (this) {
            ALPHA -> Color(0xFFC2185B)
            BETA -> Color(0xFFE65100)
            NIGHTLY -> Color(0xFF512DA8)
            RC -> Color(0xFF00796B)
            STABLE -> Color(0xFF2E7D32)
            DEV -> Color(0xFFF57F17)
        }
    }
}

/**
 * Data model for an app release note entry.
 */
data class ReleaseNote(
    val versionName: String,
    val versionCode: Int = 0,
    val releaseDate: String = "",
    val tag: AppVersionTag = AppVersionTag.STABLE,
    val isCurrent: Boolean = false,
    val isLatest: Boolean = false,
    val highlights: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val bugFixes: List<String> = emptyList(),
    val markdownContent: String = "",
    val downloadUrl: String? = null,
    val downloadSizeBytes: Long? = null,
    val apkAssets: List<ReleaseApk> = emptyList()
)

/**
 * Central manager for App Version information and update checks.
 * Integrates directly with dynamic GitHub releases and CHANGELOG.md.
 */
object AppVersionManager {

    fun parseVersionTag(versionName: String = com.example.BuildConfig.VERSION_NAME): AppVersionTag {
        val lower = versionName.lowercase()
        return when {
            lower.contains("alpha") -> AppVersionTag.ALPHA
            lower.contains("beta") -> AppVersionTag.BETA
            lower.contains("nightly") -> AppVersionTag.NIGHTLY
            lower.contains("rc") -> AppVersionTag.RC
            lower.contains("dev") -> AppVersionTag.DEV
            else -> AppVersionTag.STABLE
        }
    }

    val currentTag: AppVersionTag
        get() = parseVersionTag(com.example.BuildConfig.VERSION_NAME)

    val currentVersionName: String
        get() = com.example.BuildConfig.VERSION_NAME

    val currentVersionCode: Int
        get() = com.example.BuildConfig.VERSION_CODE

    val releaseHistory: List<ReleaseNote> = listOf(
        ReleaseNote(
            versionName = currentVersionName,
            versionCode = currentVersionCode,
            releaseDate = "2026-11-11",
            tag = currentTag,
            isCurrent = true,
            isLatest = true,
            highlights = listOf(
                "Continuous Markdown Editing & Recoverable Drafts",
                "Shared Note History, Saved Undo/Redo & Protected Storage Cleanup",
                "Trakt Sync, Custom Jikan Providers & Anime/Manga Controls",
                "Styled Sharing, Local Fonts, Smart Folders & Adaptive Reminders"
            )
        )
    )

    fun getLatestRelease(): ReleaseNote = releaseHistory.first()

    fun isUpdateAvailable(): Boolean = false

    /**
     * Performs a real HTTP network query against GitHub Releases API to check for updates.
     */
    suspend fun checkOnlineUpdates(context: Context? = null): UpdateCheckResult {
        if (context == null) return UpdateCheckResult.Error("Open Updates & Changelog to check for releases.")
        val feed = GitHubReleaseRepository.load(context, allowNetwork = true, forceRefresh = true)
        if (feed.error != null) return UpdateCheckResult.Error(feed.error)
        val latest = feed.releases.firstOrNull()
            ?: return UpdateCheckResult.Error("No published stable releases are available.")
        if (compareStableVersions(latest.version, currentVersionName) <= 0) return UpdateCheckResult.UpToDate(currentVersionName)
        val single = latest.apks.singleOrNull()
        return UpdateCheckResult.UpdateAvailable(
            latestVersionName = latest.version, releaseName = latest.name,
            releaseDate = formatIsoDate(latest.publishedAt), releaseUrl = latest.htmlUrl,
            releaseNotes = latest.notes, apkDownloadUrl = single?.url, apkSizeBytes = single?.sizeBytes,
            apkAssets = latest.apks
        )
    }

    private fun formatIsoDate(isoString: String): String {
        if (isoString.isBlank()) return ""
        return try {
            val datePart = isoString.substringBefore("T")
            val parts = datePart.split("-")
            if (parts.size == 3) {
                val year = parts[0]
                val month = when (parts[1].toIntOrNull()) {
                    1 -> "Jan"
                    2 -> "Feb"
                    3 -> "Mar"
                    4 -> "Apr"
                    5 -> "May"
                    6 -> "Jun"
                    7 -> "Jul"
                    8 -> "Aug"
                    9 -> "Sep"
                    10 -> "Oct"
                    11 -> "Nov"
                    12 -> "Dec"
                    else -> parts[1]
                }
                val day = parts[2].toIntOrNull()?.toString() ?: parts[2]
                "$month $day, $year"
            } else {
                datePart
            }
        } catch (_: Exception) {
            isoString
        }
    }

    /**
     * Compares remote and local version strings to check if remote is newer.
     */
    fun isVersionNewer(remoteVersion: String, localVersion: String): Boolean {
        val cleanRemote = remoteVersion.removePrefix("v").trim()
        val cleanLocal = localVersion.removePrefix("v").trim()
        if (cleanRemote == cleanLocal) return false

        val remoteParts = cleanRemote.split("-", ".").mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }
        val localParts = cleanLocal.split("-", ".").mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}

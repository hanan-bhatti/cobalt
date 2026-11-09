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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
        val apkSizeBytes: Long? = null
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
    val downloadSizeBytes: Long? = null
)

/**
 * Central manager for App Version information and update checks.
 * Integrates directly with dynamic GitHub releases and CHANGELOG.md.
 */
object AppVersionManager {

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

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
            releaseDate = "Unreleased",
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
    suspend fun checkOnlineUpdates(context: Context? = null): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/hanan-bhatti/cobalt/releases/latest")
                .header("User-Agent", "Cobalt-AndroidApp")
                .header("Accept", "application/vnd.github+json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        return@withContext UpdateCheckResult.UpToDate(currentVersionName)
                    }
                    return@withContext UpdateCheckResult.Error("HTTP ${response.code}: ${response.message}")
                }

                val responseBody = response.body?.string() ?: return@withContext UpdateCheckResult.Error("Empty response from server")
                val json = JSONObject(responseBody)

                val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                val htmlUrl = json.optString("html_url", "https://github.com/hanan-bhatti/cobalt/releases")
                val releaseName = json.optString("name", "Cobalt v$tagName")
                val publishedAt = json.optString("published_at", "")
                var bodyText = json.optString("body", "").trim()

                // Extract APK asset if available
                var apkUrl: String? = null
                var apkSize: Long? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                // If release body is short or just comparison link, attempt enriching from dynamic changelog
                if (context != null && (bodyText.isBlank() || (bodyText.contains("Full Changelog") && bodyText.length < 150))) {
                    try {
                        val releases = ChangelogManager.getReleases(context, forceRefresh = false)
                        val matching = releases.firstOrNull { it.versionName.equals(tagName, ignoreCase = true) }
                        if (matching != null && matching.markdownContent.isNotBlank()) {
                            bodyText = matching.markdownContent
                        }
                    } catch (_: Exception) {}
                }

                val formattedDate = formatIsoDate(publishedAt)

                if (tagName.isNotBlank() && isVersionNewer(tagName, currentVersionName)) {
                    UpdateCheckResult.UpdateAvailable(
                        latestVersionName = tagName,
                        releaseName = releaseName,
                        releaseDate = formattedDate,
                        releaseUrl = htmlUrl,
                        releaseNotes = bodyText,
                        apkDownloadUrl = apkUrl,
                        apkSizeBytes = if (apkSize != null && apkSize > 0) apkSize else null
                    )
                } else {
                    UpdateCheckResult.UpToDate(tagName.ifBlank { currentVersionName })
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to connect to update server")
        }
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

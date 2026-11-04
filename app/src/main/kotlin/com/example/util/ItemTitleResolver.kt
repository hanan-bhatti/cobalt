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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fallback localized strings and formatters used by [ItemTitleResolver].
 * Default parameters allow pure unit testing without Android context.
 */
data class TitleFallbacks(
    val untitledImage: String = "Untitled image",
    val untitledVideo: String = "Untitled video",
    val untitledAudio: String = "Untitled audio",
    val untitledVoiceMemo: String = "Untitled voice memo",
    val untitledFile: String = "Untitled file",
    val untitledScreenshot: String = "Untitled screenshot",
    val untitledNote: String = "Untitled note",
    val untitledLink: String = "Untitled link",
    val untitledItem: String = "Untitled item",
    val voiceMemoDateFormatter: ((Long) -> String)? = null,
    val cameraPhotoDateFormatter: ((Long) -> String)? = null
)

/**
 * Centralized, pure, unit-testable title resolver and sanitization engine (GitHub issue #21).
 *
 * Ensures internal paths (e.g. `/data/user/0/.../cache/media_...`, `file:`, `content:`)
 * and generated cache names are never exposed as user-facing titles.
 */
object ItemTitleResolver {

    const val MAX_TITLE_LENGTH = 120

    // Matches Cobalt's internal generated cache names (e.g. media_<uuid>, shared_img_<uuid>, audio_<uuid>, etc.)
    private val COBALT_CACHE_NAME_REGEX = Regex(
        """^(?:media|shared_img|shared_vid|audio|thumb|share|note)_[0-9a-fA-F]{8,}(?:-[0-9a-fA-F]{4,})*.*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Resolves the user-facing title for any item type across all capture and storage paths.
     *
     * Rules:
     * 1. A non-blank user-typed title always wins (trimmed), provided it does not look like an internal path/cache URI.
     * 2. Otherwise use original display name (from [OpenableColumns.DISPLAY_NAME]), with file extension removed,
     *    path separators and control characters stripped, trimmed, and capped to [MAX_TITLE_LENGTH] without splitting surrogate pairs.
     * 3. For voice memos and camera captures with no original file name, generate a readable name formatted with user's locale.
     * 4. Otherwise, a localized fallback per item type ("Untitled image", "Untitled video", etc.).
     * 5. Candidates that look like internal paths, URIs, or match Cobalt's internal cache naming are rejected.
     */
    fun resolveItemTitle(
        userTitle: String?,
        displayName: String?,
        type: SavedItemType,
        isVoiceMemo: Boolean = false,
        isCameraCapture: Boolean = false,
        isScreenshot: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
        fallbacks: TitleFallbacks = TitleFallbacks()
    ): String {
        // Rule 1: Non-blank user-typed title wins if not an internal path / cache pattern
        if (!userTitle.isNullOrBlank()) {
            if (!isInternalPathOrGeneratedName(userTitle)) {
                val sanitizedUserTitle = sanitizeCandidate(userTitle)
                if (sanitizedUserTitle.isNotBlank() && !isInternalPathOrGeneratedName(sanitizedUserTitle)) {
                    return truncateSafely(sanitizedUserTitle, MAX_TITLE_LENGTH)
                }
            }
        }

        // Rule 2: Original display name
        if (!displayName.isNullOrBlank()) {
            if (!isInternalPathOrGeneratedName(displayName)) {
                val sanitized = sanitizeDisplayName(displayName)
                val withoutExt = removeExtension(sanitized)
                val cleanName = truncateSafely(withoutExt.trim(), MAX_TITLE_LENGTH)
                if (cleanName.isNotBlank() && !isInternalPathOrGeneratedName(cleanName)) {
                    return cleanName
                }
            }
        }

        // Rule 3: Generated readable date/time name for voice memos & camera captures
        if (type == SavedItemType.AUDIO && isVoiceMemo) {
            return fallbacks.voiceMemoDateFormatter?.invoke(timestamp)
                ?: ("Voice memo, " + formatTimestamp(timestamp))
        }
        if (type == SavedItemType.IMAGE && isCameraCapture) {
            return fallbacks.cameraPhotoDateFormatter?.invoke(timestamp)
                ?: ("Photo, " + formatTimestamp(timestamp))
        }

        // Rule 4: Localized fallbacks
        if (isScreenshot) return fallbacks.untitledScreenshot
        return when (type) {
            SavedItemType.IMAGE -> fallbacks.untitledImage
            SavedItemType.VIDEO -> fallbacks.untitledVideo
            SavedItemType.AUDIO -> if (isVoiceMemo) fallbacks.untitledVoiceMemo else fallbacks.untitledAudio
            SavedItemType.TEXT, SavedItemType.CODE -> fallbacks.untitledNote
            SavedItemType.LINK -> fallbacks.untitledLink
            SavedItemType.MEDIA -> fallbacks.untitledItem
        }
    }

    /**
     * Checks if a candidate string looks like an internal path, URI, or Cobalt internal cache artifact.
     *
     * Explicit rule:
     * Numeric names, camera temp names (IMG_, VID_), or bare UUIDs are acceptable when they come
     * from the user's original file name, but rejected if they match Cobalt's cache naming (`media_<uuid>`).
     */
    fun isInternalPathOrGeneratedName(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return true
        val trimmed = candidate.trim()

        // 1. Path indicators
        if (trimmed.startsWith("/") || trimmed.startsWith("\\")) return true

        // 2. URI schemes
        if (trimmed.startsWith("file:", ignoreCase = true) ||
            trimmed.startsWith("content:", ignoreCase = true)
        ) return true

        // 3. Contains cache or internal files directories
        if (trimmed.contains("/cache/", ignoreCase = true) ||
            trimmed.contains("\\cache\\", ignoreCase = true) ||
            trimmed.contains("/files/", ignoreCase = true) ||
            trimmed.contains("\\files\\", ignoreCase = true) ||
            trimmed.contains("/data/user/", ignoreCase = true) ||
            trimmed.contains("/data/data/", ignoreCase = true)
        ) return true

        // 4. Cobalt internal cache file naming pattern (e.g. media_<uuid>, shared_img_<uuid>, etc.)
        if (COBALT_CACHE_NAME_REGEX.matches(trimmed)) return true

        return false
    }

    /**
     * Removes the last extension from a filename if one exists.
     * Preserves leading dots for hidden files without further extensions (e.g. `.nomedia`).
     */
    fun removeExtension(name: String): String {
        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0) {
            name.substring(0, lastDot)
        } else {
            name
        }
    }

    /**
     * Strips path separators and control characters from display names.
     */
    fun sanitizeDisplayName(rawName: String): String {
        val sb = java.lang.StringBuilder(rawName.length)
        for (i in 0 until rawName.length) {
            val c = rawName[i]
            if (c != '/' && c != '\\' && !c.isISOControl()) {
                sb.append(c)
            }
        }
        return sb.toString().trim()
    }

    private fun sanitizeCandidate(candidate: String): String {
        val sb = java.lang.StringBuilder(candidate.length)
        for (i in 0 until candidate.length) {
            val c = candidate[i]
            if (!c.isISOControl()) {
                sb.append(c)
            }
        }
        return sb.toString().trim()
    }

    /**
     * Truncates a string to [maxLength] characters without splitting UTF-16 surrogate pairs.
     */
    fun truncateSafely(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        var end = maxLength
        if (Character.isHighSurrogate(text[end - 1])) {
            end--
        }
        return text.substring(0, end).trimEnd()
    }

    /**
     * Safely queries [OpenableColumns.DISPLAY_NAME] from a content URI or resolves a file name.
     */
    fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1 && cursor.moveToFirst()) {
                            cursor.getString(index)?.takeIf { it.isNotBlank() }
                        } else null
                    }
                }
                ContentResolver.SCHEME_FILE -> {
                    uri.lastPathSegment?.takeIf { it.isNotBlank() }
                }
                else -> uri.lastPathSegment?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Display-time guard to ensure existing stored items with internal paths show a safe fallback.
     */
    fun getDisplaySafeTitle(
        rawTitle: String?,
        type: SavedItemType,
        linkTitle: String? = null,
        fallbacks: TitleFallbacks = TitleFallbacks()
    ): String {
        val trimmed = rawTitle?.trim().orEmpty()
        if (trimmed.isNotBlank() && !isInternalPathOrGeneratedName(trimmed)) {
            return trimmed
        }
        if (type == SavedItemType.LINK && !linkTitle.isNullOrBlank() && !isInternalPathOrGeneratedName(linkTitle)) {
            return linkTitle.trim()
        }
        return when (type) {
            SavedItemType.IMAGE -> fallbacks.untitledImage
            SavedItemType.VIDEO -> fallbacks.untitledVideo
            SavedItemType.AUDIO -> fallbacks.untitledAudio
            SavedItemType.TEXT, SavedItemType.CODE -> fallbacks.untitledNote
            SavedItemType.LINK -> fallbacks.untitledLink
            SavedItemType.MEDIA -> fallbacks.untitledItem
        }
    }

    /**
     * Display-time guard to ensure subtitles/descriptions never leak internal paths or cache URIs.
     */
    fun getDisplaySafeDescription(
        rawDescription: String?,
        type: SavedItemType
    ): String {
        val trimmed = rawDescription?.trim().orEmpty()
        if (trimmed.isBlank() || isInternalPathOrGeneratedName(trimmed)) {
            return ""
        }
        return trimmed
    }

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val format = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
            format.format(Date(timestamp))
        } catch (_: Exception) {
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ROOT).format(Date(timestamp))
        }
    }
}

/**
 * Convenience extension to load localized fallbacks from Android Context.
 */
fun Context.loadTitleFallbacks(): TitleFallbacks {
    val locale = try {
        resources.configuration.locales.get(0) ?: Locale.getDefault()
    } catch (_: Exception) {
        Locale.getDefault()
    }
    val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", locale)
    return TitleFallbacks(
        untitledImage = getString(R.string.title_untitled_image),
        untitledVideo = getString(R.string.title_untitled_video),
        untitledAudio = getString(R.string.title_untitled_audio),
        untitledVoiceMemo = getString(R.string.title_untitled_voice_memo),
        untitledFile = getString(R.string.title_untitled_file),
        untitledScreenshot = getString(R.string.title_untitled_screenshot),
        untitledNote = getString(R.string.title_untitled_note),
        untitledLink = getString(R.string.title_untitled_link),
        untitledItem = getString(R.string.title_untitled_item),
        voiceMemoDateFormatter = { ts ->
            getString(R.string.title_voice_memo_timestamp, dateFormat.format(Date(ts)))
        },
        cameraPhotoDateFormatter = { ts ->
            getString(R.string.title_photo_timestamp, dateFormat.format(Date(ts)))
        }
    )
}

/**
 * Display-time guard extension on [SavedItem].
 */
fun SavedItem.displayTitle(context: Context? = null): String {
    val fallbacks = context?.loadTitleFallbacks() ?: TitleFallbacks()
    return ItemTitleResolver.getDisplaySafeTitle(this.title, this.type, this.linkTitle, fallbacks)
}

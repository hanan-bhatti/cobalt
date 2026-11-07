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

package com.example.data.model

import java.util.UUID
import com.squareup.moshi.JsonClass

enum class SavedItemType(val displayName: String) {
    LINK("Links"),
    IMAGE("Images"),
    VIDEO("Videos"),
    CODE("Code"),
    TEXT("Text"),
    AUDIO("Audio"),
    MEDIA("Movies & Anime")
}

@JsonClass(generateAdapter = true)
data class SavedItem(
    val id: String = UUID.randomUUID().toString(),
    val type: SavedItemType,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val folders: List<String> = emptyList(),
    val extractedText: String? = null,
    val thumbnailPath: String? = null,
    val orderIndex: Double = timestamp.toDouble(),
    // Legacy v8 metadata only; no account, backup or cloud-sync behavior.
    // Retained until an explicit migration so existing records remain compatible.
    val isSynced: Boolean = false,
    val linkTitle: String? = null,
    val linkDescription: String? = null,
    val linkImage: String? = null,
    val isBackedUp: Boolean = false,
    val sizeBytes: Long = 0,
    val isPendingBackup: Boolean = false,
    val isUnavailable: Boolean = false,
    val mediaType: String? = null,
    val watchStatus: String? = null,
    val genres: List<String> = emptyList(),
    val watchProviders: List<String> = emptyList(),
    val richWatchProviders: List<MediaWatchProvider> = emptyList(),
    val trailerUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseYear: String? = null,
    val rating: Double? = null,
    val runtime: Int? = null,
    val tagline: String? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val status: String? = null,
    val productionCompanies: List<String> = emptyList(),
    val isArchived: Boolean = false,
    val updatedAt: Long = timestamp,
    val isDeleted: Boolean = false,
    val traktId: Long? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val inWatchlist: Boolean = false,
    val userRating: Int? = null,
    val lastWatchedAt: Long? = null,
    val showProgress: TvShowProgress? = null,
    val mediaFacts: MediaFacts? = null,
    val watchStatusOverride: String? = null,
    val bodyLocation: String = "INLINE",
    val contentHash: String? = null,
    val byteLength: Long = 0,
    val formatVersion: Int = 0,
    val preview: String = "",
    val bodyMissing: Boolean = false,
    val bodyLoaded: Boolean = true
)

fun SavedItem.getBestImagePath(): String? {
    val thumb = this.thumbnailPath
    if (!thumb.isNullOrBlank()) {
        if (thumb.startsWith("http://") || thumb.startsWith("https://") || thumb.startsWith("content://")) {
            return thumb
        }
        val file = java.io.File(thumb)
        if (file.exists()) {
            return thumb
        }
    }
    
    // For images, the content itself might be the path
    if (this.type == SavedItemType.IMAGE) {
        val content = this.content
        if (content.startsWith("http://") || content.startsWith("https://") || content.startsWith("content://")) {
            return content
        }
        val contentFile = java.io.File(content)
        if (contentFile.exists()) {
            return content
        }
    }
    
    // For links, use linkImage
    if (this.type == SavedItemType.LINK && !this.linkImage.isNullOrBlank()) {
        return this.linkImage
    }

    // For media items, fallback to backdropUrl if poster/thumbnail is not yet cached
    if (this.type == SavedItemType.MEDIA && !this.backdropUrl.isNullOrBlank()) {
        return this.backdropUrl
    }
    
    return null
}

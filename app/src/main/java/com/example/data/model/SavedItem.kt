/*
 * Second Brain - A universal capture and personal knowledge archive
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

enum class SavedItemType(val displayName: String) {
    LINK("Links"),
    IMAGE("Images"),
    VIDEO("Videos"),
    CODE("Code"),
    TEXT("Text"),
    AUDIO("Audio"),
    MEDIA("Movies & Anime")
}

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
    val trailerUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseYear: String? = null
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
    
    return null
}

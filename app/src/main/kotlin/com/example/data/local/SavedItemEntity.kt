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

package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_items",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["type"]),
        Index(value = ["isArchived"]),
        Index(value = ["updatedAt"]),
        Index(value = ["traktId"]),
        Index(value = ["tmdbId"])
    ]
)
data class SavedItemEntity(
    @PrimaryKey val id: String,
    val type: String,          // "Link", "Image", "Video", "Code", "Text", "MEDIA"
    val title: String,
    val content: String,        // text, URL, or local URI
    val timestamp: Long,
    val foldersJson: String,    // custom folders serialized as JSON array of strings
    val extractedText: String?, // OCR result
    val thumbnailPath: String?, // local file path or cache URI
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
    val genresJson: String = "[]",
    val watchProvidersJson: String = "[]",
    val trailerUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseYear: String? = null,
    val rating: Double? = null,
    val runtime: Int? = null,
    val tagline: String? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val status: String? = null,
    val productionCompaniesJson: String = "[]",
    val isArchived: Boolean = false,
    val updatedAt: Long = timestamp,
    val isDeleted: Boolean = false,
    val traktId: Long? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val userRating: Int? = null,
    val lastWatchedAt: Long? = null,
    val showProgressJson: String? = null
) {
    fun toDomainModel(): com.example.data.model.SavedItem {
        val foldersList = try {
            foldersJson.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
        val genresList = try {
            genresJson.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
        val richProviders = com.example.data.model.MediaWatchProvider.listFromJson(watchProvidersJson)
        val watchProvidersList = if (richProviders.isNotEmpty()) {
            richProviders.map { it.name }
        } else {
            try {
                watchProvidersJson.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotEmpty() }
            } catch (_: Exception) {
                emptyList()
            }
        }
        val itemType = try {
            com.example.data.model.SavedItemType.valueOf(type)
        } catch (_: Exception) {
            com.example.data.model.SavedItemType.TEXT
        }
        val isMedia = itemType == com.example.data.model.SavedItemType.IMAGE ||
                itemType == com.example.data.model.SavedItemType.VIDEO ||
                itemType == com.example.data.model.SavedItemType.AUDIO
        return com.example.data.model.SavedItem(
            id = id,
            type = itemType,
            title = title,
            content = content,
            timestamp = timestamp,
            folders = foldersList,
            extractedText = extractedText,
            thumbnailPath = thumbnailPath,
            orderIndex = orderIndex,
            isSynced = isSynced,
            linkTitle = linkTitle,
            linkDescription = linkDescription,
            linkImage = linkImage,
            isBackedUp = isBackedUp,
            sizeBytes = sizeBytes,
            isPendingBackup = isPendingBackup,
            isUnavailable = if (isMedia) isUnavailable else false,
            mediaType = mediaType,
            watchStatus = watchStatus,
            genres = genresList,
            watchProviders = watchProvidersList,
            richWatchProviders = richProviders,
            trailerUrl = trailerUrl,
            backdropUrl = backdropUrl,
            releaseYear = releaseYear,
            rating = rating,
            runtime = runtime,
            tagline = tagline,
            budget = budget,
            revenue = revenue,
            status = status,
            productionCompanies = try {
                productionCompaniesJson.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotEmpty() }
            } catch (_: Exception) { emptyList() },
            isArchived = isArchived,
            updatedAt = updatedAt,
            isDeleted = isDeleted,
            traktId = traktId,
            tmdbId = tmdbId,
            imdbId = imdbId,
            userRating = userRating,
            lastWatchedAt = lastWatchedAt,
            showProgress = com.example.data.model.TvShowProgress.fromJson(showProgressJson)
        )
    }

    companion object {
        fun fromDomainModel(item: com.example.data.model.SavedItem): SavedItemEntity {
            return SavedItemEntity(
                id = item.id,
                type = item.type.name,
                title = item.title,
                content = item.content,
                timestamp = item.timestamp,
                foldersJson = "[${item.folders.joinToString(",") { "\"$it\"" }}]",
                extractedText = item.extractedText,
                thumbnailPath = item.thumbnailPath,
                orderIndex = item.orderIndex,
                isSynced = item.isSynced,
                linkTitle = item.linkTitle,
                linkDescription = item.linkDescription,
                linkImage = item.linkImage,
                isBackedUp = item.isBackedUp,
                sizeBytes = item.sizeBytes,
                isPendingBackup = item.isPendingBackup,
                isUnavailable = item.isUnavailable,
                mediaType = item.mediaType,
                watchStatus = item.watchStatus,
                genresJson = "[${item.genres.joinToString(",") { "\"$it\"" }}]",
                watchProvidersJson = if (item.richWatchProviders.isNotEmpty()) {
                    com.example.data.model.MediaWatchProvider.listToJson(item.richWatchProviders)
                } else {
                    "[${item.watchProviders.joinToString(",") { "\"$it\"" }}]"
                },
                trailerUrl = item.trailerUrl,
                backdropUrl = item.backdropUrl,
                releaseYear = item.releaseYear,
                rating = item.rating,
                runtime = item.runtime,
                tagline = item.tagline,
                budget = item.budget,
                revenue = item.revenue,
                status = item.status,
                productionCompaniesJson = "[${item.productionCompanies.joinToString(",") { "\"$it\"" }}]",
                isArchived = item.isArchived,
                updatedAt = item.updatedAt,
                isDeleted = item.isDeleted,
                traktId = item.traktId,
                tmdbId = item.tmdbId,
                imdbId = item.imdbId,
                userRating = item.userRating,
                lastWatchedAt = item.lastWatchedAt,
                showProgressJson = item.showProgress?.toJson()
            )
        }
    }
}

fun com.example.data.model.SavedItem.toEntity(): SavedItemEntity = SavedItemEntity.fromDomainModel(this)

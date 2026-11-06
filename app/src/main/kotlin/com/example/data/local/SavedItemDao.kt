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

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.notes.NoteSizePolicy

@Dao
interface SavedItemDao {
    @Query("UPDATE saved_items SET title = CASE WHEN title = :oldTitle THEN COALESCE(:pageTitle, title) ELSE title END, linkTitle = COALESCE(:pageTitle, linkTitle), linkDescription = :description, linkImage = COALESCE(:image, linkImage) WHERE id = :id AND content = :url AND isDeleted = 0 AND linkDescription IS :oldDescription")
    suspend fun applyLinkPreview(id: String, url: String, oldTitle: String, oldDescription: String?, pageTitle: String?, description: String?, image: String?): Int

    @Query("SELECT isDeleted FROM saved_items WHERE id = :id")
    suspend fun isItemDeleted(id: String): Boolean?
    @Query("SELECT id FROM saved_items WHERE isDeleted = 1")
    suspend fun deletedIds(): List<String>
    @Query("UPDATE saved_items SET isDeleted = :deleted, updatedAt = :now WHERE id = :id")
    suspend fun setDeleted(id: String, deleted: Boolean, now: Long): Int
    @Query("DELETE FROM saved_items WHERE id = :id")
    suspend fun deleteById(id: String)
    @Query("SELECT content AS path, thumbnailPath FROM saved_items WHERE id != :excludedId AND upper(type) IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA')")
    suspend fun otherMediaPaths(excludedId: String): List<OwnedMediaPaths>
    @Query("SELECT id, content AS path, thumbnailPath, isDeleted, type FROM saved_items WHERE upper(type) IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA')")
    suspend fun mediaStorageItems(): List<MediaStorageItem>
    @Query("SELECT id FROM saved_items WHERE upper(type) = 'MEDIA' AND isDeleted = 0")
    suspend fun activeMediaIds(): List<String>
    /** Scalar metadata only: never return a legacy body in a Room row during startup. */
    @Query("SELECT EXISTS(SELECT 1 FROM saved_items WHERE upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') AND (bodyLocation != 'FILE' OR formatVersion < 1 OR contentHash IS NULL OR contentHash = '' OR bodyMissing = 1))")
    fun hasNotesNeedingBodyMigration(): Boolean

    @Query("UPDATE saved_items SET content = :content, thumbnailPath = :thumbnail WHERE id = :id AND isDeleted = 0")
    suspend fun updateMediaPaths(id: String, content: String, thumbnail: String?): Int

    @Query("UPDATE saved_items SET title = :title, linkDescription = :linkDescription, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitleAndDescription(id: String, title: String, linkDescription: String?, updatedAt: Long): Int
    @Query("SELECT id, COALESCE(NULLIF(substr(title, 1, 160), ''), 'Untitled note') AS title, CASE WHEN bodyLocation = 'FILE' THEN byteLength ELSE length(CAST(content AS BLOB)) END AS byteLength FROM saved_items WHERE isDeleted = 0 AND upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') ORDER BY timestamp DESC")
    fun observeRecoveryNotes(): Flow<List<com.example.notes.RecoveryNote>>

    /** Byte slices preserve NULs and UTF-8; the caller must stay off the main thread. */
    @Query("SELECT coalesce(substr(CAST(content AS BLOB), :start, max(0, min(:count, ${NoteSizePolicy.IO_CHUNK_BYTES}))), X'') FROM saved_items WHERE id = :id")
    fun readRecoveryBytes(id: String, start: Long, count: Int): ByteArray?

    @Query("SELECT id, type, title, CASE WHEN upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') THEN CASE WHEN bodyLocation = 'FILE' THEN preview ELSE substr(content, 1, 200) END ELSE content END AS content, timestamp, foldersJson, extractedText, thumbnailPath, orderIndex, isSynced, linkTitle, linkDescription, linkImage, isBackedUp, sizeBytes, isPendingBackup, isUnavailable, mediaType, watchStatus, genresJson, watchProvidersJson, trailerUrl, backdropUrl, releaseYear, rating, runtime, tagline, budget, revenue, status, productionCompaniesJson, isArchived, updatedAt, isDeleted, traktId, tmdbId, imdbId, userRating, lastWatchedAt, showProgressJson, bodyLocation, contentHash, byteLength, formatVersion, preview, bodyMissing FROM saved_items WHERE isDeleted = 0 ORDER BY orderIndex DESC, timestamp DESC")
    fun getAllItemsFlow(): Flow<List<SavedItemEntity>>

    @Query("SELECT id, type, title, CASE WHEN upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') THEN CASE WHEN bodyLocation = 'FILE' THEN preview ELSE substr(content, 1, 200) END ELSE content END AS content, timestamp, foldersJson, extractedText, thumbnailPath, orderIndex, isSynced, linkTitle, linkDescription, linkImage, isBackedUp, sizeBytes, isPendingBackup, isUnavailable, mediaType, watchStatus, genresJson, watchProvidersJson, trailerUrl, backdropUrl, releaseYear, rating, runtime, tagline, budget, revenue, status, productionCompaniesJson, isArchived, updatedAt, isDeleted, traktId, tmdbId, imdbId, userRating, lastWatchedAt, showProgressJson, bodyLocation, contentHash, byteLength, formatVersion, preview, bodyMissing FROM saved_items WHERE isDeleted = 0 ORDER BY timestamp DESC LIMIT ${NoteSizePolicy.DOCK_RECENT_ITEMS}")
    fun getRecentDockItemsFlow(): Flow<List<SavedItemEntity>>

    @Query("SELECT id, type, title, CASE WHEN upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') THEN CASE WHEN bodyLocation = 'FILE' THEN preview ELSE substr(content, 1, 200) END ELSE content END AS content, timestamp, foldersJson, extractedText, thumbnailPath, orderIndex, isSynced, linkTitle, linkDescription, linkImage, isBackedUp, sizeBytes, isPendingBackup, isUnavailable, mediaType, watchStatus, genresJson, watchProvidersJson, trailerUrl, backdropUrl, releaseYear, rating, runtime, tagline, budget, revenue, status, productionCompaniesJson, isArchived, updatedAt, isDeleted, traktId, tmdbId, imdbId, userRating, lastWatchedAt, showProgressJson, bodyLocation, contentHash, byteLength, formatVersion, preview, bodyMissing FROM saved_items WHERE id = :id")
    suspend fun getItemById(id: String): SavedItemEntity?

    @Query("SELECT id, bodyLocation, contentHash, CASE WHEN bodyLocation = 'FILE' THEN byteLength ELSE length(CAST(content AS BLOB)) END AS byteLength, preview, formatVersion, updatedAt FROM saved_items WHERE id = :id")
    fun getNoteBodyMetadata(id: String): com.example.notes.NoteBodyMetadata?

    @Query("SELECT id, bodyLocation, contentHash, CASE WHEN bodyLocation = 'FILE' THEN byteLength ELSE length(CAST(content AS BLOB)) END AS byteLength, preview, formatVersion, updatedAt FROM saved_items WHERE upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') ORDER BY timestamp")
    fun allNoteBodyMetadata(): List<com.example.notes.NoteBodyMetadata>

    @Query("UPDATE saved_items SET content = '', bodyLocation = 'FILE', contentHash = :hash, byteLength = :bytes, sizeBytes = :bytes, preview = :preview, formatVersion = 1, bodyMissing = 0, updatedAt = :changedAt WHERE id = :id AND updatedAt = :expectedUpdatedAt")
    fun commitFileBody(id: String, hash: String, bytes: Long, preview: String, expectedUpdatedAt: Long, changedAt: Long = expectedUpdatedAt): Int

    @Query("UPDATE saved_items SET bodyMissing = :missing WHERE id = :id AND bodyMissing != :missing")
    fun markBodyMissing(id: String, missing: Boolean)

    @Query("DELETE FROM note_search WHERE noteId = :id")
    suspend fun deleteNoteSearch(id: String)
    @Query("SELECT COUNT(*) FROM note_search WHERE noteId = :id")
    suspend fun noteSearchCount(id: String): Int

    @Insert
    suspend fun insertNoteSearch(entry: NoteSearchEntity)

    @Query("UPDATE note_search SET title = :title WHERE noteId = :id")
    suspend fun updateNoteSearchTitle(id: String, title: String)

    @Query("SELECT id, type, title, CASE WHEN upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') THEN CASE WHEN bodyLocation = 'FILE' THEN preview ELSE substr(content, 1, 200) END ELSE content END AS content, timestamp, foldersJson, extractedText, thumbnailPath, orderIndex, isSynced, linkTitle, linkDescription, linkImage, isBackedUp, sizeBytes, isPendingBackup, isUnavailable, mediaType, watchStatus, genresJson, watchProvidersJson, trailerUrl, backdropUrl, releaseYear, rating, runtime, tagline, budget, revenue, status, productionCompaniesJson, isArchived, updatedAt, isDeleted, traktId, tmdbId, imdbId, userRating, lastWatchedAt, showProgressJson, bodyLocation, contentHash, byteLength, formatVersion, preview, bodyMissing FROM saved_items WHERE isDeleted = 0 AND id IN (SELECT noteId FROM note_search WHERE note_search MATCH :query) ORDER BY updatedAt DESC LIMIT 200")
    suspend fun searchNoteFiles(query: String): List<SavedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SavedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<SavedItemEntity>)

    @Query("SELECT id, type, title, CASE WHEN upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') THEN CASE WHEN bodyLocation = 'FILE' THEN preview ELSE substr(content, 1, 200) END ELSE content END AS content, timestamp, foldersJson, extractedText, thumbnailPath, orderIndex, isSynced, linkTitle, linkDescription, linkImage, isBackedUp, sizeBytes, isPendingBackup, isUnavailable, mediaType, watchStatus, genresJson, watchProvidersJson, trailerUrl, backdropUrl, releaseYear, rating, runtime, tagline, budget, revenue, status, productionCompaniesJson, isArchived, updatedAt, isDeleted, traktId, tmdbId, imdbId, userRating, lastWatchedAt, showProgressJson, bodyLocation, contentHash, byteLength, formatVersion, preview, bodyMissing FROM saved_items WHERE isDeleted = 0 ORDER BY orderIndex DESC, timestamp DESC")
    suspend fun getAllItems(): List<SavedItemEntity>

    @Query("SELECT id, type, title, CASE WHEN upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') THEN CASE WHEN bodyLocation = 'FILE' THEN preview ELSE substr(content, 1, 200) END ELSE content END AS content, timestamp, foldersJson, extractedText, thumbnailPath, orderIndex, isSynced, linkTitle, linkDescription, linkImage, isBackedUp, sizeBytes, isPendingBackup, isUnavailable, mediaType, watchStatus, genresJson, watchProvidersJson, trailerUrl, backdropUrl, releaseYear, rating, runtime, tagline, budget, revenue, status, productionCompaniesJson, isArchived, updatedAt, isDeleted, traktId, tmdbId, imdbId, userRating, lastWatchedAt, showProgressJson, bodyLocation, contentHash, byteLength, formatVersion, preview, bodyMissing FROM saved_items WHERE traktId = :traktId AND isDeleted = 0 LIMIT 1")
    suspend fun getItemByTraktId(traktId: Long): SavedItemEntity?

    @Query("SELECT id, type, title, CASE WHEN upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') THEN CASE WHEN bodyLocation = 'FILE' THEN preview ELSE substr(content, 1, 200) END ELSE content END AS content, timestamp, foldersJson, extractedText, thumbnailPath, orderIndex, isSynced, linkTitle, linkDescription, linkImage, isBackedUp, sizeBytes, isPendingBackup, isUnavailable, mediaType, watchStatus, genresJson, watchProvidersJson, trailerUrl, backdropUrl, releaseYear, rating, runtime, tagline, budget, revenue, status, productionCompaniesJson, isArchived, updatedAt, isDeleted, traktId, tmdbId, imdbId, userRating, lastWatchedAt, showProgressJson, bodyLocation, contentHash, byteLength, formatVersion, preview, bodyMissing FROM saved_items WHERE tmdbId = :tmdbId AND isDeleted = 0 LIMIT 1")
    suspend fun getItemByTmdbId(tmdbId: Int): SavedItemEntity?

    @Query("SELECT id, type, title, CASE WHEN upper(type) NOT IN ('LINK', 'IMAGE', 'VIDEO', 'AUDIO', 'MEDIA') THEN CASE WHEN bodyLocation = 'FILE' THEN preview ELSE substr(content, 1, 200) END ELSE content END AS content, timestamp, foldersJson, extractedText, thumbnailPath, orderIndex, isSynced, linkTitle, linkDescription, linkImage, isBackedUp, sizeBytes, isPendingBackup, isUnavailable, mediaType, watchStatus, genresJson, watchProvidersJson, trailerUrl, backdropUrl, releaseYear, rating, runtime, tagline, budget, revenue, status, productionCompaniesJson, isArchived, updatedAt, isDeleted, traktId, tmdbId, imdbId, userRating, lastWatchedAt, showProgressJson, bodyLocation, contentHash, byteLength, formatVersion, preview, bodyMissing FROM saved_items WHERE traktId IS NOT NULL AND isDeleted = 0")
    suspend fun getAllTraktItems(): List<SavedItemEntity>
}

data class OwnedMediaPaths(val path: String, val thumbnailPath: String?)
data class MediaStorageItem(val id: String, val path: String, val thumbnailPath: String?, val isDeleted: Boolean, val type: String = "OTHER")

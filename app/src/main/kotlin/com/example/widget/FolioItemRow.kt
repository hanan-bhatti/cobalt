package com.example.widget

import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import org.json.JSONArray

/** Bounded projection: short stored previews and media pointers, never full note bodies or image bytes. */
data class FolioItemRow(val id: String, val type: String, val title: String, val timestamp: Long,
    val updatedAt: Long, val foldersJson: String, val linkTitle: String?,
    val excerpt: String = "", val mediaPath: String = "", val thumbnailPath: String? = null,
    val linkImage: String? = null, val linkDescription: String? = null) {
    fun toItem(): SavedItem {
        val folders = runCatching {
            val json = JSONArray(foldersJson)
            (0 until json.length()).map { json.getString(it) }
        }.getOrElse { foldersJson.removeSurrounding("[", "]").split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() } }
        return SavedItem(id = id, type = runCatching { SavedItemType.valueOf(type.uppercase()) }.getOrDefault(SavedItemType.TEXT),
            title = title, content = if (mediaPath.isNotBlank()) mediaPath else excerpt, thumbnailPath = thumbnailPath, linkImage = linkImage, linkDescription = linkDescription, timestamp = timestamp, updatedAt = updatedAt, folders = folders, linkTitle = linkTitle)
    }
}

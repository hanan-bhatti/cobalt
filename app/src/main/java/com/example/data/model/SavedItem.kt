package com.example.data.model

import java.util.UUID

enum class SavedItemType(val displayName: String) {
    LINK("Links"),
    IMAGE("Images"),
    VIDEO("Videos"),
    CODE("Code"),
    TEXT("Text"),
    AUDIO("Audio")
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
    val linkImage: String? = null
)

fun SavedItem.getBestImagePath(): Any {
    val thumb = this.thumbnailPath
    if (!thumb.isNullOrBlank()) {
        if (thumb.startsWith("http://") || thumb.startsWith("https://") || thumb.startsWith("content://")) {
            return thumb
        }
        val file = java.io.File(thumb)
        if (file.exists()) {
            return file
        }
    }
    val content = this.content
    if (content.startsWith("http://") || content.startsWith("https://") || content.startsWith("content://")) {
        return content
    }
    val contentFile = java.io.File(content)
    if (contentFile.exists()) {
        return contentFile
    }
    return content
}

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
    val linkImage: String? = null,
    val isBackedUp: Boolean = false,
    val sizeBytes: Long = 0,
    val isPendingBackup: Boolean = false
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

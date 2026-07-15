package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_items")
data class SavedItemEntity(
    @PrimaryKey val id: String,
    val type: String,          // "Link", "Image", "Video", "Code", "Text"
    val title: String,
    val content: String,        // text, URL, or local URI
    val timestamp: Long,
    val foldersJson: String,    // custom folders serialized as JSON array of strings
    val extractedText: String?, // OCR result
    val thumbnailPath: String?, // local file path or cache URI
    val orderIndex: Double = timestamp.toDouble(),
    val isSynced: Boolean = false,
    val linkTitle: String? = null,
    val linkDescription: String? = null,
    val linkImage: String? = null,
    val isBackedUp: Boolean = false,
    val sizeBytes: Long = 0,
    val isPendingBackup: Boolean = false
)

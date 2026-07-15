package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_folders")
data class CustomFolderEntity(
    @PrimaryKey val name: String,
    val colorHex: String? = null,
    val iconName: String? = null,
    val isPinned: Boolean = false,
    val isSynced: Boolean = false
)

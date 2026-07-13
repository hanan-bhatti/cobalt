package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_folders")
data class CustomFolderEntity(
    @PrimaryKey val name: String,
    val isSynced: Boolean = false
)

package com.example.sync

import com.example.data.model.SavedItemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BackupSyncManager {
    
    data class SyncProgress(
        val isSyncing: Boolean = false,
        val currentItem: Int = 0,
        val totalItems: Int = 0,
        val progressBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val currentCategory: SavedItemType? = null
    )

    private val _syncState = MutableStateFlow(SyncProgress())
    val syncState: StateFlow<SyncProgress> = _syncState.asStateFlow()

    fun updateProgress(progress: SyncProgress) {
        _syncState.value = progress
    }
}

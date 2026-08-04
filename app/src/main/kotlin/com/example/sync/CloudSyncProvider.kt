package com.example.sync

import com.example.data.local.CustomFolderEntity
import com.example.data.model.DeviceSession
import com.example.data.model.SavedItem

interface CloudSyncProvider {
    val isAvailable: Boolean

    suspend fun syncItem(item: SavedItem, userId: String): Boolean
    suspend fun syncItemWithMedia(item: SavedItem, userId: String, mediaUrl: String): Boolean
    suspend fun tombstoneItem(itemId: String, userId: String): Boolean
    suspend fun deleteItemRemote(itemId: String, userId: String): Boolean
    suspend fun syncFolder(folder: CustomFolderEntity, userId: String): Boolean
    suspend fun deleteFolderRemote(folderName: String, userId: String): Boolean
    suspend fun syncUnsyncedItems(items: List<SavedItem>, userId: String): Boolean
    suspend fun restoreUserDataFromCloud(userId: String): Result<Pair<List<CustomFolderEntity>, List<SavedItem>>>
    suspend fun updateDeviceSession(session: DeviceSession, userId: String): Boolean
    suspend fun getAllDeviceSessions(userId: String): List<DeviceSession>
}

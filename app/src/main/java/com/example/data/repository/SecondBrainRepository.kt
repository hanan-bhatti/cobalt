package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.CustomFolderEntity
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.remote.Content
import com.example.data.remote.GenerateContentRequest
import com.example.data.remote.InlineData
import com.example.data.remote.Part
import com.example.data.remote.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class SecondBrainRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val savedItemDao = db.savedItemDao()
    private val customFolderDao = db.customFolderDao()

    // Safe Firebase Initializations (will be null if firebase services are missing google-services.json or fail)
    val firebaseAuth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.w("SecondBrainRepo", "Firebase Auth is unavailable in this environment: ${e.message}")
        null
    }

    val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w("SecondBrainRepo", "Firebase Firestore is unavailable in this environment: ${e.message}")
        null
    }

    val storage: FirebaseStorage? = try {
        FirebaseStorage.getInstance()
    } catch (e: Exception) {
        Log.w("SecondBrainRepo", "Firebase Storage is unavailable in this environment: ${e.message}")
        null
    }

    
    suspend fun extractTextFromAudio(
        base64Audio: String,
        apiKey: String,
        model: String
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Key Missing. Enter your key in the AI Studio Secrets panel or the Profile page."
        }

        val promptText = "Transcribe this audio memo and format it as clear markdown notes. Give it a nice title as an H1, and format the rest appropriately."
        val request = com.example.data.remote.GenerateContentRequest(
            contents = listOf(
                com.example.data.remote.Content(
                    parts = listOf(
                        com.example.data.remote.Part(text = promptText),
                        com.example.data.remote.Part(inlineData = com.example.data.remote.InlineData(mimeType = "audio/mp4", data = base64Audio))
                    )
                )
            )
        )

        try {
            val response = com.example.data.remote.RetrofitClient.geminiService.generateContent("models/$model", apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Gemini Audio call failed: ${e.message}")
            "Error: ${e.localizedMessage ?: "Transcription failed"}"
        }
    }

    suspend fun formatSpeechText(
        speechText: String,
        apiKey: String,
        model: String
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "API Key Missing. Enter your key in the AI Studio Secrets panel or the Profile page."
        }

        val promptText = """
            You are an expert voice memo scribe for a Second Brain app.
            Format the following spoken thought or speech memo into a beautifully organized, professional, and clear Markdown document.
            Requirements:
            1. Provide a beautiful, highly descriptive H1 title at the very top (do not use generic titles like "Voice Memo").
            2. Add a short, punchy summary of the core message.
            3. Use elegant, structured bullet points, sections, or bold key terms with appropriate emojis to organize action items, key concepts, or reminders.
            4. Make sure it looks clean, uses generous whitespace, and is easy to scan.
            
            Input Speech/Memo:
            $speechText
        """.trimIndent()

        val request = com.example.data.remote.GenerateContentRequest(
            contents = listOf(
                com.example.data.remote.Content(
                    parts = listOf(
                        com.example.data.remote.Part(text = promptText)
                    )
                )
            )
        )

        try {
            val response = com.example.data.remote.RetrofitClient.geminiService.generateContent("models/$model", apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Gemini Speech format call failed: ${e.message}")
            "Error: ${e.localizedMessage ?: "Formatting failed"}"
        }
    }

    // ----------------------------------------------------
    // LOCAL ROOM DATABASE FLOWS
    // ----------------------------------------------------

    fun getAllItemsFlow(): Flow<List<SavedItem>> {
        return savedItemDao.getAllItemsFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getAllItems(): List<SavedItem> = withContext(Dispatchers.IO) {
        try {
            savedItemDao.getAllItems().map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAllFoldersFlow(): Flow<List<String>> {
        return customFolderDao.getAllFoldersFlow()
            .map { list -> list.map { it.name } }
            .flowOn(Dispatchers.IO)
    }

    fun getAllFolderEntitiesFlow(): Flow<List<CustomFolderEntity>> {
        return customFolderDao.getAllFoldersFlow()
            .flowOn(Dispatchers.IO)
    }

    // ----------------------------------------------------
    // DOMAIN <-> ENTITY MAPPING HELPERS
    // ----------------------------------------------------

    private fun SavedItemEntity.toDomain(): SavedItem {
        val foldersList = try {
            foldersJson.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
        return SavedItem(
            id = id,
            type = try { SavedItemType.valueOf(type) } catch (e: Exception) { SavedItemType.TEXT },
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
            isPendingBackup = isPendingBackup
        )
    }

    private fun SavedItem.toEntity(): SavedItemEntity {
        val foldersJsonStr = "[" + folders.joinToString(",") { "\"$it\"" } + "]"
        return SavedItemEntity(
            id = id,
            type = type.name,
            title = title,
            content = content,
            timestamp = timestamp,
            foldersJson = foldersJsonStr,
            extractedText = extractedText,
            thumbnailPath = thumbnailPath,
            orderIndex = orderIndex,
            isSynced = isSynced,
            linkTitle = linkTitle,
            linkDescription = linkDescription,
            linkImage = linkImage,
            isBackedUp = isBackedUp,
            sizeBytes = sizeBytes,
            isPendingBackup = isPendingBackup
        )
    }

    // ----------------------------------------------------
    // SAVING AND DELETING OPERATIONS (WITH CLOUD SYNC)
    // ----------------------------------------------------

    // Helper to read local cache file bytes for syncing offline-saved media
    fun readFileBytes(filePath: String): ByteArray? {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Failed to read file bytes from path $filePath: ${e.message}")
            null
        }
    }

    private val prefs = context.getSharedPreferences("second_brain_prefs", Context.MODE_PRIVATE)

    suspend fun updateItems(items: List<SavedItem>) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        items.forEach {
            savedItemDao.insertItem(it.toEntity())
        }
    }

    suspend fun saveItem(
        item: SavedItem,
        mediaBytes: ByteArray? = null,
        onProgress: (Float) -> Unit = {}
    ): SavedItem = withContext(Dispatchers.IO) {
        var finalItem = item.copy(isSynced = false)

        // 1. Save locally first to keep the interface fast & offline-ready
        savedItemDao.insertItem(finalItem.toEntity())
        onProgress(0.1f)

        // 2. Perform background sync if signed in
        val currentUser = firebaseAuth?.currentUser
        if (currentUser == null && prefs.getString("simulated_email", null) != null) {
            finalItem = finalItem.copy(isSynced = true)
            savedItemDao.insertItem(finalItem.toEntity())
            onProgress(1.0f)
        }

        if (currentUser != null) {
            try {
                var actualBytes = mediaBytes
                // If mediaBytes are not provided (e.g., queued/offline item), read them from the local cache file
                if (actualBytes == null && (item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO || item.type == SavedItemType.AUDIO)) {
                    val localPath = item.thumbnailPath ?: item.content
                    if (!localPath.startsWith("http://") && !localPath.startsWith("https://")) {
                        actualBytes = readFileBytes(localPath)
                    }
                }

                // Upload to Firebase Storage if we have bytes and storage is available
                if (storage != null && actualBytes != null) {
                    val fileExtension = when (item.type) {
                        SavedItemType.VIDEO -> "mp4"
                        SavedItemType.AUDIO -> "mp4"
                        else -> "jpg"
                    }
                    val storageRef = storage.reference.child("users/${currentUser.uid}/media/${item.id}.$fileExtension")
                    
                    val uploadTask = storageRef.putBytes(actualBytes)
                    uploadTask.addOnProgressListener { taskSnapshot ->
                        val progress = if (taskSnapshot.totalByteCount > 0) {
                            taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount
                        } else {
                            0f
                        }
                        // Scale progress to 10% - 85% range
                        onProgress(0.1f + progress * 0.75f)
                    }
                    val snapshot = uploadTask.await()
                    val downloadUrl = (snapshot.metadata?.reference?.downloadUrl ?: throw Exception("No reference URL")).await()
                    
                    // Update item thumbnailPath (for audio) or content (for image/video) with the cloud storage URL
                    finalItem = if (item.type == SavedItemType.AUDIO) {
                        finalItem.copy(thumbnailPath = downloadUrl.toString())
                    } else {
                        finalItem.copy(content = downloadUrl.toString())
                    }
                    // Save locally again with the new remote URL
                    savedItemDao.insertItem(finalItem.toEntity())
                    onProgress(0.9f)
                } else {
                    onProgress(0.5f)
                }

                // Sync metadata to Firestore
                if (firestore != null) {
                    val itemMap = mapOf(
                        "id" to finalItem.id,
                        "type" to finalItem.type.name,
                        "title" to finalItem.title,
                        "content" to finalItem.content,
                        "timestamp" to finalItem.timestamp,
                        "folders" to finalItem.folders,
                        "extractedText" to finalItem.extractedText,
                        "thumbnailPath" to finalItem.thumbnailPath,
                        "isSynced" to true,
                        "linkTitle" to finalItem.linkTitle,
                        "linkDescription" to finalItem.linkDescription,
                        "linkImage" to finalItem.linkImage
                    )
                    firestore.collection("users").document(currentUser.uid)
                        .collection("items").document(finalItem.id)
                        .set(itemMap).await()
                    
                    // Mark locally as synced
                    finalItem = finalItem.copy(isSynced = true)
                    savedItemDao.insertItem(finalItem.toEntity())
                }
                onProgress(1.0f)
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Background Firebase sync failed for item ${item.id}: ${e.message}")
            }
        }

        com.example.widget.WidgetUpdater.update(context)
        return@withContext finalItem
    }

    // Background sync function for uploading all unsynced items

    // Specific targeted sync/backup for selected items
    suspend fun backupSelectedItems(itemIds: List<String>) = withContext(Dispatchers.IO) {
        val currentUser = firebaseAuth?.currentUser ?: return@withContext
        val items = savedItemDao.getAllItems()
        val toBackup = items.filter { itemIds.contains(it.id) && !it.isSynced }
        if (toBackup.isEmpty()) return@withContext

        Log.d("SecondBrainRepo", "Starting backup of ${toBackup.size} selected items.")
        for (entity in toBackup) {
            try {
                var domainItem = entity.toDomain()
                var finalItem = domainItem

                if ((domainItem.type == SavedItemType.IMAGE || domainItem.type == SavedItemType.VIDEO) &&
                    !domainItem.content.startsWith("http://") && !domainItem.content.startsWith("https://")
                ) {
                    val localPath = domainItem.thumbnailPath ?: domainItem.content
                    val bytes = readFileBytes(localPath)
                    if (bytes != null && storage != null) {
                        val fileExtension = if (domainItem.type == SavedItemType.VIDEO) "mp4" else "jpg"
                        val storageRef = storage.reference.child("users/${currentUser.uid}/media/${domainItem.id}.$fileExtension")
                        val uploadTask = storageRef.putBytes(bytes)
                        val snapshot = uploadTask.await()
                        val downloadUrl = (snapshot.metadata?.reference?.downloadUrl ?: throw Exception("No reference URL")).await()
                        finalItem = finalItem.copy(
                            content = downloadUrl.toString(),
                            thumbnailPath = downloadUrl.toString()
                        )
                        savedItemDao.insertItem(finalItem.toEntity())
                    }
                }

                if (firestore != null) {
                    val itemMap = mapOf(
                        "id" to finalItem.id,
                        "type" to finalItem.type.name,
                        "title" to finalItem.title,
                        "content" to finalItem.content,
                        "timestamp" to finalItem.timestamp,
                        "folders" to finalItem.folders,
                        "extractedText" to finalItem.extractedText,
                        "thumbnailPath" to finalItem.thumbnailPath,
                        "isSynced" to true,
                        "linkTitle" to finalItem.linkTitle,
                        "linkDescription" to finalItem.linkDescription,
                        "linkImage" to finalItem.linkImage
                    )
                    firestore.collection("users").document(currentUser.uid)
                        .collection("items").document(finalItem.id)
                        .set(itemMap).await()

                    finalItem = finalItem.copy(isSynced = true)
                    savedItemDao.insertItem(finalItem.toEntity())
                }
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Background Firebase backup failed for item ${entity.id}: ${e.message}")
            }
        }
        com.example.widget.WidgetUpdater.update(context)
    }

    suspend fun removeBackup(itemIds: List<String>) = withContext(Dispatchers.IO) {
        val currentUser = firebaseAuth?.currentUser ?: return@withContext
        for (id in itemIds) {
            try {
                // Remove from Firestore
                firestore?.collection("users")?.document(currentUser.uid)
                    ?.collection("items")?.document(id)?.delete()?.await()

                // Note: Deleting media from storage could be added here, but leaving it alone or tracking it
                // is fine for standard removal of metadata. We can delete it if it's an image.
                val item = savedItemDao.getItemById(id)?.toDomain()
                if (item != null) {
                    val newDomain = item.copy(isSynced = false)
                    savedItemDao.insertItem(newDomain.toEntity())
                }
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to remove backup for item $id: ${e.message}")
            }
        }
    }

    suspend fun syncUnsyncedItems() = withContext(Dispatchers.IO) {
        val currentUser = firebaseAuth?.currentUser ?: return@withContext
        val items = savedItemDao.getAllItems()
        val unsynced = items.filter { !it.isSynced }
        if (unsynced.isEmpty()) return@withContext

        Log.d("SecondBrainRepo", "Starting sync of ${unsynced.size} unsynced items.")
        for (entity in unsynced) {
            try {
                var domainItem = entity.toDomain()
                var finalItem = domainItem

                // Upload media bytes if it's an image/video and not already uploaded
                if ((domainItem.type == SavedItemType.IMAGE || domainItem.type == SavedItemType.VIDEO) &&
                    !domainItem.content.startsWith("http://") && !domainItem.content.startsWith("https://")
                ) {
                    val localPath = domainItem.thumbnailPath ?: domainItem.content
                    val bytes = readFileBytes(localPath)
                    if (bytes != null && storage != null) {
                        val fileExtension = if (domainItem.type == SavedItemType.VIDEO) "mp4" else "jpg"
                        val storageRef = storage.reference.child("users/${currentUser.uid}/media/${domainItem.id}.$fileExtension")
                        val uploadTask = storageRef.putBytes(bytes)
                        val snapshot = uploadTask.await()
                        val downloadUrl = (snapshot.metadata?.reference?.downloadUrl ?: throw Exception("No reference URL")).await()
                        finalItem = finalItem.copy(
                            content = downloadUrl.toString(),
                            thumbnailPath = downloadUrl.toString()
                        )
                        savedItemDao.insertItem(finalItem.toEntity())
                    }
                }

                // Upload metadata to Firestore
                if (firestore != null) {
                    val itemMap = mapOf(
                        "id" to finalItem.id,
                        "type" to finalItem.type.name,
                        "title" to finalItem.title,
                        "content" to finalItem.content,
                        "timestamp" to finalItem.timestamp,
                        "folders" to finalItem.folders,
                        "extractedText" to finalItem.extractedText,
                        "thumbnailPath" to finalItem.thumbnailPath,
                        "isSynced" to true,
                        "linkTitle" to finalItem.linkTitle,
                        "linkDescription" to finalItem.linkDescription,
                        "linkImage" to finalItem.linkImage
                    )
                    firestore.collection("users").document(currentUser.uid)
                        .collection("items").document(finalItem.id)
                        .set(itemMap).await()

                    // Update locally as synced
                    finalItem = finalItem.copy(isSynced = true)
                    savedItemDao.insertItem(finalItem.toEntity())
                    Log.d("SecondBrainRepo", "Successfully synced item: ${finalItem.id}")
                }
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to sync item ${entity.id}: ${e.message}")
            }
        }

        val folders = customFolderDao.getAllFolders()
        val unsyncedFolders = folders.filter { !it.isSynced }
        for (folder in unsyncedFolders) {
            try {
                if (firestore != null) {
                    val folderMap = mapOf(
                        "name" to folder.name,
                        "colorHex" to folder.colorHex,
                        "iconName" to folder.iconName,
                        "isPinned" to folder.isPinned,
                        "isSynced" to true
                    )
                    firestore.collection("users").document(currentUser.uid)
                        .collection("folders").document(folder.name)
                        .set(folderMap).await()
                    
                    customFolderDao.insertFolder(folder.copy(isSynced = true))
                    Log.d("SecondBrainRepo", "Successfully synced folder: ${folder.name}")
                }
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to sync folder ${folder.name}: ${e.message}")
            }
        }
    }

    suspend fun deleteItem(item: SavedItem) = withContext(Dispatchers.IO) {
        // Delete locally
        savedItemDao.deleteItem(item.toEntity())

        // Delete from Firestore
        val currentUser = firebaseAuth?.currentUser
        if (currentUser != null && firestore != null) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("items").document(item.id)
                    .delete()
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to delete item from Firestore: ${e.message}")
            }
        }

        // Delete from Storage if it's a cloud storage URL
        if (currentUser != null && storage != null && item.content.startsWith("https://firebasestorage")) {
            try {
                val storageRef = storage.getReferenceFromUrl(item.content)
                storageRef.delete()
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to delete file from Storage: ${e.message}")
            }
        }
        com.example.widget.WidgetUpdater.update(context)
    }

    suspend fun addCustomFolder(
        folderName: String,
        colorHex: String? = null,
        iconName: String? = null,
        isPinned: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val newFolder = CustomFolderEntity(
            name = folderName,
            colorHex = colorHex,
            iconName = iconName,
            isPinned = isPinned,
            isSynced = false
        )
        customFolderDao.insertFolder(newFolder)

        val currentUser = firebaseAuth?.currentUser
        if (currentUser != null && firestore != null) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("folders").document(folderName)
                    .set(mapOf(
                        "name" to folderName,
                        "colorHex" to colorHex,
                        "iconName" to iconName,
                        "isPinned" to isPinned,
                        "isSynced" to true
                    ))
                customFolderDao.insertFolder(newFolder.copy(isSynced = true))
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to sync folder to Firestore: ${e.message}")
            }
        }
    }

    suspend fun deleteCustomFolder(folderName: String) = withContext(Dispatchers.IO) {
        customFolderDao.deleteFolder(CustomFolderEntity(folderName))

        val currentUser = firebaseAuth?.currentUser
        if (currentUser != null && firestore != null) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("folders").document(folderName)
                    .delete()
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to delete folder from Firestore: ${e.message}")
            }
        }
    }

    suspend fun updateCustomFolder(folder: CustomFolderEntity) = withContext(Dispatchers.IO) {
        val updatedFolder = folder.copy(isSynced = false)
        customFolderDao.insertFolder(updatedFolder)

        val currentUser = firebaseAuth?.currentUser
        if (currentUser != null && firestore != null) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("folders").document(updatedFolder.name)
                    .set(mapOf(
                        "name" to updatedFolder.name,
                        "colorHex" to updatedFolder.colorHex,
                        "iconName" to updatedFolder.iconName,
                        "isPinned" to updatedFolder.isPinned,
                        "isSynced" to true
                    ))
                customFolderDao.insertFolder(updatedFolder.copy(isSynced = true))
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to sync updated folder to Firestore: ${e.message}")
            }
        }
    }

    suspend fun renameCustomFolder(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val folders = customFolderDao.getAllFolders()
        val oldFolder = folders.find { it.name == oldName } ?: CustomFolderEntity(oldName)

        // 1. Save new folder with same settings
        val newFolder = CustomFolderEntity(
            name = newName,
            colorHex = oldFolder.colorHex,
            iconName = oldFolder.iconName,
            isPinned = oldFolder.isPinned,
            isSynced = false
        )
        customFolderDao.insertFolder(newFolder)

        // 2. Fetch all saved items and update their foldersJson if they contain oldName
        val allItemsList = savedItemDao.getAllItems()
        allItemsList.forEach { entity ->
            val domain = entity.toDomain()
            if (domain.folders.contains(oldName)) {
                val updatedFolders = domain.folders.map { if (it == oldName) newName else it }
                savedItemDao.insertItem(domain.copy(folders = updatedFolders).toEntity())
            }
        }

        // 3. Delete old folder
        customFolderDao.deleteFolder(oldFolder)

        // 4. Update in Firestore if signed in
        val currentUser = firebaseAuth?.currentUser
        if (currentUser != null && firestore != null) {
            try {
                val userDocRef = firestore.collection("users").document(currentUser.uid)
                
                // Add new folder in firestore
                userDocRef.collection("folders").document(newName).set(mapOf(
                    "name" to newName,
                    "colorHex" to oldFolder.colorHex,
                    "iconName" to oldFolder.iconName,
                    "isPinned" to oldFolder.isPinned
                )).await()
                
                // Delete old folder in firestore
                userDocRef.collection("folders").document(oldName).delete().await()

                // Update items online
                allItemsList.filter { it.foldersJson.contains(oldName) }.forEach { entity ->
                    val domain = entity.toDomain()
                    val updatedFolders = domain.folders.map { if (it == oldName) newName else it }
                    val finalItem = domain.copy(folders = updatedFolders)
                    val itemMap = mapOf(
                        "id" to finalItem.id,
                        "type" to finalItem.type.name,
                        "title" to finalItem.title,
                        "content" to finalItem.content,
                        "timestamp" to finalItem.timestamp,
                        "folders" to finalItem.folders,
                        "extractedText" to finalItem.extractedText,
                        "thumbnailPath" to finalItem.thumbnailPath,
                        "isSynced" to true,
                        "linkTitle" to finalItem.linkTitle,
                        "linkDescription" to finalItem.linkDescription,
                        "linkImage" to finalItem.linkImage
                    )
                    userDocRef.collection("items").document(finalItem.id).set(itemMap).await()
                }
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to sync folder rename to Firestore: ${e.message}")
            }
        }
    }

    // ----------------------------------------------------
    // HISTORIC BACKUP RESTORATION SYNC
    // ----------------------------------------------------

    suspend fun restoreUserDataFromCloud() = withContext(Dispatchers.IO) {
        val currentUser = firebaseAuth?.currentUser ?: return@withContext
        val firestore = firestore ?: return@withContext

        try {
            // Restore folders
            val folderSnap = firestore.collection("users").document(currentUser.uid)
                .collection("folders").get().await()
            folderSnap.documents.forEach { doc ->
                val name = doc.id
                val colorHex = doc.getString("colorHex")
                val iconName = doc.getString("iconName")
                val isPinned = doc.getBoolean("isPinned") ?: false
                customFolderDao.insertFolder(CustomFolderEntity(name, colorHex, iconName, isPinned, isSynced = true))
            }

            // Restore items
            val itemSnap = firestore.collection("users").document(currentUser.uid)
                .collection("items").get().await()
            itemSnap.documents.forEach { doc ->
                val id = doc.getString("id") ?: return@forEach
                val type = doc.getString("type") ?: SavedItemType.TEXT.name
                val title = doc.getString("title") ?: ""
                val content = doc.getString("content") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                @Suppress("UNCHECKED_CAST")
                val foldersList = doc.get("folders") as? List<String> ?: emptyList()
                val extractedText = doc.getString("extractedText")
                val thumbnailPath = doc.getString("thumbnailPath")
                val linkTitle = doc.getString("linkTitle")
                val linkDescription = doc.getString("linkDescription")
                val linkImage = doc.getString("linkImage")

                val foldersJsonStr = "[" + foldersList.joinToString(",") { "\"$it\"" } + "]"
                savedItemDao.insertItem(
                    SavedItemEntity(
                        id = id,
                        type = type,
                        title = title,
                        content = content,
                        timestamp = timestamp,
                        foldersJson = foldersJsonStr,
                        extractedText = extractedText,
                        thumbnailPath = thumbnailPath,
                        isSynced = true,
                        linkTitle = linkTitle,
                        linkDescription = linkDescription,
                        linkImage = linkImage
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Failed to restore data from Firestore: ${e.message}")
        }
    }

    // ----------------------------------------------------
    // GEMINI VISION OCR (TEXT EXTRACTION)
    // ----------------------------------------------------

    suspend fun extractTextFromRegion(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        apiKey: String,
        model: String,
        sensitivity: String = "Medium"
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("SecondBrainRepo", "Gemini API key is missing or is the placeholder.")
            return@withContext "API Key Missing. Enter your key in the AI Studio Secrets panel or the Profile page."
        }

        // Crop the bitmap to the marked region
        val croppedBitmap = if (width > 0 && height > 0) {
            val safeX = x.coerceIn(0, bitmap.width - 1)
            val safeY = y.coerceIn(0, bitmap.height - 1)
            val safeWidth = width.coerceAtMost(bitmap.width - safeX)
            val safeHeight = height.coerceAtMost(bitmap.height - safeY)
            if (safeWidth > 0 && safeHeight > 0) {
                Bitmap.createBitmap(bitmap, safeX, safeY, safeWidth, safeHeight)
            } else {
                bitmap
            }
        } else {
            bitmap
        }

        // Compress and encode base64
        val outputStream = ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        // Build prompt: specifically requests OCR text extraction + return as URL if it resembles one
        val basePrompt = """
            Extract all text cleanly from this image.
            If there are any URLs or web links found in the text, extract them carefully.
            Return ONLY a valid JSON object with this exact structure (do not use markdown code blocks, just raw JSON):
            {
              "extractedText": "The clean, formatted text from the image...",
              "urls": [
                {
                  "url": "https://...",
                  "description": "Brief description of what this url is based on context"
                }
              ]
            }
        """.trimIndent()
        val sensitivityPrompt = when (sensitivity) {
            "Low" -> " Extract only the most prominent, large text. Ignore small details, noise, or blurry text."
            "High" -> " Extract all visible text perfectly, inferring missing characters or fixing typos caused by blurriness, and preserve the layout logic."
            else -> " Extract all clearly visible text."
        }
        val promptText = basePrompt + sensitivityPrompt

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = promptText),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data))
                    )
                )
            )
        )

        try {
            val response = RetrofitClient.geminiService.generateContent("models/$model", apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Gemini API call failed: ${e.message}")
            "Error: ${e.localizedMessage ?: "OCR failed"}"
        }
    }

    // Helper to save a shared image/video to a local file cache
    suspend fun saveToLocalCache(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, fileName)
        FileOutputStream(cacheFile).use { fos ->
            fos.write(bytes)
        }
        return@withContext cacheFile.absolutePath
    }

    suspend fun fetchLinkMetadata(urlString: String): LinkMetadata = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                "https://$urlString"
            } else {
                urlString
            }

            val document = org.jsoup.Jsoup.connect(cleanUrl)
                .timeout(5000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .get()

            val title = document.title().takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[property=og:title]").attr("content").takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[name=twitter:title]").attr("content").takeIf { !it.isNullOrBlank() }
                
            val description = document.select("meta[property=og:description]").attr("content").takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[name=description]").attr("content").takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[name=twitter:description]").attr("content").takeIf { !it.isNullOrBlank() }
                
            val imageUrl = document.select("meta[property=og:image]").attr("content").takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[name=twitter:image]").attr("content").takeIf { !it.isNullOrBlank() }

            LinkMetadata(title?.trim(), description?.trim(), imageUrl)
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Failed to fetch link metadata: ${e.message}")
            LinkMetadata(null, null, null)
        }
    }
}

data class LinkMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null
)

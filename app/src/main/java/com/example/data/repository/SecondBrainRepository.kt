/*
 * Second Brain - A universal capture and personal knowledge archive
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

package com.example.data.repository

import android.content.Context
import android.provider.Settings
import com.example.data.model.DeviceSession
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
import com.example.data.remote.GenerationConfig
import com.example.data.remote.ResponseSchema
import com.example.data.remote.MediaApiClient
import com.example.data.remote.MediaSearchResultItem
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
import okhttp3.OkHttpClient
import okhttp3.Request

class SecondBrainRepository(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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
        val genresList = try {
            genresJson.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
        val watchProvidersList = try {
            watchProvidersJson.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
        val itemType = try { SavedItemType.valueOf(type) } catch (e: Exception) { SavedItemType.TEXT }
        val isMedia = itemType == SavedItemType.IMAGE || itemType == SavedItemType.VIDEO || itemType == SavedItemType.AUDIO
        return SavedItem(
            id = id,
            type = itemType,
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
            isPendingBackup = isPendingBackup,
            isUnavailable = if (isMedia) isUnavailable else false,
            mediaType = mediaType,
            watchStatus = watchStatus,
            genres = genresList,
            watchProviders = watchProvidersList,
            trailerUrl = trailerUrl,
            backdropUrl = backdropUrl,
            releaseYear = releaseYear,
            rating = rating
        )
    }

    private fun SavedItem.toEntity(): SavedItemEntity {
        val foldersJsonStr = "[" + folders.joinToString(",") { "\"$it\"" } + "]"
        val genresJsonStr = "[" + genres.joinToString(",") { "\"$it\"" } + "]"
        val watchProvidersJsonStr = "[" + watchProviders.joinToString(",") { "\"$it\"" } + "]"
        val isMedia = type == SavedItemType.IMAGE || type == SavedItemType.VIDEO || type == SavedItemType.AUDIO
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
            isPendingBackup = isPendingBackup,
            isUnavailable = if (isMedia) isUnavailable else false,
            mediaType = mediaType,
            watchStatus = watchStatus,
            genresJson = genresJsonStr,
            watchProvidersJson = watchProvidersJsonStr,
            trailerUrl = trailerUrl,
            backdropUrl = backdropUrl,
            releaseYear = releaseYear,
            rating = rating
        )
    }

    suspend fun saveItemLocallyOnly(item: SavedItem) = withContext(Dispatchers.IO) {
        savedItemDao.insertItem(item.toEntity())
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

    fun getTmdbApiKey(): String {
        return prefs.getString("tmdb_api_key", "") ?: ""
    }

    fun setTmdbApiKey(key: String) {
        prefs.edit().putString("tmdb_api_key", key).apply()
    }

    suspend fun searchMedia(query: String): List<MediaSearchResultItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<MediaSearchResultItem>()
        val apiKey = getTmdbApiKey()

        if (apiKey.isNotBlank()) {
            try {
                val tmdbResponse = MediaApiClient.tmdbApiService.searchMulti(query = query, apiKey = apiKey)
                tmdbResponse.results?.forEach { item ->
                    val type = item.mediaType
                    if (type == "movie" || type == "tv") {
                        val title = if (type == "movie") (item.title ?: item.name ?: "") else (item.name ?: item.title ?: "")
                        val releaseYear = if (type == "movie") {
                            item.releaseDate?.take(4)
                        } else {
                            item.firstAirDate?.take(4)
                        }
                        val posterUrl = item.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                        val backdropUrl = item.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }

                        results.add(
                            MediaSearchResultItem(
                                id = "tmdb_${type}_${item.id}",
                                title = title,
                                mediaType = type,
                                posterUrl = posterUrl,
                                backdropUrl = backdropUrl,
                                releaseYear = releaseYear,
                                overview = item.overview,
                                genres = emptyList(),
                                watchProviders = emptyList(),
                                trailerUrl = null,
                                rating = item.voteAverage
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "TMDb searchMulti failed: ${e.message}")
            }
        }

        try {
            val jikanResponse = MediaApiClient.jikanApiService.searchAnime(query = query, limit = 10)
            jikanResponse.data?.forEach { item ->
                val title = item.titleEnglish?.takeIf { it.isNotBlank() } ?: item.title ?: ""
                val posterUrl = item.images?.jpg?.largeImageUrl ?: item.images?.jpg?.imageUrl
                val backdropUrl = item.images?.jpg?.largeImageUrl ?: item.images?.jpg?.imageUrl
                val releaseYear = item.year?.toString()
                    ?: item.aired?.prop?.from?.year?.toString()
                    ?: item.aired?.string?.take(4)
                val trailerUrl = item.trailer?.url ?: item.trailer?.embedUrl
                    ?: item.trailer?.youtubeId?.let { "https://www.youtube.com/watch?v=$it" }
                val genres = item.genres?.mapNotNull { it.name } ?: emptyList()

                results.add(
                    MediaSearchResultItem(
                        id = "jikan_anime_${item.malId}",
                        title = title,
                        mediaType = "anime",
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        releaseYear = releaseYear,
                        overview = item.synopsis,
                        genres = genres,
                        watchProviders = emptyList(),
                        trailerUrl = trailerUrl,
                        rating = item.score
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Jikan searchAnime failed: ${e.message}")
        }

        results
    }

    suspend fun enrichMediaItemDetails(item: SavedItem): SavedItem = withContext(Dispatchers.IO) {
        if (item.type != SavedItemType.MEDIA) return@withContext item

        var genres = item.genres
        var watchProviders = item.watchProviders
        var trailerUrl = item.trailerUrl
        var backdropUrl = item.backdropUrl
        var rating = item.rating

        var updated = false

        if (item.id.startsWith("tmdb_") || item.mediaType?.lowercase() == "movie" || item.mediaType?.lowercase() == "tv") {
            val tmdbId = item.id.substringAfterLast("_").toIntOrNull()
            val apiKey = getTmdbApiKey()

            if (tmdbId != null && apiKey.isNotBlank()) {
                try {
                    val isTv = item.mediaType?.lowercase() == "tv" || item.id.startsWith("tmdb_tv_")
                    val details = if (isTv) {
                        MediaApiClient.tmdbApiService.getTvDetails(tmdbId, apiKey)
                    } else {
                        MediaApiClient.tmdbApiService.getMovieDetails(tmdbId, apiKey)
                    }

                    if (rating == null && details.voteAverage != null) {
                        rating = details.voteAverage
                        updated = true
                    }

                    if (genres.isEmpty()) {
                        val fetchedGenres = details.genres?.mapNotNull { it.name } ?: emptyList()
                        if (fetchedGenres.isNotEmpty()) {
                            genres = fetchedGenres
                            updated = true
                        }
                    }

                    if (backdropUrl.isNullOrBlank()) {
                        val fetchedBackdrop = details.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                        if (!fetchedBackdrop.isNullOrBlank()) {
                            backdropUrl = fetchedBackdrop
                            updated = true
                        }
                    }

                    if (trailerUrl.isNullOrBlank()) {
                        val ytKey = details.videos?.results?.firstOrNull {
                            it.site?.equals("YouTube", ignoreCase = true) == true &&
                            (it.type?.equals("Trailer", ignoreCase = true) == true || it.type?.equals("Teaser", ignoreCase = true) == true)
                        }?.key ?: details.videos?.results?.firstOrNull { it.site?.equals("YouTube", ignoreCase = true) == true }?.key

                        if (ytKey != null) {
                            trailerUrl = "https://www.youtube.com/watch?v=$ytKey"
                            updated = true
                        }
                    }

                    if (watchProviders.isEmpty()) {
                        val providerList = mutableListOf<String>()
                        val countryMap = details.watchProviders?.results
                        val countryData = countryMap?.get("US") ?: countryMap?.values?.firstOrNull()

                        countryData?.flatrate?.forEach { p -> p.providerName?.let { providerList.add(it) } }
                        countryData?.rent?.forEach { p -> p.providerName?.let { providerList.add(it) } }
                        countryData?.buy?.forEach { p -> p.providerName?.let { providerList.add(it) } }

                        val fetchedProviders = providerList.filter { it.isNotBlank() }.distinct()
                        if (fetchedProviders.isNotEmpty()) {
                            watchProviders = fetchedProviders
                            updated = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SecondBrainRepo", "Failed to enrich TMDb media item: ${e.message}")
                }
            }
        } else if (item.id.startsWith("jikan_") || item.mediaType?.lowercase() == "anime") {
            val malId = item.id.substringAfterLast("_").toIntOrNull()
            if (malId != null && watchProviders.isEmpty()) {
                try {
                    val streamingResp = MediaApiClient.jikanApiService.getAnimeStreaming(malId)
                    val providers = streamingResp.data?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.distinct() ?: emptyList()
                    if (providers.isNotEmpty()) {
                        watchProviders = providers
                        updated = true
                    }
                } catch (e: Exception) {
                    Log.e("SecondBrainRepo", "Failed to fetch Jikan streaming info: ${e.message}")
                }
            }
        }

        if (updated) {
            val newItem = item.copy(
                genres = genres,
                watchProviders = watchProviders,
                trailerUrl = trailerUrl,
                backdropUrl = backdropUrl,
                rating = rating
            )
            savedItemDao.insertItem(newItem.toEntity())
            return@withContext newItem
        }

        return@withContext item
    }

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
        // Calculate item size dynamically
        var itemSize = 0L
        if (mediaBytes != null) {
            itemSize = mediaBytes.size.toLong()
        } else {
            val localPath = item.thumbnailPath ?: item.content
            if (localPath.startsWith("/")) {
                val file = java.io.File(localPath)
                if (file.exists()) {
                    itemSize = file.length()
                }
            } else if (item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO || item.type == SavedItemType.AUDIO) {
                // Keep existing sizeBytes for remote media items loaded from cloud
                itemSize = if (item.sizeBytes > 0L) item.sizeBytes else 0L
            } else {
                itemSize = item.content.toByteArray().size.toLong()
            }
        }

        var finalItem = item.copy(isSynced = false, sizeBytes = itemSize, timestamp = System.currentTimeMillis())

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

                    finalItem = if (item.type == SavedItemType.AUDIO) {
                        finalItem.copy(
                            thumbnailPath = downloadUrl.toString(),
                            sizeBytes = actualBytes.size.toLong(),
                            isBackedUp = true
                        )
                    } else {
                        finalItem.copy(
                            content = downloadUrl.toString(),
                            thumbnailPath = downloadUrl.toString(),
                            sizeBytes = actualBytes.size.toLong(),
                            isBackedUp = true
                        )
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
                        "orderIndex" to finalItem.orderIndex,
                        "folders" to finalItem.folders,
                        "extractedText" to finalItem.extractedText,
                        "thumbnailPath" to finalItem.thumbnailPath,
                        "isSynced" to true,
                        "linkTitle" to finalItem.linkTitle,
                        "linkDescription" to finalItem.linkDescription,
                        "linkImage" to finalItem.linkImage,
                        "sizeBytes" to finalItem.sizeBytes,
                        "isBackedUp" to finalItem.isBackedUp,
                        "mediaType" to finalItem.mediaType,
                        "watchStatus" to finalItem.watchStatus,
                        "genres" to finalItem.genres,
                        "watchProviders" to finalItem.watchProviders,
                        "trailerUrl" to finalItem.trailerUrl,
                        "backdropUrl" to finalItem.backdropUrl,
                        "releaseYear" to finalItem.releaseYear
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

    // Specific targeted sync/backup for selected items.
    // Sets isPendingBackup = true on success so this item is recognized as
    // "the user wants this backed up" — this is what lets syncUnsyncedItems()
    // tell the difference between "never backed up yet" and "explicitly removed
    // from backup", instead of relying on isSynced alone for both meanings.
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

                val mediaUrl = if (domainItem.type == SavedItemType.AUDIO) domainItem.thumbnailPath ?: "" else domainItem.content
                if ((domainItem.type == SavedItemType.IMAGE || domainItem.type == SavedItemType.VIDEO || domainItem.type == SavedItemType.AUDIO) &&
                    !mediaUrl.startsWith("http://") && !mediaUrl.startsWith("https://")
                ) {
                    val localPath = when (domainItem.type) {
                        SavedItemType.AUDIO -> domainItem.thumbnailPath ?: ""
                        else -> domainItem.thumbnailPath ?: domainItem.content
                    }
                    val bytes = readFileBytes(localPath)
                    if (bytes != null && storage != null) {
                        val fileExtension = when (domainItem.type) {
                            SavedItemType.VIDEO -> "mp4"
                            SavedItemType.AUDIO -> "m4a"
                            else -> "jpg"
                        }
                        val storageRef = storage.reference.child("users/${currentUser.uid}/media/${domainItem.id}.$fileExtension")
                        val uploadTask = storageRef.putBytes(bytes)
                        val snapshot = uploadTask.await()
                        val downloadUrl = (snapshot.metadata?.reference?.downloadUrl ?: throw Exception("No reference URL")).await()
                        finalItem = if (domainItem.type == SavedItemType.AUDIO) {
                            finalItem.copy(
                                thumbnailPath = downloadUrl.toString(),
                                sizeBytes = bytes.size.toLong()
                            )
                        } else {
                            finalItem.copy(
                                content = downloadUrl.toString(),
                                thumbnailPath = downloadUrl.toString(),
                                sizeBytes = bytes.size.toLong()
                            )
                        }
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
                        "orderIndex" to finalItem.orderIndex,
                        "folders" to finalItem.folders,
                        "extractedText" to finalItem.extractedText,
                        "thumbnailPath" to finalItem.thumbnailPath,
                        "isSynced" to true,
                        "linkTitle" to finalItem.linkTitle,
                        "linkDescription" to finalItem.linkDescription,
                        "linkImage" to finalItem.linkImage,
                        "sizeBytes" to finalItem.sizeBytes,
                        "mediaType" to finalItem.mediaType,
                        "watchStatus" to finalItem.watchStatus,
                        "genres" to finalItem.genres,
                        "watchProviders" to finalItem.watchProviders,
                        "trailerUrl" to finalItem.trailerUrl,
                        "backdropUrl" to finalItem.backdropUrl,
                        "releaseYear" to finalItem.releaseYear
                    )
                    firestore.collection("users").document(currentUser.uid)
                        .collection("items").document(finalItem.id)
                        .set(itemMap).await()

                    // isPendingBackup=true records that the user explicitly wants
                    // this item backed up, distinct from isSynced (current state).
                    finalItem = finalItem.copy(isSynced = true, isPendingBackup = true)
                    savedItemDao.insertItem(finalItem.toEntity())
                }
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Background Firebase backup failed for item ${entity.id}: ${e.message}")
            }
        }
        com.example.widget.WidgetUpdater.update(context)
    }

    // Removes an item from cloud backup. Clears BOTH isSynced (no longer backed up)
    // AND isPendingBackup (user no longer wants this auto re-uploaded). Without
    // clearing isPendingBackup too, syncUnsyncedItems() would see isSynced=false
    // and re-upload the item on the very next sync/pull-to-refresh, resurrecting
    // something the user just removed.
    suspend fun removeBackup(itemIds: List<String>) = withContext(Dispatchers.IO) {
        val currentUser = firebaseAuth?.currentUser ?: return@withContext
        for (id in itemIds) {
            try {
                val entity = savedItemDao.getItemById(id) ?: continue
                val item = entity.toDomain()
                
                val isMedia = item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO || item.type == SavedItemType.AUDIO
                val mediaUrl = if (item.type == SavedItemType.AUDIO) item.thumbnailPath else item.content
                val hasRemoteUrl = !mediaUrl.isNullOrBlank() && (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://"))

                val localFile = if (isMedia && hasRemoteUrl) {
                    val extension = when (item.type) {
                        SavedItemType.VIDEO -> "mp4"
                        SavedItemType.AUDIO -> "mp4"
                        else -> "jpg"
                    }
                    val fileName = "${item.id}.$extension"
                    val targetDir = getPermanentMediaDir(item.type)
                    val destFile = File(targetDir, fileName)

                    // Download step
                    try {
                        val request = Request.Builder().url(mediaUrl!!).build()
                        httpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw Exception("Server returned HTTP code ${response.code}")
                            }
                            val body = response.body ?: throw Exception("Response body is empty")
                            body.byteStream().use { inputStream ->
                                FileOutputStream(destFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                    } catch (downloadEx: Exception) {
                        Log.e("SecondBrainRepo", "Failed to download media blob for item $id: ${downloadEx.message}")
                        // Abort for this item to avoid data loss
                        continue
                    }
                    destFile
                } else null

                // Update local SavedItemEntity
                val updatedItem = if (isMedia && localFile != null) {
                    if (item.type == SavedItemType.AUDIO) {
                        item.copy(
                            thumbnailPath = localFile.absolutePath,
                            isSynced = false,
                            isPendingBackup = false,
                            isBackedUp = false
                        )
                    } else {
                        item.copy(
                            content = localFile.absolutePath,
                            thumbnailPath = localFile.absolutePath,
                            isSynced = false,
                            isPendingBackup = false,
                            isBackedUp = false
                        )
                    }
                } else {
                    item.copy(
                        isSynced = false,
                        isPendingBackup = false,
                        isBackedUp = false
                    )
                }
                savedItemDao.insertItem(updatedItem.toEntity())

                // Delete from Storage if it had a remote URL
                if (storage != null && hasRemoteUrl) {
                    try {
                        val storageRef = storage.getReferenceFromUrl(mediaUrl!!)
                        storageRef.delete().await()
                    } catch (storageEx: Exception) {
                        Log.w("SecondBrainRepo", "Failed to delete storage blob for item $id: ${storageEx.message}")
                    }
                }

                // Update Firestore document (Tombstone)
                if (firestore != null) {
                    val docRef = firestore.collection("users").document(currentUser.uid)
                        .collection("items").document(id)
                    
                    val updateMap = mutableMapOf<String, Any?>(
                        "isBackedUp" to false,
                        "timestamp" to System.currentTimeMillis()
                    )
                    if (item.type == SavedItemType.AUDIO) {
                        updateMap["thumbnailPath"] = ""
                    } else if (isMedia) {
                        updateMap["content"] = ""
                        updateMap["thumbnailPath"] = ""
                    }

                    docRef.update(updateMap).await()
                }

            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to remove backup for item $id: ${e.message}")
            }
        }
    }

    private fun getFolderDocId(folderName: String): String {
        return try {
            java.net.URLEncoder.encode(folderName, "UTF-8")
        } catch (e: Exception) {
            folderName.replace("/", "_")
        }
    }

    // ----------------------------------------------------
    // BACKGROUND SYNC FOR OFFLINE-SAVED ITEMS & FOLDERS
    // ----------------------------------------------------

    // Attempts to upload any items/folders saved locally while offline or waiting for sync.
    // Preserves explicit backup removal choices: media items marked with
    // isPendingBackup = false (after a user un-backed them up) are skipped to avoid
    // silently re-uploading media the user just removed from backup (isSynced
    // alone can't distinguish "never backed up" from "explicitly removed").
    // Non-media items (TEXT/LINK/CODE) are free/unlimited and always auto-synced
    // as before, since they were never gated by backup selection.
    suspend fun syncUnsyncedItems() = withContext(Dispatchers.IO) {
        val currentUser = firebaseAuth?.currentUser ?: return@withContext
        val items = savedItemDao.getAllItems()
        val unsynced = items.filter { entity ->
            if (!entity.isSynced) {
                val isMedia = entity.type == SavedItemType.IMAGE.name ||
                    entity.type == SavedItemType.VIDEO.name ||
                    entity.type == SavedItemType.AUDIO.name
                if (isMedia) entity.isPendingBackup else true
            } else {
                false
            }
        }

        if (unsynced.isNotEmpty()) {
            Log.d("SecondBrainRepo", "Starting sync of ${unsynced.size} unsynced items.")
            for (entity in unsynced) {
                try {
                    var domainItem = entity.toDomain()
                    var finalItem = domainItem

                    val mediaUrl = if (domainItem.type == SavedItemType.AUDIO) domainItem.thumbnailPath ?: "" else domainItem.content
                    if ((domainItem.type == SavedItemType.IMAGE || domainItem.type == SavedItemType.VIDEO || domainItem.type == SavedItemType.AUDIO) &&
                        !mediaUrl.startsWith("http://") && !mediaUrl.startsWith("https://")
                    ) {
                        val localPath = when (domainItem.type) {
                            SavedItemType.AUDIO -> domainItem.thumbnailPath ?: ""
                            else -> domainItem.thumbnailPath ?: domainItem.content
                        }
                        val bytes = readFileBytes(localPath)
                        if (bytes != null && storage != null) {
                            val fileExtension = when (domainItem.type) {
                                SavedItemType.VIDEO -> "mp4"
                                SavedItemType.AUDIO -> "m4a"
                                else -> "jpg"
                            }
                            val storageRef = storage.reference.child("users/${currentUser.uid}/media/${domainItem.id}.$fileExtension")
                            val uploadTask = storageRef.putBytes(bytes)
                            val snapshot = uploadTask.await()
                            val downloadUrl = (snapshot.metadata?.reference?.downloadUrl ?: throw Exception("No reference URL")).await()
                            finalItem = if (domainItem.type == SavedItemType.AUDIO) {
                                finalItem.copy(
                                    thumbnailPath = downloadUrl.toString(),
                                    sizeBytes = bytes.size.toLong(),
                                    isBackedUp = true
                                )
                            } else {
                                finalItem.copy(
                                    content = downloadUrl.toString(),
                                    thumbnailPath = downloadUrl.toString(),
                                    sizeBytes = bytes.size.toLong(),
                                    isBackedUp = true
                                )
                            }
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
                            "orderIndex" to finalItem.orderIndex,
                            "folders" to finalItem.folders,
                            "extractedText" to finalItem.extractedText,
                            "thumbnailPath" to finalItem.thumbnailPath,
                            "isSynced" to true,
                            "linkTitle" to finalItem.linkTitle,
                            "linkDescription" to finalItem.linkDescription,
                            "linkImage" to finalItem.linkImage,
                            "sizeBytes" to finalItem.sizeBytes,
                            "isBackedUp" to finalItem.isBackedUp,
                            "mediaType" to finalItem.mediaType,
                            "watchStatus" to finalItem.watchStatus,
                            "genres" to finalItem.genres,
                            "watchProviders" to finalItem.watchProviders,
                            "trailerUrl" to finalItem.trailerUrl,
                            "backdropUrl" to finalItem.backdropUrl,
                            "releaseYear" to finalItem.releaseYear
                        )
                        firestore.collection("users").document(currentUser.uid)
                            .collection("items").document(finalItem.id)
                            .set(itemMap).await()

                        // Update locally as synced. For media items this also implicitly
                        // confirms isPendingBackup was already true (that's why it was
                        // picked up above); leave the flag as-is for non-media items.
                        finalItem = finalItem.copy(isSynced = true)
                        savedItemDao.insertItem(finalItem.toEntity())
                        Log.d("SecondBrainRepo", "Successfully synced item: ${finalItem.id}")
                    }
                } catch (e: Exception) {
                    Log.e("SecondBrainRepo", "Failed to sync item ${entity.id}: ${e.message}")
                }
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
                        .collection("folders").document(getFolderDocId(folder.name))
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
        val mediaUrl = if (item.type == SavedItemType.AUDIO) item.thumbnailPath ?: "" else item.content
        if (currentUser != null && storage != null && mediaUrl.startsWith("https://firebasestorage")) {
            try {
                val storageRef = storage.getReferenceFromUrl(mediaUrl)
                storageRef.delete().await()
            } catch (e: Exception) {
                Log.e("SecondBrainRepo", "Failed to delete media from Storage: ${e.message}")
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
                    .collection("folders").document(getFolderDocId(folderName))
                    .set(mapOf(
                        "name" to folderName,
                        "colorHex" to colorHex,
                        "iconName" to iconName,
                        "isPinned" to isPinned,
                        "isSynced" to true
                    )).await()
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
                    .collection("folders").document(getFolderDocId(folderName))
                    .delete().await()
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
                    .collection("folders").document(getFolderDocId(updatedFolder.name))
                    .set(mapOf(
                        "name" to updatedFolder.name,
                        "colorHex" to updatedFolder.colorHex,
                        "iconName" to updatedFolder.iconName,
                        "isPinned" to updatedFolder.isPinned,
                        "isSynced" to true
                    )).await()
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
                userDocRef.collection("folders").document(getFolderDocId(newName)).set(mapOf(
                    "name" to newName,
                    "colorHex" to oldFolder.colorHex,
                    "iconName" to oldFolder.iconName,
                    "isPinned" to oldFolder.isPinned
                )).await()

                // Delete old folder in firestore
                userDocRef.collection("folders").document(getFolderDocId(oldName)).delete().await()

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
                        "linkImage" to finalItem.linkImage,
                        "sizeBytes" to finalItem.sizeBytes,
                        "mediaType" to finalItem.mediaType,
                        "watchStatus" to finalItem.watchStatus,
                        "genres" to finalItem.genres,
                        "watchProviders" to finalItem.watchProviders,
                        "trailerUrl" to finalItem.trailerUrl,
                        "backdropUrl" to finalItem.backdropUrl,
                        "releaseYear" to finalItem.releaseYear
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
                val rawName = doc.getString("name") ?: doc.id
                val name = try { java.net.URLDecoder.decode(rawName, "UTF-8") } catch (e: Exception) { rawName }
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
                val orderIndex = doc.getDouble("orderIndex") ?: timestamp.toDouble()
                @Suppress("UNCHECKED_CAST")
                val foldersList = doc.get("folders") as? List<String> ?: emptyList()
                val extractedText = doc.getString("extractedText")
                val thumbnailPath = doc.getString("thumbnailPath")
                val linkTitle = doc.getString("linkTitle")
                val linkDescription = doc.getString("linkDescription")
                val linkImage = doc.getString("linkImage")
                val sizeBytes = doc.getLong("sizeBytes") ?: 0L
                val mediaType = doc.getString("mediaType")
                val watchStatus = doc.getString("watchStatus")
                @Suppress("UNCHECKED_CAST")
                val genresList = doc.get("genres") as? List<String> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val watchProvidersList = doc.get("watchProviders") as? List<String> ?: emptyList()
                val trailerUrl = doc.getString("trailerUrl")
                val backdropUrl = doc.getString("backdropUrl")
                val releaseYear = doc.getString("releaseYear")

                val foldersJsonStr = "[" + foldersList.joinToString(",") { "\"$it\"" } + "]"
                val genresJsonStr = "[" + genresList.joinToString(",") { "\"$it\"" } + "]"
                val watchProvidersJsonStr = "[" + watchProvidersList.joinToString(",") { "\"$it\"" } + "]"

                val isBackedUp = doc.getBoolean("isBackedUp") ?: true
                
                val isMedia = type == SavedItemType.IMAGE.name || type == SavedItemType.VIDEO.name || type == SavedItemType.AUDIO.name
                val existing = savedItemDao.getItemById(id)
                val isRemoteUrl = existing != null && isMedia && (
                    (type == SavedItemType.AUDIO.name && existing.thumbnailPath != null && (existing.thumbnailPath.startsWith("http://") || existing.thumbnailPath.startsWith("https://"))) ||
                    (type != SavedItemType.AUDIO.name && existing.content.isNotBlank() && (existing.content.startsWith("http://") || existing.content.startsWith("https://")))
                )
                val newIsUnavailable = if (isMedia) {
                    if (existing != null && !isBackedUp && isRemoteUrl) true else (existing?.isUnavailable ?: false)
                } else {
                    false
                }

                if (existing != null) {
                    val isBackupStatusChanged = existing.isPendingBackup && !isBackedUp
                    val isUnavailableChanged = existing.isUnavailable != newIsUnavailable
                    
                    if (isBackupStatusChanged || isUnavailableChanged) {
                        savedItemDao.insertItem(
                            existing.copy(
                                isPendingBackup = if (isBackupStatusChanged) false else existing.isPendingBackup,
                                isSynced = if (isBackupStatusChanged) false else existing.isSynced,
                                isBackedUp = isBackedUp,
                                isUnavailable = newIsUnavailable
                            )
                        )
                    }
                }

                // If local item doesn't exist yet and isBackedUp is false, skip creation if there's no usable remote URL
                if (existing == null && !isBackedUp) {
                    val isMedia = type == SavedItemType.IMAGE.name || type == SavedItemType.VIDEO.name || type == SavedItemType.AUDIO.name
                    if (isMedia) {
                        val mediaUrl = if (type == SavedItemType.AUDIO.name) thumbnailPath else content
                        val hasUsableUrl = !mediaUrl.isNullOrBlank() && (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://"))
                        if (!hasUsableUrl) {
                            return@forEach
                        }
                    }
                }

                // Fetch current version of the local item (might have been updated above)
                val currentExisting = savedItemDao.getItemById(id)

                // Check if local DB already has a newer version of this item
                if (currentExisting != null && currentExisting.isSynced && currentExisting.timestamp >= timestamp) {
                    // Local is already up-to-date; skip to avoid overwriting local edits
                    return@forEach
                }

                // Preserve isPendingBackup from the existing local record if present
                val preservedPendingBackup = currentExisting?.isPendingBackup ?: true
                
                // Do NOT touch or clear the local device's own content/thumbnailPath
                val finalContent = currentExisting?.content ?: content
                val finalThumbnailPath = currentExisting?.thumbnailPath ?: thumbnailPath

                savedItemDao.insertItem(
                    SavedItemEntity(
                        id = id,
                        type = type,
                        title = title,
                        content = finalContent,
                        timestamp = timestamp,
                        foldersJson = foldersJsonStr,
                        extractedText = extractedText,
                        thumbnailPath = finalThumbnailPath,
                        orderIndex = orderIndex,
                        isSynced = true,
                        linkTitle = linkTitle,
                        linkDescription = linkDescription,
                        linkImage = linkImage,
                        sizeBytes = sizeBytes,
                        isPendingBackup = preservedPendingBackup,
                        isBackedUp = isBackedUp,
                        isUnavailable = newIsUnavailable,
                        mediaType = mediaType,
                        watchStatus = watchStatus,
                        genresJson = genresJsonStr,
                        watchProvidersJson = watchProvidersJsonStr,
                        trailerUrl = trailerUrl,
                        backdropUrl = backdropUrl,
                        releaseYear = releaseYear
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

    private fun buildOcrResponseSchema(): ResponseSchema {
        val urlItemSchema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "url" to ResponseSchema(
                    type = "STRING",
                    description = "The exact URL, transcribed character-for-character, with https:// prepended if no scheme was present in the source image."
                ),
                "description" to ResponseSchema(
                    type = "STRING",
                    description = "A brief factual description of what this URL or its surrounding text indicates, based only on what is visible in the image."
                )
            ),
            required = listOf("url", "description"),
            propertyOrdering = listOf("url", "description")
        )

        return ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "extractedText" to ResponseSchema(
                    type = "STRING",
                    description = "The clean, markdown-formatted text from the image, preserving bold, headings, bullet points, and original script/reading direction (including right-to-left scripts such as Urdu or Arabic)."
                ),
                "urls" to ResponseSchema(
                    type = "ARRAY",
                    items = urlItemSchema,
                    description = "All URLs or web addresses found in the image."
                )
            ),
            required = listOf("extractedText", "urls"),
            propertyOrdering = listOf("extractedText", "urls")
        )
    }

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
            You are a precise OCR and formatting engine. Extract all text from this image.

            FORMATTING RULES for extractedText:
            - Preserve bold text using **bold** markdown syntax.
            - Preserve headings using # / ## / ### based on visual hierarchy (font size, weight, position).
            - Preserve bullet points and numbered lists using standard Markdown syntax (- item, 1. item).
            - If the text is written right-to-left (e.g. Urdu, Arabic), preserve the original script and reading order exactly. Do not transliterate, translate, or reorder the words.
            - Preserve paragraph breaks and line breaks as they visually appear.
            - Do not invent formatting that is not visually present in the image.

            URL EXTRACTION RULES for urls:
            - Transcribe every URL or web address character-for-character with maximum precision, including hyphens, underscores, dots, subdomains, and path segments. Do not guess, simplify, or "clean up" a URL — copy it exactly as it visually appears.
            - If a URL has no scheme (e.g. "example.com" or "my-site.dev"), prepend "https://" without altering any other character.
            - If you are not fully confident in a specific character within a URL (e.g. due to blur or small font), re-examine that specific region of the image before finalizing your answer, prioritizing accuracy over speed.
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
            ),
            generationConfig = GenerationConfig(
                temperature = 0.1f,
                responseMimeType = "application/json",
                responseSchema = buildOcrResponseSchema()
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

    fun getPermanentMediaDir(type: SavedItemType): File {
        val subfolder = when (type) {
            SavedItemType.IMAGE -> "images"
            SavedItemType.VIDEO -> "videos"
            SavedItemType.AUDIO -> "audio"
            else -> "misc"
        }
        val dir = File(context.filesDir, "media/$subfolder")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun fetchLinkMetadata(urlString: String): LinkMetadata = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                "https://$urlString"
            } else {
                urlString
            }

            val document = org.jsoup.Jsoup.connect(cleanUrl)
                .timeout(8000)
                .ignoreHttpErrors(true)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .get()

            val title = document.title().takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[property=og:title]").attr("content").takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[name=twitter:title]").attr("content").takeIf { !it.isNullOrBlank() }

            val description = document.select("meta[property=og:description]").attr("content").takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[name=description]").attr("content").takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[name=twitter:description]").attr("content").takeIf { !it.isNullOrBlank() }

            val imageUrl = document.select("meta[property=og:image]").firstOrNull()?.absUrl("content")?.takeIf { it.isNotBlank() }
                ?: document.select("meta[name=twitter:image]").firstOrNull()?.absUrl("content")?.takeIf { it.isNotBlank() }
                ?: document.select("meta[property=og:image]").attr("content").takeIf { !it.isNullOrBlank() }
                ?: document.select("meta[name=twitter:image]").attr("content").takeIf { !it.isNullOrBlank() }

            LinkMetadata(title?.trim(), description?.trim(), imageUrl)
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Failed to fetch link metadata: ${e.message}")
            LinkMetadata(null, null, null)
        }
    }

    suspend fun updateDeviceSession() = withContext(Dispatchers.IO) {
        val currentUser = firebaseAuth?.currentUser ?: return@withContext
        val firestore = firestore ?: return@withContext
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

        try {
            val deviceDocRef = firestore.collection("users").document(currentUser.uid)
                .collection("devices").document(androidId)
            
            val snapshot = deviceDocRef.get().await()
            val now = System.currentTimeMillis()
            val firstSeen = if (snapshot.exists()) {
                snapshot.getLong("firstSeen") ?: now
            } else {
                now
            }

            val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            val deviceType = com.example.utils.DevicePerformance.getDeviceType(context)
            val osVersion = "Android ${android.os.Build.VERSION.RELEASE}"
            val appVersion = BuildConfig.VERSION_NAME

            val data = mapOf(
                "deviceId" to androidId,
                "deviceName" to deviceName,
                "deviceType" to deviceType,
                "osVersion" to osVersion,
                "appVersion" to appVersion,
                "lastActive" to now,
                "firstSeen" to firstSeen
            )

            deviceDocRef.set(data).await()
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Failed to update device session: ${e.message}")
        }
    }

    suspend fun getAllDeviceSessions(): List<DeviceSession> = withContext(Dispatchers.IO) {
        val currentUser = firebaseAuth?.currentUser ?: return@withContext emptyList()
        val firestore = firestore ?: return@withContext emptyList()

        try {
            val snap = firestore.collection("users").document(currentUser.uid)
                .collection("devices").get().await()
            
            snap.documents.mapNotNull { doc ->
                val deviceId = doc.getString("deviceId") ?: return@mapNotNull null
                val deviceName = doc.getString("deviceName") ?: ""
                val deviceType = doc.getString("deviceType") ?: "MOBILE"
                val osVersion = doc.getString("osVersion") ?: ""
                val appVersion = doc.getString("appVersion") ?: ""
                val lastActive = doc.getLong("lastActive") ?: 0L
                val firstSeen = doc.getLong("firstSeen") ?: 0L
                
                DeviceSession(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    deviceType = deviceType,
                    osVersion = osVersion,
                    appVersion = appVersion,
                    lastActive = lastActive,
                    firstSeen = firstSeen
                )
            }.sortedByDescending { it.lastActive }
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Failed to get device sessions: ${e.message}")
            emptyList()
        }
    }
}

data class LinkMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null
)

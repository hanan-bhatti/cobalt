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
// FOSS build: no Firebase imports
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


    suspend fun extractTextFromAudio(
        base64Audio: String,
        apiKey: String,
        model: String
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "API Key Missing. Enter your key in the AI Studio Secrets panel or the Profile page."
        }

        val promptText = "Transcribe this audio memo and format it as clear markdown notes. Give it a nice title as an H1, and format the rest appropriately."
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = promptText),
                        Part(inlineData = InlineData(mimeType = "audio/mp4", data = base64Audio))
                    )
                )
            )
        )

        try {
            val response = RetrofitClient.geminiService.generateContent("models/$model", apiKey, request)
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
        if (apiKey.isBlank()) {
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

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = promptText)
                    )
                )
            )
        )

        try {
            val response = RetrofitClient.geminiService.generateContent("models/$model", apiKey, request)
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



    fun readFileBytes(filePath: String): ByteArray? {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SecondBrainRepo", "Failed to read file bytes: ${e.message}")
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
                Log.e("SecondBrainRepo", "TMDb searchMulti failed")
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
            Log.e("SecondBrainRepo", "Jikan searchAnime failed")
        }

        results
    }

    suspend fun enrichMediaItemDetails(item: SavedItem, saveToDb: Boolean = true): SavedItem = withContext(Dispatchers.IO) {
        if (item.type != SavedItemType.MEDIA) return@withContext item

        var mediaType = item.mediaType
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

                    val fetchedGenres = details.genres?.mapNotNull { it.name } ?: emptyList()
                    val isEastAsianAnimation = details.originalLanguage?.lowercase() in listOf("ja", "zh", "ko", "cn") &&
                            (fetchedGenres.any { it.equals("Animation", ignoreCase = true) } ||
                             genres.any { it.equals("Animation", ignoreCase = true) })
                    if (isEastAsianAnimation && mediaType?.lowercase() != "anime") {
                        mediaType = "anime"
                        updated = true
                    }

                    if (genres.isEmpty() && fetchedGenres.isNotEmpty()) {
                        genres = fetchedGenres
                        updated = true
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
                mediaType = mediaType,
                genres = genres,
                watchProviders = watchProviders,
                trailerUrl = trailerUrl,
                backdropUrl = backdropUrl,
                rating = rating
            )
            if (saveToDb) {
                savedItemDao.insertItem(newItem.toEntity())
            }
            return@withContext newItem
        }

        return@withContext item
    }

    suspend fun updateItems(items: List<SavedItem>) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        items.forEach {
            savedItemDao.insertItem(it.toEntity())
        }
    }

    suspend fun saveItem(item: SavedItem, mediaBytes: ByteArray? = null, onProgress: (Float) -> Unit = {}): SavedItem = withContext(Dispatchers.IO) {
        val itemSize = when {
            mediaBytes != null -> mediaBytes.size.toLong()
            (item.thumbnailPath ?: item.content).startsWith("/") -> java.io.File(item.thumbnailPath ?: item.content).length()
            item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO || item.type == SavedItemType.AUDIO -> item.sizeBytes
            else -> item.content.toByteArray().size.toLong()
        }
        val finalItem = item.copy(sizeBytes = itemSize, timestamp = System.currentTimeMillis())
        savedItemDao.insertItem(finalItem.toEntity())
        com.example.widget.WidgetUpdater.update(context)
        finalItem
    }


    suspend fun deleteItem(item: SavedItem) = withContext(Dispatchers.IO) {
        savedItemDao.deleteItem(item.toEntity())
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
            isPinned = isPinned
        )
        customFolderDao.insertFolder(newFolder)
    }

    suspend fun deleteCustomFolder(folderName: String) = withContext(Dispatchers.IO) {
        customFolderDao.deleteFolder(CustomFolderEntity(folderName))
    }

    suspend fun updateCustomFolder(folder: CustomFolderEntity) = withContext(Dispatchers.IO) {
        customFolderDao.insertFolder(folder)
    }

    suspend fun renameCustomFolder(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        val folders = customFolderDao.getAllFolders()
        val oldFolder = folders.find { it.name == oldName } ?: CustomFolderEntity(oldName)

        // 1. Save new folder with same settings
        val newFolder = CustomFolderEntity(
            name = newName,
            colorHex = oldFolder.colorHex,
            iconName = oldFolder.iconName,
            isPinned = oldFolder.isPinned
        )
        customFolderDao.insertFolder(newFolder)

        // 2. Fetch all saved items and update their foldersJson if they contain oldName
        val allItemsList = savedItemDao.getAllItems()
        val itemsToUpdate = mutableListOf<com.example.data.local.SavedItemEntity>()
        allItemsList.forEach { entity ->
            val domain = entity.toDomain()
            if (domain.folders.contains(oldName)) {
                val updatedFolders = domain.folders.map { if (it == oldName) newName else it }
                itemsToUpdate.add(domain.copy(folders = updatedFolders).toEntity())
            }
        }
        if (itemsToUpdate.isNotEmpty()) {
            savedItemDao.insertItems(itemsToUpdate)
        }

        // 3. Delete old folder
        customFolderDao.deleteFolder(oldFolder)
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
        if (apiKey.isBlank()) {
            Log.e("SecondBrainRepo", "Gemini API key is missing or blank.")
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

}

data class LinkMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null
)

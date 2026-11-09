/*
 * Cobalt - A universal capture and personal knowledge archive
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
import com.example.util.SafeLogger as Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.CustomFolderEntity
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.calculateSizeBytes
import com.example.data.remote.Content
import com.example.data.remote.GenerateContentRequest
import com.example.data.remote.InlineData
import com.example.data.remote.Part
import com.example.data.remote.RetrofitClient
import com.example.data.remote.GenerationConfig
import com.example.data.remote.ResponseSchema
import com.example.data.remote.MediaApiClient
import com.example.data.remote.MediaSearchResultItem
import com.example.util.ItemTitleResolver
import com.example.util.loadTitleFallbacks
import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

class CobaltRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(context)
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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

        val cleanModel = model.removePrefix("models/").ifBlank { "gemini-2.0-flash" }

        val promptText = "You are an expert audio transcription assistant. Carefully transcribe all spoken words in this audio recording into its spoken language (such as English, Urdu, Hindi, etc.). Format your output in clear Markdown with an H1 title summarizing the recording, followed by organized notes and bullet points. If there is no human speech in the recording, reply with exactly: No speech detected."

        // Place audio inlineData first, then text prompt
        val parts = if (cleanModel.contains("transcribe", ignoreCase = true)) {
            listOf(
                Part(inlineData = InlineData(mimeType = "audio/mp4", data = base64Audio))
            )
        } else {
            listOf(
                Part(inlineData = InlineData(mimeType = "audio/mp4", data = base64Audio)),
                Part(text = promptText)
            )
        }
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = parts)
            )
        )

        Log.d("CobaltRepo", "Sending audio transcription request to models/$cleanModel (base64 size: ${base64Audio.length / 1024} KB)")

        try {
            val response = RetrofitClient.geminiService.generateContent("models/$cleanModel", apiKey, request)
            val candidate = response.candidates?.firstOrNull()
            val candidateText = candidate?.content?.parts
                ?.mapNotNull { it.text ?: it.audioTranscription?.text }
                ?.joinToString("")
                ?.trim()

            val usage = response.usageMetadata
            val promptTokens = usage?.promptTokenCount ?: (base64Audio.length / 400).coerceAtLeast(10)
            val candTokens = usage?.candidatesTokenCount ?: ((candidateText?.length ?: 0) / 4).coerceAtLeast(1)
            val totalTokens = usage?.totalTokenCount ?: (promptTokens + candTokens)
            com.example.util.GeminiUsageTracker.recordUsage(
                context = context,
                taskType = "AUDIO",
                promptTokens = promptTokens,
                candidateTokens = candTokens,
                totalTokens = totalTokens,
                modelName = cleanModel
            )

            val finishReason = candidate?.finishReason
            val blockReason = response.promptFeedback?.blockReason
            Log.d("CobaltRepo", "Gemini Audio call completed: candidatesCount=${response.candidates?.size}, finishReason=$finishReason, blockReason=$blockReason, textLength=${candidateText?.length ?: 0}")

            if (!candidateText.isNullOrBlank()) {
                if (candidateText.equals("No speech detected", ignoreCase = true) ||
                    candidateText.equals("No speech detected.", ignoreCase = true)) {
                    "Error: No speech detected in the audio recording. Please speak clearly into the microphone."
                } else {
                    candidateText
                }
            } else {
                Log.w("CobaltRepo", "Gemini Audio call produced no text. finishReason=$finishReason, blockReason=$blockReason")
                when {
                    blockReason != null -> "Error: Audio transcription was blocked ($blockReason)."
                    finishReason == "SAFETY" -> "Error: Transcription was blocked by safety filters."
                    finishReason == "RECITATION" -> "Error: Transcription was blocked by recitation filters."
                    finishReason == "MAX_TOKENS" -> "Error: Audio recording exceeded token limits."
                    finishReason != null && finishReason != "STOP" -> "Error: Audio transcription stopped ($finishReason)."
                    else -> "Error: No speech detected in the audio recording. Please speak clearly into the microphone."
                }
            }
        } catch (e: Exception) {
            val errorBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
            Log.e("CobaltRepo", "Gemini Audio call failed: ${e.message}, errorBody: $errorBody", e)
            "Error: ${com.example.util.GoogleApiErrorMapper.formatException(e)}"
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
            You are an expert voice memo scribe for a Cobalt app.
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
            val ocrText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            val usage = response.usageMetadata
            val promptTokens = usage?.promptTokenCount ?: 258
            val candTokens = usage?.candidatesTokenCount ?: ((ocrText?.length ?: 0) / 4).coerceAtLeast(1)
            val totalTokens = usage?.totalTokenCount ?: (promptTokens + candTokens)
            com.example.util.GeminiUsageTracker.recordUsage(
                context = context,
                taskType = "OCR",
                promptTokens = promptTokens,
                candidateTokens = candTokens,
                totalTokens = totalTokens,
                modelName = model
            )
            ocrText
        } catch (e: Exception) {
            Log.e("CobaltRepo", "Gemini Speech format call failed: ${e.message}", e)
            "Error: ${com.example.util.GoogleApiErrorMapper.formatException(e)}"
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

    fun getRecentDockItemsFlow(): Flow<List<SavedItem>> = savedItemDao.getRecentDockItemsFlow()
        .map { items -> items.map { it.toDomainModel() } }.flowOn(Dispatchers.IO)

    suspend fun getAllItems(): List<SavedItem> = withContext(Dispatchers.IO) {
        try {
            savedItemDao.getAllItems().map { it.toDomain() }
        } catch (e: Exception) {
            Log.e("CobaltRepository", "getAllItems failed", e)
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

    private fun SavedItemEntity.toDomain(): SavedItem = toDomainModel()

    private fun SavedItem.toEntity(): SavedItemEntity = SavedItemEntity.fromDomainModel(this)

    private val notePersistence by lazy { com.example.notes.NotePersistence(context, db) }

    suspend fun searchNoteFiles(query: String): List<SavedItem> = withContext(Dispatchers.IO) {
        val terms = Regex("[\\p{L}\\p{N}_]+").findAll(query).map { it.value.take(64) }.take(12).toList()
        if (terms.isEmpty()) return@withContext emptyList()
        val match = terms.joinToString(" AND ") { "\"$it\"*" }
        savedItemDao.searchNoteFiles(match).map { it.toDomainModel() }
    }

    suspend fun getItemForOpen(id: String): SavedItem? = notePersistence.load(id)

    /** The desktop editor is bounded independently of the phone's large-note editor. */
    suspend fun getItemForWeb(id: String, maxBytes: Int): SavedItem? = withContext(Dispatchers.IO) {
        val item = notePersistence.load(id, includeSmallBody = false) ?: return@withContext null
        if (!com.example.notes.NotePersistence.isNote(item)) return@withContext item
        check(!item.bodyMissing) { "Note body is unavailable. Recover it on your phone." }
        require(item.byteLength <= maxBytes) { "This note is too large for the web editor. Open it on your phone." }
        val text = notePersistence.store.open(id, requireNotNull(item.contentHash)).use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(bytes.size().toLong() + count <= maxBytes) { "Note exceeds the web editor limit." }
                bytes.write(buffer, 0, count)
            }
            bytes.toString("UTF-8")
        }
        item.copy(content = text, bodyLoaded = true)
    }

    suspend fun saveNoteFromWeb(item: SavedItem, beforeCommit: () -> Unit): SavedItem = withContext(Dispatchers.IO) {
        require(com.example.notes.NotePersistence.isNote(item))
        notePersistence.save(sanitizeItemTitle(item), beforeCommit = beforeCommit).also {
            com.example.widget.WidgetUpdater.update(context)
        }
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
            Log.e("CobaltRepo", "Failed to read file bytes: ${e.message}")
            null
        }
    }

    private val prefs = context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_TMDB_API_KEY = ""
    }

    fun getTmdbApiKey(): String {
        return com.example.util.SecretPreferences(prefs, "cobalt_prefs").get("tmdb_api_key")?.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
    }

    fun setTmdbApiKey(key: String) {
        com.example.util.SecretPreferences(prefs, "cobalt_prefs").putAll(mapOf("tmdb_api_key" to key))
    }

    suspend fun searchMedia(query: String, onSourceFailure: (String) -> Unit = {}): List<MediaSearchResultItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = java.util.Collections.synchronizedList(mutableListOf<MediaSearchResultItem>())
        val apiKey = getTmdbApiKey()

        savedItemDao.searchCachedMedia(query).forEach { entity ->
            val cached = entity.toDomainModel()
            results.add(MediaSearchResultItem(id = cached.id, title = cached.title, mediaType = cached.mediaType ?: "movie",
                posterUrl = cached.thumbnailPath, backdropUrl = cached.backdropUrl, releaseYear = cached.releaseYear,
                overview = cached.extractedText, genres = cached.genres, rating = cached.rating,
                trailerUrl = cached.trailerUrl, traktId = cached.traktId, tmdbId = cached.tmdbId, imdbId = cached.imdbId, mediaFacts = cached.mediaFacts))
        }
        val unavailable = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val providers = com.example.media.MediaProviderSettings(context)
        val traktJob = async {
            if (providers.enabled("trakt") && com.example.data.remote.trakt.TraktAuthManager.getInstance(context).hasValidToken()) {
                try {
                    val found = withTimeoutOrNull(6_000L) { com.example.data.remote.trakt.TraktSyncEngine.getApiService().searchMedia(query.trim()) }
                        ?: run { unavailable.add("Trakt"); emptyList() }
                    for (result in found) {
                        val movie = result.movie; val show = result.show
                        val ids = movie?.ids ?: show?.ids ?: continue
                        val kind = if (movie != null) "movie" else "tv"
                        val images = movie?.images ?: show?.images
                        fun image(type: String): String? = images?.get(type)?.firstOrNull()?.let {
                            if (it.startsWith("https://")) it else "https://$it"
                        }
                        results += MediaSearchResultItem(id = "trakt_${kind}_${ids.trakt}", title = movie?.title ?: show?.title.orEmpty(),
                            mediaType = kind, posterUrl = image("poster"), backdropUrl = image("fanart"),
                            releaseYear = (movie?.year ?: show?.year)?.toString(), overview = movie?.overview ?: show?.overview,
                            genres = show?.genres.orEmpty(), rating = movie?.rating ?: show?.rating,
                            runtime = movie?.runtime ?: show?.runtime, trailerUrl = movie?.trailer ?: show?.trailer, traktId = ids.trakt, tmdbId = ids.tmdb, imdbId = ids.imdb)
                    }
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { unavailable.add("Trakt"); Log.w("CobaltRepo", "Trakt search unavailable") }
            }
        }
        val tmdbJob = async {
            if (providers.enabled("tmdb") && apiKey.isNotBlank()) {
                try {
                    val tmdbResponse = withTimeoutOrNull(6000L) {
                        MediaApiClient.tmdbApiService.searchMulti(query = query, apiKey = apiKey)
                    }
                    if (tmdbResponse == null) unavailable.add("TMDB")
                    tmdbResponse?.results?.forEach { item ->
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
                                    tmdbId = item.id,
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
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (e: Exception) {
                    unavailable.add("TMDB")
                    Log.w("CobaltRepo", "TMDB search unavailable")
                }
            }
        }

        val jikanJob = async {
            if (providers.enabled("jikan")) try {
                val jikanResponse = withTimeoutOrNull(3500L) {
                    MediaApiClient.jikanService(providers.jikanUrl).searchAnime(query = query, limit = 10)
                }
                if (jikanResponse == null) unavailable.add("Jikan")
                jikanResponse?.data?.forEach { item ->
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
                            mediaFacts = com.example.data.model.MediaFacts("Jikan", item.url, episodeCount = item.episodes, publicationStatus = item.status),
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
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (e: Exception) {
                unavailable.add("Jikan")
                Log.w("CobaltRepo", "Jikan search unavailable")
            }
        }

        val mangaJob = async {
            if (providers.enabled("jikan") && providers.mangaEnabled) try {
                val data = withTimeoutOrNull(5000L) { MediaApiClient.jikanService(providers.jikanUrl).searchManga(query, 10) }
                if (data == null) unavailable.add("Jikan")
                data?.data.orEmpty().forEach { manga -> results.add(MediaSearchResultItem(
                    id = "jikan_manga_${manga.malId}", title = manga.titleEnglish?.takeIf { it.isNotBlank() } ?: manga.title?.takeIf { it.isNotBlank() } ?: "Manga",
                    mediaType = "manga", posterUrl = manga.images?.jpg?.largeImageUrl ?: manga.images?.jpg?.imageUrl,
                    overview = manga.synopsis, rating = manga.score, genres = manga.genres.orEmpty().mapNotNull { it.name },
                    mediaFacts = com.example.data.model.MediaFacts("Jikan", manga.url, chapterCount = manga.chapters,
                        volumeCount = manga.volumes, publicationStatus = manga.status))) }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { unavailable.add("Jikan"); Log.w("CobaltRepo", "Manga search unavailable") }
        }
        traktJob.await()
        tmdbJob.await()
        jikanJob.await()
        mangaJob.await()
        if (unavailable.isNotEmpty()) onSourceFailure("${unavailable.sorted().joinToString(", ")} unavailable. Showing saved titles and other sources. Check settings or retry.")
        com.example.media.MediaIdentity.merge(results.toList(), providers.priority).map { row ->
            val existing = row.traktId?.let { savedItemDao.getItemByTraktId(it, row.mediaType) }
                ?: row.tmdbId?.let { savedItemDao.getItemByTmdbId(it, row.mediaType) }
            if (existing == null) row else row.copy(id = existing.id)
        }
    }

    suspend fun enrichMediaItemDetails(item: SavedItem, saveToDb: Boolean = true): SavedItem = withContext(Dispatchers.IO) {
        if (item.type != SavedItemType.MEDIA) return@withContext item
        if (item.mediaType in listOf("movie", "tv")) return@withContext com.example.media.MediaDetailHydrator.load(context, item, saveToDb, db)
        // Jikan anime and manga keep their catalogue identity and never enter account sync.
        if (item.mediaType != "anime" || !item.id.startsWith("jikan_anime_")) return@withContext item
        val id = item.id.substringAfterLast('_').toIntOrNull() ?: return@withContext item
        val streaming = try {
            withTimeoutOrNull(8_000L) { MediaApiClient.jikanService(com.example.media.MediaProviderSettings(context).jikanUrl).getAnimeStreaming(id) }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { null }
        val providers = streaming?.data?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.distinct().orEmpty()
        if (providers.isEmpty()) return@withContext item
        val enriched = item.copy(watchProviders = providers)
        if (saveToDb) db.withTransaction {
            val current = savedItemDao.getItemById(item.id)
            if (current != null && !current.isDeleted && !com.example.notes.NoteDeletionFence(context).deleted(item.id)) {
                val metadata = SavedItemEntity.fromDomainModel(enriched)
                savedItemDao.insertItem(current.copy(watchProvidersJson = metadata.watchProvidersJson))
            }
        }
        enriched
    }

    suspend fun updateItems(items: List<SavedItem>) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        items.forEach {
            notePersistence.save(it)
        }
    }

    suspend fun updateItemTitleAndDescription(id: String, title: String, linkDescription: String?): Int = withContext(Dispatchers.IO) {
        val count = savedItemDao.updateTitleAndDescription(id, title, linkDescription, System.currentTimeMillis())
        try {
            savedItemDao.updateNoteSearchTitle(id, title)
        } catch (_: Exception) {}
        count
    }
    private fun sanitizeItemTitle(item: SavedItem): SavedItem {
        val sanitized = ItemTitleResolver.resolveItemTitle(
            userTitle = item.title,
            displayName = null,
            type = item.type,
            timestamp = item.timestamp,
            fallbacks = context.loadTitleFallbacks()
        )
        return if (sanitized != item.title) item.copy(title = sanitized) else item
    }

    suspend fun saveItemLocallyOnly(item: SavedItem) = withContext(Dispatchers.IO) {
        val guarded = sanitizeItemTitle(item)
        fun prepare(): SavedItem {
            val itemToSave = persistLocalMedia(guarded)
            val itemSize = itemToSave.calculateSizeBytes()
            val updatedItem = itemToSave.copy(sizeBytes = itemSize, timestamp = System.currentTimeMillis())
            return updatedItem
        }
        if (com.example.notes.NotePersistence.isNote(guarded)) notePersistence.save(prepare()) else {
            com.example.notes.NoteWriteLock.mutex.withLock {
                com.example.notes.NoteDeletionFence(context).requireAlive(guarded.id)
                check(savedItemDao.isItemDeleted(guarded.id) != true) { "This item is in Trash." }
                notePersistence.saveNonNoteLocked(prepare())
            }
        }
    }

    private fun persistLocalMedia(item: SavedItem): SavedItem {
        if (item.type != SavedItemType.VIDEO && item.type != SavedItemType.IMAGE && item.type != SavedItemType.AUDIO) {
            return item
        }
        var updated = item
        val destDir = getPermanentMediaDir(item.type)
        val ext = when (item.type) {
            SavedItemType.VIDEO -> "mp4"
            SavedItemType.AUDIO -> "mp4"
            else -> "jpg"
        }

        val primaryPath = if (item.type == SavedItemType.AUDIO) item.thumbnailPath else item.content
        if (!primaryPath.isNullOrBlank() && primaryPath.startsWith("/") &&
            (primaryPath.startsWith(context.cacheDir.absolutePath) || primaryPath.contains("/cache/"))) {
            val sourceFile = File(primaryPath)
            if (sourceFile.exists() && sourceFile.length() > 0) {
                val permFile = File(destDir, "${item.id}.$ext")
                val tempFile = File(destDir, "${item.id}.$ext.tmp")
                try {
                    sourceFile.inputStream().use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                            output.flush()
                            try { output.fd.sync() } catch (_: Exception) {}
                        }
                    }
                    if (tempFile.exists() && tempFile.length() == sourceFile.length()) {
                        if (permFile.exists()) permFile.delete()
                        if (tempFile.renameTo(permFile)) {
                            try { sourceFile.delete() } catch (_: Exception) {}
                            updated = if (item.type == SavedItemType.AUDIO) {
                                updated.copy(thumbnailPath = permFile.absolutePath)
                            } else {
                                updated.copy(content = permFile.absolutePath)
                            }
                        }
                    } else {
                        try { tempFile.delete() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e("CobaltRepo", "Could not persist media to permanent storage: ${e.message}", e)
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }
        }

        // Also persist thumbnail if it's in cacheDir
        val thumbPath = updated.thumbnailPath
        if (!thumbPath.isNullOrBlank() && thumbPath.startsWith("/") &&
            (thumbPath.startsWith(context.cacheDir.absolutePath) || thumbPath.contains("/cache/")) &&
            thumbPath != updated.content) {
            val sourceThumb = File(thumbPath)
            if (sourceThumb.exists() && sourceThumb.length() > 0) {
                val permThumb = File(getPermanentMediaDir(SavedItemType.IMAGE), "thumb_${item.id}.jpg")
                val tempThumb = File(getPermanentMediaDir(SavedItemType.IMAGE), "thumb_${item.id}.jpg.tmp")
                try {
                    sourceThumb.inputStream().use { input ->
                        java.io.FileOutputStream(tempThumb).use { output ->
                            input.copyTo(output)
                            output.flush()
                            try { output.fd.sync() } catch (_: Exception) {}
                        }
                    }
                    if (tempThumb.exists() && tempThumb.length() > 0) {
                        if (permThumb.exists()) permThumb.delete()
                        if (tempThumb.renameTo(permThumb)) {
                            try { sourceThumb.delete() } catch (_: Exception) {}
                            updated = updated.copy(thumbnailPath = permThumb.absolutePath)
                        }
                    } else {
                        try { tempThumb.delete() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e("CobaltRepo", "Could not persist thumbnail to permanent storage: ${e.message}", e)
                    try { tempThumb.delete() } catch (_: Exception) {}
                }
            }
        }
        return updated
    }

    suspend fun saveItem(item: SavedItem, mediaBytes: ByteArray? = null, descriptionIsAi: Boolean = false, onProgress: (Float) -> Unit = {}): SavedItem = withContext(Dispatchers.IO) {
        val guarded = sanitizeItemTitle(item)
        suspend fun persist(): SavedItem {
            val itemToSave = persistLocalMedia(guarded)
            val itemSize = when {
                mediaBytes != null -> mediaBytes.size.toLong()
                else -> itemToSave.calculateSizeBytes()
            }
            val finalItem = itemToSave.copy(sizeBytes = itemSize, timestamp = if (itemToSave.timestamp == 0L) System.currentTimeMillis() else itemToSave.timestamp, updatedAt = System.currentTimeMillis())
            if (finalItem.type != SavedItemType.LINK) return if (com.example.notes.NotePersistence.isNote(finalItem))
                notePersistence.save(finalItem, expectedUpdatedAt = itemToSave.updatedAt) else notePersistence.saveNonNoteLocked(finalItem)
            return db.withTransaction {
                val old = if (finalItem.type == SavedItemType.LINK) savedItemDao.getItemById(finalItem.id) else null
                val saved = if (com.example.notes.NotePersistence.isNote(finalItem)) notePersistence.save(finalItem) else notePersistence.saveNonNoteLocked(finalItem)
                if (saved.type == SavedItemType.LINK) {
                    val previous = db.linkPreviewDao().get(saved.id)?.takeIf { it.url == saved.content }
                    val changed = previous == null || old?.linkDescription != saved.linkDescription
                    val state = previous ?: com.example.data.local.LinkPreviewEntity(saved.id, saved.content)
                    db.linkPreviewDao().put(state.copy(
                        replaceFallback = if (changed) descriptionIsAi else state.replaceFallback,
                        generation = if (changed) java.util.UUID.randomUUID().toString() else state.generation
                    ))
                }
                saved
            }
        }
        val saved = if (com.example.notes.NotePersistence.isNote(guarded)) persist() else {
            com.example.notes.NoteWriteLock.mutex.withLock {
                com.example.notes.NoteDeletionFence(context).requireAlive(guarded.id)
                check(savedItemDao.isItemDeleted(guarded.id) != true) { "This item is in Trash." }
                persist()
            }
        }
        if (saved.type == SavedItemType.LINK) runCatching { com.example.links.LinkPreviewWorker.schedule(context) }
        com.example.widget.WidgetUpdater.update(context)
        saved
    }

    /** Targeted compare-and-set update: a late fetch cannot restore a deleted item or older fields. */
    suspend fun processNextLinkPreview(): Boolean = withContext(Dispatchers.IO) {
        val request = db.linkPreviewDao().next() ?: return@withContext false
        val before = savedItemDao.getItemById(request.itemId) ?: return@withContext false
        val metadata = fetchLinkMetadata(request.url)
        db.withTransaction {
            val current = db.linkPreviewDao().get(request.itemId)
            if (current?.generation == request.generation) {
                val found = metadata.title != null || metadata.description != null || metadata.imageUrl != null
                val description = com.example.links.LinkDescriptionPolicy.automatic(
                    before.linkDescription, metadata.description, current.replaceFallback)
                savedItemDao.applyLinkPreview(request.itemId, request.url, before.title, before.linkDescription,
                    metadata.title, description, metadata.imageUrl)
                db.linkPreviewDao().put(current.copy(replaceFallback = current.replaceFallback && metadata.description.isNullOrBlank(),
                    attempts = current.attempts + 1, pending = !found && current.attempts < 2))
            }
        }
        com.example.widget.WidgetUpdater.update(context)
        true
    }

    suspend fun deleteItem(item: SavedItem): Boolean = com.example.notes.TrashManager(context, db).delete(item.id)

    suspend fun restoreDeletedItem(id: String) = com.example.notes.TrashManager(context, db).restore(id)

    suspend fun findExistingMediaItem(
        tmdbId: Int?,
        traktId: Long?,
        imdbId: String?,
        id: String,
        mediaKind: String? = null
    ): SavedItem? = withContext(Dispatchers.IO) {
        savedItemDao.findMediaItemByExternalIds(tmdbId, traktId, imdbId, id, mediaKind)?.toDomainModel()
    }

    suspend fun findExistingLink(url: String): SavedItem? = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return@withContext null
        val normalized = com.example.util.CobaltCustomTabsLauncher.normalizeUrl(trimmed)
        val alt = if (normalized.startsWith("https://", ignoreCase = true)) {
            "http://" + normalized.removePrefix("https://")
        } else if (normalized.startsWith("http://", ignoreCase = true)) {
            "https://" + normalized.removePrefix("http://")
        } else normalized
        savedItemDao.findLinkByUrl(normalized, alt)?.toDomainModel()
    }

    /** Explicit user add: checks all three IDs (tmdbId, traktId, imdbId) to prevent duplicates and recover from Trash. */
    suspend fun saveEditedMediaItem(item: SavedItem): SavedItem = withContext(Dispatchers.IO) {
        val supported = item.mediaType in listOf("movie", "tv") && ((item.traktId ?: 0) > 0 || (item.tmdbId ?: 0) > 0 || !item.imdbId.isNullOrBlank())
        if (!supported) return@withContext saveItem(item)
        com.example.data.remote.trakt.TraktOutbox.saveEditedMedia(context, sanitizeItemTitle(item), db).also { com.example.widget.WidgetUpdater.update(context) }
    }

    suspend fun saveNewMediaItem(item: SavedItem): SavedItem = withContext(Dispatchers.IO) {
        require(item.type == SavedItemType.MEDIA)
        val fence = com.example.notes.NoteDeletionFence(context)
        val existing = savedItemDao.findMediaItemByExternalIds(item.tmdbId, item.traktId, item.imdbId, item.id, item.mediaType)
        if (existing != null) {
            if (!existing.isDeleted && !fence.deleted(existing.id)) {
                return@withContext existing.toDomainModel()
            }
            if (existing.isDeleted && !fence.deleted(existing.id)) {
                try {
                    restoreDeletedItem(existing.id)
                    val restored = savedItemDao.getItemById(existing.id)?.toDomainModel()
                    if (restored != null) {
                        val updated = restored.copy(
                            watchStatus = item.watchStatus ?: restored.watchStatus,
                            folders = if (item.folders.isNotEmpty()) (restored.folders + item.folders).distinct() else restored.folders,
                            updatedAt = System.currentTimeMillis()
                        )
                        saveItem(updated)
                        return@withContext updated
                    }
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: IllegalStateException) { /* Purged or expired undo, fall through to fresh capture */ }
            }
        }
        val prefix = item.id.substringBeforeLast('_', item.id) + "_"
        val providerId = item.id.substringAfterLast('_')
        val matched = item.traktId?.let { savedItemDao.getItemByTraktId(it, item.mediaType) }
            ?: item.tmdbId?.let { savedItemDao.getItemByTmdbId(it, item.mediaType) }
        if (matched != null && !fence.deleted(matched.id)) return@withContext matched.toDomainModel()
        val active = savedItemDao.activeMediaIds().firstOrNull { !fence.deleted(it) && (it == item.id || (it.startsWith(prefix) && it.endsWith("_$providerId"))) }
        val identity = active ?: if (fence.deleted(item.id) || existing?.isDeleted == true) prefix + java.util.UUID.randomUUID() + "_" + providerId else item.id
        val selected = sanitizeItemTitle(item.copy(id = identity))
        val supported = selected.mediaType in listOf("movie", "tv") &&
            ((selected.traktId ?: 0) > 0 || (selected.tmdbId ?: 0) > 0 || !selected.imdbId.isNullOrBlank())
        if (supported) {
            val saved = com.example.data.remote.trakt.TraktOutbox.saveCapturedMedia(context, selected, db)
            com.example.widget.WidgetUpdater.update(context)
            saved
        } else saveItem(selected).also {
            db.mediaTombstoneDao().clearMatching(selected.mediaType.orEmpty(), selected.traktId?.toString().orEmpty(),
                selected.tmdbId?.toString().orEmpty(), selected.imdbId.orEmpty())
        }
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
            itemsToUpdate.forEach { notePersistence.save(it.toDomainModel()) }
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
        sensitivity: String = "Medium",
        customPrompt: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.e("CobaltRepo", "Gemini API key is missing or blank.")
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

        // Build prompt: custom user prompt if provided, else system default OCR prompt
        val basePrompt = if (!customPrompt.isNullOrBlank()) {
            customPrompt.trim()
        } else {
            SettingsRepository.DEFAULT_OCR_PROMPT
        }
        val sensitivityPrompt = when (sensitivity) {
            "Low" -> "\n\nExtract only the most prominent, large text. Ignore small details, noise, or blurry text."
            "High" -> "\n\nExtract all visible text perfectly, inferring missing characters or fixing typos caused by blurriness, and preserve the layout logic."
            else -> "\n\nExtract all clearly visible text."
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
            val ocrText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            val usage = response.usageMetadata
            val promptTokens = usage?.promptTokenCount ?: 258
            val candTokens = usage?.candidatesTokenCount ?: ((ocrText?.length ?: 0) / 4).coerceAtLeast(1)
            val totalTokens = usage?.totalTokenCount ?: (promptTokens + candTokens)
            com.example.util.GeminiUsageTracker.recordUsage(
                context = context,
                taskType = "OCR",
                promptTokens = promptTokens,
                candidateTokens = candTokens,
                totalTokens = totalTokens,
                modelName = model
            )
            ocrText
        } catch (e: Exception) {
            Log.e("CobaltRepo", "Gemini API call failed: ${e.message}", e)
            "Error: ${com.example.util.GoogleApiErrorMapper.formatException(e)}"
        }
    }

    // Helper to save a shared image/video to a local file cache
    suspend fun saveToLocalCache(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, fileName)
        FileOutputStream(cacheFile).use { fos ->
            fos.write(bytes)
            fos.flush()
            try { fos.fd.sync() } catch (_: Exception) {}
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
            com.example.links.LinkMetadataExtractor.fetch(urlString)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("CobaltRepo", "Link metadata unavailable")
            LinkMetadata(null, null, null)
        }
    }

    suspend fun ensureMediaCached(item: SavedItem): SavedItem? = withContext(Dispatchers.IO) {
        val isMedia = item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO || item.type == SavedItemType.AUDIO
        val remoteUrl = if (item.type == SavedItemType.AUDIO) item.thumbnailPath else item.content
        if (!isMedia || remoteUrl.isNullOrBlank() || (!remoteUrl.startsWith("http://") && !remoteUrl.startsWith("https://"))) {
            return@withContext null
        }

        try {
            val fence = com.example.notes.NoteDeletionFence(context)
            if (fence.deleted(item.id) || savedItemDao.isItemDeleted(item.id) == true) return@withContext null
            val destDir = getPermanentMediaDir(item.type)
            val extension = when (item.type) {
                SavedItemType.VIDEO -> "mp4"
                SavedItemType.AUDIO -> "mp4"
                else -> "jpg"
            }
            val fileName = "${item.id}.$extension"
            val destFile = java.io.File(destDir, fileName)

            if (!destFile.exists() || destFile.length() == 0L) {
                if (destFile.exists()) destFile.delete()
                val tempFile = java.io.File(destDir, "$fileName.tmp")
                val request = okhttp3.Request.Builder().url(remoteUrl).build()
                val client = okhttp3.OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                            output.flush()
                            try { output.fd.sync() } catch (_: Exception) {}
                        }
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0L) {
                    com.example.notes.NoteBodyLeases.guarded {
                        fence.requireAlive(item.id)
                        check(tempFile.renameTo(destFile)) { "Could not publish cached media" }
                    }
                } else {
                    try { tempFile.delete() } catch (_: Exception) {}
                    return@withContext null
                }
            }

            val updatedItem = if (item.type == SavedItemType.AUDIO) {
                item.copy(thumbnailPath = destFile.absolutePath)
            } else {
                item.copy(content = destFile.absolutePath, thumbnailPath = destFile.absolutePath)
            }

            saveItemLocallyOnly(updatedItem)
            return@withContext updatedItem
        } catch (e: Exception) {
            Log.e("CobaltRepo", "Failed to cache media: ${e.message}")
            return@withContext null
        }
    }

}

data class LinkMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null
)

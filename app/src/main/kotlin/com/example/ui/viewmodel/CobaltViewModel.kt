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

package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.util.SafeLogger as Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.repository.CobaltRepository
import com.example.data.remote.MediaSearchResultItem
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class CobaltViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = CobaltRepository(context)
    private val fileEditor = com.example.notes.FileEditorSession(context, viewModelScope)
    val fileEditorState = fileEditor.state
    fun enableContinuousEditor() = fileEditor.enableContinuous()
    fun continuousEditorChanged(document: com.example.notes.ContinuousNoteDocument) = fileEditor.continuousChanged(document)
    fun continuousEditorBusy(document: com.example.notes.ContinuousNoteDocument, busy: Boolean) = fileEditor.continuousBusy(document, busy)
    fun formatContinuousEditor(format: String) { fileEditorState.value.document?.onFormat?.invoke(format) }
    suspend fun checkpointFileEditor() = fileEditor.flushJournal()
    fun replaceContinuousNoteRange(start: Long, end: Long, hash: String, text: String) =
        fileEditor.replaceContinuousRange(start, end, hash, text)
    fun editFileWindow(text: String) = fileEditor.edit(text)
    fun focusFileWindow(start: Long, end: Long, hash: String) = fileEditor.focus(start, end, hash)
    fun flushFileEditor() { viewModelScope.launch { fileEditor.flushJournal(fullCheckpoint = false) } }
    val settingsRepository = com.example.data.repository.SettingsRepository(context)

    /** Returns true when the device has an active internet-capable network. */
    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
               caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
               caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private val _availableUpdate = MutableStateFlow<com.example.util.UpdateCheckResult.UpdateAvailable?>(null)
    val availableUpdate: StateFlow<com.example.util.UpdateCheckResult.UpdateAvailable?> = _availableUpdate.asStateFlow()

    init {
        checkForAppUpdatesOnStartup()
    }

    fun checkForAppUpdatesOnStartup() {
        viewModelScope.launch {
            if (!settingsRepository.isAutoUpdateCheckEnabled.value) return@launch
            if (!isNetworkAvailable()) return@launch
            when (val result = com.example.util.AppVersionManager.checkOnlineUpdates()) {
                is com.example.util.UpdateCheckResult.UpdateAvailable -> {
                    _availableUpdate.value = result
                }
                else -> {
                    _availableUpdate.value = null
                }
            }
        }
    }

    fun dismissUpdateBanner() {
        _availableUpdate.value = null
    }

    val isFloatingOcrEnabled = settingsRepository.isFloatingOcrEnabled
    fun setFloatingOcrEnabled(enabled: Boolean) {
        settingsRepository.setFloatingOcrEnabled(enabled)
    }

    val edgePanelHeight = settingsRepository.edgePanelHeight
    fun setEdgePanelHeight(height: Int) {
        settingsRepository.setEdgePanelHeight(height)
    }

    val edgePanelThickness = settingsRepository.edgePanelThickness
    fun setEdgePanelThickness(thickness: Int) {
        settingsRepository.setEdgePanelThickness(thickness)
    }

    val edgePanelOpacity = settingsRepository.edgePanelOpacity
    fun setEdgePanelOpacity(opacity: Float) {
        settingsRepository.setEdgePanelOpacity(opacity)
    }

    val edgePanelSide = settingsRepository.edgePanelSide
    fun setEdgePanelSide(side: String) {
        settingsRepository.setEdgePanelSide(side)
    }

    val edgePanelYPercent = settingsRepository.edgePanelYPercent
    fun setEdgePanelYPercent(yPercent: Float) {
        settingsRepository.setEdgePanelYPercent(yPercent)
    }

    val hasDismissedOnboarding = settingsRepository.hasDismissedOnboarding
    fun dismissOnboarding() {
        settingsRepository.setHasDismissedOnboarding(true)
    }

    private val _showProactiveSurvey = MutableStateFlow(false)
    val showProactiveSurvey: StateFlow<Boolean> = _showProactiveSurvey.asStateFlow()

    fun dismissProactiveSurvey() {
        _showProactiveSurvey.value = false
        prefs.edit().putLong("survey_last_dismissed", System.currentTimeMillis()).apply()
    }

    fun checkProactiveSurvey() {
        val count = prefs.getInt("saved_items_count", 0) + 1
        prefs.edit().putInt("saved_items_count", count).apply()

        val surveySubmitted = prefs.getBoolean("survey_submitted", false)
        val lastDismissed = prefs.getLong("survey_last_dismissed", 0L)
        val now = System.currentTimeMillis()
        val cooldownPassed = (now - lastDismissed) > (7 * 24 * 60 * 60 * 1000L)

        if (!surveySubmitted && (count == 5 || (count > 5 && count % 15 == 0 && cooldownPassed))) {
            _showProactiveSurvey.value = true
        }
    }

    val forceDisableBlur = settingsRepository.forceDisableBlur
    fun setForceDisableBlur(disabled: Boolean) {
        settingsRepository.setForceDisableBlur(disabled)
    }

    val blurRadius = settingsRepository.blurRadius
    fun setBlurRadius(radius: Int) {
        settingsRepository.setBlurRadius(radius)
    }

    val blurOpacity = settingsRepository.blurOpacity
    fun setBlurOpacity(opacity: Float) {
        settingsRepository.setBlurOpacity(opacity)
    }

    val isRecentCapturesExpanded = settingsRepository.isRecentCapturesExpanded
    fun setRecentCapturesExpanded(expanded: Boolean) {
        settingsRepository.setRecentCapturesExpanded(expanded)
    }

    fun isApiKeySet(): Boolean {
        return settingsRepository.geminiApiKey.value.isNotEmpty()
    }

    private val _tmdbApiKey = MutableStateFlow(repository.getTmdbApiKey())
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    fun updateTmdbApiKey(key: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.setTmdbApiKey(key.trim()) }
                _tmdbApiKey.value = key.trim()
                showToast("TMDb API Key saved successfully")
            } catch (_: Exception) { showToast("Could not protect the key. Previous credential retained.") }
        }
    }

    // ----------------------------------------------------
    // STATE FLOWS
    // ----------------------------------------------------

    val allItems: StateFlow<List<SavedItem>> = repository.getAllItemsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customFolders: StateFlow<List<String>> = repository.getAllFoldersFlow().map { folders -> folders.filter { it != "Archive" } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customFolderEntities: StateFlow<List<com.example.data.local.CustomFolderEntity>> = repository.getAllFolderEntitiesFlow().map { folders -> folders.filter { it.name != "Archive" } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedFolder = MutableStateFlow("All")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Home Items (filtered by folder, independent of search)
    val homeItems: StateFlow<List<SavedItem>> = combine(
        allItems,
        _selectedFolder
    ) { items, folder ->
        val isItemArchived: (SavedItem) -> Boolean = { it.isArchived }
        if (folder != "All") {
            val systemCategory = SavedItemType.entries.find { it.displayName == folder }
            if (systemCategory != null) {
                // System folder filter - hide archived
                items.filter { it.type == systemCategory && !isItemArchived(it) }
            } else {
                if (folder == "Archive") {
                    // Show ONLY archived items
                    items.filter { isItemArchived(it) }
                } else {
                    // Custom folder filter
                    items.filter { it.folders.contains(folder) && !isItemArchived(it) }
                }
            }
        } else {
            // Hide archived items from "All" main feed
            items.filter { !isItemArchived(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Filtered Items (computes search query over folder items using ElasticFuzzySearchEngine)
    val filteredItems: StateFlow<List<SavedItem>> = combine(
        homeItems,
        _searchQuery
    ) { items, query ->
        if (query.isNotBlank()) {
            val metadataMatches = withContext(Dispatchers.Default) {
                com.example.util.ElasticFuzzySearchEngine.search(items, query)
            }
            val allowedIds = items.map { it.id }.toHashSet()
            val fileMatches = repository.searchNoteFiles(query).filter { it.id in allowedIds }
            (metadataMatches + fileMatches).distinctBy { it.id }
        } else {
            items
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ----------------------------------------------------
    // SELECTION MODE STATE & BULK ACTIONS
    // ----------------------------------------------------

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemIds: StateFlow<Set<String>> = _selectedItemIds.asStateFlow()

    fun enterSelectionMode(firstItemId: String) {
        _isSelectionMode.value = true
        _selectedItemIds.value = setOf(firstItemId)
    }

    fun toggleSelection(itemId: String) {
        val current = _selectedItemIds.value
        if (current.contains(itemId)) {
            val updated = current - itemId
            _selectedItemIds.value = updated
            if (updated.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            _selectedItemIds.value = current + itemId
            _isSelectionMode.value = true
        }
    }

    fun clearSelection() {
        _isSelectionMode.value = false
        _selectedItemIds.value = emptySet()
    }

    fun selectAll(itemIds: List<String>) {
        _selectedItemIds.value = itemIds.toSet()
        _isSelectionMode.value = itemIds.isNotEmpty()
    }

    fun deleteSelectedItems() {
        val selectedIds = _selectedItemIds.value
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            val itemsToDelete = allItems.value.filter { selectedIds.contains(it.id) }
            itemsToDelete.forEach { item ->
                repository.deleteItem(item)
            }
            clearSelection()
        }
    }

    fun updateOrderIndices(orderedItems: List<SavedItem>) {
        viewModelScope.launch {
            try {
                // orderIndex descending, so first item has highest orderIndex
                val size = orderedItems.size.toDouble()
                val itemsToUpdate = orderedItems.mapIndexed { index, item ->
                    item.copy(orderIndex = size - index)
                }
                repository.updateItems(itemsToUpdate)
            } catch (e: Exception) {
                Log.e("CobaltVM", "Failed to update order indices: ${e.message}")
            }
        }
    }

    fun reorderItem(itemToMove: SavedItem, prevItem: SavedItem?, nextItem: SavedItem?) {
        val newOrderIndex = when {
            prevItem != null && nextItem != null -> (prevItem.orderIndex + nextItem.orderIndex) / 2.0
            prevItem != null -> prevItem.orderIndex - 1000.0
            nextItem != null -> nextItem.orderIndex + 1000.0
            else -> itemToMove.orderIndex
        }
        val updatedItem = itemToMove.copy(orderIndex = newOrderIndex)
        viewModelScope.launch {
            try {
                repository.saveItem(updatedItem, null)
            } catch (e: Exception) {
                Log.e("CobaltVM", "Failed to reorder item: ${e.message}")
            }
        }
    }

    fun tagSelectedItems(folderName: String) {
        val selectedIds = _selectedItemIds.value
        if (selectedIds.isEmpty() || folderName.isBlank()) return
        viewModelScope.launch {
            val trimmed = folderName.trim()
            if (!customFolders.value.contains(trimmed)) {
                repository.addCustomFolder(trimmed)
            }
            val itemsToTag = allItems.value.filter { selectedIds.contains(it.id) }
            itemsToTag.forEach { item ->
                if (!item.folders.contains(trimmed)) {
                    val updatedFolders = item.folders + trimmed
                    repository.saveItem(item.copy(folders = updatedFolders))
                }
            }
            clearSelection()
        }
    }

    // ----------------------------------------------------
    // DETAIL VIEW STATE
    // ----------------------------------------------------

    private val _activeDetailItem = MutableStateFlow<SavedItem?>(null)
    val activeDetailItem: StateFlow<SavedItem?> = _activeDetailItem.asStateFlow()

    fun showDetailItem(item: com.example.data.model.SavedItem) {
        _activeDetailItem.value = item
        if (com.example.notes.NotePersistence.isNote(item)) {
            viewModelScope.launch {
                val loaded = repository.getItemForOpen(item.id)
                if (_activeDetailItem.value?.id == item.id && loaded != null) _activeDetailItem.value = loaded
            }
            return
        }


        val isMedia = item.type == com.example.data.model.SavedItemType.IMAGE || item.type == com.example.data.model.SavedItemType.VIDEO || item.type == com.example.data.model.SavedItemType.AUDIO
        val hasRemoteContent = !item.content.isNullOrBlank() && (item.content.startsWith("http://") || item.content.startsWith("https://"))
        if (isMedia && hasRemoteContent) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val cachedItem = repository.ensureMediaCached(item)
                    if (cachedItem != null && _activeDetailItem.value?.id == item.id) {
                        _activeDetailItem.value = cachedItem
                    }
                } catch (e: Exception) {
                    Log.e("CobaltVM", "Failed to cache media on open: ${e.message}")
                }
            }
        }
    }

    fun closeDetailItem() {
        _activeDetailItem.value = null
    }

    fun openItemById(itemId: String) {
        viewModelScope.launch {
            try {
                val item = repository.getItemForOpen(itemId)
                if (item != null) {
                    _activeDetailItem.value = item
                }
            } catch (e: Exception) {}
        }
    }

    private val _showMediaSearchBottomSheet = MutableStateFlow(false)
    val showMediaSearchBottomSheet: StateFlow<Boolean> = _showMediaSearchBottomSheet.asStateFlow()

    private val _mediaSearchResults = MutableStateFlow<List<MediaSearchResultItem>>(emptyList())
    val mediaSearchResults: StateFlow<List<MediaSearchResultItem>> = _mediaSearchResults.asStateFlow()

    private val _isSearchingMedia = MutableStateFlow(false)
    val isSearchingMedia: StateFlow<Boolean> = _isSearchingMedia.asStateFlow()

    fun openMediaSearchSheet() {
        _showMediaSearchBottomSheet.value = true
    }

    fun closeMediaSearchSheet() {
        _showMediaSearchBottomSheet.value = false
    }

    fun searchMedia(query: String) {
        if (query.isBlank()) {
            _mediaSearchResults.value = emptyList()
            _isSearchingMedia.value = false
            return
        }
        viewModelScope.launch {
            _isSearchingMedia.value = true
            try {
                if (!isNetworkAvailable()) {
                    postFeedback("Offline mode • Searching cached & anime items", com.example.util.FeedbackSeverity.WARNING)
                } else if (repository.getTmdbApiKey().isBlank()) {
                    postFeedback("TMDb API key missing • Searching anime via Jikan. Add TMDb key in Profile for Movies & TV", com.example.util.FeedbackSeverity.WARNING)
                }

                val results = kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                    repository.searchMedia(query.trim())
                }

                if (results != null) {
                    _mediaSearchResults.value = results
                } else {
                    _mediaSearchResults.value = emptyList()
                    postFeedback(
                        message = "Media search timed out • Check connection and try again",
                        severity = com.example.util.FeedbackSeverity.ERROR,
                        actionLabel = "Retry",
                        onAction = { searchMedia(query) }
                    )
                }
            } catch (e: Exception) {
                Log.e("CobaltVM", "searchMedia failed: ${e.message}")
                _mediaSearchResults.value = emptyList()
                postFeedback(
                    message = "Media search error: ${e.localizedMessage ?: "Unknown error"}",
                    severity = com.example.util.FeedbackSeverity.ERROR,
                    actionLabel = "Retry",
                    onAction = { searchMedia(query) }
                )
            } finally {
                _isSearchingMedia.value = false
            }
        }
    }

    fun saveMediaItem(item: MediaSearchResultItem, watchStatus: String = "Plan to Watch", selectedFolders: List<String> = emptyList()) {
        val foldersToUse = if (selectedFolders.isNotEmpty()) selectedFolders else listOf("Media")
        val newItem = SavedItem(
            id = item.id,
            type = SavedItemType.MEDIA,
            title = item.title,
            content = item.overview ?: "",
            thumbnailPath = item.posterUrl,
            backdropUrl = item.backdropUrl,
            mediaType = item.mediaType,
            watchStatus = watchStatus,
            genres = item.genres,
            watchProviders = item.watchProviders,
            trailerUrl = item.trailerUrl,
            folders = foldersToUse,
            releaseYear = item.releaseYear,
            rating = item.rating
        )

        // 1. Instantly close sheet and notify user
        closeMediaSearchSheet()

        // 2. Save locally and launch background enrichment
        viewModelScope.launch {
            try {
                val saved = repository.saveNewMediaItem(newItem)
                showToast("Saved ${item.title} to Cobalt!")
                repository.enrichMediaItemDetails(saved)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { showToast("Could not save this media item. Please retry.") }
        }
    }

    fun enrichMediaItem(item: SavedItem, saveToDb: Boolean = true) {
        if (item.type != SavedItemType.MEDIA) return
        viewModelScope.launch {
            val enriched = repository.enrichMediaItemDetails(item, saveToDb)
            if (!saveToDb) {
                _activeCaptureItem.value = enriched
            } else if (_activeDetailItem.value?.id == item.id) {
                _activeDetailItem.value = enriched
            }
        }
    }

    // ----------------------------------------------------
    // CAPTURE STATE
    // ----------------------------------------------------

    private val _activeCaptureItem = MutableStateFlow<SavedItem?>(null)
    val activeCaptureItem: StateFlow<SavedItem?> = _activeCaptureItem.asStateFlow()
    private val captureDrafts = mutableMapOf<SavedItemType, SavedItem>()

    // Bitmap of captured image (used for freehand drawing & cropping for Gemini OCR)
    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _isOcrLoading = MutableStateFlow(false)
    val isOcrLoading: StateFlow<Boolean> = _isOcrLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isMetadataExtracting = MutableStateFlow(false)
    val isMetadataExtracting: StateFlow<Boolean> = _isMetadataExtracting.asStateFlow()

    private val _metadataError = MutableStateFlow<String?>(null)
    val metadataError: StateFlow<String?> = _metadataError.asStateFlow()

    private val _saveProgress = MutableStateFlow<Float?>(null)
    val saveProgress: StateFlow<Float?> = _saveProgress.asStateFlow()

    private val _ocrError = MutableStateFlow<String?>(null)
    val ocrError: StateFlow<String?> = _ocrError.asStateFlow()

    fun clearOcrError() {
        _ocrError.value = null
    }

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _availableAudioModels = MutableStateFlow<List<String>>(emptyList())
    val availableAudioModels: StateFlow<List<String>> = _availableAudioModels.asStateFlow()

    private val _userFeedback = MutableStateFlow<com.example.util.UserFeedbackMessage?>(null)
    val userFeedback: StateFlow<com.example.util.UserFeedbackMessage?> = _userFeedback.asStateFlow()

    fun postFeedback(
        message: String,
        severity: com.example.util.FeedbackSeverity = com.example.util.FeedbackSeverity.INFO,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        _userFeedback.value = com.example.util.UserFeedbackMessage(
            message = message,
            severity = severity,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }

    fun dismissFeedback() {
        _userFeedback.value = null
    }

    fun showToast(message: String) {
        val severity = when {
            message.contains("failed", ignoreCase = true) || message.contains("error", ignoreCase = true) -> com.example.util.FeedbackSeverity.ERROR
            message.contains("no internet", ignoreCase = true) || message.contains("please sign in", ignoreCase = true) || message.contains("warning", ignoreCase = true) || message.contains("offline", ignoreCase = true) -> com.example.util.FeedbackSeverity.WARNING
            message.contains("success", ignoreCase = true) || message.contains("saved", ignoreCase = true) || message.contains("synced", ignoreCase = true) || message.contains("renamed", ignoreCase = true) -> com.example.util.FeedbackSeverity.SUCCESS
            else -> com.example.util.FeedbackSeverity.INFO
        }
        postFeedback(message, severity)
    }

    private val _extractedLinksToReview = MutableStateFlow<List<ExtractedLinkReview>>(emptyList())
    val extractedLinksToReview: StateFlow<List<ExtractedLinkReview>> = _extractedLinksToReview.asStateFlow()

    fun fetchAvailableModels(isUserTriggered: Boolean = false) {
        val apiKey = resolveValidApiKeyOrNull()
        if (apiKey == null) {
            if (isUserTriggered) {
                showToast("Please save a valid Gemini API Key first.")
            }
            return
        }
        if (!isNetworkAvailable()) {
            if (isUserTriggered) showToast("No internet connection. Cannot refresh models.")
            return
        }
        viewModelScope.launch {
            try {
                val response = com.example.data.remote.RetrofitClient.geminiService.listModels(apiKey)
                val rawModels = response.models?.map { it.name.replace("models/", "") } ?: emptyList()

                // Vision / OCR models: multimodal LLMs excluding non-vision specialized models
                val visionModels = rawModels.filter { modelName ->
                    modelName.contains("gemini", ignoreCase = true) &&
                    !modelName.contains("embedding", ignoreCase = true) &&
                    !modelName.contains("embed", ignoreCase = true) &&
                    !modelName.contains("tts", ignoreCase = true) &&
                    !modelName.contains("transcribe", ignoreCase = true) &&
                    !modelName.contains("imagen", ignoreCase = true)
                }.sortedWith(
                    compareByDescending<String> { it.contains("flash-lite") }
                        .thenByDescending { it.contains("2.5") }
                        .thenByDescending { it.contains("2.0") }
                        .thenByDescending { it.contains("1.5") }
                        .thenByDescending { it.contains("flash") }
                )
                _availableModels.value = visionModels

                if (visionModels.isNotEmpty()) {
                    val currentSelected = settingsRepository.selectedModel.value
                    if (currentSelected.isBlank() || !visionModels.contains(currentSelected)) {
                        val defaultModel = visionModels.firstOrNull { it.contains("2.5-flash-lite") }
                            ?: visionModels.firstOrNull { it.contains("2.0-flash-lite") }
                            ?: visionModels.firstOrNull { it.contains("flash-lite") }
                            ?: visionModels.firstOrNull { it.contains("2.5-flash") }
                            ?: visionModels.firstOrNull { it.contains("2.0-flash") }
                            ?: visionModels.firstOrNull { it.contains("1.5-flash") }
                            ?: visionModels.firstOrNull { it.contains("flash") }
                            ?: visionModels.first()
                        settingsRepository.setSelectedModel(defaultModel)
                    }
                }

                // Audio transcribe models: dedicated speech-to-text models or audio-capable multimodal flash models
                val audioModels = rawModels.filter { modelName ->
                    modelName.contains("gemini", ignoreCase = true) &&
                    !modelName.contains("embedding", ignoreCase = true) &&
                    !modelName.contains("embed", ignoreCase = true) &&
                    !modelName.contains("tts", ignoreCase = true) &&
                    !modelName.contains("imagen", ignoreCase = true) &&
                    (modelName.contains("transcribe", ignoreCase = true) ||
                     modelName.contains("flash", ignoreCase = true) ||
                     modelName.contains("pro", ignoreCase = true))
                }.sortedWith(
                    compareByDescending<String> { it.contains("transcribe", ignoreCase = true) }
                        .thenByDescending { it.contains("2.5") }
                        .thenByDescending { it.contains("2.0") }
                        .thenByDescending { it.contains("1.5") }
                        .thenByDescending { it.contains("flash") }
                )
                _availableAudioModels.value = audioModels

                if (audioModels.isNotEmpty()) {
                    val currentAudioSelected = settingsRepository.selectedAudioModel.value
                    if (currentAudioSelected.isBlank() || !audioModels.contains(currentAudioSelected)) {
                        val defaultAudio = audioModels.firstOrNull { it.contains("transcribe", ignoreCase = true) }
                            ?: audioModels.firstOrNull { it.contains("2.5-flash") }
                            ?: audioModels.firstOrNull { it.contains("2.0-flash") }
                            ?: audioModels.firstOrNull { it.contains("1.5-flash") }
                            ?: audioModels.firstOrNull { it.contains("flash") }
                            ?: audioModels.first()
                        settingsRepository.setSelectedAudioModel(defaultAudio)
                    }
                }

                if (isUserTriggered) {
                    showToast("Refreshed ${visionModels.size} vision and ${audioModels.size} audio models.")
                }
            } catch (e: Exception) {
                Log.e("CobaltVM", "Failed to fetch models: ${e.message}")
                if (isUserTriggered) {
                    showToast("Failed to refresh models: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    // Temporary bytes for the active capture (pending local save)
    private var pendingMediaBytes: ByteArray? = null

    // ----------------------------------------------------
    // INITIAL LOADING STATE
    // ----------------------------------------------------

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val prefs = context.getSharedPreferences("cobalt_prefs", android.content.Context.MODE_PRIVATE)

    init {
        _isInitialLoading.value = false

        // Auto-fetch models when API key is available or updated
        viewModelScope.launch {
            settingsRepository.geminiApiKey.collect { key ->
                if (key.isNotBlank()) {
                    fetchAvailableModels(isUserTriggered = false)
                }
            }
        }
    }

    // ----------------------------------------------------
    // SEARCH & FILTER ACTIONS
    // ----------------------------------------------------

    fun setFolderFilter(folder: String) {
        _selectedFolder.value = folder
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ----------------------------------------------------
    // ----------------------------------------------------

    // ----------------------------------------------------
    // CAPTURE FLOW
    // ----------------------------------------------------

    fun startManualCapture(type: SavedItemType, initialFolders: List<String> = emptyList()) {
        _capturedBitmap.value = null
        pendingMediaBytes = null
        _extractedLinksToReview.value = emptyList()
        _isMetadataExtracting.value = false
        _metadataError.value = null
        editOriginalId = null
        _isEditingItem.value = false
        captureDrafts.clear()
        _activeCaptureItem.value = SavedItem(
            type = type,
            title = "",
            content = if (type == SavedItemType.CODE) "// Code snippet" else "",
            folders = initialFolders
        )
        _activeCaptureItem.value?.let { captureDrafts[type] = it }
    }

    fun cancelCapture() {
        fileEditor.close()
        noteTextRevision++
        noteTextJob?.cancel()
        _noteInputBusy.value = false
        saveAfterNoteInput = false

        _activeCaptureItem.value = null
        _capturedBitmap.value = null
        pendingMediaBytes = null
        _extractedLinksToReview.value = emptyList()
        _isMetadataExtracting.value = false
        _metadataError.value = null
        editOriginalId = null
        _isEditingItem.value = false
        captureDrafts.clear()
    }

    /**
     * Process content shared from other apps via Share Target (Intent.SEND)
     */
    fun handleSharedIntent(mimeType: String?, textContent: String?, mediaUri: Uri?, subject: String? = null) {
        viewModelScope.launch {
            editOriginalId = null
            _isEditingItem.value = false
            captureDrafts.clear()
            _capturedBitmap.value = null
            pendingMediaBytes = null
            _isMetadataExtracting.value = false
            _metadataError.value = null

            if (mimeType == null) return@launch
            if (mediaUri != null && (com.example.notes.TextFiles.accepts(null, mimeType) || mimeType == "application/octet-stream")) {
                importMarkdown(mediaUri)
                return@launch
            }

            when {
                // 1. Text Shared
                mimeType.startsWith("text/") && textContent != null -> {
                    val url = withContext(Dispatchers.Default) {
                        com.example.utils.ClipboardUrlHelper.parseUrl(textContent)
                    }

                    if (url != null) {
                        // Title might be in subject, or in the remaining text
                        val title = subject?.takeIf { it.isNotBlank() } ?: "Shared Link"
                        _activeCaptureItem.value = SavedItem(
                            type = SavedItemType.LINK,
                            title = title,
                            content = url
                        )
                        _activeCaptureItem.value?.let { captureDrafts[SavedItemType.LINK] = it }
                        fetchLinkPreviewForActiveItem(url)
                    } else {
                        // Check if looks like code
                        val isCode = withContext(Dispatchers.Default) {
                            textContent.contains("class ") || textContent.contains("fun ") || textContent.contains("{") || textContent.contains("import ")
                        }
                        _activeCaptureItem.value = SavedItem(
                            type = if (isCode) SavedItemType.CODE else SavedItemType.TEXT,
                            title = subject?.takeIf { it.isNotBlank() } ?: if (isCode) "Captured Code Snippet" else "Captured Text Note",
                            content = textContent
                        )
                        _activeCaptureItem.value?.let { captureDrafts[it.type] = it }
                    }
                }

                // 2. Image Shared
                mimeType.startsWith("image/") && mediaUri != null -> {
                    try {
                        val bytes = readUriBytes(mediaUri) ?: return@launch
                        pendingMediaBytes = bytes
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        _capturedBitmap.value = bitmap

                        // Save temporarily to cache file for display
                        val localPath = repository.saveToLocalCache("shared_img_${UUID.randomUUID()}.jpg", bytes)

                        _activeCaptureItem.value = SavedItem(
                            type = SavedItemType.IMAGE,
                            title = "Shared Screenshot/Image",
                            content = localPath,
                            thumbnailPath = localPath
                        )
                        _activeCaptureItem.value?.let { captureDrafts[SavedItemType.IMAGE] = it }
                    } catch (e: Exception) {
                        Log.e("CobaltVM", "Failed to load shared image: ${e.message}")
                    }
                }

                // 3. Video Shared
                mimeType.startsWith("video/") && mediaUri != null -> {
                    try {
                        val bytes = readUriBytes(mediaUri) ?: return@launch
                        pendingMediaBytes = bytes

                        // Save to cache file
                        val localPath = repository.saveToLocalCache("shared_vid_${UUID.randomUUID()}.mp4", bytes)

                        _activeCaptureItem.value = SavedItem(
                            type = SavedItemType.VIDEO,
                            title = "Shared Video",
                            content = localPath,
                            thumbnailPath = localPath // we can use localPath to play/display
                        )
                        _activeCaptureItem.value?.let { captureDrafts[SavedItemType.VIDEO] = it }
                    } catch (e: Exception) {
                        Log.e("CobaltVM", "Failed to load shared video: ${e.message}")
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // REGION OCR / GEMINI API
    // ----------------------------------------------------

    fun startFloatingOcrCapture(bitmap: Bitmap) {
        captureDrafts.clear()
        _capturedBitmap.value = bitmap
        _activeCaptureItem.value = SavedItem(
            type = SavedItemType.IMAGE,
            title = "Floating OCR Capture",
            content = "",
            thumbnailPath = ""
        )
        _activeCaptureItem.value?.let { captureDrafts[it.type] = it }
        _extractedLinksToReview.value = emptyList()
        _isOcrLoading.value = false
        _ocrError.value = null
    }

    fun transcribeAudioMemo(file: java.io.File) {
        val currentItem = _activeCaptureItem.value ?: return
        _isOcrLoading.value = true
        _ocrError.value = null

        viewModelScope.launch {
            try {
                if (!file.exists() || file.length() < 1024) {
                    Log.w("CobaltVM", "transcribeAudioMemo: File too small or missing (${file.length()} bytes)")
                    _ocrError.value = "Recording was too short or empty. Please record for at least 1-2 seconds."
                    _isOcrLoading.value = false
                    return@launch
                }

                val bytes = file.readBytes()
                pendingMediaBytes = bytes
                val localPath = repository.saveToLocalCache("audio_${java.util.UUID.randomUUID()}.mp4", bytes)
                Log.d("CobaltVM", "Audio cached at $localPath (${bytes.size} bytes)")

                // Attach thumbnailPath immediately so the recording can be played/saved regardless of API result
                val timeStamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                _activeCaptureItem.value = _activeCaptureItem.value?.copy(
                    thumbnailPath = localPath,
                    title = _activeCaptureItem.value?.title?.takeIf { it.isNotBlank() && it != "New Item" } ?: "Voice Memo ($timeStamp)"
                )
                _activeCaptureItem.value?.let { captureDrafts[SavedItemType.AUDIO] = it }

                val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val apiKey = resolveValidApiKeyOrNull()
                if (apiKey == null) {
                    Log.w("CobaltVM", "No Gemini API key configured for audio transcription.")
                    _ocrError.value = "Please save a valid Gemini API Key first."
                    _isOcrLoading.value = false
                    return@launch
                }

                val audioModel = settingsRepository.selectedAudioModel.value.takeIf { it.isNotBlank() }
                    ?: settingsRepository.selectedModel.value.takeIf { it.isNotBlank() }
                    ?: "gemini-2.0-flash"
                Log.d("CobaltVM", "Requesting transcription with model: $audioModel")

                val result = repository.extractTextFromAudio(
                    base64Audio = base64Data,
                    apiKey = apiKey,
                    model = audioModel
                )

                if (result != null && !result.startsWith("Error")) {
                    val extractedTitle = extractTitleFromMarkdown(result)
                    _activeCaptureItem.value = _activeCaptureItem.value?.copy(
                        content = result,
                        title = extractedTitle ?: _activeCaptureItem.value?.title ?: "Voice Memo ($timeStamp)",
                        thumbnailPath = localPath
                    )
                    _activeCaptureItem.value?.let { captureDrafts[SavedItemType.AUDIO] = it }
                    Log.d("CobaltVM", "Audio transcription successful: title=${_activeCaptureItem.value?.title}")
                } else {
                    Log.w("CobaltVM", "Audio transcription returned error/no-speech: $result")
                    _ocrError.value = result ?: "Failed to transcribe audio."
                }
            } catch (e: Exception) {
                Log.e("CobaltVM", "transcribeAudioMemo error: ${e.message}", e)
                _ocrError.value = e.localizedMessage ?: "Unknown error"
            } finally {
                _isOcrLoading.value = false
            }
        }
     }

    fun formatSpeechWithGemini(speechText: String) {
        val currentItem = _activeCaptureItem.value ?: return
        _isOcrLoading.value = true
        _ocrError.value = null

        viewModelScope.launch {
            try {
                val apiKey = resolveValidApiKeyOrNull()
                if (apiKey == null) {
                    _ocrError.value = "Please save a valid Gemini API Key first."
                    _isOcrLoading.value = false
                    return@launch
                }

                val result = repository.formatSpeechText(
                    speechText = speechText,
                    apiKey = apiKey,
                    model = settingsRepository.selectedModel.value
                )

                if (result != null && !result.startsWith("Error")) {
                    val extractedTitle = extractTitleFromMarkdown(result)
                    _activeCaptureItem.value = _activeCaptureItem.value?.copy(
                        content = result,
                        title = extractedTitle ?: _activeCaptureItem.value?.title ?: "Formatted Voice Memo"
                    )
                    _activeCaptureItem.value?.let { captureDrafts[it.type] = it }
                } else {
                    _ocrError.value = result ?: "Failed to format speech."
                }
            } catch (e: Exception) {
                _ocrError.value = e.localizedMessage ?: "Unknown error"
            } finally {
                _isOcrLoading.value = false
            }
        }
    }

    private fun extractTitleFromMarkdown(markdown: String): String? {
        val line = markdown.lines().firstOrNull { it.trim().startsWith("# ") }
        return line?.trim()?.removePrefix("#")?.trim()
    }

    private fun resolveValidApiKeyOrNull(): String? {
        val userKey = settingsRepository.geminiApiKey.value
        return if (userKey.isNotEmpty()) userKey else null
    }

    private fun parseGeminiOcrResult(raw: String): Pair<String, List<Pair<String, String>>> {
        var parsedExtractedText = raw
        val urlsList = mutableListOf<Pair<String, String>>()
        try {
            var cleanText = raw.trim()
            val startRegex = Regex("^```(?:json)?\\s*")
            val endRegex = Regex("\\s*```$")
            cleanText = cleanText.replace(startRegex, "").replace(endRegex, "").trim()

            val jsonObject = org.json.JSONObject(cleanText)
            parsedExtractedText = jsonObject.optString("extractedText", raw)
            val urlsArray = jsonObject.optJSONArray("urls")
            if (urlsArray != null) {
                for (i in 0 until urlsArray.length()) {
                    val urlObj = urlsArray.getJSONObject(i)
                    val url = urlObj.optString("url", "")
                    val desc = urlObj.optString("description", "")
                    if (url.isNotBlank()) {
                        urlsList.add(Pair(url, desc))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("CobaltVM", "Failed to parse OCR JSON, falling back to raw text.")
        }
        return Pair(parsedExtractedText, urlsList)
    }

    private suspend fun buildItemWithFetchedMetadata(item: SavedItem): SavedItem {
        if (item.type != SavedItemType.LINK) return item
        val metadata = repository.fetchLinkMetadata(item.content)
        val finalDescription = metadata.description?.takeIf { it.isNotBlank() }
            ?: item.linkDescription?.takeIf { it.isNotBlank() }
        val finalTitle = metadata.title?.takeIf { it.isNotBlank() }
            ?: item.title.takeIf { it.isNotBlank() }
            ?: "Shared Link"
        return item.copy(
            linkTitle = metadata.title ?: item.linkTitle,
            linkDescription = finalDescription,
            linkImage = metadata.imageUrl ?: item.linkImage,
            title = finalTitle
        )
    }

    fun performFullImageOcr(uri: android.net.Uri, context: android.content.Context) {
        val currentItem = _activeCaptureItem.value ?: return
        _isOcrLoading.value = true
        _ocrError.value = null
        _activeCaptureItem.value = currentItem.copy(extractedText = null)
        _extractedLinksToReview.value = emptyList()

        viewModelScope.launch {
            try {
                // Use Coil to load the image robustly. This handles content://, file://, raw paths, and http:// / https:// (remote media)
                val loader = coil.Coil.imageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false) // software bitmap is required for OCR / region selection
                    .build()
                val result = loader.execute(request)
                val bitmap = if (result is coil.request.SuccessResult) {
                    (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                } else {
                    null
                }

                if (bitmap == null) {
                    _ocrError.value = "Failed to decode image for OCR."
                    _isOcrLoading.value = false
                    return@launch
                }

                val apiKey = resolveValidApiKeyOrNull()
                if (apiKey == null) {
                    _ocrError.value = "Please save a valid Gemini API Key first."
                    _isOcrLoading.value = false
                    return@launch
                }

                val resultText = repository.extractTextFromRegion(
                    bitmap = bitmap,
                    x = 0, y = 0, width = bitmap.width, height = bitmap.height,
                    apiKey = apiKey,
                    model = settingsRepository.selectedModel.value,
                    sensitivity = "High",
                    customPrompt = settingsRepository.customOcrPrompt.value
                )

                if (resultText?.startsWith("Error") == true) {
                    _ocrError.value = com.example.util.GoogleApiErrorMapper.formatUserFriendlyError(resultText)
                } else if (resultText != null) {
                    val (parsedExtractedText, urlsList) = parseGeminiOcrResult(resultText)

                    _activeCaptureItem.value = _activeCaptureItem.value?.copy(
                        extractedText = parsedExtractedText
                    )

                    val reviews = urlsList.map { (urlStr, desc) ->
                        ExtractedLinkReview(
                            originalUrl = urlStr,
                            url = urlStr,
                            description = desc,
                            isSelected = true
                        )
                    }
                    _extractedLinksToReview.value = reviews
                } else {
                    _ocrError.value = "No readable text or hyperlinks found. Try selecting a clearer region."
                }
            } catch (e: Exception) {
                _ocrError.value = com.example.util.GoogleApiErrorMapper.formatException(e)
            } finally {
                _isOcrLoading.value = false
            }
        }
    }

    fun performRegionOcr(x: Int, y: Int, width: Int, height: Int) {
        val bitmap = _capturedBitmap.value ?: return
        val currentItem = _activeCaptureItem.value ?: return

        _isOcrLoading.value = true
        _ocrError.value = null

        // Clear previous extraction results to ensure clean state and hide old bottom panel immediately during extraction
        _activeCaptureItem.value = currentItem.copy(extractedText = null)
        _extractedLinksToReview.value = emptyList()
        viewModelScope.launch {
            try {
                val apiKey = resolveValidApiKeyOrNull()
                if (apiKey == null) {
                    _ocrError.value = "Please save a valid Gemini API Key first."
                    _isOcrLoading.value = false
                    return@launch
                }
                val model = settingsRepository.selectedModel.value
                val sensitivity = settingsRepository.ocrSensitivity.value
                val customPrompt = settingsRepository.customOcrPrompt.value
                val resultText = repository.extractTextFromRegion(bitmap, x, y, width, height, apiKey, model, sensitivity, customPrompt)
                if (resultText?.startsWith("Error") == true) {
                    _ocrError.value = com.example.util.GoogleApiErrorMapper.formatUserFriendlyError(resultText)
                } else if (resultText != null) {
                    val (parsedExtractedText, urlsList) = parseGeminiOcrResult(resultText)

                    val isHttpLink = parsedExtractedText.startsWith("http://") || parsedExtractedText.startsWith("https://")
                    _activeCaptureItem.value = currentItem.copy(
                        extractedText = parsedExtractedText,
                        // If it's a raw URL (fallback case)
                        title = if (isHttpLink) "Extracted Shared URL" else currentItem.title,
                        content = if (isHttpLink) parsedExtractedText else currentItem.content,
                        type = if (isHttpLink) com.example.data.model.SavedItemType.LINK else currentItem.type
                    )

                    // Stage extracted URLs for review instead of auto-saving
                    val reviews = urlsList.map { (urlStr, desc) ->
                        ExtractedLinkReview(
                            originalUrl = urlStr,
                            url = urlStr,
                            description = desc,
                            isSelected = true
                        )
                    }
                    _extractedLinksToReview.value = reviews
                } else {
                    _ocrError.value = "No readable text or hyperlinks found. Try selecting a clearer region."
                }
            } catch (e: Exception) {
                Log.e("CobaltVM", "OCR region extraction failed: ${e.message}")
                _ocrError.value = com.example.util.GoogleApiErrorMapper.formatException(e)
            } finally {
                _isOcrLoading.value = false
            }
        }
    }

    fun updateExtractedLink(id: String, updatedUrl: String) {
        _extractedLinksToReview.value = _extractedLinksToReview.value.map {
            if (it.id == id) it.copy(url = updatedUrl) else it
        }
    }

    fun toggleExtractedLinkSelection(id: String, isSelected: Boolean) {
        _extractedLinksToReview.value = _extractedLinksToReview.value.map {
            if (it.id == id) it.copy(isSelected = isSelected) else it
        }
    }

    private var extractedLinkSaveJob: kotlinx.coroutines.Job? = null

    fun confirmAndSaveExtractedLinks(selectedFolders: List<String> = emptyList()) {
        if (extractedLinkSaveJob?.isActive == true) return
        val linksToSave = _extractedLinksToReview.value.filter { it.isSelected && it.url.isNotBlank() }
        extractedLinkSaveJob = viewModelScope.launch(Dispatchers.IO) {
            for (reviewItem in linksToSave) {
                kotlin.coroutines.coroutineContext.ensureActive()
                try {
                    val newId = java.util.UUID.randomUUID().toString()
                    val foldersToUse = selectedFolders
                    val initialTitle = if (reviewItem.description.isNotBlank()) reviewItem.description else reviewItem.url

                    val initialItem = com.example.data.model.SavedItem(
                        id = newId,
                        type = com.example.data.model.SavedItemType.LINK,
                        title = initialTitle,
                        content = reviewItem.url,
                        folders = foldersToUse,
                        linkDescription = reviewItem.description
                    )

                    // 1. Save IMMEDIATELY to DB so it appears instantly
                    val savedItem = repository.saveItem(initialItem, null)
                    _extractedLinksToReview.update { items -> items.filterNot { it.id == reviewItem.id } }
                    withContext(Dispatchers.Main) {
                        com.example.widget.WidgetUpdater.update(context)
                    }

                    // 2. Fetch metadata in background and update DB
                    try {
                        val meta = repository.fetchLinkMetadata(reviewItem.url)
                        val realDesc = meta.description?.takeIf { it.isNotBlank() }
                        val realTitle = meta.title?.takeIf { it.isNotBlank() }
                        val realImage = meta.imageUrl?.takeIf { it.isNotBlank() }

                        if (realTitle != null || realDesc != null || realImage != null) {
                            val updatedItem = savedItem.copy(
                                title = realTitle ?: savedItem.title,
                                linkTitle = realTitle ?: savedItem.linkTitle,
                                linkDescription = realDesc ?: reviewItem.description.takeIf { it.isNotBlank() },
                                linkImage = realImage ?: savedItem.linkImage
                            )
                            repository.saveItem(updatedItem, null)
                            withContext(Dispatchers.Main) {
                                com.example.widget.WidgetUpdater.update(context)
                            }
                        }
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        // The durable link remains available without optional metadata.
                    }
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    showToast("Could not save a selected link. It remains in the review list for retry.")
                }
            }
        }
    }

    // ----------------------------------------------------
    // SAVING AND DELETING CO-ORDINATION
    // ----------------------------------------------------

    private var metadataFetchJob: kotlinx.coroutines.Job? = null

    fun fetchLinkPreviewForActiveItem(url: String) {
        val currentItem = _activeCaptureItem.value ?: return
        if (currentItem.type != SavedItemType.LINK) return
        if (url.isBlank()) return

        metadataFetchJob?.cancel()
        _isMetadataExtracting.value = true
        _metadataError.value = null

        metadataFetchJob = viewModelScope.launch {
            try {
                val metadata = repository.fetchLinkMetadata(url)
                val updatedItem = _activeCaptureItem.value ?: return@launch
                if (updatedItem.content == url) {
                    val finalDescription = metadata.description?.takeIf { it.isNotBlank() }
                        ?: updatedItem.linkDescription?.takeIf { it.isNotBlank() }
                    _activeCaptureItem.value = updatedItem.copy(
                        linkTitle = metadata.title ?: updatedItem.linkTitle,
                        linkDescription = finalDescription,
                        linkImage = metadata.imageUrl ?: updatedItem.linkImage,
                        title = metadata.title?.takeIf { it.isNotBlank() } ?: updatedItem.title
                    )

                    if (metadata.title.isNullOrBlank() && metadata.description.isNullOrBlank() && metadata.imageUrl.isNullOrBlank()) {
                        _metadataError.value = "No metadata details could be parsed from this page."
                    }
                }
            } catch (e: Exception) {
                Log.e("CobaltVM", "Failed to fetch link preview: ${e.message}")
                _metadataError.value = "Extraction failed: ${e.localizedMessage ?: "Network error or invalid link"}"
            } finally {
                _isMetadataExtracting.value = false
            }
        }
    }

    private var editOriginalId: String? = null
    private val _isEditingItem = MutableStateFlow(false)
    val isEditingItem: StateFlow<Boolean> = _isEditingItem.asStateFlow()

    private val _lastDeletedItemForUndo = MutableStateFlow<SavedItem?>(null)
    val lastDeletedItemForUndo: StateFlow<SavedItem?> = _lastDeletedItemForUndo.asStateFlow()

    fun clearUndoToast() {
        _lastDeletedItemForUndo.value = null
    }

    fun triggerUndoToast(item: SavedItem) {
        _lastDeletedItemForUndo.value = item
    }

    fun deleteEditingItem() {
        val targetId = editOriginalId
        val itemToDelete = targetId?.let { id -> allItems.value.find { it.id == id } } ?: _activeCaptureItem.value
        if (itemToDelete != null) {
            deleteSavedItem(itemToDelete)
        }
        cancelCapture()
    }

    fun startEditItem(item: SavedItem) {
        if (com.example.notes.NotePersistence.isNote(item) && (!item.bodyLoaded || com.example.notes.NoteEditorWindows.requiresWindow(item.content))) {
            viewModelScope.launch {
                val loaded = repository.getItemForOpen(item.id)
                if (loaded != null && loaded.bodyLoaded && !loaded.bodyMissing && !com.example.notes.NoteEditorWindows.requiresWindow(loaded.content)) startEditItem(loaded)
                else if (loaded != null && !loaded.bodyMissing) {
                    _capturedBitmap.value = null; pendingMediaBytes = null
                    editOriginalId = loaded.id; _isEditingItem.value = true
                    _activeCaptureItem.value = loaded.copy(content = "", bodyLoaded = false)
                    fileEditor.open(loaded)
                } else showToast("The body is unavailable. Original and recovery data are retained.")
            }
            return
        }

        _capturedBitmap.value = null
        pendingMediaBytes = null
        _extractedLinksToReview.value = emptyList()
        _isMetadataExtracting.value = false
        _metadataError.value = null
        editOriginalId = item.id
        _isEditingItem.value = true
        _activeCaptureItem.value = if (item.type == SavedItemType.TEXT || item.type == SavedItemType.CODE) {
            item.copy(extractedText = null)
        } else {
            item
        }

        if (item.type == SavedItemType.IMAGE && item.content.isNotBlank()) {
            _isOcrLoading.value = true
            viewModelScope.launch {
                try {
                    val loader = coil.Coil.imageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(item.content)
                        .allowHardware(false) // software bitmap is required for OCR / region selection
                        .build()
                    val result = loader.execute(request)
                    if (result is coil.request.SuccessResult) {
                        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        _capturedBitmap.value = bitmap
                    }
                } catch (e: Exception) {
                    Log.e("CobaltVM", "Failed to pre-load image bitmap for edit: ${e.message}")
                } finally {
                    _isOcrLoading.value = false
                }
            }
        }
    }

    fun handleMediaSelected(uri: android.net.Uri, type: SavedItemType) {
        viewModelScope.launch {
            try {
                if (type == SavedItemType.VIDEO) {
                    val cacheFile = File(context.cacheDir, "media_${UUID.randomUUID()}.mp4")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(cacheFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    // Extract thumbnail frame safely on IO dispatcher
                    val retriever = MediaMetadataRetriever()
                    val frameBitmap = withContext(Dispatchers.IO) {
                        try {
                            retriever.setDataSource(context, uri)
                            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } catch (e: Exception) {
                            Log.e("CobaltVM", "Could not extract video thumbnail: ${e.message}")
                            null
                        } finally {
                            try { retriever.release() } catch (_: Exception) {}
                        }
                    }
                    _capturedBitmap.value = frameBitmap

                    // Save extracted thumbnail bitmap to cache file for instant thumbnail display
                    val thumbPath = if (frameBitmap != null) {
                        withContext(Dispatchers.IO) {
                            try {
                                val thumbFile = File(context.cacheDir, "thumb_${UUID.randomUUID()}.jpg")
                                FileOutputStream(thumbFile).use { fos ->
                                    frameBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                                }
                                thumbFile.absolutePath
                            } catch (_: Exception) {
                                cacheFile.absolutePath
                            }
                        }
                    } else {
                        cacheFile.absolutePath
                    }

                    pendingMediaBytes = null
                    val finalPath = if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else uri.toString()
                    updateActiveCaptureItem { it.copy(content = finalPath, thumbnailPath = thumbPath, type = type) }
                } else {
                    val bytes = readUriBytes(uri)
                    if (bytes != null) {
                        pendingMediaBytes = bytes
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        _capturedBitmap.value = bitmap

                        val localPath = repository.saveToLocalCache("media_${UUID.randomUUID()}.jpg", bytes)
                        updateActiveCaptureItem { it.copy(content = localPath, thumbnailPath = localPath, type = type) }
                    } else {
                        updateActiveCaptureItem { it.copy(content = uri.toString(), thumbnailPath = uri.toString(), type = type) }
                    }
                }
            } catch (e: Exception) {
                Log.e("CobaltVM", "Error in handleMediaSelected: ${e.message}")
                updateActiveCaptureItem { it.copy(content = uri.toString(), thumbnailPath = uri.toString(), type = type) }
            }
        }
    }

    private val _noteInputBusy = MutableStateFlow(false)
    val noteInputBusy = _noteInputBusy.asStateFlow()
    data class QuickDraftState(
        val ready: Boolean = false, val text: String = "", val busy: Boolean = false, val title: String = "",
        val snapshot: com.example.notes.QuickNoteDraftStore.Snapshot? = null, val error: String? = null, val pending: Boolean = false
    )
    private val _quickDraft = MutableStateFlow(QuickDraftState())
    val quickDraft = _quickDraft.asStateFlow()
    data class MarkdownImportState(val running: Boolean = false, val bytes: Long = 0, val message: String? = null,
        val canSplit: Boolean = false, val partsSaved: Int = 0)
    private val _markdownImport = MutableStateFlow(MarkdownImportState())
    val markdownImport = _markdownImport.asStateFlow()
    private var markdownImportJob: kotlinx.coroutines.Job? = null
    private var markdownImportSource: Uri? = null
    private var markdownImportSession = UUID.randomUUID().toString()

    fun cancelMarkdownImport() { markdownImportJob?.cancel() }

    fun importMarkdown(uri: Uri) {
        if (markdownImportJob?.isActive == true) return
        markdownImportSource = uri
        markdownImportSession = UUID.randomUUID().toString()
        _markdownImport.value = MarkdownImportState(running = true)
        markdownImportJob = viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    com.example.notes.TextFileImporter(context).importFile(uri) { bytes ->
                        _markdownImport.value = MarkdownImportState(running = true, bytes = bytes)
                    }
                }
                val warning = if (com.example.notes.NoteSizePolicy.needsWarning(saved.byteLength)) " Above 50 MB; large-file mode is recommended." else ""
                _markdownImport.value = MarkdownImportState(bytes = saved.byteLength, message = "Text document imported.$warning")
                showDetailItem(saved)
                com.example.widget.WidgetUpdater.update(context)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                _markdownImport.value = MarkdownImportState(message = "Import cancelled. The source document was not changed.")
                throw cancel
            } catch (error: Exception) {
                _markdownImport.value = MarkdownImportState(canSplit = error is com.example.notes.NoteSizeExceededException,
                    message = "Import failed. New notes must be at most 55 MB; split larger files. The source document was not changed.")
            }
        }
    }

    fun importSharedTextFiles(uris: List<Uri>) {
        if (markdownImportJob?.isActive == true) { showToast("An import is already running. Please retry afterwards."); return }
        if (uris.size > 100) { showToast("Share at most 100 text documents. Nothing was imported."); return }
        markdownImportJob = viewModelScope.launch {
            var saved = 0
            var failed = 0
            for (uri in uris.distinct()) {
                try {
                    val item = com.example.notes.TextFileImporter(context).importFile(uri)
                    saved++
                    if (uris.size == 1) showDetailItem(item)
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { failed++ }
            }
            com.example.widget.WidgetUpdater.update(context)
            showToast("$saved text document(s) imported; $failed unavailable or unsupported. Originals retained.")
        }
    }

    fun splitMarkdownImport() {
        val uri = markdownImportSource ?: return
        if (markdownImportJob?.isActive == true) return
        val session = markdownImportSession
        _markdownImport.value = MarkdownImportState(running = true)
        markdownImportJob = viewModelScope.launch {
            var partsSaved = 0
            var bytesSaved = 0L
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { raw ->
                        com.example.notes.TextFiles.openUtf8(raw).use { input ->
                        val prepared = com.example.notes.NoteImport.prepare(input, "Imported note")
                        prepared.body.use { body ->
                            val parts = com.example.notes.Utf8NoteParts(body)
                            val persistence = com.example.notes.NotePersistence(context)
                            while (true) {
                                kotlin.coroutines.coroutineContext.ensureActive()
                                val part = parts.next() ?: break
                                val index = partsSaved + 1
                                val id = UUID.nameUUIDFromBytes("$session:$index".toByteArray(Charsets.UTF_8)).toString()
                                val saved = part.use { source -> persistence.importMarkdown(prepared.note.copy(id = id,
                                    title = "${prepared.note.title} (part $index)"), source) { bytes ->
                                    _markdownImport.value = MarkdownImportState(running = true, bytes = bytesSaved + bytes, partsSaved = partsSaved)
                                } }
                                partsSaved++
                                bytesSaved += saved.byteLength
                            }
                        }
                        }
                    } ?: throw java.io.IOException("Cannot read source document")
                }
                _markdownImport.value = MarkdownImportState(bytes = bytesSaved, partsSaved = partsSaved,
                    message = "Saved $partsSaved Markdown parts. The source document was not changed.")
                com.example.widget.WidgetUpdater.update(context)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                _markdownImport.value = MarkdownImportState(canSplit = true, partsSaved = partsSaved,
                    message = "Split cancelled. $partsSaved completed parts are kept. Retry checks the same part IDs.")
                throw cancel
            } catch (_: Exception) {
                _markdownImport.value = MarkdownImportState(canSplit = true, partsSaved = partsSaved,
                    message = "Split stopped. $partsSaved completed parts and the original document are retained. Retry checks the same part IDs.")
            }
        }
    }
    private var quickDraftJob: kotlinx.coroutines.Job? = null
    private var quickTitleJob: kotlinx.coroutines.Job? = null
    private var quickDraftPendingText: String? = null
    private var quickDraftSaving = false

    fun loadQuickDraft() {
        if (_quickDraft.value.ready || quickDraftJob?.isActive == true) return
        quickDraftJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { com.example.notes.QuickNoteDraftStore(context).load() }
                _quickDraft.value = QuickDraftState(ready = true, text = loaded.text, snapshot = loaded.snapshot, title = loaded.title)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { _quickDraft.value = QuickDraftState(error = "Draft unavailable. Original reference retained. Retry loading.") }
        }
    }

    fun updateQuickDraftTitle(title: String) {
        if (!_quickDraft.value.ready || _quickDraft.value.busy || quickDraftSaving) return
        if (title.length > 2048) {
            _quickDraft.update { it.copy(error = "Title limited to 2,048 characters. Paste longer text in the body.") }
            return
        }
        _quickDraft.update { it.copy(title = title, error = null) }
        quickTitleJob?.cancel()
        quickTitleJob = viewModelScope.launch {
            try {
                kotlinx.coroutines.delay(300)
                withContext(Dispatchers.IO) { com.example.notes.QuickNoteDraftStore(context).writeTitle(title) }
            }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { _quickDraft.update { it.copy(error = "Could not store the draft title. Retry saving before closing.") } }
        }
    }

    fun updateQuickDraft(text: String) {
        if (!_quickDraft.value.ready || _quickDraft.value.busy || quickDraftSaving) return
        quickDraftJob?.cancel()
        quickDraftPendingText = text
        val large = com.example.notes.NoteEditorWindows.requiresWindow(text)
        _quickDraft.update { it.copy(text = if (large) it.text else text, busy = large, error = null, pending = true) }
        quickDraftJob = viewModelScope.launch {
            try {
                if (!large) kotlinx.coroutines.delay(400)
                val snapshot = withContext(Dispatchers.IO) { com.example.notes.QuickNoteDraftStore(context).write(text) }
                quickDraftPendingText = null
                _quickDraft.value = QuickDraftState(ready = true, text = if (large) "" else text, snapshot = snapshot, title = _quickDraft.value.title)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) {
                _quickDraft.update { it.copy(busy = false, error = "Draft could not be stored. Retry before closing. New notes are limited to 55 MB; split larger pastes.") }
            }
        }
    }

    fun saveQuickDraft(folders: List<String>, open: Boolean = false, onSaved: () -> Unit = {}) {
        if (quickDraftSaving || !_quickDraft.value.ready) return
        quickDraftSaving = true
        val pending = quickDraftPendingText
        val title = _quickDraft.value.title
        val previousJob = quickDraftJob
        val previousTitleJob = quickTitleJob
        previousJob?.cancel()
        previousTitleJob?.cancel()
        _quickDraft.update { it.copy(busy = true, error = null) }
        quickDraftJob = viewModelScope.launch {
            try {
                previousJob?.join()
                previousTitleJob?.join()
                val saved = withContext(Dispatchers.IO) {
                    val drafts = com.example.notes.QuickNoteDraftStore(context)
                    if (pending != null) drafts.write(pending)
                    drafts.writeTitle(title)
                    drafts.promote(folders)
                }
                quickDraftPendingText = null
                _quickDraft.value = QuickDraftState(ready = true)
                onSaved()
                if (open) showDetailItem(saved)
                com.example.widget.WidgetUpdater.update(context)
                showToast("Note saved.")
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) {
                _quickDraft.update { it.copy(busy = false, error = "Could not save. Your draft is retained; retry.") }
            } finally { quickDraftSaving = false }
        }
    }
    data class PendingSection(val hash: String, val start: Long, val end: Long, val replacement: String)
    private val _pendingSections = MutableStateFlow<Map<String, PendingSection>>(emptyMap())
    val pendingSections = _pendingSections.asStateFlow()
    private val generationHistories = mutableMapOf<String, com.example.notes.NoteGenerationHistory>()
    fun noteGenerationHistory(id: String) = generationHistories.getOrPut(id) { com.example.notes.NoteGenerationHistory() }

    fun journalSectionOnBackground(id: String, hash: String, start: Long, end: Long, replacement: String) {
        viewModelScope.launch {
            try { com.example.notes.SectionDraftStore(context).save(id, hash, start, end, replacement) }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { showToast("Could not journal the edit. Keep the editor open and retry saving.") }
        }
    }
    private val windowHistories = object : LinkedHashMap<String, com.example.notes.WindowEditHistory>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.example.notes.WindowEditHistory>?) = size > 8
    }
    fun noteWindowHistory(id: String, hash: String?, start: Long): com.example.notes.WindowEditHistory =
        windowHistories.getOrPut("$id:$hash:$start") { com.example.notes.WindowEditHistory() }
    fun retainNoteSection(id: String, section: PendingSection) {
        _pendingSections.update { it + (id to section) }
    }
    fun clearNoteSection(id: String, expected: PendingSection? = null) {
        _pendingSections.update { if (expected == null || it[id] === expected) it - id else it }
    }
    private var noteTextJob: kotlinx.coroutines.Job? = null
    private var noteTextRevision = 0L
    private var saveAfterNoteInput = false

    fun prepareNoteWindow(id: String, start: Int, end: Int, replacement: String,
        caretStart: Int? = null, caretEnd: Int? = caretStart) {
        val source = _activeCaptureItem.value?.takeIf { it.id == id } ?: return
        val revision = ++noteTextRevision
        noteTextJob?.cancel()
        _noteInputBusy.value = true
        noteTextJob = viewModelScope.launch {
            var applied = false
            try {
                val plan = withContext(Dispatchers.Default) {
                    val cancellation = kotlin.coroutines.coroutineContext
                    com.example.notes.NoteTextReplacement(source.content, start, end, replacement) { cancellation.ensureActive() }
                }
                if (!com.example.notes.NoteSizePolicy.acceptsEdit(plan.byteLength, source.byteLength)) {
                    throw java.io.IOException("Note exceeds 55 MB. Split the paste or import smaller Markdown files.")
                }
                if (com.example.notes.NotePersistence.isNote(source)) {
                    plan.openStream().use { input -> fileEditor.stage(source, input, caretStart ?: (start + replacement.length), caretEnd ?: (start + replacement.length)) }
                    if (revision == noteTextRevision && _activeCaptureItem.value?.id == id) {
                        saveAfterNoteInput = false
                        _activeCaptureItem.value = source.copy(content = "", bodyLoaded = false)
                    }
                } else {
                    val text = withContext(Dispatchers.Default) { source.content.replaceRange(start, end, replacement) }
                    if (revision == noteTextRevision && _activeCaptureItem.value?.id == id) {
                        updateActiveCaptureItem { it.copy(content = text, bodyLoaded = true) }
                        applied = true
                    }
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (error: Exception) { showToast(error.message ?: "Paste could not be applied. Saved note unchanged.") }
            finally {
                if (revision == noteTextRevision) {
                    _noteInputBusy.value = false
                    val save = saveAfterNoteInput && applied
                    saveAfterNoteInput = false
                    if (save) saveActiveItem()
                }
            }
        }
    }

    fun updateActiveCaptureItem(updater: (SavedItem) -> SavedItem) {
        val oldItem = _activeCaptureItem.value
        var newItem = oldItem?.let(updater)
        if (newItem?.type == SavedItemType.LINK &&
            (newItem.content.length > com.example.notes.NoteSizePolicy.URL_CHARACTERS || newItem.content.indexOf('\n') >= 0)) {
            newItem = newItem.copy(type = SavedItemType.TEXT, bodyLoaded = true)
            showToast("Pasted text opened as a note so its full content is preserved.")
        }
        _activeCaptureItem.value = newItem
        if (oldItem != null) {
            captureDrafts[oldItem.type] = oldItem
        }
        if (newItem != null) {
            captureDrafts[newItem.type] = newItem
        }

        if (oldItem != null && newItem != null && newItem.type == SavedItemType.LINK) {
            if (oldItem.content != newItem.content && newItem.content.isNotBlank()) {
                fetchLinkPreviewForActiveItem(newItem.content)
            }
        }
    }

    fun switchActiveCaptureType(type: SavedItemType) {
        if (fileEditorState.value.id != null || _noteInputBusy.value) return
        val currentItem = _activeCaptureItem.value ?: return
        if (currentItem.type == type) return

        captureDrafts[currentItem.type] = currentItem
        _activeCaptureItem.value = captureDrafts[type]?.copy(type = type) ?: createCaptureDraft(type)
    }

    private fun createCaptureDraft(type: SavedItemType): SavedItem {
        return SavedItem(
            type = type,
            title = "",
            content = if (type == SavedItemType.CODE) "// Code snippet" else "",
            folders = emptyList()
        )
    }

    fun saveLocalTextItem(title: String, content: String, type: SavedItemType, selectedFolders: List<String>, onSaved: () -> Unit = {}) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val newItem = SavedItem(
                    title = title,
                    content = content,
                    type = type,
                    folders = selectedFolders,
                    timestamp = System.currentTimeMillis()
                )
                val savedItem = repository.saveItem(newItem, null)
                onSaved()
                showToast("Item saved successfully.")

                if (type == SavedItemType.LINK && content.isNotBlank()) {
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val metadata = repository.fetchLinkMetadata(content)
                            val updatedItem = savedItem.copy(
                                linkTitle = metadata.title,
                                linkDescription = metadata.description,
                                linkImage = metadata.imageUrl,
                                title = metadata.title ?: savedItem.title
                            )
                            repository.saveItem(updatedItem, null)
                        } catch (e: Exception) {
                            Log.e("CobaltVM", "Bg link preview error: ${e.message}")
                        }
                    }
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                showToast("Could not save. Your draft has been retained; please retry.")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveActiveItem() {
        if (_isSaving.value) return
        if (_noteInputBusy.value) { saveAfterNoteInput = true; return }

        val item = _activeCaptureItem.value ?: return
        if (fileEditorState.value.id == item.id && fileEditorState.value.busy) return
        _isSaving.value = true
        viewModelScope.launch {
            _saveProgress.value = 0f
            try {
                var finalItem = item
                if (finalItem.type == SavedItemType.LINK && finalItem.linkTitle.isNullOrBlank()) {
                    finalItem = buildItemWithFetchedMetadata(finalItem)
                }
                if (finalItem.title.isBlank()) {
                    val fallback = finalItem.content.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotBlank() }
                        ?.removePrefix("#")?.trim()
                        ?.take(60)
                    if (!fallback.isNullOrBlank()) {
                        finalItem = finalItem.copy(title = fallback)
                    }
                }
                val isEdit = allItems.value.any { it.id == finalItem.id } || editOriginalId != null
                val persisted = if (fileEditorState.value.id == finalItem.id) {
                    val original = finalItem
                    val saved = fileEditor.save(finalItem)
                    if (original.contentHash != null) noteGenerationHistory(original.id).record(
                        com.example.notes.NoteGeneration(original.contentHash, original.byteLength, original.preview),
                        com.example.notes.NoteGeneration(saved.contentHash!!, saved.byteLength, saved.preview))
                    saved
                } else repository.saveItem(finalItem, pendingMediaBytes) { progress -> _saveProgress.value = progress }

                if (editOriginalId != null && editOriginalId != finalItem.id) {
                    val oldItem = allItems.value.find { it.id == editOriginalId }
                    if (oldItem != null) {
                        repository.deleteItem(oldItem)
                    }
                }

                if (_activeDetailItem.value?.id == finalItem.id || _activeDetailItem.value?.id == editOriginalId) {
                    _activeDetailItem.value = persisted
                }

                _saveProgress.value = 1.0f
                kotlinx.coroutines.delay(200) // slight delay to show 100% progress state
                cancelCapture()
                editOriginalId = null
                showToast(if (isEdit) "Item updated successfully." else "Item saved successfully.")
                if (!isEdit) checkProactiveSurvey()
            } catch (e: Exception) {
                showToast("Could not save. Your draft is retained; keep the editor open and retry.")
            } finally {
                _isSaving.value = false
                _saveProgress.value = null
            }
        }
    }

    fun deleteSavedItem(item: SavedItem) {
        viewModelScope.launch {
            try {
                if (repository.deleteItem(item)) _lastDeletedItemForUndo.value = item
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { showToast("Could not move this item to Trash. Please retry.") }
        }
    }

    fun restoreDeletedItem(item: SavedItem) {
        _lastDeletedItemForUndo.value = null
        viewModelScope.launch {
            try { repository.restoreDeletedItem(item.id) }
            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { showToast("Undo is unavailable. Check Settings → Storage → Trash.") }
        }
    }

    fun archiveItem(item: SavedItem) {
        viewModelScope.launch {
            val updatedFolders = item.folders.filter { it != "Archive" }
            repository.saveItem(item.copy(isArchived = true, folders = updatedFolders))
            showToast("Item archived successfully.")
        }
    }

    fun unarchiveItem(item: SavedItem) {
        viewModelScope.launch {
            val updatedFolders = item.folders.filter { it != "Archive" }
            repository.saveItem(item.copy(isArchived = false, folders = updatedFolders))
            showToast("Item unarchived successfully.")
        }
    }

    fun createFolder(name: String, colorHex: String? = null, iconName: String? = null, isPinned: Boolean = false) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCustomFolder(name.trim(), colorHex, iconName, isPinned)
        }
    }

    fun deleteFolder(name: String) {
        viewModelScope.launch {
            repository.deleteCustomFolder(name)
        }
    }

    fun updateFolder(folder: com.example.data.local.CustomFolderEntity) {
        viewModelScope.launch {
            repository.updateCustomFolder(folder)
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        viewModelScope.launch {
            repository.renameCustomFolder(oldName, newName.trim())
            showToast("Folder renamed to '${newName.trim()}' successfully.")
        }
    }

    fun toggleFolderAssignment(item: SavedItem, folderName: String) {
        viewModelScope.launch {
            val updatedFolders = if (item.folders.contains(folderName)) {
                item.folders.filter { it != folderName }
            } else {
                item.folders + folderName
            }
            repository.saveItem(item.copy(folders = updatedFolders))
        }
    }

    fun updateSavedItem(item: SavedItem) {
        viewModelScope.launch {
            try {
                val saved = repository.saveItem(item, null)
                if (_activeDetailItem.value?.id == item.id) _activeDetailItem.value = saved
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { showToast("Could not save this change. The saved body is unchanged.") }
        }
    }

    fun createAndAssignFolder(item: SavedItem, folderName: String) {
        val trimmed = folderName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            if (!customFolders.value.contains(trimmed)) {
                repository.addCustomFolder(trimmed)
            }
            val updatedFolders = if (item.folders.contains(trimmed)) item.folders else item.folders + trimmed
            repository.saveItem(item.copy(folders = updatedFolders))
        }
    }

    // ----------------------------------------------------
    // PRIVATE STORAGE HELPERS
    // ----------------------------------------------------

    private fun readUriBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.readBytes()
    } catch (e: Exception) {
        Log.e("CobaltVM", "Failed to read URI bytes: ${e.message}"); null
    }

    private val synonymGroups = listOf(
        setOf("image", "photo", "pic", "picture", "screenshot", "camera", "jpg", "png", "jpeg", "gallery"),
        setOf("link", "web", "url", "website", "http", "www", "href", "bookmark", "internet"),
        setOf("video", "movie", "clip", "recording", "mp4", "mkv", "mov", "film"),
        setOf("audio", "music", "voice", "mic", "sound", "recording", "mp3", "m4a", "wav", "transcript", "podcast"),
        setOf("code", "dev", "program", "kotlin", "java", "script", "json", "xml", "html", "css", "js", "coding", "developer"),
        setOf("text", "note", "memo", "write", "content", "document", "doc", "pdf", "txt")
    )

    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        var prev = IntArray(s2.length + 1) { it }
        var curr = IntArray(s2.length + 1)

        for (i in 1..s1.length) {
            curr[0] = i
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[s2.length]
    }

    private fun isFuzzyMatch(word1: String, word2: String): Boolean {
        if (word1 == word2) return true
        if (word1.contains(word2) || word2.contains(word1)) return true

        val maxDistance = when {
            word1.length <= 3 -> 0
            word1.length <= 5 -> 1
            else -> 2
        }

        return levenshteinDistance(word1, word2) <= maxDistance
    }

    private fun calculateSearchScore(item: SavedItem, queryTerms: List<String>): Int {
        if (queryTerms.isEmpty()) return 0

        var totalScore = 0

        val titleWords = item.title.lowercase().split(Regex("[^a-zA-Z0-9]")).filter { it.isNotBlank() }
        val contentWords = item.content.lowercase().split(Regex("[^a-zA-Z0-9]")).filter { it.isNotBlank() }
        val extTextWords = item.extractedText?.lowercase()?.split(Regex("[^a-zA-Z0-9]"))?.filter { it.isNotBlank() } ?: emptyList()
        val linkTitleWords = item.linkTitle?.lowercase()?.split(Regex("[^a-zA-Z0-9]"))?.filter { it.isNotBlank() } ?: emptyList()
        val linkDescWords = item.linkDescription?.lowercase()?.split(Regex("[^a-zA-Z0-9]"))?.filter { it.isNotBlank() } ?: emptyList()
        val folderWords = item.folders.flatMap { it.lowercase().split(Regex("[^a-zA-Z0-9]")) }.filter { it.isNotBlank() }
        val itemTypeStr = item.type.displayName.lowercase()

        for (term in queryTerms) {
            var termMatched = false

            val synonyms = synonymGroups.find { term in it } ?: emptySet()
            val allSearchTerms = synonyms + term

            for (st in allSearchTerms) {
                // 1. Title match
                if (item.title.contains(st, ignoreCase = true)) {
                    totalScore += 20
                    termMatched = true
                } else {
                    val fuzzyTitleMatch = titleWords.any { isFuzzyMatch(it, st) }
                    if (fuzzyTitleMatch) {
                        totalScore += 12
                        termMatched = true
                    }
                }

                // 2. Folder match
                if (item.folders.any { it.contains(st, ignoreCase = true) }) {
                    totalScore += 15
                    termMatched = true
                } else {
                    val fuzzyFolderMatch = folderWords.any { isFuzzyMatch(it, st) }
                    if (fuzzyFolderMatch) {
                        totalScore += 10
                        termMatched = true
                    }
                }

                // 3. Item type match
                if (itemTypeStr.contains(st) || isFuzzyMatch(itemTypeStr, st)) {
                    totalScore += 10
                    termMatched = true
                }

                // 4. Link title match
                if (item.linkTitle?.contains(st, ignoreCase = true) == true) {
                    totalScore += 12
                    termMatched = true
                } else {
                    val fuzzyLinkTitleMatch = linkTitleWords.any { isFuzzyMatch(it, st) }
                    if (fuzzyLinkTitleMatch) {
                        totalScore += 8
                        termMatched = true
                    }
                }

                // 5. Main content match
                if (item.content.contains(st, ignoreCase = true)) {
                    totalScore += 8
                    termMatched = true
                } else {
                    val fuzzyContentMatch = contentWords.any { isFuzzyMatch(it, st) }
                    if (fuzzyContentMatch) {
                        totalScore += 5
                        termMatched = true
                    }
                }

                // 6. Link description match
                if (item.linkDescription?.contains(st, ignoreCase = true) == true) {
                    totalScore += 8
                    termMatched = true
                } else {
                    val fuzzyLinkDescMatch = linkDescWords.any { isFuzzyMatch(it, st) }
                    if (fuzzyLinkDescMatch) {
                        totalScore += 4
                        termMatched = true
                    }
                }

                // 7. Extracted text match (OCR, transcription, etc.)
                if (item.extractedText?.contains(st, ignoreCase = true) == true) {
                    totalScore += 6
                    termMatched = true
                } else {
                    val fuzzyExtTextMatch = extTextWords.any { isFuzzyMatch(it, st) }
                    if (fuzzyExtTextMatch) {
                        totalScore += 3
                        termMatched = true
                    }
                }
            }

            if (!termMatched) {
                return 0
            }
        }

        return totalScore
    }

    private val _shareDownloadProgress = MutableStateFlow<Float?>(null)
    val shareDownloadProgress: StateFlow<Float?> = _shareDownloadProgress.asStateFlow()

    fun shareItem(item: com.example.data.model.SavedItem, context: android.content.Context) {
        if (com.example.notes.NotePersistence.isNote(item)) {
            viewModelScope.launch {
                var exportFile: File? = null
                try {
                    val file = withContext(Dispatchers.IO) {
                        val persistence = com.example.notes.NotePersistence(context)
                        val saved = persistence.load(item.id, includeSmallBody = false)
                            ?: throw java.io.IOException("Note is missing")
                        val directory = File(context.cacheDir, "note_exports")
                        check(directory.isDirectory || directory.mkdirs())
                        File(directory, "note_${UUID.randomUUID()}.md").also { destination ->
                            exportFile = destination
                            val cancellation = kotlin.coroutines.coroutineContext
                            destination.outputStream().use { output ->
                                com.example.notes.NoteExport.write(saved, persistence.store, output) { cancellation.ensureActive() }
                            }
                        }
                    }
                    shareLocalFile(file, item, context)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    exportFile?.let { file -> withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { file.delete() } }
                    throw cancel
                } catch (_: Exception) {
                    withContext(Dispatchers.IO) { exportFile?.delete() }
                    showToast("Could not prepare Markdown for sharing. Original note retained.")
                }
            }
            return
        }
        val isMedia = item.type == com.example.data.model.SavedItemType.IMAGE || item.type == com.example.data.model.SavedItemType.VIDEO || item.type == com.example.data.model.SavedItemType.AUDIO
        val localPath = if (item.type == com.example.data.model.SavedItemType.AUDIO) item.thumbnailPath else item.content

        if (isMedia && !localPath.isNullOrBlank() && (localPath.startsWith("http://") || localPath.startsWith("https://"))) {
            // Need to download it first
            viewModelScope.launch(Dispatchers.IO) {
                _shareDownloadProgress.value = 0f
                try {
                    val destDir = repository.getPermanentMediaDir(item.type)
                    val extension = when (item.type) {
                        com.example.data.model.SavedItemType.VIDEO -> "mp4"
                        com.example.data.model.SavedItemType.AUDIO -> "mp4"
                        else -> "jpg"
                    }
                    val fileName = "share_${item.id}.$extension"
                    val destFile = java.io.File(destDir, fileName)

                    if (!destFile.exists()) {
                        val request = okhttp3.Request.Builder().url(localPath).build()
                        val client = okhttp3.OkHttpClient()
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw Exception("Failed to download")
                            val body = response.body ?: throw Exception("Empty body")
                            val totalBytes = body.contentLength()
                            body.byteStream().use { input ->
                                java.io.FileOutputStream(destFile).use { output ->
                                    val buffer = ByteArray(8192)
                                    var bytesCopied = 0L
                                    var read: Int
                                    while (input.read(buffer).also { read = it } >= 0) {
                                        output.write(buffer, 0, read)
                                        bytesCopied += read
                                        if (totalBytes > 0) {
                                            _shareDownloadProgress.value = bytesCopied.toFloat() / totalBytes
                                        }
                                    }
                                }
                            }
                        }
                    }
                    _shareDownloadProgress.value = null

                    // Now share it
                    shareLocalFile(destFile, item, context)
                } catch (e: Exception) {
                    _shareDownloadProgress.value = null
                    showToast("Failed to download media for sharing: ${e.message}")
                    shareAsText(item, context)
                }
            }
        } else if (isMedia && !localPath.isNullOrBlank()) {
            val file = java.io.File(localPath)
            if (file.exists()) {
                shareLocalFile(file, item, context)
            } else {
                shareAsText(item, context)
            }
        } else {
            shareAsText(item, context)
        }
    }

    private fun shareLocalFile(file: java.io.File, item: com.example.data.model.SavedItem, context: android.content.Context) {
        viewModelScope.launch {
        try {
        val exported = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "shares").also { check(it.isDirectory || it.mkdirs()) }
            val target = File(directory, "share_${UUID.randomUUID()}.${file.extension.take(8).ifEmpty { "bin" }}")
            val cancellation = kotlin.coroutines.coroutineContext
            try {
                file.inputStream().use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(com.example.notes.NoteSizePolicy.IO_CHUNK_BYTES)
                    while (true) {
                        cancellation.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                } }
                target
            } catch (error: Throwable) { target.delete(); throw error }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exported)
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = when (item.type) {
                com.example.data.model.SavedItemType.TEXT, com.example.data.model.SavedItemType.CODE -> "text/markdown"
                com.example.data.model.SavedItemType.VIDEO -> "video/*"
                com.example.data.model.SavedItemType.AUDIO -> "audio/*"
                else -> "image/*"
            }
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Media"))
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { showToast("Could not prepare the shared file. Original retained.") }
        }
    }

    private fun shareAsText(item: com.example.data.model.SavedItem, context: android.content.Context) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            val shareText = buildString {
                appendLine("Title: ${item.title}")
                appendLine("Type: ${item.type.displayName}")
                appendLine("Content: ${if (item.type == com.example.data.model.SavedItemType.VIDEO) "Video media" else item.content}")
                if (item.type == com.example.data.model.SavedItemType.AUDIO && !item.thumbnailPath.isNullOrBlank()) {
                    appendLine("Audio Link: ${item.thumbnailPath}")
                }
                if (item.extractedText != null) {
                    appendLine("Extracted Text: ${item.extractedText}")
                }
            }
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Memory"))
    }

    suspend fun submitBugReport(
        context: android.content.Context, title: String, description: String, steps: List<String>, attachmentUriStr: String?,
        userEmail: String, userId: String, deviceModel: String, osVersion: String, appVersion: String
    ): Boolean {
        return com.example.util.FeedbackSubmissionManager.submitBugReport(
            context, title, description, steps, attachmentUriStr, userEmail, userId, deviceModel, osVersion, appVersion,
        )
    }

    suspend fun submitFeatureRequest(
        context: android.content.Context, title: String, problemStatement: String, proposedSolution: String, priority: String,
        userConsent: Boolean, userEmail: String, userId: String, deviceModel: String, osVersion: String, appVersion: String
    ): Boolean {
        return com.example.util.FeedbackSubmissionManager.submitFeatureRequest(
            context, title, problemStatement, proposedSolution, priority, userConsent, userEmail, userId, deviceModel, osVersion, appVersion,
        )
    }

    suspend fun submitSurvey(
        context: android.content.Context, reaction: String, favoriteFeatures: List<String>, desiredImprovements: List<String>,
        npsScore: Int, customFeedback: String, userEmail: String, userId: String, deviceModel: String, osVersion: String, appVersion: String,
        almostQuitReason: String = ""
    ): Boolean {
        return com.example.util.FeedbackSubmissionManager.submitSurvey(
            context, reaction, favoriteFeatures, desiredImprovements, npsScore, customFeedback, almostQuitReason, userEmail, userId, deviceModel, osVersion, appVersion,
        )
    }
}

data class ExtractedLinkReview(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalUrl: String,
    val url: String,
    val description: String,
    val isSelected: Boolean = true
)

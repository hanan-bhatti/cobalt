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

package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.DeviceSession
import com.example.data.repository.SecondBrainRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.util.UUID
import com.example.utils.AnalyticsHelper
import com.example.utils.AnalyticsEvents

class SecondBrainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = SecondBrainRepository(context)
    val settingsRepository = com.example.data.repository.SettingsRepository(context)

    /** Returns true when the device has an active internet-capable network. */
    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
               caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
               caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
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
        val apiKey = settingsRepository.geminiApiKey.value
        return apiKey.isNotEmpty() || com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty()
    }

    // ----------------------------------------------------
    // STATE FLOWS
    // ----------------------------------------------------

    val allItems: StateFlow<List<SavedItem>> = repository.getAllItemsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customFolders: StateFlow<List<String>> = repository.getAllFoldersFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customFolderEntities: StateFlow<List<com.example.data.local.CustomFolderEntity>> = repository.getAllFolderEntitiesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedFolder = MutableStateFlow("All")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filtered Items (computes search query and selected system/custom folders)
    val filteredItems: StateFlow<List<SavedItem>> = combine(
        allItems,
        _selectedFolder,
        _searchQuery
    ) { items, folder, query ->
        var filtered = items

        // 1. Filter by Folder (System Category or Custom Folder)
        if (folder != "All") {
            val systemCategory = SavedItemType.entries.find { it.displayName == folder }
            filtered = if (systemCategory != null) {
                // System folder filter (e.g. Images, Links, Text, etc.) - hide archived
                filtered.filter { it.type == systemCategory && !it.folders.contains("Archive") }
            } else {
                // Custom folder filter (e.g. "Work" or "Archive")
                filtered.filter { it.folders.contains(folder) }
            }
        } else {
            // Hide archived items from "All" main feed
            filtered = filtered.filter { !it.folders.contains("Archive") }
        }

        // 2. Filter and rank by Search Query (fuzzy, synonym-aware search)
        if (query.isNotBlank()) {
            val queryTerms = query.lowercase().split(Regex("[^a-zA-Z0-9]")).filter { it.isNotBlank() }
            if (queryTerms.isNotEmpty()) {
                filtered = filtered
                    .map { item -> item to calculateSearchScore(item, queryTerms) }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .map { it.first }
            }
        }

        filtered
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
                AnalyticsHelper.logNoteDeleted(context, item.id, item.type.name)
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
                Log.e("SecondBrainVM", "Failed to update order indices: ${e.message}")
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
                Log.e("SecondBrainVM", "Failed to reorder item: ${e.message}")
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

    fun showDetailItem(item: SavedItem) {
        _activeDetailItem.value = item
    }

    fun closeDetailItem() {
        _activeDetailItem.value = null
    }

    fun openItemById(itemId: String) {
        viewModelScope.launch {
            try {
                val allItemsList = repository.getAllItemsFlow().first()
                val item = allItemsList.find { it.id == itemId }
                if (item != null) {
                    _activeDetailItem.value = item
                }
            } catch (e: Exception) {}
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

    private val _uiToast = MutableStateFlow<String?>(null)
    val uiToast: StateFlow<String?> = _uiToast.asStateFlow()

    fun showToast(message: String) {
        _uiToast.value = message
    }

    fun clearUiToast() {
        _uiToast.value = null
    }

    fun syncData() {
        if (_isSyncing.value) return

        if (!isNetworkAvailable()) {
            showToast("No internet connection. Please check your network and try again.")
            return
        }

        _isSyncing.value = true

        viewModelScope.launch {
            val auth = repository.firebaseAuth
            if (auth?.currentUser == null) {
                kotlinx.coroutines.delay(500)
                showToast("Please sign in to sync your data.")
                _isSyncing.value = false
                return@launch
            }

            try {
                // Background operations to restore and sync
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repository.restoreUserDataFromCloud()
                    repository.updateDeviceSession()
                    repository.syncUnsyncedItems()
                }
                showToast("Data synced successfully.")
            } catch (e: Exception) {
                android.util.Log.e("SecondBrainVM", "Sync failed: ${e.message}")
                showToast("Failed to sync data: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
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
                val models = response.models?.map { it.name.replace("models/", "") }
                    ?.filter { it.contains("gemini") && (it.contains("pro") || it.contains("flash") || it.contains("vision")) }
                    ?: emptyList()
                _availableModels.value = models
                if (isUserTriggered) {
                    showToast("Refreshed ${models.size} models successfully.")
                }
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Failed to fetch models: ${e.message}")
                if (isUserTriggered) {
                    showToast("Failed to refresh models: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    // Temporary storage of media bytes to upload to Storage upon saving
    private var pendingMediaBytes: ByteArray? = null

    // ----------------------------------------------------
    // AUTH STATE
    // ----------------------------------------------------

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userPhotoUrl = MutableStateFlow<String?>(null)
    val userPhotoUrl: StateFlow<String?> = _userPhotoUrl.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val maxStorageBytes = 512L * 1024 * 1024 // 512 MB default

    /** Only IMAGE, VIDEO, and AUDIO count toward the cloud storage quota. Text, links, and code are always free. */
    private fun SavedItem.isMediaType() =
        type == SavedItemType.IMAGE || type == SavedItemType.VIDEO || type == SavedItemType.AUDIO

    /** Best-effort size in bytes for a media item. Returns 0 for non-media (free) types. */
    private fun SavedItem.mediaQuotaBytes(): Long {
        if (!isMediaType()) return 0L
        // If we stored the size during upload/backup, use it directly
        if (sizeBytes > 0L) return sizeBytes
        // Otherwise try reading the local file (will be 0 on a new device after cloud restore)
        val localPath = (thumbnailPath ?: content).takeIf { it.startsWith("/") }
        if (localPath != null) {
            val file = java.io.File(localPath)
            if (file.exists()) return file.length()
        }
        return 0L
    }

    val cloudUsedStorageBytes: StateFlow<Long> = allItems.map { items: List<com.example.data.model.SavedItem> ->
        items.filter { it.isSynced && it.isMediaType() }.sumOf { it.mediaQuotaBytes() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    private val _selectedForBackupIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedForBackupIds: StateFlow<Set<String>> = _selectedForBackupIds.asStateFlow()

    fun toggleBackupSelection(itemId: String) {
        _selectedForBackupIds.update { current ->
            if (current.contains(itemId)) current - itemId else current + itemId
        }
    }

    fun clearBackupSelection() {
        _selectedForBackupIds.value = emptySet()
    }

    fun selectItemsForBackup(ids: List<String>) {
        _selectedForBackupIds.update { current ->
            current + ids
        }
    }

    fun deselectItemsForBackup(ids: List<String>) {
        _selectedForBackupIds.update { current ->
            current - ids.toSet()
        }
    }

    fun backupSelectedItems() {
        val ids = _selectedForBackupIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.backupSelectedItems(ids)
                clearBackupSelection()
            } catch (e: Exception) {
                _uiToast.value = "Backup failed: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun removeBackupItems(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.removeBackup(ids)
            } catch (e: Exception) {
                _uiToast.value = "Remove backup failed: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    val usedStorageBytes: StateFlow<Long> = allItems.map { items: List<com.example.data.model.SavedItem> ->
        var total = 0L
        items.forEach { item ->
            // Try to get file size if it's a local file
            val path = item.thumbnailPath ?: item.content
            if (path.startsWith("/")) {
                val file = java.io.File(path)
                if (file.exists()) {
                    total += file.length()
                }
            } else {
                total += path.toByteArray().size
            }
        }
        total
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)


    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authSuccess = MutableStateFlow<String?>(null)
    val authSuccess: StateFlow<String?> = _authSuccess.asStateFlow()

    private val _emailLinkSent = MutableStateFlow(false)
    val emailLinkSent: StateFlow<Boolean> = _emailLinkSent.asStateFlow()

    private val _pendingEmailLink = MutableStateFlow<String?>(null)
    val pendingEmailLink: StateFlow<String?> = _pendingEmailLink.asStateFlow()

    private val _pendingPasswordResetCode = MutableStateFlow<String?>(null)
    val pendingPasswordResetCode: StateFlow<String?> = _pendingPasswordResetCode.asStateFlow()

    private val prefs = context.getSharedPreferences("second_brain_prefs", android.content.Context.MODE_PRIVATE)

    val isFirebaseAvailable: Boolean = repository.firebaseAuth != null

    init {
        // Safe Firebase Auth State listener
        val auth = repository.firebaseAuth
        if (auth != null) {
            val user = auth.currentUser
            _userEmail.value = user?.email
            _userName.value = user?.displayName
            _userPhotoUrl.value = user?.photoUrl?.toString()

            if (user != null) {
                viewModelScope.launch {
                    _isInitialLoading.value = true
                    try {
                        repository.restoreUserDataFromCloud()
                        repository.updateDeviceSession()
                        repository.syncUnsyncedItems()
                    } catch (e: Exception) {
                        Log.e("SecondBrainVM", "Initial auto-sync error: ${e.message}", e)
                    } finally {
                        _isInitialLoading.value = false
                    }
                }
            } else {
                _isInitialLoading.value = false
            }

            auth.addAuthStateListener { firebaseAuth ->
                val updatedUser = firebaseAuth.currentUser
                _userEmail.value = updatedUser?.email
                _userName.value = updatedUser?.displayName
                _userPhotoUrl.value = updatedUser?.photoUrl?.toString()
                if (updatedUser != null) {
                    viewModelScope.launch {
                        _isInitialLoading.value = true
                        try {
                            repository.restoreUserDataFromCloud()
                            repository.updateDeviceSession()
                            repository.syncUnsyncedItems()
                        } catch (e: Exception) {
                            Log.e("SecondBrainVM", "Initial auto-sync error: ${e.message}", e)
                        } finally {
                            _isInitialLoading.value = false
                        }
                    }
                }
            }
        } else {
            // Read simulated login if Firebase is not available
            _userEmail.value = prefs.getString("simulated_email", null)
            _userName.value = prefs.getString("simulated_name", null)
            _userPhotoUrl.value = prefs.getString("simulated_photo", null)
            _isInitialLoading.value = false
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
    // AUTH ACTIONS
    // ----------------------------------------------------

    fun signUp(email: String, password: String) {
        _authSuccess.value = null
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Email and password cannot be empty."
            return
        }
        val auth = repository.firebaseAuth
        if (auth == null) {
            _authError.value = null
            prefs.edit().putString("simulated_email", email).apply()
            _userEmail.value = email
            showToast("Successfully registered and logged in as $email.")
            return
        }
        if (!isNetworkAvailable()) {
            _authError.value = "No internet connection. Please check your network and try again."
            return
        }
        _authError.value = null
        _authLoading.value = true
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val userMail = it.user?.email ?: email
                _userEmail.value = userMail
                viewModelScope.launch {
                    try {
                        repository.restoreUserDataFromCloud()
                        repository.updateDeviceSession()
                        repository.syncUnsyncedItems()
                        showToast("Successfully registered and synced as $userMail.")
                    } catch (e: Exception) {
                        Log.e("SecondBrainVM", "Post sign-up sync failed: ${e.message}")
                    } finally {
                        _authLoading.value = false
                    }
                }
            }
            .addOnFailureListener {
                _authLoading.value = false
                _authError.value = it.localizedMessage ?: "Sign up failed."
            }
    }

    fun signIn(email: String, password: String) {
        _authSuccess.value = null
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Email and password cannot be empty."
            return
        }
        val auth = repository.firebaseAuth
        if (auth == null) {
            _authError.value = null
            prefs.edit().putString("simulated_email", email).apply()
            _userEmail.value = email
            AnalyticsHelper.logSignInSuccess(context, "Simulated")
            showToast("Successfully logged in as $email.")
            return
        }
        if (!isNetworkAvailable()) {
            _authError.value = "No internet connection. Please check your network and try again."
            return
        }
        _authError.value = null
        _authLoading.value = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val userMail = it.user?.email ?: email
                _userEmail.value = userMail
                AnalyticsHelper.logSignInSuccess(context, "Email/Password")
                viewModelScope.launch {
                    try {
                        repository.restoreUserDataFromCloud()
                        repository.updateDeviceSession()
                        repository.syncUnsyncedItems()
                        showToast("Successfully logged in and synced as $userMail.")
                    } catch (e: Exception) {
                        Log.e("SecondBrainVM", "Post sign-in sync failed: ${e.message}")
                    } finally {
                        _authLoading.value = false
                    }
                }
            }
            .addOnFailureListener {
                _authLoading.value = false
                val errorMsg = it.localizedMessage ?: "Sign in failed."
                _authError.value = errorMsg
                AnalyticsHelper.logSignInFailed(context, "Email/Password", errorMsg)
            }
    }

    fun signInWithGoogle(activityContext: android.content.Context, onCompletion: (Boolean) -> Unit) {
        val auth = repository.firebaseAuth
        if (auth == null) {
            _authError.value = "Authentication not initialized."
            onCompletion(false)
            return
        }
        if (!isNetworkAvailable()) {
            _authError.value = "No internet connection. Please check your network and try again."
            onCompletion(false)
            return
        }
        _authError.value = null
        _authLoading.value = true

        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(activityContext)

                val clientId = activityContext.getString(com.example.R.string.default_web_client_id)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential

                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val userMail = authResult.user?.email ?: "Google User"
                    _userEmail.value = userMail
                    AnalyticsHelper.logSignInSuccess(context, "Google")
                    repository.restoreUserDataFromCloud()
                    repository.updateDeviceSession()
                    repository.syncUnsyncedItems()
                    showToast("Successfully logged in and synced as $userMail.")
                    onCompletion(true)
                } else {
                    val errorMsg = "Unexpected credential type: ${credential.type}"
                    _authError.value = errorMsg
                    AnalyticsHelper.logSignInFailed(context, "Google", errorMsg)
                    onCompletion(false)
                }
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Google Sign-In failed: ${e.message}")
                val msg = e.localizedMessage ?: ""
                _authError.value = "Google Sign-In failed: $msg"
                AnalyticsHelper.logSignInFailed(context, "Google", e.localizedMessage ?: "Google Sign-In failed")
                onCompletion(false)
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun signOut() {
        val auth = repository.firebaseAuth
        if (auth != null) {
            auth.signOut()
        } else {
            prefs.edit().remove("simulated_email").apply()
        }
        _userEmail.value = null
    }

    fun sendSignInLink(email: String) {
        _authSuccess.value = null
        if (email.isBlank()) {
            _authError.value = "Email address cannot be empty."
            return
        }
        val auth = repository.firebaseAuth
        if (auth == null) {
            _authError.value = null
            prefs.edit().putString("simulated_email", email).apply()
            _userEmail.value = email
            _emailLinkSent.value = true
            return
        }
        _authError.value = null
        _authLoading.value = true

        val actionCodeSettings = com.google.firebase.auth.ActionCodeSettings.newBuilder()
            .setUrl("https://second-brain-11.firebaseapp.com/signin")
            .setHandleCodeInApp(true)
            .setAndroidPackageName(
                "com.hanan_bhatti.second_brain",
                true, // installIfNotAvailable
                "24"  // minimumVersion
            )
            .build()

        auth.sendSignInLinkToEmail(email, actionCodeSettings)
            .addOnSuccessListener {
                _authLoading.value = false
                prefs.edit().putString("email_link_address", email).apply()
                _emailLinkSent.value = true
                _authSuccess.value = "Sign-in link sent to $email! Please click the link in your email to sign in."
            }
            .addOnFailureListener {
                _authLoading.value = false
                _authError.value = it.localizedMessage ?: "Failed to send sign-in link."
            }
    }

    fun sendPasswordResetEmail(email: String) {
        _authSuccess.value = null
        if (email.isBlank()) {
            _authError.value = "Please enter your email address to request a password reset."
            return
        }
        _authError.value = null
        val auth = repository.firebaseAuth
        if (auth == null) {
            _authSuccess.value = "(Sandbox Simulation) Password reset link sent to $email."
            return
        }
        _authLoading.value = true

        val actionCodeSettings = com.google.firebase.auth.ActionCodeSettings.newBuilder()
            .setUrl("https://second-brain-11.firebaseapp.com/resetpassword")
            .setHandleCodeInApp(true)
            .setAndroidPackageName(
                "com.hanan_bhatti.second_brain",
                true, // installIfNotAvailable
                "24"  // minimumVersion
            )
            .build()

        auth.sendPasswordResetEmail(email, actionCodeSettings)
            .addOnSuccessListener {
                _authLoading.value = false
                _authSuccess.value = "Password reset email sent to $email! Please check your inbox."
            }
            .addOnFailureListener {
                _authLoading.value = false
                _authError.value = it.localizedMessage ?: "Failed to send password reset email."
            }
    }

    fun handleDeepLink(linkStr: String) {
        val auth = repository.firebaseAuth
        if (auth == null) {
            val email = prefs.getString("email_link_address", "sandbox.user@example.com") ?: "sandbox.user@example.com"
            _userEmail.value = email
            return
        }
        if (auth.isSignInWithEmailLink(linkStr)) {
            val storedEmail = prefs.getString("email_link_address", null)
            if (!storedEmail.isNullOrBlank()) {
                completeEmailLinkSignIn(storedEmail, linkStr)
            } else {
                _pendingEmailLink.value = linkStr
                _authError.value = "Please confirm the email address you used to request the sign-in link."
            }
        } else if (linkStr.contains("/resetpassword")) {
            val uri = android.net.Uri.parse(linkStr)
            val oobCode = uri.getQueryParameter("oobCode")
            if (oobCode != null) {
                _authLoading.value = true
                _authError.value = null
                auth.verifyPasswordResetCode(oobCode)
                    .addOnSuccessListener {
                        _authLoading.value = false
                        _pendingPasswordResetCode.value = oobCode
                    }
                    .addOnFailureListener {
                        _authLoading.value = false
                        _authError.value = it.localizedMessage ?: "Invalid or expired password reset link."
                    }
            } else {
                _authError.value = "Missing password reset code."
            }
        }
    }

    fun setAuthError(error: String?) {
        _authError.value = error
    }

    fun resetPasswordWithCode(oobCode: String, newPassword: String) {
        if (newPassword.isBlank()) {
            _authError.value = "Password cannot be empty."
            return
        }
        val auth = repository.firebaseAuth ?: return
        _authError.value = null
        _authLoading.value = true
        auth.confirmPasswordReset(oobCode, newPassword)
            .addOnSuccessListener {
                _authLoading.value = false
                _pendingPasswordResetCode.value = null
                _authSuccess.value = "Your password has been successfully reset! You can now log in with your new password."
            }
            .addOnFailureListener {
                _authLoading.value = false
                _authError.value = it.localizedMessage ?: "Failed to reset password."
            }
    }

    fun completeEmailLinkSignIn(email: String, emailLink: String) {
        if (email.isBlank()) {
            _authError.value = "Email address cannot be empty."
            return
        }
        val auth = repository.firebaseAuth ?: return
        _authError.value = null
        _authLoading.value = true

        auth.signInWithEmailLink(email, emailLink)
            .addOnSuccessListener { result ->
                val userMail = result.user?.email ?: email
                _userEmail.value = userMail
                _pendingEmailLink.value = null
                prefs.edit().remove("email_link_address").apply()
                _emailLinkSent.value = false
                viewModelScope.launch {
                    try {
                        repository.restoreUserDataFromCloud()
                        repository.updateDeviceSession()
                        repository.syncUnsyncedItems()
                        showToast("Successfully logged in and synced as $userMail.")
                    } catch (e: Exception) {
                        Log.e("SecondBrainVM", "Post link sign-in sync failed: ${e.message}")
                    } finally {
                        _authLoading.value = false
                    }
                }
            }
            .addOnFailureListener {
                _authLoading.value = false
                _authError.value = it.localizedMessage ?: "Failed to sign in with link."
            }
    }

    fun resetEmailLinkSent() {
        _emailLinkSent.value = false
    }

    // ----------------------------------------------------
    // CAPTURE FLOW
    // ----------------------------------------------------

    fun startManualCapture(type: SavedItemType, initialFolders: List<String> = emptyList()) {
        _capturedBitmap.value = null
        pendingMediaBytes = null
        _extractedLinksToReview.value = emptyList()
        _isMetadataExtracting.value = false
        _metadataError.value = null
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
        _activeCaptureItem.value = null
        _capturedBitmap.value = null
        pendingMediaBytes = null
        _extractedLinksToReview.value = emptyList()
        _isMetadataExtracting.value = false
        _metadataError.value = null
        captureDrafts.clear()
    }

    /**
     * Process content shared from other apps via Share Target (Intent.SEND)
     */
    fun handleSharedIntent(mimeType: String?, textContent: String?, mediaUri: Uri?, subject: String? = null) {
        viewModelScope.launch {
            captureDrafts.clear()
            _capturedBitmap.value = null
            pendingMediaBytes = null
            _isMetadataExtracting.value = false
            _metadataError.value = null

            if (mimeType == null) return@launch

            when {
                // 1. Text Shared
                mimeType.startsWith("text/") && textContent != null -> {
                    // Extract URL from text using regex if it's not exclusively a URL
                    val urlRegex = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))".toRegex()
                    val matchResult = urlRegex.find(textContent)
                    val url = matchResult?.value

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
                        val isCode = textContent.contains("class ") || textContent.contains("fun ") || textContent.contains("{") || textContent.contains("import ")
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
                        Log.e("SecondBrainVM", "Failed to load shared image: ${e.message}")
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
                        Log.e("SecondBrainVM", "Failed to load shared video: ${e.message}")
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
                val bytes = file.readBytes()
                pendingMediaBytes = bytes
                val localPath = repository.saveToLocalCache("audio_${java.util.UUID.randomUUID()}.mp4", bytes)

                val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val apiKey = resolveValidApiKeyOrNull()
                if (apiKey == null) {
                    _ocrError.value = "Please save a valid Gemini API Key first."
                    _isOcrLoading.value = false
                    return@launch
                }

                val result = repository.extractTextFromAudio(
                    base64Audio = base64Data,
                    apiKey = apiKey,
                    model = settingsRepository.selectedModel.value
                )

                if (result != null && !result.startsWith("Error")) {
                    val extractedTitle = extractTitleFromMarkdown(result)
                    _activeCaptureItem.value = _activeCaptureItem.value?.copy(
                        content = result,
                        title = extractedTitle ?: _activeCaptureItem.value?.title ?: "Voice Memo Transcription",
                        thumbnailPath = localPath
                    )
                    _activeCaptureItem.value?.let { captureDrafts[SavedItemType.AUDIO] = it }
                } else {
                    _ocrError.value = result ?: "Failed to transcribe audio."
                }
            } catch (e: Exception) {
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
        val apiKey = settingsRepository.geminiApiKey.value.ifEmpty { com.example.BuildConfig.GEMINI_API_KEY }
        return if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") null else apiKey
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
            Log.w("SecondBrainVM", "Failed to parse OCR JSON, falling back to raw text.")
        }
        return Pair(parsedExtractedText, urlsList)
    }

    private suspend fun buildItemWithFetchedMetadata(item: SavedItem): SavedItem {
        if (item.type != SavedItemType.LINK) return item
        val metadata = repository.fetchLinkMetadata(item.content)
        return item.copy(
            linkTitle = metadata.title,
            linkDescription = metadata.description,
            linkImage = metadata.imageUrl,
            title = metadata.title ?: item.title.ifBlank { "Shared Link" }
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
                // Use Coil to load the image robustly. This handles content://, file://, raw paths, and http:// / https:// (Firebase links)
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
                    sensitivity = "High"
                )

                if (resultText != null) {
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
                    _ocrError.value = "Gemini OCR returned an empty result."
                }
            } catch (e: Exception) {
                _ocrError.value = "OCR Failed: ${e.localizedMessage}"
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
                val resultText = repository.extractTextFromRegion(bitmap, x, y, width, height, apiKey, model, sensitivity)
                if (resultText != null) {
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
                    _ocrError.value = "Gemini OCR was unable to read this region. Please select a clearer region."
                }
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "OCR region extraction failed: ${e.message}")
                _ocrError.value = e.message ?: "OCR region extraction failed. Please try again."
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

    fun confirmAndSaveExtractedLinks(selectedFolders: List<String> = emptyList()) {
        val linksToSave = _extractedLinksToReview.value.filter { it.isSelected && it.url.isNotBlank() }
        _extractedLinksToReview.value = emptyList() // Clear review list
        linksToSave.forEach { reviewItem ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val newId = java.util.UUID.randomUUID().toString()
                    val meta = repository.fetchLinkMetadata(reviewItem.url)
                    val newItem = com.example.data.model.SavedItem(
                        id = newId,
                        type = com.example.data.model.SavedItemType.LINK,
                        title = meta.title ?: "Extracted URL",
                        content = reviewItem.url,
                        folders = if (selectedFolders.isNotEmpty()) selectedFolders else listOf("AI Extracted"),
                        linkTitle = meta.title,
                        linkDescription = reviewItem.description.ifBlank { meta.description },
                        linkImage = meta.imageUrl
                    )
                    repository.saveItem(newItem)
                } catch (e: Exception) {
                    Log.e("SecondBrainVM", "Failed to save confirmed extracted link: ${e.message}")
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
                    _activeCaptureItem.value = updatedItem.copy(
                        linkTitle = metadata.title,
                        linkDescription = metadata.description,
                        linkImage = metadata.imageUrl,
                        title = metadata.title ?: updatedItem.title
                    )

                    if (metadata.title.isNullOrBlank() && metadata.description.isNullOrBlank() && metadata.imageUrl.isNullOrBlank()) {
                        _metadataError.value = "No metadata details could be parsed from this page."
                    }
                }
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Failed to fetch link preview: ${e.message}")
                _metadataError.value = "Extraction failed: ${e.localizedMessage ?: "Network error or invalid link"}"
            } finally {
                _isMetadataExtracting.value = false
            }
        }
    }

    fun startEditItem(item: SavedItem) {
        _capturedBitmap.value = null
        pendingMediaBytes = null
        _extractedLinksToReview.value = emptyList()
        _isMetadataExtracting.value = false
        _metadataError.value = null
        _activeCaptureItem.value = item

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
                    Log.e("SecondBrainVM", "Failed to pre-load image bitmap for edit: ${e.message}")
                } finally {
                    _isOcrLoading.value = false
                }
            }
        }
    }

    fun handleMediaSelected(uri: android.net.Uri, type: SavedItemType) {
        viewModelScope.launch {
            try {
                val bytes = readUriBytes(uri)
                if (bytes != null) {
                    pendingMediaBytes = bytes
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    _capturedBitmap.value = bitmap

                    val extension = if (type == SavedItemType.IMAGE) "jpg" else "mp4"
                    val localPath = repository.saveToLocalCache("media_${UUID.randomUUID()}.$extension", bytes)
                    updateActiveCaptureItem { it.copy(content = localPath, thumbnailPath = localPath, type = type) }
                } else {
                    // Fallback to Uri string if bytes can't be read directly
                    updateActiveCaptureItem { it.copy(content = uri.toString(), thumbnailPath = uri.toString(), type = type) }
                }
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Error in handleMediaSelected: ${e.message}")
                updateActiveCaptureItem { it.copy(content = uri.toString(), thumbnailPath = uri.toString(), type = type) }
            }
        }
    }

    fun updateActiveCaptureItem(updater: (SavedItem) -> SavedItem) {
        val oldItem = _activeCaptureItem.value
        val newItem = oldItem?.let(updater)
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

    fun saveLocalTextItem(title: String, content: String, type: SavedItemType, selectedFolders: List<String>) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val newItem = SavedItem(
                    title = title,
                    content = content,
                    type = type,
                    folders = selectedFolders,
                    timestamp = System.currentTimeMillis()
                )
                val savedItem = repository.saveItem(newItem, null)
                AnalyticsHelper.logNoteCreated(context, savedItem.id, savedItem.type.name)
                showToast("Item saved successfully.")

                // Sync to Firestore and Storage in background
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        repository.syncUnsyncedItems()
                    } catch (e: Exception) {
                        Log.e("SecondBrainVM", "Background sync failed: ${e.message}")
                    }
                }

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
                            Log.e("SecondBrainVM", "Bg link preview error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Failed to save quick note: ${e.message}")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveActiveItem() {
        val item = _activeCaptureItem.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _saveProgress.value = 0f
            try {
                var finalItem = item
                if (finalItem.type == SavedItemType.LINK && finalItem.linkTitle.isNullOrBlank()) {
                    finalItem = buildItemWithFetchedMetadata(finalItem)
                }
                val isEdit = allItems.value.any { it.id == finalItem.id }
                repository.saveItem(finalItem, pendingMediaBytes) { progress ->
                    _saveProgress.value = progress
                }
                if (isEdit) {
                    AnalyticsHelper.logNoteEdited(context, finalItem.id, finalItem.type.name)
                } else {
                    AnalyticsHelper.logNoteCreated(context, finalItem.id, finalItem.type.name)
                }
                _saveProgress.value = 1.0f
                kotlinx.coroutines.delay(200) // slight delay to show 100% progress state
                cancelCapture()
                showToast(if (isEdit) "Item updated successfully." else "Item saved successfully.")

                // Sync to Firestore and Storage in background
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        repository.syncUnsyncedItems()
                    } catch (e: Exception) {
                        Log.e("SecondBrainVM", "Background sync failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Failed to save item: ${e.message}")
            } finally {
                _isSaving.value = false
                _saveProgress.value = null
            }
        }
    }

    fun deleteSavedItem(item: SavedItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
            AnalyticsHelper.logNoteDeleted(context, item.id, item.type.name)
        }
    }

    fun restoreDeletedItem(item: SavedItem) {
        viewModelScope.launch {
            repository.saveItem(item)
            // Sync to Firebase in the background if configured
            launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    repository.syncUnsyncedItems()
                } catch (e: Exception) {
                    Log.e("SecondBrainVM", "Background sync failed: ${e.message}")
                }
            }
        }
    }

    fun archiveItem(item: SavedItem) {
        viewModelScope.launch {
            // Pre-create "Archive" custom folder if it does not exist
            if (!customFolders.value.contains("Archive")) {
                repository.addCustomFolder("Archive")
            }
            val updatedFolders = if (item.folders.contains("Archive")) item.folders else item.folders + "Archive"
            repository.saveItem(item.copy(folders = updatedFolders))
            showToast("Item archived successfully.")
        }
    }

    fun unarchiveItem(item: SavedItem) {
        viewModelScope.launch {
            val updatedFolders = item.folders.filter { it != "Archive" }
            repository.saveItem(item.copy(folders = updatedFolders))
            showToast("Item unarchived successfully.")
        }
    }

    fun createFolder(name: String, colorHex: String? = null, iconName: String? = null, isPinned: Boolean = false) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCustomFolder(name.trim(), colorHex, iconName, isPinned)
            repository.syncUnsyncedItems()
        }
    }

    fun deleteFolder(name: String) {
        viewModelScope.launch {
            repository.deleteCustomFolder(name)
            repository.syncUnsyncedItems()
        }
    }

    fun updateFolder(folder: com.example.data.local.CustomFolderEntity) {
        viewModelScope.launch {
            repository.updateCustomFolder(folder)
            repository.syncUnsyncedItems()
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        viewModelScope.launch {
            repository.renameCustomFolder(oldName, newName.trim())
            repository.syncUnsyncedItems()
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
            repository.saveItem(item, null)
            AnalyticsHelper.logNoteEdited(context, item.id, item.type.name)
            launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    repository.syncUnsyncedItems()
                } catch (e: Exception) {
                    Log.e("SecondBrainVM", "Background sync failed: ${e.message}")
                }
            }
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

    private fun readUriBytes(uri: Uri): ByteArray? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val byteBuffer = java.io.ByteArrayOutputStream()
            val bufferSize = 1024
            val buffer = ByteArray(bufferSize)
            var len: Int
            if (inputStream != null) {
                while (inputStream.read(buffer).also { len = it } != -1) {
                    byteBuffer.write(buffer, 0, len)
                }
            }
            byteBuffer.toByteArray()
        } catch (e: Exception) {
            Log.e("SecondBrainVM", "Failed to read URI bytes: ${e.message}")
            null
        }
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

    private val _devices = MutableStateFlow<List<DeviceSession>>(emptyList())
    val devices: StateFlow<List<DeviceSession>> = _devices.asStateFlow()

    private val _isDevicesLoading = MutableStateFlow(false)
    val isDevicesLoading: StateFlow<Boolean> = _isDevicesLoading.asStateFlow()

    fun loadDeviceSessions() {
        viewModelScope.launch {
            _isDevicesLoading.value = true
            try {
                _devices.value = repository.getAllDeviceSessions()
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Failed to load device sessions: ${e.message}")
            } finally {
                _isDevicesLoading.value = false
            }
        }
    }
}

data class ExtractedLinkReview(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalUrl: String,
    val url: String,
    val description: String,
    val isSelected: Boolean = true
)

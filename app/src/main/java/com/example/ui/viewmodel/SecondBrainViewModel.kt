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

class SecondBrainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = SecondBrainRepository(context)
    val settingsRepository = com.example.data.repository.SettingsRepository(context)

    // ----------------------------------------------------
    // STATE FLOWS
    // ----------------------------------------------------

    val allItems: StateFlow<List<SavedItem>> = repository.getAllItemsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customFolders: StateFlow<List<String>> = repository.getAllFoldersFlow()
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

        // 2. Filter by Search Query (title, content, or extracted text)
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true) ||
                        (it.extractedText?.contains(query, ignoreCase = true) == true)
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

    // Bitmap of captured image (used for freehand drawing & cropping for Gemini OCR)
    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _isOcrLoading = MutableStateFlow(false)
    val isOcrLoading: StateFlow<Boolean> = _isOcrLoading.asStateFlow()

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

    private val _extractedLinksToReview = MutableStateFlow<List<ExtractedLinkReview>>(emptyList())
    val extractedLinksToReview: StateFlow<List<ExtractedLinkReview>> = _extractedLinksToReview.asStateFlow()

    fun fetchAvailableModels(isUserTriggered: Boolean = false) {
        val apiKey = settingsRepository.geminiApiKey.value.ifEmpty { com.example.BuildConfig.GEMINI_API_KEY }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            if (isUserTriggered) {
                showToast("Please save a valid Gemini API Key first.")
            }
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

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authSuccess = MutableStateFlow<String?>(null)
    val authSuccess: StateFlow<String?> = _authSuccess.asStateFlow()

    private val _emailLinkSent = MutableStateFlow(false)
    val emailLinkSent: StateFlow<Boolean> = _emailLinkSent.asStateFlow()

    private val _pendingEmailLink = MutableStateFlow<String?>(null)
    val pendingEmailLink: StateFlow<String?> = _pendingEmailLink.asStateFlow()

    private val prefs = context.getSharedPreferences("second_brain_prefs", android.content.Context.MODE_PRIVATE)

    val isFirebaseAvailable: Boolean = repository.firebaseAuth != null

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1200) // Minimum display time for professional skeleton transition
            _isInitialLoading.value = false
        }

        // Safe Firebase Auth State listener
        val auth = repository.firebaseAuth
        if (auth != null) {
            _userEmail.value = auth.currentUser?.email
            _userName.value = auth.currentUser?.displayName
            _userPhotoUrl.value = auth.currentUser?.photoUrl?.toString()
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                _userEmail.value = user?.email
                _userName.value = user?.displayName
                _userPhotoUrl.value = user?.photoUrl?.toString()
                if (user != null) {
                    viewModelScope.launch {
                        _isInitialLoading.value = true
                        // Automatically pull backed up data from cloud
                        repository.restoreUserDataFromCloud()
                        // Automatically push any offline/unsynced data up to cloud
                        repository.syncUnsyncedItems()
                        _isInitialLoading.value = false
                    }
                }
            }
        } else {
            // Read simulated login if Firebase is not available
            _userEmail.value = prefs.getString("simulated_email", null)
            _userName.value = prefs.getString("simulated_name", null)
            _userPhotoUrl.value = prefs.getString("simulated_photo", null)
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
        _authError.value = null
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val userMail = it.user?.email ?: email
                _userEmail.value = userMail
                showToast("Successfully registered and logged in as $userMail.")
            }
            .addOnFailureListener {
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
            showToast("Successfully logged in as $email.")
            return
        }
        _authError.value = null
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val userMail = it.user?.email ?: email
                _userEmail.value = userMail
                showToast("Successfully logged in as $userMail.")
            }
            .addOnFailureListener {
                _authError.value = it.localizedMessage ?: "Sign in failed."
            }
    }

    fun signInWithGoogle(activityContext: android.content.Context, onCompletion: (Boolean) -> Unit) {
        val auth = repository.firebaseAuth
        if (auth == null) {
            _authError.value = null
            prefs.edit().putString("simulated_email", "google.sandbox@example.com").apply()
            _userEmail.value = "google.sandbox@example.com"
            showToast("Successfully logged in as google.sandbox@example.com.")
            onCompletion(true)
            return
        }
        _authError.value = null

        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(activityContext)
                
                // Fetch default_web_client_id from resources if available, or fall back to a placeholder
                val clientIdResId = activityContext.resources.getIdentifier("default_web_client_id", "string", activityContext.packageName)
                val clientId = if (clientIdResId != 0) {
                    activityContext.getString(clientIdResId)
                } else {
                    "YOUR_WEB_CLIENT_ID_PLACEHOLDER.apps.googleusercontent.com"
                }

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
                    showToast("Successfully logged in as $userMail.")
                    // Sync items automatically upon success
                    repository.syncUnsyncedItems()
                    onCompletion(true)
                } else {
                    _authError.value = "Unexpected credential type: ${credential.type}"
                    onCompletion(false)
                }
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Google Sign-In failed: ${e.message}")
                val msg = e.localizedMessage ?: ""
                _authError.value = if (msg.contains("No credentials available", ignoreCase = true) || msg.contains("NoCredentialException", ignoreCase = true)) {
                    "Google Sign-In is temporarily unavailable. Please sign in with your Email & Password instead."
                } else {
                    "Google Sign-In is currently unavailable. Please try using your Email & Password instead."
                }
                onCompletion(false)
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
        
        val actionCodeSettings = com.google.firebase.auth.ActionCodeSettings.newBuilder()
            .setUrl("https://second-brain-11.firebaseapp.com")
            .setHandleCodeInApp(true)
            .setAndroidPackageName(
                "com.hanan_bhatti.second_brain",
                true, // installIfNotAvailable
                "24"  // minimumVersion
            )
            .build()

        auth.sendSignInLinkToEmail(email, actionCodeSettings)
            .addOnSuccessListener {
                prefs.edit().putString("email_link_address", email).apply()
                _emailLinkSent.value = true
                _authSuccess.value = "Sign-in link sent to $email! Please click the link in your email to sign in."
            }
            .addOnFailureListener {
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
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                _authSuccess.value = "Password reset email sent to $email! Please check your inbox."
            }
            .addOnFailureListener {
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
        }
    }

    fun completeEmailLinkSignIn(email: String, emailLink: String) {
        if (email.isBlank()) {
            _authError.value = "Email address cannot be empty."
            return
        }
        val auth = repository.firebaseAuth ?: return
        _authError.value = null
        
        auth.signInWithEmailLink(email, emailLink)
            .addOnSuccessListener { result ->
                val userMail = result.user?.email ?: email
                _userEmail.value = userMail
                _pendingEmailLink.value = null
                prefs.edit().remove("email_link_address").apply()
                _emailLinkSent.value = false
                showToast("Successfully logged in as $userMail.")
                viewModelScope.launch {
                    repository.restoreUserDataFromCloud()
                    repository.syncUnsyncedItems()
                }
            }
            .addOnFailureListener {
                _authError.value = it.localizedMessage ?: "Failed to sign in with link."
            }
    }

    fun resetEmailLinkSent() {
        _emailLinkSent.value = false
    }

    // ----------------------------------------------------
    // CAPTURE FLOW
    // ----------------------------------------------------

    fun startManualCapture(type: SavedItemType) {
        _capturedBitmap.value = null
        pendingMediaBytes = null
        _extractedLinksToReview.value = emptyList()
        _activeCaptureItem.value = SavedItem(
            type = type,
            title = "",
            content = if (type == SavedItemType.CODE) "```kotlin\n// Code snippet\n```" else ""
        )
    }

    fun cancelCapture() {
        _activeCaptureItem.value = null
        _capturedBitmap.value = null
        pendingMediaBytes = null
        _extractedLinksToReview.value = emptyList()
    }

    /**
     * Process content shared from other apps via Share Target (Intent.SEND)
     */
    fun handleSharedIntent(mimeType: String?, textContent: String?, mediaUri: Uri?, subject: String? = null) {
        viewModelScope.launch {
            _capturedBitmap.value = null
            pendingMediaBytes = null

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
                        fetchLinkPreviewForActiveItem(url)
                    } else {
                        // Check if looks like code
                        val isCode = textContent.contains("class ") || textContent.contains("fun ") || textContent.contains("{") || textContent.contains("import ")
                        _activeCaptureItem.value = SavedItem(
                            type = if (isCode) SavedItemType.CODE else SavedItemType.TEXT,
                            title = subject?.takeIf { it.isNotBlank() } ?: if (isCode) "Captured Code Snippet" else "Captured Text Note",
                            content = textContent
                        )
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

    fun performRegionOcr(x: Int, y: Int, width: Int, height: Int) {
        val bitmap = _capturedBitmap.value ?: return
        val currentItem = _activeCaptureItem.value ?: return

        _isOcrLoading.value = true
        _ocrError.value = null
        viewModelScope.launch {
            try {
                val apiKey = settingsRepository.geminiApiKey.value.ifEmpty { com.example.BuildConfig.GEMINI_API_KEY }
                val model = settingsRepository.selectedModel.value
                val sensitivity = settingsRepository.ocrSensitivity.value
                val resultText = repository.extractTextFromRegion(bitmap, x, y, width, height, apiKey, model, sensitivity)
                if (resultText != null) {
                    var parsedExtractedText = resultText
                    val urlsList = mutableListOf<Pair<String, String>>()
                    
                    try {
                        val jsonText = resultText.trim().removePrefix("```json").removeSuffix("```").trim()
                        val jsonObject = org.json.JSONObject(jsonText)
                        parsedExtractedText = jsonObject.optString("extractedText", jsonText)
                        val urlsArray = jsonObject.optJSONArray("urls")
                        if (urlsArray != null) {
                            for (i in 0 until urlsArray.length()) {
                                val urlObj = urlsArray.getJSONObject(i)
                                val url = urlObj.optString("url", "")
                                val desc = urlObj.optString("description", "")
                                if (url.isNotBlank()) urlsList.add(Pair(url, desc))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("SecondBrainVM", "Failed to parse OCR JSON, falling back to raw text.")
                    }

                    _activeCaptureItem.value = currentItem.copy(
                        extractedText = parsedExtractedText,
                        // If it's a raw URL (fallback case)
                        title = if (parsedExtractedText.startsWith("http")) "Extracted Shared URL" else currentItem.title,
                        content = if (parsedExtractedText.startsWith("http")) parsedExtractedText else currentItem.content,
                        type = if (parsedExtractedText.startsWith("http")) com.example.data.model.SavedItemType.LINK else currentItem.type
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

    fun confirmAndSaveExtractedLinks() {
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
                        folders = listOf("AI Extracted"),
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
                }
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Failed to fetch link preview: ${e.message}")
            }
        }
    }

    fun startEditItem(item: SavedItem) {
        _capturedBitmap.value = null
        pendingMediaBytes = null
        _extractedLinksToReview.value = emptyList()
        _activeCaptureItem.value = item
    }

    fun updateActiveCaptureItem(updater: (SavedItem) -> SavedItem) {
        val oldItem = _activeCaptureItem.value
        val newItem = oldItem?.let(updater)
        _activeCaptureItem.value = newItem

        if (oldItem != null && newItem != null && newItem.type == SavedItemType.LINK) {
            if (oldItem.content != newItem.content && newItem.content.isNotBlank()) {
                fetchLinkPreviewForActiveItem(newItem.content)
            }
        }
    }

    fun saveLocalTextItem(title: String, content: String, type: SavedItemType, selectedFolders: List<String>) {
        viewModelScope.launch {
            try {
                val newItem = SavedItem(
                    title = title,
                    content = content,
                    type = type,
                    folders = selectedFolders,
                    timestamp = System.currentTimeMillis()
                )
                repository.saveItem(newItem, null)
                repository.syncUnsyncedItems()
                showToast("Item saved successfully.")
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Failed to save quick note: ${e.message}")
            }
        }
    }

    fun saveActiveItem() {
        val item = _activeCaptureItem.value ?: return
        viewModelScope.launch {
            try {
                var finalItem = item
                if (finalItem.type == SavedItemType.LINK && finalItem.linkTitle.isNullOrBlank()) {
                    val metadata = repository.fetchLinkMetadata(finalItem.content)
                    finalItem = finalItem.copy(
                        linkTitle = metadata.title,
                        linkDescription = metadata.description,
                        linkImage = metadata.imageUrl,
                        title = metadata.title ?: finalItem.title.ifBlank { "Shared Link" }
                    )
                }
                val isEdit = allItems.value.any { it.id == finalItem.id }
                repository.saveItem(finalItem, pendingMediaBytes)
                cancelCapture()
                showToast(if (isEdit) "Item updated successfully." else "Item saved successfully.")
                // Sync to Firestore and Storage in background
                repository.syncUnsyncedItems()
            } catch (e: Exception) {
                Log.e("SecondBrainVM", "Failed to save item: ${e.message}")
            }
        }
    }

    fun deleteSavedItem(item: SavedItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun restoreDeletedItem(item: SavedItem) {
        viewModelScope.launch {
            repository.saveItem(item)
            // Sync to Firebase in the background if configured
            repository.syncUnsyncedItems()
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

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCustomFolder(name.trim())
        }
    }

    fun deleteFolder(name: String) {
        viewModelScope.launch {
            repository.deleteCustomFolder(name)
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
}

data class ExtractedLinkReview(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalUrl: String,
    val url: String,
    val description: String,
    val isSelected: Boolean = true
)

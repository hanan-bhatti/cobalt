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

package com.example.data.remote.trakt

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.util.SafeLogger as Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

sealed class TraktDeviceAuthState {
    object Idle : TraktDeviceAuthState()
    object RequestingCode : TraktDeviceAuthState()
    data class PendingApproval(
        val userCode: String,
        val verificationUrl: String,
        val deviceCode: String,
        val statusMessage: String = "Waiting for approval at trakt.tv/activate..."
    ) : TraktDeviceAuthState()
    data class AuthorizingBrowser(
        val authUri: Uri,
        val statusMessage: String = "Opening Trakt sign-in in browser..."
    ) : TraktDeviceAuthState()
    object ExchangingToken : TraktDeviceAuthState()
    data class Success(val user: TraktUser?) : TraktDeviceAuthState()
    data class Error(val message: String) : TraktDeviceAuthState()
}

/**
 * Pluggable credential store interface to decouple credential persistence from AndroidKeyStore
 * for unit testing without embedding test fallback keys in production.
 */
interface TraktCredentialStore {
    fun get(name: String): String?
    fun putAll(values: Map<String, String?>)
}

/**
 * Production implementation strictly backed by [com.example.util.SecretPreferences]
 * and platform Keystore.
 */
class DefaultTraktCredentialStore(
    prefs: SharedPreferences,
    namespace: String
) : TraktCredentialStore {
    private val secrets = com.example.util.SecretPreferences(prefs, namespace)
    override fun get(name: String): String? = secrets.get(name)
    override fun putAll(values: Map<String, String?>) = secrets.putAll(values)
}

/**
 * Manages Trakt OAuth2 Device Code authentication, tokens, custom client credentials,
 * and active session state.
 */
class TraktAuthManager(
    private val context: Context,
    private val apiServiceProvider: () -> TraktApiService = { TraktClient.apiService },
    credentialStore: TraktCredentialStore? = null
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secrets: TraktCredentialStore = credentialStore ?: DefaultTraktCredentialStore(prefs, PREFS_NAME)
    private val authScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    private val sessionEpoch = AtomicLong(System.currentTimeMillis())
    @Volatile private var isDisconnecting = false
    private val disconnectMutex = Mutex()
    private val refreshMutex = Mutex()
    private val sessionMutex = Mutex()

    fun currentSessionEpoch(): Long = sessionEpoch.get()
    fun isDisconnectingActive(): Boolean = isDisconnecting
    fun isSessionInvalid(expectedEpoch: Long): Boolean = isDisconnecting || sessionEpoch.get() != expectedEpoch

    /**
     * Executes the given persistence or mutation block atomically with respect to session lifecycle.
     * Prevents disconnect from occurring between the epoch validity check and database write.
     * Returns true if block was executed, or false if session is invalid/disconnecting.
     */
    suspend fun <T> runIfSessionValid(expectedEpoch: Long, block: suspend () -> T): Boolean {
        return sessionMutex.withLock {
            if (isSessionInvalid(expectedEpoch)) {
                false
            } else {
                block()
                true
            }
        }
    }

    init {
        TraktClient.tokenProvider = { getAccessToken() }
        TraktClient.clientIdProvider = { getClientId() }
        TraktClient.baseUrlProvider = { getBaseUrl() }
        TraktClient.retryNotBeforeProvider = { prefs.getLong("retry_not_before", 0L) }
        TraktClient.saveRetryNotBefore = { until -> prefs.edit().putLong("retry_not_before", until).commit(); Unit }
    }

    private val _isConnected = MutableStateFlow(hasValidToken())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentUser = MutableStateFlow(loadCachedUser())
    val currentUser: StateFlow<TraktUser?> = _currentUser.asStateFlow()

    private val _deviceAuthState = MutableStateFlow<TraktDeviceAuthState>(TraktDeviceAuthState.Idle)
    val deviceAuthState: StateFlow<TraktDeviceAuthState> = _deviceAuthState.asStateFlow()

    fun getAccessToken(): String? {
        if (isDisconnecting) return null
        return secrets.get(KEY_ACCESS_TOKEN)
    }

    fun getRefreshToken(): String? {
        if (isDisconnecting) return null
        return secrets.get(KEY_REFRESH_TOKEN)
    }

    fun hasValidToken(): Boolean {
        val token = secrets.get(KEY_ACCESS_TOKEN)
        return !token.isNullOrBlank()
    }

    // ==========================================
    // ADVANCED / SELF-HOSTED CONFIGURATION
    // ==========================================

    @Synchronized fun syncSessionId(): String {
        prefs.getString("sync_session_id", null)?.let { return it }
        val id = java.util.UUID.randomUUID().toString()
        check(prefs.edit().putString("sync_session_id", id).commit()) { "Could not retain sync ownership" }
        return id
    }

    fun getClientId(): String {
        return prefs.getString(KEY_CUSTOM_CLIENT_ID, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID
    }

    fun getCustomClientId(): String? {
        return prefs.getString(KEY_CUSTOM_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun setCustomClientId(clientId: String?) {
        if (clientId.isNullOrBlank()) {
            prefs.edit().remove(KEY_CUSTOM_CLIENT_ID).apply()
        } else {
            prefs.edit().putString(KEY_CUSTOM_CLIENT_ID, clientId.trim()).apply()
        }
        TraktClient.resetClient()
    }

    fun getBaseUrl(): String {
        return prefs.getString(KEY_CUSTOM_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
    }

    fun getCustomBaseUrl(): String? {
        return prefs.getString(KEY_CUSTOM_BASE_URL, null)?.takeIf { it.isNotBlank() }
    }

    fun setCustomBaseUrl(url: String?) {
        if (url.isNullOrBlank()) {
            prefs.edit().remove(KEY_CUSTOM_BASE_URL).apply()
        } else {
            val formatted = if (url.endsWith("/")) url else "$url/"
            prefs.edit().putString(KEY_CUSTOM_BASE_URL, formatted.trim()).apply()
        }
        TraktClient.resetClient()
    }

    fun getAuthorizeUrl(): String {
        val custom = getCustomBaseUrl()
        return if (custom != null && !custom.contains("api.trakt.tv")) {
            val root = custom.removeSuffix("/")
            "$root/oauth/authorize"
        } else {
            DEFAULT_AUTHORIZE_URL
        }
    }

    fun getRedirectUri(): String {
        return prefs.getString(KEY_CUSTOM_REDIRECT_URI, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_REDIRECT_URI
    }

    fun getCustomRedirectUri(): String? {
        return prefs.getString(KEY_CUSTOM_REDIRECT_URI, null)?.takeIf { it.isNotBlank() }
    }

    fun setCustomRedirectUri(uri: String?) {
        if (uri.isNullOrBlank()) {
            prefs.edit().remove(KEY_CUSTOM_REDIRECT_URI).apply()
        } else {
            prefs.edit().putString(KEY_CUSTOM_REDIRECT_URI, uri.trim()).apply()
        }
    }

    fun isCustomConfigured(): Boolean {
        return !getCustomClientId().isNullOrBlank() || !getCustomBaseUrl().isNullOrBlank() || !getCustomRedirectUri().isNullOrBlank()
    }

    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_CUSTOM_CLIENT_ID)
            .remove(KEY_CUSTOM_BASE_URL)
            .remove(KEY_CUSTOM_REDIRECT_URI)
            .apply()
        TraktClient.resetClient()
    }

    // ==========================================
    // SYNC PREFERENCES & METRICS
    // ==========================================

    @Synchronized
    private fun setSyncPreference(key: String, category: String, enabled: Boolean) {
        val previous = prefs.getBoolean(key, true)
        val edit = prefs.edit().putBoolean(key, enabled)
        if (enabled && !previous) edit.putString("dirty_$category", java.util.UUID.randomUUID().toString())
        edit.apply()
        if (enabled && hasValidToken()) authScope.launch {
            runCatching { com.example.util.TraktSyncWorker.enqueueOneTimeSync(context, uploadOnly = previous) }
        }
    }
    internal fun dirtyCategory(category: String): String? = prefs.getString("dirty_$category", null)
    @Synchronized
    internal fun acknowledgeCategory(category: String, generation: String?) {
        if (generation != null && dirtyCategory(category) == generation) prefs.edit().remove("dirty_$category").apply()
    }
    fun isSyncWatchedEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_WATCHED, true)
    fun setSyncWatchedEnabled(enabled: Boolean) = setSyncPreference(KEY_SYNC_WATCHED, "movies", enabled)
    fun isSyncWatchlistEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_WATCHLIST, true)
    fun setSyncWatchlistEnabled(enabled: Boolean) = setSyncPreference(KEY_SYNC_WATCHLIST, "watchlist", enabled)
    fun isSyncRatingsEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_RATINGS, true)
    fun setSyncRatingsEnabled(enabled: Boolean) = setSyncPreference(KEY_SYNC_RATINGS, "ratings", enabled)
    fun isSyncTvProgressEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_TV_PROGRESS, true)
    fun setSyncTvProgressEnabled(enabled: Boolean) = setSyncPreference(KEY_SYNC_TV_PROGRESS, "shows", enabled)

    fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    fun setLastSyncTimestamp(timestamp: Long) = prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply()

    fun getLastSyncSummary(): String? = prefs.getString(KEY_LAST_SYNC_SUMMARY, null)
    fun setLastSyncSummary(summary: String) = prefs.edit().putString(KEY_LAST_SYNC_SUMMARY, summary).apply()

    fun setSyncCheckpoint(step: String, page: Int) {
        check(prefs.edit().putString(KEY_CHECKPOINT_STEP, step).putInt(KEY_CHECKPOINT_PAGE, page.coerceAtLeast(1)).commit()) { "Could not save sync progress" }
    }
    fun getSyncCheckpointStep(): String? = prefs.getString(KEY_CHECKPOINT_STEP, null)
    fun setSyncCheckpointStep(step: String?) {
        if (step == null) prefs.edit().remove(KEY_CHECKPOINT_STEP).apply()
        else prefs.edit().putString(KEY_CHECKPOINT_STEP, step).apply()
    }

    fun getSyncCheckpointPage(): Int = prefs.getInt(KEY_CHECKPOINT_PAGE, 1)
    fun setSyncCheckpointPage(page: Int) = prefs.edit().putInt(KEY_CHECKPOINT_PAGE, page).apply()

    fun clearSyncCheckpoint() {
        prefs.edit().remove(KEY_CHECKPOINT_STEP).remove(KEY_CHECKPOINT_PAGE).apply()
    }

    // ==========================================
    // USER PROFILE & SESSION
    // ==========================================

    private fun loadCachedUser(): TraktUser? {
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val name = prefs.getString(KEY_USER_NAME, null)
        val avatar = prefs.getString(KEY_USER_AVATAR, null)
        return TraktUser(
            username = username,
            name = name,
            images = if (avatar != null) TraktUserImages(TraktUserAvatar(avatar)) else null
        )
    }

    /**
     * Starts the asynchronous Device Code authorization process and polls in a persistent supervisor scope.
     * Survives activity backgrounding, custom tabs switching, and configuration changes.
     */
    fun startDeviceAuth() {
        pollingJob?.cancel()
        _deviceAuthState.value = TraktDeviceAuthState.RequestingCode
        val currentEpoch = sessionEpoch.get()

        pollingJob = authScope.launch {
            try {
                if (isSessionInvalid(currentEpoch)) return@launch
                Log.d(TAG, "Requesting Trakt device code for client_id: ${getClientId()}...")
                val codeResponse = apiServiceProvider().getDeviceCode(
                    TraktDeviceCodeRequest(client_id = getClientId())
                )
                if (isSessionInvalid(currentEpoch)) return@launch
                Log.i(TAG, "Device code generated: user_code=${codeResponse.user_code}, verification_url=${codeResponse.verification_url}, interval=${codeResponse.interval}s, expires_in=${codeResponse.expires_in}s")

                _deviceAuthState.value = TraktDeviceAuthState.PendingApproval(
                    userCode = codeResponse.user_code,
                    verificationUrl = codeResponse.verification_url,
                    deviceCode = codeResponse.device_code,
                    statusMessage = "Enter the code at ${codeResponse.verification_url} to approve."
                )

                val startTime = System.currentTimeMillis()
                val timeoutMs = codeResponse.expires_in * 1000L
                var intervalMs = (codeResponse.interval.coerceAtLeast(6)) * 1000L
                var pollCount = 0

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    delay(intervalMs)
                    if (isSessionInvalid(currentEpoch)) return@launch
                    pollCount++
                    try {
                        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000
                        Log.d(TAG, "Device token poll #$pollCount (elapsed: ${elapsedSec}s, interval: ${intervalMs / 1000}s)...")
                        val response = apiServiceProvider().getDeviceToken(
                            TraktDeviceTokenRequest(
                                code = codeResponse.device_code,
                                client_id = getClientId()
                            )
                        )

                        if (isSessionInvalid(currentEpoch)) return@launch
                        Log.d(TAG, "Device token poll #$pollCount HTTP response code: ${response.code()}")

                        when (response.code()) {
                            200 -> {
                                val tokenResponse = response.body()
                                Log.i(TAG, "Device token poll: 200 OK. Token body present: ${tokenResponse != null}")
                                if (tokenResponse != null && tokenResponse.access_token.isNotBlank()) {
                                    if (isSessionInvalid(currentEpoch)) return@launch
                                    saveTokens(tokenResponse)
                                    val postAuthEpoch = sessionEpoch.get()
                                    Log.i(TAG, "Trakt tokens successfully saved! (expires in: ${tokenResponse.expires_in}s)")

                                    // Immediately transition UI to Success so the dialog updates instantly
                                    _deviceAuthState.value = TraktDeviceAuthState.Success(_currentUser.value)

                                    // Run non-blocking background tasks for user profile and initial sync
                                    authScope.launch {
                                        try {
                                            if (isSessionInvalid(postAuthEpoch)) return@launch
                                            Log.d(TAG, "Fetching user profile post-auth...")
                                            val userResult = fetchAndSaveUserProfile()
                                            val user = userResult.getOrNull()
                                            if (user != null && !isSessionInvalid(postAuthEpoch)) {
                                                Log.i(TAG, "Trakt profile loaded: @${user.username}")
                                                _deviceAuthState.value = TraktDeviceAuthState.Success(user)
                                            }
                                        } catch (e: Exception) {
                                            if (e is CancellationException) throw e
                                            Log.w(TAG, "Non-fatal: failed to fetch profile: ${e.message}")
                                        }

                                        try {
                                            if (isSessionInvalid(postAuthEpoch)) return@launch
                                            Log.d(TAG, "Enqueuing initial Trakt sync...")
                                            com.example.util.TraktSyncWorker.enqueueOneTimeSync(context)
                                        } catch (e: Exception) {
                                            if (e is CancellationException) throw e
                                            Log.w(TAG, "Failed to enqueue initial sync: ${e.message}")
                                        }
                                    }

                                    delay(1800)
                                    if (!isSessionInvalid(postAuthEpoch)) {
                                        _deviceAuthState.value = TraktDeviceAuthState.Idle
                                    }
                                    return@launch
                                } else {
                                    val errStr = response.errorBody()?.string() ?: "Empty body"
                                    Log.e(TAG, "Device token returned 200 OK but token body is empty or invalid: $errStr")
                                    _deviceAuthState.value = TraktDeviceAuthState.Error("Failed to parse Trakt token response.")
                                    return@launch
                                }
                            }
                            400 -> {
                                // Pending approval from user - keep polling
                                Log.d(TAG, "Device token poll #$pollCount: 400 (Pending user approval). Continuing to poll...")
                                val current = _deviceAuthState.value
                                if (current is TraktDeviceAuthState.PendingApproval) {
                                    _deviceAuthState.value = current.copy(
                                        statusMessage = "Waiting for approval at ${current.verificationUrl}..."
                                    )
                                }
                            }
                            404 -> {
                                Log.e(TAG, "Device token poll #$pollCount: 404 (Not Found). Device code invalid or not recognized.")
                                _deviceAuthState.value = TraktDeviceAuthState.Error("Device activation code was not found. Please try again.")
                                return@launch
                            }
                            409 -> {
                                Log.w(TAG, "Device token poll #$pollCount: 409 (Conflict - code already used).")
                                if (hasValidToken()) {
                                    _deviceAuthState.value = TraktDeviceAuthState.Success(_currentUser.value)
                                    delay(1500)
                                    _deviceAuthState.value = TraktDeviceAuthState.Idle
                                    return@launch
                                } else {
                                    _deviceAuthState.value = TraktDeviceAuthState.Error("Activation code has already been used. Please try again.")
                                    return@launch
                                }
                            }
                            410 -> {
                                Log.e(TAG, "Device token poll #$pollCount: 410 (Gone). Activation code expired.")
                                _deviceAuthState.value = TraktDeviceAuthState.Error("Activation code expired. Please try again.")
                                return@launch
                            }
                            418 -> {
                                Log.e(TAG, "Device token poll #$pollCount: 418 (Denied). User denied authorization.")
                                _deviceAuthState.value = TraktDeviceAuthState.Error("Authorization was denied on Trakt.")
                                return@launch
                            }
                            429 -> {
                                Log.w(TAG, "Device token poll #$pollCount: 429 (Too Many Requests). Backing off interval.")
                                intervalMs += 2000L
                            }
                            else -> {
                                Log.w(TAG, "Device token poll #$pollCount: Unexpected HTTP status ${response.code()}")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Device token poll iteration error: ${e.message}", e)
                    }
                }

                if (!isSessionInvalid(currentEpoch)) {
                    _deviceAuthState.value = TraktDeviceAuthState.Error("Verification timed out. Please try again.")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Device auth job cancelled normally.")
                throw e
            } catch (e: Exception) {
                if (!isSessionInvalid(currentEpoch)) {
                    Log.e(TAG, "Failed device authorization flow: ${e.message}", e)
                    _deviceAuthState.value = TraktDeviceAuthState.Error(e.localizedMessage ?: "Failed to connect to Trakt.")
                }
            }
        }
    }

    private val secureRandom = SecureRandom()

    private fun base64UrlEncode(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
        } catch (e: Exception) {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }

    fun generateCodeVerifier(): String {
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        return base64UrlEncode(randomBytes)
    }

    fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return base64UrlEncode(digest)
    }

    fun generateState(): String {
        val randomBytes = ByteArray(16)
        secureRandom.nextBytes(randomBytes)
        return base64UrlEncode(randomBytes)
    }

    /**
     * Creates the authorization URI and persists the verifier, state, timestamp, and redirect URI
     * for process-death resilience during browser authorization.
     */
    fun createPkceAuthorizationUri(
        clientId: String = getClientId(),
        redirectUri: String = getRedirectUri()
    ): Pair<Uri, String> {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateState()

        prefs.edit()
            .putString(KEY_PKCE_CODE_VERIFIER, verifier)
            .putString(KEY_PKCE_STATE, state)
            .putLong(KEY_PKCE_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_PKCE_REDIRECT_URI, redirectUri)
            .apply()

        val uri = Uri.parse(getAuthorizeUrl()).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        return Pair(uri, state)
    }

    /**
     * Starts PKCE Authorization Code flow: generates cryptographic parameters,
     * sets state to [TraktDeviceAuthState.AuthorizingBrowser], and opens the Trakt authorization
     * URL in Chrome Custom Tabs / external browser.
     */
    fun startPkceAuth(context: Context) {
        pollingJob?.cancel()
        pollingJob = null
        val (authUri, _) = createPkceAuthorizationUri()
        _deviceAuthState.value = TraktDeviceAuthState.AuthorizingBrowser(authUri)
        try {
            com.example.util.CobaltCustomTabsLauncher.launch(context, authUri.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch custom tab for PKCE: ${e.message}", e)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, authUri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Validates and handles an OAuth redirect callback intent from Trakt.
     * Prevents replay attacks by invalidating pending PKCE state immediately.
     * Returns true if the callback was recognized and processed, false otherwise.
     */
    fun handleAuthCallback(uri: Uri): Boolean {
        val currentEpoch = sessionEpoch.get()
        if (isSessionInvalid(currentEpoch)) return false
        val expected = Uri.parse(prefs.getString(KEY_PKCE_REDIRECT_URI, null) ?: getRedirectUri())
        if (uri.scheme != expected.scheme || uri.authority != expected.authority || uri.path != expected.path) return false

        val error = uri.getQueryParameter("error")
        val errorDescription = uri.getQueryParameter("error_description")
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")

        val savedVerifier = prefs.getString(KEY_PKCE_CODE_VERIFIER, null)
        val savedState = prefs.getString(KEY_PKCE_STATE, null)
        val savedTimestamp = prefs.getLong(KEY_PKCE_TIMESTAMP, 0L)
        val savedRedirectUri = prefs.getString(KEY_PKCE_REDIRECT_URI, null) ?: getRedirectUri()

        // Invalidate pending PKCE state immediately to prevent replay attacks
        clearPendingPkce()

        if (error != null) {
            val userMsg = if (error == "access_denied") {
                "Authorization was cancelled or denied on Trakt."
            } else {
                errorDescription ?: "Trakt authorization error: $error"
            }
            _deviceAuthState.value = TraktDeviceAuthState.Error(userMsg)
            return false
        }

        if (savedVerifier.isNullOrBlank() || savedState.isNullOrBlank()) {
            _deviceAuthState.value = TraktDeviceAuthState.Error("No pending Trakt authorization request found.")
            return false
        }

        if (System.currentTimeMillis() - savedTimestamp > PKCE_EXPIRATION_MS) {
            _deviceAuthState.value = TraktDeviceAuthState.Error("Trakt authorization session expired. Please try again.")
            return false
        }

        if (savedState != state) {
            _deviceAuthState.value = TraktDeviceAuthState.Error("Invalid authorization state (possible CSRF). Authorization rejected.")
            return false
        }

        if (code.isNullOrBlank()) {
            _deviceAuthState.value = TraktDeviceAuthState.Error("No authorization code received from Trakt.")
            return false
        }

        _deviceAuthState.value = TraktDeviceAuthState.ExchangingToken

        authScope.launch {
            try {
                if (isSessionInvalid(currentEpoch)) return@launch
                val response = apiServiceProvider().exchangeToken(
                    TraktTokenRequest(
                        code = code,
                        client_id = getClientId(),
                        code_verifier = savedVerifier,
                        redirect_uri = savedRedirectUri,
                        grant_type = "authorization_code"
                    )
                )

                if (isSessionInvalid(currentEpoch)) return@launch

                if (response.isSuccessful) {
                    val tokenResponse = response.body()
                    if (tokenResponse != null && tokenResponse.access_token.isNotBlank()) {
                        saveTokens(tokenResponse)
                        val postAuthEpoch = sessionEpoch.get()
                        Log.i(TAG, "PKCE Trakt tokens successfully saved! (expires in: ${tokenResponse.expires_in}s)")

                        _deviceAuthState.value = TraktDeviceAuthState.Success(_currentUser.value)

                        authScope.launch {
                            try {
                                if (isSessionInvalid(postAuthEpoch)) return@launch
                                val userResult = fetchAndSaveUserProfile()
                                val user = userResult.getOrNull()
                                if (user != null && !isSessionInvalid(postAuthEpoch)) {
                                    _deviceAuthState.value = TraktDeviceAuthState.Success(user)
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w(TAG, "Non-fatal: failed to fetch profile: ${e.message}")
                            }

                            try {
                                if (isSessionInvalid(postAuthEpoch)) return@launch
                                com.example.util.TraktSyncWorker.enqueueOneTimeSync(context)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w(TAG, "Failed to enqueue initial sync: ${e.message}")
                            }
                        }

                        delay(1800)
                        if (!isSessionInvalid(postAuthEpoch)) {
                            _deviceAuthState.value = TraktDeviceAuthState.Idle
                        }
                    } else {
                        val errStr = response.errorBody()?.string() ?: "Empty body"
                        Log.e(TAG, "exchangeToken returned 200 OK but token body is empty: $errStr")
                        _deviceAuthState.value = TraktDeviceAuthState.Error("Failed to parse Trakt token response.")
                    }
                } else {
                    val errStr = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                    Log.e(TAG, "exchangeToken failed with HTTP ${response.code()}: $errStr")
                    _deviceAuthState.value = TraktDeviceAuthState.Error("Trakt sign-in failed (${response.code()}). Please try again.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isSessionInvalid(currentEpoch)) {
                    Log.e(TAG, "PKCE token exchange exception: ${e.message}", e)
                    _deviceAuthState.value = TraktDeviceAuthState.Error(e.localizedMessage ?: "Failed to complete Trakt connection.")
                }
            }
        }
        return true
    }

    fun clearPendingPkce() {
        prefs.edit()
            .remove(KEY_PKCE_CODE_VERIFIER)
            .remove(KEY_PKCE_STATE)
            .remove(KEY_PKCE_TIMESTAMP)
            .remove(KEY_PKCE_REDIRECT_URI)
            .apply()
    }

    /**
     * Cancels active authentication (device code polling or PKCE pending state) and resets state to Idle.
     */
    fun cancelAuth() {
        pollingJob?.cancel()
        pollingJob = null
        clearPendingPkce()
        _deviceAuthState.value = TraktDeviceAuthState.Idle
    }

    /**
     * Cancels active device authentication and resets state to Idle.
     */
    fun cancelDeviceAuth() = cancelAuth()

    fun saveTokens(token: TraktTokenResponse, newSession: Boolean = true) {
        if (isDisconnecting) return
        if (newSession) sessionEpoch.incrementAndGet()
        val expiresAt = System.currentTimeMillis() + (token.expires_in * 1000L)
        secrets.putAll(mapOf(KEY_ACCESS_TOKEN to token.access_token, KEY_REFRESH_TOKEN to token.refresh_token))
        prefs.edit().putLong(KEY_EXPIRES_AT, expiresAt).apply()

        _isConnected.value = true
    }

    /**
     * Fetches the user profile from /users/me and caches it locally.
     */
    suspend fun fetchAndSaveUserProfile(): Result<TraktUser> = withContext(Dispatchers.IO) {
        val startEpoch = sessionEpoch.get()
        if (isSessionInvalid(startEpoch)) {
            return@withContext Result.failure(IllegalStateException("Session disconnected or invalid"))
        }
        try {
            val user = apiServiceProvider().getCurrentUser()
            if (isSessionInvalid(startEpoch)) {
                return@withContext Result.failure(IllegalStateException("Session disconnected or invalid"))
            }
            prefs.edit()
                .putString(KEY_USERNAME, user.username)
                .putString(KEY_USER_NAME, user.name)
                .putString(KEY_USER_AVATAR, user.images?.avatar?.full)
                .apply()
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to fetch user profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Refreshes the access token using the stored refresh token if expired.
     */
    suspend fun refreshAccessTokenIfNeeded(): String? = refreshMutex.withLock { withContext(Dispatchers.IO) {
        val startEpoch = sessionEpoch.get()
        if (isSessionInvalid(startEpoch)) return@withContext null

        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()
        val currentAccessToken = secrets.get(KEY_ACCESS_TOKEN)

        // Refresh if within 2 days of expiration or already expired
        val twoDaysMs = 2 * 24 * 60 * 60 * 1000L
        if (now + twoDaysMs < expiresAt && !currentAccessToken.isNullOrBlank()) {
            return@withContext currentAccessToken
        }

        val refreshToken = secrets.get(KEY_REFRESH_TOKEN) ?: return@withContext null

        try {
            val response = apiServiceProvider().refreshToken(
                TraktRefreshTokenRequest(
                    client_id = getClientId(),
                    refresh_token = refreshToken,
                    redirect_uri = getRedirectUri()
                )
            )
            if (isSessionInvalid(startEpoch)) {
                Log.w(TAG, "Token refreshed but session was invalidated during call; discarding.")
                return@withContext null
            }
            // Token rotation belongs to the same account/session. Do not invalidate its sync.
            sessionMutex.withLock {
                if (isSessionInvalid(startEpoch)) return@withContext null
                saveTokens(response, newSession = false)
            }
            return@withContext response.access_token
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to refresh Trakt access token: ${e.message}", e)
            return@withContext if (!isSessionInvalid(startEpoch)) currentAccessToken else null
        }
    }
    }

    /**
     * Disconnects and wipes all Trakt tokens, session data, and cached profiles.
     * Executes in NonCancellable context so local credential clearing completes even if
     * the caller coroutine is cancelled.
     * Attempts server-side token revocation with bounded timeout.
     * Preserves custom client ID, base URL, and redirect URI if configured.
     */
    suspend fun disconnect(): TraktDisconnectResult = withContext(Dispatchers.IO + NonCancellable) {
        disconnectMutex.withLock {
            val tokenToRevoke = secrets.get(KEY_ACCESS_TOKEN)
            val clientId = getClientId()
            val customClientId = getCustomClientId()
            val customBaseUrl = getCustomBaseUrl()
            val customRedirectUri = getCustomRedirectUri()

            if (tokenToRevoke.isNullOrBlank() && !hasValidToken() && _currentUser.value == null) {
                return@withContext TraktDisconnectResult(
                    success = true,
                    revokedOnServer = false,
                    userMessage = "Trakt is already disconnected."
                )
            }

            try {
                isDisconnecting = true

                sessionMutex.withLock {
                    sessionEpoch.incrementAndGet()

                    // 1. Cancel active in-flight authentication polling or PKCE pending state
                    cancelAuth()

                    // 2. Stop and cancel all WorkManager background sync jobs
                    try {
                        com.example.util.TraktSyncWorker.cancelAll(context)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "WorkManager cancellation notice: ${e.message}")
                    }

                    // 3. Invalidate sync engine
                    TraktSyncEngine.onSessionInvalidated()

                    com.example.data.local.AppDatabase.getDatabase(context).traktOutboxDao().clearSession(syncSessionId())

                    // 4. Wipe local credentials, session data, user profiles, and sync checkpoints
                    prefs.edit().clear().apply()
                    secrets.putAll(mapOf(KEY_ACCESS_TOKEN to null, KEY_REFRESH_TOKEN to null))
                    if (customClientId != null) setCustomClientId(customClientId)
                    if (customBaseUrl != null) setCustomBaseUrl(customBaseUrl)
                    if (customRedirectUri != null) setCustomRedirectUri(customRedirectUri)
                    _isConnected.value = false
                    _currentUser.value = null
                }

                // 5. Attempt remote server-side revocation bounded by timeout
                var serverRevoked = false
                if (!tokenToRevoke.isNullOrBlank()) {
                    serverRevoked = try {
                        withTimeoutOrNull(8000L) {
                            try {
                                val response = apiServiceProvider().revokeToken(
                                    authorization = "Bearer $tokenToRevoke",
                                    request = TraktRevokeTokenRequest(
                                        token = tokenToRevoke,
                                        client_id = clientId
                                    )
                                )
                                response.isSuccessful
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w(TAG, "Remote revocation network call failed: ${e.message}")
                                false
                            }
                        } ?: false
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Remote revocation error: ${e.message}")
                        false
                    }
                }

                val userMessage = if (serverRevoked) {
                    "Disconnected from Trakt. Server authorization was revoked."
                } else {
                    "Disconnected locally. To revoke access on Trakt, visit trakt.tv/oauth/authorized_applications."
                }

                TraktDisconnectResult(
                    success = true,
                    revokedOnServer = serverRevoked,
                    userMessage = userMessage
                )
            } finally {
                isDisconnecting = false
            }
        }
    }

    companion object {
        const val DEFAULT_CLIENT_ID = "dj1F8ZeR5jYd9_Bs8ZeKRJOP-B3kydXanE43vwHW9tQ"
        const val DEFAULT_BASE_URL = "https://api.trakt.tv/"
        const val DEFAULT_AUTHORIZE_URL = "https://auth.trakt.tv/oauth/authorize"
        const val DEFAULT_REDIRECT_URI = "https://cobalt.hanan-bhatti.site/callback"
        const val PKCE_EXPIRATION_MS = 10 * 60 * 1000L // 10 minutes

        private const val TAG = "TraktAuthManager"
        private const val PREFS_NAME = "cobalt_trakt_prefs"
        private const val KEY_ACCESS_TOKEN = "trakt_access_token"
        private const val KEY_REFRESH_TOKEN = "trakt_refresh_token"
        private const val KEY_EXPIRES_AT = "trakt_expires_at"
        private const val KEY_USERNAME = "trakt_username"
        private const val KEY_USER_NAME = "trakt_user_name"
        private const val KEY_USER_AVATAR = "trakt_user_avatar"

        private const val KEY_CUSTOM_CLIENT_ID = "trakt_custom_client_id"
        private const val KEY_CUSTOM_BASE_URL = "trakt_custom_base_url"
        private const val KEY_CUSTOM_REDIRECT_URI = "trakt_custom_redirect_uri"

        private const val KEY_PKCE_CODE_VERIFIER = "trakt_pkce_code_verifier"
        private const val KEY_PKCE_STATE = "trakt_pkce_state"
        private const val KEY_PKCE_TIMESTAMP = "trakt_pkce_timestamp"
        private const val KEY_PKCE_REDIRECT_URI = "trakt_pkce_redirect_uri"

        private const val KEY_SYNC_WATCHED = "trakt_sync_watched"
        private const val KEY_SYNC_WATCHLIST = "trakt_sync_watchlist"
        private const val KEY_SYNC_RATINGS = "trakt_sync_ratings"
        private const val KEY_SYNC_TV_PROGRESS = "trakt_sync_tv_progress"
        private const val KEY_LAST_SYNC_TIMESTAMP = "trakt_last_sync_timestamp"
        private const val KEY_LAST_SYNC_SUMMARY = "trakt_last_sync_summary"
        private const val KEY_CHECKPOINT_STEP = "trakt_checkpoint_step"
        private const val KEY_CHECKPOINT_PAGE = "trakt_checkpoint_page"

        @Volatile
        private var instance: TraktAuthManager? = null

        fun getInstance(context: Context): TraktAuthManager {
            return instance ?: synchronized(this) {
                instance ?: TraktAuthManager(context.applicationContext).also { instance = it }
            }
        }

        fun setInstanceForTesting(manager: TraktAuthManager?) {
            synchronized(this) {
                instance = manager
            }
        }
    }
}

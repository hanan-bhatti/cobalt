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
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class TraktDeviceAuthState {
    object Idle : TraktDeviceAuthState()
    object RequestingCode : TraktDeviceAuthState()
    data class PendingApproval(
        val userCode: String,
        val verificationUrl: String,
        val deviceCode: String,
        val statusMessage: String = "Waiting for approval at trakt.tv/activate..."
    ) : TraktDeviceAuthState()
    data class Success(val user: TraktUser?) : TraktDeviceAuthState()
    data class Error(val message: String) : TraktDeviceAuthState()
}

/**
 * Manages Trakt OAuth2 Device Code authentication, tokens, custom client credentials,
 * and active session state.
 */
class TraktAuthManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val authScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    init {
        TraktClient.tokenProvider = { getAccessToken() }
        TraktClient.clientIdProvider = { getClientId() }
        TraktClient.baseUrlProvider = { getBaseUrl() }
    }

    private val _isConnected = MutableStateFlow(hasValidToken())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentUser = MutableStateFlow(loadCachedUser())
    val currentUser: StateFlow<TraktUser?> = _currentUser.asStateFlow()

    private val _deviceAuthState = MutableStateFlow<TraktDeviceAuthState>(TraktDeviceAuthState.Idle)
    val deviceAuthState: StateFlow<TraktDeviceAuthState> = _deviceAuthState.asStateFlow()

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun hasValidToken(): Boolean {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        return !token.isNullOrBlank()
    }

    // ==========================================
    // ADVANCED / SELF-HOSTED CONFIGURATION
    // ==========================================

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

    fun isCustomConfigured(): Boolean {
        return !getCustomClientId().isNullOrBlank() || !getCustomBaseUrl().isNullOrBlank()
    }

    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_CUSTOM_CLIENT_ID)
            .remove(KEY_CUSTOM_BASE_URL)
            .apply()
        TraktClient.resetClient()
    }

    // ==========================================
    // SYNC PREFERENCES & METRICS
    // ==========================================

    fun isSyncWatchedEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_WATCHED, true)
    fun setSyncWatchedEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SYNC_WATCHED, enabled).apply()

    fun isSyncWatchlistEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_WATCHLIST, true)
    fun setSyncWatchlistEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SYNC_WATCHLIST, enabled).apply()

    fun isSyncRatingsEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_RATINGS, true)
    fun setSyncRatingsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SYNC_RATINGS, enabled).apply()

    fun isSyncTvProgressEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_TV_PROGRESS, true)
    fun setSyncTvProgressEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SYNC_TV_PROGRESS, enabled).apply()

    fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    fun setLastSyncTimestamp(timestamp: Long) = prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply()

    fun getLastSyncSummary(): String? = prefs.getString(KEY_LAST_SYNC_SUMMARY, null)
    fun setLastSyncSummary(summary: String) = prefs.edit().putString(KEY_LAST_SYNC_SUMMARY, summary).apply()

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

        pollingJob = authScope.launch {
            try {
                Log.d(TAG, "Requesting Trakt device code for client_id: ${getClientId()}...")
                val codeResponse = TraktClient.apiService.getDeviceCode(
                    TraktDeviceCodeRequest(client_id = getClientId())
                )
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
                    pollCount++
                    try {
                        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000
                        Log.d(TAG, "Device token poll #$pollCount (elapsed: ${elapsedSec}s, interval: ${intervalMs / 1000}s)...")
                        val response = TraktClient.apiService.getDeviceToken(
                            TraktDeviceTokenRequest(
                                code = codeResponse.device_code,
                                client_id = getClientId()
                            )
                        )

                        Log.d(TAG, "Device token poll #$pollCount HTTP response code: ${response.code()}")

                        when (response.code()) {
                            200 -> {
                                val tokenResponse = response.body()
                                Log.i(TAG, "Device token poll: 200 OK. Token body present: ${tokenResponse != null}")
                                if (tokenResponse != null && tokenResponse.access_token.isNotBlank()) {
                                    saveTokens(tokenResponse)
                                    Log.i(TAG, "Trakt tokens successfully saved! (expires in: ${tokenResponse.expires_in}s)")

                                    // Immediately transition UI to Success so the dialog updates instantly
                                    _deviceAuthState.value = TraktDeviceAuthState.Success(_currentUser.value)

                                    // Run non-blocking background tasks for user profile and initial sync
                                    authScope.launch {
                                        try {
                                            Log.d(TAG, "Fetching user profile post-auth...")
                                            val userResult = fetchAndSaveUserProfile()
                                            val user = userResult.getOrNull()
                                            if (user != null) {
                                                Log.i(TAG, "Trakt profile loaded: @${user.username}")
                                                _deviceAuthState.value = TraktDeviceAuthState.Success(user)
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Non-fatal: failed to fetch profile: ${e.message}")
                                        }

                                        try {
                                            Log.d(TAG, "Enqueuing initial Trakt sync...")
                                            com.example.util.TraktSyncWorker.enqueueOneTimeSync(context)
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Failed to enqueue initial sync: ${e.message}")
                                        }
                                    }

                                    delay(1800)
                                    _deviceAuthState.value = TraktDeviceAuthState.Idle
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
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Device token poll iteration error: ${e.message}", e)
                    }
                }

                _deviceAuthState.value = TraktDeviceAuthState.Error("Verification timed out. Please try again.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Device auth job cancelled normally.")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed device authorization flow: ${e.message}", e)
                _deviceAuthState.value = TraktDeviceAuthState.Error(e.localizedMessage ?: "Failed to connect to Trakt.")
            }
        }
    }

    /**
     * Cancels active device authentication and resets state to Idle.
     */
    fun cancelDeviceAuth() {
        pollingJob?.cancel()
        pollingJob = null
        _deviceAuthState.value = TraktDeviceAuthState.Idle
    }

    fun saveTokens(token: TraktTokenResponse) {
        val expiresAt = System.currentTimeMillis() + (token.expires_in * 1000L)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token.access_token)
            .putString(KEY_REFRESH_TOKEN, token.refresh_token)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()

        _isConnected.value = true
    }

    /**
     * Fetches the user profile from /users/me and caches it locally.
     */
    suspend fun fetchAndSaveUserProfile(): Result<TraktUser> = withContext(Dispatchers.IO) {
        try {
            val user = TraktClient.apiService.getCurrentUser()
            prefs.edit()
                .putString(KEY_USERNAME, user.username)
                .putString(KEY_USER_NAME, user.name)
                .putString(KEY_USER_AVATAR, user.images?.avatar?.full)
                .apply()
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Refreshes the access token using the stored refresh token if expired.
     */
    suspend fun refreshAccessTokenIfNeeded(): String? = withContext(Dispatchers.IO) {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()
        val currentAccessToken = prefs.getString(KEY_ACCESS_TOKEN, null)

        // Refresh if within 2 days of expiration or already expired
        val twoDaysMs = 2 * 24 * 60 * 60 * 1000L
        if (now + twoDaysMs < expiresAt && !currentAccessToken.isNullOrBlank()) {
            return@withContext currentAccessToken
        }

        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext null

        try {
            val response = TraktClient.apiService.refreshToken(
                TraktRefreshTokenRequest(
                    client_id = getClientId(),
                    refresh_token = refreshToken
                )
            )
            saveTokens(response)
            return@withContext response.access_token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh Trakt access token: ${e.message}", e)
            return@withContext currentAccessToken
        }
    }

    /**
     * Disconnects and wipes all Trakt tokens, session data, and cached profiles.
     * Preserves custom client ID and custom base URL if configured.
     */
    fun disconnect() {
        val customClientId = getCustomClientId()
        val customBaseUrl = getCustomBaseUrl()
        prefs.edit().clear().apply()
        if (customClientId != null) setCustomClientId(customClientId)
        if (customBaseUrl != null) setCustomBaseUrl(customBaseUrl)
        _isConnected.value = false
        _currentUser.value = null
    }

    companion object {
        const val DEFAULT_CLIENT_ID = "dj1F8ZeR5jYd9_Bs8ZeKRJOP-B3kydXanE43vwHW9tQ"
        const val DEFAULT_BASE_URL = "https://api.trakt.tv/"
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
    }
}

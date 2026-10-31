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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages Trakt OAuth2 Device Code authentication, tokens, custom client credentials,
 * and active session state.
 */
class TraktAuthManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        TraktClient.tokenProvider = { getAccessToken() }
        TraktClient.clientIdProvider = { getClientId() }
        TraktClient.baseUrlProvider = { getBaseUrl() }
    }

    private val _isConnected = MutableStateFlow(hasValidToken())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentUser = MutableStateFlow(loadCachedUser())
    val currentUser: StateFlow<TraktUser?> = _currentUser.asStateFlow()

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
     * Step 1 of Device Code Flow: Requests a unique verification code from Trakt.
     */
    suspend fun requestDeviceCode(): Result<TraktDeviceCodeResponse> = withContext(Dispatchers.IO) {
        try {
            val response = TraktClient.apiService.getDeviceCode(
                TraktDeviceCodeRequest(client_id = getClientId())
            )
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device code: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Step 2 of Device Code Flow: Polls Trakt until user approves at trakt.tv/activate.
     */
    suspend fun pollForToken(
        deviceCode: String,
        intervalSeconds: Int = 5,
        expiresInSeconds: Int = 600,
        onProgress: (statusMessage: String) -> Unit = {}
    ): Result<TraktTokenResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val timeoutMs = expiresInSeconds * 1000L
        val pollIntervalMs = (intervalSeconds.coerceAtLeast(5)) * 1000L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            delay(pollIntervalMs)
            try {
                val response = TraktClient.apiService.getDeviceToken(
                    TraktDeviceTokenRequest(
                        client_id = getClientId(),
                        device_code = deviceCode
                    )
                )

                when (response.code()) {
                    200 -> {
                        val tokenResponse = response.body()
                        if (tokenResponse != null) {
                            saveTokens(tokenResponse)
                            fetchAndSaveUserProfile()
                            return@withContext Result.success(tokenResponse)
                        }
                    }
                    400 -> {
                        // Pending approval from user - continue polling
                        onProgress("Waiting for approval at trakt.tv/activate...")
                    }
                    404 -> {
                        // Slow down
                        onProgress("Polling too fast, adjusting interval...")
                        delay(2000)
                    }
                    409 -> {
                        return@withContext Result.failure(Exception("Activation code expired. Please try again."))
                    }
                    410 -> {
                        return@withContext Result.failure(Exception("Access was denied by the user."))
                    }
                    else -> {
                        Log.w(TAG, "Unexpected poll status: ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Poll iteration error: ${e.message}")
            }
        }

        Result.failure(Exception("Verification timed out. Please try again."))
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

        @Volatile
        private var instance: TraktAuthManager? = null

        fun getInstance(context: Context): TraktAuthManager {
            return instance ?: synchronized(this) {
                instance ?: TraktAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

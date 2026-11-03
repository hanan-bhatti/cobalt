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
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TraktDisconnectTest {

    private lateinit var context: Context
    private lateinit var fakeApiService: FakeTraktApiService
    private lateinit var fakeCredentialStore: FakeTraktCredentialStore
    private lateinit var authManager: TraktAuthManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        try {
            val config = androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
                .build()
            androidx.work.WorkManager.initialize(context, config)
        } catch (_: Exception) {}

        fakeApiService = FakeTraktApiService()
        fakeCredentialStore = FakeTraktCredentialStore()
        authManager = TraktAuthManager(
            context = context,
            apiServiceProvider = { fakeApiService },
            credentialStore = fakeCredentialStore
        )
        TraktAuthManager.setInstanceForTesting(authManager)
        TraktSyncEngine.apiServiceProvider = { fakeApiService }

        context.getSharedPreferences("cobalt_trakt_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @After
    fun tearDown() {
        TraktAuthManager.setInstanceForTesting(null)
        TraktSyncEngine.apiServiceProvider = null
    }

    private fun logInWithMockTokens(accessToken: String = "token_abc_123", refreshToken: String = "refresh_xyz_456") {
        authManager.saveTokens(
            TraktTokenResponse(
                access_token = accessToken,
                refresh_token = refreshToken,
                expires_in = 7200L
            )
        )
    }

    @Test
    fun testSuccessfulRevocation_clearsCredentialsAndReportsRevoked() = runBlocking {
        logInWithMockTokens("valid_token_001")
        authManager.setCustomClientId("custom_client_id_test")
        authManager.setCustomBaseUrl("https://trakt-proxy.example.com/")

        assertTrue(authManager.hasValidToken())
        assertTrue(authManager.isConnected.value)
        assertEquals("valid_token_001", authManager.getAccessToken())

        val result = authManager.disconnect()

        assertTrue(result.success)
        assertTrue(result.revokedOnServer)
        assertTrue(result.userMessage.contains("Server authorization was revoked", ignoreCase = true))

        assertTrue(fakeApiService.revokeCalled)
        assertEquals("valid_token_001", fakeApiService.lastRevokeRequest?.token)
        assertEquals("custom_client_id_test", fakeApiService.lastRevokeRequest?.client_id)
        assertEquals("Bearer valid_token_001", fakeApiService.lastRevokeAuthHeader)

        assertFalse(authManager.hasValidToken())
        assertFalse(authManager.isConnected.value)
        assertNull(authManager.getAccessToken())
        assertNull(authManager.currentUser.value)

        // Custom configurations must be preserved
        assertEquals("custom_client_id_test", authManager.getCustomClientId())
        assertEquals("https://trakt-proxy.example.com/", authManager.getCustomBaseUrl())
    }

    @Test
    fun testOfflineOrNetworkError_stillClearsLocalCredentialsAndReportsFailure() = runBlocking {
        logInWithMockTokens("token_offline_002")
        fakeApiService.shouldFailRevoke = true

        val result = authManager.disconnect()

        assertTrue(result.success)
        assertFalse(result.revokedOnServer)
        assertTrue(result.userMessage.contains("Disconnected locally", ignoreCase = true))
        assertTrue(result.userMessage.contains("trakt.tv", ignoreCase = true))

        assertFalse(authManager.hasValidToken())
        assertFalse(authManager.isConnected.value)
        assertNull(authManager.getAccessToken())
        assertNull(authManager.currentUser.value)
    }

    @Test
    fun testTimeoutDuringRevocation_clearsCredentialsAndDoesNotHang() = runBlocking {
        logInWithMockTokens("token_hang_003")
        fakeApiService.revokeDelayMs = 12000L // Longer than 8000ms bounded timeout

        val result = authManager.disconnect()

        assertTrue(result.success)
        assertFalse(result.revokedOnServer)
        assertFalse(authManager.hasValidToken())
        assertNull(authManager.getAccessToken())
        assertTrue(result.userMessage.contains("Disconnected locally", ignoreCase = true))
    }

    @Test
    fun testDisconnectCancelsPendingWorkAndResetsState() = runBlocking {
        logInWithMockTokens()
        authManager.startDeviceAuth()

        val disconnectResult = authManager.disconnect()

        assertTrue(disconnectResult.success)
        assertEquals(TraktDeviceAuthState.Idle, authManager.deviceAuthState.value)
        assertEquals(TraktSyncStatus.Idle, TraktSyncEngine.syncStatus.value)
    }

    @Test
    fun testDisconnectDuringTokenRefresh_cannotRestoreSession() = runBlocking {
        logInWithMockTokens("old_expired_token")
        fakeApiService.refreshTokenDelayMs = 250L

        // Launch refreshAccessTokenIfNeeded in background
        val refreshJob = async(Dispatchers.IO) {
            authManager.refreshAccessTokenIfNeeded()
        }

        delay(50L) // Wait for refresh call to enter in-flight state
        val disconnectResult = authManager.disconnect()
        assertTrue(disconnectResult.success)

        val refreshedToken = refreshJob.await()
        assertNull(refreshedToken)

        // Verify fresh token from server was discarded and not persisted
        assertFalse(authManager.hasValidToken())
        assertNull(authManager.getAccessToken())
        assertFalse(authManager.isConnected.value)
    }

    @Test
    fun testDisconnectDuringSync_preventsStaleResultsFromApplying() = runBlocking {
        logInWithMockTokens("sync_token_test")

        // Seed some fake items that the api service returns
        fakeApiService.watchedShowsToReturn = listOf(
            TraktWatchedShowItem(
                plays = 1,
                last_watched_at = "2026-01-01T00:00:00.000Z",
                show = TraktShow(
                    title = "Stale Test Show",
                    ids = TraktIds(trakt = 99999L)
                )
            )
        )
        fakeApiService.getWatchedShowsDelayMs = 200L

        val syncJob = async(Dispatchers.IO) {
            TraktSyncEngine.performSync(context)
        }

        delay(50L) // Wait for sync to start and enter in-flight network call

        // Disconnect while sync is running in flight
        val disconnectResult = authManager.disconnect()
        assertTrue(disconnectResult.success)

        val syncResult = syncJob.await()
        assertTrue(syncResult.isFailure)
        assertEquals(TraktSyncStatus.Idle, TraktSyncEngine.syncStatus.value)
        assertFalse(authManager.hasValidToken())

        // Verify the stale item was NOT inserted into Room database
        val db = AppDatabase.getDatabase(context)
        val staleItem = db.savedItemDao().getItemByTraktId(99999L)
        assertNull(staleItem)
    }

    @Test
    fun testRepeatedDisconnectCalls_areSafeAndIdempotent() = runBlocking {
        logInWithMockTokens("token_repeat_123")

        val result1 = async(Dispatchers.IO) { authManager.disconnect() }
        val result2 = async(Dispatchers.IO) { authManager.disconnect() }

        val r1 = result1.await()
        val r2 = result2.await()

        assertTrue(r1.success)
        assertTrue(r2.success)
        assertFalse(authManager.hasValidToken())
        assertNull(authManager.getAccessToken())

        // Third synchronous call after fully disconnected
        val r3 = authManager.disconnect()
        assertTrue(r3.success)
        assertFalse(r3.revokedOnServer)
        assertEquals("Trakt is already disconnected.", r3.userMessage)
    }

    @Test
    fun testReconnectingWithNewSession_isNotAffectedByPriorSessionStaleWork() = runBlocking {
        logInWithMockTokens("old_session_token")
        val oldEpoch = authManager.currentSessionEpoch()

        authManager.disconnect()

        // User connects a new account
        authManager.saveTokens(
            TraktTokenResponse(
                access_token = "new_session_token_xyz",
                refresh_token = "new_refresh_token_xyz",
                expires_in = 7200L
            )
        )

        val newEpoch = authManager.currentSessionEpoch()
        assertTrue(newEpoch > oldEpoch)
        assertTrue(authManager.isSessionInvalid(oldEpoch))
        assertFalse(authManager.isSessionInvalid(newEpoch))

        assertEquals("new_session_token_xyz", authManager.getAccessToken())
        assertTrue(authManager.hasValidToken())
        assertTrue(authManager.isConnected.value)
    }

    @Test
    fun testDisconnect_clearsCredentialsEvenIfCallerCoroutineIsCancelled() = runBlocking {
        logInWithMockTokens("token_caller_cancel")
        fakeApiService.revokeDelayMs = 200L // Network call in progress

        val disconnectJob = launch(Dispatchers.IO) {
            authManager.disconnect()
        }

        delay(30L) // Wait for disconnect to start
        disconnectJob.cancel() // Cancel the caller coroutine
        disconnectJob.join()

        // Local credentials must still be cleared because disconnect executes in NonCancellable context
        assertFalse(authManager.hasValidToken())
        assertNull(authManager.getAccessToken())
        assertFalse(authManager.isConnected.value)
        assertFalse(authManager.isDisconnectingActive())
    }

    @Test
    fun testOutboundMutation_oldSessionCannotPushToNewlyConnectedSession() = runBlocking {
        logInWithMockTokens("account_a_token")
        val db = AppDatabase.getDatabase(context)
        val dao = db.savedItemDao()
        val testItem = SavedItem(
            id = "test_item_race",
            title = "Race Test Movie",
            type = SavedItemType.MEDIA,
            mediaType = "movie",
            traktId = 12345L,
            content = "https://trakt.tv/movies/12345"
        )
        dao.insertItem(
            SavedItemEntity(
                id = "test_item_race",
                type = SavedItemType.MEDIA.name,
                title = "Race Test Movie",
                content = "https://trakt.tv/movies/12345",
                timestamp = System.currentTimeMillis(),
                foldersJson = "[]",
                extractedText = null,
                thumbnailPath = null,
                traktId = 12345L,
                mediaType = "movie"
            )
        )

        // Launch outbound mutation in background coroutine
        val mutationJob = async(Dispatchers.IO) {
            TraktSyncEngine.markMovieWatched(context, testItem, isWatched = true)
        }

        // Immediately disconnect Account A and connect Account B
        authManager.disconnect()
        authManager.saveTokens(
            TraktTokenResponse(
                access_token = "account_b_token",
                refresh_token = "account_b_refresh",
                expires_in = 7200L
            )
        )

        val result = mutationJob.await()
        assertTrue(result.isSuccess)

        // Outbound mutation was for Account A's epoch; it must NOT have sent addToHistory to Trakt using Account B
        assertFalse(fakeApiService.addToHistoryCalled)
    }
}

class FakeTraktCredentialStore : TraktCredentialStore {
    private val map = mutableMapOf<String, String>()
    override fun get(name: String): String? = map[name]
    override fun putAll(values: Map<String, String?>) {
        values.forEach { (k, v) ->
            if (v == null) map.remove(k) else map[k] = v
        }
    }
}

class FakeTraktApiService : TraktApiService {
    var revokeCalled = false
    var lastRevokeRequest: TraktRevokeTokenRequest? = null
    var lastRevokeAuthHeader: String? = null
    var shouldFailRevoke = false
    var revokeDelayMs = 0L

    var refreshTokenCalled = false
    var refreshTokenDelayMs = 0L
    var mockRefreshTokenResponse = TraktTokenResponse(
        access_token = "refreshed_access_token_xyz",
        refresh_token = "refreshed_refresh_token_xyz",
        expires_in = 7200L
    )

    var watchedMoviesToReturn = listOf<TraktWatchedMovieItem>()
    var watchedShowsToReturn = listOf<TraktWatchedShowItem>()
    var getWatchedShowsDelayMs = 0L

    var addToHistoryCalled = false

    override suspend fun revokeToken(
        authorization: String?,
        request: TraktRevokeTokenRequest
    ): retrofit2.Response<Unit> {
        revokeCalled = true
        lastRevokeAuthHeader = authorization
        lastRevokeRequest = request
        if (revokeDelayMs > 0) {
            delay(revokeDelayMs)
        }
        if (shouldFailRevoke) {
            return retrofit2.Response.error(500, "Server error".toResponseBody(null))
        }
        return retrofit2.Response.success(Unit)
    }

    override suspend fun refreshToken(request: TraktRefreshTokenRequest): TraktTokenResponse {
        refreshTokenCalled = true
        if (refreshTokenDelayMs > 0) {
            delay(refreshTokenDelayMs)
        }
        return mockRefreshTokenResponse
    }

    override suspend fun getDeviceCode(request: TraktDeviceCodeRequest): TraktDeviceCodeResponse {
        return TraktDeviceCodeResponse(
            device_code = "mock_device_code",
            user_code = "ABC-123",
            verification_url = "https://trakt.tv/activate",
            expires_in = 600,
            interval = 1
        )
    }

    override suspend fun getDeviceToken(request: TraktDeviceTokenRequest): retrofit2.Response<TraktTokenResponse> {
        return retrofit2.Response.success(
            TraktTokenResponse(
                access_token = "mock_access_token_123",
                refresh_token = "mock_refresh_token_123",
                expires_in = 7200L
            )
        )
    }

    override suspend fun getCurrentUser(): TraktUser {
        return TraktUser(username = "testuser", name = "Test User")
    }

    override suspend fun getLastActivities(): TraktLastActivitiesResponse = TraktLastActivitiesResponse()
    override suspend fun getWatchedMovies(page: Int?, limit: Int?): List<TraktWatchedMovieItem> = watchedMoviesToReturn
    override suspend fun getWatchedShows(page: Int?, limit: Int?): List<TraktWatchedShowItem> {
        if (getWatchedShowsDelayMs > 0) {
            delay(getWatchedShowsDelayMs)
        }
        return watchedShowsToReturn
    }
    override suspend fun getWatchlistMovies(page: Int?, limit: Int?): List<TraktWatchlistItem> = emptyList()
    override suspend fun getWatchlistShows(page: Int?, limit: Int?): List<TraktWatchlistItem> = emptyList()
    override suspend fun getMovieRatings(page: Int?, limit: Int?): List<TraktRatingItem> = emptyList()
    override suspend fun getShowRatings(page: Int?, limit: Int?): List<TraktRatingItem> = emptyList()
    override suspend fun addToHistory(payload: TraktSyncPayload): TraktSyncResponse {
        addToHistoryCalled = true
        return TraktSyncResponse()
    }
    override suspend fun removeFromHistory(payload: TraktSyncPayload): TraktSyncResponse = TraktSyncResponse()
    override suspend fun addRatings(payload: TraktSyncPayload): TraktSyncResponse = TraktSyncResponse()
    override suspend fun removeRatings(payload: TraktSyncPayload): TraktSyncResponse = TraktSyncResponse()
    override suspend fun addToWatchlist(payload: TraktSyncPayload): TraktSyncResponse = TraktSyncResponse()
    override suspend fun removeFromWatchlist(payload: TraktSyncPayload): TraktSyncResponse = TraktSyncResponse()
    override suspend fun getShowWatchedProgress(id: String): TraktShowWatchedProgressResponse = TraktShowWatchedProgressResponse()
    override suspend fun getShowRatings(id: String): TraktRatingsResponse = TraktRatingsResponse()
    override suspend fun getShowStats(id: String): TraktStatsResponse = TraktStatsResponse()
    override suspend fun getShowSeasons(id: String): List<TraktSeasonFullItem> = emptyList()
    override suspend fun getShowSeasonEpisodes(id: String, season: Int): List<TraktSeasonEpisodeItem> = emptyList()
    override suspend fun getShowPeople(id: String): TraktCastResponse = TraktCastResponse()
}

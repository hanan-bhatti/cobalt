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
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class TraktPkceAuthTest {

    private lateinit var context: Context
    private lateinit var fakeApiService: FakeTraktApiService
    private lateinit var credentialStore: FakeTraktCredentialStore
    private lateinit var authManager: TraktAuthManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeApiService = FakeTraktApiService()
        credentialStore = FakeTraktCredentialStore()
        authManager = TraktAuthManager(
            context = context,
            apiServiceProvider = { fakeApiService },
            credentialStore = credentialStore
        )
        TraktAuthManager.setInstanceForTesting(authManager)
        TraktSyncEngine.apiServiceProvider = { fakeApiService }
    }

    @After
    fun tearDown() {
        runBlocking {
            authManager.disconnect()
        }
        TraktAuthManager.setInstanceForTesting(null)
        TraktSyncEngine.apiServiceProvider = null
    }

    @Test
    fun testCodeVerifierAndChallengeGeneration() {
        val verifier = authManager.generateCodeVerifier()
        // RFC 7636 Section 4.1: length between 43 and 128 characters
        assertTrue("Verifier length should be >= 43", verifier.length >= 43)
        assertTrue("Verifier length should be <= 128", verifier.length <= 128)
        // Must contain only unreserved characters [A-Za-z0-9-_.~]
        assertTrue("Verifier contains invalid characters", verifier.matches(Regex("^[A-Za-z0-9_-]+$")))

        val challenge = authManager.generateCodeChallenge(verifier)
        assertNotNull(challenge)
        assertTrue("Challenge length should be 43 characters (SHA-256 base64url unpadded)", challenge.length == 43)
        assertTrue("Challenge contains invalid characters", challenge.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun testCreatePkceAuthorizationUriHasRequiredParameters() {
        val (authUri, state) = authManager.createPkceAuthorizationUri()

        assertEquals("https", authUri.scheme)
        assertEquals("auth.trakt.tv", authUri.host)
        assertEquals("/oauth/authorize", authUri.path)
        assertEquals("code", authUri.getQueryParameter("response_type"))
        assertEquals(TraktAuthManager.DEFAULT_CLIENT_ID, authUri.getQueryParameter("client_id"))
        assertEquals("https://cobalt.hanan-bhatti.site/callback", authUri.getQueryParameter("redirect_uri"))
        assertEquals(state, authUri.getQueryParameter("state"))
        assertEquals("S256", authUri.getQueryParameter("code_challenge_method"))
        assertNotNull(authUri.getQueryParameter("code_challenge"))
    }

    @Test
    fun testSuccessfulPkceAuthorizationCallback() = runBlocking {
        val (_, state) = authManager.createPkceAuthorizationUri()

        val callbackUri = Uri.parse("https://cobalt.hanan-bhatti.site/callback?code=test_auth_code_123&state=$state")
        val handled = authManager.handleAuthCallback(callbackUri)

        assertTrue("Callback should be handled successfully", handled)

        // Wait for coroutine token exchange to complete
        var retries = 0
        while (!fakeApiService.exchangeTokenCalled && retries < 20) {
            delay(50)
            retries++
        }

        assertTrue("exchangeToken should have been called", fakeApiService.exchangeTokenCalled)
        assertEquals("test_auth_code_123", fakeApiService.lastExchangeTokenRequest?.code)
        assertEquals(TraktAuthManager.DEFAULT_CLIENT_ID, fakeApiService.lastExchangeTokenRequest?.client_id)
        assertEquals("authorization_code", fakeApiService.lastExchangeTokenRequest?.grant_type)
        assertEquals("https://cobalt.hanan-bhatti.site/callback", fakeApiService.lastExchangeTokenRequest?.redirect_uri)
        assertNotNull(fakeApiService.lastExchangeTokenRequest?.code_verifier)

        // Verify tokens were stored in credential store
        assertEquals("pkce_access_token_123", credentialStore.get("trakt_access_token"))
        assertEquals("pkce_refresh_token_123", credentialStore.get("trakt_refresh_token"))
        assertTrue("User should be marked connected", authManager.isConnected.value)
    }

    @Test
    fun testStateMismatchRejectsCallback() = runBlocking {
        authManager.createPkceAuthorizationUri()

        val callbackUri = Uri.parse("https://cobalt.hanan-bhatti.site/callback?code=test_code&state=mismatched_state_xyz")
        val handled = authManager.handleAuthCallback(callbackUri)

        assertFalse("Mismatched state should be rejected", handled)
        assertFalse("exchangeToken should not be called", fakeApiService.exchangeTokenCalled)
        val authState = authManager.deviceAuthState.value
        assertTrue("State should be Error", authState is TraktDeviceAuthState.Error)
        assertTrue((authState as TraktDeviceAuthState.Error).message.contains("CSRF"))
    }

    @Test
    fun testReplayAttackRejected() = runBlocking {
        val (_, state) = authManager.createPkceAuthorizationUri()

        val callbackUri = Uri.parse("https://cobalt.hanan-bhatti.site/callback?code=test_code&state=$state")
        val firstAttempt = authManager.handleAuthCallback(callbackUri)
        assertTrue("First attempt should succeed", firstAttempt)

        // Second attempt with the exact same state/code should fail because state was invalidated
        val secondAttempt = authManager.handleAuthCallback(callbackUri)
        assertFalse("Replay attempt must be rejected", secondAttempt)
        val authState = authManager.deviceAuthState.value
        assertTrue("State should be Error on replay", authState is TraktDeviceAuthState.Error)
    }

    @Test
    fun testUserAccessDeniedCallback() = runBlocking {
        val (_, state) = authManager.createPkceAuthorizationUri()

        val callbackUri = Uri.parse("https://cobalt.hanan-bhatti.site/callback?error=access_denied&error_description=The+user+denied+the+request&state=$state")
        val handled = authManager.handleAuthCallback(callbackUri)

        assertFalse("Denied authorization should return false", handled)
        assertFalse("exchangeToken must not be called", fakeApiService.exchangeTokenCalled)
        val authState = authManager.deviceAuthState.value
        assertTrue("State should be Error", authState is TraktDeviceAuthState.Error)
        assertTrue((authState as TraktDeviceAuthState.Error).message.contains("cancelled or denied"))
    }

    @Test
    fun testExpiredPkceSessionRejection() = runBlocking {
        val (_, state) = authManager.createPkceAuthorizationUri()

        // Artificially expire timestamp by modifying preferences directly
        val prefs = context.getSharedPreferences("cobalt_trakt_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("trakt_pkce_timestamp", System.currentTimeMillis() - (15 * 60 * 1000L)).apply()

        val callbackUri = Uri.parse("https://cobalt.hanan-bhatti.site/callback?code=test_code&state=$state")
        val handled = authManager.handleAuthCallback(callbackUri)

        assertFalse("Expired PKCE session should be rejected", handled)
        assertFalse("exchangeToken must not be called", fakeApiService.exchangeTokenCalled)
        val authState = authManager.deviceAuthState.value
        assertTrue("State should be Error", authState is TraktDeviceAuthState.Error)
        assertTrue((authState as TraktDeviceAuthState.Error).message.contains("expired"))
    }

    @Test
    fun testCancelAuthClearsPendingPkce() = runBlocking {
        authManager.createPkceAuthorizationUri()

        val prefs = context.getSharedPreferences("cobalt_trakt_prefs", Context.MODE_PRIVATE)
        assertNotNull(prefs.getString("trakt_pkce_state", null))

        authManager.cancelAuth()

        assertNull("Pending state should be wiped after cancellation", prefs.getString("trakt_pkce_state", null))
        assertNull("Pending verifier should be wiped after cancellation", prefs.getString("trakt_pkce_code_verifier", null))
        assertEquals(TraktDeviceAuthState.Idle, authManager.deviceAuthState.value)
    }
}

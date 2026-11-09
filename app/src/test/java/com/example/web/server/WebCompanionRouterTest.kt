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

package com.example.web.server

import com.example.web.auth.WebCompanionSecurityManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

class WebCompanionRouterTest {

    private lateinit var securityManager: WebCompanionSecurityManager
    private lateinit var router: WebCompanionRouter

    @Before
    fun setUp() {
        securityManager = WebCompanionSecurityManager()
        router = WebCompanionRouter(
            securityManager = securityManager,
            repositoryProvider = { null },
            databaseProvider = { null },
            contextProvider = { null }
        )
    }

    @Test
    fun loginWithCorrectPinReturns200AndSetsCookie() = runBlocking {
        val pin = securityManager.currentPin
        val req = HttpRequest(
            method = "POST",
            rawUri = "/api/auth/login",
            path = "/api/auth/login",
            queryParams = emptyMap(),
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"pin":"$pin"}""".toByteArray(StandardCharsets.UTF_8),
            clientIp = "192.168.1.50"
        )

        val res = router.handleRequest(req)
        assertEquals(200, res.statusCode)

        val json = JSONObject(res.body.toString(StandardCharsets.UTF_8))
        assertTrue(json.getBoolean("authenticated"))
        assertTrue(json.has("token"))

        val cookie = res.headers["Set-Cookie"]
        assertTrue(cookie != null && cookie.contains("cobalt_session="))
        assertTrue(cookie!!.contains("HttpOnly"))
    }

    @Test
    fun loginWithIncorrectPinReturns401() = runBlocking {
        val wrongPin = "9999"
        val realPin = securityManager.currentPin
        val pinToUse = if (realPin == wrongPin) "0000" else wrongPin

        val req = HttpRequest(
            method = "POST",
            rawUri = "/api/auth/login",
            path = "/api/auth/login",
            queryParams = emptyMap(),
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"pin":"$pinToUse"}""".toByteArray(StandardCharsets.UTF_8),
            clientIp = "192.168.1.50"
        )

        val res = router.handleRequest(req)
        assertEquals(401, res.statusCode)

        val json = JSONObject(res.body.toString(StandardCharsets.UTF_8))
        assertEquals("invalid_pin", json.getString("error"))
    }

    @Test
    fun unauthenticatedRequestToProtectedApiReturns401() = runBlocking {
        val req = HttpRequest(
            method = "GET",
            rawUri = "/api/notes",
            path = "/api/notes",
            queryParams = emptyMap(),
            headers = emptyMap(),
            body = ByteArray(0),
            clientIp = "192.168.1.50"
        )

        val res = router.handleRequest(req)
        assertEquals(401, res.statusCode)

        val json = JSONObject(res.body.toString(StandardCharsets.UTF_8))
        assertEquals("unauthorized", json.getString("error"))
    }

    @Test
    fun authenticatedRequestWithBearerTokenSucceeds() = runBlocking {
        val pin = securityManager.currentPin
        val loginReq = HttpRequest(
            method = "POST",
            rawUri = "/api/auth/login",
            path = "/api/auth/login",
            queryParams = emptyMap(),
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"pin":"$pin"}""".toByteArray(StandardCharsets.UTF_8),
            clientIp = "192.168.1.50"
        )
        val loginRes = router.handleRequest(loginReq)
        val token = JSONObject(loginRes.body.toString(StandardCharsets.UTF_8)).getString("token")

        val statusReq = HttpRequest(
            method = "GET",
            rawUri = "/api/status",
            path = "/api/status",
            queryParams = emptyMap(),
            headers = mapOf("Authorization" to "Bearer $token"),
            body = ByteArray(0),
            clientIp = "192.168.1.50"
        )

        val statusRes = router.handleRequest(statusReq)
        assertEquals(200, statusRes.statusCode)

        val json = JSONObject(statusRes.body.toString(StandardCharsets.UTF_8))
        assertEquals("Cobalt", json.getString("app"))
    }

    @Test
    fun authenticatedRequestWithCookieSucceeds() = runBlocking {
        val pin = securityManager.currentPin
        val loginReq = HttpRequest(
            method = "POST",
            rawUri = "/api/auth/login",
            path = "/api/auth/login",
            queryParams = emptyMap(),
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"pin":"$pin"}""".toByteArray(StandardCharsets.UTF_8),
            clientIp = "192.168.1.50"
        )
        val loginRes = router.handleRequest(loginReq)
        val token = JSONObject(loginRes.body.toString(StandardCharsets.UTF_8)).getString("token")

        val authStatusReq = HttpRequest(
            method = "GET",
            rawUri = "/api/auth/status",
            path = "/api/auth/status",
            queryParams = emptyMap(),
            headers = mapOf("Cookie" to "cobalt_session=$token"),
            body = ByteArray(0),
            clientIp = "192.168.1.50"
        )

        val authStatusRes = router.handleRequest(authStatusReq)
        assertEquals(200, authStatusRes.statusCode)

        val json = JSONObject(authStatusRes.body.toString(StandardCharsets.UTF_8))
        assertTrue(json.getBoolean("authenticated"))
    }

    @Test
    fun logoutRevokesSessionToken() = runBlocking {
        val pin = securityManager.currentPin
        val loginReq = HttpRequest(
            method = "POST",
            rawUri = "/api/auth/login",
            path = "/api/auth/login",
            queryParams = emptyMap(),
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"pin":"$pin"}""".toByteArray(StandardCharsets.UTF_8),
            clientIp = "192.168.1.50"
        )
        val loginRes = router.handleRequest(loginReq)
        val token = JSONObject(loginRes.body.toString(StandardCharsets.UTF_8)).getString("token")

        // Logout
        val logoutReq = HttpRequest(
            method = "POST",
            rawUri = "/api/auth/logout",
            path = "/api/auth/logout",
            queryParams = emptyMap(),
            headers = mapOf("Authorization" to "Bearer $token"),
            body = ByteArray(0),
            clientIp = "192.168.1.50"
        )
        val logoutRes = router.handleRequest(logoutReq)
        assertEquals(200, logoutRes.statusCode)

        // Subsequent check must be unauthorized
        val checkReq = HttpRequest(
            method = "GET",
            rawUri = "/api/status",
            path = "/api/status",
            queryParams = emptyMap(),
            headers = mapOf("Authorization" to "Bearer $token"),
            body = ByteArray(0),
            clientIp = "192.168.1.50"
        )
        val checkRes = router.handleRequest(checkReq)
        assertEquals(401, checkRes.statusCode)
    }

    @Test
    fun staticAssetFallbackServesBootstrapHtml() = runBlocking {
        val req = HttpRequest(
            method = "GET",
            rawUri = "/",
            path = "/",
            queryParams = emptyMap(),
            headers = emptyMap(),
            body = ByteArray(0),
            clientIp = "192.168.1.50"
        )

        val res = router.handleRequest(req)
        assertEquals(200, res.statusCode)
        assertEquals("text/html; charset=utf-8", res.headers["Content-Type"])

        val html = res.body.toString(StandardCharsets.UTF_8)
        assertTrue(html.contains("Cobalt Web Companion"))
    }
}

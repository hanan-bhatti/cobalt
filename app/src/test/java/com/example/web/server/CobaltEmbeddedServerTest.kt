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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class CobaltEmbeddedServerTest {

    private lateinit var testScope: CoroutineScope
    private lateinit var securityManager: WebCompanionSecurityManager
    private var server: CobaltEmbeddedServer? = null

    @Before
    fun setUp() {
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        securityManager = WebCompanionSecurityManager()
    }

    @After
    fun tearDown() {
        server?.stop()
        testScope.cancel()
    }

    @Test
    fun sanitizePathProtectsAgainstDirectoryTraversal() {
        assertEquals("/notes", HttpRequest.sanitizePath("/notes"))
        assertEquals("/api/notes", HttpRequest.sanitizePath("/api/notes?limit=10"))
        assertEquals("/etc/passwd", HttpRequest.sanitizePath("/../../etc/passwd"))
        assertEquals("/notes", HttpRequest.sanitizePath("/api/../notes"))
        assertEquals("/secret", HttpRequest.sanitizePath("/..\\..\\secret"))
    }

    @Test
    fun parseQueryParamsExtractsParametersAccurately() {
        val uri = "/api/notes?page=2&limit=50&q=test%20query&tag="
        val params = HttpRequest.parseQueryParams(uri)

        assertEquals("2", params["page"])
        assertEquals("50", params["limit"])
        assertEquals("test query", params["q"])
        assertEquals("", params["tag"])
    }

    @Test
    fun serverStartsAndStopsCleanly() {
        server = CobaltEmbeddedServer(
            requestedPort = 0, // OS assigns an ephemeral port
            securityManager = securityManager
        ) { req ->
            HttpResponse.text(200, "OK")
        }

        val port = server!!.start(testScope)
        assertTrue(port > 0)
        assertTrue(server!!.isRunning)
        assertEquals(port, server!!.actualPort)

        server!!.stop()
        assertFalse(server!!.isRunning)
        assertEquals(0, server!!.actualPort)
    }

    @Test
    fun serverServesGetRequestSuccessfully() {
        server = CobaltEmbeddedServer(
            requestedPort = 0,
            securityManager = securityManager
        ) { req ->
            if (req.path == "/api/ping") {
                HttpResponse.json(200, """{"pong":true}""")
            } else {
                HttpResponse.notFound()
            }
        }
        val port = server!!.start(testScope)

        val url = URL("http://127.0.0.1:$port/api/ping")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 3000
        }

        try {
            val code = conn.responseCode
            assertEquals(200, code)
            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            assertTrue(body.contains(""""pong":true"""))
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun serverEnforcesHostHeaderValidationAntiDnsRebinding() {
        server = CobaltEmbeddedServer(
            requestedPort = 0,
            securityManager = securityManager,
            allowedAddressesProvider = { listOf("192.168.1.100") }
        ) { req ->
            HttpResponse.text(200, "Sensitive internal data")
        }
        val port = server!!.start(testScope)

        // Request with spoofed malicious external host header
        val url = URL("http://127.0.0.1:$port/api/test")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Host", "attacker.evil.com:8080")
            connectTimeout = 3000
            readTimeout = 3000
        }

        try {
            val code = conn.responseCode
            assertEquals("Spoofed Host header must be rejected with 403 Forbidden", 403, code)
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun serverHandlesCorsOptionsPreflight() {
        server = CobaltEmbeddedServer(
            requestedPort = 0,
            securityManager = securityManager
        ) { req ->
            HttpResponse.text(200, "OK")
        }
        val port = server!!.start(testScope)

        val url = URL("http://127.0.0.1:$port/api/notes")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "OPTIONS"
            connectTimeout = 3000
            readTimeout = 3000
        }

        try {
            val code = conn.responseCode
            assertEquals(204, code)
            val allowMethods = conn.getHeaderField("Access-Control-Allow-Methods")
            assertTrue(allowMethods != null && allowMethods.contains("GET"))
        } finally {
            conn.disconnect()
        }
    }
}

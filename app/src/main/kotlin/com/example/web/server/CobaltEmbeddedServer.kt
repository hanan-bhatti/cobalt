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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CancellationException
import java.nio.charset.StandardCharsets

/**
 * Lightweight, zero-dependency, coroutine-based HTTP/1.1 server running locally on Android.
 *
 * Requests have bounded headers/bodies and read timeouts. At most eight client sockets
 * are served concurrently. The browser client is same-origin; cross-origin access is denied.
 */
class CobaltEmbeddedServer(
    val requestedPort: Int = 8080,
    val securityManager: WebCompanionSecurityManager,
    val allowedAddressesProvider: () -> Collection<String> = { emptyList() },
    val handler: suspend (HttpRequest) -> HttpResponse
) {

    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val slots = Semaphore(8)

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    @Volatile
    private var _isRunning: Boolean = false
    val isRunning: Boolean get() = _isRunning

    @Volatile
    private var _actualPort: Int = 0
    val actualPort: Int get() = _actualPort

    /**
     * Starts the embedded HTTP server on the requested port (or next available).
     */
    @Synchronized
    fun start(scope: CoroutineScope): Int {
        if (_isRunning) return _actualPort

        val socket = try {
            ServerSocket(requestedPort, 50, InetAddress.getByName("0.0.0.0")).apply {
                reuseAddress = true
            }
        } catch (_: Exception) {
            // If requested port is busy, fallback to system-assigned available port
            ServerSocket(0, 50, InetAddress.getByName("0.0.0.0")).apply {
                reuseAddress = true
            }
        }

        serverSocket = socket
        _actualPort = socket.localPort
        _isRunning = true

        serverJob = scope.launch(Dispatchers.IO) {
            try { acceptLoop(socket) } finally {
                runCatching { socket.close() }
                clients.forEach { runCatching { it.close() } }
                _isRunning = false
            }
        }

        return _actualPort
    }

    /**
     * Stops the embedded server and frees all socket resources.
     */
    @Synchronized
    fun stop() {
        if (!_isRunning && serverSocket == null) return
        _isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        clients.forEach { runCatching { it.close() } }
        serverJob?.cancel()
        serverJob = null
        _actualPort = 0
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        withContext(Dispatchers.IO) {
            while (isActive && _isRunning) {
                try {
                    val clientSocket = socket.accept()
                    if (!slots.tryAcquire()) { clientSocket.close(); continue }
                    clients.add(clientSocket)
                    launch(Dispatchers.IO) {
                        try { handleClient(clientSocket) } finally {
                            clients.remove(clientSocket)
                            slots.release()
                        }
                    }
                } catch (e: SocketException) {
                    // Socket closed on stop()
                    break
                } catch (_: Exception) {
                    if (!_isRunning) break
                }
            }
        }
    }

    private suspend fun handleClient(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 15_000 // 15-second read timeout
            val clientIp = clientSocket.inetAddress?.hostAddress ?: "127.0.0.1"

            val input = BufferedInputStream(clientSocket.getInputStream())
            val output = BufferedOutputStream(clientSocket.getOutputStream())

            val request = parseRequest(input, clientIp)
            if (request == null) {
                writeResponse(output, HttpResponse.text(400, "Bad Request"))
                return
            }

            // 2. Anti-DNS Rebinding verification
            val hostHeader = request.header("Host")
            val allowed = allowedAddressesProvider()
            if (!securityManager.isValidHost(hostHeader, allowed)) {
                writeResponse(output, HttpResponse.forbidden("Invalid Host header (anti-rebinding protection)"))
                return
            }

            val origin = request.header("Origin")
            if (origin != null && !sameOrigin(origin, hostHeader)) {
                writeResponse(output, HttpResponse.forbidden("Cross-origin access denied"))
                return
            }
            if (request.method == "OPTIONS") {
                writeResponse(output, HttpResponse(204, "No Content", mapOf("Allow" to "GET, POST, PUT, DELETE, OPTIONS")))
                return
            }

            // 3. API Rate limiting check
            if (securityManager.isRateLimited(clientIp)) {
                writeResponse(
                    output,
                    HttpResponse.tooManyRequests(10, "Too many requests. Please slow down.")
                )
                return
            }

            // 4. Dispatch to route handler
            val response = try {
                handler(request)
            } catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) {
                HttpResponse.serverError("Unable to complete request")
            }

            writeResponse(output, response)
        } catch (_: Exception) {
            // Client disconnected or timed out
        } finally {
            try {
                clientSocket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Parses HTTP/1.1 request line, headers, and body.
     */
    private fun parseRequest(input: InputStream, clientIp: String): HttpRequest? {
        val requestLine = readLine(input, maxBytes = 4096) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size != 3 || parts[2] !in setOf("HTTP/1.0", "HTTP/1.1") || !parts[1].startsWith("/") || parts[1].startsWith("//")) return null

        val method = parts[0].uppercase()
        val rawUri = parts[1]
        val path = HttpRequest.sanitizePath(rawUri)
        val queryParams = HttpRequest.parseQueryParams(rawUri)

        // Read headers (capped at 16KB total)
        val headers = mutableMapOf<String, String>()
        var headerBytesRead = 0
        while (true) {
            val line = readLine(input, maxBytes = 4096) ?: return null
            if (line.isEmpty()) break // End of headers
            headerBytesRead += line.length
            if (headerBytesRead > 16_384) return null // Header too large

            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim()
                if (!key.matches(Regex("[A-Za-z0-9-]+")) || headers.keys.any { it.equals(key, true) }) return null
                headers[key] = value
            } else return null
        }

        // Read body if Content-Length present (capped at 10MB)
        if (headers.keys.any { it.equals("Transfer-Encoding", true) }) return null
        val lengthHeader = headers.entries.find { it.key.equals("Content-Length", true) }?.value
        val contentLength = if (lengthHeader == null) 0 else lengthHeader.toIntOrNull() ?: return null
        if (contentLength !in 0..10_485_760) return null

        val body = if (contentLength in 1..10_485_760) {
            val buffer = ByteArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val read = input.read(buffer, totalRead, contentLength - totalRead)
                if (read == -1) break
                totalRead += read
            }
            if (totalRead != contentLength) return null
            buffer
        } else {
            ByteArray(0)
        }

        return HttpRequest(
            method = method,
            rawUri = rawUri,
            path = path,
            queryParams = queryParams,
            headers = headers,
            body = body,
            clientIp = clientIp
        )
    }

    /**
     * Writes HTTP/1.1 response line, security headers, and body to the socket stream.
     */
    private fun writeResponse(output: OutputStream, response: HttpResponse) {
        val statusLine = "HTTP/1.1 ${response.statusCode} ${response.statusMessage}\r\n"
        output.write(statusLine.toByteArray(StandardCharsets.US_ASCII))

        // Security & connection headers
        val headersToWrite = mutableMapOf<String, String>().apply {
            put("Connection", "close")
            put("X-Content-Type-Options", "nosniff")
            put("X-Frame-Options", "DENY")
            put("Cache-Control", "no-store")
            put("Referrer-Policy", "no-referrer")
            put("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
            putAll(response.headers)
            if (!containsKey("Content-Length")) {
                put("Content-Length", response.body.size.toString())
            }
        }

        for ((key, value) in headersToWrite) {
            val headerLine = "$key: $value\r\n"
            output.write(headerLine.toByteArray(StandardCharsets.US_ASCII))
        }
        output.write("\r\n".toByteArray(StandardCharsets.US_ASCII))

        if (response.body.isNotEmpty()) {
            output.write(response.body)
        }
        output.flush()
    }

    /**
     * Reads a line terminated by CRLF or LF with length safety.
     */
    private fun readLine(input: InputStream, maxBytes: Int): String? {
        val out = ByteArrayOutputStream()
        var count = 0
        while (count < maxBytes) {
            val b = input.read()
            if (b == -1) {
                return null
            }
            if (b == '\n'.code) {
                return out.toString(StandardCharsets.UTF_8.name())
            } else if (b != '\r'.code) {
                out.write(b)
            }
            count++
        }
        return null
    }

    private fun sameOrigin(origin: String, host: String?): Boolean = try {
        val uri = URI(origin)
        val expected = URI("http://$host")
        uri.scheme == "http" && uri.host != null && uri.host.equals(expected.host, true) &&
            (if (uri.port == -1) 80 else uri.port) == (if (expected.port == -1) 80 else expected.port) &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty()
    } catch (_: Exception) { false }
}

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

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parsed HTTP/1.1 incoming request.
 */
data class HttpRequest(
    val method: String,
    val rawUri: String,
    val path: String,
    val queryParams: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
    val clientIp: String
) {
    /** Case-insensitive header lookup. */
    fun header(name: String): String? {
        val lower = name.lowercase()
        return headers.entries.find { it.key.lowercase() == lower }?.value
    }

    /** Extracts Bearer token from the Authorization header if present. */
    fun bearerToken(): String? {
        val auth = header("Authorization")?.trim() ?: return null
        if (auth.startsWith("Bearer ", ignoreCase = true)) {
            return auth.substring(7).trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    /** Extracts a cookie value by name from the Cookie header. */
    fun cookie(name: String): String? {
        val cookieHeader = header("Cookie") ?: return null
        return cookieHeader.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.trim()
    }

    /** Body decoded as UTF-8 string. */
    fun textBody(): String = String(body, StandardCharsets.UTF_8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpRequest
        if (method != other.method) return false
        if (path != other.path) return false
        if (queryParams != other.queryParams) return false
        if (clientIp != other.clientIp) return false
        return body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + queryParams.hashCode()
        result = 31 * result + clientIp.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    companion object {
        /**
         * Sanitizes request URI path, strips query params, and protects against path traversal.
         */
        fun sanitizePath(rawUri: String): String {
            val withoutQuery = rawUri.substringBefore('?')
            val decoded = try {
                URLDecoder.decode(withoutQuery, "UTF-8")
            } catch (_: Exception) {
                withoutQuery
            }
            val segments = decoded.replace('\\', '/')
                .split('/')
                .filter { it.isNotEmpty() && it != "." && it != ".." }

            return "/" + segments.joinToString("/")
        }

        /**
         * Parses query parameters from raw URI string.
         */
        fun parseQueryParams(rawUri: String): Map<String, String> {
            val query = rawUri.substringAfter('?', "")
            if (query.isBlank()) return emptyMap()

            val map = mutableMapOf<String, String>()
            for (pair in query.split('&')) {
                if (pair.isBlank()) continue
                val key = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (key.isNotEmpty()) {
                    val decodedKey = try { URLDecoder.decode(key, "UTF-8") } catch (_: Exception) { key }
                    val decodedVal = try { URLDecoder.decode(value, "UTF-8") } catch (_: Exception) { value }
                    map[decodedKey] = decodedVal
                }
            }
            return map
        }
    }
}

/**
 * HTTP/1.1 outgoing response.
 */
data class HttpResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpResponse
        if (statusCode != other.statusCode) return false
        if (statusMessage != other.statusMessage) return false
        if (headers != other.headers) return false
        return body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + statusMessage.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + body.contentHashCode()
        return result
    }

    companion object {
        fun json(
            statusCode: Int = 200,
            json: String,
            extraHeaders: Map<String, String> = emptyMap()
        ): HttpResponse {
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            val headers = mutableMapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "Content-Length" to bytes.size.toString(),
                "Cache-Control" to "no-store, no-cache, must-revalidate"
            ).apply { putAll(extraHeaders) }

            return HttpResponse(
                statusCode = statusCode,
                statusMessage = statusMessageFor(statusCode),
                headers = headers,
                body = bytes
            )
        }

        fun html(
            html: String,
            statusCode: Int = 200,
            extraHeaders: Map<String, String> = emptyMap()
        ): HttpResponse {
            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            val headers = mutableMapOf(
                "Content-Type" to "text/html; charset=utf-8",
                "Content-Length" to bytes.size.toString(),
                "Cache-Control" to "no-cache"
            ).apply { putAll(extraHeaders) }

            return HttpResponse(
                statusCode = statusCode,
                statusMessage = statusMessageFor(statusCode),
                headers = headers,
                body = bytes
            )
        }

        fun bytes(
            contentType: String,
            bytes: ByteArray,
            statusCode: Int = 200,
            extraHeaders: Map<String, String> = emptyMap()
        ): HttpResponse {
            val headers = mutableMapOf(
                "Content-Type" to contentType,
                "Content-Length" to bytes.size.toString(),
                "Accept-Ranges" to "bytes"
            ).apply { putAll(extraHeaders) }

            return HttpResponse(
                statusCode = statusCode,
                statusMessage = statusMessageFor(statusCode),
                headers = headers,
                body = bytes
            )
        }

        fun text(
            statusCode: Int = 200,
            text: String,
            extraHeaders: Map<String, String> = emptyMap()
        ): HttpResponse {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            val headers = mutableMapOf(
                "Content-Type" to "text/plain; charset=utf-8",
                "Content-Length" to bytes.size.toString()
            ).apply { putAll(extraHeaders) }

            return HttpResponse(
                statusCode = statusCode,
                statusMessage = statusMessageFor(statusCode),
                headers = headers,
                body = bytes
            )
        }

        fun unauthorized(message: String = "Authentication required"): HttpResponse {
            return json(
                statusCode = 401,
                json = """{"error":"unauthorized","message":"$message"}"""
            )
        }

        fun forbidden(message: String = "Access denied"): HttpResponse {
            return json(
                statusCode = 403,
                json = """{"error":"forbidden","message":"$message"}"""
            )
        }

        fun notFound(message: String = "Resource not found"): HttpResponse {
            return json(
                statusCode = 404,
                json = """{"error":"not_found","message":"$message"}"""
            )
        }

        fun conflict(message: String): HttpResponse {
            return json(
                statusCode = 409,
                json = """{"error":"conflict","message":"$message"}"""
            )
        }

        fun tooManyRequests(retryAfterSec: Long, message: String): HttpResponse {
            return json(
                statusCode = 429,
                json = """{"error":"too_many_requests","message":"$message","retryAfterSeconds":$retryAfterSec}""",
                extraHeaders = mapOf("Retry-After" to retryAfterSec.toString())
            )
        }

        fun serverError(message: String = "Internal server error"): HttpResponse {
            return json(
                statusCode = 500,
                json = """{"error":"internal_error","message":"$message"}"""
            )
        }

        fun statusMessageFor(code: Int): String = when (code) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            206 -> "Partial Content"
            301 -> "Moved Permanently"
            302 -> "Found"
            304 -> "Not Modified"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            else -> "HTTP $code"
        }
    }
}

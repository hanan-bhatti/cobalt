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

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.repository.CobaltRepository
import com.example.web.auth.AuthResult
import com.example.web.auth.WebCompanionSecurityManager
import com.example.web.session.EditorSessionRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * Request router for the Cobalt Local Web Companion.
 *
 * Handles:
 * 1. 4-digit PIN authentication (`POST /api/auth/login`) with session cookies.
 * 2. Session verification (`GET /api/auth/status`) and logout (`POST /api/auth/logout`).
 * 3. System and server telemetry (`GET /api/status`).
 * 4. Paginated, lightweight note cards (`GET /api/notes?page=1&limit=25&q=...`).
 * 5. On-demand full note content (`GET /api/notes/{id}`) with mobile editor lock detection.
 * 6. Local media attachment streaming (`GET /api/media/{hash}`).
 * 7. Static web client asset serving (`/index.html`, `/app.js`, `/style.css`).
 */
class WebCompanionRouter(
    val securityManager: WebCompanionSecurityManager,
    val repositoryProvider: () -> CobaltRepository?,
    val databaseProvider: () -> AppDatabase?,
    val contextProvider: () -> Context? = { null }
) {

    suspend fun handleRequest(request: HttpRequest): HttpResponse {
        val path = request.path

        // 1. Public Authentication Endpoints
        if (path == "/api/auth/login" && request.method == "POST") {
            return handleLogin(request)
        }
        if (path == "/api/auth/status" && request.method == "GET") {
            return handleAuthStatus(request)
        }
        if (path == "/api/auth/logout" && request.method == "POST") {
            return handleLogout(request)
        }

        // 2. Static Web Assets (Public so login page UI loads in browser)
        if (!path.startsWith("/api/")) {
            return serveStaticAsset(path)
        }

        // 3. All remaining /api/* endpoints require a valid session
        val token = extractSessionToken(request)
        if (token == null || !securityManager.isValidSession(token, request.clientIp)) {
            return HttpResponse.unauthorized("Authentication required. Please enter your 4-digit PIN.")
        }

        // 4. Authenticated API Endpoints
        return when {
            path == "/api/status" && request.method == "GET" -> handleStatus(request)
            path == "/api/notes" && request.method == "GET" -> handleNotesList(request)
            path.startsWith("/api/notes/") && request.method == "GET" -> {
                val id = path.removePrefix("/api/notes/").substringBefore('/')
                handleNoteDetail(id, request)
            }
            path.startsWith("/api/media/") && request.method == "GET" -> {
                val mediaId = path.removePrefix("/api/media/").substringBefore('/')
                handleMediaStream(mediaId, request)
            }
            else -> HttpResponse.notFound("Endpoint not found")
        }
    }

    private fun handleLogin(request: HttpRequest): HttpResponse {
        val json = try { JSONObject(request.textBody()) } catch (_: Exception) { null }
        val pin = json?.optString("pin")?.trim() ?: ""

        if (pin.isEmpty()) {
            return HttpResponse.json(400, """{"error":"bad_request","message":"PIN is required"}""")
        }

        return when (val auth = securityManager.verifyPin(pin, request.clientIp)) {
            is AuthResult.Success -> {
                val cookie = "cobalt_session=${auth.token}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${auth.expiresInSeconds}"
                HttpResponse.json(
                    statusCode = 200,
                    json = JSONObject().apply {
                        put("authenticated", true)
                        put("token", auth.token)
                        put("expiresInSeconds", auth.expiresInSeconds)
                    }.toString(),
                    extraHeaders = mapOf("Set-Cookie" to cookie)
                )
            }
            is AuthResult.InvalidPin -> {
                HttpResponse.json(
                    statusCode = 401,
                    json = JSONObject().apply {
                        put("error", "invalid_pin")
                        put("message", "Incorrect PIN.")
                        put("remainingAttempts", auth.remainingAttempts)
                    }.toString()
                )
            }
            is AuthResult.LockedOut -> {
                HttpResponse.tooManyRequests(
                    retryAfterSec = auth.remainingLockoutSeconds,
                    message = "Too many failed attempts. Device is temporarily locked."
                )
            }
            is AuthResult.TripwireTriggered -> {
                HttpResponse.tooManyRequests(
                    retryAfterSec = 600,
                    message = "Security tripwire triggered. PIN has been rotated."
                )
            }
        }
    }

    private fun handleLogout(request: HttpRequest): HttpResponse {
        val token = extractSessionToken(request)
        if (token != null) {
            securityManager.revokeSession(token)
        }
        val clearCookie = "cobalt_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"
        return HttpResponse.json(
            statusCode = 200,
            json = """{"authenticated":false,"message":"Logged out successfully"}""",
            extraHeaders = mapOf("Set-Cookie" to clearCookie)
        )
    }

    private fun handleAuthStatus(request: HttpRequest): HttpResponse {
        val token = extractSessionToken(request)
        val valid = token != null && securityManager.isValidSession(token, request.clientIp)
        return HttpResponse.json(
            statusCode = 200,
            json = JSONObject().apply {
                put("authenticated", valid)
            }.toString()
        )
    }

    private fun handleStatus(request: HttpRequest): HttpResponse {
        val db = databaseProvider()
        val totalCount = runCatching { db?.savedItemDao()?.getAllItems()?.size ?: 0 }.getOrDefault(0)

        return HttpResponse.json(
            statusCode = 200,
            json = JSONObject().apply {
                put("app", "Cobalt")
                put("version", "2.3.0")
                put("totalNotes", totalCount)
            }.toString()
        )
    }

    private suspend fun handleNotesList(request: HttpRequest): HttpResponse {
        val db = databaseProvider() ?: return HttpResponse.serverError("Database unavailable")
        val page = (request.queryParams["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (request.queryParams["limit"]?.toIntOrNull() ?: 25).coerceIn(1, 100)
        val query = request.queryParams["q"]?.trim()
        val folder = request.queryParams["folder"]?.trim()
        val type = request.queryParams["type"]?.trim()?.uppercase()

        val rawItems = try {
            if (!query.isNullOrEmpty()) {
                db.savedItemDao().searchNoteFiles(query)
            } else {
                db.savedItemDao().getAllItems()
            }
        } catch (_: Exception) {
            emptyList()
        }

        // Filter active notes and criteria
        val filtered = rawItems.filter { item ->
            !item.isDeleted &&
            (folder.isNullOrEmpty() || item.foldersJson.contains(folder)) &&
            (type.isNullOrEmpty() || item.type.uppercase() == type)
        }

        val totalItems = filtered.size
        val totalPages = if (totalItems == 0) 1 else ((totalItems + limit - 1) / limit)
        val startIndex = (page - 1) * limit
        val pageItems = if (startIndex < totalItems) {
            filtered.subList(startIndex, minOf(startIndex + limit, totalItems))
        } else {
            emptyList()
        }

        val itemsArray = JSONArray()
        for (item in pageItems) {
            val isLocked = EditorSessionRegistry.isNoteLockedOnMobile(item.id)
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title.ifBlank { "Untitled Note" })
                put("type", item.type)
                put("preview", item.preview ?: (item.content.take(200)))
                put("timestamp", item.timestamp)
                put("updatedAt", item.updatedAt)
                put("byteLength", item.byteLength)
                put("isLockedByMobile", isLocked)
                val folders = runCatching { JSONArray(item.foldersJson) }.getOrDefault(JSONArray())
                put("folders", folders)
            }
            itemsArray.put(obj)
        }

        val responseJson = JSONObject().apply {
            put("items", itemsArray)
            put("page", page)
            put("limit", limit)
            put("totalItems", totalItems)
            put("totalPages", totalPages)
        }

        return HttpResponse.json(200, responseJson.toString())
    }

    private suspend fun handleNoteDetail(id: String, request: HttpRequest): HttpResponse {
        if (id.isBlank()) return HttpResponse.notFound("Note ID missing")
        val repo = repositoryProvider()
        val db = databaseProvider()

        val fullItem = repo?.getItemForOpen(id)
            ?: db?.savedItemDao()?.getItemById(id)?.let { entity ->
                com.example.data.model.SavedItem(
                    id = entity.id,
                    type = try {
                        com.example.data.model.SavedItemType.valueOf(entity.type)
                    } catch (_: Exception) {
                        com.example.data.model.SavedItemType.TEXT
                    },
                    title = entity.title,
                    content = entity.content,
                    timestamp = entity.timestamp,
                    updatedAt = entity.updatedAt,
                    folders = emptyList()
                )
            }

        if (fullItem == null) {
            return HttpResponse.notFound("Note not found")
        }

        val isLocked = EditorSessionRegistry.isNoteLockedOnMobile(id)
        val obj = JSONObject().apply {
            put("id", fullItem.id)
            put("title", fullItem.title.ifBlank { "Untitled Note" })
            put("type", fullItem.type.name)
            put("content", fullItem.content)
            put("timestamp", fullItem.timestamp)
            put("updatedAt", fullItem.updatedAt)
            put("isLockedByMobile", isLocked)
            if (isLocked) {
                put("lockReason", "This note is currently open in the editor on your phone. Read-only.")
            }
            val foldersArray = JSONArray()
            fullItem.folders.forEach { foldersArray.put(it) }
            put("folders", foldersArray)
        }

        return HttpResponse.json(200, obj.toString())
    }

    private suspend fun handleMediaStream(mediaId: String, request: HttpRequest): HttpResponse {
        if (mediaId.isBlank()) return HttpResponse.notFound("Media ID missing")
        val db = databaseProvider() ?: return HttpResponse.notFound()
        val item = db.savedItemDao().getItemById(mediaId) ?: return HttpResponse.notFound()

        val file = File(item.content)
        if (!file.exists() || !file.isFile) {
            return HttpResponse.notFound("Media file not found")
        }

        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return HttpResponse.serverError("Unable to read media file")
        }

        val mimeType = mimeTypeFor(file.name)
        return HttpResponse.bytes(mimeType, bytes)
    }

    private fun serveStaticAsset(rawPath: String): HttpResponse {
        val path = if (rawPath == "/" || rawPath.isBlank()) "/index.html" else rawPath
        val assetPath = "web" + (if (path.startsWith("/")) path else "/$path")

        val context = contextProvider()
        val inputStream: InputStream? = if (context != null) {
            try {
                context.assets.open(assetPath)
            } catch (_: Exception) {
                try { context.assets.open("web/index.html") } catch (_: Exception) { null }
            }
        } else {
            javaClass.classLoader?.getResourceAsStream(assetPath)
        }

        if (inputStream == null) {
            return if (path == "/index.html" || path == "/") {
                HttpResponse.html(DEFAULT_BOOTSTRAP_HTML)
            } else {
                HttpResponse.notFound("Asset not found")
            }
        }

        val bytes = inputStream.use { it.readBytes() }
        val mimeType = mimeTypeFor(path)
        return HttpResponse.bytes(mimeType, bytes)
    }

    private fun extractSessionToken(request: HttpRequest): String? {
        return request.bearerToken() ?: request.cookie("cobalt_session")
    }

    companion object {
        fun mimeTypeFor(path: String): String {
            val ext = path.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "html", "htm" -> "text/html; charset=utf-8"
                "css" -> "text/css; charset=utf-8"
                "js" -> "application/javascript; charset=utf-8"
                "json" -> "application/json; charset=utf-8"
                "svg" -> "image/svg+xml"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "ico" -> "image/x-icon"
                "woff2" -> "font/woff2"
                "woff" -> "font/woff"
                "ttf" -> "font/ttf"
                else -> "application/octet-stream"
            }
        }

        private const val DEFAULT_BOOTSTRAP_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Cobalt Web Companion</title>
  <style>
    body { font-family: system-ui, sans-serif; background: #121212; color: #fff; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
    .card { background: #1e1e1e; padding: 32px; border-radius: 16px; text-align: center; max-width: 360px; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
    h1 { font-size: 20px; margin-bottom: 8px; }
    p { font-size: 14px; color: #aaa; margin-bottom: 24px; }
    input { width: 100%; box-sizing: border-box; padding: 12px; font-size: 18px; text-align: center; letter-spacing: 6px; border: 1px solid #333; background: #2a2a2a; color: #fff; border-radius: 8px; margin-bottom: 16px; }
    button { width: 100%; padding: 12px; font-size: 16px; font-weight: 600; border: none; background: #2563eb; color: #fff; border-radius: 8px; cursor: pointer; }
    button:hover { background: #1d4ed8; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Cobalt Web Companion</h1>
    <p>Enter the 4-digit PIN displayed on your Android device to connect.</p>
    <input type="password" id="pin" maxlength="4" placeholder="••••" autofocus>
    <button onclick="login()">Connect</button>
  </div>
  <script>
    async function login() {
      const pin = document.getElementById('pin').value;
      if (!pin) return;
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({pin})
      });
      const data = await res.json();
      if (res.ok && data.authenticated) {
        window.location.reload();
      } else {
        alert(data.message || 'Incorrect PIN');
      }
    }
  </script>
</body>
</html>"""
    }
}

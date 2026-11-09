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
import java.io.ByteArrayOutputStream
import com.example.BuildConfig
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.web.session.LockAcquisitionResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

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
    val contextProvider: () -> Context? = { null },
    val noteStoreProvider: () -> WebNoteStore? = { repositoryProvider()?.let(::RepositoryWebNoteStore) }
) {

    suspend fun handleRequest(request: HttpRequest): HttpResponse {
        val path = request.path
        if (request.method !in setOf("GET", "POST", "PUT", "DELETE")) return HttpResponse.text(405, "Method not allowed")
        if (request.method != "GET" && !request.header("Content-Type").orEmpty().startsWith("application/json", true)) {
            return HttpResponse.text(415, "JSON content type required")
        }

        // 1. Public Authentication & Theme Endpoints
        if (path == "/api/auth/login" && request.method == "POST") {
            return handleLogin(request)
        }
        if (path == "/api/auth/status" && request.method == "GET") {
            return handleAuthStatus(request)
        }
        if (path == "/api/auth/logout" && request.method == "POST") {
            return handleLogout(request)
        }
        if (path == "/api/theme" && request.method == "GET") {
            return HttpResponse.json(200, WebThemeExporter.exportThemeJson(contextProvider()))
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
            path == "/api/status" && request.method == "GET" -> handleStatus()
            path == "/api/notes" && request.method == "GET" -> handleNotesList(request)
            path.startsWith("/api/notes/") -> {
                val segments = path.removePrefix("/api/notes/").split('/')
                when {
                    segments.size == 1 && request.method == "GET" -> handleNoteDetail(segments[0], request)
                    segments.size == 1 && request.method == "PUT" -> handleNoteSave(segments[0], request, token)
                    segments.size == 2 && segments[1] == "lock" -> handleNoteLock(segments[0], request, token)
                    else -> HttpResponse.notFound()
                }
            }
            path.startsWith("/api/media/") && request.method == "GET" -> {
                val mediaId = path.removePrefix("/api/media/").substringBefore('/')
                handleMediaStream(mediaId)
            }
            else -> HttpResponse.notFound("Endpoint not found")
        }
    }

    private suspend fun handleLogin(request: HttpRequest): HttpResponse {
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
                delay(auth.penaltyDelayMs)
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
            if (securityManager.isValidSession(token, request.clientIp)) {
                securityManager.revokeSession(token)
                EditorSessionRegistry.releaseSession(token)
            }
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

    private suspend fun handleStatus(): HttpResponse {
        val totalCount = noteStoreProvider()?.list(null)?.size ?: 0

        return HttpResponse.json(
            statusCode = 200,
            json = JSONObject().apply {
                put("app", "Cobalt")
                put("version", BuildConfig.VERSION_NAME)
                put("totalNotes", totalCount)
            }.toString()
        )
    }

    private suspend fun handleNotesList(request: HttpRequest): HttpResponse {
        val store = noteStoreProvider() ?: return HttpResponse.serverError("Repository unavailable")
        val page = (request.queryParams["page"]?.toIntOrNull() ?: 1).coerceIn(1, 1_000_000)
        val limit = (request.queryParams["limit"]?.toIntOrNull() ?: 25).coerceIn(1, 100)
        val folder = request.queryParams["folder"]?.trim()
        val type = request.queryParams["type"]?.trim()?.uppercase()
        val filtered = store.list(request.queryParams["q"]?.trim()).filter { item ->
            !item.isDeleted && (folder.isNullOrEmpty() || folder in item.folders) &&
                (type.isNullOrEmpty() || item.type.name == type ||
                    type == "ATTACHMENT" && item.type in setOf(SavedItemType.IMAGE, SavedItemType.VIDEO, SavedItemType.AUDIO))
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
                put("type", item.type.name)
                put("preview", item.preview.ifBlank { if (item.type in setOf(SavedItemType.TEXT, SavedItemType.CODE, SavedItemType.LINK)) item.content.take(200) else item.type.displayName })
                put("timestamp", item.timestamp)
                put("updatedAt", item.updatedAt)
                put("byteLength", item.byteLength)
                put("isLockedByMobile", isLocked)
                val folders = JSONArray(item.folders)
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
        val store = noteStoreProvider() ?: return HttpResponse.serverError("Repository unavailable")
        val item = try { store.read(id) } catch (_: IllegalArgumentException) {
            return HttpResponse.text(413, "This note exceeds the 5 MB web limit. Open it on your phone.")
        } ?: return HttpResponse.notFound("Note not found")
        if (item.isDeleted) return HttpResponse.notFound()
        return noteResponse(item, request)
    }

    private fun noteResponse(item: SavedItem, request: HttpRequest): HttpResponse {
        val editable = item.type in setOf(SavedItemType.TEXT, SavedItemType.CODE)
        val locked = EditorSessionRegistry.isNoteLockedOnMobile(item.id)
        return HttpResponse.json(200, JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("type", item.type.name)
            put("content", if (editable || item.type == SavedItemType.LINK) item.content else item.extractedText.orEmpty())
            put("timestamp", item.timestamp)
            put("updatedAt", item.updatedAt)
            put("contentHash", item.contentHash ?: JSONObject.NULL)
            put("byteLength", item.byteLength)
            put("editable", editable && !item.bodyMissing)
            put("isLockedByMobile", locked)
            put("isLockedByOtherWeb", EditorSessionRegistry.canWriteNote(item.id, extractSessionToken(request)) is LockAcquisitionResult.ConflictWeb)
            put("folders", JSONArray(item.folders))
            if (item.type in setOf(SavedItemType.IMAGE, SavedItemType.VIDEO, SavedItemType.AUDIO)) {
                put("mediaUrl", "/api/media/${java.net.URLEncoder.encode(item.id, "UTF-8").replace("+", "%20")}")
            }
        }.toString())
    }

    private suspend fun handleNoteLock(id: String, request: HttpRequest, token: String): HttpResponse {
        if (request.method == "DELETE") {
            EditorSessionRegistry.releaseWebLock(id, token)
            return HttpResponse.json(200, "{\"released\":true}")
        }
        if (request.method == "PUT") {
            return if (EditorSessionRegistry.refreshWebHeartbeat(id, token)) HttpResponse.json(200, "{\"renewed\":true}")
            else HttpResponse.conflict("Editing lock expired or your phone is editing this note. Your draft is retained.")
        }
        if (request.method != "POST") return HttpResponse.text(405, "Method not allowed")
        val item = try { noteStoreProvider()?.read(id) } catch (_: IllegalArgumentException) {
            return HttpResponse.text(413, "This note exceeds the 5 MB web limit. Open it on your phone.")
        } ?: return HttpResponse.notFound()
        if (item.isDeleted) return HttpResponse.notFound()
        if (item.type !in setOf(SavedItemType.TEXT, SavedItemType.CODE)) return HttpResponse.text(405, "Only notes and code are editable")
        return when (EditorSessionRegistry.acquireWebLock(id, token, request.clientIp)) {
            LockAcquisitionResult.Granted -> noteResponse(item, request)
            else -> HttpResponse.conflict("This note is being edited on your phone or in another browser.")
        }
    }

    private suspend fun handleNoteSave(id: String, request: HttpRequest, token: String): HttpResponse {
        val json = try { JSONObject(request.textBody()) } catch (_: Exception) { return HttpResponse.text(400, "Invalid JSON") }
        if (json.opt("title") !is String || json.opt("content") !is String || json.opt("updatedAt") !is Number || !json.has("contentHash")) {
            return HttpResponse.text(400, "Title, content and base revision are required")
        }
        val revision = json.get("updatedAt") as Number
        if (revision.toDouble() != revision.toLong().toDouble() || revision.toLong() < 0 ||
            !json.isNull("contentHash") && json.opt("contentHash") !is String) return HttpResponse.text(400, "Invalid base revision")
        val content = json.getString("content")
        val title = json.getString("title").trim()
        if (title.length > 512) return HttpResponse.text(400, "Title is too long")
        if (content.toByteArray(Charsets.UTF_8).size > WebNoteStore.MAX_BODY_BYTES) return HttpResponse.text(413, "Web notes are limited to 5 MB")
        val store = noteStoreProvider() ?: return HttpResponse.serverError("Repository unavailable")
        val item = try { store.read(id) } catch (_: IllegalArgumentException) {
            return HttpResponse.text(413, "This note exceeds the 5 MB web limit. Open it on your phone.")
        } ?: return HttpResponse.notFound()
        if (item.isDeleted) return HttpResponse.notFound()
        if (item.type !in setOf(SavedItemType.TEXT, SavedItemType.CODE)) return HttpResponse.text(405, "Only notes and code are editable")
        val baseHash = if (json.isNull("contentHash")) null else json.getString("contentHash")
        if (item.updatedAt != json.getLong("updatedAt") || item.contentHash != baseHash) {
            return HttpResponse.conflict("This note changed since you opened it. Your draft is retained; reopen before saving.")
        }
        val guard = {
            check(securityManager.isValidSession(token, request.clientIp)) { "Session expired; reconnect before saving." }
            check(EditorSessionRegistry.refreshWebHeartbeat(id, token) &&
                EditorSessionRegistry.canWriteNote(id, token) == LockAcquisitionResult.Granted) {
                "This note is being edited elsewhere or its lock expired. Your draft is retained."
            }
        }
        return try {
            guard()
            val saved = store.save(item.copy(title = title, content = content, bodyLoaded = true), guard)
            noteResponse(saved, request)
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: IllegalStateException) {
            HttpResponse.conflict("The note changed or its editing lock is unavailable. Your draft is retained; reopen before saving.")
        } catch (_: java.io.IOException) {
            HttpResponse.conflict("Could not commit this note. Your draft is retained. Check the note on your phone.")
        }
    }

    private suspend fun handleMediaStream(mediaId: String): HttpResponse {
        if (mediaId.isBlank()) return HttpResponse.notFound("Media ID missing")
        val db = databaseProvider() ?: return HttpResponse.notFound()
        val item = db.savedItemDao().getItemById(mediaId) ?: return HttpResponse.notFound()

        if (item.isDeleted || item.type !in setOf("IMAGE", "VIDEO", "AUDIO")) return HttpResponse.notFound()
        val context = contextProvider() ?: return HttpResponse.notFound()
        val file = File(item.content).canonicalFile
        val roots = listOfNotNull(context.filesDir, context.cacheDir, context.getExternalFilesDir(null))
        if (roots.none { file.path.startsWith(it.canonicalPath + File.separator) }) return HttpResponse.notFound()
        if (!file.exists() || !file.isFile) {
            return HttpResponse.notFound("Media file not found")
        }

        if (file.length() > 10_485_760) return HttpResponse.text(413, "Attachment is too large for the web viewer")
        val bytes = try {
            file.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 10_485_760) return HttpResponse.text(413, "Attachment is too large for the web viewer")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (_: Exception) {
            return HttpResponse.serverError("Unable to read media file")
        }

        val mimeType = mimeTypeFor(file.name)
        return HttpResponse.bytes(mimeType, bytes)
    }

    private fun serveStaticAsset(rawPath: String): HttpResponse {
        val path = if (rawPath == "/" || rawPath.isBlank()) "/index.html" else rawPath
        if (path !in setOf("/index.html", "/app.js", "/style.css")) return HttpResponse.notFound("Asset not found")
        val assetPath = "web" + (if (path.startsWith("/")) path else "/$path")

        val context = contextProvider()
        val inputStream: InputStream? = if (context != null) {
            try {
                context.assets.open(assetPath)
            } catch (_: Exception) {
                null
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
                "mp4" -> "video/mp4"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
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

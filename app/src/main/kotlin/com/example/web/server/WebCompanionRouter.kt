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
import java.net.URI
import java.io.RandomAccessFile
import com.example.R
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val captureMutex = Mutex()

    suspend fun handleRequest(request: HttpRequest): HttpResponse {
        val path = request.path
        if (request.method !in setOf("GET", "POST", "PUT", "DELETE")) return HttpResponse.text(405, "Method not allowed")
        if (request.method != "GET" && path != "/api/attachments" && !request.header("Content-Type").orEmpty().startsWith("application/json", true)) {
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
            path == "/api/notes" && request.method == "POST" -> captureMutex.withLock { handleCapture(request) }
            path == "/api/folders" && request.method == "GET" -> handleFolders()
            path == "/api/folders" && request.method == "POST" -> captureMutex.withLock { handleFolderCreate(request) }
            path == "/api/attachments" && request.method == "POST" -> captureMutex.withLock { handleAttachment(request) }
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
                handleMediaStream(mediaId, request)
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
        val matched = store.list(request.queryParams["q"]?.trim()).filter { item ->
            !item.isDeleted && (folder.isNullOrEmpty() || folder in item.folders) &&
                (type.isNullOrEmpty() || item.type.name == type ||
                    type == "ATTACHMENT" && item.type in setOf(SavedItemType.IMAGE, SavedItemType.VIDEO, SavedItemType.AUDIO))
        }
        val filtered = when (request.queryParams["sort"]) {
            "title" -> matched.sortedWith(compareBy<SavedItem> { it.title.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
            "newest" -> matched.sortedWith(compareByDescending<SavedItem> { it.timestamp }.thenBy { it.id })
            else -> matched.sortedWith(compareByDescending<SavedItem> { it.updatedAt }.thenBy { it.id })
        }

        val totalItems = filtered.size
        val totalPages = if (totalItems == 0) 1 else ((totalItems + limit - 1) / limit)
        val effectivePage = page.coerceAtMost(totalPages)
        val startIndex = (effectivePage - 1) * limit
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
                put("thumbnailUrl", thumbnailUrl(item) ?: JSONObject.NULL)
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
            put("page", effectivePage)
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
            put("linkTitle", item.linkTitle ?: JSONObject.NULL)
            put("linkDescription", item.linkDescription ?: JSONObject.NULL)
            put("thumbnailUrl", thumbnailUrl(item) ?: JSONObject.NULL)
            put("mediaType", item.mediaType ?: JSONObject.NULL)
            put("watchStatus", item.watchStatus ?: JSONObject.NULL)
            put("releaseYear", item.releaseYear ?: JSONObject.NULL)
            put("rating", item.rating ?: JSONObject.NULL)
            put("runtime", item.runtime ?: JSONObject.NULL)
            put("tagline", item.tagline ?: JSONObject.NULL)
            put("genres", JSONArray(item.genres))
            put("watchProviders", JSONArray(item.watchProviders))
            put("trailerUrl", safeWebUrl(item.trailerUrl) ?: JSONObject.NULL)
            put("sourceUrl", safeWebUrl(item.mediaFacts?.sourceUrl) ?: JSONObject.NULL)
            put("source", item.mediaFacts?.source ?: JSONObject.NULL)
            put("episodeCount", item.mediaFacts?.episodeCount ?: JSONObject.NULL)
            put("chapterCount", item.mediaFacts?.chapterCount ?: JSONObject.NULL)
            put("sizeBytes", item.sizeBytes)
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
            val folders = if (json.has("folders")) parseFolders(json.optJSONArray("folders")) else item.folders
            val saved = store.save(item.copy(title = title, content = content, folders = folders, bodyLoaded = true), guard)
            noteResponse(saved, request)
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: IllegalStateException) {
            HttpResponse.conflict("The note changed or its editing lock is unavailable. Your draft is retained; reopen before saving.")
        } catch (_: IllegalArgumentException) {
            HttpResponse.text(400, "Invalid folder selection")
        } catch (_: java.io.IOException) {
            HttpResponse.conflict("Could not commit this note. Your draft is retained. Check the note on your phone.")
        }
    }

    private fun safeWebUrl(value: String?): String? = try {
        val uri = URI(value.orEmpty())
        value?.takeIf { uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null }
    } catch (_: Exception) { null }

    private fun thumbnailUrl(item: SavedItem): String? {
        val path = item.thumbnailPath ?: item.linkImage ?: item.backdropUrl ?: return null
        return if (path.startsWith("/")) "/api/media/${item.id}?thumbnail=1" else safeWebUrl(path)
    }

    private fun parseFolders(array: JSONArray?): List<String> {
        require(array != null && array.length() <= 30)
        return (0 until array.length()).map {
            require(array.opt(it) is String)
            array.getString(it).trim().also { name -> require(name.isNotBlank() && name.length <= 100 && name.none { it.isISOControl() }) }
        }.distinct()
    }

    private suspend fun handleFolders(): HttpResponse {
        val store = noteStoreProvider() ?: return HttpResponse.serverError("Repository unavailable")
        val items = store.list(null).filterNot { it.isDeleted }
        val known = store.folders().associateBy { it.name }
        val names = (known.keys + items.flatMap { it.folders }).distinct()
        return HttpResponse.json(200, JSONObject().apply {
            put("folders", JSONArray(names.map { name -> JSONObject().apply {
                put("name", name); put("color", known[name]?.colorHex ?: JSONObject.NULL)
                put("icon", known[name]?.iconName ?: JSONObject.NULL)
                put("isPinned", known[name]?.isPinned ?: false)
                put("count", items.count { name in it.folders })
            } }))
            put("counts", JSONObject().apply {
                put("ALL", items.size)
                SavedItemType.entries.forEach { type -> put(type.name, items.count { it.type == type }) }
                put("ATTACHMENT", items.count { it.type in setOf(SavedItemType.IMAGE, SavedItemType.VIDEO, SavedItemType.AUDIO) })
            })
        }.toString())
    }

    private suspend fun handleFolderCreate(request: HttpRequest): HttpResponse {
        val store = noteStoreProvider() ?: return HttpResponse.serverError("Repository unavailable")
        val json = try { JSONObject(request.textBody()) } catch (_: Exception) { return HttpResponse.text(400, "Invalid folder") }
        val name = json.optString("name").trim()
        if (name.isBlank() || name.length > 40 || name.any { it.isISOControl() }) return HttpResponse.text(400, "Use a folder name between 1 and 40 characters")
        if (store.folders().any { it.name.trim().equals(name, ignoreCase = true) }) return HttpResponse.conflict("A folder with this name already exists")
        val color = json.optString("color").takeIf { it.isNotBlank() }
        val icon = json.optString("icon").takeIf { it.isNotBlank() }
        if (color != null && !Regex("#[0-9a-fA-F]{6}").matches(color) ||
            icon != null && icon !in setOf("folder", "star", "bookmark", "idea", "checklist", "vault", "tools", "heart", "work", "school", "code", "book", "home", "shopping", "globe", "music", "movie", "image", "archive", "lock"))
            return HttpResponse.text(400, "Choose a valid folder color and icon")
        if (json.has("isPinned") && json.get("isPinned") !is Boolean) return HttpResponse.text(400, "Choose a valid pin setting")
        store.createFolder(name, color, icon, json.optBoolean("isPinned", false))
        return HttpResponse.json(201, JSONObject().put("name", name).toString())
    }

    private suspend fun handleCapture(request: HttpRequest): HttpResponse {
        val store = noteStoreProvider() ?: return HttpResponse.serverError("Repository unavailable")
        val item = try {
            val json = JSONObject(request.textBody())
            val id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
            require(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(id))
            val type = SavedItemType.valueOf(json.getString("type"))
            require(type in setOf(SavedItemType.TEXT, SavedItemType.CODE, SavedItemType.LINK, SavedItemType.MEDIA))
            val title = json.getString("title").trim()
            val content = json.getString("content")
            require(title.length <= 512)
            if (content.toByteArray(Charsets.UTF_8).size > WebNoteStore.MAX_BODY_BYTES) return HttpResponse.text(413, "Web notes are limited to 5 MB")
            require(type != SavedItemType.LINK || safeWebUrl(content) != null)
            require(type != SavedItemType.MEDIA || title.isNotBlank())
            val folders = parseFolders(json.optJSONArray("folders") ?: JSONArray())
            val mediaType = json.optString("mediaType", "movie")
            require(type != SavedItemType.MEDIA || mediaType in setOf("movie", "tv", "anime", "manga"))
            SavedItem(id = id, type = type, title = title.ifBlank { if (type == SavedItemType.LINK) URI(content).host else "Untitled note" },
                content = content, folders = folders,
                linkDescription = if (type == SavedItemType.LINK) json.optString("description").take(10000) else null,
                extractedText = if (type == SavedItemType.MEDIA) content else null,
                mediaType = if (type == SavedItemType.MEDIA) mediaType else null,
                watchStatus = if (type == SavedItemType.MEDIA) "Plan to Watch" else null,
                releaseYear = if (type == SavedItemType.MEDIA) json.optString("releaseYear").take(4) else null)
        } catch (_: Exception) { return HttpResponse.text(400, "Check the capture type, title, folders and URL. Links must use http or https.") }
        val existing = store.read(item.id)
        if (existing != null) {
            return if (!existing.isDeleted && existing.type == item.type && existing.title == item.title &&
                existing.content == item.content && existing.folders == item.folders)
                noteResponse(existing, request) else HttpResponse.conflict("This capture was already saved. Open it from your archive before making changes.")
        }
        return try { noteResponse(store.create(item), request).copy(statusCode = 201, statusMessage = "Created") }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { HttpResponse.serverError("Could not save this capture. Your draft is retained; try again.") }
    }

    private suspend fun handleAttachment(request: HttpRequest): HttpResponse {
        val store = noteStoreProvider() ?: return HttpResponse.serverError("Repository unavailable")
        val context = contextProvider() ?: return HttpResponse.serverError("Storage unavailable")
        val mime = request.header("Content-Type").orEmpty().substringBefore(';').lowercase()
        val extension = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/gif" to "gif",
            "video/mp4" to "mp4", "video/webm" to "webm", "audio/mpeg" to "mp3", "audio/mp4" to "m4a", "audio/wav" to "wav", "audio/ogg" to "ogg", "audio/webm" to "webm")[mime]
            ?: return HttpResponse.text(415, "Choose a supported image, audio or video file")
        if (request.body.isEmpty()) return HttpResponse.text(400, "The attachment is empty")
        if (request.body.size > 10_485_760) return HttpResponse.text(413, "Attachments are limited to 10 MB per upload")
        val title = request.queryParams["title"].orEmpty().trim()
        if (title.length > 512) return HttpResponse.text(400, "Title is too long")
        val folders = try { parseFolders(JSONArray(request.queryParams["folders"] ?: "[]")) } catch (_: Exception) { return HttpResponse.text(400, "Invalid folder selection") }
        val type = when { mime.startsWith("image/") -> SavedItemType.IMAGE; mime.startsWith("video/") -> SavedItemType.VIDEO; else -> SavedItemType.AUDIO }
        val id = request.queryParams["id"] ?: java.util.UUID.randomUUID().toString()
        if (!Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(id)) return HttpResponse.text(400, "Invalid capture identifier")
        val existing = store.read(id)
        if (existing != null) return if (!existing.isDeleted && existing.type == type)
            noteResponse(existing, request) else HttpResponse.conflict("This capture was already saved.")
        val directory = File(context.filesDir, "media/web").apply { mkdirs() }
        val file = File(directory, "$id.$extension")
        return try {
            java.io.FileOutputStream(file).use { output -> output.write(request.body); output.fd.sync() }
            val saved = store.create(SavedItem(id = id, type = type, title = title.ifBlank { "New attachment" },
                content = if (type == SavedItemType.AUDIO) "" else file.path,
                thumbnailPath = if (type == SavedItemType.AUDIO) file.path else null, folders = folders, sizeBytes = file.length()))
            noteResponse(saved, request).copy(statusCode = 201, statusMessage = "Created")
        } catch (cancel: CancellationException) { file.delete(); throw cancel
        } catch (_: Exception) { file.delete(); HttpResponse.serverError("Could not save this attachment. Try again.") }
    }

    private suspend fun handleMediaStream(mediaId: String, request: HttpRequest): HttpResponse {
        val item = noteStoreProvider()?.read(mediaId) ?: return HttpResponse.notFound()
        if (item.isDeleted) return HttpResponse.notFound()
        val thumbnail = request.queryParams["thumbnail"] == "1"
        if (!thumbnail && item.type !in setOf(SavedItemType.IMAGE, SavedItemType.VIDEO, SavedItemType.AUDIO)) return HttpResponse.notFound()
        val context = contextProvider() ?: return HttpResponse.notFound()
        val path = if (thumbnail) item.thumbnailPath ?: item.linkImage ?: item.backdropUrl else
            if (item.type == SavedItemType.AUDIO) item.thumbnailPath ?: item.content else item.content
        if (path.isNullOrBlank() || !path.startsWith("/")) return HttpResponse.notFound()
        val file = File(path).canonicalFile
        val roots = listOfNotNull(context.filesDir, context.cacheDir, context.getExternalFilesDir(null))
        if (roots.none { file.path.startsWith(it.canonicalPath + File.separator) } || !file.isFile) return HttpResponse.notFound("Attachment unavailable on this device")
        val length = file.length()
        val range = request.header("Range")
        var start = 0L
        var end = length - 1
        if (range != null) {
            val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(range)
                ?: return HttpResponse.text(416, "Invalid range", mapOf("Content-Range" to "bytes */$length"))
            val first = match.groupValues[1]
            val last = match.groupValues[2]
            if (first.isBlank()) {
                val suffix = last.toLongOrNull() ?: 0
                start = (length - suffix).coerceAtLeast(0)
            } else {
                start = first.toLongOrNull() ?: length
                end = last.toLongOrNull()?.coerceAtMost(end) ?: end
            }
            if (start < 0 || start >= length || end < start) return HttpResponse.text(416, "Range unavailable", mapOf("Content-Range" to "bytes */$length"))
            end = minOf(end, start + 1_048_575)
        } else if (length > 10_485_760) {
            return HttpResponse.text(413, "Use the media player to stream this attachment")
        }
        if (length == 0L) return HttpResponse.notFound("Empty attachment")
        return try {
            val bytes = ByteArray((end - start + 1).toInt())
            RandomAccessFile(file, "r").use { it.seek(start); it.readFully(bytes) }
            val headers = mutableMapOf("Accept-Ranges" to "bytes")
            if (range != null) headers["Content-Range"] = "bytes $start-$end/$length"
            HttpResponse.bytes(mimeTypeFor(file.name), bytes, if (range != null) 206 else 200, headers)
        } catch (_: Exception) { HttpResponse.serverError("Unable to read attachment") }
    }

    private fun serveStaticAsset(rawPath: String): HttpResponse {
        val path = if (rawPath == "/" || rawPath.isBlank()) "/index.html" else rawPath
        val fonts = mapOf("inter" to R.font.inter_variable, "literata" to R.font.literata_variable,
            "source_serif_4" to R.font.source_serif_4_variable, "lexend" to R.font.lexend_variable,
            "atkinson_hyperlegible" to R.font.atkinson_hyperlegible_regular, "jetbrains_mono" to R.font.jetbrains_mono_variable)
        if (path.startsWith("/fonts/")) {
            val resource = fonts[path.removePrefix("/fonts/").removeSuffix(".ttf")] ?: return HttpResponse.notFound()
            val context = contextProvider() ?: return HttpResponse.notFound()
            return HttpResponse.bytes("font/ttf", context.resources.openRawResource(resource).use { it.readBytes() })
        }
        if (path !in setOf("/index.html", "/app.js", "/style.css", "/capture.css", "/editor.js", "/markdown.js", "/highlight.js", "/markdown-renderer.js", "/markdown-worker.js")) return HttpResponse.notFound("Asset not found")
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
                "gif" -> "image/gif"
                "webm" -> "video/webm"
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

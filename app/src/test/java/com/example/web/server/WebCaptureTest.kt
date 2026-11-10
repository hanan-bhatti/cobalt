package com.example.web.server

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.web.auth.AuthResult
import com.example.web.auth.WebCompanionSecurityManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WebCaptureTest {
    private val security = WebCompanionSecurityManager()
    private val token = (security.verifyPin(security.currentPin, "127.0.0.1") as AuthResult.Success).token
    private val rows = mutableMapOf<String, SavedItem>()
    private val folderRows = mutableListOf<CustomFolderEntity>()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = object : WebNoteStore {
        override suspend fun list(query: String?) = rows.values.toList()
        override suspend fun read(id: String) = rows[id]
        override suspend fun save(item: SavedItem, beforeCommit: () -> Unit): SavedItem { beforeCommit(); rows[item.id] = item; return item }
        override suspend fun create(item: SavedItem): SavedItem { rows[item.id] = item; return item }
        override suspend fun folders() = folderRows.toList()
        override suspend fun createFolder(name: String, color: String?, icon: String?, isPinned: Boolean) { folderRows.add(CustomFolderEntity(name, color, icon, isPinned)) }
    }
    private val router = WebCompanionRouter(security, { null }, { null }, { context }, { store })
    private val id = "00000000-0000-4000-8000-000000000001"
    private suspend fun request(path: String, body: String, query: Map<String,String> = emptyMap(), method: String = "POST") =
        router.handleRequest(HttpRequest(method, path, path, query, mapOf("Content-Type" to "application/json", "Authorization" to "Bearer $token"), body.toByteArray(), "127.0.0.1"))
    private fun payload(type: String, content: String) = JSONObject().put("id", id).put("type", type).put("title", "Desktop capture")
        .put("content", content).put("folders", JSONArray(listOf("Ideas"))).toString()
    @Test fun notesAreDurableStoreCapturesAndRetryIsIdempotent() = runBlocking {
        val body = payload("TEXT", "# Heading\n\nUnicode اردو")
        assertEquals(201, request("/api/notes", body).statusCode)
        assertEquals(200, request("/api/notes", body).statusCode)
        assertEquals(1, rows.size)
        assertEquals("# Heading\n\nUnicode اردو", rows[id]?.content)
        assertEquals(listOf("Ideas"), rows[id]?.folders)
        assertEquals(409, request("/api/notes", payload("TEXT", "Changed retry")).statusCode)
        assertEquals("# Heading\n\nUnicode اردو", rows[id]?.content)
    }
    @Test fun unsafeLinksInvalidTypesAndMalformedFoldersNeverWrite() = runBlocking {
        assertEquals(400, request("/api/notes", payload("LINK", "javascript:alert(1)")).statusCode)
        assertEquals(400, request("/api/notes", payload("LINK", "https://name:secret@example.com")).statusCode)
        assertEquals(400, request("/api/notes", payload("IMAGE", "body")).statusCode)
        assertEquals(400, request("/api/notes", """{"type":"TEXT","title":"x","content":"x","folders":[12]}""").statusCode)
        assertEquals(0, rows.size)
        assertEquals(201, request("/api/notes", payload("LINK", "https://example.com")).statusCode)
    }
    @Test fun codeMediaAndEmptyNotesAreSupported() = runBlocking {
        assertEquals(201, request("/api/notes", payload("CODE", "fun main() = Unit")).statusCode)
        rows.clear()
        assertEquals(201, request("/api/notes", payload("MEDIA", "Synopsis")).statusCode)
        assertEquals("movie", rows[id]?.mediaType)
        assertEquals("Plan to Watch", rows[id]?.watchStatus)
        rows.clear()
        assertEquals(201, request("/api/notes", payload("TEXT", "")).statusCode)
        assertEquals("", rows[id]?.content)
    }
    @Test fun folderColorsIconsAndDuplicateValidation() = runBlocking {
        assertEquals(201, request("/api/folders", """{"name":"Ideas","color":"#aabbcc","icon":"idea"}""").statusCode)
        assertEquals("#aabbcc", folderRows.first().colorHex)
        assertEquals("idea", folderRows.first().iconName)
        assertEquals(409, request("/api/folders", """{"name":"Ideas"}""").statusCode)
        assertEquals(409, request("/api/folders", """{"name":" ideas "}""").statusCode)
        assertEquals(400, request("/api/folders", """{"name":"Invalid","color":"red;display:none"}""").statusCode)
        assertEquals(400, request("/api/folders", """{"name":"Invalid","icon":"script"}""").statusCode)
        assertEquals(1, folderRows.size)
    }
    @Test fun nativeFolderPaletteAndPinSettingReachTheRepository() = runBlocking {
        com.example.ui.screens.folderPresetIcons.forEachIndexed { index, icon ->
            val body = JSONObject().put("name", "Folder $index").put("icon", icon)
                .put("color", com.example.ui.screens.folderPresetColors[index].first).put("isPinned", true)
            assertEquals(201, request("/api/folders", body.toString()).statusCode)
            assertEquals(icon, folderRows.last().iconName)
            assertTrue(folderRows.last().isPinned)
        }
        val response = JSONObject(request("/api/folders", "", method = "GET").body.toString(Charsets.UTF_8))
        assertTrue(response.getJSONArray("folders").getJSONObject(0).getBoolean("isPinned"))
        assertEquals(400, request("/api/folders", """{"name":"Bad pin","isPinned":"true"}""").statusCode)
    }
    @Test fun sortingHappensBeforePaginationAndOutOfRangePageIsClamped() = runBlocking {
        repeat(30) { i -> rows["row-$i"] = SavedItem(id = "row-$i", type = SavedItemType.TEXT, title = "Title " + (30-i), content = "", updatedAt = i.toLong()) }
        val response = JSONObject(request("/api/notes", "", mapOf("sort" to "updated", "limit" to "25", "page" to "1"), "GET").body.toString(Charsets.UTF_8))
        assertEquals("row-29", response.getJSONArray("items").getJSONObject(0).getString("id"))
        val last = JSONObject(request("/api/notes", "", mapOf("page" to "999"), "GET").body.toString(Charsets.UTF_8))
        assertEquals(2, last.getInt("page"))
        assertEquals(5, last.getJSONArray("items").length())
    }
    @Test fun uploadedImageIsPrivateAndSupportsByteRanges() = runBlocking {
        val bytes = byteArrayOf(1,2,3,4,5)
        val upload = HttpRequest("POST", "/api/attachments", "/api/attachments", mapOf("id" to id, "title" to "Image"),
            mapOf("Content-Type" to "image/png", "Authorization" to "Bearer $token"), bytes, "127.0.0.1")
        assertEquals(201, router.handleRequest(upload).statusCode)
        assertEquals(200, router.handleRequest(upload).statusCode)
        assertEquals(1, rows.size)
        val saved = rows.getValue(id)
        assertTrue(File(saved.content).canonicalPath.startsWith(context.filesDir.canonicalPath))
        val media = HttpRequest("GET", "/api/media/$id", "/api/media/$id", emptyMap(),
            mapOf("Range" to "bytes=1-3", "Authorization" to "Bearer $token"), ByteArray(0), "127.0.0.1")
        val response = router.handleRequest(media)
        assertEquals(206, response.statusCode); assertArrayEquals(byteArrayOf(2,3,4), response.body)
        assertEquals("bytes 1-3/5", response.headers["Content-Range"])
        File(saved.content).delete()
        Unit
    }
    @Test fun uploadErrorsDoNotCreateMetadata() = runBlocking {
        val bad = HttpRequest("POST", "/api/attachments", "/api/attachments", emptyMap(),
            mapOf("Content-Type" to "text/html", "Authorization" to "Bearer $token"), byteArrayOf(1), "127.0.0.1")
        assertEquals(415, router.handleRequest(bad).statusCode)
        assertEquals(400, router.handleRequest(bad.copy(headers = bad.headers + ("Content-Type" to "image/png"), body = ByteArray(0))).statusCode)
        assertTrue(rows.isEmpty())
    }
}

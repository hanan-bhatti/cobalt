package com.example.web.server

import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.web.auth.AuthResult
import com.example.web.auth.WebCompanionSecurityManager
import com.example.web.session.EditorSessionRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class WebNoteEditingTest {
    private val security = WebCompanionSecurityManager()
    private lateinit var token: String
    private var item = SavedItem(id = "fixture", type = SavedItemType.TEXT, title = "Original", content = "🙂 اردو", updatedAt = 1, contentHash = "base", folders = listOf("Work"))
    private var beforeSave: () -> Unit = {}
    private val store = object : WebNoteStore {
        override suspend fun list(query: String?) = listOf(item)
        override suspend fun read(id: String) = item.takeIf { it.id == id }
        override suspend fun save(item: SavedItem, beforeCommit: () -> Unit): SavedItem {
            beforeSave()
            beforeCommit()
            this@WebNoteEditingTest.item = item.copy(updatedAt = item.updatedAt + 1, contentHash = "next")
            return this@WebNoteEditingTest.item
        }
    }
    private val router = WebCompanionRouter(security, { null }, { null }, noteStoreProvider = { store })
    @Before fun setup() {
        EditorSessionRegistry.clear()
        token = (security.verifyPin(security.currentPin, "127.0.0.1") as AuthResult.Success).token
    }
    @After fun cleanup() { EditorSessionRegistry.clear(); EditorSessionRegistry.timeProvider = System::currentTimeMillis }
    private suspend fun request(method: String, path: String, body: String = "{}", auth: String = token, query: Map<String,String> = emptyMap()) =
        router.handleRequest(HttpRequest(method, path, path, query, mapOf("Content-Type" to "application/json", "Authorization" to "Bearer $auth"), body.toByteArray(), "127.0.0.1"))
    private fun payload(content: String = "Updated 🙂 اردو") = JSONObject().put("title", "Changed").put("content", content).put("updatedAt", 1).put("contentHash", "base").toString()
    @Test fun editSaveReopenRetainsUnicodeAndFolders() = runBlocking {
        assertEquals(200, request("POST", "/api/notes/fixture/lock").statusCode)
        assertEquals(200, request("PUT", "/api/notes/fixture", payload()).statusCode)
        val reopened = JSONObject(request("GET", "/api/notes/fixture").body.toString(Charsets.UTF_8))
        assertEquals("Updated 🙂 اردو", reopened.getString("content"))
        assertEquals("Work", reopened.getJSONArray("folders").getString(0))
        assertEquals(409, request("PUT", "/api/notes/fixture", payload("stale")).statusCode)
        assertEquals("Updated 🙂 اردو", item.content)
    }
    @Test fun writeRequiresLockAndMobilePreemptsWeb() = runBlocking {
        assertEquals(409, request("PUT", "/api/notes/fixture", payload()).statusCode)
        request("POST", "/api/notes/fixture/lock")
        EditorSessionRegistry.acquireMobileLock("fixture")
        assertEquals(409, request("PUT", "/api/notes/fixture", payload()).statusCode)
        assertEquals(409, request("PUT", "/api/notes/fixture/lock").statusCode)
        assertEquals("🙂 اردو", item.content)
    }
    @Test fun secondBrowserCannotAcquireOrOverwrite() = runBlocking {
        request("POST", "/api/notes/fixture/lock")
        val second = (security.verifyPin(security.currentPin, "127.0.0.1") as AuthResult.Success).token
        assertEquals(409, request("POST", "/api/notes/fixture/lock", auth = second).statusCode)
        assertEquals(409, request("PUT", "/api/notes/fixture", payload(), second).statusCode)
    }
    @Test fun logoutReleasesOnlyOwnedLocks() = runBlocking {
        request("POST", "/api/notes/fixture/lock")
        request("POST", "/api/auth/logout")
        assertEquals(401, request("GET", "/api/notes/fixture").statusCode)
        assertEquals(com.example.web.session.NoteLockState.Unlocked, EditorSessionRegistry.getNoteLockState("fixture"))
    }
    @Test fun mobileOpeningDuringSaveRejectsCommit() = runBlocking {
        request("POST", "/api/notes/fixture/lock")
        beforeSave = { EditorSessionRegistry.acquireMobileLock("fixture") }
        assertEquals(409, request("PUT", "/api/notes/fixture", payload()).statusCode)
        assertEquals("🙂 اردو", item.content)
    }
    @Test fun malformedPayloadAndUnsupportedItemCannotMutate() = runBlocking {
        assertEquals(400, request("PUT", "/api/notes/fixture", "{}").statusCode)
        item = item.copy(type = SavedItemType.LINK)
        assertEquals(405, request("POST", "/api/notes/fixture/lock").statusCode)
        assertEquals(405, request("PUT", "/api/notes/fixture", payload()).statusCode)
    }
    @Test fun emptyBodyIsValidAndOversizeIsRejected() = runBlocking {
        request("POST", "/api/notes/fixture/lock")
        assertEquals(413, request("PUT", "/api/notes/fixture", payload("x".repeat(5_000_001))).statusCode)
        assertEquals(200, request("PUT", "/api/notes/fixture", payload("")).statusCode)
        assertEquals("", item.content)
    }
    @Test fun foldersMatchExactlyAndDeletedDetailsStayHidden() = runBlocking {
        val result = JSONObject(request("GET", "/api/notes", query = mapOf("folder" to "Wor")).body.toString(Charsets.UTF_8))
        assertEquals(0, result.getInt("totalItems"))
        item = item.copy(isDeleted = true)
        assertEquals(404, request("GET", "/api/notes/fixture").statusCode)
    }
    @Test fun expiredLockCannotBeRevivedByHeartbeat() = runBlocking {
        var now = 1000L
        EditorSessionRegistry.timeProvider = { now }
        request("POST", "/api/notes/fixture/lock")
        now += 60_001
        assertEquals(409, request("PUT", "/api/notes/fixture/lock").statusCode)
        assertEquals(409, request("PUT", "/api/notes/fixture", payload()).statusCode)
    }
}

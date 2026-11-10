package com.example.web

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.repository.CobaltRepository
import com.example.notes.NotePersistence
import com.example.web.auth.WebCompanionSecurityManager
import com.example.web.server.CobaltEmbeddedServer
import com.example.web.server.WebCompanionRouter
import com.example.web.session.EditorSessionRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Real debug storage and HTTP; retains explicitly labelled fixtures for browser/native review. */
@RunWith(AndroidJUnit4::class)
class WebCompanionDeviceTest {
    @Test fun actualHttpReadEditSaveHistoryAndConflicts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".debug"))
        val db = AppDatabase.getDatabase(context)
        val repo = CobaltRepository(context, db)
        val prefix = "web-review-${UUID.randomUUID()}"
        val before = db.savedItemDao().getAllItems().associateBy { it.id }
        val persistence = NotePersistence(context, db)
        val largeText = "# Web review large Unicode note\n\n" + "🙂 اردو café — retained line\n".repeat(12_000) + "END_OF_LARGE_NOTE"
        val small = repo.saveItem(SavedItem(id = "$prefix-small", type = SavedItemType.TEXT, title = "Web review Markdown", content = "# Read and edit\n\n**Bold** and *italic*.\n\n| A | B |\n| --- | --- |\n| one | two |\n\n[Safe link](https://example.com)\n\n<script>window.webReviewXss = true</script>\n[Unsafe](javascript:alert%281%29)", folders = listOf("Web review")))
        val large = repo.saveItem(SavedItem(id = "$prefix-large", type = SavedItemType.TEXT, title = "Web review large Unicode", content = largeText))
        repo.saveItem(SavedItem(id = "$prefix-code", type = SavedItemType.CODE, title = "Web review code", content = "fun main() { println(\"🙂\") }"))
        repo.saveItem(SavedItem(id = "$prefix-empty", type = SavedItemType.TEXT, title = "Web review empty", content = ""))
        val imageFile = File(context.filesDir, "$prefix-image.png")
        Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff7c6bb0.toInt()) }.let {
            imageFile.outputStream().use { output -> it.compress(Bitmap.CompressFormat.PNG, 100, output) }; it.recycle()
        }
        val image = repo.saveItem(SavedItem(id = "$prefix-image", type = SavedItemType.IMAGE, title = "Web review image", content = imageFile.absolutePath))
        val security = WebCompanionSecurityManager(burstCapacity = 200)
        val router = WebCompanionRouter(security, { repo }, { db }, { context })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = CobaltEmbeddedServer(0, security) { router.handleRequest(it) }
        val port = server.start(scope)
        fun request(method: String, path: String, json: JSONObject? = null, token: String? = null): Pair<Int, ByteArray> {
            val conn = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            conn.requestMethod = method; conn.connectTimeout = 5000; conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/json")
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (method != "GET") { conn.doOutput = true; conn.outputStream.use { it.write((json ?: JSONObject()).toString().toByteArray()) } }
            return try { val code = conn.responseCode; code to (if (code < 400) conn.inputStream else conn.errorStream).use { it.readBytes() } } finally { conn.disconnect() }
        }
        fun decode(response: Pair<Int, ByteArray>) = JSONObject(response.second.toString(Charsets.UTF_8))
        fun login() = decode(request("POST", "/api/auth/login", JSONObject().put("pin", security.currentPin))).getString("token")
        fun payload(note: SavedItem, text: String) = JSONObject().put("title", note.title).put("content", text).put("updatedAt", note.updatedAt).put("contentHash", note.contentHash)
        try {
            assertEquals(401, request("GET", "/api/notes").first)
            assertEquals(200, request("GET", "/").first)
            val token = login()
            val detail = decode(request("GET", "/api/notes/${large.id}", token = token))
            assertEquals(largeText, detail.getString("content"))
            assertEquals(200, request("POST", "/api/notes/${large.id}/lock", token = token).first)
            val changedText = largeText + "\nSaved from desktop 🙂 اردو"
            val save = request("PUT", "/api/notes/${large.id}", payload(large, changedText), token)
            assertEquals(save.second.toString(Charsets.UTF_8), 200, save.first)
            val native = requireNotNull(repo.getItemForWeb(large.id, 5_000_000))
            assertEquals(changedText, native.content)
            assertTrue(native.updatedAt > large.updatedAt)
            assertNotEquals(large.contentHash, native.contentHash)
            assertEquals(largeText, persistence.store.open(large.id, large.contentHash!!).use { it.readBytes().toString(Charsets.UTF_8) })
            assertEquals(changedText, decode(request("GET", "/api/notes/${large.id}", token = token)).getString("content"))
            assertEquals(409, request("PUT", "/api/notes/${large.id}", payload(large, "stale"), token).first)
            assertEquals(200, request("POST", "/api/notes/${small.id}/lock", token = token).first)
            val second = login()
            assertEquals(409, request("POST", "/api/notes/${small.id}/lock", token = second).first)
            EditorSessionRegistry.acquireMobileLock(small.id)
            assertEquals(409, request("PUT", "/api/notes/${small.id}", payload(small, "overwrite"), token).first)
            EditorSessionRegistry.releaseMobileLock(small.id)
            assertEquals(200, request("POST", "/api/auth/logout", token = token).first)
            assertEquals(200, request("POST", "/api/notes/${small.id}/lock", token = second).first)
            assertEquals(200, request("GET", "/api/media/${image.id}", token = second).first)
            assertEquals(404, request("GET", "/api/media/${small.id}", token = second).first)
            assertFalse(decode(request("GET", "/api/notes/${image.id}", token = second)).getString("content").contains(context.filesDir.absolutePath))
            assertTrue(decode(request("GET", "/api/notes?q=Web%20review", token = second)).getInt("totalItems") >= 5)
            assertEquals(before, db.savedItemDao().getAllItems().filter { it.id in before }.associateBy { it.id })
            File(context.filesDir, "web-review-results.json").writeText(JSONObject().put("prefix", prefix).put("existingRowsRetained", before.size).put("largeBytes", largeText.toByteArray().size).put("httpReadSaveNativeReopen", true).put("historyRetained", true).put("staleMobileAndOtherBrowserBlocked", true).toString())
        } finally {
            EditorSessionRegistry.releaseMobileLock(small.id)
            EditorSessionRegistry.clearWebLocks()
            server.stop(); scope.cancel()
        }
    }

    @Test fun seedAndCheckReadOnlyTypes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".debug"))
        val db = AppDatabase.getDatabase(context)
        val repo = CobaltRepository(context, db)
        val prefix = "web-review-types-${UUID.randomUUID()}"
        val security = WebCompanionSecurityManager()
        val token = (security.verifyPin(security.currentPin, "127.0.0.1") as com.example.web.auth.AuthResult.Success).token
        val router = WebCompanionRouter(security, { repo }, { db }, { context })
        val before = db.savedItemDao().getAllItems().associateBy { it.id }
        val fixtures = listOf(
            SavedItem(id="$prefix-link",type=SavedItemType.LINK,title="Web review link",content="https://example.com"),
            SavedItem(id="$prefix-movie",type=SavedItemType.MEDIA,title="Web review fictional movie",content="",mediaType="movie",extractedText="Fictional movie fixture. Read-only."),
            SavedItem(id="$prefix-show",type=SavedItemType.MEDIA,title="Web review fictional show",content="",mediaType="tv",extractedText="Fictional show fixture. Read-only.")
        ).toMutableList()
        val assets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        listOf("review-audio.wav" to SavedItemType.AUDIO, "review-video.mp4" to SavedItemType.VIDEO).forEach { (name,type) ->
            val file = File(context.filesDir,"$prefix-$name")
            assets.open("web/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
            fixtures.add(SavedItem(id="$prefix-${type.name.lowercase()}",type=type,title="Web review ${type.name.lowercase()}",content=file.absolutePath))
        }
        fixtures.forEach { item ->
            repo.saveItem(item)
            fun request(method:String,path:String) = com.example.web.server.HttpRequest(method,path,path,emptyMap(),mapOf("Authorization" to "Bearer $token","Content-Type" to "application/json"),"{}".toByteArray(),"127.0.0.1")
            val detail = router.handleRequest(request("GET","/api/notes/${item.id}"))
            assertEquals(200,detail.statusCode)
            assertFalse(JSONObject(detail.body.toString(Charsets.UTF_8)).getBoolean("editable"))
            assertEquals(405,router.handleRequest(request("POST","/api/notes/${item.id}/lock")).statusCode)
            if (item.type in setOf(SavedItemType.AUDIO,SavedItemType.VIDEO)) {
                assertArrayEquals(File(item.content).readBytes(),router.handleRequest(request("GET","/api/media/${item.id}")).body)
            }
        }
        assertEquals(before,db.savedItemDao().getAllItems().filter { it.id in before }.associateBy { it.id })
        File(context.filesDir,"web-review-types-results.json").writeText(JSONObject().put("prefix",prefix).put("readOnlyTypes",true).put("audioVideoBytesExact",true).toString())
    }
}

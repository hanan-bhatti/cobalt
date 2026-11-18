package com.example.web.server

import com.example.web.auth.WebCompanionSecurityManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class WorkspaceAssetsTest {
    @Test
    fun workspaceLoadsItsBundleAndRejectsUnlistedPaths() = runBlocking {
        val router = WebCompanionRouter(
            securityManager = WebCompanionSecurityManager(), repositoryProvider = { null },
            databaseProvider = { null }, contextProvider = { RuntimeEnvironment.getApplication() }
        )
        suspend fun get(path: String) = router.handleRequest(HttpRequest(
            "GET", path, path, emptyMap(), emptyMap(), byteArrayOf(), "127.0.0.1"
        ))
        for (path in listOf("/workspace", "/workspace/", "/workspace/index.html", "/workspace/app.js", "/workspace/app.css", "/workspace/icons/logo.svg")) {
            val result = get(path)
            assertEquals(path, 200, result.statusCode)
            assertTrue(path, result.body.isNotEmpty())
        }
        for (path in listOf("/workspace/../index.html", "/workspace/icons/../../index.html", "/workspace/secret.json", "/workspace/demo/example.png")) {
            assertEquals(path, 404, get(path).statusCode)
        }
    }
}

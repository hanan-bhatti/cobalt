package com.example.util

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CobaltCustomTabsTest {

    @Test
    fun testNormalizeUrlAddsHttpsScheme() {
        val raw = "github.com/hanan-bhatti/cobalt"
        val normalized = CobaltCustomTabsLauncher.normalizeUrl(raw)
        assertEquals("https://github.com/hanan-bhatti/cobalt", normalized)
    }

    @Test
    fun testNormalizeUrlPreservesExistingScheme() {
        val httpUrl = "http://example.com/page"
        val httpsUrl = "https://example.com/page"

        assertEquals(httpUrl, CobaltCustomTabsLauncher.normalizeUrl(httpUrl))
        assertEquals(httpsUrl, CobaltCustomTabsLauncher.normalizeUrl(httpsUrl))
    }

    @Test
    fun testNormalizeUrlTrimsWhitespace() {
        val raw = "   https://example.com/trim   "
        val normalized = CobaltCustomTabsLauncher.normalizeUrl(raw)
        assertEquals("https://example.com/trim", normalized)
    }

    @Test
    fun testActionReceiverCopyLinkCopiesToClipboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = CobaltCustomTabsActionReceiver()

        val testUrl = "https://example.com/test-article"
        val intent = Intent(CobaltCustomTabsActionReceiver.ACTION_COPY_LINK).apply {
            putExtra(CobaltCustomTabsActionReceiver.EXTRA_URL, testUrl)
        }

        receiver.onReceive(context, intent)

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        assertNotNull(clip)
        assertEquals(1, clip!!.itemCount)
        assertEquals(testUrl, clip.getItemAt(0).text.toString())
    }

    @Test
    fun testLaunchHandlesFallbackGracefully() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // In Robolectric environment without full CCT service, launch should safely invoke fallback without throwing unhandled exceptions
        CobaltCustomTabsLauncher.launch(
            context = context,
            url = "https://example.com",
            isDarkTheme = false
        )
        // Passes without crashing
        assertTrue(true)
    }

    @Test
    fun testParseMarkdownLinkAnnotationInvokesListener() {
        var clickedUrl = ""
        val annotated = com.example.ui.screens.parseMarkdown(
            text = "Here is a [Google](https://google.com) link and https://cobalt.app",
            onLinkClick = { clickedUrl = it }
        )
        assertNotNull(annotated)
        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(2, linkAnnotations.size)
        val firstLink = linkAnnotations[0].item as androidx.compose.ui.text.LinkAnnotation.Url
        assertEquals("https://google.com", firstLink.url)
        assertNotNull(firstLink.linkInteractionListener)
        firstLink.linkInteractionListener?.onClick(firstLink)
        assertEquals("https://google.com", clickedUrl)
    }
}

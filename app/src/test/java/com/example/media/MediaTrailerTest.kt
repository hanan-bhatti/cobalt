package com.example.media

import com.example.ui.screens.extractYouTubeVideoId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MediaTrailerTest {
    @Test fun trailerLinksSupportWatchShortAndEmbedFormatsWithoutAcceptingLookalikeDomains() {
        val id = "abcdefghijk"
        listOf("https://www.youtube.com/watch?v=$id&t=3", "https://youtu.be/$id", "https://youtube.com/shorts/$id", "https://www.youtube-nocookie.com/embed/$id").forEach {
            assertEquals(id, extractYouTubeVideoId(it))
        }
        assertNull(extractYouTubeVideoId("https://notyoutube.com/watch?v=$id"))
        assertNull(extractYouTubeVideoId("javascript:alert(1)"))
    }
    @Test fun dockAndCaptureKeepProviderIdsAndMangaFacts() {
        val result = com.example.data.remote.MediaSearchResultItem("tmdb_movie_550","Title","movie",tmdbId = 550, traktId = 10,
            mediaFacts = com.example.data.model.MediaFacts("Jikan", chapterCount = 12, volumeCount = 3))
        val saved = result.toSavedMedia()
        assertEquals(550,saved.tmdbId); assertEquals(10L,saved.traktId)
        assertEquals(12,saved.mediaFacts!!.chapterCount)
        assertEquals(saved.mediaFacts,com.example.data.model.MediaFacts.fromJson(saved.mediaFacts.toJson()))
    }
    @Test fun embedUsesInstalledAppIdentityAndRejectsHtmlOrScriptInVideoIds() {
        val html = YouTubeEmbed.html("abcdefghijk", "com.hanan_bhatti.cobalt")
        assertTrue(html.contains("origin:'https://com.hanan_bhatti.cobalt'"))
        assertTrue(html.contains("strict-origin-when-cross-origin"))
        assertEquals("https://com.hanan_bhatti.cobalt.debug", YouTubeEmbed.origin("com.hanan_bhatti.cobalt.debug"))
        try { YouTubeEmbed.html("</script>", "com.example"); fail("Invalid ID should never reach HTML") } catch (_: IllegalArgumentException) {}
        try { YouTubeEmbed.origin("com.example';"); fail("Invalid identity should be rejected") } catch (_: IllegalArgumentException) {}
    }
    @Test fun mangaUsesReadingLanguageAndCannotBeSentAsATraktShow() {
        assertEquals("Plan to Read", mediaStatusLabel(null, "manga"))
        assertEquals("Reading", mediaStatusLabel("Watching", "manga"))
        val manga = com.example.data.model.SavedItem(type = com.example.data.model.SavedItemType.MEDIA, title = "Manga", content = "", mediaType = "manga", tmdbId = 1)
        try { com.example.data.remote.trakt.TraktSyncEngine.ids(manga); fail("Manga must stay local") } catch (_: IllegalArgumentException) {}
    }
    @Test fun embeddedFrameCanLoadYouTubeButCannotNavigateTheHostOrUseLookalikeDomains() {
        assertTrue(YouTubeEmbed.allowNavigation(android.net.Uri.parse("https://www.youtube.com/embed/abcdefghijk"), false))
        assertFalse(YouTubeEmbed.allowNavigation(android.net.Uri.parse("https://www.youtube.com/watch?v=abcdefghijk"), true))
        assertFalse(YouTubeEmbed.allowNavigation(android.net.Uri.parse("https://youtube.com.evil.test/embed/abcdefghijk"), false))
        assertFalse(YouTubeEmbed.allowNavigation(android.net.Uri.parse("file:///etc/passwd"), false))
    }
    @Test fun trailerEventBridgeExposesOnlyAnnotatedPlaybackCallbacks() {
        val type = com.example.ui.components.TrailerEvents::class.java
        val methods = listOf(type.getMethod("ready"), type.getMethod("state", Int::class.javaPrimitiveType), type.getMethod("error", Int::class.javaPrimitiveType))
        methods.forEach { assertNotNull(it.getAnnotation(android.webkit.JavascriptInterface::class.java)) }
        assertNull(type.getMethod("release").getAnnotation(android.webkit.JavascriptInterface::class.java))
    }
}

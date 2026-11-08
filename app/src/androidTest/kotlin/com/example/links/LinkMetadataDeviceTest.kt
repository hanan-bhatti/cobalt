package com.example.links

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.repository.CobaltRepository
import com.example.data.repository.LinkMetadata
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Local parser/network checks plus an explicitly selected, read-only public-link probe. */
@RunWith(AndroidJUnit4::class)
class LinkMetadataDeviceTest {
    private fun parse(html: String, url: String = "https://example.org/article") =
        LinkMetadataExtractor.parse(Jsoup.parse(html, url))

    @Test fun websiteAndSocialMetadataFormats() {
        val expected = LinkMetadata("Actual post", "A & B", "https://example.org/cover.jpg")
        for (attribute in listOf("property", "name")) {
            assertEquals(expected, parse("""<title>Fallback</title><meta $attribute="og:title" content="Actual post">
                <meta $attribute="og:description" content="A &amp; B"><meta $attribute="og:image" content="/cover.jpg">"""))
            assertEquals(expected, parse("""<title>Generic title</title><meta $attribute="twitter:title" content="Actual post">
                <meta $attribute="twitter:description" content="A &amp; B"><meta $attribute="twitter:image" content="//example.org/cover.jpg">"""))
        }
        assertEquals(expected, parse("""<meta property="og:title" content=""><meta property="og:title" content="Actual post">
            <meta name="description" content="A &amp; B"><meta property="og:image" content="javascript:alert(1)">
            <meta name="twitter:image:src" content="/cover.jpg">"""))
        assertEquals(expected, parse("""<meta itemprop="name" content="Actual post"><meta itemprop="description" content="A &amp; B">
            <meta itemprop="thumbnailUrl" content="/cover.jpg">"""))
        assertEquals("https://example.org/cover.jpg?f=webp&region=us", parse("""<title>Video</title>
            <meta property="og:image" content="https://example.org/cover.jpg?f=webp&amp;region=us">""").imageUrl)
        for (type in listOf("VideoObject", "Article", "SocialMediaPosting", "Product", "WebPage")) {
            assertEquals(expected, parse("""<script type="application/ld+json">{"@context":"https://schema.org","@graph":[
                {"@type":"$type","name":"Actual post","description":"A & B","image":[{"url":"/cover.jpg"}]}]}</script>"""))
        }
        assertEquals(expected, parse("""<script type="application/ld+json">{broken</script>
            <script type="application/ld+json">[{"@type":"WebPage","name":"Shell","video":{
            "@type":"VideoObject","name":"Actual post","description":"A & B","thumbnailUrl":"/cover.jpg"}}]</script>"""))
        assertEquals(LinkMetadata("Plain page", null, null), parse("<title>Plain page</title>"))
        assertEquals(LinkMetadata(), parse("""<title>Just a moment...</title><meta property="og:title" content="Wrong preview">"""))
        assertEquals(LinkMetadata(), parse("""<title>JUST A MOMENT...</title><meta property="og:title" content="Wrong preview">"""))
        for (url in listOf("https://www.instagram.com/reel/abc/", "https://www.tiktok.com/@person/video/123",
            "https://x.com/person/status/123", "https://www.facebook.com/reel/123", "https://www.reddit.com/r/test/comments/abc")) {
            val name = when {
                "instagram" in url -> "Instagram"
                "tiktok" in url -> "TikTok - Make Your Day"
                "facebook" in url -> "Facebook"
                "reddit" in url -> "Reddit"
                else -> "X / X"
            }
            assertEquals(LinkMetadata(), parse("""<title>$name</title><meta property="og:image" content="/logo.png">""", url))
            val actual = parse("""<title>$name</title><meta property="og:title" content="Actual post">
                <meta property="og:description" content="A &amp; B"><meta property="og:image" content="https://example.org/cover.jpg">""", url)
            assertEquals(expected, actual)
        }
        assertEquals(LinkMetadata(), parse("""<title>Log in • Instagram</title><meta name="description" content="Create an account or log in">
            <meta property="og:image" content="/logo.png">""", "https://www.instagram.com/accounts/login/"))
        assertEquals(LinkMetadata(), parse("""<title>TikTok - Make Your Day</title><meta name="description" content="TikTok - trends start here. Download the app.">
            <meta property="og:image" content="/logo.png">""", "https://www.tiktok.com/@person/video/123"))
    }

    @Test fun embeddedVideoDataAndUrlFormats() {
        val youtube = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        for (url in listOf(youtube, "https://youtu.be/dQw4w9WgXcQ?si=tracking", "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42", "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ", "youtube.com/watch?v=dQw4w9WgXcQ")) {
            assertEquals(youtube, LinkMetadataExtractor.pageUrl(url))
        }
        val unrelated = "https://example.org/shorts/dQw4w9WgXcQ?keep=true"
        assertEquals(unrelated, LinkMetadataExtractor.pageUrl(unrelated))
        val details = JSONObject().put("videoId", "dQw4w9WgXcQ").put("title", "Real video").put("shortDescription", "Full description with {braces} and \"quotes\"")
            .put("thumbnail", JSONObject().put("thumbnails", JSONArray().put(JSONObject().put("url", "https://example.org/low.jpg"))
                .put(JSONObject().put("url", "https://example.org/high.jpg"))))
        assertEquals(LinkMetadata("Real video", "Full description with {braces} and \"quotes\"", "https://example.org/high.jpg"),
            parse("""<meta property="og:description" content="Truncated"><script>var ytInitialPlayerResponse={"videoDetails":$details};</script>""", youtube))
        details.put("videoId", "aaaaaaaaaaa")
        assertEquals(LinkMetadata(), parse("""<script>var data={"videoDetails":$details};</script>""", youtube))
        val tiktokUrl = "https://www.tiktok.com/@person/video/123"
        val item = """{"id":"123","desc":"Real caption","video":{"cover":"https://example.org/cover.jpg"}}"""
        for ((id, json) in listOf(
            "__UNIVERSAL_DATA_FOR_REHYDRATION__" to """{"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":$item}}}}""",
            "SIGI_STATE" to """{"ItemModule":{"123":$item}}""")) {
            assertEquals(LinkMetadata("Real caption", "Real caption", "https://example.org/cover.jpg"),
                parse("""<title>TikTok - Make Your Day</title><script id="$id" type="application/json">$json</script>""", tiktokUrl))
        }
        for (key in listOf("shortcode_media", "xdt_shortcode_media")) {
            assertEquals(LinkMetadata("Real caption", "Real caption", "https://example.org/cover.jpg"),
                parse("""<title>Instagram</title><script>window.data={"$key":{"shortcode":"abc","display_url":"https://example.org/cover.jpg",
                    "edge_media_to_caption":{"edges":[{"node":{"text":"Real caption"}}]}}};</script>""", "https://www.instagram.com/reel/abc/"))
        }
        assertEquals(LinkMetadata("Real caption", "Real caption", "https://example.org/cover.jpg"),
            parse("""<title>Instagram</title><div class="Caption">Real caption</div><img class="EmbeddedMediaImage" src="https://example.org/cover.jpg">""",
                "https://www.instagram.com/p/abc/embed/captioned/"))
    }

    @Test fun actualFetchFollowsRedirectsAndRepositoryHandlesMissingPages(): Unit = runBlocking {
        val server = ServerSocket(0)
        val worker = thread(name = "link-preview-test") {
            server.use {
                repeat(3) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val path = reader.readLine().split(' ')[1]
                        while (!reader.readLine().isNullOrEmpty()) { /* headers */ }
                        val body = "<title>Redirected article</title><meta name=description content='Actual description'>"
                        val response = when (path) {
                            "/redirect" -> "HTTP/1.1 302 Found\r\nLocation: /article\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            "/article" -> "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
                            else -> "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        }
                        socket.getOutputStream().write(response.toByteArray())
                    }
                }
            }
        }
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val repository = CobaltRepository(context)
            val base = "http://127.0.0.1:${server.localPort}"
            assertEquals(LinkMetadata("Redirected article", "Actual description", null), repository.fetchLinkMetadata("$base/redirect"))
            assertEquals(LinkMetadata(), repository.fetchLinkMetadata("$base/missing"))
        } finally { server.close(); worker.join(2000) }
    }

    @Test fun livePublicLinks() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit opt-in public network probe", arguments.getString("links_live") == "yes")
        val selected = arguments.getString("links_types")?.split(',')?.toSet()
        val samples = linkedMapOf(
            "website" to "https://www.wikipedia.org/",
            "article" to "https://en.wikipedia.org/wiki/Android_(operating_system)",
            "youtube_video" to "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "youtube_short_url" to "https://youtu.be/dQw4w9WgXcQ?si=preview-test",
            "youtube_shorts_format" to "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "youtube_real_short" to "https://www.youtube.com/shorts/k5Z7sFwFF7Q",
            "youtube_embed" to "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "instagram_post" to "https://www.instagram.com/p/fA9uwTtkSN/",
            "instagram_reel" to "https://www.instagram.com/reel/DdRyQxKteC1/",
            "instagram_embed" to "https://www.instagram.com/reel/DdRyQxKteC1/embed/captioned/",
            "tiktok_video" to "https://www.tiktok.com/@scout2015/video/6718335390845095173",
            "tiktok_recent_video" to "https://www.tiktok.com/@nasa/video/7689547576265149710",
            "x_post" to "https://x.com/Interior/status/463440424141459456",
            "twitter_post" to "https://twitter.com/Interior/status/463440424141459456",
            "facebook_video" to "https://www.facebook.com/Facebook/videos/10153231379946729/",
            "facebook_reel" to "https://www.facebook.com/reel/1380134307388381",
            "reddit_post" to "https://www.reddit.com/r/aww/comments/92dd8/",
            "linkedin" to "https://www.linkedin.com/company/google/",
            "pinterest" to "https://www.pinterest.com/pin/99360735500167749/",
            "vimeo_video" to "https://vimeo.com/76979871"
        ).filterKeys { selected == null || it in selected }
        assertTrue("Select at least one public link", samples.isNotEmpty())
        val rows = JSONArray()
        val report = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "link-metadata-live.json")
        for ((name, url) in samples) {
            val started = System.currentTimeMillis()
            val row = JSONObject().put("type", name).put("url", url)
            try {
                val result = LinkMetadataExtractor.fetch(url)
                row.put("status", if (result == LinkMetadata()) "no_public_metadata" else "metadata")
                    .put("title", result.title).put("description", result.description?.take(500)).put("image", result.imageUrl)
                assertFalse("Generic landing page counted as $name metadata", result.title in listOf("Instagram", "TikTok - Make Your Day", "Facebook", "Reddit", "X / X"))
                result.imageUrl?.let { image ->
                    val available = runCatching { Jsoup.connect(image).timeout(8000).maxBodySize(1024).ignoreContentType(true).execute()
                        .contentType()?.startsWith("image/") == true }.getOrDefault(false)
                    row.put("thumbnailAvailable", available)
                }
            } catch (error: Exception) {
                row.put("status", "fetch_failed").put("error", "${error.javaClass.simpleName}: ${error.message}")
            }
            row.put("elapsedMs", System.currentTimeMillis() - started)
            rows.put(row)
            report.writeText(rows.toString(2))
            android.util.Log.i("LinkMetadataProbe", "$name: ${row.getString("status")}")
        }
        assertEquals(samples.size, rows.length())
    }
}

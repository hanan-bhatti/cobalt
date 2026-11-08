package com.example.links

import com.example.data.repository.LinkMetadata
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.Locale

/** Public HTML only: uses existing Jsoup and Android JSON, without APIs or a browser. */
internal object LinkMetadataExtractor {
    private const val DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val PREVIEW = "facebookexternalhit/1.1"
    private val whitespace = Regex("[\\s\\p{Z}]+")

    fun fetch(input: String): LinkMetadata {
        val url = pageUrl(input)
        val first = try { load(url, DESKTOP) } catch (error: HttpStatusException) {
            if (!isSocial(url) || error.statusCode !in setOf(400, 403)) throw error
            null
        }
        val resolved = first?.location() ?: url
        val metadata = first?.let(::parse) ?: LinkMetadata()
        if (metadata != LinkMetadata() || !isSocial(resolved)) return metadata
        // Social sites can serve public link-preview tags separately from their app shell.
        // At most one fallback; HTTP 429/rate limits are never retried here.
        return runCatching { parse(load(resolved, PREVIEW)) }.getOrDefault(metadata)
    }

    private fun load(url: String, agent: String): Document {
        // Pinterest streams its head tags after a large route manifest (~1.5 MB).
        val pinterest = host(url)?.let { it == "pinterest.com" || it.endsWith(".pinterest.com") } == true
        return Jsoup.connect(url).timeout(8000).maxBodySize((if (pinterest) 2 else 1) * 1024 * 1024)
            .userAgent(agent).header("Accept-Language", "en-US,en;q=0.9").get()
    }

    /** Normalize only the metadata fetch, never the user's stored URL or clipboard. */
    internal fun pageUrl(input: String): String {
        val value = input.trim().let { if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "https://$it" }
        val uri = URI(value)
        require(uri.scheme.lowercase(Locale.ROOT) in setOf("http", "https"))
        val parts = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
        val id = when (host(value)) {
            "youtu.be" -> parts.firstOrNull()
            "youtube.com", "m.youtube.com", "youtube-nocookie.com" ->
                if (parts.firstOrNull() in setOf("shorts", "embed", "live", "v")) parts.getOrNull(1)
                else uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter('=')
            else -> null
        }
        return if (id != null && Regex("[A-Za-z0-9_-]{11}").matches(id)) "https://www.youtube.com/watch?v=$id" else value
    }

    internal fun parse(document: Document): LinkMetadata {
        if (document.select("#challenge-running, #challenge-form, script[src*=/cdn-cgi/challenge-platform/]").isNotEmpty() ||
            document.title().trim().lowercase(Locale.ROOT) in setOf("just a moment...", "attention required! | cloudflare")) return LinkMetadata()
        val social = isSocial(document.location())
        // Jsoup already decoded HTML attributes; a second decode corrupts URL keys such as &region.
        fun text(value: String?): String? = value?.let { whitespace.replace(it, " ").trim() }
            ?.takeIf { it.isNotEmpty() && it != "null" }
        fun meaningful(value: String?): String? = text(value)?.takeUnless { social && isShellText(it) }
        fun image(value: String?): String? = value?.trim()?.replace("&amp;", "&")?.takeIf(String::isNotEmpty)
            ?.let { runCatching { URI(document.baseUri()).resolve(it).toString() }.getOrNull() }
            ?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
        fun meta(vararg names: String, clean: (String?) -> String? = ::text): String? = names.firstNotNullOfOrNull { name ->
            document.select("meta").firstNotNullOfOrNull { tag ->
                if (listOf("property", "name", "itemprop").any { tag.attr(it).equals(name, true) }) clean(tag.attr("content")) else null
            }
        }
        val structured = structuredData(document)
        val video = videoData(document)
        val title = text(video.title) ?: meta("og:title", "twitter:title", clean = ::meaningful) ?: meaningful(structured.title)
            ?: meta("headline", "name", clean = ::meaningful) ?: meaningful(document.title())
        val description = text(video.description) ?: meta("og:description", "twitter:description", "description", clean = ::meaningful)
            ?: meaningful(structured.description)
        val thumbnail = image(video.imageUrl) ?: meta("og:image:secure_url", "og:image", "twitter:image", "twitter:image:src", "thumbnailUrl", "image", clean = ::image)
            ?: image(structured.imageUrl)
        return LinkMetadata(title, description, thumbnail.takeUnless { social && title == null && description == null })
    }

    private fun structuredData(document: Document): LinkMetadata {
        var result = LinkMetadata()
        var priority = -1
        fun read(value: Any?, depth: Int) {
            if (depth > 4) return
            when (value) {
                is JSONArray -> for (i in 0 until minOf(value.length(), 32)) read(value.opt(i), depth + 1)
                is JSONObject -> {
                    val type = value.opt("@type")?.toString().orEmpty()
                    if (listOf("VideoObject", "Article", "SocialMediaPosting", "WebPage", "Product", "ImageObject").any(type::contains)) {
                        val title = value.optString("headline").ifBlank { value.optString("name") }.takeIf(String::isNotBlank)
                        val description = value.optString("description").ifBlank { value.optString("articleBody") }.takeIf(String::isNotBlank)
                        val image = imageValue(value.opt("thumbnailUrl")) ?: imageValue(value.opt("image"))
                            ?: if (type.contains("ImageObject")) imageValue(value) else null
                        val nextPriority = if (type.contains("ImageObject")) 0 else if (type.contains("WebPage")) 1 else 2
                        result = if (nextPriority > priority) LinkMetadata(title ?: result.title, description ?: result.description, image ?: result.imageUrl)
                            else LinkMetadata(result.title ?: title, result.description ?: description, result.imageUrl ?: image)
                        priority = maxOf(priority, nextPriority)
                    }
                    for (key in listOf("@graph", "mainEntity", "video")) read(value.opt(key), depth + 1)
                }
            }
        }
        for (script in document.select("script[type=application/ld+json]")) {
            runCatching { JSONTokener(script.data()).nextValue() }.getOrNull()?.let { read(it, 0) }
        }
        return result
    }

    private fun imageValue(value: Any?, depth: Int = 0): String? = if (depth > 4) null else when (value) {
        is String -> value.takeIf(String::isNotBlank)
        is JSONArray -> (0 until minOf(value.length(), 8)).firstNotNullOfOrNull { imageValue(value.opt(it), depth + 1) }
        is JSONObject -> imageValue(value.opt("url"), depth + 1) ?: imageValue(value.opt("contentUrl"), depth + 1)
        else -> null
    }

    private fun videoData(document: Document): LinkMetadata {
        val host = host(document.location())
        if (host in setOf("youtube.com", "m.youtube.com", "youtu.be")) {
            for (script in document.select("script")) {
                val details = objectAfter(script.data(), "\"videoDetails\"") ?: continue
                if (pageUrl(document.location()).substringAfter("v=", "") != details.optString("videoId")) continue
                val thumbnails = details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                return LinkMetadata(details.optString("title").takeIf(String::isNotBlank), details.optString("shortDescription").takeIf(String::isNotBlank),
                    thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url"))
            }
        }
        if (host == "tiktok.com" || host?.endsWith(".tiktok.com") == true) {
            val id = Regex("/(?:video|photo)/(\\d+)").find(document.location())?.groupValues?.get(1)
            for (script in document.select("script#__UNIVERSAL_DATA_FOR_REHYDRATION__, script#SIGI_STATE")) {
                val root = runCatching { JSONObject(script.data()) }.getOrNull() ?: continue
                val item = root.optJSONObject("__DEFAULT_SCOPE__")?.optJSONObject("webapp.video-detail")?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
                    ?: root.optJSONObject("ItemModule")?.optJSONObject(id.orEmpty()) ?: continue
                if (id == null || item.optString("id") != id) continue
                val caption = item.optString("desc").takeIf(String::isNotBlank)
                return LinkMetadata(caption, caption, item.optJSONObject("video")?.let { imageValue(it.opt("cover")) ?: imageValue(it.opt("originCover")) })
            }
        }
        if (host == "instagram.com") {
            val shortcode = Regex("/(?:p|reel|reels|tv)/([^/]+)").find(document.location())?.groupValues?.get(1) ?: return LinkMetadata()
            for (script in document.select("script")) {
                val media = objectAfter(script.data(), "\"shortcode_media\"") ?: objectAfter(script.data(), "\"xdt_shortcode_media\"") ?: continue
                if (media.optString("shortcode") != shortcode) continue
                val caption = media.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")?.optJSONObject(0)?.optJSONObject("node")?.optString("text")
                return LinkMetadata(caption, caption, imageValue(media.opt("display_url")) ?: imageValue(media.opt("thumbnail_src")))
            }
            val caption = document.selectFirst(".Caption")?.text()?.takeIf(String::isNotBlank)
            return LinkMetadata(caption, caption, document.selectFirst("img.EmbeddedMediaImage")?.absUrl("src")?.takeIf(String::isNotBlank))
        }
        return LinkMetadata()
    }

    /** Read one balanced JSON object, without evaluating scripts or matching quoted braces. */
    private fun objectAfter(source: String, key: String): JSONObject? {
        val keyAt = source.indexOf(key).takeIf { it >= 0 } ?: return null
        var start = keyAt + key.length
        while (start < source.length && source[start].isWhitespace()) start++
        if (source.getOrNull(start++) != ':') return null
        while (start < source.length && source[start].isWhitespace()) start++
        if (source.getOrNull(start) != '{') return null
        var depth = 0; var quoted = false; var escaped = false
        for (end in start until source.length) {
            val char = source[end]
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return runCatching { JSONObject(source.substring(start, end + 1)) }.getOrNull()
            }
        }
        return null
    }

    private fun host(url: String): String? = runCatching { URI(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.") }.getOrNull()
    private fun isSocial(url: String): Boolean = host(url)?.let { value ->
        listOf("instagram.com", "tiktok.com", "facebook.com", "youtube.com", "youtu.be", "x.com", "twitter.com", "reddit.com", "pinterest.com", "linkedin.com", "threads.net", "threads.com", "vimeo.com").any { value == it || value.endsWith(".$it") }
    } == true
    private fun isShellText(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        return lower in setOf("instagram", "tiktok", "tiktok - make your day", "facebook", "youtube", "reddit", "pinterest", "linkedin", "x", "x / x", "twitter", "threads", "vimeo") ||
            listOf("log in", "login •", "sign up", "sign in", "create an account", "welcome back to instagram", "facebook - log in", "facebook – log in",
                "facebook helps you connect", "tiktok - trends start here", "tiktok is the destination for short-form", "security check", "verify you are human",
                "you’ve been blocked", "you've been blocked", "page isn't available", "page not found", "video unavailable", "video is unavailable").any(lower::startsWith)
    }
}

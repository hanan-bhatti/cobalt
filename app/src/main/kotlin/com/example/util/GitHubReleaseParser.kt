package com.example.util

import org.json.JSONArray
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun validPublishedAt(value: String): Boolean {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z").matches(value)) return false
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    return parser.parse(value, position) != null && position.index == value.length
}

/** Reject draft releases, prereleases and unrelated/unuploaded assets at the boundary. */
internal fun parsePublishedReleases(payload: String): List<PublishedRelease> {
    require(payload.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024) { "Release response is too large" }
    val array = JSONArray(payload)
    val releases = mutableListOf<PublishedRelease>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        if (item.opt("draft") != false || item.opt("prerelease") != false) continue
        val tag = item.optString("tag_name")
        val version = tag.removePrefix("v")
        if (tag != "v$version" || stableVersionParts(version) == null) continue
        val id = item.optLong("id", 0)
        val publishedAt = item.optString("published_at")
        val page = item.optString("html_url")
        if (id <= 0 || !validPublishedAt(publishedAt) ||
            !officialGitHubUrl(page, "/hanan-bhatti/cobalt/releases/tag/$tag")) continue
        val assets = item.optJSONArray("assets") ?: JSONArray()
        val apks = expectedApkNames(version).mapNotNull { (name, label) ->
            val matches = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .filter { it.optString("name") == name }
            val asset = matches.singleOrNull() ?: return@mapNotNull null
            val assetId = asset.optLong("id", 0)
            val size = asset.optLong("size", 0)
            val url = asset.optString("browser_download_url")
            if (assetId <= 0 || size <= 0 || asset.optString("state") != "uploaded" ||
                !officialGitHubUrl(url, "/hanan-bhatti/cobalt/releases/download/$tag/$name")) return@mapNotNull null
            ReleaseApk(assetId, name, label, url, size)
        }
        releases.add(PublishedRelease(id, tag, version, item.optString("name").ifBlank { "Cobalt v$version" },
            publishedAt, item.opt("body") as? String ?: "", page, apks))
    }
    // Ambiguous identities are unusable; never pick an arbitrary duplicate.
    return releases.groupBy { it.version }.values.filter { it.size == 1 }.map { it.single() }
        .sortedWith { a, b -> compareStableVersions(b.version, a.version) }
}

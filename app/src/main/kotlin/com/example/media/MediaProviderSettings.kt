package com.example.media

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class MediaProviderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("cobalt_media_providers", Context.MODE_PRIVATE)
    fun enabled(provider: String) = prefs.getBoolean("enabled_$provider", true)
    fun setEnabled(provider: String, enabled: Boolean) { prefs.edit().putBoolean("enabled_$provider", enabled).apply() }
    var priority: String
        get() = prefs.getString("priority", "trakt") ?: "trakt"
        set(value) { require(value in listOf("trakt", "tmdb")); prefs.edit().putString("priority", value).apply() }
    var jikanUrl: String
        get() = prefs.getString("jikan_url", DEFAULT_JIKAN) ?: DEFAULT_JIKAN
        set(value) { prefs.edit().putString("jikan_url", validateJikanUrl(value)).apply() }
    var mangaEnabled: Boolean
        get() = prefs.getBoolean("manga_enabled", true)
        set(value) { prefs.edit().putBoolean("manga_enabled", value).apply() }
    companion object {
        const val DEFAULT_JIKAN = "https://api.jikan.moe/v4/"
        fun validateJikanUrl(value: String): String {
            val url = requireNotNull(value.trim().toHttpUrlOrNull()) { "Enter a complete Jikan v4 URL." }
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "Use a server URL without credentials, a query or a fragment."
            }
            val h = url.host
            val octets = h.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 && it.all { n -> n in 0..255 } }
            val privateHost = h == "localhost" || h == "::1" || h.endsWith(".local") || octets?.let {
                it[0] == 127 || it[0] == 10 || (it[0] == 192 && it[1] == 168) || (it[0] == 172 && it[1] in 16..31)
            } == true
            require(url.isHttps || privateHost) { "Use HTTPS for public servers. HTTP is supported only for local/private servers." }
            return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build().toString()
        }
    }
}

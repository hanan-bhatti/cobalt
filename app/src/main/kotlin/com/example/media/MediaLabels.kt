package com.example.media

internal fun mediaTypeLabel(mediaType: String?): String = when (mediaType?.lowercase(java.util.Locale.ROOT)) {
    "movie" -> "Movie"
    "tv", "tv show", "tv_show" -> "TV Show"
    "anime" -> "Anime"
    "manga" -> "Manga"
    else -> "Media"
}

/** Keep the existing persisted statuses compatible while using reading language for manga. */
internal fun mediaStatusLabel(status: String?, mediaType: String?): String {
    val value = status ?: "Plan to Watch"
    return if (mediaType.equals("manga", true)) when (value) {
        "Plan to Watch" -> "Plan to Read"
        "Watching" -> "Reading"
        else -> value
    } else value
}

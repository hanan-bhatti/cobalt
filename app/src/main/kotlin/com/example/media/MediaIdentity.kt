package com.example.media

import com.example.data.remote.MediaSearchResultItem
import java.text.Normalizer
import java.util.Locale

internal object MediaIdentity {
    private fun title(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun same(a: MediaSearchResultItem, b: MediaSearchResultItem): Boolean {
        if (a.mediaType != b.mediaType) return false
        if (a.id == b.id) return true
        val jikanPrefix = "jikan_${a.mediaType}_"
        if (a.id.startsWith(jikanPrefix) && b.id.startsWith(jikanPrefix))
            return a.id.substringAfterLast('_') == b.id.substringAfterLast('_')
        if (a.traktId != null && b.traktId != null) return a.traktId == b.traktId
        if (a.tmdbId != null && b.tmdbId != null) return a.tmdbId == b.tmdbId
        if (a.imdbId != null && b.imdbId != null) return a.imdbId == b.imdbId
        return !a.releaseYear.isNullOrBlank() && a.releaseYear == b.releaseYear && title(a.title) == title(b.title)
    }
    fun merge(results: List<MediaSearchResultItem>, preferred: String): List<MediaSearchResultItem> {
        val merged = ArrayList<MediaSearchResultItem>()
        for (row in results.sortedBy { if (it.id.startsWith(preferred)) 0 else 1 }) {
            val index = merged.indexOfFirst { same(it, row) }
            if (index < 0) merged += row else {
                val old = merged[index]
                merged[index] = old.copy(traktId = old.traktId ?: row.traktId, tmdbId = old.tmdbId ?: row.tmdbId,
                    imdbId = old.imdbId ?: row.imdbId, posterUrl = old.posterUrl ?: row.posterUrl,
                    backdropUrl = old.backdropUrl ?: row.backdropUrl, overview = old.overview ?: row.overview,
                    trailerUrl = old.trailerUrl ?: row.trailerUrl)
            }
        }
        return merged
    }
}

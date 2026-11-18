package com.example.widget

import com.example.media.recommendation.ScreeningRecommendation
import com.example.media.recommendation.ScreeningRecommendationEngine
import com.example.data.model.SavedItem
import com.example.data.local.TraktPlaybackEntity
import com.example.data.remote.trakt.TraktPlaybackRepository
import com.example.media.recommendation.ScreeningScoreTrace

enum class ScreeningSource(val label: String, val description: String) {
    ALL("Everything", "Movies, TV shows, anime and manga"),
    MOVIES("Movies", "Saved films"),
    SHOWS("TV shows", "Live-action series"),
    ANIME("Anime", "Anime series and films"),
    MANGA("Manga", "Your saved reading list");

    fun accepts(item: SavedItem): Boolean {
        val type = item.mediaType?.trim()?.lowercase()
        return when (this) {
            ALL -> true
            MOVIES -> type != "anime" && ScreeningRecommendationEngine.kind(item) == "movie"
            SHOWS -> type != "anime" && ScreeningRecommendationEngine.kind(item) == "tv"
            ANIME -> type == "anime"
            MANGA -> type == "manga"
        }
    }
}

data class ScreeningWidgetConfig(val source: ScreeningSource = ScreeningSource.ALL)
enum class ScreeningThumb { LIKED, DISLIKED }

/** Only display metadata and an item identity; no bodies, synopsis or episode history. */
data class ScreeningPick(
    val itemId: String,
    val key: String,
    val title: String,
    val metadata: String,
    val reason: String,
    val artwork: String?,
    val kind: String,
    val playbackProgress: Double? = null,
    val playbackSession: String? = null,
    val trace: ScreeningScoreTrace? = null
) {
    companion object {
        fun from(pick: ScreeningRecommendation): ScreeningPick {
            val item = pick.item
            val kind = when (item.mediaType?.trim()?.lowercase()) {
                "anime" -> "anime"
                "manga" -> "manga"
                else -> ScreeningRecommendationEngine.kind(item) ?: "movie"
            }
            val genre = when (kind) {
                "anime" -> "Anime"
                "manga" -> "Manga"
                else -> if (kind == "tv") "TV show" else "Movie"
            }
            val duration = item.runtime?.takeIf { it in 1..1440 }?.let {
                if (it >= 60) "${it / 60} h${if (it % 60 > 0) " ${it % 60} min" else ""}" else "$it min"
            }
            return ScreeningPick(item.id, pick.key, item.title.ifBlank { "Untitled ${if (kind == "tv") "show" else "movie"}" }.take(180),
                listOfNotNull(genre, duration).joinToString(" · ").take(180), pick.reason.take(240),
                (item.backdropUrl?.takeIf { it.isNotBlank() } ?: item.thumbnailPath?.takeIf { it.isNotBlank() })?.take(2048), kind,
                trace = ScreeningScoreTrace.from(pick))
        }
    }
}

data class ScreeningWidgetEntry(
    val config: ScreeningWidgetConfig = ScreeningWidgetConfig(),
    val pick: ScreeningPick? = null,
    val poolSize: Int = 0,
    val seen: List<String> = emptyList(),
    val day: String = "",
    val generation: Long = 0,
    val thumb: ScreeningThumb? = null
) {
    val canAdvance: Boolean get() = pick != null && poolSize > 1
}

/** Periodic refresh keeps today's pick; Next cycles through eligible titles without a dislike. */
object ScreeningWidgetSelection {
    internal fun withPlayback(entry: ScreeningWidgetEntry, item: SavedItem?, session: String?, positions: List<TraktPlaybackEntity>): ScreeningWidgetEntry {
        val playback = if (item != null && session != null) TraktPlaybackRepository.find(item, positions.filter { it.session == session }) else null
        return entry.copy(pick = entry.pick?.copy(playbackProgress = playback?.progress, playbackSession = playback?.session))
    }

    fun update(entry: ScreeningWidgetEntry, ranked: List<ScreeningRecommendation>, id: Int, day: String, advance: Boolean = false): ScreeningWidgetEntry {
        val pool = ranked.filter { entry.config.source.accepts(it.item) }
        val current = entry.pick?.let { old -> pool.find {
            it.key == old.key || it.item.id == old.itemId || old.key in ScreeningRecommendationEngine.aliases(it.item)
        } }
        val validKeys = pool.map { it.key }.toSet()
        var seen = if (entry.day == day) entry.seen.filter { it in validKeys } else emptyList()
        val keep = current != null && ((!advance && entry.day == day) || (advance && pool.size == 1))
        val selected = if (keep) current else {
            val exclude = seen.toSet() + listOfNotNull(current?.key)
            ScreeningRecommendationEngine.choose(pool, "widget:$id:$day:${entry.generation + 1}", exclude)
                ?: run {
                    seen = emptyList()
                    ScreeningRecommendationEngine.choose(pool, "widget:$id:$day:${entry.generation + 1}",
                        if (pool.size > 1) setOfNotNull(current?.key) else emptySet())
                }
        }
        val pick = selected?.let(ScreeningPick::from)?.let { fresh ->
            entry.pick?.takeIf { keep }?.let { old -> fresh.copy(trace = old.trace, reason = old.reason) } ?: fresh
        }
        // Enriching aliases or replacing a duplicate copy keeps the same published suggestion.
        val changed = (!keep && (pick?.key != entry.pick?.key || pick?.itemId != entry.pick?.itemId)) || entry.day != day
        return entry.copy(pick = pick, poolSize = pool.size, seen = (seen + listOfNotNull(pick?.key)).distinct().takeLast(200),
            day = day, generation = if (changed) entry.generation + 1 else entry.generation,
            thumb = if (pick?.itemId == entry.pick?.itemId) entry.thumb else null)
    }
}

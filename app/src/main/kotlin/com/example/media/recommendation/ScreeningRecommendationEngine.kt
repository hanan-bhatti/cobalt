package com.example.media.recommendation

import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

const val SCREENING_DAY = 86_400_000L

enum class ScreeningFeedback(val label: String) {
    USEFUL("Useful"), NOT_INTERESTED("Not interested"), NOT_NOW("Not now"),
    STILL_WATCHING("Still watching"), PAUSED("Paused for now"), WATCHED("Finished")
}

data class ProgressSample(val at: Long, val watched: Int)
data class ScreeningItemSignal(
    val watchingSince: Long? = null,
    val lastProgressAt: Long? = null,
    val watched: Int = 0,
    val samples: List<ProgressSample> = emptyList(),
    val lastShownAt: Long? = null,
    val shownCount: Int = 0,
    val suppressedUntil: Long = 0,
    val notInterested: Boolean = false,
    val lastFeedbackAt: Long? = null
)
data class ScreeningFeedbackEvent(val key: String, val kind: ScreeningFeedback, val at: Long, val genres: List<String>,
    val id: String = "feedback:$key:${kind.name}:$at", val origin: String? = null, val title: String? = null, val itemId: String? = null)
data class ScreeningSignals(
    val items: Map<String, ScreeningItemSignal> = emptyMap(),
    val feedback: List<ScreeningFeedbackEvent> = emptyList(),
    val reactions: Map<String, ScreeningReaction> = emptyMap(),
    val ignoredEvidence: Set<String> = emptySet(),
    val ratings: Map<String, ScreeningRatingObservation> = emptyMap(),
    val aliases: Map<String, String> = emptyMap()
)
data class ScreeningProgress(
    val watched: Int,
    val aired: Int?,
    val remaining: Int?,
    val lastWatchedAt: Long?,
    val paceDaysPerEpisode: Double?,
    val caughtUp: Boolean,
    val inconsistent: Boolean,
    val unreleased: Boolean = false
)
data class ScreeningRecommendation(
    val item: SavedItem,
    val key: String,
    val score: Double,
    val reason: String,
    val bucket: String,
    val progress: ScreeningProgress,
    val question: String,
    val features: Map<String, Double>,
    val diversityAdjustment: Double = 0.0,
    val generatedAt: Long = 0,
    val shortlist: List<ScreeningSelectionWeight> = emptyList(),
    val eligiblePoolSize: Int = 0
) {
    val selectionScore: Double get() = score + diversityAdjustment
}

/** Small, explainable local ranker. Opening, elapsed time and silence never mark anything watched. */
object ScreeningRecommendationEngine {
    fun kind(item: SavedItem): String? = when (item.mediaType?.trim()?.lowercase()) {
        "movie", "film" -> "movie"
        "tv", "tv show", "tv_show", "show", "series", "anime" -> "tv"
        else -> if (item.mediaType.isNullOrBlank() && item.showProgress != null) "tv" else null
    }

    fun aliases(item: SavedItem): List<String> {
        val kind = kind(item) ?: "media"
        val legacyTmdb = if (item.id.startsWith("tmdb_${kind}_")) item.id.substringAfterLast('_').toIntOrNull() else null
        val legacyTrakt = if (item.id.startsWith("trakt_${kind}_")) item.id.substringAfterLast('_').toLongOrNull() else null
        return listOfNotNull(
            (item.tmdbId ?: legacyTmdb)?.takeIf { it > 0 }?.let { "$kind:tmdb:$it" },
            (item.traktId ?: legacyTrakt)?.takeIf { it > 0 }?.let { "$kind:trakt:$it" },
            item.imdbId?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { "$kind:imdb:$it" },
            "$kind:local:${item.id}")
    }
    fun key(item: SavedItem): String = aliases(item).first()

    fun mergeSignals(values: List<ScreeningItemSignal>): ScreeningItemSignal {
        if (values.isEmpty()) return ScreeningItemSignal()
        val latest = values.maxBy { maxOf(it.lastProgressAt ?: 0, it.lastFeedbackAt ?: 0, it.lastShownAt ?: 0) }
        return latest.copy(watchingSince = values.mapNotNull { it.watchingSince }.minOrNull(),
            lastProgressAt = values.mapNotNull { it.lastProgressAt }.maxOrNull(),
            lastShownAt = values.mapNotNull { it.lastShownAt }.maxOrNull(),
            shownCount = values.maxOf { it.shownCount }, suppressedUntil = values.maxOf { it.suppressedUntil },
            notInterested = values.any { it.notInterested })
    }

    fun watchStatus(item: SavedItem): String = (item.watchStatusOverride ?: item.watchStatus).orEmpty().trim().lowercase()

    fun progress(item: SavedItem, signal: ScreeningItemSignal = ScreeningItemSignal(), now: Long,
                 zone: ZoneId = ZoneId.systemDefault()): ScreeningProgress {
        val p = item.showProgress
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val episodes = p?.seasons.orEmpty().filter { it.seasonNumber > 0 }.flatMap { it.episodes }
            .filter { it.seasonNumber > 0 && it.episodeNumber > 0 }.groupBy { it.seasonNumber to it.episodeNumber }
            .values.map { copies -> copies.maxWith(compareBy<com.example.data.model.TvEpisodeProgress> { it.isWatched }.thenBy { it.lastWatchedAt ?: 0L }) }
        val watched = max(max(p?.totalWatched?.coerceAtLeast(0) ?: 0, episodes.count { it.isWatched }),
            p?.seasons.orEmpty().filter { it.seasonNumber > 0 }.groupBy { it.seasonNumber }.values.sumOf { copies -> copies.maxOf { it.watchedEpisodes.coerceAtLeast(0) } })
        val ended = item.status.orEmpty().lowercase() in setOf("ended", "canceled", "cancelled", "finished") ||
            item.mediaFacts?.publicationStatus.orEmpty().lowercase() == "finished"
        var aired = p?.totalAired?.takeIf { it > 0 }
            ?: item.mediaFacts?.episodeCount?.takeIf { it > 0 && ended }
        // An air date is evidence about release, not evidence about viewing. Season 0 is optional.
        val dated = episodes.mapNotNull { ep -> ep.airDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } }
        if (dated.size == episodes.size && episodes.isNotEmpty()) {
            val released = dated.count { !it.isAfter(today) }
            if (aired != null && aired <= episodes.size) aired = released
        }
        val premiere = p?.premiered?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val unreleased = (premiere?.isAfter(today) == true && watched == 0) ||
            (episodes.isNotEmpty() && dated.size == episodes.size && dated.all { it.isAfter(today) } && watched == 0 && (p?.totalAired ?: 0) <= episodes.size)
        val inconsistent = aired != null && watched > aired
        if (inconsistent) aired = null
        val knownTimes = episodes.filter { it.isWatched }.mapNotNull { ep ->
            ep.lastWatchedAt?.takeIf { it in 1..now }?.let { ProgressSample(it, 1) }
        }.groupingBy { it.at }.eachCount().toSortedMap()
        var cumulative = 0
        val datedSamples = knownTimes.map { (at, n) -> cumulative += n; ProgressSample(at, cumulative) }
        val samples = if (datedSamples.size >= 3) datedSamples else signal.samples.filter { it.at in 1..now }.sortedBy { it.at }
        val intervals = samples.zipWithNext().mapNotNull { (a, b) ->
            if (b.watched > a.watched && b.at > a.at) (b.at - a.at).toDouble() / SCREENING_DAY / (b.watched - a.watched) else null
        }.filter { it > 0 && it <= 60 }
        // At least two real intervals; an imported count or a single timestamp is not a pace model.
        val pace = if (intervals.size >= 2) intervals.sorted().let { it[it.size / 2].coerceIn(.2, 30.0) } else null
        val last = (episodes.filter { it.isWatched }.mapNotNull { it.lastWatchedAt } + listOfNotNull(item.lastWatchedAt, signal.lastProgressAt))
            .filter { it in 1..now }.maxOrNull()
        return ScreeningProgress(watched, aired, aired?.let { (it - watched).coerceAtLeast(0) }, last, pace,
            aired != null && aired > 0 && watched >= aired, inconsistent, unreleased)
    }

    fun rank(items: List<SavedItem>, signals: ScreeningSignals = ScreeningSignals(), now: Long = System.currentTimeMillis(),
             zone: ZoneId = ZoneId.systemDefault(), includeCooldowns: Boolean = true,
             profile: ScreeningTasteProfile = ScreeningTaste.profile(items, signals, now)): List<ScreeningRecommendation> {
        val candidates = ScreeningIdentity.resolve(items, signals.aliases).mapNotNull { title ->
            val item = title.item
            val k = title.key
            val signal = mergeSignals(title.aliases.mapNotNull { signals.items[it] })
            if (!eligibility(item, signal, now, zone, includeCooldowns).eligible) return@mapNotNull null
            val status = watchStatus(item)
            val progress = progress(item, signal, now, zone)
            val watching = status == "watching" || progress.watched > 0
            val ageDays = ((now - item.timestamp).coerceAtLeast(0).toDouble() / SCREENING_DAY).coerceAtMost(365.0)
            val idleDays = (now - (progress.lastWatchedAt ?: signal.watchingSince ?: now)).coerceAtLeast(0).toDouble() / SCREENING_DAY
            val features = linkedMapOf<String, Double>("base" to 20.0)
            var reason: String
            val bucket: String
            when {
                watching && progress.remaining != null && progress.remaining in 1..2 -> {
                    features["near_finish"] = 25.0; bucket = "finish"
                    reason = "Only ${progress.remaining} released episode${if (progress.remaining == 1) "" else "s"} left"
                }
                watching -> { features["continue"] = 20.0; bucket = "continue"
                    reason = when {
                        kind(item) == "movie" -> "Continue a movie you started"
                        progress.inconsistent -> "Continue watching · episode counts need updating"
                        progress.aired == null -> "Continue watching · episode total is unknown"
                        else -> "Continue from ${progress.watched} of ${progress.aired} released episodes"
                    } }
                ageDays >= 30 -> { features["rediscovery"] = 18.0; bucket = "rediscover"; reason = "Saved ${ageDays.toInt()} days ago and not marked watched" }
                else -> { features["watchlist"] = 12.0; bucket = "watchlist"; reason = "A pick from your saved watchlist" }
            }
            if (watching) features["recent_progress"] = 8.0 * exp(-idleDays / 14.0)
            features["preference"] = profile.affinity(item.genres) * 18.0
            val reaction = profile.evidence.firstOrNull { it.key == k && it.active && it.kind in setOf(ScreeningEvidenceKind.LIKE, ScreeningEvidenceKind.DISLIKE) }
            if (reaction != null) features["title_preference"] = (if (reaction.kind == ScreeningEvidenceKind.LIKE) 6.0 else -10.0) *
                (reaction.amount / 1.25).coerceIn(0.0, 1.0)
            features["age"] = ln(1 + ageDays) * 1.5
            features["fatigue"] = if (signal.shownCount == 0) 0.0 else -minOf(12.0, signal.shownCount * 1.5)
            if (kind(item) == "movie" && item.runtime?.let { it in 1..100 } == true && !watching) features["short_pick"] = 3.0
            if (!watching && features.getValue("preference") >= 1.0) {
                val leading = profile.genres.filter { it.key in item.genres && (it.weight ?: 0.0) > 0.0 }.maxByOrNull { it.weight ?: 0.0 }
                if (leading != null) reason = "Your feedback favors ${leading.label.lowercase()}"
            }
            val title = item.title.ifBlank { "this title" }
            val dueAfter = progress.paceDaysPerEpisode?.let { pace -> (pace * (progress.remaining ?: 1) + 2).coerceIn(5.0, 21.0) } ?: 7.0
            val question = when {
                watching && idleDays >= dueAfter -> "Still watching $title, or have you finished?"
                watching -> "How is $title going?"
                signal.lastShownAt != null -> "Did you get a chance to watch $title?"
                ageDays >= 30 -> "Still interested in watching $title?"
                else -> "Would $title be a useful pick for you?"
            }
            ScreeningRecommendation(item, k, features.values.sum(), reason, bucket, progress, question, features, generatedAt = now)
        }.sortedWith(compareByDescending<ScreeningRecommendation> { it.score }.thenBy { it.key }).take(200)
        return diversify(candidates)
    }

    fun eligibility(item: SavedItem, signal: ScreeningItemSignal, now: Long,
                    zone: ZoneId = ZoneId.systemDefault(), includeCooldowns: Boolean = false): ScreeningEligibility {
        if (item.type != SavedItemType.MEDIA || kind(item) == null) return ScreeningEligibility(false, "This format is not supported by Screening yet")
        if (item.isDeleted) return ScreeningEligibility(false, "Title removed")
        if (item.isArchived) return ScreeningEligibility(false, "In the archive")
        if (item.isUnavailable) return ScreeningEligibility(false, "Title unavailable")
        val status = watchStatus(item)
        if (status in setOf("completed", "watched")) return ScreeningEligibility(false, "Already marked finished")
        if (status in setOf("dropped", "on hold", "on_hold", "paused", "not interested")) return ScreeningEligibility(false, "Saved status: ${status.replace('_', ' ')}")
        if (signal.notInterested) return ScreeningEligibility(false, "Hidden by a Not interested response")
        if (signal.suppressedUntil > now) return ScreeningEligibility(false, "Temporarily skipped", signal.suppressedUntil)
        if (includeCooldowns && signal.lastShownAt?.let { it in 1..now && now - it < 2 * SCREENING_DAY } == true)
            return ScreeningEligibility(false, "Recently suggested", signal.lastShownAt!! + 2 * SCREENING_DAY)
        val p = progress(item, signal, now, zone)
        if (p.unreleased) return ScreeningEligibility(false, "No released episodes yet")
        if (kind(item) == "tv" && p.caughtUp) return ScreeningEligibility(false, "Caught up with released episodes")
        if (kind(item) == "movie" && status != "watching" && item.lastWatchedAt?.let { it in 1..now } == true)
            return ScreeningEligibility(false, "Already recorded as watched")
        return ScreeningEligibility(true, "Eligible for this source")
    }

    /** Recompute variety within the actual source/exclusion pool, then retain its adjusted utility. */
    fun diversify(candidates: List<ScreeningRecommendation>): List<ScreeningRecommendation> {
        // Greedy re-ranking trades a little score for different genres and different purposes.
        val remaining = candidates.sortedWith(compareByDescending<ScreeningRecommendation> { it.score }.thenBy { it.key }).toMutableList()
        val result = mutableListOf<ScreeningRecommendation>()
        while (remaining.isNotEmpty()) {
            fun penalty(candidate: ScreeningRecommendation) = result.takeLast(3).sumOf { prior ->
                    val a = ScreeningGenres.normalize(candidate.item.genres).toSet(); val b = ScreeningGenres.normalize(prior.item.genres).toSet()
                    val overlap = if ((a + b).isEmpty()) 0.0 else (a intersect b).size.toDouble() / (a + b).size
                    overlap * 5 + if (candidate.bucket == prior.bucket) 3.0 else 0.0
                }
            val next = remaining.maxBy { it.score - penalty(it) }
            val adjustment = penalty(next).let { if (it == 0.0) 0.0 else -it }
            result += next.copy(diversityAdjustment = adjustment); remaining.remove(next)
        }
        return result
    }

    /** Stable weighted shuffle: refreshes don't flicker; Next changes nonce without recording a dislike. */
    fun choose(ranked: List<ScreeningRecommendation>, seed: String, excludedKeys: Set<String> = emptySet()): ScreeningRecommendation? {
        val eligible = ranked.filter { it.key !in excludedKeys }
        val pool = diversify(eligible).take(8)
        if (pool.isEmpty()) return null
        val chosen = pool.maxBy { candidate ->
            val digest = MessageDigest.getInstance("SHA-256").digest("$seed:${candidate.key}".toByteArray(Charsets.UTF_8))
            var hash = 0L
            for (i in 0..5) hash = (hash shl 8) or (digest[i].toLong() and 255)
            val u = (hash + 1.0) / (281474976710656.0 + 1.0)
            // Exponential race gives score-weighted sampling without unseeded randomness.
            ln(u) / max(1.0, candidate.selectionScore)
        }
        return chosen.copy(shortlist = pool.map { ScreeningSelectionWeight(it.key, max(1.0, it.selectionScore)) }, eligiblePoolSize = eligible.size)
    }
}

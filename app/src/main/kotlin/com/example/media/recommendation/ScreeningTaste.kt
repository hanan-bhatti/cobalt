package com.example.media.recommendation

import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

const val SCREENING_MODEL_VERSION = "local-taste-v2"

enum class ScreeningPreference { LIKED, DISLIKED }

data class ScreeningReaction(
    val key: String,
    val value: ScreeningPreference?,
    val at: Long? = null,
    val origin: String = "Widget",
    val revision: Long = 0
)

data class ScreeningRatingObservation(val value: Int?, val at: Long? = null)

/** Presentation grouping never changes the genre vocabulary used by the ranker. */
object ScreeningGenres {
    private val whitespace = Regex("\\s+")
    fun key(value: String): String = when (val clean = value.trim().lowercase(Locale.ROOT)
        .replace(whitespace, " ")) {
        "sci-fi", "sci fi", "scifi", "science-fiction" -> "science fiction"
        "action & adventure", "action and adventure" -> "action & adventure"
        else -> clean
    }

    fun normalize(values: List<String>): List<String> = values.map(::key).filter(String::isNotBlank).distinct()
    fun label(key: String): String = key.replaceFirstChar { it.titlecase(Locale.ROOT) }

    fun overview(genres: List<ScreeningGenreTaste>, limit: Int = 5): ScreeningGenreOverview {
        val ordered = genres.sortedWith(compareByDescending<ScreeningGenreTaste> { it.weight != null }
            .thenByDescending { abs(it.weight ?: 0.0) }.thenByDescending { it.titleCount }.thenBy { it.key })
        return ScreeningGenreOverview(ordered.take(limit.coerceAtLeast(1)), ordered.drop(limit.coerceAtLeast(1)))
    }
}

data class ScreeningGenreOverview(val visible: List<ScreeningGenreTaste>, val others: List<ScreeningGenreTaste>)

data class ScreeningCanonicalTitle(
    val key: String,
    val aliases: List<String>,
    val item: SavedItem,
    val copies: List<SavedItem>,
    val ratingItem: SavedItem?,
    val genres: List<String>
)

/** Resolve identities before counting ratings, reactions, or completed titles. */
object ScreeningIdentity {
    fun resolve(items: List<SavedItem>, knownAliases: Map<String, String> = emptyMap()): List<ScreeningCanonicalTitle> {
        val media = items.filter { it.type == SavedItemType.MEDIA && !it.isDeleted }
        val parent = IntArray(media.size) { it }
        fun root(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        val seen = mutableMapOf<String, Int>()
        fun aliases(item: SavedItem): List<String> = ScreeningRecommendationEngine.aliases(item).let { values ->
            (values + values.mapNotNull(knownAliases::get)).distinct()
        }
        media.forEachIndexed { index, item -> aliases(item).forEach { alias ->
            seen[alias]?.let { parent[root(index)] = root(it) }
            seen[alias] = index
        } }
        return media.indices.groupBy(::root).values.map { indices ->
            val copies = indices.map(media::get)
            val item = copies.maxWith(compareBy<SavedItem> { it.watchStatusOverride != null }
                .thenBy { !it.isArchived && !it.isUnavailable }.thenBy { it.updatedAt }
                .thenBy { ScreeningRecommendationEngine.aliases(it).size }.thenBy { it.id })
            val aliases = copies.flatMap(::aliases).distinct().sortedWith(
                compareBy<String> { when { ":tmdb:" in it -> 0; ":trakt:" in it -> 1; ":imdb:" in it -> 2; else -> 3 } }.thenBy { it })
            val rating = copies.filter { it.userRating?.let { value -> value in 1..10 } == true }.maxWithOrNull(
                compareBy<SavedItem> { it.updatedAt }.thenBy { it.id })
            val genres = ScreeningGenres.normalize(item.genres.ifEmpty { copies.flatMap { it.genres } })
            ScreeningCanonicalTitle(aliases.first(), aliases, item.copy(genres = genres), copies, rating, genres)
        }.sortedBy { it.key }
    }
}

enum class ScreeningEvidenceKind { RATING, COMPLETION, LIKE, DISLIKE, RESPONSE }

data class ScreeningEvidence(
    val id: String,
    val key: String,
    val itemId: String?,
    val title: String,
    val genres: List<String>,
    val kind: ScreeningEvidenceKind,
    val value: String,
    val origin: String,
    val at: Long?,
    val reward: Double,
    val amount: Double,
    val ignored: Boolean = false,
    val active: Boolean = true,
    val feedback: ScreeningFeedback? = null
) {
    val contributes: Boolean get() = active && !ignored && amount > 0 && reward != 0.0
    val influence: Double get() = if (contributes) reward * amount else 0.0
}

data class ScreeningGenreTaste(
    val key: String,
    val weight: Double?,
    val evidenceAmount: Double,
    val titleCount: Int,
    val evidenceIds: List<String>
) {
    val label: String get() = ScreeningGenres.label(key)
    val direction: String get() = when {
        weight == null -> "No feedback yet"
        abs(weight) < .000001 -> "Balanced feedback"
        weight > 0 -> "More often"
        else -> "Less often"
    }
}

data class ScreeningTasteProfile(
    val generatedAt: Long,
    val genres: List<ScreeningGenreTaste>,
    val evidence: List<ScreeningEvidence>,
    val titleCount: Int,
    val modelVersion: String = SCREENING_MODEL_VERSION
) {
    private val genresByKey = genres.associateBy { it.key }
    fun affinity(genres: List<String>): Double {
        return ScreeningGenres.normalize(genres).map { genresByKey[it]?.weight ?: 0.0 }
            .average().takeIf { it.isFinite() } ?: 0.0
    }
}

object ScreeningTaste {
    private fun decay(at: Long?, now: Long): Double = if (at == null) 1.0
        else if (at !in 1..now) 0.0 else 2.0.pow(-(now - at).toDouble() / (60 * SCREENING_DAY))

    fun profile(items: List<SavedItem>, signals: ScreeningSignals, now: Long): ScreeningTasteProfile {
        val titles = ScreeningIdentity.resolve(items, signals.aliases)
        val byAlias = titles.flatMap { title -> title.aliases.map { it to title } }.toMap()
        val evidence = mutableListOf<ScreeningEvidence>()
        fun excluded(id: String, title: ScreeningCanonicalTitle): Boolean = id in signals.ignoredEvidence ||
            title.aliases.any { "rating:$it" in signals.ignoredEvidence }
        for (title in titles) {
            val rating = title.ratingItem?.userRating
            val id = "rating:${title.key}"
            if (rating != null) {
                val observed = title.aliases.mapNotNull { signals.ratings[it] }.firstOrNull { it.value == rating }
                evidence += ScreeningEvidence(id, title.key, title.ratingItem?.id, title.item.title, title.genres,
                    ScreeningEvidenceKind.RATING, "Your rating $rating/10", "Saved personal rating", observed?.at,
                    (rating - 5.5) / 4.5, 1.0, excluded(id, title))
            } else if (ScreeningRecommendationEngine.watchStatus(title.item) in setOf("completed", "watched")) {
                evidence += ScreeningEvidence("completion:${title.key}", title.key, title.item.id, title.item.title, title.genres,
                    ScreeningEvidenceKind.COMPLETION, "Marked finished", "Saved watch status",
                    title.item.lastWatchedAt?.takeIf { it in 1..now }, .15, .5)
            }
        }
        val reactions = signals.reactions.values.groupBy { byAlias[it.key]?.key ?: it.key }.mapValues { (_, values) ->
            values.maxWith(compareBy<ScreeningReaction> { it.at ?: 0L }.thenBy { it.revision }.thenBy { it.key })
        }
        for ((key, reaction) in reactions) {
            val title = byAlias[key] ?: continue
            val liked = reaction.value == ScreeningPreference.LIKED
            evidence += ScreeningEvidence("reaction:${title.key}", title.key, title.item.id, title.item.title, title.genres,
                if (liked) ScreeningEvidenceKind.LIKE else ScreeningEvidenceKind.DISLIKE,
                when (reaction.value) { ScreeningPreference.LIKED -> "Like"; ScreeningPreference.DISLIKED -> "Not for me"; null -> "Thumb cleared" },
                reaction.origin, reaction.at, if (liked) 1.0 else -1.0, 1.25 * decay(reaction.at, now),
                active = reaction.value != null)
        }
        // Only the latest retained opinion per canonical title trains. Repeated questions are not extra votes.
        val responses = signals.feedback.distinctBy { it.id }
        val opinions = responses.filter { it.kind in setOf(ScreeningFeedback.USEFUL, ScreeningFeedback.NOT_INTERESTED) &&
            it.at in 1..now && it.id !in signals.ignoredEvidence }.groupBy { byAlias[it.key]?.key ?: it.key }
            .mapValues { (_, values) -> values.maxWith(compareBy<ScreeningFeedbackEvent> { it.at }.thenBy { it.id }) }
        for (event in responses) {
            if (event.at !in 1..now) continue
            val title = byAlias[event.key]
            val key = title?.key ?: event.key
            val opinion = opinions[key]
            val reward = when (event.kind) { ScreeningFeedback.USEFUL -> 1.0; ScreeningFeedback.NOT_INTERESTED -> -1.0; else -> 0.0 }
            evidence += ScreeningEvidence(event.id, key, title?.item?.id ?: event.itemId,
                title?.item?.title ?: event.title ?: "Saved title no longer available",
                title?.genres ?: ScreeningGenres.normalize(event.genres), ScreeningEvidenceKind.RESPONSE,
                when (event.kind) { ScreeningFeedback.USEFUL -> "Good pick"; ScreeningFeedback.NOT_INTERESTED -> "Not interested";
                    ScreeningFeedback.NOT_NOW -> "Maybe later"; else -> event.kind.label }, event.origin ?: "Screening response · source not recorded",
                event.at, reward, .65 * decay(event.at, now), event.id in signals.ignoredEvidence,
                active = reward == 0.0 || opinion?.id == event.id, feedback = event.kind)
        }
        val vocabulary = (titles.flatMap { it.genres } + evidence.flatMap { it.genres }).distinct().sorted()
        val byGenre = evidence.filter { it.contributes }.flatMap { e -> e.genres.map { it to e } }.groupBy({ it.first }, { it.second })
        val genres = vocabulary.map { genre ->
            val entries = byGenre[genre].orEmpty()
            val amount = entries.sumOf { it.amount }
            ScreeningGenreTaste(genre, if (amount == 0.0) null else entries.sumOf { it.influence } / (3.0 + amount),
                amount, entries.map { it.key }.distinct().size, entries.map { it.id })
        }
        return ScreeningTasteProfile(now, genres, evidence, evidence.filter { it.contributes }.map { it.key }.distinct().size)
    }
}

data class ScreeningSelectionWeight(val key: String, val weight: Double)

data class ScreeningScoreTrace(
    val features: Map<String, Double>,
    val rawPoints: Double,
    val varietyAdjustment: Double,
    val selectionPoints: Double,
    val shortlist: List<ScreeningSelectionWeight>,
    val poolSize: Int,
    val generatedAt: Long,
    val modelVersion: String = SCREENING_MODEL_VERSION
) {
    companion object {
        fun from(pick: ScreeningRecommendation) = ScreeningScoreTrace(pick.features, pick.score, pick.diversityAdjustment,
            pick.selectionScore, pick.shortlist, pick.eligiblePoolSize, pick.generatedAt)
    }
}

data class ScreeningDecisionSnapshot(
    val publicationId: String,
    val surface: String,
    val source: String,
    val key: String,
    val itemId: String,
    val title: String,
    val at: Long,
    val trace: ScreeningScoreTrace?,
    val mode: String = "Selected",
    val reason: String? = null
)

data class ScreeningEligibility(val eligible: Boolean, val reason: String, val until: Long? = null)

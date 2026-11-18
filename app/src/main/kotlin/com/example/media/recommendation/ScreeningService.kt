package com.example.media.recommendation

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.remote.trakt.TraktSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ScreeningInsights(
    val items: List<SavedItem>, val signals: ScreeningSignals, val profile: ScreeningTasteProfile,
    val ranked: List<ScreeningRecommendation>, val history: List<ScreeningHistoryChange>,
    val publications: List<ScreeningDecisionSnapshot>, val deliveries: Map<String, ScreeningStore.Delivery> = emptyMap()
) {
    val itemsById: Map<String, SavedItem> = items.associateBy { it.id }
    val titles: List<ScreeningCanonicalTitle> = ScreeningIdentity.resolve(items, signals.aliases)
    val signalsByKey: Map<String, ScreeningItemSignal> = titles.associate { title -> title.key to
        ScreeningRecommendationEngine.mergeSignals(title.aliases.mapNotNull(signals.items::get)) }
    val eligibilityByKey: Map<String, ScreeningEligibility> = titles.associate { title -> title.key to
        ScreeningRecommendationEngine.eligibility(title.item, signalsByKey.getValue(title.key), profile.generatedAt) }
    val blockedTitles: List<ScreeningCanonicalTitle> = titles.filter {
        val signal = signalsByKey.getValue(it.key)
        signal.notInterested || signal.suppressedUntil > profile.generatedAt
    }

    companion object {
        val Empty = ScreeningInsights(emptyList(), ScreeningSignals(), ScreeningTasteProfile(0, emptyList(), emptyList(), 0),
            emptyList(), emptyList(), emptyList())
    }
}

object ScreeningService {
    private val feedbackMutex = Mutex()
    private val observationMutex = Mutex()
    private val observedMedia = java.util.WeakHashMap<Context, List<SavedItem>>()
    suspend fun ranked(context: Context, cooldowns: Boolean = true): List<ScreeningRecommendation> = withContext(Dispatchers.IO) {
        val items = AppDatabase.getDatabase(context).savedItemDao().screeningMedia().map { it.toDomainModel() }
        val now = System.currentTimeMillis()
        val store = ScreeningStore(context)
        val signals = observationMutex.withLock {
            val owner = context.applicationContext
            val saved = if (observedMedia[owner] == items) store.learningSnapshot(now).signals else null
            // A reset clears progress baselines; rebuild them once. Ordinary refreshes need no per-title writes.
            if (saved == null || (saved.items.isEmpty() && items.any { ScreeningRecommendationEngine.kind(it) != null })) {
                store.observe(items, now).also { observedMedia[owner] = items }
            } else saved
        }
        ScreeningRecommendationEngine.rank(items, signals, now, includeCooldowns = cooldowns)
    }
    /** Reading insights does not observe progress, reserve a question, publish, or train new evidence. */
    suspend fun insights(context: Context): ScreeningInsights = withContext(Dispatchers.IO) {
        val items = AppDatabase.getDatabase(context).savedItemDao().screeningMedia().map { it.toDomainModel() }
        val now = System.currentTimeMillis(); val store = ScreeningStore(context)
        buildInsights(items, store.learningSnapshot(now), now)
    }
    fun buildInsights(items: List<SavedItem>, learning: ScreeningStore.LearningSnapshot, now: Long): ScreeningInsights {
        val signals = learning.signals
        val profile = ScreeningTaste.profile(items, signals, now)
        return ScreeningInsights(items, signals, profile,
            ScreeningRecommendationEngine.rank(items, signals, now, includeCooldowns = false, profile = profile),
            learning.history, learning.publications, learning.deliveries)
    }
    /** Publishing a visible review happens after rendering, without blocking navigation or its feedback sheet. */
    suspend fun reviewDelivery(context: Context, pick: ScreeningRecommendation, source: String): ScreeningStore.Delivery? = withContext(Dispatchers.IO) {
        feedbackMutex.withLock {
            val store = ScreeningStore(context)
            val current = AppDatabase.getDatabase(context).savedItemDao().getItemById(pick.item.id)?.toDomainModel()
            if (current == null || current.isDeleted || current.isArchived || current.isUnavailable) return@withLock null
            val key = store.canonicalKey(current)
            if (key != store.canonicalKey(pick.key) && pick.key !in ScreeningRecommendationEngine.aliases(current)) return@withLock null
            val visible = pick.copy(key = key)
            val now = System.currentTimeMillis()
            store.createDelivery(visible, now, ScreeningSchedule.day(now), false)?.also {
                store.markPosted(it.token, now, visible, source, if (visible.shortlist.isEmpty()) "Opened saved title" else "Selected")
            }
        }
    }
    suspend fun ignoreEvidence(context: Context, id: String, ignored: Boolean): Boolean = withContext(Dispatchers.IO) {
        feedbackMutex.withLock {
            val store = ScreeningStore(context)
            val items = AppDatabase.getDatabase(context).savedItemDao().screeningMedia().map { it.toDomainModel() }
            val now = System.currentTimeMillis(); val signals = store.observe(items, now)
            val canonicalId = if (id.startsWith("rating:")) "rating:${signals.aliases[id.removePrefix("rating:")] ?: id.removePrefix("rating:")}" else id
            if (ScreeningTaste.profile(items, signals, now).evidence.none { it.id == canonicalId }) return@withLock false
            store.ignoreEvidence(canonicalId, ignored, now)
            com.example.widget.WidgetUpdater.update(context); true
        }
    }
    suspend fun clearReaction(context: Context, key: String) = withContext(Dispatchers.IO) {
        feedbackMutex.withLock { ScreeningStore(context).clearReaction(key); com.example.widget.WidgetUpdater.update(context) }
    }
    suspend fun recommendAgain(context: Context, key: String) = withContext(Dispatchers.IO) {
        feedbackMutex.withLock { ScreeningStore(context).recommendAgain(key); com.example.widget.WidgetUpdater.update(context) }
    }
    suspend fun resetFeedback(context: Context) = withContext(Dispatchers.IO) {
        feedbackMutex.withLock { ScreeningStore(context).resetLearning(); com.example.widget.WidgetUpdater.update(context) }
    }
    suspend fun answer(context: Context, token: String, feedback: ScreeningFeedback): Boolean = withContext(Dispatchers.IO) {
        feedbackMutex.withLock {
            val store = ScreeningStore(context)
            val original = store.delivery(token) ?: return@withLock false
            if (original.answered) return@withLock false
            val db = AppDatabase.getDatabase(context)
            val item = db.savedItemDao().getItemById(original.itemId)?.toDomainModel() ?: return@withLock false
            store.observe(listOf(item))
            val delivery = store.delivery(token) ?: return@withLock false
            if (ScreeningRecommendationEngine.watchStatus(item) in setOf("completed", "dropped") || item.isDeleted || item.isArchived || item.isUnavailable || store.canonicalKey(item) != delivery.key) return@withLock false
            if (feedback == ScreeningFeedback.WATCHED || feedback == ScreeningFeedback.STILL_WATCHING) {
                // This action is confirmed on screen, never inferred. TV completion doesn't fabricate episode history.
                val normalized = item.copy(mediaType = ScreeningRecommendationEngine.kind(item))
                val status = if (feedback == ScreeningFeedback.WATCHED) "Completed" else "Watching"
                val ids = TraktSyncEngine.resolveIds(normalized.mediaType, normalized.id, normalized.traktId, normalized.tmdbId, normalized.imdbId)
                if (ids.trakt != null || ids.tmdb != null || ids.imdb != null) TraktSyncEngine.setWatchStatus(context, normalized, status).getOrThrow()
                else com.example.notes.NoteWriteLock.mutex.withLock {
                    db.withTransaction {
                        val current = db.savedItemDao().getItemById(item.id) ?: error("Title removed")
                        check(!current.isDeleted && !current.isArchived && !current.isUnavailable)
                        db.savedItemDao().insertItem(current.copy(watchStatus = status, watchStatusOverride = status, inWatchlist = false,
                            lastWatchedAt = if (normalized.mediaType == "movie" && status == "Completed") System.currentTimeMillis() else current.lastWatchedAt,
                            updatedAt = System.currentTimeMillis()))
                    }
                }
            }
            val saved = store.answer(token, item, feedback)
            if (saved) { ScreeningNotifications.cancel(context, token); com.example.widget.WidgetUpdater.update(context) }
            saved
        }
    }
}

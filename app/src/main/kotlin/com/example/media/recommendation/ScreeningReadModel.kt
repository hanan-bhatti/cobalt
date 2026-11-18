package com.example.media.recommendation

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.example.data.local.AppDatabase
import com.example.data.local.TraktPlaybackEntity
import com.example.data.remote.trakt.TraktAuthManager
import com.example.widget.ScreeningThumb
import com.example.widget.ScreeningWidgetEntry
import com.example.widget.ScreeningWidgetSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScreeningUiState(
    val insights: ScreeningInsights = ScreeningInsights.Empty,
    val ready: Boolean = false,
    val error: String? = null,
    val playbackSession: String? = null,
    val playback: List<TraktPlaybackEntity> = emptyList()
) {
    fun preview(entry: ScreeningWidgetEntry): ScreeningWidgetEntry {
        val pick = entry.pick ?: return entry
        val item = insights.itemsById[pick.itemId]
        val aliases = (listOf(pick.key) + item?.let(ScreeningRecommendationEngine::aliases).orEmpty()).distinct()
        val reaction = aliases.flatMap { key -> listOfNotNull(insights.signals.reactions[key],
            insights.signals.aliases[key]?.let(insights.signals.reactions::get)) }
            .maxWithOrNull(compareBy<ScreeningReaction> { it.at ?: 0L }.thenBy { it.revision })
        return ScreeningWidgetSelection.withPlayback(entry, item, playbackSession, playback)
            .copy(thumb = reaction?.value?.let { ScreeningThumb.valueOf(it.name) })
    }
}

/** One eagerly prepared, retained view for every Screening screen. Navigation never starts a query. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScreeningReadModel private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = MutableStateFlow(0L)
    private val mutableState = MutableStateFlow(ScreeningUiState())
    val state: StateFlow<ScreeningUiState> = mutableState.asStateFlow()
    @Volatile private var refreshedAt = 0L

    init {
        scope.launch {
            val store = ScreeningStore(app)
            val prefs = app.getSharedPreferences("cobalt_screening_widgets", Context.MODE_PRIVATE)
            // Legacy thumbs can come from older widget processes. Layout/config writes don't affect taste.
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key?.startsWith("thumb_") == true) refresh()
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            try {
                val media = com.example.notes.backgroundNoteDatabaseReads(app) {
                    AppDatabase.getDatabase(app).savedItemDao().observeScreeningMedia()
                        .map { rows -> rows.map { it.toDomainModel() } }.distinctUntilChanged()
                }
                requests.flatMapLatest {
                    combine(media, store.changes) { items, _ -> items }
                        .catch { mutableState.update { state -> state.copy(error = "Couldn't refresh Screening. Your saved view is still available.") } }
                }.collectLatest { items ->
                    try {
                        val now = System.currentTimeMillis()
                        val learning = store.learningSnapshot(now)
                        val insights = withContext(Dispatchers.Default) { ScreeningService.buildInsights(items, learning, now) }
                        mutableState.update { it.copy(insights = insights, ready = true, error = null) }
                        refreshedAt = SystemClock.elapsedRealtime()
                        scope.launch {
                            insights.ranked.take(12).map { it.item.backdropUrl ?: it.item.thumbnailPath }.distinct()
                                .forEach { com.example.widget.ScreeningArtworkCache.cached(app, it) }
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { mutableState.update { it.copy(error = "Couldn't refresh Screening. Your saved view is still available.") } }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutableState.update { it.copy(error = "Couldn't refresh Screening. Your saved view is still available.") } }
            finally { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        scope.launch {
            val auth = TraktAuthManager.getInstance(app)
            combine(auth.playbackSession, com.example.notes.noteDatabaseReadiness(app)) { session, allowed -> session to allowed }
                .flatMapLatest { (session, allowed) ->
                if (session != null && allowed) AppDatabase.getDatabase(app).traktPlaybackDao().observe(session).map { session to it }
                else flowOf(session to emptyList())
            }.catch { emit(null to emptyList()) }.collect { (session, positions) ->
                mutableState.update { it.copy(playbackSession = session, playback = positions) }
            }
        }
    }

    fun refresh() { requests.update { it + 1 } }
    fun refreshIfExpired() {
        if (state.value.ready && SystemClock.elapsedRealtime() - refreshedAt > 5 * 60_000) refresh()
    }

    companion object {
        private val instances = java.util.WeakHashMap<Context, ScreeningReadModel>()
        fun get(context: Context): ScreeningReadModel = synchronized(instances) {
            instances.getOrPut(context.applicationContext) { ScreeningReadModel(context.applicationContext) }
        }
    }
}

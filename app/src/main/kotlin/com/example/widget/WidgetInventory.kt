package com.example.widget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WidgetInventoryState(
    val folioIds: List<Int> = emptyList(),
    val screeningIds: List<Int> = emptyList(),
    val folios: Map<Int, FolioConfig> = emptyMap(),
    val screenings: Map<Int, ScreeningWidgetEntry> = emptyMap(),
    val ready: Boolean = false
)

/** Android owns registrations; drafts are separate and never counted as installed widgets. */
class WidgetInventory private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = MutableStateFlow(0L)
    private val mutableState = MutableStateFlow(WidgetInventoryState())
    val state: StateFlow<WidgetInventoryState> = mutableState.asStateFlow()

    init {
        scope.launch {
            val folios = FolioConfigStore(app)
            val screenings = ScreeningWidgetStore(app)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "draft" || key?.startsWith("widget_") == true) refresh()
            }
            folios.preferences.registerOnSharedPreferenceChangeListener(listener)
            screenings.preferences.registerOnSharedPreferenceChangeListener(listener)
            try {
                requests.collectLatest {
                    try {
                        val folioIds = FolioWidgetProvider.ids(app)
                        val screeningIds = ScreeningWidgetProvider.ids(app)
                        // Ignore unregistered settings in the UI, but preserve them for onRestored's ID remapping.
                        val snapshot = WidgetInventoryState(
                            folioIds.filter(folios::isConfigured), screeningIds.filter(screenings::isConfigured),
                            (folioIds + -1).associateWith(folios::get), (screeningIds + -1).associateWith(screenings::getSaved), true)
                        currentCoroutineContext().ensureActive()
                        mutableState.value = snapshot
                        snapshot.screenings.values.mapNotNull { it.pick?.artwork }.distinct()
                            .forEach { ScreeningArtworkCache.cached(app, it) }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) { android.util.Log.w("CobaltWidgets", "Could not refresh widget registrations", error) }
                }
            } finally {
                folios.preferences.unregisterOnSharedPreferenceChangeListener(listener)
                screenings.preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }
    fun refresh() { requests.update { it + 1 } }

    companion object {
        private val instances = java.util.WeakHashMap<Context, WidgetInventory>()
        fun get(context: Context): WidgetInventory = synchronized(instances) {
            instances.getOrPut(context.applicationContext) { WidgetInventory(context.applicationContext) }
        }
    }
}

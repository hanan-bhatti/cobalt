package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.media.recommendation.ScreeningSchedule
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import com.example.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreeningWidgetSettingsScreen(viewModel: CobaltViewModel, onNavigateBack: () -> Unit,
    onRecommendations: ((String?, ScreeningSource) -> Unit)? = null, configurationId: Int? = null, onConfigured: (() -> Unit)? = null,
    onTaste: ((String?, ScreeningSource) -> Unit)? = null, onMorningCheckIn: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsWrites = remember { Mutex() }
    val store = remember(context) { ScreeningWidgetStore(context) }
    val inventory by viewModel.widgets.state.collectAsState()
    val screening by viewModel.screening.state.collectAsState()
    val activeIds = inventory.screeningIds
    var creating by rememberSaveable { mutableStateOf(false) }
    var activitySource by rememberSaveable(configurationId) { mutableStateOf<String?>(null) }
    var sourceOverrides by remember { mutableStateOf<Map<Int, ScreeningSource>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var pendingPin by rememberSaveable { mutableStateOf(false) }
    var launcherHelp by remember { mutableStateOf(false) }
    var removeId by remember { mutableStateOf<Int?>(null) }
    var pinBefore by remember { mutableStateOf<List<Int>?>(null) }
    var preferredId by remember { mutableStateOf<Int?>(null) }
    val pages = when {
        configurationId != null -> listOf(configurationId)
        creating -> listOf(-1)
        else -> activeIds
    }
    val latestPages by rememberUpdatedState(pages)
    val pager = rememberPagerState { latestPages.size.coerceAtLeast(1) }
    val selectedId = pages.getOrElse(pager.currentPage) { pages.firstOrNull() ?: -1 }
    val editing = configurationId != null || creating || activeIds.isNotEmpty()
    val day = ScreeningSchedule.day(System.currentTimeMillis())

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            val target = preferredId?.let(pages::indexOf)?.takeIf { it >= 0 } ?: pager.currentPage.coerceAtMost(pages.lastIndex)
            pager.scrollToPage(target)
            preferredId = null
        }
    }
    LaunchedEffect(activeIds) {
        val before = pinBefore
        val added = if (before != null) activeIds.firstOrNull { it !in before } else null
        if (added != null) { preferredId = added; creating = false; pendingPin = false; pinBefore = null }
    }
    LaunchedEffect(inventory) {
        sourceOverrides = sourceOverrides.filter { (id, source) -> inventory.screenings[id]?.config?.source != source &&
            (id == -1 || id in activeIds) }
    }
    // A launcher may show its pin dialog without pausing Cobalt. Cancellation remains retryable.
    LaunchedEffect(pendingPin) { if (pendingPin) { delay(2_000); pendingPin = false } }

    fun preview(id: Int): ScreeningWidgetEntry {
        val saved = inventory.screenings[id] ?: ScreeningWidgetEntry()
        val source = if (configurationId != null) activitySource?.let(ScreeningSource::valueOf) ?: saved.config.source
            else sourceOverrides[id] ?: saved.config.source
        val entry = saved.copy(config = ScreeningWidgetConfig(source))
        return if (screening.ready) ScreeningWidgetSelection.update(entry, screening.insights.ranked, id, day) else entry
    }
    val entry = remember(selectedId, inventory, screening.insights, screening.ready, activitySource, sourceOverrides, day) { preview(selectedId) }
    fun openMedia() { context.startActivity(Intent(context, MainActivity::class.java).putExtra("NAVIGATE_TO", "media_hub").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    fun openPick(pick: ScreeningPick?) {
        if (pick == null) openMedia()
        else if (configurationId == null) viewModel.openItemById(pick.itemId)
        else context.startActivity(Intent(context, MainActivity::class.java).putExtra("OPEN_ITEM_ID", pick.itemId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    fun changeSource(source: ScreeningSource) {
        if (configurationId != null) activitySource = source.name
        else {
            val id = selectedId
            sourceOverrides = sourceOverrides + (id to source)
            scope.launch {
                try {
                    settingsWrites.withLock { withContext(Dispatchers.IO) {
                        if (id == -1) store.saveConfig(id, ScreeningWidgetConfig(source))
                        else {
                            check(id in ScreeningWidgetProvider.ids(context) && store.saveConfigured(id, ScreeningWidgetConfig(source))) { "Widget was removed" }
                            WidgetUpdater.update(context)
                        }
                    } }
                } catch (_: Exception) {
                    sourceOverrides = sourceOverrides - id
                    Toast.makeText(context, "Couldn't save your card settings. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    NoteSettingsPage(title = "Screening Card", subtitle = "Your next watch. Or your next read.", onBack = onNavigateBack, itemSpacing = 16.dp,
        actions = { if (configurationId == null && !creating && selectedId > 0) WidgetSetupMenu { removeId = selectedId } }) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (editing) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalPager(state = pager, key = { latestPages.getOrNull(it) ?: "removed-$it" }, modifier = Modifier.fillMaxWidth()) { page ->
                            val id = latestPages.getOrNull(page) ?: return@HorizontalPager
                            val pageEntry = remember(id, inventory, screening.insights, screening.ready, activitySource, sourceOverrides, day) { preview(id) }
                            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                ScreeningCardPreview(pageEntry, busy, onOpen = { openPick(pageEntry.pick) }, feedbackOrigin = "Widget settings")
                            }
                        }
                        if (pages.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pages.forEachIndexed { index, _ ->
                                val width by animateDpAsState(if (index == pager.currentPage) 20.dp else 6.dp, label = "Screening page")
                                val color by animateColorAsState(if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "Screening indicator")
                                Box(Modifier.size(width, 6.dp).background(color, CircleShape).clickable { scope.launch { pager.animateScrollToPage(index) } })
                            }
                        }
                    }
                    AiInfoLabel("Customize", "These choices apply to this Screening Card only. Tap artwork to open details. Thumbs save recommendation preferences; tap the selected thumb again to clear it. The bottom line shows a saved Trakt playback position.")
                    if (configurationId == null && !creating && activeIds.size > 1) Text(
                        "Card ${pager.currentPage + 1} of ${activeIds.size} · swipe to switch", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExpressiveSettingsGroup { ScreeningSourceSelectorItem(entry.config.source, ::changeSource) }
                    if (entry.pick == null && screening.ready) Text("No eligible picks for this source. Choose another source or update your saved titles in Media.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        if (configurationId == null && !creating) { creating = true; preferredId = -1 }
                        else {
                            busy = true
                            scope.launch {
                                try {
                                    if (configurationId != null) {
                                        withContext(Dispatchers.IO) {
                                            check(configurationId in ScreeningWidgetProvider.ids(context)) { "Widget was removed" }
                                            store.save(configurationId, entry)
                                            ScreeningWidgetProvider.showSaved(context); ScreeningWidgetRefreshWorker.enqueue(context)
                                        }
                                        onConfigured?.invoke()
                                    } else {
                                        pendingPin = true; pinBefore = activeIds
                                        if (!withContext(Dispatchers.IO) { ScreeningWidgetProvider.requestPin(context, entry) }) { pendingPin = false; pinBefore = null; launcherHelp = true }
                                    }
                                } catch (_: Exception) { pendingPin = false; pinBefore = null; Toast.makeText(context, "Couldn't place your card. Please try again.", Toast.LENGTH_LONG).show() }
                                finally { busy = false }
                            }
                        }
                    }, enabled = !busy && !pendingPin, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (configurationId != null) "Save widget" else if (!creating) "Add another card" else "Add to home screen")
                    }
                    if (configurationId != null || creating) Text(
                        if (configurationId != null) "Save to apply your choices. Back keeps the current settings." else "Confirm placement in your launcher’s dialog.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (configurationId == null && creating) TextButton(onClick = { creating = false }) { Text("Cancel new widget") }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        ScreeningCardPreview(entry, busy, onOpen = { openPick(entry.pick) }, feedbackOrigin = "Widget settings")
                        Text("Choose which saved titles this card recommends before adding it to your home screen.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { creating = true; preferredId = -1 }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Set up Screening Card") }
                    }
                }
                SettingsGroupLabel("Recommendations")
                ExpressiveSettingsNavigationItem("Current pick & feedback", {
                    onRecommendations?.invoke(entry.pick?.itemId, entry.config.source) ?: context.startActivity(Intent(context, MainActivity::class.java)
                        .putExtra("NAVIGATE_TO", "screening_recommendations?itemId=${android.net.Uri.encode(entry.pick?.itemId.orEmpty())}&source=${entry.config.source.name}")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }, subtitle = "Review a suggestion, understand it and update progress",
                    leadingIcon = { SettingsIconBadge(com.example.R.drawable.ic_widget_current_pick) }, compact = true)
                ExpressiveSettingsNavigationItem("Recommendation insights", {
                    onTaste?.invoke(null, entry.config.source) ?: context.startActivity(Intent(context, MainActivity::class.java)
                        .putExtra("NAVIGATE_TO", "screening_taste?source=${entry.config.source.name}")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }, subtitle = "Genres, saved titles, feedback and learning history",
                    leadingIcon = { SettingsIconBadge(com.example.R.drawable.ic_widget_insights) }, compact = true)
                SettingsGroupLabel("Reminders")
                ExpressiveSettingsNavigationItem("Morning check-in", {
                    onMorningCheckIn?.invoke() ?: context.startActivity(Intent(context, MainActivity::class.java).putExtra("NAVIGATE_TO", "screening_morning_settings")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }, subtitle = "Choose whether and when to receive a question about your picks",
                    leadingIcon = { SettingsIconBadge(com.example.R.drawable.ic_custom_reminder) }, compact = true)
                screening.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel.screening::refresh) { Text("Try again") }
                }
            }
        }
    }
    removeId?.let { id ->
        AlertDialog(onDismissRequest = { removeId = null }, title = { Text("Remove this card from Cobalt?") },
            text = { Text("Remove the widget from your home screen first. This clears its saved card settings. Your saved titles and feedback stay.") },
            confirmButton = { TextButton(onClick = {
                removeId = null
                scope.launch { withContext(Dispatchers.IO) { store.delete(id) }; viewModel.widgets.refresh() }
            }) { Text("Remove saved card") } },
            dismissButton = { TextButton(onClick = { removeId = null }) { Text("Keep card") } })
    }
    if (launcherHelp) AlertDialog(onDismissRequest = { launcherHelp = false }, title = { Text("Add from your home screen") },
        text = { Text("Long-press an empty space on your home screen, open Widgets, and choose Cobalt → Screening Card. You can choose this card’s source before placing it.") },
        confirmButton = { TextButton(onClick = { launcherHelp = false }) { Text("Got it") } })
}

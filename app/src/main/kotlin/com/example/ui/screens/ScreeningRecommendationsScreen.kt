package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedItem
import com.example.media.recommendation.*
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import com.example.widget.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class ScreeningFeedbackTarget(val pick: ScreeningRecommendation)

@Composable
fun ScreeningRecommendationsScreen(viewModel: CobaltViewModel, onNavigateBack: () -> Unit,
    initialItemId: String? = null, source: ScreeningSource = ScreeningSource.ALL,
    onTaste: ((String?, ScreeningSource) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.screening.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var publicationError by remember { mutableStateOf<String?>(null) }
    var publicationRevision by remember { mutableIntStateOf(0) }
    var feedbackTarget by remember { mutableStateOf<ScreeningFeedbackTarget?>(null) }
    val excluded = remember(initialItemId, source) { mutableStateListOf<String>() }
    val pool = remember(state.insights.ranked, source, excluded.toList()) {
        state.insights.ranked.filter { source.accepts(it.item) && it.key !in excluded }
    }
    // Initial composition uses already prepared data. Refreshing keeps the visible decision and its trace.
    var retained by remember(initialItemId, source) { mutableStateOf(
        pool.find { it.item.id == initialItemId } ?: ScreeningRecommendationEngine.choose(pool, "preview:0")) }
    val pick = remember(pool, retained, revision) {
        val current = retained?.let { old -> pool.find { it.key == old.key || it.item.id == old.item.id } }
        if (current != null) retained!!.copy(item = current.item, key = current.key, progress = current.progress)
        else pool.find { revision == 0 && it.item.id == initialItemId } ?: ScreeningRecommendationEngine.choose(pool, "preview:$revision")
    }
    LaunchedEffect(pick) { retained = pick }
    LaunchedEffect(pick?.key, pick?.item?.id, source, publicationRevision) {
        val visible = pick ?: return@LaunchedEffect
        try {
            ScreeningService.reviewDelivery(context, visible, source.name)
            publicationError = null
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { publicationError = "Couldn't save this review's history. You can still review this pick." }
    }
    fun feedback(kind: ScreeningFeedback) {
        val target = feedbackTarget ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                // Reservation is durable but never gates the card or opening the sheet.
                val delivery = ScreeningService.reviewDelivery(context, target.pick, source.name)
                val saved = delivery != null && ScreeningService.answer(context, delivery.token, kind)
                if (saved) excluded += target.pick.key
                feedbackTarget = null
                revision++
                Toast.makeText(context, if (saved) "Response saved" else "This question is no longer available. Your picks have been updated.", Toast.LENGTH_SHORT).show()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, "Couldn't save your response. Try again.", Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    fun openMedia() {
        context.startActivity(android.content.Intent(context, com.example.MainActivity::class.java)
            .putExtra("NAVIGATE_TO", "media_hub")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    val reading = source == ScreeningSource.MANGA || pick?.item?.mediaType?.lowercase() == "manga"
    NoteSettingsPage(title = if (reading) "Your next read" else "Your next watch", subtitle = "A pick from the titles you have saved.", onBack = onNavigateBack) {
        item {
            val current = pick
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (current != null) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ScreeningCardPreview(ScreeningWidgetEntry(config = ScreeningWidgetConfig(source), pick = ScreeningPick.from(current), poolSize = pool.size),
                            busy, onOpen = { viewModel.openItemById(current.item.id) })
                    }
                    ExpressiveSettingsNavigationItem("Why this pick?", {
                        onTaste?.invoke(current.item.id, source) ?: context.startActivity(android.content.Intent(context, com.example.MainActivity::class.java)
                            .putExtra("NAVIGATE_TO", "screening_taste?itemId=${android.net.Uri.encode(current.item.id)}&source=${source.name}")
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    }, subtitle = "Inspect its score and the feedback behind it")
                    SettingsSubsectionHeader("Your feedback", description = "Help improve the next recommendation.",
                        tooltipText = "Good pick asks for similar recommendations. Not interested hides this title. Maybe later skips it for 7 days. Update watching or reading progress in the saved title’s details.")
                    Button(onClick = { feedbackTarget = ScreeningFeedbackTarget(current) },
                        enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("What do you think?") }
                    ExpressiveSettingsNavigationItem(if (reading) "Update reading progress" else "Update watch progress",
                        onClick = { viewModel.openItemById(current.item.id) }, subtitle = "Open this title’s existing details")
                } else {
                    Text(if (!state.ready) "Review a title from your saved collection."
                        else if (excluded.isNotEmpty()) "You've seen all available picks for now."
                        else "No picks available for this source. Choose another source or update your saved titles in Media.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExpressiveSettingsNavigationItem("Saved titles", ::openMedia, subtitle = "Open your collection in Media")
                    if (excluded.isNotEmpty()) TextButton(onClick = { excluded.clear(); retained = null; revision++ }) { Text("Start again") }
                }
                (state.error ?: publicationError)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { publicationRevision++; viewModel.screening.refresh() }) { Text("Try again") }
                }
            }
        }
    }
    feedbackTarget?.let { target -> ScreeningFeedbackSheet(target.pick.item.title, busy, ::feedback, onDismiss = { feedbackTarget = null }) }
}

/** Notification questions use the same retained catalog and delivery snapshot as the other screens. */
@Composable
fun ScreeningQuestionScreen(token: String, viewModel: CobaltViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.screening.state.collectAsState()
    val delivery = state.insights.deliveries[token]
    val candidate = delivery?.itemId?.let(state.insights.itemsById::get)
    val valid = delivery != null && candidate != null && !candidate.isDeleted && !candidate.isArchived && !candidate.isUnavailable &&
        (ScreeningRecommendationEngine.aliases(candidate) + ScreeningRecommendationEngine.aliases(candidate).mapNotNull(state.insights.signals.aliases::get))
            .contains(delivery.key)
    val item = candidate?.takeIf { valid }
    var answered by remember(token) { mutableStateOf(false) }
    var savedMessage by remember(token) { mutableStateOf<String?>(null) }
    val usable = valid && delivery?.answered == false && !answered &&
        ScreeningRecommendationEngine.watchStatus(item!!) !in setOf("completed", "dropped")
    var busy by remember { mutableStateOf(false) }
    var feedbackOpen by remember(token) { mutableStateOf(usable) }
    LaunchedEffect(usable) { if (usable) feedbackOpen = true }
    fun answer(kind: ScreeningFeedback) {
        if (busy || !usable) return
        busy = true
        scope.launch {
            try {
                val saved = ScreeningService.answer(context, token, kind)
                answered = true; feedbackOpen = false
                savedMessage = if (saved) "Response saved. Future picks will use your feedback." else "This question is no longer available."
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { savedMessage = "Couldn't save your response. Please try again." }
            finally { busy = false }
        }
    }
    val reading = item?.mediaType?.lowercase() == "manga"
    NoteSettingsPage(if (reading) "Your next read" else "Your next watch", "A question about your saved title.", onNavigateBack) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item?.let { saved ->
                    val display = remember(saved) { ScreeningPick.from(ScreeningRecommendation(saved, ScreeningRecommendationEngine.key(saved), 0.0, "", "watchlist",
                        ScreeningRecommendationEngine.progress(saved, now = System.currentTimeMillis()), "", emptyMap())) }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ScreeningCardPreview(ScreeningWidgetEntry(pick = display, poolSize = 1), busy, { viewModel.openItemById(saved.id) })
                    }
                    if (usable) {
                        SettingsSubsectionHeader("Your feedback", description = "Help improve the next recommendation.", tooltipText = "These choices tune recommendations. Watching and reading progress belongs in the title’s details.")
                        Button(onClick = { feedbackOpen = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("What do you think?") }
                    }
                    ExpressiveSettingsNavigationItem(if (reading) "Update reading progress" else "Update watch progress", { viewModel.openItemById(saved.id) }, subtitle = "Open this title’s existing details")
                }
                val message = savedMessage ?: when {
                    delivery?.answered == true -> "Your response has already been saved."
                    state.ready && !usable -> "This question is no longer available."
                    item == null -> "Review your saved titles from Screening Card."
                    else -> null
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (item == null) ExpressiveSettingsNavigationItem("Saved titles", {
                    context.startActivity(android.content.Intent(context, com.example.MainActivity::class.java).putExtra("NAVIGATE_TO", "media_hub")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }, subtitle = "Open your collection in Media")
            }
        }
    }
    if (feedbackOpen && usable) ScreeningFeedbackSheet(item?.title.orEmpty(), busy, ::answer, onDismiss = { feedbackOpen = false })
}

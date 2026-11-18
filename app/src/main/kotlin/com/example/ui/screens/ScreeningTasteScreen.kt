package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.MainActivity
import com.example.media.recommendation.*
import com.example.ui.components.ExpressiveSettingsNavigationItem
import com.example.ui.components.NoteSettingsPage
import com.example.ui.viewmodel.CobaltViewModel
import com.example.widget.ScreeningSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ScreeningTasteScreen(viewModel: CobaltViewModel, onNavigateBack: () -> Unit,
    initialItemId: String? = null, source: ScreeningSource = ScreeningSource.ALL) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.screening.state.collectAsState()
    var activeChanges by remember { mutableIntStateOf(0) }
    val busy = activeChanges > 0
    val snackbar = remember { SnackbarHostState() }
    suspend fun finishChange(task: suspend () -> Unit): Boolean {
        return try { task(); true }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { false }
        finally { activeChanges-- }
    }
    fun action(message: String, task: suspend () -> Unit) {
        if (activeChanges > 0) return
        activeChanges++
        scope.launch {
            val saved = finishChange(task)
            snackbar.showSnackbar(if (saved) message else "Couldn't save that change. Try again.")
        }
    }
    Box(Modifier.fillMaxSize()) {
        ScreeningTasteContent(state.insights, state.error, busy, initialItemId, source, onNavigateBack,
            onRetry = viewModel.screening::refresh, onOpenTitle = viewModel::openItemById,
            onOpenMedia = { context.startActivity(Intent(context, MainActivity::class.java).putExtra("NAVIGATE_TO", "media_hub")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) },
            onIgnore = { evidence, ignored ->
                if (activeChanges == 0) {
                    activeChanges++
                    scope.launch {
                        val saved = finishChange {
                            check(ScreeningService.ignoreEvidence(context, evidence.id, ignored)) { "Evidence is no longer available" }
                        }
                        if (saved) {
                            val result = snackbar.showSnackbar(if (ignored) "Removed from recommendation learning" else "Added back to recommendation learning", "Undo")
                            if (result == SnackbarResult.ActionPerformed) {
                                activeChanges++
                                val restored = finishChange { check(ScreeningService.ignoreEvidence(context, evidence.id, !ignored)) }
                                if (!restored) snackbar.showSnackbar("Couldn't undo that change. Review this evidence in Your feedback.")
                            }
                        } else snackbar.showSnackbar("Couldn't save that change. Try again.")
                    }
                }
            },
            onClearReaction = { key -> action("Thumb cleared") { ScreeningService.clearReaction(context, key) } },
            onRecommendAgain = { key -> action("Recommendation filter cleared") { ScreeningService.recommendAgain(context, key) } },
            onReset = { action("Screening feedback cleared. Saved ratings still guide your picks.") { ScreeningService.resetFeedback(context) } })
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

/** The presentation reads the shared snapshot; no learning or publication occurs here. */
@Composable
fun ScreeningTasteContent(snapshot: ScreeningInsights, error: String? = null, busy: Boolean = false,
    initialItemId: String? = null, source: ScreeningSource = ScreeningSource.ALL,
    onBack: () -> Unit, onRetry: () -> Unit = {}, onOpenTitle: (String) -> Unit = {}, onOpenMedia: () -> Unit = onBack,
    onIgnore: (ScreeningEvidence, Boolean) -> Unit = { _, _ -> }, onClearReaction: (String) -> Unit = {},
    onRecommendAgain: (String) -> Unit = {}, onReset: () -> Unit = {}) {
    var page by rememberSaveable(initialItemId) { mutableStateOf(if (initialItemId == null) "overview" else "why") }
    var pageStack by rememberSaveable(initialItemId) { mutableStateOf(listOf<String>()) }
    var selectedStack by rememberSaveable(initialItemId) { mutableStateOf(listOf<String?>()) }
    var selectedItem by rememberSaveable(initialItemId) { mutableStateOf(initialItemId) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var genreKey by rememberSaveable { mutableStateOf<String?>(null) }
    var evidenceId by rememberSaveable { mutableStateOf<String?>(null) }
    var parentGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var learningDetails by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var titleFilter by rememberSaveable { mutableStateOf("all") }
    var feedbackFilter by rememberSaveable { mutableStateOf("active") }
    var resetDialog by remember { mutableStateOf(false) }
    val profile = snapshot.profile
    val overview = remember(profile) { ScreeningGenres.overview(profile.genres) }
    val titles = snapshot.titles
    val pool = remember(snapshot.ranked, source) { ScreeningRecommendationEngine.diversify(snapshot.ranked.filter { source.accepts(it.item) }) }
    val suggested = remember(pool, snapshot.publications, snapshot.signals.aliases, profile.generatedAt, source) { snapshot.let { data ->
        val last = data.publications.firstOrNull { it.source == source.name && pool.any { p ->
            p.item.id == it.itemId || p.key == (data.signals.aliases[it.key] ?: it.key)
        } }
        val lastKey = last?.key?.let { data.signals.aliases[it] ?: it }
        pool.find { it.item.id == last?.itemId || it.key == lastKey } ?:
            ScreeningRecommendationEngine.choose(pool, "insights:${ScreeningSchedule.day(data.profile.generatedAt)}:${source.name}")
    } }
    val title = titles.find { t -> t.copies.any { it.id == selectedItem } }
    val current = if (selectedItem == null) suggested else pool.find { it.key == title?.key }
    val selectedTitle = title ?: if (selectedItem == null) titles.find { it.key == current?.key } else null
    fun signal(t: ScreeningCanonicalTitle) = snapshot.signalsByKey.getValue(t.key)
    fun eligibility(t: ScreeningCanonicalTitle) = if (!source.accepts(t.item))
        ScreeningEligibility(false, "Outside the ${source.label.lowercase()} source")
        else snapshot.eligibilityByKey.getValue(t.key)
    val selectedSignal = selectedTitle?.let(::signal)
    val state = selectedTitle?.let(::eligibility)
    val blocked = snapshot.blockedTitles
    val latestPublication = snapshot.publications.firstOrNull { p -> selectedTitle != null &&
        (p.itemId == selectedTitle.item.id || (snapshot.signals.aliases[p.key] ?: p.key) == selectedTitle.key) }
    val evidence = profile.evidence.find { it.id == evidenceId }
    val genre = profile.genres.find { it.key == genreKey }

    fun go(destination: String, itemId: String? = selectedItem) {
        if (page != destination) {
            pageStack = pageStack + page
            selectedStack = selectedStack + selectedItem
            selectedItem = itemId
            page = destination
        }
    }
    fun back() {
        if (pageStack.isEmpty()) onBack()
        else {
            page = pageStack.last(); pageStack = pageStack.dropLast(1)
            selectedItem = selectedStack.last(); selectedStack = selectedStack.dropLast(1)
        }
    }
    fun showEvidence(e: ScreeningEvidence, fromGenre: Boolean = false) {
        parentGenre = if (fromGenre) genreKey else null
        evidenceId = e.id; learningDetails = false; detail = "evidence"
    }
    fun closeDetail() { detail = null; parentGenre = null; learningDetails = false }
    fun backToGenre() {
        genreKey = parentGenre; parentGenre = null; detail = "genre"; learningDetails = false
    }
    androidx.activity.compose.BackHandler(enabled = pageStack.isNotEmpty() && detail == null, onBack = ::back)
    val heading = when (page) {
        "others" -> "Other genres"; "feedback" -> "Your feedback"; "why" -> "Why this pick?"
        "history" -> "Learning history"; "hidden" -> "Hidden titles"; else -> "Recommendation insights"
    }
    val subtitle = when (page) {
        "others" -> "Every genre stays part of your taste."
        "feedback" -> "What shapes your next pick."
        "why" -> "The reasons behind this suggestion."
        "history" -> "Your recent feedback changes."
        "hidden" -> "Review titles you have skipped."
        else -> "Genres, titles and recommendation feedback"
    }

    if (page == "titles") TasteSavedTitles(titles.map { it to eligibility(it) }, source, search, { search = it },
        titleFilter, { titleFilter = it }, ::back, { go("why", it) })
    else NoteSettingsPage(heading, subtitle, ::back, itemSpacing = 24.dp, actions = {
        IconButton(onClick = { detail = "help" }) { TasteIcon(R.drawable.ic_custom_info, description = "About your taste") }
    }) {
        if (error != null) item {
            TasteGroup { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) { Text("Try again") }
            } }
        }
        when (page) {
            "overview" -> {
                item {
                    TasteSectionTitle("Explore")
                    com.example.ui.components.ExpressiveSettingsGroup {
                        ExpressiveSettingsNavigationItem("Saved titles", { go("titles") },
                            subtitle = "Review eligibility and why a title is suggested", trailingBadge = titles.size.toString(),
                            leadingIcon = { com.example.ui.components.SettingsIconBadge(R.drawable.ic_custom_movie) }, compact = true)
                        ExpressiveSettingsNavigationItem("Your feedback", { go("feedback") },
                            subtitle = "Ratings, reactions and excluded activity", trailingBadge = profile.evidence.size.toString(),
                            leadingIcon = { com.example.ui.components.SettingsIconBadge(R.drawable.ic_widget_feedback) }, compact = true)
                        ExpressiveSettingsNavigationItem("Hidden titles", { go("hidden") },
                            subtitle = "Review skipped suggestions and allow them again", trailingBadge = blocked.size.toString(),
                            leadingIcon = { com.example.ui.components.SettingsIconBadge(R.drawable.ic_widget_hidden_titles) }, compact = true)
                        ExpressiveSettingsNavigationItem("Learning history", { go("history") },
                            subtitle = "See changes to recommendation feedback", trailingBadge = snapshot.history.size.toString(),
                            leadingIcon = { com.example.ui.components.SettingsIconBadge(R.drawable.ic_custom_history) }, compact = true)
                    }
                }
                item { TasteSectionTitle("Your genres") }
                if (overview.visible.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(if (profile.titleCount == 0) "No taste feedback yet." else
                                "From ${profile.titleCount} ${if (profile.titleCount == 1) "title" else "titles"}",
                                Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TasteGenreGroup(overview.visible) { genreKey = it; detail = "genre"; learningDetails = false }
                        }
                    }
                    if (overview.others.isNotEmpty()) item {
                        ExpressiveSettingsNavigationItem("Others", { go("others") }, trailingBadge = overview.others.size.toString(),
                            leadingIcon = { TasteIcon(R.drawable.ic_custom_bar_chart) })
                    }
                } else item {
                    TasteEmpty(if (profile.titleCount == 0) "Your taste starts here" else "No genres to show yet",
                        if (profile.titleCount == 0) "Rate a saved title or give a pick a thumb." else
                            "Your feedback is saved. Genre information is not recorded yet.",
                        if (titles.isEmpty()) "Open Media" else "Review a saved title",
                        { if (titles.isEmpty()) onOpenMedia() else go("titles") })
                }
                if (suggested != null) {
                    item { TasteSectionTitle("Your next pick") }
                    item { TasteGroup { TasteMediaRow(suggested.item, suggested.reason, {
                        go("why", suggested.item.id)
                    }) } }
                }
                val recent = profile.evidence.filter { it.active && !it.ignored }.sortedByDescending { it.at ?: 0L }.take(3)
                if (recent.isNotEmpty()) {
                    item { TasteSectionTitle("Recent feedback") }
                    item { TasteGroup { recent.forEachIndexed { index, e ->
                        TasteEvidenceRow(e, titles.find { it.key == e.key }?.item, { showEvidence(e) })
                        if (index != recent.lastIndex) TasteDivider()
                    } } }
                }
                item { ExpressiveSettingsNavigationItem("See all feedback", { go("feedback") },
                    subtitle = "Review and adjust your feedback", leadingIcon = { TasteIcon(R.drawable.ic_widget_feedback) }) }
            }
            "others" -> {
                items(overview.others.chunked(6), key = { it.first().key }) { entries ->
                    TasteGenreGroup(entries) { genreKey = it; detail = "genre"; learningDetails = false }
                }
            }
            "feedback" -> {
                item {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("active" to "Active", "all" to "All feedback")) { (key, label) ->
                            FilterChip(selected = feedbackFilter == key, onClick = { feedbackFilter = key }, label = { Text(label) })
                        }
                    }
                }
                val feedback = profile.evidence.filter { feedbackFilter == "all" || (it.active && !it.ignored) }
                    .sortedWith(compareByDescending<ScreeningEvidence> { it.at ?: 0L }.thenBy { it.id })
                if (feedback.isEmpty()) item {
                    TasteEmpty("No active feedback", "Your ratings and reactions will appear here.",
                        if (profile.evidence.isEmpty()) "Review a saved title" else "Show all feedback",
                        { if (profile.evidence.isEmpty()) go("titles") else feedbackFilter = "all" })
                }
                items(feedback.chunked(8), key = { it.first().id }) { entries ->
                    TasteGroup { entries.forEachIndexed { index, e ->
                        TasteEvidenceRow(e, titles.find { it.key == e.key }?.item, { showEvidence(e) })
                        if (index != entries.lastIndex) TasteDivider()
                    } }
                }
                if (blocked.isNotEmpty()) item { ExpressiveSettingsNavigationItem("Hidden titles", { go("hidden") },
                    trailingBadge = blocked.size.toString(), subtitle = "Review skipped suggestions", leadingIcon = { TasteIcon(R.drawable.ic_widget_hidden_titles) }) }
                if (snapshot.history.isNotEmpty()) item { ExpressiveSettingsNavigationItem("Learning history", { go("history") },
                    subtitle = "Your recent changes", leadingIcon = { TasteIcon(R.drawable.ic_custom_history) }) }
                item { ExpressiveSettingsNavigationItem("Reset feedback", { resetDialog = true }, enabled = !busy,
                    subtitle = "Keep saved ratings and progress", leadingIcon = { TasteIcon(R.drawable.ic_custom_undo) }) }
            }
            "history" -> {
                if (snapshot.history.isEmpty()) item { Text("No learning history yet. Feedback changes will appear here.", style = MaterialTheme.typography.bodyMedium) }
                items(snapshot.history.chunked(6), key = { it.first().id }) { entries ->
                    TasteGroup { entries.forEachIndexed { index, c -> TasteHistoryRow(c); if (index != entries.lastIndex) TasteDivider() } }
                }
            }
            "hidden" -> {
                if (blocked.isEmpty()) item { Text("No hidden titles. Suggestions you hide will appear here.", style = MaterialTheme.typography.bodyMedium) }
                items(blocked, key = { it.key }) { t ->
                    TasteGroup { TasteMediaRow(t.item, tasteKind(t.item), { go("why", t.item.id) },
                        tasteEligibilityLabel(eligibility(t))) }
                }
            }
            "why" -> {
                if (selectedTitle != null) item { TasteMediaHero(selectedTitle.item) { onOpenTitle(selectedTitle.item.id) } }
                else item { TasteEmpty("No suggestion available", "Browse your saved titles to inspect another pick.") }
                if (state != null && !state.eligible) item {
                    TasteGroup { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TasteBadge(tasteEligibilityLabel(state))
                        Text(state.reason, style = MaterialTheme.typography.bodyLarge)
                        state.until?.let { TasteKeyValue("Skipped until", tasteDate(it)) }
                        if (selectedSignal?.notInterested == true || (selectedSignal?.suppressedUntil ?: 0L) > profile.generatedAt)
                            FilledTonalButton(onClick = { selectedTitle?.let { onRecommendAgain(it.key) } }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Recommend again") }
                    } }
                }
                if (current != null) {
                    item { TasteSectionTitle("Why it fits") }
                    item { TasteReasons(tasteReasons(current)) }
                    item { TasteScoreCard(ScreeningScoreTrace.from(current)) { detail = "score" } }
                }
                if (latestPublication != null) item {
                    ExpressiveSettingsNavigationItem("Last suggestion", { detail = "publication"; learningDetails = false },
                        subtitle = "${tasteSurface(latestPublication.surface)} · ${tasteDate(latestPublication.at)}",
                        leadingIcon = { TasteIcon(R.drawable.ic_custom_history) })
                }
                item { ExpressiveSettingsNavigationItem("Saved titles", { go("titles") },
                    subtitle = "Check another movie or show", leadingIcon = { TasteIcon(R.drawable.ic_custom_search) }) }
            }
        }
    }

    when (detail) {
        "genre" -> if (genre != null) TasteSheet(genre.label, ::closeDetail) {
            TasteBadge(genre.direction)
            Text("Based on ${genre.titleCount} ${if (genre.titleCount == 1) "title" else "titles"}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val entries = profile.evidence.filter { genre.key in it.genres }
            if (entries.isEmpty()) Text("No feedback for this genre yet.", style = MaterialTheme.typography.bodyMedium)
            else TasteGroup { entries.forEachIndexed { index, e ->
                TasteEvidenceRow(e, titles.find { it.key == e.key }?.item, { showEvidence(e, true) })
                if (index != entries.lastIndex) TasteDivider()
            } }
            TextButton(onClick = { learningDetails = !learningDetails }) { Text("Learning details") }
            if (learningDetails) {
                TasteKeyValue("Learned weight", genre.weight?.let(::tasteSigned) ?: "No estimate")
                TasteKeyValue("Evidence amount", tastePoints(genre.evidenceAmount))
            }
        }
        "evidence" -> if (evidence != null) TasteSheet("Your feedback", ::closeDetail,
            if (parentGenre != null) ({ backToGenre() }) else null) {
            val item = titles.find { it.key == evidence.key }?.item
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item != null) TastePoster(item, 56.dp, 80.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(evidence.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    TasteBadge(tasteEvidenceLabel(evidence))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What this changes", style = MaterialTheme.typography.titleMedium)
                Text(tasteEvidenceEffect(evidence), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TasteGroup { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TasteKeyValue("Source", evidence.origin)
                TasteKeyValue("Recorded", tasteDate(evidence.at))
            } }
            TextButton(onClick = { learningDetails = !learningDetails }) { Text("Learning details") }
            if (learningDetails) {
                TasteKeyValue("Current influence", tasteSigned(evidence.influence))
                TasteKeyValue("Evidence amount", tastePoints(evidence.amount))
                if (evidence.genres.isNotEmpty()) Text(tasteGenreSummary(evidence.genres), style = MaterialTheme.typography.bodyMedium)
            }
            if (evidence.kind == ScreeningEvidenceKind.RATING || evidence.kind == ScreeningEvidenceKind.RESPONSE) {
                val rating = evidence.kind == ScreeningEvidenceKind.RATING
                Button(onClick = { onIgnore(evidence, !evidence.ignored) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (evidence.ignored) if (rating) "Use rating again" else "Use response again"
                        else if (rating) "Ignore rating" else "Ignore response")
                }
            } else if (evidence.active && evidence.kind in setOf(ScreeningEvidenceKind.LIKE, ScreeningEvidenceKind.DISLIKE)) {
                Button(onClick = { onClearReaction(evidence.key) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Clear thumb") }
            }
            evidence.itemId?.let { id -> TextButton(onClick = { onOpenTitle(id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open saved title") } }
        }
        "score" -> if (current != null) TasteSheet("Score breakdown", ::closeDetail) {
            TasteScoreDetails(ScreeningScoreTrace.from(current))
        }
        "publication" -> if (latestPublication != null) TasteSheet("Last suggestion", ::closeDetail) {
            Text(latestPublication.title, style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            latestPublication.reason?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            TasteGroup { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TasteKeyValue("Where", tasteSurface(latestPublication.surface))
                TasteKeyValue("When", tasteDate(latestPublication.at))
                TasteKeyValue("Source", ScreeningSource.entries.firstOrNull { it.name == latestPublication.source }?.label ?: latestPublication.source)
            } }
            Text("This is the explanation saved with that suggestion.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (latestPublication.trace != null) {
                TextButton(onClick = { learningDetails = !learningDetails }) { Text("Score details") }
                if (learningDetails) TasteScoreDetails(latestPublication.trace)
            } else Text("No score details were recorded.", style = MaterialTheme.typography.bodyMedium)
        }
        "help" -> TasteSheet("How your taste works", ::closeDetail) {
            Text("Your ratings, thumbs and Screening responses guide your picks. Every genre stays in your profile; Others keeps the overview tidy.",
                style = MaterialTheme.typography.bodyLarge)
            Text("Opening a title or leaving a question unanswered adds no taste feedback.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TasteGroup { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TasteKeyValue("Taste profile", "Shared across saved media")
                TasteKeyValue("Question history", "Up to 180 days · 500 responses")
                TasteKeyValue("Recorded changes", "Latest 200 changes")
            } }
            Text("Dates are shown only when recorded. Reset clears Screening feedback; your saved ratings and watch progress stay.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (resetDialog) AlertDialog(onDismissRequest = { resetDialog = false }, title = { Text("Reset feedback?") },
        text = { Text("Clear your Screening thumbs and question responses? Your saved ratings and watch progress will stay. Saved ratings will still guide your picks.") },
        confirmButton = { TextButton(onClick = { resetDialog = false; onReset() }, enabled = !busy) { Text("Clear Screening feedback") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Keep feedback") } })
}

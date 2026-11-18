package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SavedItem
import com.example.media.recommendation.*
import com.example.widget.ScreeningSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun TasteSectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
internal fun TasteIcon(icon: Int, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(icon), description, modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun TastePoster(item: SavedItem?, width: Dp = 48.dp, height: Dp = 68.dp) {
    Box(Modifier.size(width, height).clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        TasteIcon(if (item?.mediaType == "movie") R.drawable.ic_custom_movie else R.drawable.ic_custom_video)
        val image = item?.thumbnailPath?.takeIf { it.isNotBlank() } ?: item?.backdropUrl?.takeIf { it.isNotBlank() }
        if (image != null) AsyncImage(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun TasteMediaHero(item: SavedItem, onOpen: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                TastePoster(item, 76.dp, 108.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tasteKind(item), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val facts = listOfNotNull(item.runtime?.takeIf { it > 0 }?.let { "$it min" },
                        item.watchStatusOverride ?: item.watchStatus).filter(String::isNotBlank)
                    if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Open saved title")
            }
        }
    }
}

@Composable
internal fun TasteMediaRow(item: SavedItem, subtitle: String, onClick: () -> Unit, badge: String? = null,
    modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        TastePoster(item)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (badge != null) TasteBadge(badge)
        }
        Icon(painterResource(R.drawable.ic_custom_chevron_right), null, Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TasteBadge(label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun TasteGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun TasteDivider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))

@Composable
internal fun TasteEmpty(title: String, message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    TasteGroup {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TasteIcon(R.drawable.ic_custom_ai_brain, Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) FilledTonalButton(onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

@Composable
internal fun TasteGenreGroup(genres: List<ScreeningGenreTaste>, onGenre: (String) -> Unit) {
    TasteGroup {
        genres.forEachIndexed { index, genre ->
            val colors = MaterialTheme.colorScheme
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onGenre(genre.key) }
                .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(genre.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(genre.direction, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Text(if (genre.titleCount == 0) "No evidence" else "${genre.titleCount} ${if (genre.titleCount == 1) "title" else "titles"}",
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Icon(painterResource(R.drawable.ic_custom_chevron_right), null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
                Canvas(Modifier.fillMaxWidth().height(8.dp).semantics {
                    contentDescription = "${genre.label}: ${genre.direction}, ${genre.titleCount} contributing titles"
                }) {
                    val center = size.width / 2
                    drawLine(colors.outlineVariant.copy(alpha = .45f), Offset(0f, size.height / 2),
                        Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
                    val weight = genre.weight?.coerceIn(-1.0, 1.0)
                    if (weight != null && weight != 0.0) drawLine(colors.primary, Offset(center, size.height / 2),
                        Offset(center + (center * weight).toFloat(), size.height / 2), size.height, StrokeCap.Round)
                    drawLine(colors.onSurfaceVariant, Offset(center, 0f), Offset(center, size.height), 1.dp.toPx())
                }
            }
            if (index != genres.lastIndex) TasteDivider()
        }
    }
}

@Composable
internal fun TasteEvidenceRow(evidence: ScreeningEvidence, item: SavedItem?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("taste_evidence_${evidence.id}").heightIn(min = 88.dp)
        .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (item != null) TastePoster(item, 42.dp, 60.dp)
        else TasteIcon(R.drawable.ic_custom_history, Modifier.size(32.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(evidence.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(tasteEvidenceLabel(evidence), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (evidence.ignored || !evidence.active) Text(if (evidence.ignored) "Ignored" else "Removed or replaced",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_custom_chevron_right), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TasteHistoryRow(change: ScreeningHistoryChange) {
    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center) { TasteIcon(R.drawable.ic_custom_history) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(change.title ?: "Screening", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(tasteHistoryLabel(change), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tasteDate(change.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal data class TasteReason(val icon: Int, val title: String, val detail: String? = null)

internal fun tasteReasons(pick: ScreeningRecommendation): List<TasteReason> {
    val reasons = mutableListOf<TasteReason>()
    val f = pick.features
    when {
        (f["near_finish"] ?: 0.0) > 0 -> reasons += TasteReason(R.drawable.ic_custom_check, "Close to finishing", pick.reason)
        (f["continue"] ?: 0.0) > 0 -> reasons += TasteReason(R.drawable.ic_custom_play, "Continue watching", pick.reason)
        (f["rediscovery"] ?: 0.0) > 0 -> reasons += TasteReason(R.drawable.ic_custom_history, "Rediscover a saved title", "Saved ${tasteDate(pick.item.timestamp)}")
        else -> reasons += TasteReason(R.drawable.ic_custom_bookmark, "From your watchlist")
    }
    if ((f["title_preference"] ?: 0.0) > 0) reasons += TasteReason(R.drawable.ic_custom_helpful, "You liked this title")
    if ((f["preference"] ?: 0.0) >= 1) reasons += TasteReason(R.drawable.ic_custom_heart, "Matches your genre feedback")
    if ((f["short_pick"] ?: 0.0) > 0) reasons += TasteReason(R.drawable.ic_custom_movie, "A shorter movie", "${pick.item.runtime} minutes")
    return reasons.take(3)
}

@Composable
internal fun TasteReasons(reasons: List<TasteReason>) {
    TasteGroup {
        reasons.forEachIndexed { index, reason ->
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center) { TasteIcon(reason.icon) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(reason.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (reason.detail != null) Text(reason.detail, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (index != reasons.lastIndex) TasteDivider()
        }
    }
}

@Composable
internal fun TasteScoreCard(trace: ScreeningScoreTrace, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ranking points", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Score details", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(tastePoints(trace.rawPoints), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Icon(painterResource(R.drawable.ic_custom_chevron_right), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasteSheet(title: String, onDismiss: () -> Unit, onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().testTag("taste_detail_sheet").verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { TasteIcon(R.drawable.ic_custom_back, description = "Back to genre") }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onDismiss) { TasteIcon(R.drawable.ic_custom_close, description = "Close details") }
            }
            content()
        }
    }
}

@Composable
internal fun TasteKeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun TasteScoreDetails(trace: ScreeningScoreTrace) {
    Text("Ranking points compare your saved titles. They are not an enjoyment percentage.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TasteGroup {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val names = mapOf("base" to "Starting points", "near_finish" to "Close to finishing", "continue" to "Continue watching",
                "rediscovery" to "Rediscovery", "watchlist" to "Saved watchlist", "recent_progress" to "Recent progress",
                "preference" to "Genre feedback", "title_preference" to "Your thumb", "age" to "Time since saving",
                "fatigue" to "Previous suggestions", "short_pick" to "Shorter movie")
            trace.features.filterValues { abs(it) > .000001 }.forEach { (key, value) ->
                TasteKeyValue(names[key] ?: key, tasteSigned(value))
            }
            HorizontalDivider()
            TasteKeyValue("Ranking points", tastePoints(trace.rawPoints))
            TasteKeyValue("Variety adjustment", tasteSigned(trace.varietyAdjustment))
            TasteKeyValue("Selection points", tastePoints(trace.selectionPoints))
        }
    }
    Text(if (trace.shortlist.isEmpty()) "No new weighted choice is recorded for this calculation."
        else "${trace.shortlist.size} titles in the weighted shortlist, from ${trace.poolSize} eligible titles.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Calculated ${tasteDate(trace.generatedAt)} · values rounded for display",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun TasteSavedTitles(items: List<Pair<ScreeningCanonicalTitle, ScreeningEligibility>>, source: ScreeningSource,
    search: String, onSearch: (String) -> Unit, filter: String, onFilter: (String) -> Unit,
    onBack: () -> Unit, onSelect: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val filtered = items.filter { (title, state) ->
        title.item.title.contains(search.trim(), ignoreCase = true) &&
            (filter == "all" || if (filter == "ready") state.eligible else !state.eligible)
    }
    Scaffold(topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)) {
                TasteIcon(R.drawable.ic_custom_back, description = "Back")
            }
            val color = MaterialTheme.colorScheme
            BasicTextField(search, onSearch, modifier = Modifier.weight(1f).testTag("taste_title_search")
                .semantics { contentDescription = "Search saved titles" }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = color.onSurface), cursorBrush = SolidColor(color.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                decorationBox = { field ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(color.surfaceContainerHigh, RoundedCornerShape(16.dp))
                        .padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TasteIcon(R.drawable.ic_custom_search)
                        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            if (search.isEmpty()) Text("Search saved titles", style = MaterialTheme.typography.bodyLarge, color = color.onSurfaceVariant)
                            field()
                        }
                        if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) { TasteIcon(R.drawable.ic_custom_close, description = "Clear search") }
                    }
                })
        }
    }) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset).imePadding().testTag("taste_saved_titles"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saved titles", style = MaterialTheme.typography.headlineSmall)
                    Text("${filtered.size} ${if (filtered.size == 1) "title" else "titles"} · ${source.label}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("all" to "All", "ready" to "Ready to suggest", "excluded" to "Not suggested")) { (key, label) ->
                        FilterChip(selected = filter == key, onClick = { onFilter(key) }, label = { Text(label) })
                    }
                }
            }
            if (filtered.isEmpty()) item {
                TasteEmpty(if (search.isBlank()) "No titles here" else "No matching titles",
                    if (search.isBlank()) "Try another filter or save a movie or show." else "Try another title or clear your search.",
                    if (search.isNotBlank()) "Clear search" else "Show all titles", { onSearch(""); onFilter("all") })
            }
            items(filtered, key = { it.first.key }) { (title, state) ->
                TasteGroup { TasteMediaRow(title.item, tasteKind(title.item), { focus.clearFocus(); onSelect(title.item.id) }, tasteEligibilityLabel(state)) }
            }
        }
    }
}

internal fun tasteKind(item: SavedItem): String = when (ScreeningRecommendationEngine.kind(item)) {
    "movie" -> "Movie"; "tv" -> "TV show"; else -> item.mediaType?.replaceFirstChar { it.titlecase() } ?: "Saved media"
}

internal fun tasteEligibilityLabel(state: ScreeningEligibility): String = when {
    state.eligible -> "Ready to suggest"
    state.reason.startsWith("Already") -> "Finished"
    state.reason.startsWith("Hidden") -> "Hidden"
    state.reason.startsWith("Temporarily") -> "Skipped"
    state.reason.startsWith("Caught up") -> "Caught up"
    state.reason.startsWith("Outside") -> "Other source"
    state.reason == "Title unavailable" -> "Unavailable"
    state.reason.contains("not supported") -> "Unsupported"
    state.reason.startsWith("No released") -> "Not released"
    else -> "Not suggested"
}

internal fun tasteEvidenceLabel(e: ScreeningEvidence): String = when (e.kind) {
    ScreeningEvidenceKind.RATING -> e.value
    ScreeningEvidenceKind.COMPLETION -> "Marked finished"
    ScreeningEvidenceKind.LIKE -> if (e.active) "Liked" else "Thumb cleared"
    ScreeningEvidenceKind.DISLIKE -> if (e.active) "Not for me" else "Thumb cleared"
    ScreeningEvidenceKind.RESPONSE -> e.value
}

internal fun tasteEvidenceEffect(e: ScreeningEvidence): String = when {
    e.ignored -> "This feedback is saved, but excluded from recommendation learning."
    !e.active -> "This feedback was removed or replaced. It has no current influence."
    e.feedback == ScreeningFeedback.NOT_INTERESTED -> "Hides this title from future picks."
    e.feedback == ScreeningFeedback.NOT_NOW -> "Skips this title for seven days without changing genre taste."
    e.kind == ScreeningEvidenceKind.LIKE -> if (e.genres.isEmpty()) "Raises this title's priority. Genre information is not recorded yet."
        else "Raises this title's priority and guides your genre preferences."
    e.kind == ScreeningEvidenceKind.DISLIKE -> if (e.genres.isEmpty()) "Lowers this title's priority. Genre information is not recorded yet."
        else "Lowers this title's priority and guides your genre preferences."
    e.genres.isEmpty() -> "Genre information is not recorded for this title yet."
    e.contributes -> "Guides how often Cobalt suggests ${tasteGenreSummary(e.genres)}."
    else -> "Adds no genre preference."
}

internal fun tasteHistoryLabel(c: ScreeningHistoryChange): String = when (c.action) {
    "LIKED" -> "Liked"; "DISLIKED" -> "Not for me"; "CLEAR" -> "Thumb cleared"; "IGNORE" -> "Feedback ignored"
    "RESTORE" -> "Feedback restored"; "ALLOW" -> "Title allowed again"; "RESET" -> "Feedback reset"
    "RATING_OBSERVED" -> "Rating updated"; "USEFUL" -> "Good pick"; "NOT_INTERESTED" -> "Not interested"
    "NOT_NOW" -> "Maybe later"; "STILL_WATCHING" -> "Still watching"; "PAUSED" -> "Paused"; "WATCHED" -> "Finished"
    else -> "Feedback updated"
}

internal fun tastePoints(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
internal fun tasteSigned(value: Double): String = String.format(Locale.getDefault(), "%+.2f", if (value == 0.0) 0.0 else value)
internal fun tasteDate(at: Long?): String = if (at == null || at <= 0) "Date not recorded" else
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
internal fun tasteSurface(value: String): String = if (value.startsWith("Widget ")) "Home screen card" else value
internal fun tasteGenreSummary(values: List<String>): String = values.take(3).joinToString { ScreeningGenres.label(it) } +
    if (values.size > 3) " +${values.size - 3} others" else ""

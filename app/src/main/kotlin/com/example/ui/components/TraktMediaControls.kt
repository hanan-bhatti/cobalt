package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.TraktEpisodeEntity
import com.example.data.model.SavedItem
import com.example.data.model.TvSeasonProgress
import com.example.data.model.TvShowProgress
import com.example.data.remote.trakt.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

internal fun SavedItem.hasTraktIdentity() = mediaType in listOf("movie", "tv") &&
    ((traktId ?: 0) > 0 || (tmdbId ?: 0) > 0 || !imdbId.isNullOrBlank())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraktMediaControls(item: SavedItem, notify: (String) -> Unit) {
    if (!item.hasTraktIdentity()) return
    val context = LocalContext.current
    val auth = remember(context) { TraktAuthManager.getInstance(context) }
    val connected by auth.isConnected.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember(item.id) { mutableStateOf(false) }
    var choice by remember(item.id, item.userRating) { mutableIntStateOf(item.userRating ?: 0) }
    var season by rememberSaveable(item.id) { mutableStateOf<Int?>(null) }
    fun change(action: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        scope.launch {
            try { action().getOrThrow(); notify(if (connected) "Saved locally. Trakt sync queued." else "Saved on this device.") }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notify("This change could not be saved. Please retry.") }
            finally { busy = false }
        }
    }
    MediaActivityContent(item.copy(userRating = choice.takeIf { it > 0 }), busy,
        onRate = { choice = it }, onRatingCommit = { score -> change { TraktSyncEngine.rateItem(context, item, score) } },
        onWatchlist = { change { TraktSyncEngine.toggleWatchlist(context, item, !item.inWatchlist) } },
        onSeason = { season = it }, onStatus = { status -> change { TraktSyncEngine.setWatchStatus(context, item, status) } })
    season?.let { number ->
        TraktEpisodeSheet(item, number, onSeason = { season = it }, onDismiss = { season = null })
    }
}

/** One frameless activity area; the poster's status is the only watch-status label. */
@Composable
internal fun MediaActivityContent(item: SavedItem, busy: Boolean, onRate: (Int) -> Unit,
    onWatchlist: () -> Unit, onSeason: (Int) -> Unit, onStatus: (String) -> Unit, onRatingCommit: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your rating", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.userRating?.let { "${it / 2f} / 5" } ?: "Not rated", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.userRating != null) IconButton(onClick = { onRate(0); onRatingCommit(0) }, enabled = !busy) {
                    Icon(painterResource(R.drawable.ic_dock_close), "Clear rating", Modifier.size(16.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StarRatingInput(item.userRating ?: 0, { if (!busy) onRate(it) }, onCommit = { if (!busy) onRatingCommit(it) })
        }
        FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("Plan to Watch", "Watching", "Completed", "Dropped").forEach { status ->
                FilterChip(selected = item.watchStatus == status, onClick = { if (!busy && item.watchStatus != status) onStatus(status) },
                    modifier = Modifier.weight(1f), enabled = !busy, shape = RoundedCornerShape(20.dp), label = { Text(status) })
            }
        }
        TextButton(onClick = onWatchlist, enabled = !busy, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(painterResource(if (item.inWatchlist) R.drawable.ic_custom_bookmark_filled else R.drawable.ic_custom_bookmark),
                null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (item.inWatchlist) "Remove from watchlist" else "Add to watchlist")
        }
        item.showProgress?.let { progress ->
            if (progress.totalAired > 0 || progress.seasons.isNotEmpty()) {
                MediaWatchProgress(progress)
                if (progress.seasons.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(progress.seasons.sortedBy { it.seasonNumber }, key = { it.seasonNumber }) { season ->
                        AssistChip(onClick = { onSeason(season.seasonNumber) }, label = {
                            Text("${seasonLabel(season.seasonNumber)} · ${season.watchedEpisodes}/${season.totalEpisodes}")
                        })
                    }
                }
            }
        }
        if (item.mediaType == "tv" && item.traktId != null && item.showProgress?.seasons.isNullOrEmpty()) {
            TextButton(onClick = { onSeason(1) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Seasons & episodes") }
        }
    }
}

internal fun seasonLabel(number: Int) = if (number == 0) "Specials" else "Season $number"

@Composable
internal fun MediaWatchProgress(progress: TvShowProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Watch progress", style = MaterialTheme.typography.titleSmall)
            Text("${progress.progressPercent}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(progress = { progress.progressFraction }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
        Text("${progress.totalWatched} of ${progress.totalAired} episodes watched",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TraktEpisodeSheet(item: SavedItem, number: Int, onSeason: (Int) -> Unit, onDismiss: () -> Unit) {
    val seasons = remember(item.showProgress?.seasons, number) {
        (item.showProgress?.seasons.orEmpty().map { it.seasonNumber } + number).distinct().sorted()
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surface) {
        SeasonPagerContent(seasons, number, onSeason) { selected, active, select ->
            SeasonEpisodePage(item, selected, active, select, onDismiss)
        }
    }
}

@Composable
internal fun SeasonPagerContent(seasons: List<Int>, initial: Int, onChanged: (Int) -> Unit = {},
    content: @Composable (Int, Boolean, (Int) -> Unit) -> Unit) {
    if (seasons.isEmpty()) { Text("No seasons available"); return }
    key(seasons) {
        val pager = rememberPagerState(initialPage = seasons.indexOf(initial).coerceAtLeast(0), pageCount = { seasons.size })
        val scope = rememberCoroutineScope()
        LaunchedEffect(pager.settledPage) { onChanged(seasons[pager.settledPage.coerceIn(seasons.indices)]) }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = pager.currentPage > 0, onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }) {
                    Icon(painterResource(R.drawable.ic_custom_arrow_right), "Previous season", Modifier.size(20.dp).graphicsLayer { rotationZ = 180f })
                }
                Text("${pager.currentPage + 1} / ${seasons.size}", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium)
                IconButton(enabled = pager.currentPage < seasons.lastIndex, onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(seasons.lastIndex)) } }) {
                    Icon(painterResource(R.drawable.ic_custom_arrow_right), "Next season", Modifier.size(20.dp))
                }
            }
            HorizontalPager(state = pager, key = { seasons[it] }, modifier = Modifier.weight(1f)) { page ->
                content(seasons[page], page == pager.currentPage) { selected ->
                    seasons.indexOf(selected).takeIf { it >= 0 }?.let { scope.launch { pager.animateScrollToPage(it) } }
                }
            }
        }
    }
}

@Composable
private fun SeasonEpisodePage(item: SavedItem, number: Int, active: Boolean, onSeason: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dao = remember(context) { AppDatabase.getDatabase(context).traktEpisodeDao() }
    val rows by remember(item.id, number) { dao.observeSeason(item.id, number) }.collectAsState(initial = emptyList())
    var error by remember(item.id, number) { mutableStateOf<String?>(null) }
    var loading by remember(item.id, number) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load(refresh: Boolean = false) {
        loading = true; error = null
        try { error = TraktEpisodeCatalog.load(context, item, number, refresh) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = "Couldn’t refresh episodes. Your saved information is still available." }
        finally { loading = false }
    }
    LaunchedEffect(item.id, number, active) { if (active) load() }
    SeasonEpisodesContent(item, number, rows, loading, error, onSeason, onRefresh = { scope.launch { load(true) } }, onDismiss)
}

@Composable
internal fun SeasonEpisodesContent(item: SavedItem, number: Int, rows: List<TraktEpisodeEntity>, loading: Boolean,
    error: String?, onSeason: (Int) -> Unit, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    val season = item.showProgress?.seasons?.firstOrNull { it.seasonNumber == number }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(seasonLabel(number), style = MaterialTheme.typography.headlineSmall)
                Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ic_dock_close), "Close episodes", Modifier.size(20.dp)) }
        }
        if (!season?.title.isNullOrBlank() && season?.title != seasonLabel(number)) Text(season!!.title!!,
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        val total = season?.totalEpisodes?.takeIf { it > 0 } ?: rows.size
        Text("${season?.watchedEpisodes ?: 0} of $total watched", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        LinearProgressIndicator(progress = { season?.progressFraction ?: 0f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(item.showProgress?.seasons.orEmpty().sortedBy { it.seasonNumber }, key = { it.seasonNumber }) { entry ->
                    FilterChip(selected = entry.seasonNumber == number, onClick = { onSeason(entry.seasonNumber) },
                        label = { Text(seasonLabel(entry.seasonNumber)) })
                }
            }
            TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (rows.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        if (loading && rows.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(rows, key = { it.number }) { episode -> SeasonEpisodeRow(episode, season) }
            if (!loading && rows.isEmpty()) item { Text("No episode information is available yet.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
internal fun SeasonEpisodeRow(episode: TraktEpisodeEntity, season: TvSeasonProgress?) {
    val progress = season?.episodes?.firstOrNull { it.episodeNumber == episode.number }
    val watched = progress?.isWatched == true
    val playback = episode.playbackProgress?.takeIf { it.isFinite() && it > 0 && it <= 100 }
    val completion = playback?.let { (it / 100).toFloat() } ?: if (watched) 1f else null
    val thumbnail = episode.thumbnailUrl ?: progress?.stillPath
    val date = remember(episode.airDate) { try {
        episode.airDate?.take(10)?.let { java.time.LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) }
    } catch (_: Exception) { null } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(width = 96.dp, height = 60.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (!thumbnail.isNullOrBlank()) AsyncImage(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("E${episode.number.toString().padStart(2, '0')}", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            episode.runtime?.takeIf { it > 0 }?.let { duration ->
                Surface(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                    Text("$duration min", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(episode.title?.takeIf { it.isNotBlank() } ?: "Episode ${episode.number}", style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull("E${episode.number}", date)
                .joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (completion != null) {
                Text(if (playback != null) "${if (watched) "Watching again" else "In progress"} · ${playback.toInt()}%" else "Watched",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(progress = { completion }, modifier = Modifier.fillMaxWidth().height(3.dp))
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

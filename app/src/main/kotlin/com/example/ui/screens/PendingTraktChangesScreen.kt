package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AppDatabase
import com.example.data.local.TraktOutboxEntity
import com.example.data.remote.trakt.TraktAuthManager
import com.example.data.remote.trakt.TraktSyncEngine
import com.example.data.remote.trakt.TraktSyncStatus
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.TraktSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

internal fun pendingChangeLabel(action: String) = when (action) {
    "watchlist:add" -> "Add to watchlist"
    "watchlist:remove" -> "Remove from watchlist"
    "rating:add" -> "Update personal rating"
    "rating:remove" -> "Clear personal rating"
    "history:add" -> "Add watched history"
    "history:remove" -> "Remove watched history"
    else -> "Saved change"
}

@Composable
fun PendingTraktChangesScreen(viewModel: CobaltViewModel, onBack: () -> Unit, onAccount: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { AppDatabase.getDatabase(context) }
    val auth = remember(context) { TraktAuthManager.getInstance(context) }
    val connected by auth.isConnected.collectAsState()
    val session = remember(connected) { auth.syncSessionId() }
    val rows by remember(session, connected) {
        if (connected) db.traktOutboxDao().observePending(session) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val total by remember(session, connected) {
        if (connected) db.traktOutboxDao().observePendingCount(session) else flowOf(0)
    }.collectAsState(initial = 0)
    val library by viewModel.allItems.collectAsState()
    val titles = remember(library) { library.associateBy { it.id } }
    val status by TraktSyncEngine.syncStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var retrying by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val header = rememberCollapsibleTopBarState(list, maxHeight = 160.dp)
    val height = with(LocalDensity.current) { header.heightPx.toDp() }
    fun paused(row: TraktOutboxEntity): Boolean = when {
        row.action.startsWith("rating:") -> !auth.isSyncRatingsEnabled()
        row.action.startsWith("watchlist:") -> !auth.isSyncWatchlistEnabled()
        row.payload.contains("\"movies\":") -> !auth.isSyncWatchedEnabled()
        else -> !auth.isSyncTvProgressEnabled()
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(header.nestedScrollConnection)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = height + 12.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (!connected) "Connect Trakt to sync" else if (total == 0) "All changes delivered" else "$total pending changes", style = MaterialTheme.typography.titleLarge)
                        Text(if (!connected) "Your movie and TV choices are saved locally. Connecting also syncs titles saved before sign-in."
                            else "Changes stay on this device until Trakt accepts them. Paused categories wait until you enable them in account settings.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        (failure ?: (status as? TraktSyncStatus.Error)?.errorMessage)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        (status as? TraktSyncStatus.Syncing)?.let {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(it.message, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!connected) Button(onClick = onAccount, modifier = Modifier.fillMaxWidth()) { Text("Connect account") }
                        else {
                            Button(onClick = {
                                retrying = true
                                failure = null
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            db.traktOutboxDao().retryNow(session)
                                            TraktSyncWorker.enqueueOneTimeSync(context, uploadOnly = true)
                                        }
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { failure = "Could not schedule sync. Your changes are still saved; please retry." }
                                    finally { retrying = false }
                                }
                            }, enabled = total > 0 && !retrying && status !is TraktSyncStatus.Syncing, modifier = Modifier.fillMaxWidth()) { Text("Retry now") }
                            TextButton(onClick = onAccount, modifier = Modifier.fillMaxWidth()) { Text("Manage sync categories") }
                        }
                    }
                }
            }
            items(rows, key = { it.key }) { row ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(titles[row.itemId]?.title ?: "Saved title", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(pendingChangeLabel(row.action), style = MaterialTheme.typography.bodyMedium)
                        val state = when {
                            paused(row) -> "Category paused"
                            row.created > System.currentTimeMillis() -> "Waiting for retry · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(row.created))}"
                            else -> "Queued · requires a connection to Trakt"
                        }
                        Text(state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (total > rows.size) item { Text("Showing the first ${rows.size} changes. The rest will appear as these are delivered.", style = MaterialTheme.typography.bodySmall) }
        }
        CollapsibleCommonTopBar(title = "Pending Trakt changes", subtitle = "Outgoing movie & TV activity",
            collapseFraction = header.collapseFraction, headerHeight = height, onBackClick = onBack)
    }
}

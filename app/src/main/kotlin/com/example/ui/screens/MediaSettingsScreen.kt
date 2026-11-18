package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.media.MediaProviderSettings
import com.example.ui.components.*

/** Shared entry for catalogue search and account sync. Uses the existing settings components. */
@Composable
fun MediaSettingsScreen(onBack: () -> Unit, onTrakt: () -> Unit, onTmdb: () -> Unit, onPending: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { MediaProviderSettings(context) }
    val list = rememberLazyListState()
    val header = rememberCollapsibleTopBarState(list, maxHeight = 160.dp)
    val height = with(LocalDensity.current) { header.heightPx.toDp() }
    var priority by remember { mutableStateOf(settings.priority) }
    var manga by remember { mutableStateOf(settings.mangaEnabled) }
    val db = remember(context) { com.example.data.local.AppDatabase.getDatabase(context) }
    val ignored by remember(db) { db.mediaTombstoneDao().observeCount() }.collectAsState(initial = 0)
    val auth = remember(context) { com.example.data.remote.trakt.TraktAuthManager.getInstance(context) }
    val connected by auth.isConnected.collectAsState()
    val pending by remember(connected) { if (connected) db.traktOutboxDao().observePendingCount(auth.syncSessionId()) else kotlinx.coroutines.flow.flowOf(0) }.collectAsState(initial = 0)
    val scope = rememberCoroutineScope()
    var resetIgnored by remember { mutableStateOf(false) }
    if (resetIgnored) ExpressiveAlertDialog(onDismissRequest = { resetIgnored = false }, title = "Allow removed titles to return?",
        text = { Text("Titles removed in Cobalt may be imported again during Trakt sync. This clears the list used to keep them out. Nothing is deleted from your Trakt account.") },
        confirmButtonText = "Allow titles to return", onConfirm = { scope.launch { db.mediaTombstoneDao().clearAll() }; resetIgnored = false },
        dismissButtonText = "Cancel", onDismiss = { resetIgnored = false })
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().nestedScroll(header.nestedScrollConnection)) {
        LazyColumn(state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
            top = height + 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                SettingsSubsectionHeader(title = "Accounts & API keys", description = "Connect Trakt and set up movie and TV details.")
                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem("Trakt account", onTrakt, subtitle = if (connected) "Connected · watched history, watchlist and ratings" else "Connect to sync movies and TV shows with Trakt", shape = connectedPillShape(0, 2))
                    ExpressiveSettingsNavigationItem("TMDB API key", onTmdb, subtitle = "Set up posters, trailers and movie and TV details", shape = connectedPillShape(1, 2))
                }
            }
            item {
                SettingsSubsectionHeader(title = "Search sources", description = "Choose sources for search in Capture, Media Hub and Side Dock.")
                ExpressiveSettingsGroup {
                    listOf("trakt" to "Trakt", "tmdb" to "TMDB", "jikan" to "Jikan").forEachIndexed { index, (key, title) ->
                        var enabled by remember(key) { mutableStateOf(settings.enabled(key)) }
                        ExpressiveSettingsSwitchItem(title = title, subtitle = when(key) {
                            "trakt" -> "Search movies and TV shows · requires Trakt connection"
                            "tmdb" -> "Search movies and TV shows · requires TMDB API key"
                            else -> "Search anime and manga · no account or API key needed"
                        }, checked = enabled, onCheckedChange = { enabled = it; settings.setEnabled(key, it) }, shape = connectedPillShape(index, 3))
                    }
                }
                Spacer(Modifier.height(12.dp))
                ExpressiveSettingsSelectorItem(title = "Preferred movie & TV source",
                    options = linkedMapOf("trakt" to "Trakt first", "tmdb" to "TMDB first"), selectedKey = priority,
                    onSelectionChanged = { priority = it; settings.priority = it },
                    description = "Prefer details from this source when Trakt and TMDB find the same title.")
            }
            item {
                SettingsSubsectionHeader(title = "Anime & manga", description = "Search anime and manga through Jikan. Reading progress stays on this device.")
                ExpressiveSettingsSwitchItem(title = "Include manga in search", subtitle = "Show manga results alongside anime when Jikan is enabled",
                    checked = manga, onCheckedChange = { manga = it; settings.mangaEnabled = it })
                Spacer(Modifier.height(12.dp))
                JikanServerSettings(settings)
            }
            item {
                SettingsSubsectionHeader(title = "Sync & removed titles", description = "Manage pending Trakt changes and titles removed from Cobalt.")
                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem("Pending sync changes", onPending, subtitle = "Review changes waiting to sync with Trakt", trailingBadge = pending.toString(), shape = connectedPillShape(0, 2))
                    ExpressiveSettingsNavigationItem("Allow removed titles to return", { resetIgnored = true }, subtitle = "Clear the list that keeps removed titles out of imports", trailingBadge = ignored.toString(), enabled = ignored > 0, shape = connectedPillShape(1, 2))
                }
            }
        }
        CollapsibleCommonTopBar(title = "Media & accounts", subtitle = "Search, accounts and sync",
            collapseFraction = header.collapseFraction, headerHeight = height, onBackClick = onBack)
    }
}

@Composable
internal fun JikanServerSettings(settings: MediaProviderSettings) {
    var endpoint by rememberSaveable { mutableStateOf(settings.jikanUrl) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val requester = remember { BringIntoViewRequester() }
    val keyboardBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, keyboardBottom) { if (focused) requester.bringIntoView() }
    fun save() {
        try {
            settings.jikanUrl = endpoint
            endpoint = settings.jikanUrl
            saved = true
            error = null
            focus.clearFocus()
        } catch (failure: IllegalArgumentException) { error = failure.message }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Anime & manga server", style = MaterialTheme.typography.titleMedium)
            Text("Use the public Jikan server or enter your own Jikan v4 API address. No API key is needed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it; error = null; saved = false },
                label = { Text("Server URL") }, placeholder = { Text(MediaProviderSettings.DEFAULT_JIKAN) },
                supportingText = { Text(error ?: if (saved) "Server address saved" else "Use a Jikan v4 API address, including /v4/.") },
                isError = error != null, singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused })
            Button(onClick = { save() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save address") }
            TextButton(onClick = { endpoint = MediaProviderSettings.DEFAULT_JIKAN; save() }, modifier = Modifier.fillMaxWidth()) { Text("Use default server") }
        }
    }
}

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
    if (resetIgnored) ExpressiveAlertDialog(onDismissRequest = { resetIgnored = false }, title = "Allow deleted titles to return?",
        text = { Text("Cobalt remembers only provider IDs of locally deleted titles so background sync won’t recreate them. Resetting allows Trakt to import them again. Nothing is deleted from your Trakt account.") },
        confirmButtonText = "Reset ignored titles", onConfirm = { scope.launch { db.mediaTombstoneDao().clearAll() }; resetIgnored = false },
        dismissButtonText = "Cancel", onDismiss = { resetIgnored = false })
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().nestedScroll(header.nestedScrollConnection)) {
        LazyColumn(state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
            top = height + 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                SettingsSubsectionHeader(title = "Connections", description = "Manage your Trakt account and movie metadata.")
                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem("Trakt account & sync", onTrakt, subtitle = if (connected) "Connected · history, watchlist and personal ratings" else "Connect to sync your saved movies and TV", shape = connectedPillShape(0, 2))
                    ExpressiveSettingsNavigationItem("TMDB API key", onTmdb, subtitle = "Posters, trailers and movie or TV details", shape = connectedPillShape(1, 2))
                }
            }
            item {
                SettingsSubsectionHeader(title = "Search sources", description = "Applies to media search in Capture, the Media Hub and Side Dock.")
                ExpressiveSettingsGroup {
                    listOf("trakt" to "Trakt", "tmdb" to "TMDB", "jikan" to "Jikan").forEachIndexed { index, (key, title) ->
                        var enabled by remember(key) { mutableStateOf(settings.enabled(key)) }
                        ExpressiveSettingsSwitchItem(title = title, subtitle = when(key) {
                            "trakt" -> "Movies and TV; requires a connected account"
                            "tmdb" -> "Movies and TV; uses your API key"
                            else -> "Anime and optional manga; no account required"
                        }, checked = enabled, onCheckedChange = { enabled = it; settings.setEnabled(key, it) }, shape = connectedPillShape(index, 3))
                    }
                }
            }
            item { ExpressiveSettingsSelectorItem(title = "Movie & TV metadata priority",
                options = linkedMapOf("trakt" to "Trakt first", "tmdb" to "TMDB first"), selectedKey = priority,
                onSelectionChanged = { priority = it; settings.priority = it },
                description = "Choose which source leads when both find the same title.") }
            item {
                SettingsSubsectionHeader(title = "Anime & manga", description = "Jikan provides catalogue details. Reading progress stays on this device.")
                ExpressiveSettingsSwitchItem(title = "Include manga", subtitle = "Save chapter, volume and publication details separately from anime",
                    checked = manga, onCheckedChange = { manga = it; settings.mangaEnabled = it })
            }
            item { JikanServerSettings(settings) }
            item {
                SettingsSubsectionHeader(title = "Sync & local archive", description = "Review outgoing changes and titles you removed locally.")
                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem("Pending Trakt changes", onPending, subtitle = "Review queued titles, paused categories and retries", trailingBadge = pending.toString(), shape = connectedPillShape(0, 2))
                    ExpressiveSettingsNavigationItem("Ignored deleted titles", { resetIgnored = true }, subtitle = "Keeps local deletions from returning during imports", trailingBadge = ignored.toString(), enabled = ignored > 0, shape = connectedPillShape(1, 2))
                }
            }
        }
        CollapsibleCommonTopBar(title = "Media & Providers", subtitle = "Search providers, metadata & sync",
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
            Text("Jikan server", style = MaterialTheme.typography.titleMedium)
            Text("The public server works without a key. You can also use your own Jikan v4 instance.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it; error = null; saved = false },
                label = { Text("API URL") }, placeholder = { Text(MediaProviderSettings.DEFAULT_JIKAN) },
                supportingText = { Text(error ?: if (saved) "Server saved" else "Include /v4/ in your server address.") },
                isError = error != null, singleLine = true, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused })
            Button(onClick = { save() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save server") }
            TextButton(onClick = { endpoint = MediaProviderSettings.DEFAULT_JIKAN; save() }, modifier = Modifier.fillMaxWidth()) { Text("Use public server") }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.media.MediaProviderSettings
import com.example.ui.components.*

/** Shared entry for catalogue search and account sync. Uses the existing settings components. */
@Composable
fun MediaSettingsScreen(onBack: () -> Unit, onTrakt: () -> Unit, onTmdb: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { MediaProviderSettings(context) }
    val list = rememberLazyListState()
    val header = rememberCollapsibleTopBarState(list, maxHeight = 160.dp)
    val height = with(LocalDensity.current) { header.heightPx.toDp() }
    var priority by remember { mutableStateOf(settings.priority) }
    var manga by remember { mutableStateOf(settings.mangaEnabled) }
    var endpoint by remember { mutableStateOf(settings.jikanUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
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
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(header.nestedScrollConnection)) {
        LazyColumn(state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
            top = height + 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                SettingsSubsectionHeader(title = "Accounts & metadata", description = "Your archive stays on this device. Enable the providers you want to use.")
                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem("Trakt account & sync", onTrakt, subtitle = "Sign in, choose sync categories and review results", shape = connectedPillShape(0, 2))
                    ExpressiveSettingsNavigationItem("TMDB API key", onTmdb, subtitle = "Posters, trailers and movie or TV details", shape = connectedPillShape(1, 2))
                }
            }
            item {
                SettingsSubsectionHeader(title = "Local sync data", description = "Keep local deletes and pending changes under your control.")
                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem("Pending Trakt changes", onTrakt, subtitle = "Kept on this device until Trakt accepts them", trailingBadge = pending.toString(), shape = connectedPillShape(0, 2))
                    ExpressiveSettingsNavigationItem("Ignored deleted titles", { resetIgnored = true }, subtitle = "Stops removed titles returning during background imports", trailingBadge = ignored.toString(), enabled = ignored > 0, shape = connectedPillShape(1, 2))
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
            item { ExpressiveSettingsSelectorItem(title = "Preferred metadata source",
                options = linkedMapOf("trakt" to "Trakt first", "tmdb" to "TMDB first"), selectedKey = priority,
                onSelectionChanged = { priority = it; settings.priority = it },
                description = "Matching titles share their provider IDs instead of creating duplicate cards.") }
            item {
                SettingsSubsectionHeader(title = "Jikan", description = "Use the public API or your own Jikan v4 instance. Previously saved details remain available offline.")
                ExpressiveSettingsSwitchItem(title = "Include manga", subtitle = "Save chapter, volume and publication details separately from anime",
                    checked = manga, onCheckedChange = { manga = it; settings.mangaEnabled = it })
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it; error = null; saved = false },
                        label = { Text("Jikan API URL") }, placeholder = { Text(MediaProviderSettings.DEFAULT_JIKAN) },
                        supportingText = { Text(error ?: if (saved) "Server saved" else "For example: http://192.168.1.20:8080/v4/") },
                        isError = error != null, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { endpoint = MediaProviderSettings.DEFAULT_JIKAN; settings.jikanUrl = endpoint; error = null; saved = true }) { Text("Use public server") }
                        Button(onClick = {
                            try { settings.jikanUrl = endpoint; endpoint = settings.jikanUrl; saved = true; error = null }
                            catch (e: IllegalArgumentException) { error = e.message }
                        }) { Text("Save server") }
                    }
                }
            }
        }
        CollapsibleCommonTopBar(title = "Media & Providers", subtitle = "Search providers, metadata & sync",
            collapseFraction = header.collapseFraction, headerHeight = height, onBackClick = onBack)
    }
}

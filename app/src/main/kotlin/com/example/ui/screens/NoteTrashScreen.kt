package com.example.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.TrashItem
import com.example.notes.*
import com.example.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@Composable
fun NoteTrashScreen(manager: TrashManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<TrashItem>?>(null) }
    var sizes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var enabled by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var turnOff by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<TrashItem?>(null) }
    var emptying by remember { mutableStateOf<Map<String, Long>?>(null) }
    fun perform(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { notice = action(); enabled = withContext(Dispatchers.IO) { manager.enabled() }; sizes = manager.ownedSizes() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "This action could not finish. Refresh Trash before retrying. An interrupted permanent deletion will resume safely." }
            finally { busy = false }
        }
    }
    LaunchedEffect(manager) {
        enabled = withContext(Dispatchers.IO) { manager.enabled() }
        try {
            manager.expire()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "Some expired items could not be removed yet. You can retry Delete forever." }
        try { manager.observe().collect { list -> sizes = manager.ownedSizes(); rows = list } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "Trash could not be loaded. Your retained files have not been discarded." }
    }
    val hasUndoWindow = rows.orEmpty().any { it.undoOnly && !it.purgeStarted }
    val clock by produceState(System.currentTimeMillis(), hasUndoWindow) {
        while (isActive) { value = System.currentTimeMillis(); delay(if (hasUndoWindow) 1000 else 60_000) }
    }
    NoteSettingsPage("Trash", "Deleted items stay recoverable for 30 days", onBack, itemSpacing = 8.dp,
        actions = { TextButton(onClick = { emptying = rows.orEmpty().associate { it.id to it.deletedAt } },
            enabled = !busy && !rows.isNullOrEmpty()) { Text("Empty") } }) {
        item {
            ExpressiveSettingsSwitchItem("Use Trash", checked = enabled, enabled = !busy && rows != null,
                subtitle = if (enabled) "Keep deleted items for 30 days, including their history."
                    else "New deletions are permanent after a 30-second Undo window. Existing Trash keeps its deadline.",
                onCheckedChange = { value -> if (!value) turnOff = true else perform { manager.setEnabled(true); "Trash enabled" } })
        }
        if (busy || rows == null) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
        notice?.let { text -> item { StorageMessage(text) } }
        item { SettingsSubsectionHeader("Deleted items", description = "Sizes include retained note versions and recovery files.") }
        if (rows?.isEmpty() == true) item { StorageActionCard("Trash is empty", "0 items", "Deleted items will appear here while they can still be restored.") }
        itemsIndexed(rows.orEmpty(), key = { _, item -> item.id }) { index, item ->
            var menu by remember(item.id) { mutableStateOf(false) }
            val time = clock
            val days = TrashPolicy.daysRemaining(time, item.expiresAt)
            val status = when {
                item.purgeStarted -> "Finishing permanent deletion"
                item.undoOnly -> "Pending permanent deletion"
                days == 0 -> "Due for removal"
                days == 1 -> "1 day remaining"
                else -> "$days days remaining"
            }
            Surface(shape = connectedPillShape(index, rows.orEmpty().size), color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().combinedClickable(enabled = !busy,
                    onClick = { menu = true }, onLongClick = { menu = true })) {
                Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.title.ifBlank { "Untitled item" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        Text("${formatNoteSize(context, sizes[item.id] ?: item.byteLength)} · $status",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !busy) {
                            Icon(painterResource(R.drawable.ic_custom_more_vert), "Trash actions")
                        }
                        NoteContextMenu(menu, { menu = false }) {
                            if (!item.purgeStarted && (!item.undoOnly || time < item.expiresAt)) {
                                NoteContextAction("Restore", R.drawable.ic_custom_undo) {
                                    menu = false; perform { manager.restore(item.id); "Item restored" }
                                }
                            }
                            NoteContextAction("Delete forever", R.drawable.ic_custom_delete, destructive = true) {
                                menu = false; removing = item
                            }
                        }
                    }
                }
            }
        }
        item {
            StorageMessage("Trash still uses storage. Items are removed after 30 days when Android allows cleanup to run. Empty Trash removes them now.")
        }
    }
    if (turnOff) ExpressiveAlertDialog(onDismissRequest = { turnOff = false }, title = "Turn off Trash?",
        text = { Text("New deletions will keep only a 30-second Undo window before permanent removal. Items already in Trash keep their existing deadline.") },
        confirmButtonText = "Turn off", onConfirm = { turnOff = false; perform { manager.setEnabled(false); "Trash disabled for new deletions" } },
        dismissButtonText = "Cancel", onDismiss = { turnOff = false })
    removing?.let { item -> ExpressiveAlertDialog(onDismissRequest = { removing = null }, title = "Delete forever?",
        text = { Text("Permanently remove this item, all its saved versions and recovery files? This cannot be undone.") },
        confirmButtonText = "Delete forever", onConfirm = { removing = null; perform { manager.deleteForever(item.id, item.deletedAt); "Item permanently deleted" } },
        dismissButtonText = "Cancel", onDismiss = { removing = null }) }
    emptying?.let { reviewed -> ExpressiveAlertDialog(onDismissRequest = { emptying = null }, title = "Empty Trash?",
        text = { Text("Permanently remove ${reviewed.size} items and their saved versions and recovery files? This cannot be undone.") },
        confirmButtonText = "Empty Trash", onConfirm = { emptying = null; perform { "${manager.empty(reviewed)} items permanently deleted" } },
        dismissButtonText = "Cancel", onDismiss = { emptying = null }) }
}

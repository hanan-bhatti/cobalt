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
    NoteSettingsPage(
        title = "Trash",
        subtitle = "Deleted items stay recoverable for 30 days",
        onBack = onBack,
        itemSpacing = 8.dp,
        onRefresh = { perform { manager.expire(); "Trash updated" } },
        isRefreshing = busy,
        actions = {
            if (!rows.isNullOrEmpty()) {
                TextButton(
                    onClick = { emptying = rows.orEmpty().associate { it.id to it.deletedAt } },
                    enabled = !busy
                ) {
                    Text("Empty", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    ) {
        item {
            ExpressiveSettingsSwitchItem(
                title = "Use Trash",
                checked = enabled,
                enabled = !busy && rows != null,
                subtitle = if (enabled) "Deleted items stay in Trash for 30 days before permanent deletion."
                    else "Items are permanently deleted immediately upon deletion.",
                onCheckedChange = { value -> if (!value) turnOff = true else perform { manager.setEnabled(true); "Trash enabled" } }
            )
        }
        notice?.let { text -> item { StorageMessage(text) } }
        item { SettingsSubsectionHeader("Deleted items", description = "Restore items back to your notes or delete them permanently.") }
        if (rows == null && busy) {
            items(3) { index ->
                SkeletonStorageRow(shape = connectedPillShape(index, 3), hasBadge = true, badgeWidth = 48.dp)
            }
        } else if (rows?.isEmpty() == true) {
            item { StorageActionCard("Trash is empty", "0 items", "Deleted items will appear here while they can still be restored.") }
        }
        itemsIndexed(rows.orEmpty(), key = { _, item -> item.id }) { index, item ->
            var menu by remember(item.id) { mutableStateOf(false) }
            val time = clock
            val days = TrashPolicy.daysRemaining(time, item.expiresAt)
            val status = when {
                item.purgeStarted || item.undoOnly -> "Deleting…"
                days == 0 -> "Due for removal"
                days == 1 -> "1 day remaining"
                else -> "$days days remaining"
            }
            Surface(
                shape = connectedPillShape(index, rows.orEmpty().size),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        enabled = !busy,
                        onClick = { menu = true },
                        onLongClick = { menu = true }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            item.title.ifBlank { "Untitled item" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${formatNoteSize(context, sizes[item.id] ?: item.byteLength)} · $status",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !busy) {
                            Icon(
                                painterResource(R.drawable.ic_custom_more_vert),
                                contentDescription = "Trash actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
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

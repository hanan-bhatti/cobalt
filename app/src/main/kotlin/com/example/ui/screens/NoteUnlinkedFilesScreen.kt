package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.notes.*
import com.example.ui.components.*
import kotlinx.coroutines.*

@Composable
fun NoteUnlinkedFilesScreen(manager: NoteStorageManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copies by remember { mutableStateOf<List<NoteStorageManager.UnlinkedCopy>>(emptyList()) }
    var report by remember { mutableStateOf<NoteStorageManager.Report?>(null) }
    var busy by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<NoteStorageManager.UnlinkedCopy?>(null) }
    var exporting by remember { mutableStateOf<NoteStorageManager.UnlinkedCopy?>(null) }
    var reviewing by remember { mutableStateOf<NoteStorageManager.UnlinkedReview?>(null) }
    suspend fun refresh() { copies = manager.unlinkedCopies(); report = manager.scan() }
    fun perform(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { notice = action(); refresh() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "This action could not finish. Refresh this page before retrying; files in use remain protected." }
            finally { busy = false }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        val copy = exporting
        exporting = null
        if (uri != null && copy != null) perform { manager.exportUnlinked(copy, uri); if (copy.media == null) "Markdown exported" else "File exported" }
    }
    LaunchedEffect(manager) {
        try { refresh() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "Unlinked files could not be listed. Nothing was removed." }
        finally { busy = false }
    }
    NoteSettingsPage(
        title = "Unlinked storage",
        subtitle = "Media and files no longer attached to any note",
        onBack = onBack,
        itemSpacing = 8.dp,
        onRefresh = { perform { refresh(); "Storage refreshed" } },
        isRefreshing = busy
    ) {
        item {
            StorageActionCard(
                "Orphaned files",
                report?.let { formatNoteSize(context, it.unattached) },
                "These files are orphaned remnants from previous deletions or imports. You can recover or export any file you want to keep, or clean them up to free space.",
                isLoading = report == null && busy
            )
        }
        item {
            val amount = report?.unlinkedRemovable ?: 0
            Button(
                onClick = { perform { reviewing = manager.reviewUnlinked(); "Review the retained files selected for removal" } },
                enabled = !busy && amount > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(if (amount > 0) "Review & clean files · ${formatNoteSize(context, amount)}" else "No unlinked files to clean")
            }
        }
        notice?.let { text -> item { StorageMessage(text) } }
        item { SettingsSubsectionHeader("Retained files", description = "Showing up to 200 files. Clean up reviews all eligible files.") }
        if (copies.isEmpty() && busy) {
            items(3) { index ->
                SkeletonStorageRow(shape = connectedPillShape(index, 3), hasBadge = true, badgeWidth = 46.dp)
            }
        }
        itemsIndexed(copies, key = { _, copy -> copy.key }) { index, copy ->
            var menu by remember(copy.key) { mutableStateOf(false) }
            Surface(
                shape = connectedPillShape(index, copies.size),
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
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = if (copy.media != null) R.drawable.ic_nav_media_outline else R.drawable.ic_nav_notes_outline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            copy.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${formatNoteSize(context, copy.bytes)} · ${if (copy.compressed) "Compressed copy" else if (copy.media != null) "Media file" else "Markdown file"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !busy) {
                            Icon(
                                painterResource(R.drawable.ic_custom_more_vert),
                                contentDescription = "File actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        NoteContextMenu(menu, { menu = false }) {
                            if (copy.recoverable) NoteContextAction(if (copy.media == null) "Recover as note" else "Recover item", R.drawable.ic_custom_undo) {
                                menu = false; perform {
                                    if (manager.recoverUnlinked(copy)) "Item recovered. It is available in your archive."
                                    else "Note recovered. Its old retained file is still needed by another version."
                                }
                            }
                            NoteContextAction(if (copy.media == null) "Export Markdown" else "Export file", R.drawable.ic_custom_export) {
                                menu = false; exporting = copy
                                val title = copy.title.filter { it.isLetterOrDigit() || it in " -_" }.take(60).ifBlank { "Recovered note" }
                                val mime = if (copy.media == null) "text/markdown" else
                                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(copy.extension.lowercase()) ?: "application/octet-stream"
                                exporter.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType(mime).putExtra(Intent.EXTRA_TITLE, "$title.${copy.extension}"))
                            }
                            NoteContextAction("Delete forever", R.drawable.ic_custom_delete, destructive = true) { menu = false; deleting = copy }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { copy -> ExpressiveAlertDialog(onDismissRequest = { deleting = null }, title = "Remove this retained file?",
        text = { Text("Permanently remove this ${formatNoteSize(context, copy.bytes)} file? It has no matching note record and cannot be moved to Trash. Export it first if needed.") },
        confirmButtonText = "Delete forever", onConfirm = { deleting = null; perform {
            if (manager.removeUnlinked(copy)) "Retained file removed" else "This file supports another version or is in use. Use Review cleanup to remove the group safely."
        } }, dismissButtonText = "Cancel", onDismiss = { deleting = null }) }
    reviewing?.let { files -> ExpressiveAlertDialog(onDismissRequest = { reviewing = null }, title = "Clean unlinked files?",
        text = { Text("Remove ${files.count} reviewed files, up to ${formatNoteSize(context, files.bytes)}? Current notes, Trash and protected drafts are kept. Removed files cannot be recovered.") },
        confirmButtonText = "Remove files", onConfirm = { reviewing = null; perform {
            val result = manager.cleanupUnlinked(files.bytes, files)
            "Removed ${formatNoteSize(context, result.bytes)} · ${result.files} files"
        } }, dismissButtonText = "Cancel", onDismiss = { reviewing = null }) }
}

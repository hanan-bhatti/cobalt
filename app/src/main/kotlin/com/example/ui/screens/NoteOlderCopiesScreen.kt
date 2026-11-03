package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.notes.NoteStorageManager
import com.example.ui.components.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NoteOlderCopiesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { NoteStorageManager(context) }
    val scope = rememberCoroutineScope()
    var copies by remember { mutableStateOf<List<NoteStorageManager.OlderCopy>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf<NoteStorageManager.OlderCopy?>(null) }
    var exporting by remember { mutableStateOf<NoteStorageManager.OlderCopy?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val copy = exporting; exporting = null
        if (uri != null && copy != null) {
            busy = true
            scope.launch {
                try { manager.export(copy, uri); message = "Older copy exported" }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { message = "Export did not finish. The original copy is retained; the destination may be incomplete." }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(manager) {
        try { copies = manager.olderCopies() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { message = "Older copies could not be listed. Your notes are retained." }
        finally { busy = false }
    }
    NoteSettingsPage("Saved versions", "Restore or export an earlier version", onBack, itemSpacing = 4.dp) {
        item {
            Text("Up to 200 recent copies are shown. These may include recovered drafts as well as saved versions.",
                modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp)) }
        message?.let { text -> item { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) } }
        if (copies.isEmpty() && !busy) item { Text("No older copies available", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
        items(copies, key = { it.note.id + it.hash }) { copy ->
            var expanded by remember(copy.hash) { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().combinedClickable(enabled = !busy,
                    onClick = { expanded = true }, onLongClick = { expanded = true })) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(copy.note.title.ifBlank { "Untitled note" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(formatNoteSize(context, copy.bytes), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(copy.created)),
                                maxLines = 1, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box {
                        IconButton(onClick = { expanded = true }, enabled = !busy) {
                            Icon(painterResource(R.drawable.ic_custom_more_vert), contentDescription = "Copy actions")
                        }
                        NoteContextMenu(expanded, { expanded = false }) {
                            NoteContextAction("Export Markdown", R.drawable.ic_custom_export) {
                                expanded = false; exporting = copy
                                exporter.launch(copy.note.title.take(80).ifBlank { "Note" } + "-older.md")
                            }
                            NoteContextAction("Restore this copy", R.drawable.ic_custom_undo) { expanded = false; restoring = copy }
                        }
                    }
                }
            }
        }
    }
    restoring?.let { copy -> ExpressiveAlertDialog(onDismissRequest = { restoring = null },
        title = "Restore this copy?",
        text = { Text("Replace the current note with this ${formatNoteSize(context, copy.bytes)} copy? The current full copy will remain available until you explicitly clean it up. Finish any recovery draft first.") },
        confirmButtonText = "Restore copy", onConfirm = {
            restoring = null; busy = true
            scope.launch {
                try { manager.restore(copy); copies = manager.olderCopies(); message = "Older copy restored" }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { message = "Could not restore this copy. The note may have changed or have a recovery draft. Reopen this page after checking the note." }
                finally { busy = false }
            }
        }, dismissButtonText = "Cancel", onDismiss = { restoring = null }) }
}

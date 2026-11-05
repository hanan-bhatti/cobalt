package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.notes.CaptureDraftRecovery
import com.example.ui.components.*
import kotlinx.coroutines.*

@Composable
fun CaptureDraftRecoveryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val recovery = remember(context) { CaptureDraftRecovery(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<CaptureDraftRecovery.Entry>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportTitle by rememberSaveable { mutableStateOf("") }
    var exportBytes by rememberSaveable { mutableLongStateOf(0) }
    var exportRecord by rememberSaveable { mutableStateOf("") }
    var exportToken by rememberSaveable { mutableStateOf<String?>(null) }
    suspend fun refresh() { entries = recovery.list() }
    fun perform(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { message = action(); refresh() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message = "Recovery could not finish. Close any open editor and retry. Original notes and recovery files are retained." }
            finally { busy = false }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val id = exportId
        exportId = null
        if (uri != null && id != null) {
            val entry = CaptureDraftRecovery.Entry(id, exportTitle, exportBytes, exportRecord, exportToken)
            perform { recovery.export(entry, uri); "Draft exported" }
        }
    }
    LaunchedEffect(recovery) {
        try { refresh() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { message = "Drafts could not be listed. No recovery files were removed." }
        finally { busy = false }
    }
    NoteSettingsPage("Draft recovery", "Interrupted edits and notes not saved to the archive", onBack, itemSpacing = 8.dp,
        actions = { TextButton(onClick = { perform { refresh(); "Drafts refreshed" } }, enabled = !busy) { Text("Refresh") } }) {
        item { StorageMessage("Recover creates a new note with the complete draft. You can also export a Markdown copy. Close its editor before recovering a draft.") }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        message?.let { text -> item { StorageMessage(text) } }
        if (!busy && entries.isEmpty()) item { StorageActionCard("No drafts to recover", "", "Recoverable edits appear here after an interrupted session.") }
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            var menu by remember(entry.id) { mutableStateOf(false) }
            Surface(shape = connectedPillShape(index, entries.size), color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().combinedClickable(enabled = !busy && !entry.problem,
                    onClick = { menu = true }, onLongClick = { menu = true })) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(if (entry.problem) "Draft metadata needs attention" else "${formatNoteSize(context, entry.bytes)} note",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!entry.problem) Box {
                        IconButton(onClick = { menu = true }, enabled = !busy) { Icon(painterResource(R.drawable.ic_custom_more_vert), "Draft actions") }
                        NoteContextMenu(menu, { menu = false }) {
                            NoteContextAction("Recover copy", R.drawable.ic_custom_undo) {
                                menu = false; perform { recovery.recover(entry); "Draft recovered as a new note" }
                            }
                            NoteContextAction("Export Markdown", R.drawable.ic_custom_export) {
                                menu = false
                                exportId = entry.id; exportTitle = entry.title; exportBytes = entry.bytes
                                exportRecord = entry.record; exportToken = entry.token
                                exporter.launch(entry.title.filter { it.isLetterOrDigit() || it in " -_" }.take(80).ifBlank { "Recovered draft" } + ".md")
                            }
                        }
                    }
                }
            }
        }
    }
}

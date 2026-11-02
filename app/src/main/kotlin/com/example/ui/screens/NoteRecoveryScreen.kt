package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.LegacyNoteRecovery
import com.example.notes.NoteSizePolicy
import com.example.notes.RecoveryNote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Independent recovery UI: never constructs the normal ViewModel or Markdown tree. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteRecoveryScreen(onExitSafeMode: () -> Unit) {
    val context = LocalContext.current
    val reader = remember { LegacyNoteRecovery(context) }
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<RecoveryNote>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var largeOnly by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportTitle by rememberSaveable { mutableStateOf("") }
    var exportBytes by rememberSaveable { mutableLongStateOf(0L) }
    val exportProgress = remember { MutableStateFlow(0L) }
    val exported by exportProgress.collectAsState()

    LaunchedEffect(reader) {
        try {
            reader.observeNotes().collect { notes = it; loading = false }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            loading = false
            message = "Recovery could not open the database. No notes were deleted."
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val id = exportId
        if (uri != null && id != null) {
            val note = RecoveryNote(id, exportTitle, exportBytes)
            scope.launch {
                busy = true
                exportProgress.value = 0
                try {
                    reader.export(note, uri) { exportProgress.value = it }
                    message = "Raw Markdown exported. The original note is unchanged."
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    message = "Export failed. The selected file may be incomplete; the original note is unchanged."
                } finally {
                    busy = false
                }
            }
        }
        exportId = null
    }

    fun export(note: RecoveryNote) {
        exportId = note.id
        exportTitle = note.title
        exportBytes = note.byteLength
        val name = note.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80)
        exporter.launch("${name.ifBlank { "note" }}.md")
    }

    BackHandler(enabled = selectedId != null && !busy) { selectedId = null }
    val selected = notes.firstOrNull { it.id == selectedId }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (selected == null) "Note recovery" else selected.title, maxLines = 1) },
            navigationIcon = {
                if (selected != null) TextButton(onClick = { selectedId = null }, enabled = !busy) { Text("Back") }
            },
            actions = { TextButton(onClick = { confirmExit = true }, enabled = !busy) { Text("Exit safe mode") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Safe mode: background work, learning and link previews are paused.")
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text("Exported $exported bytes")
            }
            message?.let { Text(it, Modifier.padding(vertical = 8.dp)) }
            if (selected != null) {
                TextButton(onClick = { export(selected) }, enabled = !busy) { Text("Export raw Markdown") }
                NoteBodyPlainText(reader, selected, Modifier.weight(1f).fillMaxWidth())
            } else {
                TextButton(onClick = { largeOnly = !largeOnly }) {
                    Text(if (largeOnly) "Show all notes" else "Quarantine large notes")
                }
                if (largeOnly) Text("Notes of 200 KB or larger. Nothing is moved or deleted automatically.")
                if (loading) CircularProgressIndicator()
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(notes.filter { !largeOnly || it.byteLength >= NoteSizePolicy.TIER_A_MAX_BYTES }, key = { it.id }) { note ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text("${note.byteLength} bytes")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { selectedId = note.id }, enabled = !busy) { Text("Plain text") }
                                TextButton(onClick = { export(note) }, enabled = !busy) { Text("Export") }
                                TextButton(onClick = { deleteId = note.id }, enabled = !busy) { Text("Delete") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (deleteId != null) AlertDialog(
        onDismissRequest = { deleteId = null },
        title = { Text("Permanently delete this note?") },
        text = { Text("Export it first if you want to keep a copy.") },
        confirmButton = {
            TextButton(onClick = {
                val id = deleteId ?: return@TextButton
                deleteId = null
                scope.launch {
                    try { reader.delete(id) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { message = "The note could not be deleted." }
                }
            }) { Text("Delete permanently") }
        },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
    )
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Return to normal mode?") },
        text = { Text("Background features will be allowed again. Large notes remain unchanged.") },
        confirmButton = { TextButton(onClick = onExitSafeMode) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Cancel") } }
    )
}

@Composable
internal fun NoteBodyPlainText(reader: LegacyNoteRecovery, note: RecoveryNote, modifier: Modifier, jumpByte: Long = 0) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        // A block remains roughly one viewport high even for text consisting only of newlines.
        val chunkBytes = (maxHeight.value / (24f * fontScale)).toInt().coerceIn(1, 128)
        val count = if (note.byteLength == 0L) 0 else (note.byteLength - 1) / chunkBytes + 1
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(jumpByte, chunkBytes, count) {
            if (count in 1..Int.MAX_VALUE.toLong()) listState.scrollToItem((jumpByte / chunkBytes).coerceIn(0, count - 1).toInt())
        }
        if (count > Int.MAX_VALUE) {
            Text("This note is too large for recovery display. Use Export to preserve the complete body.")
        } else LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(count.toInt(), key = { it }) { index ->
                val chunk by produceState<String?>(null, note.id, index, chunkBytes) {
                    try { value = reader.displayChunk(note, index, chunkBytes) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { value = "[This part could not be read. The original note is unchanged.]" }
                }
                SelectionContainer {
                    Text(chunk ?: "…", fontFamily = FontFamily.Monospace, lineHeight = 24.sp)
                }
            }
        }
    }
}

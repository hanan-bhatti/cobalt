package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedItem
import com.example.notes.*
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.*

/** Bounded plain-text fallback and section editor; never lays out a complete long note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileNoteScreen(item: SavedItem, viewModel: CobaltViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val persistence = remember { NotePersistence(context) }
    val reader = remember { LegacyNoteRecovery(context) }
    val scope = rememberCoroutineScope()
    var note by remember(item.id) { mutableStateOf(item.copy(bodyLoaded = false)) }
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var pageStart by rememberSaveable(item.id) { mutableLongStateOf(0L) }
    var pageEnd by rememberSaveable(item.id) { mutableLongStateOf(0L) }
    var editingHash by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(item.id) { mutableStateOf("") }
    var dirty by rememberSaveable(item.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    val previousStarts = remember(item.id) { mutableStateListOf<Long>() }
    val folders by viewModel.customFolders.collectAsState()
    val folderEntities by viewModel.customFolderEntities.collectAsState()
    val pendingSections by viewModel.pendingSections.collectAsState()
    val pendingSection = pendingSections[item.id]
    LaunchedEffect(pendingSection) {
        pendingSection?.let {
            editing = true
            pageStart = it.start
            pageEnd = it.end
            editingHash = it.hash
            dirty = true
        }
    }

    LaunchedEffect(item.id, item.contentHash, item.updatedAt) {
        try {
            val loaded = persistence.load(item.id, includeSmallBody = false)
            if (loaded != null) note = loaded
            com.example.util.AdaptiveReminderStore(context).recordItemOpened(item)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { message = "Could not read this note. Its saved body has not been replaced." }
    }
    LaunchedEffect(editing, pageStart, note.contentHash) {
        val hash = note.contentHash
        if (editing && hash != null && !note.bodyMissing) {
            if (dirty && editingHash != hash) {
                message = "The note changed elsewhere. Your draft is retained; reload before saving."
            } else {
                try {
                    val window = persistence.window(note.id, hash, pageStart)
                    pageEnd = window.end
                    if (!dirty) { draft = window.text; editingHash = hash }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { message = "Could not open this section. Original retained." }
            }
        }
    }

    fun saveSection(replacement: String) {
        val hash = pendingSection?.hash ?: editingHash ?: return
        val pending = pendingSection ?: CobaltViewModel.PendingSection(hash, pageStart, pageEnd, replacement)
        viewModel.retainNoteSection(note.id, pending)
        val window = EditableNoteWindow(pending.start, pending.end, "")
        dirty = true
        busy = true
        job = scope.launch {
            try {
                val saved = persistence.replaceWindow(note.id, pending.hash, window, pending.replacement)
                viewModel.clearNoteSection(note.id, pending)
                dirty = false
                note = saved
                viewModel.showDetailItem(saved)
                message = "Section saved."
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message = "Save failed or the note changed. Draft and latest saved body are retained." }
            finally { busy = false; job = null }
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            busy = true
            job = scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val cancellation = currentCoroutineContext()
                        context.contentResolver.openOutputStream(uri, "wt")?.use {
                            NoteExport.write(note, persistence.store, it) { cancellation.ensureActive() }
                        } ?: throw java.io.IOException("Cannot open selected document")
                    }
                    message = "Markdown exported with title, tags and dates."
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { message = "Export failed; selected file may be incomplete. Original retained." }
                finally { busy = false; job = null }
            }
        }
    }
    fun close() {
        if (dirty || pendingSection != null) confirmDiscard = true else onClose()
    }
    BackHandler { if (!busy) close() }

    Scaffold(topBar = {
        TopAppBar(title = { Text(note.title.ifBlank { "Note" }, maxLines = 1) },
            navigationIcon = { TextButton(onClick = { close() }, enabled = !busy) { Text("Back") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { job?.cancel() }) { Text("Cancel") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
            if (note.bodyMissing || note.contentHash == null) {
                Text("The body is missing, unavailable, or still needs migration. The note record is retained. See Settings → Data Migration.")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { if (!dirty) editing = !editing }, enabled = !busy) {
                        Text(if (editing) "Read" else "Edit sections")
                    }
                    TextButton(onClick = {
                        export.launch(note.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).ifBlank { "note" } + ".md")
                    }, enabled = !busy) { Text("Export") }
                    TextButton(onClick = { showFolders = true }, enabled = !busy) { Text("Folders") }
                    TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Delete") }
                }
                TextButton(onClick = { viewModel.shareItem(note, context) }, enabled = !busy) { Text("Share Markdown") }
                if (pendingSection != null) {
                    Text("An unsaved section is retained in memory. Retry saving before closing this note. It will not survive process termination.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { saveSection(pendingSection.replacement) }, enabled = !busy && editingHash != null) { Text("Retry section save") }
                }
                if (editing) {
                    Text("Edit one section at a time. Save before moving. Large pastes are saved in the background.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = draft, onValueChange = { value ->
                        if (NoteEditorWindows.requiresWindow(value)) { saveSection(value) }
                        else { draft = value; dirty = true }
                    }, enabled = !busy && pendingSection == null, maxLines = 12, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp))
                    Row {
                        TextButton(onClick = { saveSection(draft) }, enabled = dirty && !busy) { Text("Save section") }
                        TextButton(onClick = {
                            if (previousStarts.isNotEmpty()) pageStart = previousStarts.removeAt(previousStarts.lastIndex)
                        }, enabled = previousStarts.isNotEmpty() && !dirty && !busy) { Text("Previous") }
                        TextButton(onClick = {
                            previousStarts.add(pageStart); pageStart = pageEnd
                        }, enabled = pageEnd > pageStart && pageEnd < note.byteLength && !dirty && !busy) { Text("Next") }
                    }
                } else {
                    Text("Large-note mode · plain text · ${note.byteLength} bytes", style = MaterialTheme.typography.bodySmall)
                    key(note.contentHash) {
                        NoteBodyPlainText(reader, RecoveryNote(note.id, note.title, note.byteLength), Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard this section's unsaved changes?") },
        confirmButton = { TextButton(onClick = { viewModel.clearNoteSection(note.id); onClose() }) { Text("Discard and close") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this note?") },
        text = { Text("Export a copy first if you want to keep it.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteSavedItem(note); onClose() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    if (showFolders) com.example.ui.components.MoveToFolderDialog(
        customFolders = folders, customFolderEntities = folderEntities,
        initialSelectedFolders = note.folders.toSet(), onDismiss = { showFolders = false },
        onCreateFolder = { name, icon -> viewModel.createFolder(name, iconName = icon) },
        onConfirm = { selected -> viewModel.updateSavedItem(note.copy(folders = selected.toList(), bodyLoaded = false)); showFolders = false }
    )
}

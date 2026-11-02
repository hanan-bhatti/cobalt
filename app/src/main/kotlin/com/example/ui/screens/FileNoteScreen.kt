package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileNoteScreen(item: SavedItem, viewModel: CobaltViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val persistence = remember { NotePersistence(context) }
    val reader = remember { LegacyNoteRecovery(context) }
    val sectionDrafts = remember { SectionDraftStore(context) }
    var durableDraft by remember(item.id) { mutableStateOf<SectionDraftStore.Entry?>(null) }
    var journalRecovered by remember(item.id) { mutableStateOf(false) }
    var draftLoaded by remember(item.id) { mutableStateOf(false) }
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
    var showFind by rememberSaveable(item.id) { mutableStateOf(false) }
    var findQuery by rememberSaveable(item.id) { mutableStateOf("") }
    var replacementText by rememberSaveable(item.id) { mutableStateOf("") }
    var renameTitle by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var lastMatch by remember(item.id, note.contentHash) { mutableStateOf<Long?>(null) }
    var jumpByte by rememberSaveable(item.id) { mutableLongStateOf(0) }
    var outline by remember(item.id) { mutableStateOf<List<NoteTextNavigator.Heading>?>(null) }
    var lineIndex by remember(item.id, note.contentHash) { mutableStateOf<Utf8LineIndex?>(null) }
    var targetLine by rememberSaveable(item.id) { mutableStateOf("") }
    val previousStarts = remember(item.id) { mutableStateListOf<Long>() }
    val folders by viewModel.customFolders.collectAsState()
    val folderEntities by viewModel.customFolderEntities.collectAsState()
    val pendingSections by viewModel.pendingSections.collectAsState()
    val pendingSection = pendingSections[item.id]
    val editHistory = remember(item.id, editingHash, pageStart) { viewModel.noteWindowHistory(item.id, editingHash, pageStart) }
    var canUndo by remember(editHistory) { mutableStateOf(editHistory.canUndo) }
    var canRedo by remember(editHistory) { mutableStateOf(editHistory.canRedo) }
    LaunchedEffect(item.id) {
        try { durableDraft = sectionDrafts.load(item.id); journalRecovered = durableDraft != null }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { message = "A section draft could not be read. Its recovery reference is retained." }
        finally { draftLoaded = true }
    }
    LaunchedEffect(draft, dirty, editingHash, pageStart, pageEnd, draftLoaded, pendingSection, busy, journalRecovered) {
        val hash = editingHash
        if (draftLoaded && dirty && hash != null && pendingSection == null && !busy && !journalRecovered) {
            delay(400)
            try { durableDraft = sectionDrafts.save(note.id, hash, pageStart, pageEnd, draft) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message = "Could not journal this edit. Save or retry before leaving." }
        }
    }
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
                if (NoteSizePolicy.needsWarning(note.byteLength)) Text(
                    "This note is above 50 MB. It is kept intact; large-file operations may take longer.",
                    style = MaterialTheme.typography.bodySmall)
                try {
                    val window = persistence.window(note.id, hash, pageStart)
                    pageEnd = window.end
                    if (!dirty) { draft = window.text; editingHash = hash }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { message = "Could not open this section. Original retained." }
            }
        }
    }

    fun saveSection(replacement: String, expectedHash: String? = null, start: Long = pageStart, end: Long = pageEnd) {
        val hash = pendingSection?.hash ?: expectedHash ?: editingHash ?: return
        val pending = pendingSection ?: CobaltViewModel.PendingSection(hash, start, end, replacement)
        viewModel.retainNoteSection(note.id, pending)
        dirty = true
        busy = true
        job = scope.launch {
            try {
                val entry = sectionDrafts.save(note.id, pending.hash, pending.start, pending.end, pending.replacement)
                durableDraft = entry
                val saved = sectionDrafts.apply(note.id, entry)
                durableDraft = null
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
        if (dirty || pendingSection != null || durableDraft != null) confirmDiscard = true else onClose()
    }
    BackHandler { if (!busy) close() }

    Scaffold(topBar = {
        TopAppBar(title = { Text(note.title.ifBlank { "Note" }, maxLines = 1) },
            navigationIcon = { TextButton(onClick = { close() }, enabled = !busy) { Text("Back") } },
            actions = { TextButton(onClick = { renameTitle = note.title }, enabled = !busy) { Text("Rename") } })
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val controlsHeight = if (editing) maxHeight else maxHeight * 0.45f
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { job?.cancel() }) { Text("Cancel") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3) }
            if (note.bodyMissing || note.contentHash == null) {
                Text("The body is missing, unavailable, or still needs migration. The note record is retained. See Settings → Data Migration.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Row {
                    TextButton(onClick = { showFind = !showFind }, enabled = !busy) { Text("Find") }
                    TextButton(onClick = {
                        val hash = note.contentHash ?: return@TextButton
                        busy = true
                        job = scope.launch {
                            try {
                                outline = withContext(Dispatchers.IO) {
                                    val cancellation = currentCoroutineContext()
                                    persistence.store.open(note.id, hash).use { input ->
                                        NoteTextNavigator.headings(input) { cancellation.ensureActive() }
                                    }
                                }
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { message = "Could not read headings. Original retained." }
                            finally { busy = false; job = null }
                        }
                    }, enabled = !busy) { Text("Outline") }
                }
                if (showFind) Column {
                    Row {
                    OutlinedTextField(value = findQuery, onValueChange = { findQuery = it.take(256); lastMatch = null },
                        label = { Text("Find (case sensitive)") }, singleLine = true,
                        modifier = Modifier.weight(1f), enabled = !busy)
                    TextButton(onClick = {
                        val hash = note.contentHash ?: return@TextButton
                        val query = findQuery
                        val start = lastMatch?.plus(1) ?: 0L
                        busy = true
                        job = scope.launch {
                            try {
                                val match = withContext(Dispatchers.IO) {
                                    val cancellation = currentCoroutineContext()
                                    persistence.store.open(note.id, hash).use { input ->
                                        NoteTextNavigator.find(input, query, start) { cancellation.ensureActive() }
                                    }
                                }
                                lastMatch = match
                                if (match == null) message = "No further match. Next searches from the start."
                                else { editing = false; jumpByte = match; message = "Match at byte $match." }
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { message = "Search failed. Original retained." }
                            finally { busy = false; job = null }
                        }
                    }, enabled = !busy && !dirty && durableDraft == null && findQuery.isNotEmpty()) { Text("Next") }
                    }
                    Row {
                        OutlinedTextField(value = replacementText, onValueChange = {
                            if (it.length <= NoteEditorWindows.SECTION_INPUT_CHARACTERS) replacementText = it
                            else message = "Replacement is limited to ${NoteEditorWindows.SECTION_INPUT_CHARACTERS} characters here. Use Edit sections for a larger paste."
                        },
                            label = { Text("Replace current match") }, singleLine = true, modifier = Modifier.weight(1f), enabled = !busy)
                        TextButton(onClick = {
                            val match = lastMatch ?: return@TextButton
                            val hash = note.contentHash ?: return@TextButton
                            val bytes = findQuery.toByteArray(Charsets.UTF_8).size
                            saveSection(replacementText, hash, match, match + bytes)
                        }, enabled = !busy && !dirty && durableDraft == null && lastMatch != null) { Text("Replace") }
                    }
                    Row {
                        OutlinedTextField(value = targetLine, onValueChange = { targetLine = it.filter(Char::isDigit).take(12) },
                            singleLine = true, label = { Text("Line number") }, modifier = Modifier.weight(1f), enabled = !busy)
                        TextButton(onClick = {
                            val hash = note.contentHash ?: return@TextButton
                            val requested = targetLine.toLongOrNull()?.minus(1) ?: return@TextButton
                            val cached = lineIndex
                            busy = true
                            job = scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        val cancellation = currentCoroutineContext()
                                        val table = PieceTable(FileNoteByteSource(persistence.store, note.id, hash, note.byteLength))
                                        val index = cached ?: table.openStream().use { input ->
                                            Utf8LineIndex.build(input) { cancellation.ensureActive() }
                                        }
                                        val offset = if (requested in 0 until index.lineCount) index.byteOffset(requested,
                                            { table.openStream(it) }) { cancellation.ensureActive() } else null
                                        index to offset
                                    }
                                    lineIndex = result.first
                                    result.second?.let { editing = false; jumpByte = it }
                                    message = if (result.second == null) "Line is out of range. This note has ${result.first.lineCount} lines." else "Line ${requested + 1}."
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { message = "Could not index lines. Original retained." }
                                finally { busy = false; job = null }
                            }
                        }, enabled = !busy && !dirty && durableDraft == null && targetLine.toLongOrNull()?.let { it > 0 } == true) { Text("Jump") }
                    }
                }
                if (pendingSection != null) {
                    Text(if (durableDraft != null) "A section draft is stored privately for recovery. Retry saving before closing."
                        else "A section is waiting to be saved. Retry before closing; its private recovery copy may not yet be complete.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { saveSection(pendingSection.replacement) }, enabled = !busy && editingHash != null) { Text("Retry section save") }
                }
                durableDraft?.let { entry ->
                    if (pendingSection == null && journalRecovered) {
                        Text("A saved section draft is available. If this note changed, recover it as a separate note.", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = {
                                busy = true
                                job = scope.launch {
                                    try {
                                        val saved = sectionDrafts.apply(note.id, entry)
                                        durableDraft = null; journalRecovered = false; dirty = false; note = saved
                                        viewModel.showDetailItem(saved)
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { message = "Draft could not be applied. Recover it as a separate note to keep both versions." }
                                    finally { busy = false; job = null }
                                }
                            }, enabled = !busy && entry.expectedHash == note.contentHash) { Text("Apply saved draft") }
                            TextButton(onClick = {
                                busy = true
                                job = scope.launch {
                                    try {
                                        sectionDrafts.recoverAsNote(note.id, entry)
                                        durableDraft = null; journalRecovered = false; dirty = false
                                        message = "Section recovered as a separate note."
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { message = "Recovery failed. Draft retained." }
                                    finally { busy = false; job = null }
                                }
                            }, enabled = !busy) { Text("Recover as note") }
                        }
                    }
                }
                if (editing) {
                    Text("Edit one section at a time. Save before moving. Large pastes are saved in the background.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = draft, onValueChange = { value ->
                        if (NoteEditorWindows.sectionNeedsStreaming(value)) { saveSection(value) }
                        else {
                            editHistory.record(draft, value); draft = value; dirty = true
                            canUndo = editHistory.canUndo; canRedo = editHistory.canRedo
                        }
                    }, enabled = draftLoaded && !busy && pendingSection == null && !journalRecovered, maxLines = 12, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp))
                    Row {
                        TextButton(onClick = {
                            editHistory.undo(draft)?.let { draft = it; dirty = true }
                            canUndo = editHistory.canUndo; canRedo = editHistory.canRedo
                        }, enabled = canUndo && !busy && pendingSection == null && !journalRecovered) { Text("Undo") }
                        TextButton(onClick = {
                            editHistory.redo(draft)?.let { draft = it; dirty = true }
                            canUndo = editHistory.canUndo; canRedo = editHistory.canRedo
                        }, enabled = canRedo && !busy && pendingSection == null && !journalRecovered) { Text("Redo") }
                    }
                    Row {
                        TextButton(onClick = { saveSection(draft) }, enabled = dirty && !busy) { Text("Save section") }
                        TextButton(onClick = {
                            if (previousStarts.isNotEmpty()) pageStart = previousStarts.removeAt(previousStarts.lastIndex)
                        }, enabled = previousStarts.isNotEmpty() && !dirty && !busy) { Text("Previous") }
                        TextButton(onClick = {
                            previousStarts.add(pageStart); pageStart = pageEnd
                        }, enabled = pageEnd > pageStart && pageEnd < note.byteLength && !dirty && !busy) { Text("Next") }
                    }
                }
            }
        }
        // The lazy reader is a sibling of the bounded scrollable controls, never a child.
        if (!editing && !note.bodyMissing && note.contentHash != null) {
            Text("Large-note mode · plain text · ${note.byteLength} bytes", style = MaterialTheme.typography.bodySmall)
            key(note.contentHash) {
                NoteBodyPlainText(reader, RecoveryNote(note.id, note.title, note.byteLength), Modifier.weight(1f).fillMaxWidth(), jumpByte)
            }
        }
        }
        }
    }
    renameTitle?.let { title ->
        AlertDialog(onDismissRequest = { renameTitle = null }, title = { Text("Rename note") },
            text = { OutlinedTextField(value = title, onValueChange = { renameTitle = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                viewModel.updateSavedItem(note.copy(title = title, bodyLoaded = false)); renameTitle = null
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTitle = null }) { Text("Cancel") } })
    }
    outline?.let { headings ->
        AlertDialog(onDismissRequest = { outline = null }, title = { Text("Heading outline") }, text = {
            if (headings.isEmpty()) Text("No ATX headings found outside fenced code.")
            else androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(headings.size) { index ->
                    val heading = headings[index]
                    TextButton(onClick = { editing = false; jumpByte = heading.offset; outline = null }, enabled = !dirty && durableDraft == null) {
                        Text("${"#".repeat(heading.level)} ${heading.title}", maxLines = 2)
                    }
                }
                if (headings.size == NoteTextNavigator.MAX_HEADINGS) item { Text("Showing the first ${NoteTextNavigator.MAX_HEADINGS} headings.") }
            }
        }, confirmButton = { TextButton(onClick = { outline = null }) { Text("Close") } })
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard this section's unsaved changes?") },
        confirmButton = { TextButton(onClick = {
            scope.launch {
                try { sectionDrafts.clear(note.id); viewModel.clearNoteSection(note.id); onClose() }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { message = "Could not clear the recovery draft. Please retry."; confirmDiscard = false }
            }
        }) { Text("Discard and close") } },
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

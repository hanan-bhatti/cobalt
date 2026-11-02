package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.notes.LegacyNoteRecovery
import com.example.notes.NoteSizePolicy
import com.example.notes.RecoveryNote
import com.example.notes.StartupCrashGuard
import com.example.ui.components.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Recovery shares settings visuals without constructing the normal ViewModel or a Markdown tree. */
@Composable
fun NoteRecoveryScreen(onExitSafeMode: () -> Unit, safeMode: Boolean = StartupCrashGuard.isSafeMode) {
    val context = LocalContext.current
    val reader = remember { LegacyNoteRecovery(context) }
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<RecoveryNote>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val recoveryListState = rememberLazyListState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var largeOnly by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportTitle by rememberSaveable { mutableStateOf("") }
    var exportBytes by rememberSaveable { mutableLongStateOf(0L) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    val exportProgress = remember { MutableStateFlow(0L) }
    val exported by exportProgress.collectAsState()
    val exportFraction by animateFloatAsState(
        if (exportBytes > 0) (exported.toDouble() / exportBytes).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(200), label = "recoveryExportProgress")

    LaunchedEffect(reader) {
        try { reader.observeNotes().collect { notes = it; loading = false } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { loading = false; message = "Could not open the note list. No notes were deleted." }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val id = exportId
        if (uri != null && id != null) {
            val note = RecoveryNote(id, exportTitle, exportBytes)
            busy = true
            exportProgress.value = 0
            exportJob = scope.launch {
                try {
                    reader.export(note, uri) { exportProgress.value = it }
                    message = "Markdown exported. Your original note is unchanged."
                } catch (cancel: CancellationException) {
                    message = "Export cancelled. The selected file may be incomplete; your original is safe."
                    throw cancel
                } catch (_: Exception) {
                    message = "Export failed. The selected file may be incomplete; your original is safe."
                } finally { busy = false; exportJob = null }
            }
        }
        exportId = null
    }
    fun export(note: RecoveryNote) {
        exportId = note.id; exportTitle = note.title; exportBytes = note.byteLength
        val name = note.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80)
        exporter.launch("${name.ifBlank { "note" }}.md")
    }
    fun back() {
        if (selectedId != null) selectedId = null
        else if (safeMode) confirmExit = true
        else onExitSafeMode()
    }
    val selected = notes.firstOrNull { it.id == selectedId }
    val largeNotes = remember(notes) { notes.filter { it.byteLength >= NoteSizePolicy.TIER_A_MAX_BYTES } }
    val filtered = if (largeOnly) largeNotes else notes
    val exportStatus: @Composable () -> Unit = {
        if (busy && exportJob != null) Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Exporting Markdown", style = MaterialTheme.typography.titleSmall)
                        Text("${formatNoteSize(context, exported)} of ${formatNoteSize(context, exportBytes)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { exportJob?.cancel() }) { Text("Cancel") }
                }
                LinearProgressIndicator(progress = { exportFraction }, modifier = Modifier.fillMaxWidth().height(6.dp))
            }
        }
    }
    NoteBackSurface(enabled = !confirmExit && deleteId == null, onBack = ::back) {
        AnimatedContent(targetState = selected?.id, label = "recoveryNote", transitionSpec = {
            (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { if (targetState != null) it / 10 else -it / 10 }) togetherWith
                (fadeOut(tween(140)) + slideOutHorizontally(tween(240)) { if (targetState != null) -it / 10 else it / 10 })
        }) { targetId ->
            val displayed = notes.firstOrNull { it.id == targetId }
            if (displayed != null) {
                val listState = rememberLazyListState()
                val bar = rememberCollapsibleTopBarState(listState, maxHeight = 160.dp)
                val height = with(LocalDensity.current) { bar.heightPx.toDp() }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(bar.nestedScrollConnection)) {
                    NoteBodyPlainText(reader, displayed, Modifier.fillMaxSize(), listState = listState,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = height + 12.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
                        header = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(displayed.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                        RecoveryNoteSize(displayed.byteLength)
                                    }
                                }
                                exportStatus()
                                message?.let { NoteStorageNotice("Recovery", it) }
                            }
                        })
                    CollapsibleCommonTopBar("Plain text", bar.collapseFraction, height, ::back,
                        subtitle = "Read without Markdown formatting", actions = {
                            RecoveryNoteMenu(displayed, busy, onExport = { export(displayed) }, onDelete = { deleteId = displayed.id })
                        })
                }
            } else NoteSettingsPage("Note Recovery", "Read, export or recover your notes", ::back, itemSpacing = 2.dp, listState = recoveryListState) {
                item {
                    Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (safeMode) NoteStorageNotice("Safe mode is on", "Background activity is paused while you recover your notes.")
                        val allLabel = "All (${notes.size})"
                        val largeLabel = "Large (${largeNotes.size})"
                        ExpressiveSegmentedChoiceItem(title = "Show notes", options = listOf(allLabel, largeLabel),
                            selectedOption = if (largeOnly) largeLabel else allLabel,
                            subtitle = "Large notes are ${formatNoteSize(context, NoteSizePolicy.TIER_A_MAX_BYTES)} or more.",
                            onOptionSelected = { option ->
                                val next = option == largeLabel
                                if (next != largeOnly) {
                                    largeOnly = next
                                    scope.launch { recoveryListState.animateScrollToItem(0) }
                                }
                            })
                        exportStatus()
                        message?.let { NoteStorageNotice("Recovery", it) }
                        SettingsSubsectionHeader(title = if (largeOnly) "Large notes" else "All notes",
                            description = if (largeOnly) "Showing ${filtered.size} of ${notes.size} notes · ${formatNoteSize(context, NoteSizePolicy.TIER_A_MAX_BYTES)} or larger"
                                else "${notes.size} notes · tap to read, hold for actions")
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) }
                if (!loading && filtered.isEmpty()) item {
                    NoteStorageNotice(if (largeOnly) "No large notes" else "No notes to recover",
                        if (largeOnly) "Choose All to see your other notes." else "Your note list will appear here when it is available.")
                }
                itemsIndexed(filtered, key = { _, note -> note.id }) { index, note ->
                    RecoveryNoteCard(note, index, filtered.size, busy,
                        onRead = { selectedId = note.id }, onExport = { export(note) }, onDelete = { deleteId = note.id })
                }
            }
        }
    }
    deleteId?.let { id -> ExpressiveConfirmationDialog(onDismissRequest = { deleteId = null },
        title = "Delete this note?", message = "This permanently removes the note. Export a copy first if you want to keep it.",
        confirmButtonText = "Delete note", onConfirm = {
            deleteId = null; busy = true
            scope.launch {
                try { reader.delete(id); if (selectedId == id) selectedId = null }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { message = "Could not delete this note." }
                finally { busy = false }
            }
        }) }
    if (confirmExit) ExpressiveConfirmationDialog(onDismissRequest = { confirmExit = false },
        title = "Leave safe mode?", message = "Background features will be allowed again. Your notes stay on this device.",
        confirmButtonText = "Continue", isDestructive = false, onConfirm = onExitSafeMode)
}

@Composable
private fun RecoveryNoteMenu(note: RecoveryNote, busy: Boolean, onExport: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember(note.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "Options for ${note.title}") }
        NoteContextMenu(expanded, onDismiss = { expanded = false }) {
            NoteContextAction("Export Markdown", R.drawable.ic_custom_note_export) { expanded = false; onExport() }
            NoteContextAction("Delete note", R.drawable.ic_custom_delete, destructive = true) { expanded = false; onDelete() }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RecoveryNoteCard(note: RecoveryNote, index: Int, total: Int, busy: Boolean,
    onRead: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var menu by remember(note.id) { mutableStateOf(false) }
    Surface(shape = connectedPillShape(index, total), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(connectedPillShape(index, total)).combinedClickable(enabled = !busy, onClick = onRead,
            onLongClick = { menu = true }, onLongClickLabel = "Note actions")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                RecoveryNoteSize(note.byteLength)
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "Options for ${note.title}") }
                NoteContextMenu(menu, onDismiss = { menu = false }) {
                    NoteContextAction("Read plain text", R.drawable.ic_custom_eye) { menu = false; onRead() }
                    NoteContextAction("Export Markdown", R.drawable.ic_custom_note_export) { menu = false; onExport() }
                    NoteContextAction("Delete note", R.drawable.ic_custom_delete, destructive = true) { menu = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun RecoveryNoteSize(bytes: Long) {
    val context = LocalContext.current
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.background) {
        Text(formatNoteSize(context, bytes), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedItem
import com.example.notes.*
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.*

/** The note body is content inside the existing DetailScreen, never a second screen. */
@Composable
fun UnifiedNoteBody(item: SavedItem, viewModel: CobaltViewModel, plain: Boolean, jump: Long,
    modifier: Modifier, header: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(item.id) { mutableStateOf(false) }
    var failure by remember(item.id) { mutableStateOf<String?>(null) }
    Column(modifier) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val hash = item.contentHash
        if (item.bodyMissing || hash == null) LazyColumn(Modifier.fillMaxSize()) {
            item { header() }
            item { Text("The note body is unavailable. Its record is retained. See Settings → Data Migration.") }
        } else key(item.id, hash, plain) {
            if (!plain && item.byteLength < NoteSizePolicy.TIER_B_MAX_BYTES) LazyMarkdownNote(item.id, hash,
                item.byteLength, jump, Modifier.weight(1f).fillMaxWidth(), header = header,
                onBlockChange = if (busy) null else { start, end, updated ->
                    busy = true
                    scope.launch {
                        try {
                            val drafts = SectionDraftStore(context)
                            val entry = drafts.save(item.id, hash, start, end, updated)
                            val saved = drafts.apply(item.id, entry)
                            viewModel.noteGenerationHistory(item.id).record(NoteGeneration(hash, item.byteLength, item.preview),
                                NoteGeneration(saved.contentHash!!, saved.byteLength, saved.preview))
                            viewModel.showDetailItem(saved)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failure = "Could not save this change. The note and recovery draft are retained." }
                        finally { busy = false }
                    }
                })
            else NoteBodyPlainText(remember { LegacyNoteRecovery(context) }, RecoveryNote(item.id, item.title, item.byteLength),
                Modifier.weight(1f).fillMaxWidth(), jump, header)
        }
    }
}

@Composable
fun NoteDetailActions(item: SavedItem, viewModel: CobaltViewModel, plain: Boolean,
    onPlainChange: (Boolean) -> Unit, onJump: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persistence = remember { NotePersistence(context) }
    var menu by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var lastMatch by remember(item.contentHash) { mutableStateOf<Long?>(null) }
    var headings by remember { mutableStateOf<List<NoteTextNavigator.Heading>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf<String?>(null) }
    var work by remember { mutableStateOf<Job?>(null) }
    val history = viewModel.noteGenerationHistory(item.id)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            busy = true
            work = scope.launch {
                try { withContext(Dispatchers.IO) {
                    val current = persistence.load(item.id, false) ?: error("Note missing")
                    val cancellation = currentCoroutineContext()
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        NoteExport.write(current, persistence.store, it) { cancellation.ensureActive() }
                    } ?: error("Destination unavailable")
                } } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { failureMessage = "Export failed. The selected file may be incomplete; the original note is retained." }
                finally { busy = false }
            }
        }
    }
    fun restore(undo: Boolean) {
        val change = (if (undo) history.undoChange else history.redoChange) ?: return
        busy = true
        work = scope.launch {
            try {
                val saved = persistence.restoreGeneration(item.id, if (undo) change.after.hash else change.before.hash,
                    if (undo) change.before else change.after)
                if (undo) history.didUndo(change) else history.didRedo(change)
                viewModel.showDetailItem(saved)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failureMessage = "Could not restore this saved version. Current note retained." }
            finally { busy = false }
        }
    }
    Box {
        IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "More note options") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Find in note") }, onClick = { menu = false; search = true })
            DropdownMenuItem(text = { Text("Outline") }, onClick = {
                menu = false; busy = true
                work = scope.launch {
                    try { headings = withContext(Dispatchers.IO) {
                        val cancellation = currentCoroutineContext()
                        persistence.store.open(item.id, item.contentHash ?: error("Body unavailable")).use {
                            NoteTextNavigator.headings(it) { cancellation.ensureActive() }
                        }
                    } } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { failureMessage = "Could not read the outline." }
                    finally { busy = false }
                }
            })
            DropdownMenuItem(text = { Text("Export Markdown") }, onClick = {
                menu = false
                export.launch(item.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).ifBlank { "note" } + ".md")
            })
            if (item.byteLength < NoteSizePolicy.TIER_B_MAX_BYTES) DropdownMenuItem(text = { Text(if (plain) "Read as Markdown" else "Read as plain text") },
                onClick = { menu = false; onPlainChange(!plain) })
            DropdownMenuItem(text = { Text("Undo last saved edit") }, enabled = history.undoChange != null,
                onClick = { menu = false; restore(true) })
            DropdownMenuItem(text = { Text("Redo saved edit") }, enabled = history.redoChange != null,
                onClick = { menu = false; restore(false) })
        }
    }
    if (search) AlertDialog(onDismissRequest = { if (!busy) search = false }, title = { Text("Find in note") }, text = {
        Column {
            OutlinedTextField(query, onValueChange = { if (it.length <= 256) { query = it; lastMatch = null } },
                singleLine = true, label = { Text("Case-sensitive text") }, enabled = !busy)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(enabled = query.isNotEmpty() && !busy, onClick = {
        busy = true
        work = scope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    val cancellation = currentCoroutineContext()
                    persistence.store.open(item.id, item.contentHash ?: error("Body unavailable")).use {
                        NoteTextNavigator.find(it, query, lastMatch?.plus(1) ?: 0) { cancellation.ensureActive() }
                    }
                }
                lastMatch = found
                if (found != null) { onJump(found); search = false } else failureMessage = "No further match. Next search starts at the beginning."
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failureMessage = "Search failed; note retained." }
            finally { busy = false }
        }
    }) { Text("Find next") } }, dismissButton = { TextButton(onClick = { work?.cancel(); busy = false; search = false }) { Text("Close") } })
    headings?.let { outline -> AlertDialog(onDismissRequest = { headings = null }, title = { Text("Outline") }, text = {
        if (outline.isEmpty()) Text("No headings found.") else LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(outline.size) { i -> TextButton(onClick = { onJump(outline[i].offset); headings = null }) { Text(outline[i].title, maxLines = 2) } }
        }
    }, confirmButton = { TextButton(onClick = { headings = null }) { Text("Close") } }) }
    failureMessage?.let { message -> AlertDialog(onDismissRequest = { failureMessage = null }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { failureMessage = null }) { Text("OK") } }) }
}

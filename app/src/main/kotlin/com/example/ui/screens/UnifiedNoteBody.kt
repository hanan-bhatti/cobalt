package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SavedItem
import com.example.notes.*
import com.example.ui.components.NoteActionsBottomSheet
import com.example.ui.components.NoteOutlineBottomSheet
import com.example.ui.components.bounceClick
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.*

/** The note body is content inside the existing DetailScreen, never a second screen. */
@Composable
fun UnifiedNoteBody(item: SavedItem, viewModel: CobaltViewModel, plain: Boolean, jump: Long,
    modifier: Modifier, header: @Composable () -> Unit,
    searchQuery: String? = null, activeMatchByte: Long? = null,
    noteListState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(item.id) { mutableStateOf(false) }
    var failure by remember(item.id) { mutableStateOf<String?>(null) }
    Column(modifier) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (item.byteLength >= NoteSizePolicy.SOFT_MAX_BYTES) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Large document (${NoteSizePolicy.formatBytes(item.byteLength)}). Rendered in streaming mode. Live editing is locked for single notes above 50 MB to prevent app memory crashes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        val hash = item.contentHash
        if (item.bodyMissing || hash == null) LazyColumn(Modifier.fillMaxSize(), state = noteListState) {
            item { header() }
            item { Text("The note body is unavailable. Its record is retained. See Settings → Data Migration.") }
        } else key(item.id, hash, plain) {
            if (!plain) LazyMarkdownNote(item.id, hash,
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
                },
                searchQuery = searchQuery,
                activeMatchByte = activeMatchByte,
                state = noteListState)
            else NoteBodyPlainText(remember { LegacyNoteRecovery(context) }, RecoveryNote(item.id, item.title, item.byteLength),
                Modifier.weight(1f).fillMaxWidth(), jump, header,
                listState = noteListState,
                searchQuery = searchQuery,
                activeMatchByte = activeMatchByte)
        }
    }
}

@Composable
fun NoteDetailActions(
    item: SavedItem,
    viewModel: CobaltViewModel,
    plain: Boolean,
    onPlainChange: (Boolean) -> Unit,
    onJump: (Long) -> Unit,
    onStartSearch: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persistence = remember { NotePersistence(context) }
    var menu by remember { mutableStateOf(false) }
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

    IconButton(
        onClick = { menu = true },
        enabled = !busy,
        modifier = Modifier.bounceClick().testTag("detail_more_options_button")
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_custom_more_vert),
            contentDescription = "More note options",
            modifier = Modifier.size(22.dp)
        )
    }

    if (menu) {
        NoteActionsBottomSheet(
            item = item,
            plain = plain,
            onPlainChange = onPlainChange,
            canUndo = history.undoChange != null && !busy,
            canRedo = history.redoChange != null && !busy,
            onUndo = { restore(true) },
            onRedo = { restore(false) },
            onFindInNote = { onStartSearch() },
            onShowOutline = {
                busy = true
                work = scope.launch {
                    try {
                        headings = withContext(Dispatchers.IO) {
                            val cancellation = currentCoroutineContext()
                            persistence.store.open(item.id, item.contentHash ?: error("Body unavailable")).use {
                                NoteTextNavigator.headings(it) { cancellation.ensureActive() }
                            }
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { failureMessage = "Could not read the outline." }
                    finally { busy = false }
                }
            },
            onExportMarkdown = {
                export.launch(item.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).ifBlank { "note" } + ".md")
            },
            onShare = {
                viewModel.shareItem(item, context)
            },
            onDelete = {
                onDeleteClick?.invoke()
            },
            onDismissRequest = { menu = false }
        )
    }

    headings?.let { outline ->
        NoteOutlineBottomSheet(
            outline = outline,
            onSelectHeading = { offset ->
                onJump(offset)
                headings = null
            },
            onDismissRequest = { headings = null }
        )
    }

    failureMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { failureMessage = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failureMessage = null }) { Text("OK") } }
        )
    }
}

package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.notes.ContinuousNoteDocument
import com.example.notes.LegacyNoteRecovery
import com.example.notes.NoteBodyMetadata
import com.example.notes.NoteExport
import com.example.notes.PrivateMarkdownStore
import com.example.notes.RecoveryNote
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.components.ContinuousMarkdownView
import com.example.ui.components.NoteBodySkeleton
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** One frameless native editor, with a viewport independent of the document's total height. */
@Composable
internal fun ContinuousNoteEditor(viewModel: CobaltViewModel, modifier: Modifier,
    header: @Composable () -> Unit, footer: @Composable () -> Unit, preview: Boolean = false) {
    val state by viewModel.fileEditorState.collectAsState()
    val activeItem by viewModel.activeCaptureItem.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recovery = remember(context) { LegacyNoteRecovery(context) }
    var plain by remember(state.id) { mutableStateOf(false) }
    val document = state.document
    var native by remember(state.id) { mutableStateOf<ContinuousMarkdownView?>(null) }
    var tool by remember(state.id) { mutableStateOf<String?>(null) }
    var message by remember(state.id) { mutableStateOf<String?>(null) }
    var pasteProgress by remember(state.id) { mutableStateOf<Float?>(null) }
    var previewReady by remember(state.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val item = activeItem
        val ref = state.generation
        if (uri != null && item != null && ref != null && item.id == state.id) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val cancellation = kotlin.coroutines.coroutineContext
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        NoteExport.write(item.copy(contentHash = ref.hash, byteLength = ref.bytes, bodyLocation = "FILE",
                            content = "", bodyLoaded = false), PrivateMarkdownStore(context), output) { cancellation.ensureActive() }
                    } ?: error("Destination unavailable")
                }
                message = "Markdown exported"
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { message = "Export did not finish. The complete note is retained." }
        }
    }

    LaunchedEffect(state.id, state.generation?.hash, state.busy) {
        if (state.document == null && !state.busy && state.error == null) viewModel.enableContinuousEditor()
    }
    LaunchedEffect(preview, document) {
        previewReady = false
        if (preview && document != null) {
            document.freeze(true)
            viewModel.checkpointFileEditor()
            previewReady = !viewModel.fileEditorState.value.dirty
        } else document?.freeze(false)
    }

    Column(modifier.testTag("capture_continuous_editor")) {
        header()
        state.error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
        message?.let { Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        pasteProgress?.let { progress ->
            Row(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).padding(top = 14.dp))
                TextButton(onClick = { native?.cancelPaste() }) { Text("Cancel") }
            }
        }
        if (tool == "find") ContinuousFindBar(
            onFind = { query, backwards -> native?.find(query, backwards) },
            onReplace = { query, replacement, all -> native?.find(query, replacement = replacement, all = all) },
            onClose = { tool = null })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val body = state.generation
            if (document == null && plain && body != null) {
                NoteBodyPlainText(recovery, RecoveryNote(state.id!!, activeItem?.title.orEmpty(), body.bytes), Modifier.fillMaxSize(),
                    body = NoteBodyMetadata(state.id!!, "FILE", body.hash, body.bytes, body.preview, 1, 0))
            } else if (document == null) {
                if (state.error == null) NoteBodySkeleton()
                else if (state.generation != null && !state.busy) {
                    Column {
                        Row {
                            TextButton(onClick = { plain = true }) { Text("Read plain text") }
                            TextButton(onClick = { export.launch("${activeItem?.title.orEmpty().take(80).ifBlank { "Note" }}.md") }) { Text("Export") }
                        }
                        TextButton(onClick = viewModel::enableContinuousEditor) { Text("Retry editing") }
                    }
                } else Text("The saved note is retained. Reopen it or use Note Recovery.", style = MaterialTheme.typography.bodyMedium)
            } else if (preview && previewReady && state.generation != null) {
                val ref = state.generation!!
                LazyMarkdownNote(state.id!!, ref.hash, ref.bytes, 0, Modifier.fillMaxSize(),
                    onBlockChange = if (state.busy) null else { start, end, value ->
                        viewModel.replaceContinuousNoteRange(start, end, ref.hash, value)
                    })
            } else {
                key(document) {
                    AndroidView(
                        factory = { context -> ContinuousMarkdownView(context).also { native = it } },
                        modifier = Modifier.fillMaxSize().testTag("capture_content_input"),
                        onRelease = { view -> view.dispose(); if (native === view) native = null },
                        update = { view ->
                            view.message = { message = it }
                            view.pasteProgress = { pasteProgress = it; viewModel.continuousEditorBusy(document, it != null) }
                            view.onTool = { tool = it }
                            view.bind(document) { viewModel.continuousEditorChanged(document) }
                            view.isEditable = !state.busy && !preview && pasteProgress == null && !document.frozen
                            view.applyColors(colors.background.toArgb(), colors.onBackground.toArgb(),
                                colors.primary.toArgb(), colors.onSurfaceVariant.toArgb(), colors.primary.copy(alpha = 0.22f).toArgb())
                        })
                }
            }
        }
        footer()
    }
    if (tool == "outline" && document != null) ContinuousOutline(document,
        onJump = { native?.jumpToHeading(it); tool = null }, onClose = { tool = null })
}

@Composable
private fun ContinuousFindBar(onFind: (String, Boolean) -> Unit,
    onReplace: (String, String, Boolean) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(query, {
            if (it.length <= 256) { query = it; validation = null }
            else validation = "Search terms are limited to 256 characters."
        }, singleLine = true, label = { Text("Find in note") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(replacement, {
            if (it.length <= 4096) { replacement = it; validation = null }
            else validation = "Replacement text is limited to 4,096 characters."
        }, singleLine = true,
            label = { Text("Replace with") }, modifier = Modifier.fillMaxWidth())
        validation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row {
            TextButton(onClick = { onFind(query, true) }, enabled = query.isNotEmpty()) { Text("Previous") }
            TextButton(onClick = { onFind(query, false) }, enabled = query.isNotEmpty()) { Text("Next") }
            TextButton(onClick = { onReplace(query, replacement, false) }, enabled = query.isNotEmpty()) { Text("Replace") }
        }
        Row {
            TextButton(onClick = { onReplace(query, replacement, true) }, enabled = query.isNotEmpty()) { Text("Replace all") }
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinuousOutline(document: ContinuousNoteDocument, onJump: (Int) -> Unit, onClose: () -> Unit) {
    val headings by produceState<List<Pair<Int, String>>?>(null, document, document.revision) {
        value = withContext(Dispatchers.Default) {
            val snapshot = document.snapshot()
            try {
                buildList {
                    var fence: Char? = null
                    var fenceLength = 0
                    for (index in 0 until snapshot.lineCount) {
                        ensureActive()
                        val line = snapshot.getLine(index)
                        val raw = line.subSequence(0, minOf(line.length, 1024)).toString()
                        val indent = raw.takeWhile { it == ' ' }.length
                        if (indent > 3) continue
                        val prefix = raw.drop(indent)
                        val marker = prefix.firstOrNull()
                        val run = if (marker == '`' || marker == '~') prefix.takeWhile { it == marker }.length else 0
                        if (run >= 3) {
                            if (fence == null) { fence = marker; fenceLength = run }
                            else if (fence == marker && run >= fenceLength && prefix.drop(run).isBlank()) fence = null
                            continue
                        }
                        if (fence != null) continue
                        val level = prefix.takeWhile { it == '#' }.length
                        if (level in 1..6 && prefix.getOrNull(level)?.isWhitespace() == true) {
                            add(index to prefix.drop(level).trim())
                            if (size >= 500) break
                        }
                    }
                }
            } finally { snapshot.release() }
        }
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Text("Outline", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        Text("Up to 500 headings", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp))
        val current = headings
        if (current == null) NoteBodySkeleton(Modifier.padding(24.dp))
        else if (current.isEmpty()) Text("No headings in this note", modifier = Modifier.padding(24.dp))
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).navigationBarsPadding()) {
            items(current, key = { it.first }) { (line, title) ->
                Text(title.ifBlank { "Untitled heading" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onJump(line) }.padding(horizontal = 24.dp, vertical = 14.dp))
            }
        }
    }
}

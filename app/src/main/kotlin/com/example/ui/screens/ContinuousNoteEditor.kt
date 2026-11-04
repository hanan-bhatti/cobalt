package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.notes.ContinuousNoteDocument
import com.example.notes.LegacyNoteRecovery
import com.example.notes.NoteBodyMetadata
import com.example.notes.NoteExport
import com.example.notes.NoteTextNavigator
import com.example.notes.PrivateMarkdownStore
import com.example.notes.RecoveryNote
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.components.ContinuousMarkdownView
import com.example.ui.components.DiskMarkdownView
import com.example.ui.components.applyCobaltTypography
import com.example.ui.components.NoteBodySkeleton
import com.example.ui.components.NoteOutlineBottomSheet
import com.example.ui.viewmodel.CobaltViewModel
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** One frameless native editor, with a viewport independent of the document's total height. */
@Composable
internal fun ContinuousNoteEditor(viewModel: CobaltViewModel, modifier: Modifier,
    header: @Composable () -> Unit, footer: @Composable () -> Unit, preview: Boolean = false,
    localHazeState: HazeState? = null) {
    val state by viewModel.fileEditorState.collectAsState()
    val activeItem by viewModel.activeCaptureItem.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recovery = remember(context) { LegacyNoteRecovery(context) }
    var plain by remember(state.id) { mutableStateOf(false) }
    val document = state.document
    var native by remember(state.id) { mutableStateOf<ContinuousMarkdownView?>(null) }
    var diskNative by remember(state.id) { mutableStateOf<DiskMarkdownView?>(null) }
    var tool by remember(state.id) { mutableStateOf<String?>(null) }
    var message by remember(state.id) { mutableStateOf<String?>(null) }
    var pasteProgress by remember(state.id) { mutableStateOf<Float?>(null) }
    var previewReady by remember(state.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    var exportingId by remember(state.id) { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val item = activeItem
        val ref = state.generation
        val requested = exportingId
        exportingId = null
        if (uri != null && item != null && ref != null && item.id == state.id && item.id == requested) {
            // Retain the COW snapshot before the editor can close while IO is dispatched.
            val disk = state.document?.disk
            val exportSnapshot = try { if (disk == null) state.document?.snapshot() else null }
            catch (_: OutOfMemoryError) { message = "Not enough memory to prepare export. Retry after closing other apps."; null }
            catch (_: Exception) { message = "The editor changed before export. Retry from this note."; null }
            val requiresSnapshot = state.document != null && disk == null
            if (!requiresSnapshot || exportSnapshot != null) {
                val exportJob = scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val cancellation = kotlin.coroutines.coroutineContext
                            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                                if (disk != null) {
                                    disk.openRecoveryUtf8().use { input -> NoteExport.writeDraft(item, input, output) { cancellation.ensureActive() } }
                                } else if (exportSnapshot != null) {
                                    ContinuousNoteDocument.stream(exportSnapshot).use { input ->
                                        NoteExport.writeDraft(item, input, output) { cancellation.ensureActive() }
                                    }
                                } else NoteExport.write(item.copy(contentHash = ref.hash, byteLength = ref.bytes,
                                    bodyLocation = "FILE", content = "", bodyLoaded = false),
                                    PrivateMarkdownStore(context), output) { cancellation.ensureActive() }
                            } ?: error("Destination unavailable")
                        }
                        message = "Markdown exported"
                    } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                    catch (_: Exception) { message = "Export did not finish. The original is retained; the destination may be incomplete." }
                }
                exportJob.invokeOnCompletion { exportSnapshot?.release() }
            }
        } else if (uri != null) message = "The selected note changed. Start export again from that note."
    }
    fun beginExport(name: String) { exportingId = state.id; export.launch(name) }

    LaunchedEffect(state.id, state.generation?.hash, state.busy) {
        if (state.document == null && !state.busy && state.error == null) viewModel.enableContinuousEditor()
    }
    LaunchedEffect(preview, document, state.busy) {
        previewReady = false
        if (state.busy) return@LaunchedEffect
        if (preview && document != null) {
            document.freeze(true)
            viewModel.checkpointFileEditor()
            previewReady = !viewModel.fileEditorState.value.dirty
        } else document?.freeze(false)
    }

    Column(modifier.testTag("capture_continuous_editor")) {
        header()
        state.error?.let {
            Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall)
            if (document != null) TextButton(onClick = {
                beginExport("${activeItem?.title.orEmpty().take(80).ifBlank { "Note" }}-unsaved.md")
            }) { Text("Export unsaved edits") }
        }
        message?.let { Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        pasteProgress?.let { progress ->
            Row(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).padding(top = 14.dp))
                TextButton(onClick = { if (document?.disk != null) diskNative?.cancelPaste() else native?.cancelPaste() }) { Text("Cancel") }
            }
        }
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
                            TextButton(onClick = { beginExport("${activeItem?.title.orEmpty().take(80).ifBlank { "Note" }}.md") }) { Text("Export") }
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
            } else if (document.disk != null) {
                val typo = com.example.ui.theme.LocalCobaltTypography.current
                key(document) {
                    AndroidView(factory = { DiskMarkdownView(it).also { view -> diskNative = view } },
                        modifier = Modifier.fillMaxSize().testTag("capture_content_input"),
                        onRelease = { view -> view.dispose(); if (diskNative === view) diskNative = null },
                        update = { view ->
                            view.applyCobaltTypography(typo, activeItem?.type == com.example.data.model.SavedItemType.CODE)
                            view.message = { message = it }
                            view.progress = { pasteProgress = it; viewModel.continuousEditorBusy(document, it != null) }
                            view.tool = { tool = it }
                            view.bind(document)
                            view.setHostEditable(!state.busy && !preview && pasteProgress == null && !document.frozen)
                            view.applyColors(colors.background.toArgb(), colors.onBackground.toArgb(), colors.primary.toArgb(),
                                colors.onSurfaceVariant.toArgb(), colors.primary.copy(alpha = 0.22f).toArgb())
                        })
                }
            } else {
                val typo = com.example.ui.theme.LocalCobaltTypography.current
                val isCode = activeItem?.type == com.example.data.model.SavedItemType.CODE
                key(document) {
                    AndroidView(
                        factory = { context -> ContinuousMarkdownView(context).also { native = it } },
                        modifier = Modifier.fillMaxSize().testTag("capture_content_input"),
                        onRelease = { view -> view.dispose(); if (native === view) native = null },
                        update = { view ->
                            view.applyCobaltTypography(typo, isCode)
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

            // Modern Floating Search & Replace Bar
            androidx.compose.animation.AnimatedVisibility(
                visible = tool == "find",
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                EditorFloatingSearchReplaceBar(
                    localHazeState = localHazeState,
                    onFind = { query, backwards ->
                        if (document?.disk != null) diskNative?.find(query, backwards)
                        else native?.find(query, backwards)
                    },
                    onReplace = { query, replacement, all ->
                        if (document?.disk != null) diskNative?.find(query, replacement = replacement, all = all)
                        else native?.find(query, replacement = replacement, all = all)
                    },
                    onClose = { tool = null }
                )
            }
        }
        footer()
    }
    if (tool == "outline" && document != null) {
        val headings by produceState<List<NoteTextNavigator.Heading>?>(null, document, document.revision) {
            value = withContext(Dispatchers.Default) {
                val cancellation = kotlin.coroutines.coroutineContext
                if (document.disk != null) {
                    document.disk.openRecoveryUtf8().use { NoteTextNavigator.headings(it) { cancellation.ensureActive() } }
                } else {
                    val snapshot = document.snapshot()
                    try {
                        ContinuousNoteDocument.stream(snapshot).use { NoteTextNavigator.headings(it) { cancellation.ensureActive() } }
                    } finally {
                        snapshot.release()
                    }
                }
            }
        }
        NoteOutlineBottomSheet(
            outline = headings ?: emptyList(),
            onSelectHeading = { offset ->
                if (document.disk != null) {
                    diskNative?.jumpToCharacter(offset.toInt())
                } else {
                    native?.jumpToCharacter(offset.toInt())
                }
                tool = null
            },
            onDismissRequest = { tool = null }
        )
    }
}

@Composable
private fun EditorFloatingSearchReplaceBar(
    localHazeState: HazeState?,
    onFind: (String, Boolean) -> Unit,
    onReplace: (String, String, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(query) {
        if (query.isNotEmpty()) {
            kotlinx.coroutines.delay(250)
            onFind(query, false)
        }
    }

    val containerModifier = if (localHazeState != null) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .hazeEffect(
                state = localHazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.02f
                )
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.5f),
                RoundedCornerShape(24.dp)
            )
    } else {
        Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.4f),
                RoundedCornerShape(24.dp)
            )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .then(containerModifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Find Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Find in note...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            if (it.length <= 256) {
                                query = it
                                validation = null
                            } else {
                                validation = "Search terms are limited to 256 characters."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (query.isNotEmpty()) onFind(query, false) })
                    )
                }

                // Previous match (Up)
                IconButton(
                    onClick = { if (query.isNotEmpty()) onFind(query, true) },
                    enabled = query.isNotEmpty(),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_chevron_up),
                        contentDescription = "Previous match",
                        tint = if (query.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Next match (Down)
                IconButton(
                    onClick = { if (query.isNotEmpty()) onFind(query, false) },
                    enabled = query.isNotEmpty(),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_chevron_down),
                        contentDescription = "Next match",
                        tint = if (query.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Toggle Replace Row
                IconButton(
                    onClick = { showReplace = !showReplace },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_sync),
                        contentDescription = "Toggle Replace",
                        tint = if (showReplace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Replace Row (Expands when showReplace is true)
            AnimatedVisibility(
                visible = showReplace,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_edit),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (replacement.isEmpty()) {
                                Text(
                                    text = "Replace with...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            BasicTextField(
                                value = replacement,
                                onValueChange = {
                                    if (it.length <= 4096) {
                                        replacement = it
                                        validation = null
                                    } else {
                                        validation = "Replacement text is limited to 4,096 characters."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                            )
                        }

                        // Replace one button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (query.isNotEmpty()) 0.8f else 0.3f),
                            modifier = Modifier.clickable(enabled = query.isNotEmpty()) {
                                onReplace(query, replacement, false)
                            }
                        ) {
                            Text(
                                text = "Replace",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (query.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        // Replace all button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (query.isNotEmpty()) 0.8f else 0.3f),
                            modifier = Modifier.clickable(enabled = query.isNotEmpty()) {
                                onReplace(query, replacement, true)
                            }
                        ) {
                            Text(
                                text = "All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (query.isNotEmpty()) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            validation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
        }
    }
}

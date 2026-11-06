package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.CobaltViewModel
import java.text.NumberFormat

/** Import belongs to Add. One activity-level sheet also handles incoming shared files. */
@Composable
fun rememberTextImportAction(viewModel: CobaltViewModel): () -> Unit {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importMarkdown(uri)
    }
    return {
        picker.launch(arrayOf("text/*", "application/json", "application/xml", "application/yaml",
            "application/x-yaml", "application/toml", "application/javascript", "application/mdx", "application/octet-stream"))
    }
}

@Composable
fun TextDocumentImportSheetHost(viewModel: CobaltViewModel) {
    val state by viewModel.markdownImport.collectAsState()
    if (!state.visible) return
    TextDocumentImportSheet(state, viewModel::dismissMarkdownImport, viewModel::cancelMarkdownImport,
        onOpen = {
            state.importedItem?.let { item -> viewModel.dismissMarkdownImport(); viewModel.showDetailItem(item) }
        }, onRetry = viewModel::retryMarkdownImport, onSplit = viewModel::splitMarkdownImport)
}

/** A completed or failed import stays here until an explicit user action. No timer or auto-navigation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextDocumentImportSheet(state: CobaltViewModel.MarkdownImportState,
    onDismiss: () -> Unit, onCancel: () -> Unit, onOpen: () -> Unit, onRetry: () -> Unit, onSplit: () -> Unit) {
    val running by rememberUpdatedState(state.running)
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = { next -> !running || next != SheetValue.Hidden })
    var confirmSplit by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val title = when {
        state.running -> if (state.partsSaved > 0) "Splitting document" else "Importing document"
        state.cancelled -> "Import cancelled"
        state.failed && state.documentsSaved > 0 -> "Some files weren't imported"
        state.failed && state.canSplit -> "Document too large"
        state.failed -> "Couldn't finish importing"
        state.partsSaved > 0 -> "Notes imported"
        else -> "Import complete"
    }
    ModalBottomSheet(onDismissRequest = { if (!running) onDismiss() }, sheetState = sheetState,
        containerColor = colors.surface, contentColor = colors.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)
            .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, Modifier.testTag("import_status_title"), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                state.fileName?.let { name -> Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = if (state.failed) colors.errorContainer else colors.surfaceContainer,
                    shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.running) {
                            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).testTag("import_progress"))
                            Text(if (state.documentsTotal > 1) state.message ?: "Importing documents…"
                                else if (state.partsSaved > 0) "Saving part ${state.partsSaved + 1}…" else "Reading and saving your document…",
                                style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(state.message ?: "Your document is ready.", Modifier.testTag("import_result_message"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.failed) colors.onErrorContainer else colors.onSurface)
                        }
                        if (state.bytes > 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (state.running) "Processed" else if (state.failed) "Read before stopping" else "Imported",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.failed) colors.onErrorContainer else colors.onSurfaceVariant)
                            Text(formatImportSize(state.bytes), style = MaterialTheme.typography.labelLarge,
                                color = if (state.failed) colors.onErrorContainer else colors.primary)
                        }
                        if (state.partsSaved > 0) Text("${state.partsSaved} parts saved", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (confirmSplit && !state.running) {
                    Text("Import all text as notes of up to 50 MB each? Your source file stays unchanged.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { confirmSplit = false }, modifier = Modifier.weight(1f)) { Text("Not now") }
                        Button(onClick = { confirmSplit = false; onSplit() }, modifier = Modifier.weight(1f)) { Text("Split now") }
                    }
                } else if (state.canSplit && !state.running) {
                    OutlinedButton(onClick = { confirmSplit = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(20.dp)) { Text("Split into notes") }
                }
            }
            if (state.running) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(20.dp)) { Text("Cancel import") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(20.dp)) { Text("Done") }
                    if (state.importedItem != null) {
                        Button(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            shape = RoundedCornerShape(20.dp)) { Text("Open note") }
                    } else if ((state.failed || state.cancelled) && state.documentsTotal == 1 && !state.canSplit) {
                        Button(onClick = onRetry, modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            shape = RoundedCornerShape(20.dp)) { Text("Try again") }
                    }
                }
            }
        }
    }
}

/** Decimal units match the 50/55 MB note policy; processed bytes aren't a guessed percentage. */
internal fun formatImportSize(bytes: Long): String {
    val amount = bytes.coerceAtLeast(0)
    if (amount < 1000) return "$amount B"
    val (unit, divisor) = when { amount < 1_000_000 -> "KB" to 1000.0; amount < 1_000_000_000 -> "MB" to 1_000_000.0; else -> "GB" to 1_000_000_000.0 }
    val format = NumberFormat.getNumberInstance().apply { minimumFractionDigits = 0; maximumFractionDigits = 1 }
    return "${format.format(amount / divisor)} $unit"
}

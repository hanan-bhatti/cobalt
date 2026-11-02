package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.ui.viewmodel.CobaltViewModel

/** Import belongs to Add, not inside an editor's writing surface. */
@Composable
fun rememberTextImportAction(viewModel: CobaltViewModel): () -> Unit {
    var visible by rememberSaveable { mutableStateOf(false) }
    var confirmSplit by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.markdownImport.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { visible = true; viewModel.importMarkdown(uri) }
    }
    if (visible) AlertDialog(onDismissRequest = { if (!state.running) visible = false },
        title = { Text("Import text document") },
        text = {
            Column {
                if (state.running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Importing ${state.bytes} bytes…")
                } else Text(state.message ?: "Markdown, MDX, TXT and other text documents are supported.")
            }
        },
        confirmButton = {
            if (state.running) TextButton(onClick = viewModel::cancelMarkdownImport) { Text("Cancel") }
            else TextButton(onClick = { visible = false }) { Text("Done") }
        },
        dismissButton = {
            if (state.canSplit && !state.running) TextButton(onClick = { confirmSplit = true }) { Text("Split into notes") }
        })
    if (confirmSplit) AlertDialog(onDismissRequest = { confirmSplit = false }, title = { Text("Split this document?") },
        text = { Text("This creates multiple notes and may divide paragraphs, tables or code fences. Completed parts are kept if cancelled. Your source file is not changed.") },
        confirmButton = { TextButton(onClick = { confirmSplit = false; viewModel.splitMarkdownImport() }) { Text("Split into notes") } },
        dismissButton = { TextButton(onClick = { confirmSplit = false }) { Text("Cancel") } })
    return { picker.launch(arrayOf("text/*", "application/json", "application/xml", "application/yaml",
        "application/x-yaml", "application/toml", "application/javascript", "application/mdx", "application/octet-stream")) }
}

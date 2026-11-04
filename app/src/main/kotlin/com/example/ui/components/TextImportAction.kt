package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.notes.NoteSizePolicy
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

    if (visible) {
        val readableSize = remember(state.bytes) { NoteSizePolicy.formatBytes(state.bytes) }
        val isOversize = state.bytes > NoteSizePolicy.SOFT_MAX_BYTES

        AlertDialog(
            onDismissRequest = { if (!state.running) visible = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_import),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when {
                            state.running -> "Importing text document"
                            state.canSplit && state.bytes >= NoteSizePolicy.IMPORT_MAX_BYTES -> "Document Exceeds 55 MB"
                            state.canSplit -> "Large Document Imported"
                            else -> "Import Document"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.running) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.partsSaved > 0) "Importing part ${state.partsSaved + 1}…" else "Importing content…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = readableSize,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (isOversize) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_info),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Document is large ($readableSize). Files up to 55 MB can be imported in streaming mode, but live rich editing is locked to prevent app lag.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else if (state.bytes > NoteSizePolicy.TIER_B_MAX_BYTES) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_info),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Large document ($readableSize). High-performance streaming mode is active.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    } else {
                        if (state.bytes > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Document size: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = readableSize,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = state.message ?: "Markdown, MDX, TXT and other text documents are supported.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                if (state.running) {
                    TextButton(onClick = viewModel::cancelMarkdownImport) {
                        Text("Cancel")
                    }
                } else if (state.importedItem != null) {
                    Button(onClick = {
                        val item = state.importedItem
                        visible = false
                        if (item != null) viewModel.showDetailItem(item)
                    }) {
                        Text("Open note")
                    }
                } else {
                    Button(onClick = { visible = false }) {
                        Text("Done")
                    }
                }
            },
            dismissButton = {
                if (state.canSplit && !state.running) {
                    OutlinedButton(onClick = { confirmSplit = true }) {
                        Text("Split into notes")
                    }
                }
            }
        )
    }

    if (confirmSplit) {
        AlertDialog(
            onDismissRequest = { confirmSplit = false },
            title = { Text("Split document into notes?") },
            text = {
                Text("This partitions the large document into multiple linked parts (each under 50 MB) so they can be smoothly viewed and edited on any device. Your original source file is untouched.")
            },
            confirmButton = {
                Button(onClick = { confirmSplit = false; viewModel.splitMarkdownImport() }) {
                    Text("Split now")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSplit = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    return {
        picker.launch(
            arrayOf(
                "text/*",
                "application/json",
                "application/xml",
                "application/yaml",
                "application/x-yaml",
                "application/toml",
                "application/javascript",
                "application/mdx",
                "application/octet-stream"
            )
        )
    }
}

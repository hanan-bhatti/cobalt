package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.notes.NoteMigrationManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteMigrationScreen(
    startup: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onRecovery: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by NoteMigrationManager.status.collectAsState()
    var showPrompt by remember { mutableStateOf(false) }
    val running = status.phase == NoteMigrationManager.Phase.RUNNING
    LaunchedEffect(Unit) {
        NoteMigrationManager.refreshStatus(context)
        if (startup) showPrompt = NoteMigrationManager.acknowledgePrompt(context)
    }
    fun runMigration() { scope.launch { NoteMigrationManager.run(context) } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Data Migration") }, navigationIcon = {
            TextButton(onClick = onBack, enabled = !running) { Text(if (startup) "Close" else "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Markdown note storage", style = MaterialTheme.typography.headlineSmall)
            Text("Every text and code note, including one-line notes, is stored as a private Markdown file. Metadata and short previews stay in the database.")
            Text("Original bodies are kept until the file copy passes length and hash verification. Interrupted runs can be resumed without duplicating notes.")
            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Checking note ${status.verified + status.failed + 1} of ${status.total} · ${status.bytes} bytes copied")
            }
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status: ${status.phase.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    Text("Notes: ${status.total} · Converted this run: ${status.converted}")
                    Text("Verified: ${status.verified} · Need attention: ${status.failed}")
                    if (status.lastRun > 0) Text("Last run: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(status.lastRun))}")
                }
            }
            status.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { runMigration() }, enabled = !running && !showPrompt) {
                Text(if (status.phase == NoteMigrationManager.Phase.COMPLETE) "Run migration again" else "Continue migration")
            }
            if (startup && status.phase == NoteMigrationManager.Phase.COMPLETE) {
                Button(onClick = onDone) { Text("Open Cobalt") }
            }
            if (startup && !running && onRecovery != null) {
                OutlinedButton(onClick = onRecovery) { Text("Open note recovery") }
            }
            Text("This migration tool is temporary and is planned for deprecation once the storage transition is complete.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("A failed conversion never authorizes deleting the original. Retrying verifies existing files and converts any remaining notes.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (showPrompt) AlertDialog(
        onDismissRequest = {},
        title = { Text("Move your notes to Markdown storage") },
        text = { Text("Cobalt will verify a private Markdown copy of each note before switching storage. Keep the app open while it runs. You can review results and retry later in Settings → Data Migration. This prompt is shown once for this storage version.") },
        confirmButton = { TextButton(onClick = { showPrompt = false; runMigration() }) { Text("Migrate notes") } },
        dismissButton = { TextButton(onClick = { showPrompt = false; onBack() }) { Text("Close for now") } }
    )
}

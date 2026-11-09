package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.NoteMigrationManager
import com.example.notes.NoteMigrationManager.Phase
import com.example.ui.components.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable

@Composable
fun NoteMigrationScreen(startup: Boolean, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by NoteMigrationManager.status.collectAsState()
    var showPrompt by rememberSaveable { mutableStateOf(false) }
    var promptChecked by rememberSaveable { mutableStateOf(false) }
    val navigation = rememberNavController()
    var starting by remember { mutableStateOf(false) }
    val running = starting || status.phase == Phase.RUNNING
    val complete = status.phase == Phase.COMPLETE
    val checked = (status.verified + status.failed).coerceAtMost(status.total)
    val progress by animateFloatAsState(
        targetValue = if (complete) 1f else if (status.total > 0) checked.toFloat() / status.total else 0f,
        animationSpec = tween(250), label = "migrationProgress")
    LaunchedEffect(Unit) {
        NoteMigrationManager.refreshStatus(context)
        if (startup && !promptChecked) {
            showPrompt = NoteMigrationManager.acknowledgePrompt(context)
            promptChecked = true
        }
    }
    fun migrate() {
        if (running) return
        starting = true
        scope.launch {
            try { NoteMigrationManager.run(context) }
            finally { starting = false }
        }
    }

    NoteStorageNavHost(navigation, "migration") {
        composable("migration") {
            NoteStorageRoute(navigation, this) {
                NoteBackSurface(enabled = startup && !showPrompt, onBack = onBack) {
                    NoteSettingsPage(title = "Data Migration", subtitle = "Update and verify note storage", onBack = onBack) {
                        item {
                            SettingsSubsectionHeader(title = "Note storage",
                                description = "Check each note as it moves to the current storage format.")
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text(when (status.phase) {
                                                Phase.READY -> "Ready to move"
                                                Phase.RUNNING -> "Moving your notes"
                                                Phase.COMPLETE -> "Your notes are ready"
                                                Phase.ATTENTION -> "Some notes need attention"
                                                Phase.INTERRUPTED -> "Ready to resume"
                                            }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text(when {
                                                running && status.total > 0 -> "${status.verified} of ${status.total} notes verified"
                                                running -> "Preparing your notes…"
                                                complete -> "${status.verified} of ${status.total} notes verified"
                                                status.total > 0 -> "${status.total} notes · kept on this device"
                                                else -> "Original content is retained until verification"
                                            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (running && status.total > 0) Text("${(progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (running) {
                                        if (running && status.total == 0) LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp))
                                        else LinearProgressIndicator(progress = { progress },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                                    }
                                    Text(when {
                                        running && status.bytes > 0 -> "${formatNoteSize(context, status.bytes)} processed this run · verifying content"
                                        running -> "Original content is kept until the updated note is verified."
                                        complete -> "Everything is saved locally. You can continue using Cobalt."
                                        status.phase == Phase.ATTENTION -> "Unfinished notes and their originals are retained. Retry when ready."
                                        status.phase == Phase.INTERRUPTED -> "Continue from the last verified note."
                                        else -> "Update Markdown note storage on this device, then verify the saved content."
                                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (status.failed > 0) {
                                        Text("${status.failed} notes need attention", style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                    if (status.lastRun > 0) Text("Last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                        .format(Date(status.lastRun)), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            Button(onClick = { if (complete && startup) onDone() else migrate() }, enabled = !running,
                                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).bounceClick()) {
                                Text(when {
                                    running -> "Moving notes…"
                                    complete && startup -> "Open Cobalt"
                                    complete -> "Check notes again"
                                    status.phase == Phase.ATTENTION -> "Retry unfinished notes"
                                    status.phase == Phase.INTERRUPTED -> "Resume migration"
                                    else -> "Update notes"
                                }, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (status.errors.isNotEmpty()) item {
                            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Needs attention", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                                status.errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        item {
                            Text("This migration tool is temporary. Your Markdown notes will stay available after it is retired.",
                                modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (showPrompt) ExpressiveAlertDialog(
        onDismissRequest = { showPrompt = false; onBack() }, title = "Update note storage?",
        text = { Text("Cobalt will update note storage on this device and verify each note’s content. Original content is retained until verification. Closing for now returns to your home screen; reopen Cobalt to continue.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButtonText = "Update notes", onConfirm = { showPrompt = false; migrate() },
        dismissButtonText = "Close for now", onDismiss = { showPrompt = false; onBack() })
}

@Composable
fun NoteStorageErrorScreen(onRetry: () -> Unit, onRecovery: () -> Unit, onBack: () -> Unit) {
    NoteBackSurface(enabled = true, onBack = onBack) {
        NoteSettingsPage("Note storage", "Get back to your notes", onBack) {
            item { NoteStorageNotice("Couldn't prepare note storage", "Your notes have not been deleted. Try again, or open recovery to read and export them.") }
            item {
                Button(onClick = onRetry, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).bounceClick()) { Text("Try again") }
            }
            item {
                ExpressiveSettingsNavigationItem(title = "Note recovery", subtitle = "Read or export your saved notes", onClick = onRecovery)
            }
        }
    }
}

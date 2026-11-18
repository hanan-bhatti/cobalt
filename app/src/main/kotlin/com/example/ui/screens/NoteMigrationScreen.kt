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
import androidx.compose.ui.res.painterResource
import com.example.R
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
                    NoteSettingsPage(title = "Note storage update", subtitle = "Update and verify saved notes", onBack = onBack) {
                        item {
                            Text("This tool is temporary and will be removed soon. Your saved notes will remain available.",
                                modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item {
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    SettingsIconBadge(when {
                                        complete -> R.drawable.ic_custom_check
                                        status.phase == Phase.ATTENTION -> R.drawable.ic_custom_info
                                        else -> R.drawable.ic_custom_sync
                                    })
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(when {
                                            starting -> "Preparing notes…"
                                            else -> when (status.phase) {
                                                Phase.READY -> "Ready to update"
                                                Phase.RUNNING -> "Updating notes"
                                                Phase.COMPLETE -> "Storage is up to date"
                                                Phase.ATTENTION -> "Some notes need attention"
                                                Phase.INTERRUPTED -> "Ready to resume"
                                            }
                                        }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                        Text(when {
                                            running && status.total > 0 -> "${checked} of ${status.total} notes checked"
                                            running -> "Preparing your notes for verification."
                                            complete -> "${status.verified} notes verified"
                                            status.phase == Phase.INTERRUPTED -> "Continue the interrupted storage update."
                                            status.phase == Phase.ATTENTION -> "Retry the notes that could not be verified."
                                            else -> "Update note storage on this device and check the saved content."
                                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (status.total > 0) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            MigrationCount("Verified", status.verified, Modifier.weight(1f), false)
                                            MigrationCount("Needs attention", status.failed, Modifier.weight(1f), status.failed > 0)
                                        }
                                    }
                                    if (running) {
                                        if (running && status.total == 0) LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp))
                                        else LinearProgressIndicator(progress = { progress },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                                    }
                                    if (running && status.bytes > 0) Text(
                                        "${formatNoteSize(context, status.bytes)} processed · verifying content",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (status.phase == Phase.ATTENTION) Text(
                                        "Unfinished notes and their originals are retained. Retry when ready.",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    running -> "Updating notes…"
                                    complete && startup -> "Open Cobalt"
                                    complete -> "Check notes again"
                                    status.phase == Phase.ATTENTION -> "Retry unfinished notes"
                                    status.phase == Phase.INTERRUPTED -> "Resume update"
                                    else -> "Update notes"
                                }, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (status.errors.isNotEmpty()) item {
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Needs attention", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                    status.errors.forEach { Text(it, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer) }
                                }
                            }
                        }
                        item {
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(painterResource(R.drawable.ic_custom_info), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("What this does", style = MaterialTheme.typography.titleMedium)
                                        Text("Updates Markdown note storage locally and verifies each note. Original content is retained until verification.",
                                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
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

@Composable
private fun MigrationCount(label: String, count: Int, modifier: Modifier, needsAttention: Boolean) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp),
        color = if (needsAttention) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(java.text.NumberFormat.getIntegerInstance().format(count), style = MaterialTheme.typography.headlineMedium,
                color = if (needsAttention) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (needsAttention) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

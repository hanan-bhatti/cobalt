package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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

@Composable
fun NoteMigrationScreen(startup: Boolean, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by NoteMigrationManager.status.collectAsState()
    var showPrompt by rememberSaveable { mutableStateOf(false) }
    var promptChecked by rememberSaveable { mutableStateOf(false) }
    var recovery by rememberSaveable { mutableStateOf(false) }
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

    AnimatedContent(targetState = recovery, label = "migrationRecovery", transitionSpec = {
        (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { if (targetState) it / 10 else -it / 10 }) togetherWith
            (fadeOut(tween(140)) + slideOutHorizontally(tween(240)) { if (targetState) -it / 10 else it / 10 })
    }) { recovering ->
        if (recovering) NoteRecoveryScreen(onExitSafeMode = { recovery = false }, safeMode = false)
        else NoteBackSurface(enabled = startup && !showPrompt, onBack = onBack) {
            NoteSettingsPage(title = "Data Migration", subtitle = "Markdown storage and recovery", onBack = onBack) {
                item {
                    SettingsSubsectionHeader(title = "Note storage",
                        description = "A verified Markdown copy for every note.")
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
                                        running && status.total > 0 -> "${(checked + 1).coerceAtMost(status.total)} of ${status.total} notes"
                                        running -> "Preparing your notes…"
                                        complete -> "${status.verified} notes verified"
                                        status.total > 0 -> "${status.total} notes · kept on this device"
                                        else -> "Your originals stay safe"
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
                                running && status.bytes > 0 -> "${formatNoteSize(context, status.bytes)} copied · checking this note"
                                running -> "Originals are kept until each copy is verified."
                                complete -> "Everything is saved locally. You can continue using Cobalt."
                                status.phase == Phase.ATTENTION -> "Unfinished notes and their originals are retained. Retry when ready."
                                status.phase == Phase.INTERRUPTED -> "Continue from the last verified note."
                                else -> "Move notes to private Markdown files without losing their content."
                            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (status.converted > 0 || status.failed > 0) {
                                Text(buildList {
                                    if (status.converted > 0) add("${status.converted} moved to Markdown")
                                    if (status.failed > 0) add("${status.failed} need attention")
                                }.joinToString(" · "), style = MaterialTheme.typography.labelLarge,
                                    color = if (status.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
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
                            else -> "Move notes"
                        }, fontWeight = FontWeight.SemiBold)
                    }
                }
                item {
                    ExpressiveSettingsNavigationItem(title = "Note recovery", subtitle = "Read or export a saved note",
                        enabled = !running, onClick = { recovery = true })
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
    if (showPrompt && !recovery) ExpressiveAlertDialog(
        onDismissRequest = { showPrompt = false; onBack() }, title = "Move your notes?",
        text = { Text("Cobalt will save a verified Markdown copy of each note on this device. Your originals are kept until the copy is ready.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButtonText = "Move notes", onConfirm = { showPrompt = false; migrate() },
        dismissButtonText = "Not now", onDismiss = { showPrompt = false; onBack() })
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

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.notes.NoteStorageManager
import com.example.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Matches Appearance's page header, connected surfaces, dynamic palette and settings transitions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteStorageManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { NoteStorageManager(context) }
    val scope = rememberCoroutineScope()
    val navigation = rememberNavController()
    var report by remember { mutableStateOf<NoteStorageManager.Report?>(null) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    fun refresh() {
        if (busy && report != null) return
        busy = true
        scope.launch {
            try { report = manager.scan() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message = "Storage could not be checked. Nothing was removed." }
            finally { busy = false }
        }
    }
    LaunchedEffect(manager) {
        try { report = manager.scan() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { message = "Storage could not be checked. Nothing was removed." }
        finally { busy = false }
    }
    NoteStorageNavHost(navigation, "storage") {
        composable("storage") {
            NoteStorageRoute(navigation, this) {
                NoteSettingsPage("Storage & Recovery", "Private notes, older copies and recovery", onBack) {
                    item {
                        SettingsSubsectionHeader("On this device", description = "File sizes, not an estimate of memory use.")
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                val value = report
                                if (value == null) {
                                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    else Text("Storage details unavailable", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    StorageSizeRow("Current Markdown notes", formatNoteSize(context, value.current))
                                    StorageSizeRow("Older full copies", formatNoteSize(context, value.older))
                                    StorageSizeRow("Drafts & recovery files", formatNoteSize(context, value.recovery))
                                    StorageSizeRow("Database", formatNoteSize(context, value.database))
                                    StorageSizeRow("Other private files", formatNoteSize(context, value.media))
                                    StorageSizeRow("App cache", formatNoteSize(context, value.cache))
                                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    item {
                        SettingsSubsectionHeader("Older copies", description = "Choose how many extra full copies to keep per note when you run cleanup.")
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NoteStorageManager.RETENTIONS.forEach { count ->
                                        FilterChip(selected = report?.copiesToKeep == count, enabled = !busy && report != null,
                                            onClick = {
                                                busy = true
                                                scope.launch {
                                                    try { manager.setRetention(count); report = manager.scan() }
                                                    catch (cancel: CancellationException) { throw cancel }
                                                    catch (_: Exception) { message = "Could not update cleanup settings. No files were removed." }
                                                    finally { busy = false }
                                                }
                                            }, label = { Text(if (count < 0) "All" else count.toString()) })
                                    }
                                }
                                Text("This choice does not delete anything. Recovery drafts and undo steps still in use are protected.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        val value = report
                        val amount = value?.removable ?: 0
                        Button(onClick = { confirming = true }, enabled = !busy && amount > 0,
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).bounceClick()) {
                            Text(if (busy) "Checking storage…" else if (amount > 0) "Review cleanup · ${formatNoteSize(context, amount)}" else "No older copies to clean")
                        }
                        TextButton(onClick = ::refresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh sizes") }
                    }
                    message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp)) } }
                    item {
                        ExpressiveSettingsNavigationItem(title = "Older copies", subtitle = "Restore or export a previous full copy",
                            enabled = !busy, onClick = { navigation.navigate("copies") { launchSingleTop = true } })
                    }
                    item {
                        ExpressiveSettingsNavigationItem(title = "Note recovery", subtitle = "Read or export your notes",
                            enabled = !busy, onClick = { navigation.navigate("recovery") { launchSingleTop = true } })
                    }
                    item {
                        NoteStorageNotice("Cleanup protects your notes",
                            "Current notes are kept. Active drafts, interrupted writes and files that cannot be safely identified are kept too. Older full copies removed here cannot be restored. Export important notes first.")
                    }
                }
            }
        }
        composable("copies") {
            NoteStorageRoute(navigation, this) { NoteOlderCopiesScreen(onBack = { navigation.popBackStack() }) }
        }
        composable("recovery") {
            NoteStorageRoute(navigation, this) {
                NoteRecoveryScreen(onExitSafeMode = { navigation.popBackStack() }, safeMode = false, navigationBack = true)
            }
        }
    }
    if (confirming) ExpressiveAlertDialog(onDismissRequest = { confirming = false },
        title = "Remove older copies?",
        text = { Text("Remove up to ${formatNoteSize(context, report?.removable ?: 0)} of older copies and completed recovery logs? Current notes and recoverable drafts are kept. This cannot be undone.") },
        confirmButtonText = "Remove copies", onConfirm = {
            confirming = false; busy = true
            val reviewed = report?.removable ?: 0
            scope.launch {
                try {
                    val removed = manager.cleanup(reviewed)
                    message = "Removed ${formatNoteSize(context, removed.bytes)} · ${removed.files} files"
                    report = manager.scan()
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { message = "Cleanup could not finish. Some reviewed copies may have been removed; current notes are retained. Refresh sizes before retrying." }
                finally { busy = false }
            }
        }, dismissButtonText = "Cancel", onDismiss = { confirming = false })
}

@Composable
private fun StorageSizeRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

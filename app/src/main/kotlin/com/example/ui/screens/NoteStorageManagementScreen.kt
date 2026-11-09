package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.notes.*
import com.example.ui.components.*
import kotlinx.coroutines.*

/** One storage hub with focused actions; the existing settings shell owns theme and predictive back. */
@Composable
fun NoteStorageManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { NoteStorageManager(context) }
    val trash = remember(context) { TrashManager(context) }
    val scope = rememberCoroutineScope()
    val navigation = rememberNavController()
    val entry by navigation.currentBackStackEntryAsState()
    var report by remember { mutableStateOf<NoteStorageManager.Report?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var reviewed by remember { mutableLongStateOf(0) }
    var reviewedFiles by remember { mutableStateOf<List<NoteStorageFile>>(emptyList()) }
    var disposableReview by remember { mutableStateOf<NoteStorageManager.DisposableReview?>(null) }
    var advancedCleanup by remember { mutableStateOf(false) }
    fun perform(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { message = action(); report = manager.scan() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message = "This action could not finish. Your current notes are retained. Refresh and try again." }
            finally { busy = false }
        }
    }
    fun refresh() = perform { "Storage sizes updated" }
    LaunchedEffect(entry?.destination?.route) {
        if (entry?.destination?.route in setOf("storage", "history", "maintenance")) {
            busy = true
            try { report = manager.scan() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message = "Storage details could not be loaded. Try Refresh." }
            finally { busy = false }
        }
    }
    fun open(route: String) { navigation.navigate(route) { launchSingleTop = true } }
    NoteStorageNavHost(navigation, "storage") {
        composable("storage") {
            NoteStorageRoute(navigation, this) {
                NoteSettingsPage("Storage", "Notes, retained data and space on this device", onBack,
                    actions = { TextButton(onClick = ::refresh, enabled = !busy) { Text("Refresh") } }) {
                    item { StorageUsageCard(report, busy) }
                    item {
                        SettingsSubsectionHeader("Your data")
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ExpressiveSettingsNavigationItem("Manage notes", onClick = { open("recovery") },
                                subtitle = "Read, export or delete your notes", shape = connectedPillShape(0, 5))
                            ExpressiveSettingsNavigationItem("Draft recovery", onClick = { open("drafts") },
                                subtitle = "Recover interrupted and unsaved edits",
                                trailingBadge = report?.let { formatNoteSize(context, it.recovery) },
                                trailingContent = if (report == null && busy) { { SkeletonBadge(46.dp) } } else null,
                                shape = connectedPillShape(1, 5))
                            ExpressiveSettingsNavigationItem("Trash", onClick = { open("trash") },
                                subtitle = "Restore items or delete them forever",
                                trailingBadge = report?.let { "${it.trashCount} · ${formatNoteSize(context, it.trash)}" },
                                trailingContent = if (report == null && busy) { { SkeletonBadge(40.dp) } } else null,
                                shape = connectedPillShape(2, 5))
                            ExpressiveSettingsNavigationItem("Version history", onClick = { open("history") },
                                subtitle = "Restore versions and review history cleanup",
                                trailingBadge = report?.let { formatNoteSize(context, it.older) },
                                trailingContent = if (report == null && busy) { { SkeletonBadge(50.dp) } } else null,
                                shape = connectedPillShape(3, 5))
                            ExpressiveSettingsNavigationItem("Unlinked files", onClick = { open("unlinked") },
                                subtitle = "Retained files without a matching note",
                                trailingBadge = report?.let { formatNoteSize(context, it.unattached) },
                                trailingContent = if (report == null && busy) { { SkeletonBadge(44.dp) } } else null,
                                shape = connectedPillShape(4, 5))
                        }
                    }
                    item {
                        SettingsSubsectionHeader("Maintenance")
                        ExpressiveSettingsNavigationItem("Cache & database", onClick = { open("maintenance") },
                            subtitle = "Clear safe cache and reclaim empty database space")
                    }
                    message?.let { text -> item { StorageMessage(text) } }
                }
            }
        }
        composable("drafts") { NoteStorageRoute(navigation, this) { CaptureDraftRecoveryScreen { navigation.popBackStack() } } }
        composable("trash") {
            NoteStorageRoute(navigation, this) { NoteTrashScreen(trash, onBack = { navigation.popBackStack() }) }
        }
        composable("unlinked") {
            NoteStorageRoute(navigation, this) { NoteUnlinkedFilesScreen(manager, onBack = { navigation.popBackStack() }) }
        }
        composable("history") {
            NoteStorageRoute(navigation, this) {
                NoteSettingsPage("Version history", "Earlier versions, with current notes kept intact", { navigation.popBackStack() }) {
                    item {
                        StorageActionCard(
                            "Saved history",
                            report?.let { formatNoteSize(context, it.older) },
                            "Versions share unchanged text. History shows additional retained data, while a version's complete note size can be much larger. Older legacy versions may use full copies or deltas. Cleanup removes only the versions you review.",
                            isLoading = report == null && busy
                        )
                    }
                    item {
                        ExpressiveSettingsSelectorItem("Versions to keep",
                            NoteStorageManager.RETENTIONS.associate { it.toString() to if (it < 0) "All versions" else if (it == 0) "No extra versions" else "$it extra versions" },
                            report?.copiesToKeep?.toString() ?: "5", onSelectionChanged = { key ->
                                if (!busy) perform { manager.setRetention(key.toInt()); "History preference updated" }
                            }, description = "Applied when you review cleanup. Versions still needed by recovery or Undo remain protected.")
                    }
                    item { ExpressiveSettingsNavigationItem("Browse versions", onClick = { open("copies") }, subtitle = "Restore or export an earlier version") }
                    item {
                        val amount = report?.removable ?: 0
                        Button(onClick = { perform {
                            reviewedFiles = manager.reviewHistory(); reviewed = reviewedFiles.sumOf { it.bytes }
                            confirmation = "history"; "Review the versions selected for removal"
                        } }, enabled = !busy && amount > 0,
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(if (amount > 0) "Review cleanup · ${formatNoteSize(context, amount)}" else "No history ready to clean")
                        }
                    }
                    message?.let { text -> item { StorageMessage(text) } }
                }
            }
        }
        composable("maintenance") {
            NoteStorageRoute(navigation, this) {
                NoteSettingsPage("Cache & database", "Temporary data and reusable database space", { navigation.popBackStack() },
                    actions = { TextButton(onClick = ::refresh, enabled = !busy) { Text("Refresh") } }) {
                    item {
                        val protectedCacheText = report?.let { formatNoteSize(context, it.protectedCache) } ?: "Protected cache"
                        StorageActionCard(
                            "App cache",
                            report?.let { formatNoteSize(context, it.cache) },
                            "Includes rebuildable previews, workspace and temporary exports. $protectedCacheText is protected or too recent to clear. Active edits, recordings and recent shares stay intact; temporary exports become eligible after 24 hours.",
                            isLoading = report == null && busy
                        )
                    }
                    item {
                        val amount = report?.clearableCache ?: 0
                        Button(onClick = { perform { manager.clearCache(); "Safe cache cleared. Previews rebuild as you browse." } }, enabled = !busy && amount > 0,
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(if (amount > 0) "Clear safe cache · ${formatNoteSize(context, amount)}" else "No safe cache to clear")
                        }
                    }
                    item {
                        StorageActionCard(
                            "Database",
                            report?.let { formatNoteSize(context, it.database) },
                            "Deleted records can leave reserved space. Optimize compacts the database without removing notes or preferences.",
                            isLoading = report == null && busy
                        )
                    }
                    item {
                        val amount = report?.freeDatabase ?: 0
                        OutlinedButton(onClick = { reviewed = amount; confirmation = "database" }, enabled = !busy && amount > 0,
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(if (amount > 0) "Optimize · up to ${formatNoteSize(context, amount)}" else "Database is compact")
                        }
                    }
                    item {
                        TextButton(onClick = { advancedCleanup = !advancedCleanup }, enabled = !busy) {
                            Text(if (advancedCleanup) "Hide advanced cleanup" else "Advanced cleanup")
                        }
                    }
                    if (advancedCleanup) item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Remove disposable versions and cache. Current notes, attachments, folders, settings, Trash and unsaved writing are kept.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { perform {
                                disposableReview = manager.reviewDisposable()
                                confirmation = "disposable"
                                "Review cleanup before holding the button"
                            } }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Text("Clear disposable storage")
                            }
                        }
                    }
                    message?.let { text -> item { StorageMessage(text) } }
                }
            }
        }
        composable("copies") { NoteStorageRoute(navigation, this) { NoteOlderCopiesScreen { navigation.popBackStack() } } }
        composable("recovery") {
            NoteStorageRoute(navigation, this) {
                NoteRecoveryScreen(onExitSafeMode = { navigation.popBackStack() }, safeMode = false, navigationBack = true)
            }
        }
    }
    confirmation?.let { kind -> if (kind == "disposable") {
        AlertDialog(onDismissRequest = { confirmation = null; disposableReview = null },
            title = { Text("Clear disposable storage?") },
            text = { Text("Remove up to ${formatNoteSize(context, disposableReview?.bytes ?: 0)} of reviewed version history and safe cache. Removed versions cannot be restored.\n\nYour saved notes, media, folders, settings and Trash stay intact. Unsaved drafts, active edits and files needed for recovery are protected. Some protected history may remain.\n\nPress and hold continuously for five seconds. Releasing or leaving the app cancels the hold.") },
            confirmButton = { HoldToClearStorageButton(!busy && disposableReview != null) {
                val review = disposableReview ?: return@HoldToClearStorageButton
                confirmation = null; disposableReview = null
                perform { val removed = manager.clearDisposable(review)
                    "Cleared ${removed.files} disposable versions and safe cache. Saved data and unsaved writing are protected." }
            } },
            dismissButton = { TextButton(onClick = { confirmation = null; disposableReview = null }) { Text("Cancel") } })
    } else ExpressiveAlertDialog(onDismissRequest = { confirmation = null },
        title = if (kind == "history") "Remove older versions?" else "Optimize the database?",
        text = { Text(if (kind == "history") "Remove up to ${formatNoteSize(context, reviewed)} of reviewed history and completed recovery logs? Your current notes and active drafts stay intact. Removed versions cannot be restored."
            else "Rebuild SQLite to reclaim empty pages. Keep Cobalt open while this runs. Temporary free space is required; your notes and settings are kept.") },
        confirmButtonText = if (kind == "history") "Remove versions" else "Optimize", onConfirm = {
            confirmation = null
            perform {
                if (kind == "history") { val removed = manager.cleanup(reviewed, reviewedEntries = reviewedFiles); "Removed ${formatNoteSize(context, removed.bytes)} of history" }
                else { manager.optimizeDatabase(); "Database optimized" }
            }
        }, dismissButtonText = "Cancel", onDismiss = { confirmation = null }) }
}

@Composable
internal fun StorageMessage(text: String) {
    Text(text, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun StorageActionCard(
    title: String,
    amount: String?,
    description: String,
    isLoading: Boolean = false
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (isLoading || amount == null || amount == "—") {
                SkeletonBox(
                    modifier = Modifier.width(110.dp).height(32.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            } else {
                Text(amount, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

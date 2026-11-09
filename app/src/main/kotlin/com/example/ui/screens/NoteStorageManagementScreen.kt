package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.notes.*
import com.example.ui.components.*
import kotlinx.coroutines.*

/** One storage hub with focused actions; the existing settings shell owns theme and predictive back. */
@OptIn(ExperimentalMaterial3Api::class)
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
                NoteSettingsPage(
                    title = "Storage",
                    subtitle = "Notes, retained data and space on this device",
                    onBack = onBack,
                    onRefresh = ::refresh,
                    isRefreshing = busy
                ) {
                    item { StorageUsageCard(report, busy) }
                    item {
                        SettingsSubsectionHeader("YOUR CONTENT")
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ExpressiveSettingsNavigationItem(
                                title = "Manage notes",
                                onClick = { open("recovery") },
                                subtitle = "Browse and inspect your saved notes",
                                shape = connectedPillShape(0, 3)
                            )
                            ExpressiveSettingsNavigationItem(
                                title = "Draft recovery",
                                onClick = { open("drafts") },
                                subtitle = "Recover unsaved writing and interrupted edits",
                                trailingBadge = report?.let { formatNoteSize(context, it.recovery) },
                                trailingContent = if (report == null && busy) { { SkeletonBadge(46.dp) } } else null,
                                shape = connectedPillShape(1, 3)
                            )
                            ExpressiveSettingsNavigationItem(
                                title = "Trash",
                                onClick = { open("trash") },
                                subtitle = "Restore items or empty deleted notes",
                                trailingBadge = report?.let { "${it.trashCount} · ${formatNoteSize(context, it.trash)}" },
                                trailingContent = if (report == null && busy) { { SkeletonBadge(40.dp) } } else null,
                                shape = connectedPillShape(2, 3)
                            )
                        }
                    }
                    item {
                        SettingsSubsectionHeader("HISTORY & COPIES")
                        ExpressiveSettingsNavigationItem(
                            title = "Version history",
                            onClick = { open("history") },
                            subtitle = "Inspect older note versions and edit history",
                            trailingBadge = report?.let { formatNoteSize(context, it.older) },
                            trailingContent = if (report == null && busy) { { SkeletonBadge(50.dp) } } else null,
                            shape = connectedPillShape(0, 1)
                        )
                    }
                    item {
                        SettingsSubsectionHeader("CLEANUP & SYSTEM")
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ExpressiveSettingsNavigationItem(
                                title = "Cache & database",
                                onClick = { open("maintenance") },
                                subtitle = "Clear temporary preview cache and optimize space",
                                shape = connectedPillShape(0, 2)
                            )
                            ExpressiveSettingsNavigationItem(
                                title = "Unlinked storage",
                                onClick = { open("unlinked") },
                                subtitle = "Clean up orphaned media and interrupted imports",
                                trailingBadge = report?.let { formatNoteSize(context, it.unattached) },
                                trailingContent = if (report == null && busy) { { SkeletonBadge(44.dp) } } else null,
                                shape = connectedPillShape(1, 2)
                            )
                        }
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
                NoteSettingsPage(
                    title = "Version history",
                    subtitle = "Earlier note versions and edit history",
                    onBack = { navigation.popBackStack() },
                    onRefresh = ::refresh,
                    isRefreshing = busy
                ) {
                    item {
                        SettingsSubsectionHeader("HISTORY RETENTION", description = "Cobalt retains earlier revisions so you can restore changes anytime.")
                        StorageActionCard(
                            "Saved history",
                            report?.let { formatNoteSize(context, it.older) },
                            "Cleaning older revisions frees storage while keeping your current notes completely intact.",
                            isLoading = report == null && busy
                        )
                    }
                    item {
                        SettingsSubsectionHeader("RETENTION PREFERENCE", description = "Choose how many extra snapshots to keep for each note.")
                        ExpressiveSettingsSelectorItem(
                            "Versions to keep",
                            NoteStorageManager.RETENTIONS.associate { it.toString() to if (it < 0) "All versions" else if (it == 0) "No extra versions" else "$it extra versions" },
                            report?.copiesToKeep?.toString() ?: "5",
                            onSelectionChanged = { key ->
                                if (!busy) perform { manager.setRetention(key.toInt()); "History preference updated" }
                            },
                            description = "Applied when you clean up history. Versions needed by active Undo remain protected."
                        )
                    }
                    item {
                        SettingsSubsectionHeader("MANAGE VERSIONS")
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ExpressiveSettingsNavigationItem(
                                "Browse versions",
                                onClick = { open("copies") },
                                subtitle = "Restore or export an earlier version"
                            )
                            val amount = report?.removable ?: 0
                            Button(
                                onClick = {
                                    perform {
                                        reviewedFiles = manager.reviewHistory()
                                        reviewed = reviewedFiles.sumOf { it.bytes }
                                        confirmation = "history"
                                        "Review the versions selected for removal"
                                    }
                                },
                                enabled = !busy && amount > 0,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            ) {
                                Text(if (amount > 0) "Review & clean history · ${formatNoteSize(context, amount)}" else "History is clean")
                            }
                        }
                    }
                    message?.let { text -> item { StorageMessage(text) } }
                }
            }
        }
        composable("maintenance") {
            NoteStorageRoute(navigation, this) {
                NoteSettingsPage(
                    title = "Cache & database",
                    subtitle = "Temporary preview data and database optimization",
                    onBack = { navigation.popBackStack() },
                    onRefresh = ::refresh,
                    isRefreshing = busy
                ) {
                    item {
                        SettingsSubsectionHeader(
                            "TEMPORARY CACHE",
                            description = "Rebuildable preview thumbnails and temporary exports."
                        )
                        val clearable = report?.clearableCache ?: 0
                        StorageMaintenanceCard(
                            iconRes = R.drawable.ic_custom_sparkle,
                            title = "App cache",
                            amount = report?.let { formatNoteSize(context, it.cache) },
                            description = "Rebuildable preview thumbnails and temporary exports. Clearing cache is completely safe and frees up space immediately without affecting your notes.",
                            actionButtonText = if (clearable > 0) "Clear safe cache · ${formatNoteSize(context, clearable)}" else "Cache is clean",
                            onActionClick = {
                                perform {
                                    manager.clearCache()
                                    "Safe cache cleared. Previews rebuild as you browse."
                                }
                            },
                            actionEnabled = !busy && clearable > 0,
                            isLoading = report == null && busy
                        )
                    }
                    item {
                        SettingsSubsectionHeader(
                            "DATABASE OPTIMIZATION",
                            description = "Compact SQLite indexes and reclaim unused database space."
                        )
                        val freeDb = report?.freeDatabase ?: 0
                        StorageMaintenanceCard(
                            iconRes = R.drawable.ic_custom_storage,
                            title = "SQLite Database",
                            amount = report?.let { formatNoteSize(context, it.database) },
                            description = "Compacting optimizes SQLite indexing and reclaims unused database space without removing any notes or preferences.",
                            actionButtonText = if (freeDb > 0) "Optimize database · up to ${formatNoteSize(context, freeDb)}" else "Database is optimized",
                            onActionClick = {
                                reviewed = freeDb
                                confirmation = "database"
                            },
                            actionEnabled = !busy && freeDb > 0,
                            isLoading = report == null && busy
                        )
                    }
                    item {
                        SettingsSubsectionHeader(
                            "DEEP CLEANUP",
                            description = "Remove disposable older versions and safe preview cache."
                        )
                        StorageMaintenanceCard(
                            iconRes = R.drawable.ic_custom_delete,
                            title = "Disposable Snapshots & Cache",
                            amount = report?.let {
                                val totalDisposable = it.clearableCache + it.removable
                                formatNoteSize(context, totalDisposable)
                            },
                            description = "Removes older version snapshots and rebuildable preview cache. All your saved notes, media, folders, settings, and unsaved drafts are 100% kept intact.",
                            actionButtonText = "Review & clear disposable storage",
                            onActionClick = {
                                perform {
                                    disposableReview = manager.reviewDisposable()
                                    confirmation = "disposable"
                                    "Review cleanup before holding the button"
                                }
                            },
                            actionEnabled = !busy,
                            isDestructive = true,
                            isLoading = report == null && busy
                        )
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

    confirmation?.let { kind ->
        if (kind == "disposable") {
            ClearDisposableStorageBottomSheet(
                bytes = disposableReview?.bytes ?: 0L,
                busy = busy,
                onDismissRequest = {
                    confirmation = null
                    disposableReview = null
                },
                onConfirm = {
                    val review = disposableReview ?: return@ClearDisposableStorageBottomSheet
                    confirmation = null
                    disposableReview = null
                    perform {
                        val removed = manager.clearDisposable(review)
                        "Cleared ${removed.files} disposable versions and safe cache. Saved data and unsaved writing are protected."
                    }
                }
            )
        } else {
            ExpressiveAlertDialog(
                onDismissRequest = { confirmation = null },
                title = if (kind == "history") "Remove older versions?" else "Optimize the database?",
                text = {
                    Text(
                        if (kind == "history") "Remove up to ${formatNoteSize(context, reviewed)} of reviewed history and completed recovery logs? Your current notes and active drafts stay intact. Removed versions cannot be restored."
                        else "Rebuild SQLite to reclaim empty pages. Keep Cobalt open while this runs. Temporary free space is required; your notes and settings are kept."
                    )
                },
                confirmButtonText = if (kind == "history") "Remove versions" else "Optimize",
                onConfirm = {
                    confirmation = null
                    perform {
                        if (kind == "history") {
                            val removed = manager.cleanup(reviewed, reviewedEntries = reviewedFiles)
                            "Removed ${formatNoteSize(context, removed.bytes)} of history"
                        } else {
                            manager.optimizeDatabase()
                            "Database optimized"
                        }
                    }
                },
                dismissButtonText = "Cancel",
                onDismiss = { confirmation = null }
            )
        }
    }
}

@Composable
internal fun StorageMessage(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (isLoading || amount == null || amount == "—") {
                SkeletonBox(
                    modifier = Modifier.width(110.dp).height(32.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            } else {
                Text(amount, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun StorageMaintenanceCard(
    iconRes: Int,
    title: String,
    amount: String?,
    description: String,
    actionButtonText: String,
    onActionClick: () -> Unit,
    actionEnabled: Boolean,
    isDestructive: Boolean = false,
    isLoading: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = if (isDestructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = if (isDestructive) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isLoading || amount == null || amount == "—") {
                        SkeletonBox(
                            modifier = Modifier.width(72.dp).height(20.dp),
                            shape = RoundedCornerShape(6.dp)
                        )
                    } else {
                        Text(
                            text = amount,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isDestructive) {
                OutlinedButton(
                    onClick = onActionClick,
                    enabled = actionEnabled,
                    border = BorderStroke(
                        width = 1.2.dp,
                        color = if (actionEnabled) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = if (actionEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = actionButtonText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (actionEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                    )
                }
            } else {
                Button(
                    onClick = onActionClick,
                    enabled = actionEnabled,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = actionButtonText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

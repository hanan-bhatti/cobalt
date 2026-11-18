package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.data.local.CustomFolderEntity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.example.R
import com.example.bookmarks.BookmarkTransferController
import com.example.bookmarks.BookmarkTransferController.Phase
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import java.time.LocalDate
import java.text.NumberFormat

@Composable
fun BookmarkTransferSettingsScreen(viewModel: CobaltViewModel, onBack: () -> Unit) {
    val transfer = viewModel.bookmarkTransfer
    val state by transfer.state.collectAsState()
    val markdown by viewModel.markdownImport.collectAsState()
    val importMarkdown = rememberMarkdownImportAction(viewModel)
    var guide by remember { mutableStateOf(false) }
    AiSettingsPage("Import & export", "Move bookmarks between Cobalt and your browser", onBack) {
        item {
            SettingsGroupLabel("Bookmarks")
            ExpressiveSettingsGroup {
                ExpressiveSettingsNavigationItem("Import bookmarks", { transfer.chooseBookmarks() },
                    subtitle = "Choose a browser bookmarks HTML file", compact = true,
                    enabled = !state.busy && !markdown.running,
                    leadingIcon = { SettingsIconBadge(R.drawable.ic_transfer_import) })
                ExpressiveSettingsNavigationItem("Export bookmarks", { transfer.prepareExport() },
                    subtitle = "Save your links as a browser-compatible HTML file", compact = true,
                    enabled = !state.busy,
                    leadingIcon = { SettingsIconBadge(R.drawable.ic_transfer_export) })
            }
        }
        item {
            SettingsGroupLabel("Notes")
            ExpressiveSettingsNavigationItem("Import Markdown notes", importMarkdown,
                subtitle = "Choose a Markdown or plain text document", compact = true,
                enabled = !state.busy && !markdown.running,
                leadingIcon = { SettingsIconBadge(R.drawable.ic_transfer_markdown) })
        }
        item {
            SettingsGroupLabel("Before you start")
            ExpressiveSettingsNavigationItem("How bookmark transfer works", { guide = true },
                subtitle = "Supported files, folders and existing links", compact = true,
                leadingIcon = { SettingsIconBadge(R.drawable.ic_custom_info) })
        }
    }
    if (guide) SettingsBottomSheet("Bookmark transfer", "Your links stay under your control", { guide = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { TransferExplanation("Use a browser export", "Choose a bookmarks .html or .htm file exported by your browser. An ordinary webpage isn't a bookmark export. Files can contain up to 50,000 bookmarks and be up to 10 MB.") }
            item { TransferExplanation("Review before importing", "You'll see counts and review options before importing. Saved link titles and descriptions are kept. Missing descriptions can be filled from the file or website. Links in Trash aren't restored.") }
            item { TransferExplanation("Folder paths use spaced slashes", "Import turns nested folders such as Research → Android into Research / Android. Export reverses this: a slash with a space on each side ( / ) creates a nested browser folder. A slash without those spaces, as in UI/UX, stays part of the folder name. Existing folders are reused only when you choose their names.") }
            item { TransferExplanation("Unusual folder names are preserved", "A path with an empty part, such as Research /  / Android, stays one folder during export. Paths deeper than 64 folders also stay one folder. Shared parent paths are combined. Extra spaces at the ends of nested path parts are trimmed. Dots such as . or .. remain ordinary folder names. Folder colors and icons stay in Cobalt; HTML doesn't preserve them.") }
            item { TransferExplanation("Save first, load previews afterward", "Import saves links in batches without waiting for websites. Cobalt then visits saved links in the background to fetch available preview information. The review cards show the HTML file's contents before those requests begin. No AI is used.") }
            item { TransferExplanation("Descriptions are kept", "Cobalt keeps saved descriptions and uses descriptions from the HTML file when a saved link has none. If neither provides a description, Cobalt tries to fetch it from the website. Some websites don't provide descriptions or preview images.") }
            item { TransferExplanation("Background work can take time", "Your links are usable as soon as they're saved. Preview requests wait for a network connection and run in small groups. Pending requests stay queued across app restarts, and failed requests have limited retries. Android may delay background work. You don't need to keep the import sheet open.") }
            item { TransferExplanation("Original dates are kept when available", "New links use the browser's date added when the file includes a valid date. Otherwise, Cobalt uses the import date and time. Existing links keep their original date added.") }
            item { TransferExplanation("Supported addresses", "HTTP and HTTPS links are supported. Other addresses, including bookmarklets, are skipped and counted in the review.") }
            item { TransferExplanation("Export links and folders", "HTML exports URLs, titles, dates and available descriptions. Browsers may ignore descriptions. Notes, attachments, folder colors and history aren't included. Items in Trash are excluded.") }
        }
    }
}

/** One activity-level host owns pickers and sheets, including imports started from either FAB. */
@Composable
fun BookmarkTransferHost(viewModel: CobaltViewModel) {
    val transfer = viewModel.bookmarkTransfer
    val state by transfer.state.collectAsState()
    val folders by transfer.folders.collectAsState(initial = emptyList())
    val importMarkdown = rememberMarkdownImportAction(viewModel)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) transfer.pickerCancelled() else transfer.read(uri)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri == null) transfer.pickerCancelled() else transfer.write(uri)
    }
    LaunchedEffect(state.phase) {
        when (state.phase) {
            Phase.IMPORT_PICKER -> {
                transfer.pickerLaunched()
                picker.launch(arrayOf("text/html", "application/xhtml+xml", "application/octet-stream"))
            }
            Phase.EXPORT_PICKER -> {
                transfer.pickerLaunched()
                export.launch("cobalt-bookmarks-${LocalDate.now()}.html")
            }
            else -> Unit
        }
    }
    when (state.phase) {
        Phase.CHOOSER -> SettingsBottomSheet("Import", "What would you like to bring into Cobalt?", transfer::dismiss) {
            Column(Modifier.padding(bottom = 24.dp).navigationBarsPadding()) {
                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem("Browser bookmarks", transfer::chooseBookmarks,
                        subtitle = "Links and folders from a bookmarks HTML file", compact = true,
                        leadingIcon = { SettingsIconBadge(R.drawable.ic_transfer_bookmarks) })
                    ExpressiveSettingsNavigationItem("Markdown notes", { transfer.dismiss(); importMarkdown() },
                        subtitle = "A Markdown or plain text document", compact = true,
                        leadingIcon = { SettingsIconBadge(R.drawable.ic_transfer_markdown) })
                }
            }
        }
        Phase.REVIEW -> BookmarkImportReview(state, transfer, folders)
        Phase.EXPORT_REVIEW -> BookmarkExportReview(state, transfer, folders)
        Phase.READING, Phase.IMPORTING, Phase.EXPORT_LOADING, Phase.WRITING -> TransferSheet(
            title = when (state.phase) {
                Phase.READING -> "Reading bookmarks"
                Phase.IMPORTING -> "Importing bookmarks"
                Phase.EXPORT_LOADING -> "Preparing export"
                else -> "Exporting bookmarks"
            }, description = state.fileName.ifBlank { "Your saved links stay on your device." },
            onDismiss = {}, busy = true,
            action = { OutlinedButton(onClick = transfer::cancel, enabled = !state.cancelRequested, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) { Text(if (state.cancelRequested) "Stopping…" else "Cancel") } }
        ) {
            item {
                TransferCard {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (state.phase == Phase.IMPORTING) "${count(state.processed)} of ${count(state.parsed?.entries?.size ?: 0)} entries processed"
                        else "This may take a moment.", style = MaterialTheme.typography.bodyMedium)
                    if (state.phase == Phase.IMPORTING) Text("Completed batches are kept if you cancel.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Phase.RESULT -> TransferSheet(
            title = if (state.failed) "Transfer stopped" else if (state.cancelled) "Transfer cancelled" else if (state.exporting) "Export complete" else "Import complete",
            description = state.message,
            onDismiss = transfer::dismiss,
            action = {
                if (state.failed || state.cancelled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = transfer::dismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = CircleShape) { Text("Done") }
                        Button(onClick = {
                            if (state.exporting) transfer.prepareExport() else transfer.retryImport()
                        }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = CircleShape) { Text("Try again") }
                    }
                } else Button(onClick = transfer::dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) { Text("Done") }
            }
        ) {
            if (!state.exporting && state.parsed != null) item {
                TransferCard {
                    CountLine("Links added", state.counts.added)
                    CountLine("Folders created", state.counts.folders)
                    CountLine("Previews queued", state.counts.previews)
                    CountLine("Already saved", state.counts.existing)
                    CountLine("Folder assignments updated", state.counts.updated)
                    CountLine("Kept in Trash", state.counts.trash)
                    CountLine("Unsupported entries skipped", state.counts.skipped)
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun BookmarkImportReview(state: BookmarkTransferController.State, transfer: BookmarkTransferController, folders: List<CustomFolderEntity>) {
    val parsed = state.parsed ?: return
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("overview") }
    if (section == "folders") {
        BookmarkImportFolders(state, transfer, folders) { section = "overview" }
        return
    }
    if (section == "links") {
        TransferSheet("Review links", if (parsed.entries.size > 100) "Showing the first 100 entries. All supported entries will be imported." else "Titles, addresses and descriptions from your file.",
            onDismiss = { section = "overview" }, action = {
                FilledTonalButton(onClick = { section = "overview" }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) { Text("Done") }
            }) {
            items(parsed.entries.take(100)) { entry -> BookmarkPreviewRow(entry) }
            if (parsed.entries.isEmpty()) item { TransferExplanation("No supported links", "This file may contain empty folders or unsupported addresses.") }
        }
        return
    }
    val names = if (state.singleFolder) listOf(state.destination.trim()) else state.mapping.values.map { it.trim() }
    TransferReviewSheet("Review bookmarks", transfer::dismiss, footer = {
        Button(onClick = transfer::startImport,
            enabled = names.none { it.isBlank() } && (parsed.entries.isNotEmpty() || parsed.folders.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) { Text("Import bookmarks") }
        Text(state.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        TextButton(onClick = transfer::chooseBookmarks, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            Text("Choose another file", style = MaterialTheme.typography.labelMedium)
        }
    }) {
        item { TransferOverviewCounts(listOf("Links" to count(parsed.entries.size), "Folders" to count(parsed.folders.size), "Already saved" to count(state.existingLinks))) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BookmarkReviewCard("Review folders", if (state.singleFolder) "One destination · ${state.destination}" else "Keep folders · rename or choose destinations",
                    R.drawable.ic_custom_folder_open) { section = "folders" }
                BookmarkReviewCard("Review links", "Check titles, addresses and descriptions", R.drawable.ic_transfer_bookmarks) { section = "links" }
            }
        }
        if (state.duplicates > 0 || state.trashLinks > 0 || parsed.skipped > 0) item {
            TransferCard {
                if (state.duplicates > 0) CountLine("Repeated entries", state.duplicates)
                if (state.trashLinks > 0) CountLine("Kept in Trash", state.trashLinks)
                if (parsed.skipped > 0) CountLine("Unsupported entries skipped", parsed.skipped)
            }
        }
        item { Text("Links save first. Previews and missing descriptions load in the background when online. Existing titles and descriptions are kept.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun BookmarkImportFolders(state: BookmarkTransferController.State, transfer: BookmarkTransferController,
    folders: List<CustomFolderEntity>, onBack: () -> Unit) {
    val parsed = state.parsed ?: return
    var choosingExisting by remember { mutableStateOf(false) }
    var destinationPath by remember { mutableStateOf<List<String>?>(null) }
    val names = state.mapping.values.map { it.trim() }
    TransferSheet("Review folders", "Keep the file's folders or put all links in one destination.", onBack, action = {
        FilledTonalButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) { Text("Done") }
    }) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!state.singleFolder, { transfer.singleFolder(false) }, label = { Text("Keep folders") }, shape = CircleShape, modifier = Modifier.weight(1f))
                FilterChip(state.singleFolder, { transfer.singleFolder(true) }, label = { Text("One folder") }, shape = CircleShape, modifier = Modifier.weight(1f))
            }
        }
        if (state.singleFolder) item {
            TransferCard {
                AiFilledField(state.destination, transfer::destination, "Destination folder", "Folder name")
                val folder = folders.find { it.name == state.destination.trim() }
                BookmarkFolderRow(folder ?: CustomFolderEntity(state.destination.trim().ifBlank { "Choose a folder" }),
                    selected = false, subtitle = if (folder == null) "New folder" else "Existing folder",
                    onClick = { destinationPath = null; choosingExisting = true }, selectionControl = false)
            }
        } else {
            item {
                Text("Use space + slash + space ( / ) between folder levels. Export turns these names back into nested folders. Matching destination names combine their links.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (parsed.entries.any { it.path.isEmpty() }) Text("Bookmarks outside folders stay ungrouped.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (names.distinct().size != names.size) Text("Some destinations share a name and will be combined.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
            }
            items(state.mapping.entries.toList(), key = { it.key.joinToString("") { segment -> "${segment.length}:$segment" } }) { (path, name) ->
                TransferCard {
                    Text(path.joinToString(" → "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AiFilledField(name, { transfer.folderName(path, it) }, "Folder in Cobalt", "Folder name")
                    BookmarkFolderRow(folders.find { it.name == name.trim() } ?: CustomFolderEntity(name.trim().ifBlank { "Choose a folder" }),
                        selected = false, subtitle = "Choose or create a destination", selectionControl = false,
                        onClick = { destinationPath = path; choosingExisting = true })
                }
            }
        }
    }
    if (choosingExisting) BookmarkDestinationPicker(folders, destinationPath?.let { state.mapping[it] } ?: state.destination,
        onSelect = { folder ->
            val path = destinationPath
            if (path == null) transfer.destination(folder.name) else transfer.folderName(path, folder.name)
            choosingExisting = false
        }, onCreate = transfer::createDestinationFolder, onDismiss = { choosingExisting = false })
}

@Composable
private fun BookmarkExportReview(state: BookmarkTransferController.State, transfer: BookmarkTransferController, folders: List<CustomFolderEntity>) {
    var choosing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (choosing) {
        BookmarkExportSelection(state, transfer, folders) { choosing = false }
        return
    }
    TransferReviewSheet("Export bookmarks", transfer::dismiss, footer = {
        Button(onClick = transfer::chooseExportFile, enabled = state.exportCount > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) { Text("Save HTML file") }
        Text("cobalt-bookmarks-${LocalDate.now()}.html", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Choose where to save in the next step", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }) {
        item { TransferOverviewCounts(listOf("Links selected" to count(state.exportCount), "Folders" to if (state.allFolders) "All" else count(state.selectedFolders.size))) }
        item { BookmarkReviewCard("Choose links & folders", if (state.allFolders) "All links · ${if (state.includeArchived) "with" else "without"} archived links" else "${count(state.selectedFolders.size)} folders selected",
            R.drawable.ic_custom_folder_open) { choosing = true } }
        item {
            TransferCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsIconBadge(R.drawable.ic_transfer_export)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Bookmarks HTML", style = MaterialTheme.typography.titleSmall)
                        Text("URLs, titles, dates and descriptions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Names with \" / \" become nested folders. UI/UX stays a single name. Empty path parts stay unchanged. Notes, attachments and items in Trash aren't included.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.unsupportedExport > 0) CountLine("Unsupported addresses excluded", state.unsupportedExport)
            }
        }
        if (state.exportCount == 0) item { TransferExplanation("No links selected", "Choose folders or include archived links to continue.") }
    }
}

@Composable
private fun BookmarkExportSelection(state: BookmarkTransferController.State, transfer: BookmarkTransferController,
    folders: List<CustomFolderEntity>, onBack: () -> Unit) {
    var search by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val details = remember(folders) { folders.associateBy { it.name } }
    val visible = remember(state.exportFolders, search) { state.exportFolders.filter { it.contains(search.trim(), ignoreCase = true) } }
    TransferSheet("Choose links & folders", "${count(state.exportCount)} links selected for export", onBack, action = {
        FilledTonalButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) { Text("Done") }
    }) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.allFolders, { transfer.updateExportOptions(all = true) }, label = { Text("All links") }, shape = CircleShape, modifier = Modifier.weight(1f))
                FilterChip(!state.allFolders, { transfer.updateExportOptions(all = false) }, label = { Text("Choose folders") }, shape = CircleShape, modifier = Modifier.weight(1f))
            }
        }
        item { TransferToggle("Archived links", "Include links you've archived", state.includeArchived) { transfer.updateExportOptions(archived = it) } }
        if (!state.allFolders) {
            item { TransferToggle("Ungrouped links", "Links outside custom folders", state.includeUngrouped) { transfer.updateExportOptions(ungrouped = it) } }
            item { FolderSearchField(search, { search = it }) }
            items(visible, key = { it }) { name ->
                val path = remember(name) { com.example.bookmarks.BookmarkHtml.exportPath(name) }
                BookmarkFolderRow(details[name] ?: CustomFolderEntity(name), name in state.selectedFolders, multiple = true,
                    subtitle = if (path.size > 1) "In browser: ${path.joinToString(" → ")}" else if (" / " in name) "Exports as one folder · empty or overly deep path" else null,
                    onClick = { transfer.updateExportOptions(folders = if (name in state.selectedFolders) state.selectedFolders - name else state.selectedFolders + name) })
            }
            if (visible.isEmpty()) item { Text(if (state.exportFolders.isEmpty()) "No folders with exportable links." else "No matching folders.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferReviewSheet(title: String, onDismiss: () -> Unit, footer: @Composable ColumnScope.() -> Unit,
    busy: Boolean = false, content: LazyListScope.() -> Unit) {
    val currentBusy by rememberUpdatedState(busy)
    ModalBottomSheet(onDismissRequest = { if (!currentBusy) onDismiss() },
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { !currentBusy || it != SheetValue.Hidden }),
        containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp * 0.85f)
            .navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (!busy) IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ic_custom_close), "Close") }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp), content = footer)
        }
    }
}

@Composable
private fun TransferOverviewCounts(values: List<Pair<String, String>>) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { (label, value) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BookmarkReviewCard(title: String, subtitle: String, icon: Int, onClick: () -> Unit) {
    ExpressiveSettingsNavigationItem(title, onClick, subtitle = subtitle, compact = true, shape = RoundedCornerShape(24.dp),
        leadingIcon = { SettingsIconBadge(icon) })
}

/** All transfer steps share a fixed header, scrollable content and bottom action area. */
@Composable
private fun TransferSheet(title: String, description: String, onDismiss: () -> Unit, busy: Boolean = false,
    action: @Composable () -> Unit, content: LazyListScope.() -> Unit) {
    TransferReviewSheet(title, onDismiss, busy = busy, footer = { action() }) {
        item { Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}

@Composable
private fun TransferToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(onClick = { onChange(!checked) }, modifier = Modifier.semantics {
        role = Role.Switch
        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
    }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun TransferCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun TransferExplanation(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CountLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(count(value), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}
private fun count(value: Int) = NumberFormat.getIntegerInstance().format(value)

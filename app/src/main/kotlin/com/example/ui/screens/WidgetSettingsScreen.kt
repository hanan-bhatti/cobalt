package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.displayTitle
import com.example.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class WidgetDesign(val title: String, val subtitle: String, val icon: Int, val available: Boolean = false)

@Composable
fun WidgetGalleryList(viewModel: CobaltViewModel, onOpenFolio: () -> Unit, onOpenScreening: () -> Unit) {
    val inventory by viewModel.widgets.state.collectAsState()
    val folders by viewModel.customFolderEntities.collectAsState()
    val items by viewModel.folioItems.collectAsState()
    val typography by viewModel.settingsRepository.typographyPrefs.collectAsState()
    val theme by viewModel.settingsRepository.themeMode.collectAsState()
    val dynamic by viewModel.settingsRepository.dynamicColor.collectAsState()
    val screening by viewModel.screening.state.collectAsState()
    val folioConfig = inventory.folioIds.firstOrNull()?.let { inventory.folios[it] } ?: inventory.folios[-1] ?: FolioConfig()
    val folder = folders.find { it.name == folioConfig.folderName }
    val folioItems = remember(items, folioConfig) { FolioItems.select(items, folioConfig) }
    val screeningId = inventory.screeningIds.firstOrNull() ?: -1
    val savedScreening = inventory.screenings[screeningId] ?: ScreeningWidgetEntry()
    val screeningEntry = remember(savedScreening, screening.insights, screening.ready) {
        if (screening.ready) ScreeningWidgetSelection.update(savedScreening, screening.insights.ranked, screeningId,
            com.example.media.recommendation.ScreeningSchedule.day(System.currentTimeMillis())) else savedScreening
    }
    val available = listOf(
        WidgetDesign("Pocket Folio", "A folder and its saved items · 4 × 3", R.drawable.ic_custom_folder_open, true),
        WidgetDesign("Screening Card", "Picks from your saved movies, shows and manga · 4 × 3", R.drawable.ic_custom_movie, true)
    )
    val upcoming = listOf(
        WidgetDesign("Capture Ticket", "A space for your next idea · 4 × 2", R.drawable.ic_widget_capture),
        WidgetDesign("Archive Window", "Rediscover saved items · 4 × 3", R.drawable.ic_widget_archive),
        WidgetDesign("Focus Page", "Keep one note in view · 2 × 3", R.drawable.ic_widget_focus_note)
    )
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column {
            SettingsGroupLabel("Available widgets")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                available.forEach { design ->
                    val action = if (design.title == "Pocket Folio") onOpenFolio else onOpenScreening
                    Surface(onClick = action, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            if (design.title == "Pocket Folio") {
                                FolioLivePreview(folioConfig, folder, folioItems, typography, theme, dynamic,
                                    onChooseFolder = {}, onItem = {}, interactive = false)
                            } else ScreeningGalleryPreview(screeningEntry)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(design.title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(design.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        Column {
            SettingsGroupLabel("Coming soon")
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    upcoming.forEach { design ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsIconBadge(design.icon)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(design.title, style = MaterialTheme.typography.titleMedium)
                                Text(design.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetGalleryScreen(viewModel: CobaltViewModel, onBack: () -> Unit, onOpenFolio: () -> Unit, onOpenScreening: () -> Unit) {
    AiSettingsPage("Widgets", "Choose a widget for your home screen", onBack) {
        item { WidgetGalleryList(viewModel, onOpenFolio, onOpenScreening) }
    }
}

private val configSaver = listSaver<FolioConfig, String>(
    save = { listOf(it.folderName.orEmpty(), it.colorHex.orEmpty(), it.order.name, it.removedFolder.orEmpty()) },
    restore = { FolioConfig(it[0].ifEmpty { null }, it[1].ifEmpty { null }, FolioOrder.valueOf(it[2]), it[3].ifEmpty { null }) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(viewModel: CobaltViewModel, onNavigateBack: () -> Unit, configurationId: Int? = null, onConfigured: (() -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember(context) { FolioConfigStore(context) }
    val scope = rememberCoroutineScope()
    val settingsWrites = remember { Mutex() }
    val inventory by viewModel.widgets.state.collectAsState()
    val activeIds = inventory.folioIds
    var creating by rememberSaveable { mutableStateOf(false) }
    var pendingPin by rememberSaveable { mutableStateOf(false) }
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var showLauncherHelp by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var removeId by remember { mutableStateOf<Int?>(null) }
    var pinBefore by remember { mutableStateOf<List<Int>?>(null) }
    var preferredId by remember { mutableStateOf<Int?>(null) }
    var configOverrides by remember { mutableStateOf<Map<Int, FolioConfig>>(emptyMap()) }
    val folders by viewModel.customFolderEntities.collectAsState()
    val allItems by viewModel.folioItems.collectAsState()
    val typography by viewModel.settingsRepository.typographyPrefs.collectAsState()
    val theme by viewModel.settingsRepository.themeMode.collectAsState()
    val dynamic by viewModel.settingsRepository.dynamicColor.collectAsState()
    val pages = when {
        configurationId != null -> listOf(configurationId)
        creating -> listOf(-1)
        else -> activeIds
    }
    val latestPages by rememberUpdatedState(pages)
    val pager = rememberPagerState { latestPages.size.coerceAtLeast(1) }
    val selectedId = pages.getOrElse(pager.currentPage) { pages.firstOrNull() ?: -1 }
    val editing = configurationId != null || creating || activeIds.isNotEmpty()
    var activityConfig by rememberSaveable(configurationId, stateSaver = configSaver) { mutableStateOf(FolioConfig()) }
    var activityEdited by rememberSaveable(configurationId) { mutableStateOf(false) }
    val config = if (configurationId != null && activityEdited) activityConfig
        else configOverrides[selectedId] ?: inventory.folios[selectedId] ?: FolioConfig()
    val folder = folders.find { it.name == config.folderName }
    val selectedItems = remember(allItems, config, folder) { if (folder == null) emptyList() else FolioItems.select(allItems, config) }

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            val target = preferredId?.let(pages::indexOf)?.takeIf { it >= 0 } ?: pager.currentPage.coerceAtMost(pages.lastIndex)
            pager.scrollToPage(target); preferredId = null
        }
    }
    LaunchedEffect(activeIds) {
        val before = pinBefore
        val added = if (before != null) activeIds.firstOrNull { it !in before } else null
        if (added != null) { preferredId = added; creating = false; pendingPin = false; pinBefore = null }
    }
    LaunchedEffect(inventory) {
        configOverrides = configOverrides.filter { (id, value) -> inventory.folios[id] != value && (id == -1 || id in activeIds) }
    }
    LaunchedEffect(pendingPin) { if (pendingPin) { delay(2_000); pendingPin = false } }
    fun change(updated: FolioConfig) {
        if (configurationId != null) { activityConfig = updated; activityEdited = true }
        else {
            val id = selectedId
            configOverrides = configOverrides + (id to updated)
            scope.launch {
                runCatching {
                    settingsWrites.withLock { withContext(Dispatchers.IO) {
                        if (id == -1) store.save(id, updated)
                        else {
                            check(id in FolioWidgetProvider.ids(context) && store.saveConfigured(id, updated)) { "Widget was removed" }
                            WidgetUpdater.update(context)
                        }
                    } }
                }.onFailure {
                    configOverrides = configOverrides - id
                    Toast.makeText(context, "Couldn't save your Folio. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    NoteSettingsPage(title = "Pocket Folio", subtitle = "Your favorite folder, within reach.", onBack = onNavigateBack, itemSpacing = 16.dp,
        actions = { if (configurationId == null && !creating && selectedId > 0) WidgetSetupMenu { removeId = selectedId } }) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (editing) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalPager(state = pager, key = { latestPages.getOrNull(it) ?: "removed-$it" }, modifier = Modifier.fillMaxWidth()) { page ->
                                val id = latestPages.getOrNull(page) ?: return@HorizontalPager
                                val pageConfig = if (configurationId != null && activityEdited) activityConfig
                                    else configOverrides[id] ?: inventory.folios[id] ?: FolioConfig()
                                val pageFolder = folders.find { it.name == pageConfig.folderName }
                                val pageItems = remember(allItems, pageConfig, pageFolder) { if (pageFolder == null) emptyList() else FolioItems.select(allItems, pageConfig) }
                                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                    FolioLivePreview(pageConfig, pageFolder, pageItems, typography, theme, dynamic,
                                        onChooseFolder = { showFolders = true }, onItem = { viewModel.openItemById(it.id) })
                                }
                            }
                            if (configurationId == null) {
                                if (pages.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    pages.forEachIndexed { index, _ ->
                                        val width by animateDpAsState(if (index == pager.currentPage) 20.dp else 6.dp, label = "Folio page")
                                        val color by animateColorAsState(if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "Folio indicator")
                                        Box(Modifier.size(width, 6.dp).background(color, CircleShape).clickable { scope.launch { pager.animateScrollToPage(index) } })
                                    }
                                }
                            }
                    }
                    AiInfoLabel("Customize", "These choices apply to this Pocket Folio only. Choose its folder, color and item order. Tap a preview item to open it in Cobalt.")
                    if (!creating && configurationId == null && activeIds.size > 1) Text(
                        "Folio ${pager.currentPage + 1} of ${activeIds.size} · swipe to switch", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExpressiveSettingsGroup {
                        ExpressiveSettingsNavigationItem("Folder", { showFolders = true }, subtitle = folder?.name ?: config.removedFolder?.let { "$it was removed. Choose another folder." } ?: "Choose the collection to show",
                            leadingIcon = {
                                val accent = settingsIconColors(R.drawable.ic_custom_folder_open)
                                Box(Modifier.size(40.dp).background(accent.first, CircleShape), contentAlignment = Alignment.Center) {
                                    FolderIcon(folder?.iconName, tint = accent.second)
                                }
                            }, trailingBadge = if (folder == null) "Choose" else "Change", shape = connectedPillShape(0, 3))
                        if (folder?.colorHex.isNullOrBlank()) {
                            ExpressiveSettingsSelectorItem("Folio color", linkedMapOf("default" to "Folio gold").apply { putAll(folderPresetColors.toMap()) }, config.colorHex ?: "default",
                                { change(config.copy(colorHex = it.takeUnless { key -> key == "default" })) }, description = "This folder has no color. Choose a color for this Folio only.", shape = connectedPillShape(1, 3),
                                optionColor = { key -> parseHexColor(key.takeUnless { it == "default" }, Color(0xFFF3D986), FolioRenderer.isDark(context)) })
                        } else Surface(shape = connectedPillShape(1, 3), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(24.dp).background(parseHexColor(folder.colorHex, isDark = FolioRenderer.isDark(context)), CircleShape))
                                Column {
                                    Text("Folder color", style = MaterialTheme.typography.titleMedium)
                                    Text("Folio follows this folder's color", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        ExpressiveSettingsSelectorItem("Item order", FolioOrder.entries.associate { it.name to it.label }, config.order.name,
                            { change(config.copy(order = FolioOrder.valueOf(it))) }, description = "Choose which two items appear first", shape = connectedPillShape(2, 3))
                    }
                    if (folder != null && selectedItems.isEmpty()) Text("This folder is empty. Tap the widget to add your first note.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        if (configurationId == null && !creating) { creating = true; preferredId = -1 }
                        else if (folder != null) {
                            saving = true
                            scope.launch {
                                runCatching {
                                    if (configurationId != null) {
                                        withContext(Dispatchers.IO) {
                                            check(configurationId in FolioWidgetProvider.ids(context)) { "Widget was removed" }
                                            store.save(configurationId, config)
                                        }
                                        WidgetUpdater.update(context); onConfigured?.invoke()
                                    } else {
                                        pendingPin = true; pinBefore = activeIds
                                        if (!withContext(Dispatchers.IO) { FolioWidgetProvider.requestPin(context, config, folder, selectedItems) }) {
                                            pendingPin = false; pinBefore = null; showLauncherHelp = true
                                        }
                                    }
                                }.onFailure { pendingPin = false; pinBefore = null; Toast.makeText(context, "Couldn't place your Folio. Please try again.", Toast.LENGTH_LONG).show() }
                                saving = false
                            }
                        }
                    }, enabled = !saving && !pendingPin && (folder != null || configurationId == null && !creating), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (configurationId != null) "Save widget" else if (!creating) "Add another Folio" else "Add to home screen")
                    }
                    if (configurationId != null || creating) Text(
                        if (configurationId != null) "Save to apply your choices. Back keeps the current widget settings." else "Confirm placement in your launcher’s dialog.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (configurationId == null && creating) TextButton(onClick = { creating = false }) { Text("Cancel new widget") }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        FolioLivePreview(config, folder, selectedItems, typography, theme, dynamic,
                            onChooseFolder = { creating = true; preferredId = -1; showFolders = true }, onItem = { viewModel.openItemById(it.id) })
                        Button(onClick = { creating = true; preferredId = -1; showFolders = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Choose a folder")
                        }
                    }
                }
            }
        }
    }
    if (showFolders) FolioFolderPicker(folders, allItems, config.folderName, onDismiss = { showFolders = false },
        onSelect = { change(config.copy(folderName = it.name, removedFolder = null)); showFolders = false },
        onCreate = { name, icon -> viewModel.createFolder(name, iconName = icon) })
    removeId?.let { id ->
        AlertDialog(onDismissRequest = { removeId = null }, title = { Text("Remove this Folio from Cobalt?") },
            text = { Text("Remove the widget from your home screen first. This clears its saved widget settings. Your folder and notes stay.") },
            confirmButton = { TextButton(onClick = {
                removeId = null
                scope.launch { withContext(Dispatchers.IO) { store.delete(id); FolioSnapshotStore(context).delete(id) }; viewModel.widgets.refresh() }
            }) { Text("Remove saved Folio") } },
            dismissButton = { TextButton(onClick = { removeId = null }) { Text("Keep Folio") } })
    }
    if (showLauncherHelp) AlertDialog(onDismissRequest = { showLauncherHelp = false }, title = { Text("Add from your home screen") },
        text = { Text("Long-press an empty space on your home screen, open Widgets, and choose Cobalt → Pocket Folio. You'll choose its folder before placing it.") },
        confirmButton = { TextButton(onClick = { showLauncherHelp = false }) { Text("Got it") } })
}

@Composable
private fun FolioLivePreview(config: FolioConfig, folder: CustomFolderEntity?, items: List<SavedItem>, typography: Any, theme: String, dynamic: Boolean, onChooseFolder: () -> Unit, onItem: (SavedItem) -> Unit, interactive: Boolean = true) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val colors = MaterialTheme.colorScheme
    val bitmap = remember(config, folder, items, typography, theme, dynamic, colors, configuration.fontScale, configuration.uiMode, density) {
        FolioRenderer.render(context, config, folder, items, (316 * density).toInt(), (248 * density).toInt(), colors = colors, drawItems = false).asImageBitmap()
    }
    BoxWithConstraints(Modifier.widthIn(max = 316.dp).fillMaxWidth().aspectRatio(316f / 248f)) {
        Image(bitmap, null, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().then(if (interactive) Modifier.clickable(onClick = onChooseFolder) else Modifier).semantics { contentDescription = folder?.let { "Pocket Folio preview, ${it.name}, ${items.size} items." } ?: "Pocket Folio preview" })
        val previewItems = items.take(5)
        var previewRows by remember(previewItems, colors, configuration.fontScale, typography) {
            mutableStateOf<List<Pair<SavedItem, androidx.compose.ui.graphics.ImageBitmap>>>(emptyList())
        }
        LaunchedEffect(previewItems, colors, configuration.fontScale, typography) {
            previewRows = withContext(Dispatchers.IO) {
                FolioRows.prepare(context, previewItems)
                previewItems.map { it to FolioRows.render(context, it, (280 * density).toInt().coerceIn(100, 560), colors).asImageBitmap() }
            }
        }
        if (folder != null && previewItems.isNotEmpty()) {
            LazyColumn(Modifier.offset(x = maxWidth * (18f / 316), y = maxHeight * (88f / 248))
                .size(maxWidth * (280f / 316), maxHeight * (108f / 248)), userScrollEnabled = interactive) {
                items(previewRows, key = { it.first.id }) { (item, row) ->
                    Image(row, contentDescription = listOf(item.displayTitle(context), FolioRows.excerpt(item)).filter { it.isNotBlank() }.joinToString(". "),
                        modifier = Modifier.fillMaxWidth().aspectRatio(280f / 56f).then(if (interactive) Modifier.clickable { onItem(item) } else Modifier))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolioFolderPicker(folders: List<CustomFolderEntity>, items: List<SavedItem>, selected: String?, onDismiss: () -> Unit, onSelect: (CustomFolderEntity) -> Unit, onCreate: (String, String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var awaiting by rememberSaveable { mutableStateOf<String?>(null) }
    var createError by remember { mutableStateOf(false) }
    LaunchedEffect(folders, awaiting) { folders.find { it.name == awaiting }?.let { awaiting = null; onSelect(it) } }
    LaunchedEffect(awaiting) { if (awaiting != null) { delay(8_000); awaiting = null; createError = true } }
    val filtered = remember(folders, search) { folders.filter { it.name.contains(search, true) }.sortedBy { it.name.lowercase() } }
    val counts = remember(items) { items.filter { !it.isDeleted && !it.isArchived }.flatMap { it.folders }.groupingBy { it }.eachCount() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose folder", style = MaterialTheme.typography.titleLarge)
            FolderSearchField(search, { search = it })
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filtered, key = { it.name }) { folder ->
                    Surface(onClick = { onSelect(folder) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FolderIcon(folder.iconName, tint = parseHexColor(folder.colorHex, MaterialTheme.colorScheme.primary, FolioRenderer.isDark(androidx.compose.ui.platform.LocalContext.current)))
                            Text(folder.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((counts[folder.name] ?: 0).toString(), style = MaterialTheme.typography.labelMedium)
                            if (folder.name == selected) Icon(painterResource(R.drawable.ic_custom_check), "Selected", Modifier.size(20.dp))
                        }
                    }
                }
                if (filtered.isEmpty()) item { Text(if (folders.isEmpty()) "Create a folder for your first Folio." else "No matching folders.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            if (awaiting != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (creating) InlineNewFolderCreator(onAddFolder = { name, icon -> awaiting = name; createError = false; onCreate(name, icon) }, onCancel = { creating = false }, existingFolders = folders.map { it.name })
            else TextButton(onClick = { creating = true }) { Icon(painterResource(R.drawable.ic_custom_add_folder), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("New folder") }
            if (createError) Text("Couldn't create the folder. Please try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScreeningGalleryPreview(entry: ScreeningWidgetEntry) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(entry, colors, density) {
        bitmap = withContext(Dispatchers.IO) {
            val width = (336 * density).toInt().coerceIn(1, 900)
            ScreeningRenderer.render(context, entry, width, (width * 264f / 336).toInt().coerceAtLeast(1),
                artwork = ScreeningArtworkCache.cached(context, entry.pick?.artwork), colors = colors).asImageBitmap()
        }
    }
    Box(Modifier.widthIn(max = 336.dp).fillMaxWidth().aspectRatio(336f / 264f), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, "Screening Card preview", Modifier.fillMaxSize()) }
            ?: SkeletonBox(Modifier.fillMaxSize(), RoundedCornerShape(28.dp))
    }
}

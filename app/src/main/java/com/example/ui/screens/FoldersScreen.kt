package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.example.ui.components.bounceClick
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.ui.components.bounceClick
import com.example.ui.viewmodel.SecondBrainViewModel

// Preset Palette of aesthetic Pastel/Vibrant Colors
val folderPresetColors = listOf(
    "#FF6B6B" to "Coral Rose",
    "#FF8E53" to "Peach Sun",
    "#FFA800" to "Soft Gold",
    "#4CAF50" to "Emerald Sage",
    "#00BFA5" to "Minty Teal",
    "#29B6F6" to "Ice Blue",
    "#5C6BC0" to "Royal Indigo",
    "#BA68C8" to "Lavender Dream",
    "#D81B60" to "Crimson Berry",
    "#78909C" to "Slate Silver"
)

// Preset Palette of standard premium Icons
val folderPresetIcons = listOf(
    "folder",
    "star",
    "book",
    "code",
    "heart",
    "work",
    "school",
    "home",
    "shopping",
    "music",
    "tools"
)

fun parseHexColor(hex: String?, defaultColor: Color = Color(0xFF6750A4)): Color {
    if (hex.isNullOrBlank()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        defaultColor
    }
}

@Composable
fun FolderIcon(
    iconName: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val resId = when (iconName) {
        "folder" -> R.drawable.ic_custom_folder
        "star" -> R.drawable.ic_custom_star
        "book" -> R.drawable.ic_custom_text
        "code" -> R.drawable.ic_custom_code
        "heart" -> R.drawable.ic_custom_heart
        "work" -> R.drawable.ic_custom_work
        "school" -> R.drawable.ic_custom_school
        "home" -> R.drawable.ic_custom_home
        "shopping" -> R.drawable.ic_custom_shopping
        "music" -> R.drawable.ic_custom_music
        "tools" -> R.drawable.ic_custom_tools
        else -> R.drawable.ic_custom_folder
    }
    Icon(
        painter = painterResource(id = resId),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    viewModel: SecondBrainViewModel,
    modifier: Modifier = Modifier
) {
    val allItems by viewModel.allItems.collectAsState()
    val customFolderEntities by viewModel.customFolderEntities.collectAsState()

    var activeBrowseFolder by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = activeBrowseFolder != null) {
        activeBrowseFolder = null
    }
    var folderToCustomize by remember { mutableStateOf<CustomFolderEntity?>(null) }

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val context = LocalContext.current

    AnimatedContent(
        targetState = activeBrowseFolder,
        transitionSpec = {
            if (targetState != null) {
                // Slide in to browse items
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            } else {
                // Slide out back to list
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            }
        },
        label = "folders_navigation"
    ) { browseFolder ->
        if (browseFolder != null) {
            // FOLDER BROWSER VIEW
            FolderContentsBrowser(
                folderName = browseFolder,
                viewModel = viewModel,
                onBack = { activeBrowseFolder = null }
            )
        } else {
            // MAIN FOLDERS DIRECTORY LIST VIEW
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "Folders",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "CATEGORIES & DIRECTORIES",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        letterSpacing = 1.sp
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    val pinnedFolders = remember(customFolderEntities) {
                        customFolderEntities.filter { it.isPinned }
                    }
                    val otherFolders = remember(customFolderEntities) {
                        customFolderEntities.filter { !it.isPinned }
                    }

                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // System Built-in Categories Section
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            Text(
                                text = "System Categories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(SavedItemType.entries.size) { index ->
                            val type = SavedItemType.entries[index]
                            val count = remember(allItems) {
                                allItems.count { it.type == type && !it.folders.contains("Archive") }
                            }
                            val iconResId = when (type) {
                                SavedItemType.LINK -> R.drawable.ic_custom_link
                                SavedItemType.IMAGE -> R.drawable.ic_custom_image
                                SavedItemType.VIDEO -> R.drawable.ic_custom_video
                                SavedItemType.CODE -> R.drawable.ic_custom_code
                                SavedItemType.TEXT -> R.drawable.ic_custom_text
                                SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                            }
                            SystemCategoryCard(
                                name = type.displayName,
                                count = count,
                                iconResId = iconResId,
                                onClick = { activeBrowseFolder = type.displayName },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Pinned Folders Section
                        if (pinnedFolders.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                Text(
                                    text = "Pinned Folders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                )
                            }

                            items(pinnedFolders.size, key = { "pinned_${pinnedFolders[it].name}" }) { index ->
                                val folder = pinnedFolders[index]
                                val count = remember(allItems) {
                                    allItems.count { it.folders.contains(folder.name) }
                                }
                                PinnedFolderCard(
                                    folder = folder,
                                    count = count,
                                    onClick = { activeBrowseFolder = folder.name },
                                    onCustomize = { folderToCustomize = folder },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Custom Folders Directory Section
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            Text(
                                text = "Custom Folders",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }

                        if (otherFolders.isEmpty() && pinnedFolders.isEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_folder_open),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No custom folders yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                otherFolders.size,
                                key = { "other_${otherFolders[it].name}" },
                                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { index ->
                                val folder = otherFolders[index]
                                val count = remember(allItems) {
                                    allItems.count { it.folders.contains(folder.name) }
                                }
                                FolderDirectoryItem(
                                    folder = folder,
                                    count = count,
                                    onClick = { activeBrowseFolder = folder.name },
                                    onCustomize = { folderToCustomize = folder },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { showAddFolderDialog = true },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("fab_create_folder").size(64.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_plus),
                            contentDescription = "New Folder",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    // Add Custom Folder Dialog
    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddFolderDialog = false
                newFolderName = ""
            },
            title = { Text("New Custom Folder", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("e.g. Work, Inspiration....") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_folder_name_field")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newFolderName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createFolder(trimmed)
                            showAddFolderDialog = false
                            newFolderName = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_folder_btn")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddFolderDialog = false
                    newFolderName = ""
                }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Customize Folder Dialog/Sheet (Aesthetics Customizer)
    if (folderToCustomize != null) {
        FolderCustomizerDialog(
            folder = folderToCustomize!!,
            viewModel = viewModel,
            onDismiss = { folderToCustomize = null }
        )
    }
}

@Composable
fun SystemCategoryCard(
    name: String,
    count: Int,
    iconResId: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
            .height(100.dp)
            .bounceClick(interactionSource)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,

                )
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count items",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun PinnedFolderCard(
    folder: CustomFolderEntity,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onCustomize: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val themeColor = parseHexColor(folder.colorHex, MaterialTheme.colorScheme.primary)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = themeColor.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, themeColor.copy(alpha = 0.4f)),
        modifier = modifier
            .height(115.dp)
            .bounceClick(interactionSource)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolderIcon(
                    iconName = folder.iconName,
                    tint = themeColor,
                    modifier = Modifier.size(26.dp)
                )
                IconButton(
                    onClick = onCustomize,

                    ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_settings),
                        contentDescription = "Customize",
                        tint = themeColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$count items",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_pin),
                        contentDescription = "Pinned",
                        tint = themeColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FolderDirectoryItem(
    folder: CustomFolderEntity,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onCustomize: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val themeColor = parseHexColor(folder.colorHex, MaterialTheme.colorScheme.primary)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .bounceClick(interactionSource)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Colored Folder Icon Box
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(themeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    FolderIcon(
                        iconName = folder.iconName,
                        tint = themeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$count items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(onClick = onCustomize) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_settings),
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContentsBrowser(
    folderName: String,
    viewModel: SecondBrainViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.allItems.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    // Filter items matching current browsing folder (either custom tag or system type)
    val folderItems = remember(items, folderName) {
        val systemCategory = SavedItemType.entries.find { it.displayName == folderName }
        if (systemCategory != null) {
            items.filter { it.type == systemCategory && !it.folders.contains("Archive") }
        } else {
            items.filter { it.folders.contains(folderName) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = folderName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${folderItems.size} items preserved",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_back),
                            contentDescription = "Back",

                            )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncData() },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (folderItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_folder_open),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "This Folder is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Save links or files, then assign them to this folder to organize.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(folderItems, key = { it.id }) { item ->
                        // Direct standard Item card layout
                        FolderBrowseItemRow(
                            item = item,
                            onClick = { viewModel.showDetailItem(item) },
                            onLongClick = { /* TODO: Implement long click action */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FolderBrowseItemRow(
    item: SavedItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var showContextMenu by remember { mutableStateOf(false) }
    val iconResId = when (item.type) {
        SavedItemType.LINK -> R.drawable.ic_custom_link
        SavedItemType.IMAGE -> R.drawable.ic_custom_image
        SavedItemType.VIDEO -> R.drawable.ic_custom_video
        SavedItemType.CODE -> R.drawable.ic_custom_code
        SavedItemType.TEXT -> R.drawable.ic_custom_text
        SavedItemType.AUDIO -> R.drawable.ic_custom_voice
    }

    Box {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongClick ?: { showContextMenu = true }
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title.ifBlank { "Untitled preserving" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.content.take(60).ifBlank { "No content preview available" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = { showContextMenu = false; onClick() }
            )
        }
    }
}

@Composable
fun FolderCustomizerDialog(
    folder: CustomFolderEntity,
    viewModel: SecondBrainViewModel,
    onDismiss: () -> Unit
) {
    var folderNameInput by remember { mutableStateOf(folder.name) }
    var selectedColorHex by remember { mutableStateOf(folder.colorHex ?: folderPresetColors.first().first) }
    var selectedIconName by remember { mutableStateOf(folder.iconName ?: "folder") }
    var isPinned by remember { mutableStateOf(folder.isPinned) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Folder") },
            text = { Text("Are you sure you want to delete the folder \"${folder.name}\"? This won't delete saved items in it, only the folder itself.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.name)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Customize Folder", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Rename input
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("customize_folder_name_field")
                    )

                    // Pin toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isPinned = !isPinned }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_pin),
                                contentDescription = null,
                                tint = if (isPinned) parseHexColor(selectedColorHex) else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pin to top", fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = isPinned,
                            onCheckedChange = { isPinned = it }
                        )
                    }

                    // Choose Icon
                    Column {
                        Text(
                            text = "Choose Icon",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Horizontal Icon Grid layout
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(folderPresetIcons.size) { index ->
                                val iconName = folderPresetIcons[index]
                                val isSelected = selectedIconName == iconName
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) parseHexColor(selectedColorHex).copy(alpha = 0.2f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            1.5.dp,
                                            if (isSelected) parseHexColor(selectedColorHex)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                        .clickable { selectedIconName = iconName },
                                    contentAlignment = Alignment.Center
                                ) {
                                    FolderIcon(
                                        iconName = iconName,
                                        tint = if (isSelected) parseHexColor(selectedColorHex) else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Choose Color
                    Column {
                        Text(
                            text = "Choose Theme Color",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(folderPresetColors) { (hex, name) ->
                                val color = parseHexColor(hex)
                                val isSelected = selectedColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { selectedColorHex = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_check),
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Delete button
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("delete_folder_btn")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_delete),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Folder")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalName = folderNameInput.trim()
                        if (finalName.isNotEmpty()) {
                            // 1. Rename folder first if changed
                            if (finalName != folder.name) {
                                viewModel.renameFolder(folder.name, finalName)
                            }
                            // 2. Save settings
                            viewModel.updateFolder(
                                CustomFolderEntity(
                                    name = finalName,
                                    colorHex = selectedColorHex,
                                    iconName = selectedIconName,
                                    isPinned = isPinned,
                                    isSynced = folder.isSynced
                                )
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.testTag("save_custom_folder_btn")
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

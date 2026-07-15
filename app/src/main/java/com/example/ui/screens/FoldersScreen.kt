package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
    "folder" to Icons.Default.Folder,
    "star" to Icons.Default.Star,
    "book" to Icons.Default.Book,
    "code" to Icons.Default.Code,
    "heart" to Icons.Default.Favorite,
    "work" to Icons.Default.Work,
    "school" to Icons.Default.School,
    "home" to Icons.Default.Home,
    "shopping" to Icons.Default.ShoppingCart,
    "music" to Icons.Default.MusicNote
)

fun getFolderIcon(iconName: String?): ImageVector {
    return when (iconName) {
        "star" -> Icons.Default.Star
        "book" -> Icons.Default.Book
        "code" -> Icons.Default.Code
        "heart" -> Icons.Default.Favorite
        "work" -> Icons.Default.Work
        "school" -> Icons.Default.School
        "home" -> Icons.Default.Home
        "shopping" -> Icons.Default.ShoppingCart
        "music" -> Icons.Default.MusicNote
        else -> Icons.Default.Folder
    }
}

fun parseHexColor(hex: String?, defaultColor: Color = Color(0xFF6750A4)): Color {
    if (hex.isNullOrBlank()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        defaultColor
    }
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
                        actions = {
                            IconButton(
                                onClick = { showAddFolderDialog = true },
                                modifier = Modifier.testTag("create_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "Create Folder",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAddFolderDialog = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("fab_create_folder")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Folder")
                    }
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
                    contentPadding = PaddingValues(bottom = 80.dp),
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
                        val icon = when (type) {
                            SavedItemType.LINK -> Icons.Outlined.Link
                            SavedItemType.IMAGE -> Icons.Outlined.Image
                            SavedItemType.VIDEO -> Icons.Outlined.VideoLibrary
                            SavedItemType.CODE -> Icons.Outlined.Code
                            SavedItemType.TEXT -> Icons.Outlined.Description
                            SavedItemType.AUDIO -> Icons.Outlined.Mic
                        }
                        SystemCategoryCard(
                            name = type.displayName,
                            count = count,
                            icon = icon,
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
                                        imageVector = Icons.Outlined.FolderOpen,
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
                        items(otherFolders.size, key = { "other_${otherFolders[it].name}" }, span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { index ->
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
                    placeholder = { Text("e.g. Work, Inspiration, Recipes...") },
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
    icon: ImageVector,
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
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
                Icon(
                    imageVector = getFolderIcon(folder.iconName),
                    contentDescription = null,
                    tint = themeColor,
                    modifier = Modifier.size(26.dp)
                )
                IconButton(
                    onClick = onCustomize,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
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
                        imageVector = Icons.Filled.PushPin,
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
                    Icon(
                        imageVector = getFolderIcon(folder.iconName),
                        contentDescription = null,
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
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.secondary
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
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()

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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (folderItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
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
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(folderItems, key = { it.id }) { item ->
                    // Direct standard Item card layout
                    FolderBrowseItemRow(
                        item = item,
                        onClick = { viewModel.showDetailItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderBrowseItemRow(
    item: SavedItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val displayIcon = when (item.type) {
        SavedItemType.LINK -> Icons.Outlined.Link
        SavedItemType.IMAGE -> Icons.Outlined.Image
        SavedItemType.VIDEO -> Icons.Outlined.VideoLibrary
        SavedItemType.CODE -> Icons.Outlined.Code
        SavedItemType.TEXT -> Icons.Outlined.Description
        SavedItemType.AUDIO -> Icons.Outlined.Mic
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(interactionSource)
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
                    imageVector = displayIcon,
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
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
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
                                imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
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
                            items(folderPresetIcons) { (iconName, iconVector) ->
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
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
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
                                            imageVector = Icons.Default.Check,
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
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
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

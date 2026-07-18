/*
 * Second Brain - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.example.utils.DevicePerformance

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FoldersScreen(
    viewModel: SecondBrainViewModel,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val allItems by viewModel.allItems.collectAsState()
    val customFolderEntities by viewModel.customFolderEntities.collectAsState()

    var activeBrowseFolder by remember { mutableStateOf<String?>(null) }

    val activeDetailItem by viewModel.activeDetailItem.collectAsState()

    BackHandler(enabled = activeBrowseFolder != null && activeDetailItem == null) {
        activeBrowseFolder = null
    }
    var folderToCustomize by remember { mutableStateOf<CustomFolderEntity?>(null) }

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    var folderSearchQuery by remember { mutableStateOf("") }

    var newFolderColorHex by remember { mutableStateOf(folderPresetColors.first().first) }
    var newFolderIconName by remember { mutableStateOf("folder") }
    var newFolderPinned by remember { mutableStateOf(false) }

    val resetNewFolderFields = {
        showAddFolderDialog = false
        newFolderName = ""
        newFolderColorHex = folderPresetColors.first().first
        newFolderIconName = "folder"
        newFolderPinned = false
    }

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
                onBack = { activeBrowseFolder = null },
                hazeState = hazeState
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
                    val pinnedFolders = remember(customFolderEntities, folderSearchQuery) {
                        customFolderEntities.filter {
                            it.isPinned && it.name.contains(folderSearchQuery, ignoreCase = true)
                        }
                    }
                    val otherFolders = remember(customFolderEntities, folderSearchQuery) {
                        customFolderEntities.filter {
                            !it.isPinned && it.name.contains(folderSearchQuery, ignoreCase = true)
                        }
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
                        // Search bar
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            OutlinedTextField(
                                value = folderSearchQuery,
                                onValueChange = { folderSearchQuery = it },
                                placeholder = { Text("Search folders...", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_search),
                                        contentDescription = "Search icon",
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (folderSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { folderSearchQuery = "" }) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_close),
                                                contentDescription = "Clear search",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }

                        if (folderSearchQuery.isBlank()) {
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
                                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }
                            ) { index ->
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

                val folderFabModifier = if (DevicePerformance.shouldUseBlur(context)) {
                    Modifier
                        .testTag("fab_create_folder")
                        .size(56.dp)
                        .clip(CircleShape)
                        .hazeEffect(state = hazeState, style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            tint = HazeTint(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            blurRadius = 20.dp,
                            noiseFactor = 0.05f
                        ))
                } else {
                    Modifier
                        .testTag("fab_create_folder")
                        .size(56.dp)
                        .clip(CircleShape)
                }

                val folderInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { showAddFolderDialog = true },
                        shape = CircleShape,
                        interactionSource = folderInteractionSource,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp
                        ),
                        containerColor = if (DevicePerformance.shouldUseBlur(context)) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = folderFabModifier
                            .bounceClick(folderInteractionSource)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_add_folder),
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
            onDismissRequest = { resetNewFolderFields() },
            title = { Text("New Custom Folder", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("e.g. Work, Inspiration....") },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_folder_name_field")
                    )

                    // Pin toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { newFolderPinned = !newFolderPinned }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_pin),
                                contentDescription = null,
                                tint = if (newFolderPinned) parseHexColor(newFolderColorHex) else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pin to top", fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = newFolderPinned,
                            onCheckedChange = { newFolderPinned = it },
                            thumbContent = if (newFolderPinned) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            }
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
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(folderPresetIcons.size) { index ->
                                val iconName = folderPresetIcons[index]
                                val isSelected = newFolderIconName == iconName
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) parseHexColor(newFolderColorHex).copy(alpha = 0.2f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            1.5.dp,
                                            if (isSelected) parseHexColor(newFolderColorHex)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                        .clickable { newFolderIconName = iconName },
                                    contentAlignment = Alignment.Center
                                ) {
                                    FolderIcon(
                                        iconName = iconName,
                                        tint = if (isSelected) parseHexColor(newFolderColorHex) else MaterialTheme.colorScheme.secondary,
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
                                val isSelected = newFolderColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { newFolderColorHex = hex },
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
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newFolderName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createFolder(
                                name = trimmed,
                                colorHex = newFolderColorHex,
                                iconName = newFolderIconName,
                                isPinned = newFolderPinned
                            )
                            resetNewFolderFields()
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_folder_btn")
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetNewFolderFields() }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
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
    val baseColor = when (name) {
        "Links" -> Color(0xFF42A5F5)
        "Images" -> Color(0xFFAB47BC)
        "Videos" -> Color(0xFFEF5350)
        "Text" -> Color(0xFFFFA726)
        "Code" -> Color(0xFF66BB6A)
        "Audio" -> Color(0xFF26A69A)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = baseColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, baseColor.copy(alpha = 0.2f)),
        modifier = modifier
            .height(72.dp)
            .bounceClick(interactionSource)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    tint = baseColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$count items",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = baseColor.copy(alpha = 0.8f)
            )
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
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_settings),
                        contentDescription = "Customize",
                        tint = themeColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
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
        color = themeColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.2f)),
        modifier = modifier
            .height(72.dp)
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(themeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    FolderIcon(
                        iconName = folder.iconName,
                        tint = themeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$count items",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = themeColor.copy(alpha = 0.8f)
                    )
                }
            }

            IconButton(
                onClick = onCustomize,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_settings),
                    contentDescription = "Customize",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderContentsBrowser(
    folderName: String,
    viewModel: SecondBrainViewModel,
    onBack: () -> Unit,
    hazeState: HazeState
) {
    val items by viewModel.allItems.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val context = LocalContext.current

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
        floatingActionButton = {
            val systemCategory = remember(folderName) {
                SavedItemType.entries.find { it.displayName == folderName }
            }
            val targetType = systemCategory ?: SavedItemType.TEXT
            val fabColor = when (targetType) {
                SavedItemType.LINK -> Color(0xFF42A5F5)
                SavedItemType.IMAGE -> Color(0xFFAB47BC)
                SavedItemType.VIDEO -> Color(0xFFEF5350)
                SavedItemType.TEXT -> Color(0xFFFFA726)
                SavedItemType.CODE -> Color(0xFF66BB6A)
                SavedItemType.AUDIO -> Color(0xFF26A69A)
            }
            
            val detailFabModifier = if (DevicePerformance.shouldUseBlur(context)) {
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .hazeEffect(state = hazeState, style = HazeStyle(
                        backgroundColor = fabColor,
                        tint = HazeTint(fabColor.copy(alpha = 0.45f)),
                        blurRadius = 20.dp,
                        noiseFactor = 0.05f
                    ))
            } else {
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            }
            
            val detailInteractionSource = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = {
                    if (systemCategory != null) {
                        viewModel.startManualCapture(systemCategory)
                    } else {
                        viewModel.startManualCapture(SavedItemType.TEXT, listOf(folderName))
                    }
                },
                shape = CircleShape,
                interactionSource = detailInteractionSource,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                ),
                containerColor = if (DevicePerformance.shouldUseBlur(context)) Color.Transparent else fabColor.copy(alpha = 0.8f),
                contentColor = Color.White,
                modifier = detailFabModifier
                    .bounceClick(detailInteractionSource)
                    .clip(CircleShape)
            ) {
                val iconRes = when (targetType) {
                    SavedItemType.LINK -> R.drawable.ic_custom_link
                    SavedItemType.IMAGE -> R.drawable.ic_custom_image
                    SavedItemType.VIDEO -> R.drawable.ic_custom_video
                    SavedItemType.TEXT -> R.drawable.ic_custom_text
                    SavedItemType.CODE -> R.drawable.ic_custom_code
                    SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                }
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "Capture",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncData() },
            state = pullToRefreshState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isSyncing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
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
                        FolderBrowseItemRow(
                            item = item,
                            viewModel = viewModel,
                            onClick = { viewModel.showDetailItem(item) },
                            modifier = Modifier.animateItem()
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
    viewModel: SecondBrainViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var showContextMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    val iconResId = when (item.type) {
        SavedItemType.LINK -> R.drawable.ic_custom_link
        SavedItemType.IMAGE -> R.drawable.ic_custom_image
        SavedItemType.VIDEO -> R.drawable.ic_custom_video
        SavedItemType.CODE -> R.drawable.ic_custom_code
        SavedItemType.TEXT -> R.drawable.ic_custom_text
        SavedItemType.AUDIO -> R.drawable.ic_custom_voice
    }

    if (showMoveDialog) {
        FolderMoveDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { showMoveDialog = false }
        )
    }

    Box(modifier = modifier) {
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
                    onLongClick = { showContextMenu = true }
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
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier
                .width(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            // 1. View Action (For all)
            DropdownMenuItem(
                leadingIcon = { 
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_eye), 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    ) 
                },
                text = { Text("View Detail", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                onClick = {
                    showContextMenu = false
                    onClick()
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.primary
                )
            )

            // 2. Content Aware Action
            when (item.type) {
                SavedItemType.LINK -> {
                    DropdownMenuItem(
                        leadingIcon = { 
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_globe), 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            ) 
                        },
                        text = { Text("Open in Browser", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                        onClick = {
                            showContextMenu = false
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.content))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Cannot open browser", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                SavedItemType.TEXT, SavedItemType.CODE -> {
                    DropdownMenuItem(
                        leadingIcon = { 
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_copy), 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            ) 
                        },
                        text = { Text("Copy to Clipboard", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                        onClick = {
                            showContextMenu = false
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Second Brain Note", item.content)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                SavedItemType.AUDIO -> {
                    DropdownMenuItem(
                        leadingIcon = { 
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_play), 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            ) 
                        },
                        text = { Text("Listen / Play Audio", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                        onClick = {
                            showContextMenu = false
                            onClick()
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                SavedItemType.IMAGE, SavedItemType.VIDEO -> {
                    DropdownMenuItem(
                        leadingIcon = { 
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_eye), 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            ) 
                        },
                        text = { Text("View Fullscreen", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                        onClick = {
                            showContextMenu = false
                            onClick()
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // 3. Move Action (For all)
            DropdownMenuItem(
                leadingIcon = { 
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_folder), 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    ) 
                },
                text = { Text("Move / Assign Folders", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                onClick = {
                    showContextMenu = false
                    showMoveDialog = true
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.primary
                )
            )

            // 4. Archive Action (For all)
            val isArchived = item.folders.contains("Archive")
            DropdownMenuItem(
                leadingIcon = { 
                    Icon(
                        painter = painterResource(id = if (isArchived) R.drawable.ic_custom_unarchive else R.drawable.ic_custom_archive), 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    ) 
                },
                text = { Text(if (isArchived) "Unarchive" else "Archive", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                onClick = {
                    showContextMenu = false
                    if (isArchived) {
                        viewModel.unarchiveItem(item)
                    } else {
                        viewModel.archiveItem(item)
                    }
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.primary
                )
            )

            // 5. Share Action (For all)
            DropdownMenuItem(
                leadingIcon = { 
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_share), 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    ) 
                },
                text = { Text("Share", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                onClick = {
                    showContextMenu = false
                    try {
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, item.content)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share preserve"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Cannot share", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.primary
                )
            )

            // 6. Delete Action (For all)
            DropdownMenuItem(
                leadingIcon = { 
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_delete), 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    ) 
                },
                text = { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                onClick = {
                    showContextMenu = false
                    viewModel.deleteSavedItem(item)
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                )
            )
        }
    }
}

@Composable
fun FolderMoveDialog(
    item: SavedItem,
    viewModel: SecondBrainViewModel,
    onDismiss: () -> Unit
) {
    val customFolders by viewModel.customFolders.collectAsState()
    var currentFolders by remember { mutableStateOf(item.folders.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move / Assign Folders") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (customFolders.isEmpty()) {
                    Text("No custom folders created yet.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    customFolders.forEach { folder ->
                        val isAssigned = currentFolders.contains(folder)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentFolders = if (isAssigned) {
                                        currentFolders - folder
                                    } else {
                                        currentFolders + folder
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = isAssigned,
                                onCheckedChange = { checked ->
                                    currentFolders = if (checked == true) {
                                        currentFolders + folder
                                    } else {
                                        currentFolders - folder
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(folder)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = item.copy(folders = currentFolders.toList())
                    viewModel.updateSavedItem(updated)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
                            onCheckedChange = { isPinned = it },
                            thumbContent = if (isPinned) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            }
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

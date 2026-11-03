/*
 * Cobalt - A universal capture and personal knowledge archive
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

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.ui.screens.getFolderIconRes
import com.example.ui.screens.parseHexColor
import com.example.util.FolderRankingEngine
import com.example.util.RankedFolder

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoveToFolderDialog(
    title: String = "Move to Folder",
    subtitle: String? = null,
    customFolders: List<String>,
    customFolderEntities: List<CustomFolderEntity>,
    allItems: List<SavedItem> = emptyList(),
    currentItem: SavedItem? = null,
    initialSelectedFolders: Set<String> = emptySet(),
    isMultiSelect: Boolean = true,
    rankingEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onCreateFolder: (name: String, iconName: String?) -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selectedFolders by remember { mutableStateOf(initialSelectedFolders) }
    var searchQuery by remember { mutableStateOf("") }
    var showNewFolderCreator by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val rankedFolders = remember(customFolders, customFolderEntities, allItems, rankingEnabled, currentItem) {
        FolderRankingEngine.rankFolders(
            folders = customFolders,
            folderEntities = customFolderEntities,
            allItems = allItems,
            currentItemType = currentItem?.type,
            currentItemTitle = currentItem?.title,
            currentItemContent = currentItem?.content,
            rankingEnabled = rankingEnabled
        )
    }

    val filteredFolders = remember(rankedFolders, searchQuery) {
        if (searchQuery.isBlank()) {
            rankedFolders
        } else {
            rankedFolders.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
                .animateContentSize(animationSpec = spring()),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_folder),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_move_dialog")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_close),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar if more than 4 folders
                if (customFolders.size > 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("folder_search_input"),
                        placeholder = {
                            Text(
                                text = "Search folders...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_close),
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Folders List
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    if (filteredFolders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching folders" else "No folders created yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filteredFolders, key = { it.name }) { ranked ->
                                val folder = ranked.name
                                val isSelected = selectedFolders.contains(folder)
                                val folderEntity = ranked.entity ?: customFolderEntities.find { it.name.equals(folder, ignoreCase = true) }
                                val folderColor = parseHexColor(
                                    folderEntity?.colorHex,
                                    defaultColor = MaterialTheme.colorScheme.primary,
                                    isDark = isDark
                                )
                                val iconRes = getFolderIconRes(folderEntity?.iconName)

                                Surface(
                                    onClick = {
                                        selectedFolders = if (isMultiSelect) {
                                            if (isSelected) selectedFolders - folder else selectedFolders + folder
                                        } else {
                                            if (isSelected) emptySet() else setOf(folder)
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) {
                                        folderColor.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) {
                                            folderColor
                                        } else if (ranked.isSuggested) {
                                            folderColor.copy(alpha = 0.65f)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .folderSuggestionShimmer(
                                            color = folderColor,
                                            enabled = ranked.isSuggested && !isSelected
                                        )
                                        .bounceClick()
                                        .testTag("move_folder_row_$folder")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(folderColor.copy(alpha = 0.20f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = null,
                                                tint = folderColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = folder,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (ranked.isSuggested && !isSelected) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = folderColor.copy(alpha = 0.12f)
                                                    ) {
                                                        Text(
                                                            text = "Suggested",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = folderColor,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (ranked.usageCount > 0) {
                                                Text(
                                                    text = "${ranked.usageCount} ${if (ranked.usageCount == 1) "item" else "items"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }

                                        // Selection indicator
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) folderColor else Color.Transparent
                                                )
                                                .then(
                                                    if (!isSelected) {
                                                        Modifier.background(
                                                            Color.Transparent,
                                                            CircleShape
                                                        )
                                                    } else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_custom_check),
                                                    contentDescription = null,
                                                    tint = if (isDark) Color.Black else Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // New Folder Button / Inline Creator (Same 44.dp height & 16.dp corner radius for zero modal layout shift)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    androidx.compose.animation.Crossfade(
                        targetState = showNewFolderCreator,
                        label = "newFolderCreatorCrossfade"
                    ) { isCreating ->
                        if (isCreating) {
                            InlineNewFolderCreator(
                                onAddFolder = { name, iconName ->
                                    onCreateFolder(name, iconName)
                                    selectedFolders = selectedFolders + name
                                    showNewFolderCreator = false
                                },
                                onCancel = { showNewFolderCreator = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Surface(
                                onClick = { showNewFolderCreator = true },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .bounceClick()
                                    .testTag("dialog_new_folder_button")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_plus),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Create New Folder",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("dialog_cancel_button")
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            onConfirm(selectedFolders)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("dialog_confirm_button")
                    ) {
                        Text(
                            text = if (isMultiSelect) "Apply (${selectedFolders.size})" else "Move",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

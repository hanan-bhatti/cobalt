/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SavedItemType
import com.example.ui.screens.FolderChipItem
import com.example.ui.screens.getFolderIconRes
import com.example.ui.screens.parseHexColor
import com.example.ui.viewmodel.CobaltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFilterBottomSheet(
    viewModel: CobaltViewModel,
    itemCount: Int,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val homeSortOrder by viewModel.homeSortOrder.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()
    val customFolderEntities by viewModel.customFolderEntities.collectAsState()
    val isDark = isSystemInDarkTheme()

    val filteredCustomFolders = remember(customFolders) { customFolders.filter { it != "Archive" } }
    val systemCategories = remember { SavedItemType.entries.map { it.displayName } }
    val allFolderKeys = remember(filteredCustomFolders, systemCategories) {
        listOf("All") + filteredCustomFolders + systemCategories + listOf("Archive")
    }

    val hasActiveFilters = selectedFolder != "All" || homeSortOrder != "Newest"
    val sortOptions = remember { listOf("Newest", "Oldest", "Title A-Z", "Size") }
    val sortListState = rememberLazyListState()
    val folderListState = rememberLazyListState()

    LaunchedEffect(homeSortOrder) {
        val index = sortOptions.indexOf(homeSortOrder)
        if (index >= 0) {
            sortListState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(selectedFolder) {
        val index = allFolderKeys.indexOf(selectedFolder)
        if (index >= 0) {
            folderListState.animateScrollToItem(index)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_filter),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Filter & Sort",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ORGANIZATION & ARCHIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (hasActiveFilters) {
                    TextButton(
                        onClick = { viewModel.resetHomeFilters() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Section 1: Sort Order (Single Row Scrollable with Edge Fades)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSubsectionHeader(
                    title = "SORT BY",
                    description = "Change the order of your captured items"
                )
                LazyRow(
                    state = sortListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalFadingEdges(lazyListState = sortListState, fadeWidth = 24.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(sortOptions, key = { it }) { sort ->
                        val isSelected = homeSortOrder == sort
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { viewModel.setHomeSortOrder(sort) }
                        ) {
                            Text(
                                text = sort,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Section 2: Folders (Single Row Scrollable with Edge Fades)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSubsectionHeader(
                    title = "FOLDERS",
                    description = if (selectedFolder != "All") "Current filter: $selectedFolder" else "All folders & categories"
                )

                LazyRow(
                    state = folderListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalFadingEdges(lazyListState = folderListState, fadeWidth = 24.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. All Folder Chip
                    item(key = "folder_all") {
                        val isSelected = selectedFolder == "All"
                        FolderChipItem(
                            folder = "All",
                            isSelected = isSelected,
                            painter = painterResource(id = R.drawable.ic_custom_folder),
                            onClick = { viewModel.setFolderFilter("All") }
                        )
                    }

                    // 2. Custom Folders
                    items(filteredCustomFolders, key = { "custom_$it" }) { folder ->
                        val isSelected = selectedFolder == folder
                        val folderEntity = customFolderEntities.find { it.name.equals(folder, ignoreCase = true) }
                        val iconRes = getFolderIconRes(folderEntity?.iconName)
                        val folderAccentColor = folderEntity?.colorHex?.takeIf { it.isNotBlank() }?.let { hex ->
                            parseHexColor(hex, defaultColor = MaterialTheme.colorScheme.primary, isDark = isDark)
                        }
                        FolderChipItem(
                            folder = folder,
                            isSelected = isSelected,
                            painter = painterResource(id = iconRes),
                            accentColor = folderAccentColor,
                            onClick = { viewModel.setFolderFilter(folder) }
                        )
                    }

                    // 3. System Category Folders
                    items(systemCategories, key = { "system_$it" }) { category ->
                        val isSelected = selectedFolder == category
                        val painter = when (category) {
                            "Links" -> painterResource(id = R.drawable.ic_custom_link)
                            "Images" -> painterResource(id = R.drawable.ic_custom_image)
                            "Videos" -> painterResource(id = R.drawable.ic_custom_video)
                            "Code" -> painterResource(id = R.drawable.ic_custom_code)
                            "Text" -> painterResource(id = R.drawable.ic_custom_text)
                            "Audio" -> painterResource(id = R.drawable.ic_custom_voice)
                            "Movies & Anime" -> painterResource(id = R.drawable.ic_custom_movie)
                            else -> painterResource(id = R.drawable.ic_custom_folder)
                        }
                        FolderChipItem(
                            folder = category,
                            isSelected = isSelected,
                            painter = painter,
                            onClick = { viewModel.setFolderFilter(category) }
                        )
                    }

                    // 4. Archive Folder
                    item(key = "folder_archive") {
                        val isSelected = selectedFolder == "Archive"
                        FolderChipItem(
                            folder = "Archive",
                            isSelected = isSelected,
                            painter = painterResource(id = R.drawable.ic_custom_archive),
                            onClick = { viewModel.setFolderFilter("Archive") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Button
            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (itemCount > 0) "Show $itemCount Items" else "Done",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

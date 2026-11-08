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
import com.example.ui.viewmodel.CobaltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaHubFilterBottomSheet(
    viewModel: CobaltViewModel,
    mediaCount: Int,
    typeFilters: List<String>,
    genreFilters: List<String>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedStatus by viewModel.mediaHubFilterStatus.collectAsState()
    val selectedType by viewModel.mediaHubFilterType.collectAsState()
    val selectedGenre by viewModel.mediaHubFilterGenre.collectAsState()
    val selectedSort by viewModel.mediaHubSortOrder.collectAsState()

    val hasActiveFilters = selectedStatus != "All" ||
            selectedType != "All Types" ||
            selectedGenre != "All Genres" ||
            selectedSort != "Default"

    val statusFilters = remember { listOf("All", "Plan to Watch", "Watching", "Completed", "Dropped") }
    val sortOptions = remember { listOf("Default", "Rating", "Newest", "Year", "Title A-Z") }

    val sortListState = rememberLazyListState()
    val typeListState = rememberLazyListState()
    val genreListState = rememberLazyListState()

    LaunchedEffect(selectedSort) {
        val index = sortOptions.indexOf(selectedSort)
        if (index >= 0) {
            sortListState.animateScrollToItem(index)
        }
    }


    LaunchedEffect(selectedType) {
        val index = typeFilters.indexOf(selectedType)
        if (index >= 0) {
            typeListState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(selectedGenre) {
        val index = genreFilters.indexOf(selectedGenre)
        if (index >= 0) {
            genreListState.animateScrollToItem(index)
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
                            text = "Media Filters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "MOVIES, TV SHOWS & ANIME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                    }
                }

                if (hasActiveFilters) {
                    TextButton(
                        onClick = { viewModel.resetMediaHubFilters() },
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

            // Section 1: Sort By (Single Row Scrollable with Edge Fades)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSubsectionHeader(
                    title = "SORT BY",
                    description = "Choose display order for your library"
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
                        val isSelected = selectedSort == sort
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
                                .clickable { viewModel.setMediaHubSortOrder(sort) }
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

            // Section 2: Watch Status (Single Row Scrollable with Edge Fades)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSubsectionHeader(
                    title = "PROGRESS STATUS",
                    description = if (selectedStatus != "All") "Filtered: $selectedStatus" else "All statuses"
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    statusFilters.forEach { status ->
                        FilterChip(selected = selectedStatus == status, onClick = { viewModel.setMediaHubFilterStatus(status) },
                            label = { Text(when(status) { "Plan to Watch" -> "Planned"; "Watching" -> "In progress"; else -> status }) })
                    }
                }
            }


            // Section 3: Media Format / Category (Single Row Scrollable with Edge Fades)
            if (typeFilters.size > 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsSubsectionHeader(
                        title = "MEDIA FORMAT",
                        description = "Filter by movies, TV series, anime or manga"
                    )
                    LazyRow(
                        state = typeListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalFadingEdges(lazyListState = typeListState, fadeWidth = 24.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(typeFilters, key = { it }) { type ->
                            val isSelected = selectedType == type
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
                                    .clickable { viewModel.setMediaHubFilterType(type) }
                            ) {
                                Text(
                                    text = type,
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
            }

            // Section 4: Genre (Single Row Scrollable with Edge Fades)
            if (genreFilters.size > 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsSubsectionHeader(
                        title = "GENRES",
                        description = if (selectedGenre != "All Genres") "Filtered: $selectedGenre" else "All available genres"
                    )
                    LazyRow(
                        state = genreListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalFadingEdges(lazyListState = genreListState, fadeWidth = 24.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(genreFilters, key = { "genre_$it" }) { genre ->
                            val isSelected = selectedGenre == genre
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
                                    .clickable { viewModel.setMediaHubFilterGenre(genre) }
                            ) {
                                Text(
                                    text = genre,
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
                    text = if (mediaCount > 0) "Show $mediaCount Titles" else "Done",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

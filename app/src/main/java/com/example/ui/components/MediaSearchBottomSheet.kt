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

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.remote.MediaSearchResultItem
import com.example.ui.viewmodel.SecondBrainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSearchBottomSheet(viewModel: SecondBrainViewModel) {
    val showSheet by viewModel.showMediaSearchBottomSheet.collectAsState()
    if (!showSheet) return

    val searchResults by viewModel.mediaSearchResults.collectAsState()
    val isSearchingMedia by viewModel.isSearchingMedia.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(searchQuery) {
        delay(350)
        viewModel.searchMedia(searchQuery)
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeMediaSearchSheet() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = "Search Movies, TV & Anime",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.closeMediaSearchSheet() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Close"
                    )
                }
            }

            // Minimal Search Input Text Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search title...") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_close),
                                contentDescription = "Clear",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Loading Indicator or Results List
            if (isSearchingMedia) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Type a movie, TV show, or anime name to search" else "No results found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(searchResults, key = { it.id }) { item ->
                        MediaSearchResultCard(
                            item = item,
                            onSave = { status ->
                                viewModel.saveMediaItem(item, watchStatus = status)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaSearchResultCard(
    item: MediaSearchResultItem,
    onSave: (watchStatus: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedStatusMenu by remember { mutableStateOf(false) }
    val watchStatusOptions = listOf("Plan to Watch", "Watching", "Completed", "Dropped")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Poster thumbnail
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 70.dp, height = 100.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 70.dp, height = 100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_movie),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Header Row: Media type badge + Year
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val formattedType = when (item.mediaType.lowercase()) {
                        "movie" -> "Movie"
                        "tv" -> "TV Show"
                        "anime" -> "Anime"
                        else -> item.mediaType.replaceFirstChar { it.uppercase() }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = formattedType,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (!item.releaseYear.isNullOrBlank()) {
                        Text(
                            text = item.releaseYear,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!item.overview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Save Action Button with Dropdown Menu
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { onSave("Plan to Watch") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Save", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = { expandedStatusMenu = true },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 10.dp, bottomEnd = 10.dp),
                        modifier = Modifier.height(36.dp).width(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_chevron_down),
                            contentDescription = "Select status",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expandedStatusMenu,
                    onDismissRequest = { expandedStatusMenu = false }
                ) {
                    watchStatusOptions.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status) },
                            onClick = {
                                expandedStatusMenu = false
                                onSave(status)
                            }
                        )
                    }
                }
            }
        }
    }
}

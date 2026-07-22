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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.getBestImagePath
import com.example.ui.theme.CategoryMedia
import com.example.ui.viewmodel.SecondBrainViewModel
import com.example.utils.DevicePerformance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaHubScreen(
    viewModel: SecondBrainViewModel,
    onMediaClick: (SavedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSyncing by viewModel.isSyncing.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val blurOpacity by viewModel.blurOpacity.collectAsState()

    val fabColor = MaterialTheme.colorScheme.surfaceVariant
    val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur
    val fabModifier = if (useBlur) {
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .hazeEffect(state = hazeState, style = HazeStyle(
                backgroundColor = fabColor,
                tint = HazeTint(fabColor.copy(alpha = blurOpacity)),
                blurRadius = blurRadius.dp,
                noiseFactor = 0.02f
            ))
    } else {
        Modifier
            .size(56.dp)
            .clip(CircleShape)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_movie),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Media Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openMediaSearchSheet() },
                shape = CircleShape,
                containerColor = if (useBlur) Color.Transparent else fabColor.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                ),
                modifier = fabModifier
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_search),
                    contentDescription = "Search Movies & Anime",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncData() },
            state = pullToRefreshState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .hazeSource(state = hazeState),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isSyncing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            MediaHubContent(
                viewModel = viewModel,
                onMediaClick = onMediaClick
            )
        }
    }
}

@Composable
fun MediaHubContent(
    viewModel: SecondBrainViewModel,
    onMediaClick: (SavedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val allItems by viewModel.allItems.collectAsState()
    var selectedStatus by rememberSaveable { mutableStateOf("All") }
    var selectedType by rememberSaveable { mutableStateOf("All Types") }
    var selectedGenre by rememberSaveable { mutableStateOf("All Genres") }

    val allMediaItems = remember(allItems) {
        allItems.filter { item ->
            item.type == SavedItemType.MEDIA && !item.folders.contains("Archive")
        }
    }

    val statusFilters = remember { listOf("All", "Plan to Watch", "Watching", "Completed", "Dropped") }

    val typeFilters = remember(allMediaItems) {
        val types = allMediaItems.mapNotNull { it.mediaType?.lowercase() }.toSet()
        val list = mutableListOf("All Types")
        if (types.contains("movie")) list.add("Movies")
        if (types.contains("tv") || types.contains("tv show") || types.contains("tv_show")) list.add("TV")
        if (types.contains("anime")) list.add("Anime")
        list
    }

    val genreFilters = remember(allMediaItems, selectedType) {
        val filteredForType = allMediaItems.filter { item ->
            when (selectedType) {
                "All Types" -> true
                "Movies" -> item.mediaType?.lowercase() == "movie"
                "TV" -> item.mediaType?.lowercase() in listOf("tv", "tv show", "tv_show")
                "Anime" -> item.mediaType?.lowercase() == "anime"
                else -> true
            }
        }
        val uniqueGenres = filteredForType.flatMap { it.genres }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        listOf("All Genres") + uniqueGenres
    }

    LaunchedEffect(genreFilters) {
        if (!genreFilters.contains(selectedGenre)) {
            selectedGenre = "All Genres"
        }
    }

    LaunchedEffect(typeFilters) {
        if (!typeFilters.contains(selectedType)) {
            selectedType = "All Types"
        }
    }

    val mediaItems = remember(allMediaItems, selectedStatus, selectedType, selectedGenre) {
        allMediaItems.filter { item ->
            if (selectedStatus == "All") true
            else item.watchStatus?.equals(selectedStatus, ignoreCase = true) == true
        }.filter { item ->
            when (selectedType) {
                "All Types" -> true
                "Movies" -> item.mediaType?.lowercase() == "movie"
                "TV" -> item.mediaType?.lowercase() in listOf("tv", "tv show", "tv_show")
                "Anime" -> item.mediaType?.lowercase() == "anime"
                else -> true
            }
        }.filter { item ->
            if (selectedGenre == "All Genres") true
            else item.genres.contains(selectedGenre)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Status Filter Row (Horizontal scroll)
        Text(
            text = "Status Filter",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(statusFilters, key = { "status_$it" }) { status ->
                val isSelected = selectedStatus == status
                FilterChipItem(
                    label = status,
                    isSelected = isSelected,
                    onClick = { selectedStatus = status }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category/Type Filter Row (Horizontal scroll)
        Text(
            text = "Media Category",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(typeFilters, key = { "type_$it" }) { type ->
                val isSelected = selectedType == type
                FilterChipItem(
                    label = type,
                    isSelected = isSelected,
                    onClick = { selectedType = type }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Genre Filter Row (Horizontal scroll)
        if (genreFilters.size > 1) {
            Text(
                text = "Genre Filter",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(genreFilters, key = { "genre_$it" }) { genre ->
                    val isSelected = selectedGenre == genre
                    FilterChipItem(
                        label = genre,
                        isSelected = isSelected,
                        onClick = { selectedGenre = genre }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Content Grid or Empty State
        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_movie),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Media Items Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedStatus != "All" || selectedType != "All Types")
                            "No items match '$selectedStatus' status and '$selectedType' category."
                        else
                            "Your media collection is empty. Tap 'Search Media' below to discover and save movies, TV shows, and anime!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(mediaItems, key = { it.id }) { item ->
                    MediaItemCard(
                        item = item,
                        onClick = { onMediaClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun MediaItemCard(
    item: SavedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imagePath = item.getBestImagePath()
    val formattedType = when (item.mediaType?.lowercase()) {
        "movie" -> "Movie"
        "tv", "tv show", "tv_show" -> "TV"
        "anime" -> "Anime"
        else -> item.mediaType?.replaceFirstChar { it.uppercase() } ?: "Media"
    }
    val badgeText = if (!item.releaseYear.isNullOrBlank()) {
        "${item.releaseYear} • ${formattedType.uppercase()}"
    } else {
        formattedType
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Poster Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!imagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = imagePath,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_movie),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Media Type Badge overlay (Top-Left)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Rating Badge overlay (Top-Right)
                if (item.rating != null && item.rating > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "★ ${String.format(java.util.Locale.US, "%.1f", item.rating)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Status Badge Chip
            val status = item.watchStatus ?: "Plan to Watch"
            val (statusBg, statusFg) = when (status.lowercase()) {
                "watching" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                "completed" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                "dropped" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusBg,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = statusFg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

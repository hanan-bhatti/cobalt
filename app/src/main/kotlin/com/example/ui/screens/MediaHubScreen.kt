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

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import com.example.ui.components.FloatingScrollPill
import com.example.ui.components.MediaHubFilterBottomSheet
import com.example.ui.components.rememberLazyGridScrollAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.example.ui.viewmodel.CobaltViewModel
import com.example.utils.DevicePerformance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MediaHubScreen(
    viewModel: CobaltViewModel,
    onMediaClick: (SavedItem) -> Unit,
    onNavigateToTraktSync: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val blurOpacity by viewModel.blurOpacity.collectAsState()
    val floatingScrollbarEnabled by viewModel.floatingScrollbarEnabled.collectAsState()
    val floatingScrollbarMediaHub by viewModel.floatingScrollbarMediaHub.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }

    val allItems by viewModel.allItems.collectAsState()
    val selectedStatus by viewModel.mediaHubFilterStatus.collectAsState()
    val selectedType by viewModel.mediaHubFilterType.collectAsState()
    val selectedGenre by viewModel.mediaHubFilterGenre.collectAsState()
    val selectedSort by viewModel.mediaHubSortOrder.collectAsState()

    val allMediaItems = remember(allItems) {
        allItems.filter { item ->
            item.type == SavedItemType.MEDIA && !item.isArchived
        }
    }

    val typeFilters = remember(allMediaItems) {
        val types = allMediaItems.mapNotNull { it.mediaType?.lowercase() }.toSet()
        val list = mutableListOf("All Types")
        if (types.contains("movie")) list.add("Movies")
        if (types.contains("tv") || types.contains("tv show") || types.contains("tv_show")) list.add("TV")
        if (types.contains("anime")) list.add("Anime")
        if (types.contains("manga")) list.add("Manga")
        list
    }

    val genreFilters = remember(allMediaItems, selectedType) {
        val filteredForType = allMediaItems.filter { item ->
            when (selectedType) {
                "All Types" -> true
                "Movies" -> item.mediaType?.lowercase() == "movie"
                "TV" -> item.mediaType?.lowercase() in listOf("tv", "tv show", "tv_show")
                "Anime" -> item.mediaType?.lowercase() == "anime"
                "Manga" -> item.mediaType?.lowercase() == "manga"
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
            viewModel.setMediaHubFilterGenre("All Genres")
        }
    }

    LaunchedEffect(typeFilters) {
        if (!typeFilters.contains(selectedType)) {
            viewModel.setMediaHubFilterType("All Types")
        }
    }

    val mediaItems = remember(allMediaItems, selectedStatus, selectedType, selectedGenre, selectedSort) {
        allMediaItems.filter { item ->
            when (selectedStatus) {
                "All" -> true
                "Completed" -> item.watchStatus.equals("Completed", ignoreCase = true) || item.watchStatus.equals("Watched", ignoreCase = true)
                else -> item.watchStatus.equals(selectedStatus, ignoreCase = true)
            }
        }.filter { item ->
            when (selectedType) {
                "All Types" -> true
                "Movies" -> item.mediaType?.lowercase() == "movie"
                "TV" -> item.mediaType?.lowercase() in listOf("tv", "tv show", "tv_show")
                "Anime" -> item.mediaType?.lowercase() == "anime"
                "Manga" -> item.mediaType?.lowercase() == "manga"
                else -> true
            }
        }.filter { item ->
            if (selectedGenre == "All Genres") true
            else item.genres.contains(selectedGenre)
        }.let { list ->
            when (selectedSort) {
                "Rating" -> list.sortedByDescending { it.rating ?: 0.0 }
                "Newest" -> list.sortedByDescending { it.timestamp }
                "Year" -> list.sortedByDescending { it.releaseYear ?: "" }
                "Title A-Z" -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                else -> list
            }
        }
    }

    val hasActiveFilters = selectedStatus != "All" ||
            selectedType != "All Types" ||
            selectedGenre != "All Genres" ||
            selectedSort != "Default"

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
                            painter = painterResource(id = R.drawable.ic_nav_media_outline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Media Hub",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "MOVIES, TV SHOWS & ANIME",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_filter),
                                contentDescription = "Media Filter",
                                tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                            if (hasActiveFilters) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }
                    if (onNavigateToTraktSync != null) {
                        IconButton(onClick = onNavigateToTraktSync) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_settings),
                                contentDescription = "Media settings",
                                tint = Color(0xFFED1C24),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
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
        Box(modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()) {
            MediaHubContent(
                viewModel = viewModel,
                mediaItems = mediaItems,
                hasActiveFilters = hasActiveFilters,
                onMediaClick = onMediaClick,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                gridState = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
            )

            FloatingScrollPill(
                adapter = rememberLazyGridScrollAdapter(gridState),
                localHazeState = hazeState,
                enabled = floatingScrollbarEnabled && floatingScrollbarMediaHub,
                useBlur = useBlur,
                blurRadius = blurRadius,
                blurOpacity = blurOpacity,
                topPadding = 80.dp,
                bottomPadding = 120.dp
            )
        }
    }

    if (showFilterSheet) {
        MediaHubFilterBottomSheet(
            viewModel = viewModel,
            mediaCount = mediaItems.size,
            typeFilters = typeFilters,
            genreFilters = genreFilters,
            onDismissRequest = { showFilterSheet = false }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MediaHubContent(
    viewModel: CobaltViewModel,
    mediaItems: List<SavedItem>,
    hasActiveFilters: Boolean,
    onMediaClick: (SavedItem) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier
) {
    val traktSyncStatus by com.example.data.remote.trakt.TraktSyncEngine.syncStatus.collectAsState()
    val isTraktSyncing = traktSyncStatus is com.example.data.remote.trakt.TraktSyncStatus.Syncing

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Active Trakt Sync Banner
        androidx.compose.animation.AnimatedVisibility(visible = isTraktSyncing) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val msg = (traktSyncStatus as? com.example.data.remote.trakt.TraktSyncStatus.Syncing)?.message ?: "Syncing with Trakt.tv..."
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
                        text = if (hasActiveFilters)
                            "No media items match your active filters."
                        else
                            "Your media collection is empty. Tap 'Search Media' below to discover and save movies, TV shows, and anime!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (hasActiveFilters) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.resetMediaHubFilters() }) {
                            Text("Reset Filters")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(mediaItems, key = { it.id }) { item ->
                    MediaItemCard(
                        item = item,
                        onClick = { onMediaClick(item) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MediaItemCard(
    item: SavedItem,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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

    val isHomeHub = TransitionCoordinator.isHomeHubTransition()
    val isDetail = TransitionCoordinator.isDetailTransition()
    val isVisibleOnHome = item.id in TransitionCoordinator.homeVisibleItemIds
    val enableSharedBounds = isDetail || (isHomeHub && isVisibleOnHome)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .maybeSharedBounds(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                key = "card_${item.id}",
                clipShape = RoundedCornerShape(16.dp),
                enabled = enableSharedBounds
            )
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
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
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

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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.example.util.displayTitle
import com.example.utils.DevicePerformance
import com.example.ui.components.FloatingScrollPill
import com.example.ui.components.HomeFilterBottomSheet
import com.example.ui.components.rememberLazyListScrollAdapter
import com.example.ui.components.rememberLazyStaggeredGridScrollAdapter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import com.example.ui.components.bounceClick
import com.example.ui.components.InlineNewFolderCreator
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import coil.compose.AsyncImage
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.getBestImagePath
import com.example.ui.components.CobaltWelcomeIllustration
import com.example.ui.components.HowToCaptureBottomSheet
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Deterministic resolution for all Home screen empty and content states.
 */
enum class HomeEmptyState {
    LOADING,
    CONTENT,
    SEARCH_EMPTY,
    FILTER_EMPTY,
    RETURNING_EMPTY,
    FIRST_TIME_WELCOME
}

fun resolveHomeEmptyState(
    isLoading: Boolean,
    itemCount: Int,
    totalArchiveCount: Int,
    searchQuery: String,
    hasDismissedOnboarding: Boolean
): HomeEmptyState {
    if (isLoading) return HomeEmptyState.LOADING
    if (itemCount > 0) return HomeEmptyState.CONTENT
    if (searchQuery.isNotBlank()) return HomeEmptyState.SEARCH_EMPTY
    if (totalArchiveCount > 0) return HomeEmptyState.FILTER_EMPTY
    if (hasDismissedOnboarding) return HomeEmptyState.RETURNING_EMPTY
    return HomeEmptyState.FIRST_TIME_WELCOME
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    viewModel: CobaltViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToMediaHub: () -> Unit = {},
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onItemClick: ((SavedItem) -> Unit)? = null
) {
    val handleItemClick: (SavedItem) -> Unit = { clickedItem ->
        if (onItemClick != null) {
            onItemClick(clickedItem)
        } else {
            viewModel.showDetailItem(clickedItem)
        }
    }

    val items by viewModel.homeItems.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()
    val customFolderEntities by viewModel.customFolderEntities.collectAsState()
    val isFolderRankingEnabled by viewModel.isFolderRankingEnabled.collectAsState()
    val folderColorsMap = remember(customFolderEntities) {
        customFolderEntities.associate { it.name to (it.colorHex ?: "") }
    }
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItemIds by viewModel.selectedItemIds.collectAsState()
    val hasDismissedOnboarding by viewModel.hasDismissedOnboarding.collectAsState()
    val emptyState = resolveHomeEmptyState(
        isLoading = isInitialLoading,
        itemCount = items.size,
        totalArchiveCount = allItems.size,
        searchQuery = searchQuery,
        hasDismissedOnboarding = hasDismissedOnboarding
    )
    val themeMode by viewModel.settingsRepository.themeMode.collectAsState()
    val isDark = when (themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val isListView by viewModel.settingsRepository.isListView.collectAsState()
    var itemToManageFolders by remember { mutableStateOf<SavedItem?>(null) }
    var isFabExpanded by remember { mutableStateOf(false) }
    val isRecentCapturesExpanded by viewModel.isRecentCapturesExpanded.collectAsState()

    val speechRecognizerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = matches?.firstOrNull()
            if (!recognizedText.isNullOrBlank()) {
                viewModel.startManualCapture(SavedItemType.AUDIO)
                viewModel.updateActiveCaptureItem { item ->
                    item.copy(
                        title = "Voice Memo",
                        content = "Transcription: $recognizedText"
                    )
                }
                viewModel.saveActiveItem()
            }
        }
    }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("draft_prefs", android.content.Context.MODE_PRIVATE) }
    var showSearchPageOverlay by remember { mutableStateOf(false) }

    BackHandler(enabled = showSearchPageOverlay || isSelectionMode || isFabExpanded) {
        if (showSearchPageOverlay) {
            showSearchPageOverlay = false
        } else if (isSelectionMode) {
            viewModel.clearSelection()
        } else if (isFabExpanded) {
            isFabExpanded = false
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val lastDeletedItem by viewModel.lastDeletedItemForUndo.collectAsState()
    var undoDismissJob by remember { mutableStateOf<Job?>(null) }
    var toastOffsetX by remember { mutableFloatStateOf(0f) }
    var itemToDelete by remember { mutableStateOf<SavedItem?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showReleaseNotesSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showHowToCaptureSheet by remember { mutableStateOf(false) }
    val homeSortOrder by viewModel.homeSortOrder.collectAsState()

    LaunchedEffect(lastDeletedItem) {
        undoDismissJob?.cancel()
        toastOffsetX = 0f
        if (lastDeletedItem != null) {
            undoDismissJob = launch {
                kotlinx.coroutines.delay(4000)
                viewModel.clearUndoToast()
            }
        }
    }

    LaunchedEffect(allItems.isNotEmpty()) {
        if (allItems.isNotEmpty() && !hasDismissedOnboarding) {
            viewModel.dismissOnboarding()
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    val hazeState = remember { HazeState() }
    val floatingScrollbarEnabled by viewModel.floatingScrollbarEnabled.collectAsState()
    val floatingScrollbarHome by viewModel.floatingScrollbarHome.collectAsState()
    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val blurOpacity by viewModel.blurOpacity.collectAsState()
    val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur

    LaunchedEffect(listState, gridState, isListView) {
        snapshotFlow {
            if (isListView) {
                val layout = listState.layoutInfo
                layout.visibleItemsInfo.firstOrNull()?.index to layout.visibleItemsInfo.lastOrNull()?.index
            } else {
                val layout = gridState.layoutInfo
                layout.visibleItemsInfo.firstOrNull()?.index to layout.visibleItemsInfo.lastOrNull()?.index
            }
        }
        .distinctUntilChanged()
        .collect {
            val ids = if (isListView) {
                val layout = listState.layoutInfo
                val vStart = layout.viewportStartOffset
                val vEnd = layout.viewportEndOffset
                layout.visibleItemsInfo.mapNotNull { info ->
                    if (info.offset >= vStart && (info.offset + info.size) <= vEnd) info.key as? String else null
                }.toSet()
            } else {
                val layout = gridState.layoutInfo
                val vStart = layout.viewportStartOffset
                val vEnd = layout.viewportEndOffset
                layout.visibleItemsInfo.mapNotNull { info ->
                    if (info.offset.y >= vStart && (info.offset.y + info.size.height) <= vEnd) info.key as? String else null
                }.toSet()
            }
            TransitionCoordinator.homeVisibleItemIds = ids
        }
    }

    LaunchedEffect(listState, gridState, isListView, items) {
        TransitionCoordinator.requestScrollHalfVisibleHomeItem = {
            if (isListView) {
                val layout = listState.layoutInfo
                val vStart = layout.viewportStartOffset
                val vEnd = layout.viewportEndOffset
                val partiallyClippedMedia = layout.visibleItemsInfo.firstOrNull { info ->
                    val item = items.getOrNull(info.index)
                    if (item?.type == SavedItemType.MEDIA) {
                        val top = info.offset
                        val bottom = info.offset + info.size
                        top < vStart && bottom > vStart
                    } else false
                }
                if (partiallyClippedMedia != null) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(partiallyClippedMedia.index, scrollOffset = 0)
                    }
                }
            }
        }
    }

    val showUndoToastWithItem = { item: SavedItem ->
        viewModel.triggerUndoToast(item)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedItemIds.size} selected",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("clear_selection_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_close),
                                contentDescription = "Clear selection",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    actions = {
                        val isAllSelected = items.isNotEmpty() && items.all { it.id in selectedItemIds }
                        IconButton(
                            onClick = {
                                if (isAllSelected) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAll(items.map { it.id })
                                }
                            },
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isAllSelected) R.drawable.ic_custom_select_all_bold else R.drawable.ic_custom_select_all
                                ),
                                contentDescription = if (isAllSelected) "Deselect all" else "Select all",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showBulkTagDialog = true },
                            modifier = Modifier.testTag("bulk_tag_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_folder),
                                contentDescription = "Bulk tag folders",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showBulkDeleteConfirm = true },
                            modifier = Modifier.testTag("bulk_delete_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_delete),
                                contentDescription = "Bulk delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Cobalt",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                com.example.ui.components.AppVersionBadge(
                                    onClick = { showReleaseNotesSheet = true },
                                    fontSize = 12.sp,
                                    horizontalPadding = 0.dp,
                                    verticalPadding = 0.dp,
                                    isFrameless = true,
                                    modifier = Modifier.padding(top = 2.dp, start = 2.dp).testTag("topbar_version_badge")
                                )
                            }
                            Text(
                                text = if (selectedFolder != "All") "${items.size} in $selectedFolder" else "${items.size} items captured",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                    actions = {
                        val hasActiveFilters = selectedFolder != "All" || homeSortOrder != "Newest"
                        IconButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier.testTag("home_filter_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_filter),
                                    contentDescription = "Filter and Sort",
                                    tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                                if (hasActiveFilters) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { viewModel.settingsRepository.setIsListView(!isListView) },
                            modifier = Modifier.testTag("toggle_view_button")
                        ) {
                            Icon(
                                painter = painterResource(id = if (isListView) R.drawable.ic_custom_grid else R.drawable.ic_custom_list),
                                contentDescription = if (isListView) "Switch to Grid View" else "Switch to List View",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { onNavigateToSearch() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_search),
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Search your archive...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                }
            }

            // Recent Captures Redesign (Compact, space-saving, and collapsible)
            if (items.isNotEmpty() && selectedFolder == "All" && searchQuery.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setRecentCapturesExpanded(!isRecentCapturesExpanded) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Captures",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Icon(
                        painter = painterResource(id = if (isRecentCapturesExpanded) R.drawable.ic_custom_chevron_up else R.drawable.ic_custom_chevron_down),
                        contentDescription = if (isRecentCapturesExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isRecentCapturesExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val recentItems = remember(items) { items.sortedByDescending { it.timestamp }.take(5) }
                    Column {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recentItems, key = { it.id + "_recent" }) { item ->
                                RecentCaptureMicroCard(
                                    item = item,
                                    onClick = { handleItemClick(item) }
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Items Grid list or Empty States
            when (emptyState) {
                HomeEmptyState.LOADING -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }
                HomeEmptyState.SEARCH_EMPTY -> {
                    // State D: User searches and no items match
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                                .padding(bottom = 48.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No matching items",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try a different search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            TextButton(
                                onClick = { viewModel.setSearchQuery("") }
                            ) {
                                Text(
                                    text = "Clear search",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                HomeEmptyState.FILTER_EMPTY -> {
                    // State C: User has saved items, but current category/filter has no results
                    val filterCategory = remember(selectedFolder) {
                        when (selectedFolder) {
                            "Videos" -> Triple("No videos found", "There are no saved videos matching this view.", R.drawable.ic_custom_video)
                            "Images" -> Triple("No images found", "There are no saved images matching this view.", R.drawable.ic_custom_image)
                            "Links" -> Triple("No links found", "There are no saved links matching this view.", R.drawable.ic_custom_link)
                            "Notes" -> Triple("No notes found", "There are no saved notes matching this view.", R.drawable.ic_custom_text)
                            "Audio" -> Triple("No audio found", "There are no saved audio clips matching this view.", R.drawable.ic_custom_voice)
                            "Code" -> Triple("No code found", "There are no saved code snippets matching this view.", R.drawable.ic_custom_code)
                            "Archive" -> Triple("Archive is empty", "There are no archived items matching this view.", R.drawable.ic_custom_archive)
                            else -> Triple("No items in $selectedFolder", "There are no saved items matching this view.", R.drawable.ic_custom_folder)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                                .padding(bottom = 48.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = filterCategory.third),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = filterCategory.first,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = filterCategory.second,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            FilledTonalButton(
                                onClick = { viewModel.resetHomeFilters() },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    text = "Clear filter",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                HomeEmptyState.RETURNING_EMPTY -> {
                    // State B: Returning user with an entirely empty archive
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 48.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_cobalt_logo),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Nothing saved yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Your saved items will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { viewModel.openQuickNote() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_plus),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Add something",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                HomeEmptyState.FIRST_TIME_WELCOME -> {
                    // State A: First-time user with an entirely empty archive
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = BiasAlignment(0f, -0.20f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CobaltWelcomeIllustration()

                            Spacer(modifier = Modifier.height(22.dp))

                            Text(
                                text = "Welcome to Cobalt",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Save links, notes, images, code, and anything worth keeping.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            // Primary action button: "+ Add your first item"
                            Button(
                                onClick = {
                                    viewModel.openQuickNote()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_plus),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Add your first item",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Secondary action button: Share icon + "How to capture"
                            FilledTonalButton(
                                onClick = { showHowToCaptureSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_share),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "How to capture",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                HomeEmptyState.CONTENT -> {
                    AnimatedContent(
                    targetState = isListView,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.92f, animationSpec = tween(350, easing = FastOutSlowInEasing)))
                                .togetherWith(fadeOut(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                        scaleOut(targetScale = 1.05f, animationSpec = tween(280, easing = FastOutSlowInEasing)))
                        },
                        label = "content_view_toggle",
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) { targetIsList ->
                        if (targetIsList) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .hazeSource(state = hazeState),
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(items, key = { it.id }) { item ->
                                    SwipeToDismissWrapper(
                                        item = item,
                                        enable = !isSelectionMode,
                                        isScrolling = listState.isScrollInProgress,
                                        isDark = isDark,
                                        onDismissToDelete = {
                                            itemToDelete = item
                                        },
                                        onDismissToArchive = {
                                            if (item.isArchived) {
                                                viewModel.unarchiveItem(item)
                                            } else {
                                                viewModel.archiveItem(item)
                                            }
                                        },
                                        modifier = Modifier.animateItem(),
                                        content = {
                                            ArchiveItemRow(
                                                item = item,
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        viewModel.toggleSelection(item.id)
                                                    } else {
                                                        handleItemClick(item)
                                                    }
                                                },
                                                onDelete = {
                                                    itemToDelete = item
                                                },
                                                onManageFolders = { itemToManageFolders = item },
                                                onArchive = {
                                                    if (item.isArchived) {
                                                        viewModel.unarchiveItem(item)
                                                    } else {
                                                        viewModel.archiveItem(item)
                                                    }
                                                },
                                                isSelected = selectedItemIds.contains(item.id),
                                                isSelectionMode = isSelectionMode,
                                                folderColors = folderColorsMap,
                                                onLongClick = {
                                                    if (isSelectionMode) {
                                                        viewModel.toggleSelection(item.id)
                                                    } else {
                                                        viewModel.enterSelectionMode(item.id)
                                                    }
                                                },
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope
                                            )
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(2),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .hazeSource(state = hazeState),
                                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalItemSpacing = 12.dp
                            ) {
                                items(items, key = { it.id }) { item ->
                                    ArchiveItemCard(
                                        item = item,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(item.id)
                                            } else {
                                                handleItemClick(item)
                                            }
                                        },
                                        onDelete = {
                                            itemToDelete = item
                                        },
                                        onManageFolders = { itemToManageFolders = item },
                                        onArchive = {
                                            if (item.isArchived) {
                                                viewModel.unarchiveItem(item)
                                            } else {
                                                viewModel.archiveItem(item)
                                            }
                                        },
                                        isSelected = selectedItemIds.contains(item.id),
                                        isSelectionMode = isSelectionMode,
                                        folderColors = folderColorsMap,
                                        onLongClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(item.id)
                                            } else {
                                                viewModel.enterSelectionMode(item.id)
                                            }
                                        },
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val homeScrollAdapter = if (isListView) {
            rememberLazyListScrollAdapter(listState)
        } else {
            rememberLazyStaggeredGridScrollAdapter(gridState)
        }

        FloatingScrollPill(
            adapter = homeScrollAdapter,
            localHazeState = hazeState,
            enabled = floatingScrollbarEnabled && floatingScrollbarHome,
            useBlur = useBlur,
            blurRadius = blurRadius,
            blurOpacity = blurOpacity,
            topPadding = 72.dp,
            bottomPadding = 96.dp
        )

        // Sleek interactive Undo Toast UI overlay (half height, auto-dismiss, swipe-to-dismiss)
        AnimatedVisibility(
            visible = lastDeletedItem != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp, start = 20.dp, end = 20.dp)
                .offset { androidx.compose.ui.unit.IntOffset(toastOffsetX.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(toastOffsetX) > 120f) {
                                undoDismissJob?.cancel()
                                viewModel.clearUndoToast()
                            }
                            toastOffsetX = 0f
                        },
                        onDragCancel = {
                            toastOffsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            toastOffsetX += dragAmount
                        }
                    )
                }
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("undo_toast")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_delete),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Item deleted",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        onClick = {
                            undoDismissJob?.cancel()
                            lastDeletedItem?.let { item ->
                                viewModel.restoreDeletedItem(item)
                                android.widget.Toast.makeText(context, "Item restored!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .bounceClick()
                            .testTag("undo_button")
                    ) {
                        Text(
                            text = "UNDO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
}

    // Add Custom Folder Dialog
    com.example.ui.components.FolderBottomSheet(
        isVisible = showAddFolderDialog,
        onDismiss = {
            showAddFolderDialog = false
            newFolderName = ""
        },
        initialFolder = null,
        onSave = { updatedFolder, _ ->
            val finalName = updatedFolder.name.trim()
            if (finalName.isNotEmpty()) {
                viewModel.createFolder(
                    name = finalName,
                    colorHex = updatedFolder.colorHex,
                    iconName = updatedFolder.iconName,
                    isPinned = updatedFolder.isPinned
                )
                showAddFolderDialog = false
                newFolderName = ""
            }
        },
        useBlur = false,
        hazeState = null
    )

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        com.example.ui.components.ExpressiveConfirmationDialog(
            onDismissRequest = { itemToDelete = null },
            title = "Delete Item",
            message = "Are you sure you want to delete this item? This action cannot be undone.",
            confirmButtonText = "Delete",
            onConfirm = {
                itemToDelete?.let {
                    viewModel.deleteSavedItem(it)
                    showUndoToastWithItem(it)
                }
                itemToDelete = null
            },
            dismissButtonText = "Cancel",
            isDestructive = true
        )
    }

    if (showBulkDeleteConfirm) {
        com.example.ui.components.ExpressiveConfirmationDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = "Delete Selected Items",
            message = "Are you sure you want to delete these selected items? This action cannot be undone.",
            confirmButtonText = "Delete",
            onConfirm = {
                viewModel.deleteSelectedItems()
                showBulkDeleteConfirm = false
            },
            dismissButtonText = "Cancel",
            isDestructive = true
        )
    }

    // Manage Item Folders Dialog
    if (itemToManageFolders != null) {
        val item = itemToManageFolders!!
        val liveItem = items.find { it.id == item.id } ?: item

        com.example.ui.components.MoveToFolderDialog(
            title = "Move / Assign Folders",
            subtitle = "Assign folders to \"${liveItem.displayTitle(context)}\"",
            customFolders = customFolders,
            customFolderEntities = customFolderEntities,
            allItems = items,
            currentItem = liveItem,
            initialSelectedFolders = liveItem.folders.toSet(),
            isMultiSelect = true,
            rankingEnabled = isFolderRankingEnabled,
            onDismiss = { itemToManageFolders = null },
            onCreateFolder = { name, iconName ->
                viewModel.createFolder(name = name, iconName = iconName)
            },
            onConfirm = { selectedSet ->
                val added = selectedSet - liveItem.folders.toSet()
                val removed = liveItem.folders.toSet() - selectedSet
                for (folder in added) {
                    viewModel.toggleFolderAssignment(liveItem, folder)
                }
                for (folder in removed) {
                    viewModel.toggleFolderAssignment(liveItem, folder)
                }
                itemToManageFolders = null
            }
        )
    }

    // Bulk Tag / Category Assignment Dialog
    if (showBulkTagDialog) {
        com.example.ui.components.MoveToFolderDialog(
            title = "Move to Folder",
            subtitle = "Assign ${selectedItemIds.size} selected items to a folder",
            customFolders = customFolders,
            customFolderEntities = customFolderEntities,
            allItems = items,
            initialSelectedFolders = emptySet(),
            isMultiSelect = false,
            rankingEnabled = isFolderRankingEnabled,
            onDismiss = { showBulkTagDialog = false },
            onCreateFolder = { name, iconName ->
                viewModel.createFolder(name = name, iconName = iconName)
            },
            onConfirm = { targetFolders ->
                targetFolders.firstOrNull()?.let { folder ->
                    viewModel.tagSelectedItems(folder)
                }
            }
        )
    }

    if (showReleaseNotesSheet) {
        com.example.ui.components.ReleaseNotesBottomSheet(
            onDismissRequest = { showReleaseNotesSheet = false }
        )
    }

    if (showFilterSheet) {
        HomeFilterBottomSheet(
            viewModel = viewModel,
            itemCount = items.size,
            onDismissRequest = { showFilterSheet = false }
        )
    }

    if (showHowToCaptureSheet) {
        HowToCaptureBottomSheet(
            onDismissRequest = { showHowToCaptureSheet = false },
            onGotIt = {
                viewModel.dismissOnboarding()
                showHowToCaptureSheet = false
            },
            onCreateNote = {
                viewModel.dismissOnboarding()
                showHowToCaptureSheet = false
                viewModel.openQuickNote()
            }
        )
    }
}
        // The FAB overlay has been moved to MainActivity via GlobalExpandingFab

@Composable
fun SwipeToDismissWrapper(
    item: SavedItem,
    onDismissToDelete: () -> Unit,
    onDismissToArchive: () -> Unit,
    modifier: Modifier = Modifier,
    enable: Boolean = true,
    isScrolling: Boolean = false,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isArchived = remember(item.folders) { item.isArchived }
    val offsetX = remember { Animatable(0f) }
    var cardWidth by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val enableState = rememberUpdatedState(enable)

    // Calculate how far swipe has progressed (-1..1 range)
    val swipeProgress = if (cardWidth > 0f) (offsetX.value / cardWidth).coerceIn(-1f, 1f) else 0f
    val absProgress = kotlin.math.abs(swipeProgress)
    val isPastThreshold = absProgress >= 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                cardWidth = placeable.width.toFloat()
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
    ) {
        // Background layer — only visible while dragging
        if (absProgress > 0.02f) {
            val isArchiveDirection = offsetX.value > 0
            val bgColor by animateColorAsState(
                targetValue = if (isArchiveDirection) {
                    if (isPastThreshold) {
                        if (isDark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
                    } else {
                        if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                    }
                } else {
                    if (isPastThreshold) {
                        if (isDark) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.error
                    } else {
                        if (isDark) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer
                    }
                },
                animationSpec = tween(200),
                label = "swipe_bg"
            )
            val iconTint by animateColorAsState(
                targetValue = if (isArchiveDirection) {
                    if (isPastThreshold) {
                        if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                    } else {
                        if (isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    }
                } else {
                    if (isPastThreshold) {
                        if (isDark) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onError
                    } else {
                        if (isDark) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer
                    }
                },
                animationSpec = tween(200),
                label = "swipe_tint"
            )
            val iconResId = if (isArchiveDirection) {
                if (isArchived) R.drawable.ic_custom_unarchive else R.drawable.ic_custom_archive
            } else {
                R.drawable.ic_custom_delete
            }
            val text = if (isArchiveDirection) {
                if (isArchived) "Restore" else "Archive"
            } else {
                "Delete"
            }
            val alignment = if (isArchiveDirection) Alignment.CenterStart else Alignment.CenterEnd

            // Scale icon based on progress for satisfying feedback
            val iconScale by animateFloatAsState(
                targetValue = if (isPastThreshold) 1.2f else 0.8f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "icon_scale"
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                ) {
                    if (isArchiveDirection) {
                        Icon(
                            painter = painterResource(id = iconResId),
                            contentDescription = text,
                            tint = iconTint
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = iconTint
                        )
                    } else {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = iconTint
                        )
                        Icon(
                            painter = painterResource(id = iconResId),
                            contentDescription = text,
                            tint = iconTint
                        )
                    }
                }
            }
        }

        // Foreground content with horizontal drag
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .pointerInput(Unit) {
                    if (!enableState.value) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { /* nothing */ },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                )
                            }
                        },
                        onDragEnd = {
                            val progress = if (cardWidth > 0f) kotlin.math.abs(offsetX.value / cardWidth) else 0f
                            if (progress >= 0.5f) {
                                // Only trigger action if dragged past 50%
                                if (offsetX.value > 0) {
                                    onDismissToArchive()
                                } else {
                                    onDismissToDelete()
                                }
                            }
                            // Always snap back
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!enableState.value) return@detectHorizontalDragGestures
                            change.consume()
                            scope.launch {
                                val newValue = (offsetX.value + dragAmount)
                                    .coerceIn(-cardWidth, cardWidth)
                                offsetX.snapTo(newValue)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
fun OnboardingSharingGuide(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_cobalt_logo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your Cobalt is Ready!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Share from any app or add notes manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("dismiss_onboarding_button")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Dismiss guide",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "Capture links, photos, code, and notes in seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            SharingGuideVisualMockup()

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Text(
                text = "HOW TO GET STARTED",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            OnboardingStepRow(
                stepNumber = "1",
                title = "Share from any app",
                description = "Open Chrome, YouTube, Photos, X, or any app and tap Share."
            )

            OnboardingStepRow(
                stepNumber = "2",
                title = "Select Cobalt",
                description = "Pick Cobalt from the share sheet to save it instantly."
            )

            OnboardingStepRow(
                stepNumber = "3",
                title = "Or add it manually",
                description = "Use the + button for a quick note, link, or code snippet."
            )
        }
    }
}

@Composable
fun SharingGuideVisualMockup() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.size(width = 76.dp, height = 72.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_grid),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Any App",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_custom_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .size(16.dp)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.size(width = 82.dp, height = 72.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cobalt_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Cobalt",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun OnboardingStepRow(
    stepNumber: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FolderChipItem(
    folder: String,
    isSelected: Boolean,
    painter: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit,
    accentColor: Color? = null
) {
    val chipAccentColor = accentColor ?: MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) {
            chipAccentColor
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
                chipAccentColor
            } else if (accentColor != null) {
                accentColor.copy(alpha = if (isDark) 0.55f else 0.40f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier
            .bounceClick()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("folder_chip_$folder")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    chipAccentColor
                }
            )
            Text(
                text = folder,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}


fun getSearchHistory(prefs: android.content.SharedPreferences): List<String> {
    val historyStr = prefs.getString("search_history", "") ?: ""
    return if (historyStr.isBlank()) emptyList() else historyStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun saveSearchHistory(prefs: android.content.SharedPreferences, history: List<String>) {
    prefs.edit().putString("search_history", history.joinToString(",")).apply()
}

fun addQueryToHistory(prefs: android.content.SharedPreferences, query: String) {
    if (query.isBlank()) return
    val trimmed = query.trim()
    val current = getSearchHistory(prefs).toMutableList()
    current.remove(trimmed)
    current.add(0, trimmed)
    val limited = current.take(10) // keep last 10
    saveSearchHistory(prefs, limited)
}

private fun removeQueryFromHistory(prefs: android.content.SharedPreferences, query: String) {
    val current = getSearchHistory(prefs).toMutableList()
    current.remove(query.trim())
    saveSearchHistory(prefs, current)
}

fun clearSearchHistory(prefs: android.content.SharedPreferences) {
    prefs.edit().remove("search_history").apply()
}

@Composable
fun RecentCaptureMicroCard(
    item: SavedItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .width(135.dp)
            .height(50.dp)
            .bounceClick(interactionSource)
    ) {
        Row(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon / Thumbnail Box
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                when (item.type) {
                    SavedItemType.IMAGE, SavedItemType.VIDEO -> {
                        if (item.isUnavailable) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            AsyncImage(
                                model = item.getBestImagePath(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    SavedItemType.LINK -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_link),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    SavedItemType.CODE -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_code),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    SavedItemType.AUDIO -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_voice),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    else -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_text),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Title
            Text(
                text = item.displayTitle(context),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

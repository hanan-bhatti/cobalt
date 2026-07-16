package com.example.ui.screens

import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import com.example.ui.components.bounceClick
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
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.getBestImagePath
import com.example.ui.viewmodel.SecondBrainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    viewModel: SecondBrainViewModel,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val items by viewModel.filteredItems.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItemIds by viewModel.selectedItemIds.collectAsState()
    val hasDismissedOnboarding by viewModel.hasDismissedOnboarding.collectAsState()

    var isDragging by remember { mutableStateOf(false) }
    var mutableItems by remember { mutableStateOf(items) }
    LaunchedEffect(items) {
        if (!isDragging) mutableItems = items
    }

    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val isListView by viewModel.settingsRepository.isListView.collectAsState()
    var itemToManageFolders by remember { mutableStateOf<SavedItem?>(null) }
    var showManualCaptureOverlay by remember { mutableStateOf(false) }
    var captureType by remember { mutableStateOf(SavedItemType.TEXT) }
    var captureTitle by remember { mutableStateOf("") }
    var captureContent by remember { mutableStateOf("") }
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
    var showUndoToast by remember { mutableStateOf(false) }
    var deletedItemForUndo by remember { mutableStateOf<SavedItem?>(null) }
    var undoDismissJob by remember { mutableStateOf<Job?>(null) }
    var itemToDelete by remember { mutableStateOf<SavedItem?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    val snackbarHostState = remember { SnackbarHostState() }

    val showUndoToastWithItem = { item: SavedItem ->
        deletedItemForUndo = item
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar("Item deleted", "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                deletedItemForUndo?.let { viewModel.restoreDeletedItem(it) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        IconButton(
                            onClick = { viewModel.selectAll(items.map { it.id }) },
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_select_all),
                                contentDescription = "Select all",
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
                            Text(
                                text = "Second Brain",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${items.size} items captured",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                    actions = {
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
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
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
            
            // Horizontal Filter Chips Row
            val filteredCustomFolders = remember(customFolders) { customFolders.filter { it != "Archive" } }
            val systemCategories = remember { SavedItemType.entries.map { it.displayName } }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .testTag("folder_chips_row"),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. All Folder Chip
                item {
                    val isSelected = selectedFolder == "All"
                    FolderChipItem(
                        folder = "All",
                        isSelected = isSelected,
                        painter = painterResource(id = R.drawable.ic_custom_folder),
                        onClick = { viewModel.setFolderFilter("All") }
                    )
                }

                // 2. Custom Folders
                items(filteredCustomFolders, key = { it }) { folder ->
                    val isSelected = selectedFolder == folder
                    FolderChipItem(
                        folder = folder,
                        isSelected = isSelected,
                        painter = painterResource(id = R.drawable.ic_custom_star),
                        onClick = { viewModel.setFolderFilter(folder) }
                    )
                }

                // 5. System Category Folders
                items(systemCategories, key = { it }) { category ->
                    val isSelected = selectedFolder == category
                    val painter = when (category) {
                        "Links" -> painterResource(id = R.drawable.ic_custom_link)
                        "Images" -> painterResource(id = R.drawable.ic_custom_image)
                        "Videos" -> painterResource(id = R.drawable.ic_custom_video)
                        "Code" -> painterResource(id = R.drawable.ic_custom_code)
                        "Text" -> painterResource(id = R.drawable.ic_custom_text)
                        else -> painterResource(id = R.drawable.ic_custom_folder)
                    }
                    FolderChipItem(
                        folder = category,
                        isSelected = isSelected,
                        painter = painter,
                        onClick = { viewModel.setFolderFilter(category) }
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
                                    onClick = { viewModel.showDetailItem(item) }
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

            // Items Grid list
            if (items.isEmpty()) {
                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No results found",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try searching something else",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!hasDismissedOnboarding) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                            ) {
                                OnboardingSharingGuide(onDismiss = { viewModel.dismissOnboarding() })
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_folder_special),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Your Second Brain is Empty",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Share links, photos, code snippets from other apps,\nor tap the '+' button below to add your first memory.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                if (isInitialLoading) {
                    AnimatedContent(
                        targetState = isListView,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "skeleton_view_toggle",
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) { targetIsList ->
                        if (targetIsList) {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(6) {
                                    SkeletonItemCard(isListView = true)
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(6) {
                                    SkeletonItemCard(isListView = false)
                                }
                            }
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = isListView,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "content_view_toggle",
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) { targetIsList ->
                        if (targetIsList) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .dragToReorder(
                                        lazyListState = listState,
                                        onMove = { from, to ->
                                            isDragging = true
                                            mutableItems = mutableItems.toMutableList().apply {
                                                add(to, removeAt(from))
                                            }
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            viewModel.updateOrderIndices(mutableItems)
                                        }
                                    ),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(mutableItems, key = { _, item -> item.id }) { index, item ->
                            val animDelay = (index % 10) * 50
                            val entryAlpha = remember { Animatable(0f) }
                            val entryOffsetY = remember { Animatable(40f) }
                            LaunchedEffect(item.id) {
                                delay(animDelay.toLong())
                                launch {
                                    entryAlpha.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)
                                    )
                                }
                                launch {
                                    entryOffsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)
                                    )
                                }
                            }

                            SwipeToDismissWrapper(
                                item = item,
                                enable = !isSelectionMode,
                                isScrolling = listState.isScrollInProgress,
                                onDismissToDelete = {
                                    itemToDelete = item
                                },
                                onDismissToArchive = {
                                    if (item.folders.contains("Archive")) {
                                        viewModel.unarchiveItem(item)
                                    } else {
                                        viewModel.archiveItem(item)
                                    }
                                },
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = entryAlpha.value
                                        translationY = entryOffsetY.value
                                    }
                                    .animateItem(),
                                content = {
                                    ArchiveItemRow(
                                        item = item,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(item.id)
                                            } else {
                                                viewModel.showDetailItem(item)
                                            }
                                        },
                                        onDelete = { 
                                            itemToDelete = item
                                        },
                                        onManageFolders = { itemToManageFolders = item },
                                        onArchive = {
                                            if (item.folders.contains("Archive")) {
                                                viewModel.unarchiveItem(item)
                                            } else {
                                                viewModel.archiveItem(item)
                                            }
                                        },
                                        isSelected = selectedItemIds.contains(item.id),
                                        isSelectionMode = isSelectionMode,
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .dragToReorderGrid(
                                lazyGridState = gridState,
                                onMove = { from, to ->
                                    isDragging = true
                                    mutableItems = mutableItems.toMutableList().apply {
                                        add(to, removeAt(from))
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    viewModel.updateOrderIndices(mutableItems)
                                }
                            ),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(mutableItems, key = { _, item -> item.id }) { index, item ->
                            val animDelay = (index % 10) * 50
                            val entryAlpha = remember { Animatable(0f) }
                            val entryOffsetY = remember { Animatable(40f) }
                            LaunchedEffect(item.id) {
                                delay(animDelay.toLong())
                                launch {
                                    entryAlpha.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)
                                    )
                                }
                                launch {
                                    entryOffsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)
                                    )
                                }
                            }

                            ArchiveItemCard(
                                item = item,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        viewModel.showDetailItem(item)
                                    }
                                },
                                onDelete = { 
                                    itemToDelete = item
                                },
                                onManageFolders = { itemToManageFolders = item },
                                onArchive = {
                                    if (item.folders.contains("Archive")) {
                                        viewModel.unarchiveItem(item)
                                    } else {
                                        viewModel.archiveItem(item)
                                    }
                                },
                                isSelected = selectedItemIds.contains(item.id),
                                isSelectionMode = isSelectionMode,
                                onLongClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        viewModel.enterSelectionMode(item.id)
                                    }
                                },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = entryAlpha.value
                                        translationY = entryOffsetY.value
                                    }
                                    .animateItem()
                            )
                        }
                    }
                }
            }
        }
        }
        }

        // Beautiful interactive Undo Toast UI overlay
        AnimatedVisibility(
            visible = showUndoToast && deletedItemForUndo != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("undo_toast")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_delete),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Item deleted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    TextButton(
                        onClick = {
                            deletedItemForUndo?.let { item ->
                                viewModel.restoreDeletedItem(item)
                                android.widget.Toast.makeText(context, "Item restored!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            showUndoToast = false
                            deletedItemForUndo = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.inversePrimary
                        ),
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Text("UNDO", fontWeight = FontWeight.Bold)
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
            title = { Text("New Custom Folder", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Work, Inspiration, Books...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("folder_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createFolder(newFolderName)
                        showAddFolderDialog = false
                        newFolderName = ""
                    },
                    modifier = Modifier.testTag("confirm_folder_button")
                ) {
                    Text("Create", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddFolderDialog = false
                    newFolderName = ""
                }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete this item? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let {
                            viewModel.deleteSavedItem(it)
                            showUndoToastWithItem(it)
                        }
                        itemToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete Selected Items") },
            text = { Text("Are you sure you want to delete these selected items? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedItems()
                        showBulkDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Manage Item Folders Dialog
    if (itemToManageFolders != null) {
        val item = itemToManageFolders!!
        val liveItem = items.find { it.id == item.id } ?: item
        
        AlertDialog(
            onDismissRequest = { itemToManageFolders = null },
            title = {
                Column {
                    Text(
                        text = "Manage Folders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Assign \"${liveItem.title.ifBlank { "Untitled Note" }}\" to folders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                var newFolderInDialog by remember { mutableStateOf("") }
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    
                    Text(
                        text = "Custom Folders",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (customFolders.isEmpty()) {
                        Text(
                            text = "No custom folders yet. Create one below!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(customFolders) { folder ->
                                val isAssigned = liveItem.folders.contains(folder)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.toggleFolderAssignment(liveItem, folder)
                                        }
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                        .testTag("manage_folder_row_$folder"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_folder),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = folder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Checkbox(
                                        checked = isAssigned,
                                        onCheckedChange = {
                                            viewModel.toggleFolderAssignment(liveItem, folder)
                                        },
                                        modifier = Modifier.testTag("checkbox_${folder}")
                                    )
                                }
                            }
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    
                    Text(
                        text = "Create and Assign Folder",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newFolderInDialog,
                            onValueChange = { newFolderInDialog = it },
                            placeholder = { Text("e.g. Work, Ideas") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_new_folder_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Button(
                            onClick = {
                                if (newFolderInDialog.isNotBlank()) {
                                    viewModel.createAndAssignFolder(liveItem, newFolderInDialog)
                                    newFolderInDialog = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.testTag("dialog_create_folder_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_plus),
                                contentDescription = "Create and assign folder",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { itemToManageFolders = null },
                    modifier = Modifier.testTag("close_manage_folders_button")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Bulk Tag / Category Assignment Dialog
    if (showBulkTagDialog) {
        AlertDialog(
            onDismissRequest = { showBulkTagDialog = false },
            title = {
                Column {
                    Text(
                        text = "Bulk Tag Folders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Assign ${selectedItemIds.size} selected items to a folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            text = {
                var newFolderInDialog by remember { mutableStateOf("") }
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    
                    Text(
                        text = "Select Custom Folder",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (customFolders.isEmpty()) {
                        Text(
                            text = "No custom folders yet. Create one below!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(customFolders) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.tagSelectedItems(folder)
                                            showBulkTagDialog = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp)
                                        .testTag("bulk_tag_row_$folder"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_folder),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = folder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    
                    Text(
                        text = "Create and Assign Folder",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newFolderInDialog,
                            onValueChange = { newFolderInDialog = it },
                            placeholder = { Text("e.g. Work, Ideas") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bulk_tag_new_folder_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Button(
                            onClick = {
                                if (newFolderInDialog.isNotBlank()) {
                                    viewModel.tagSelectedItems(newFolderInDialog)
                                    showBulkTagDialog = false
                                    newFolderInDialog = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.testTag("bulk_tag_create_folder_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_plus),
                                contentDescription = "Create and assign folder",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showBulkTagDialog = false },
                    modifier = Modifier.testTag("close_bulk_tag_button")
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showManualCaptureOverlay) {
        AlertDialog(
            onDismissRequest = {
                showManualCaptureOverlay = false
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_folder_special),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Manual Capture",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Choose the type of information you want to add to your archive:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(SavedItemType.TEXT, SavedItemType.LINK).forEach { type ->
                            val isSelected = captureType == type
                            val typeLabel = if (type == SavedItemType.TEXT) "Text Note" else "Web Link"
                            val typeIconRes = if (type == SavedItemType.TEXT) R.drawable.ic_custom_text else R.drawable.ic_custom_link

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { captureType = type }
                                    .testTag("capture_type_chip_${type.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = typeIconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = captureTitle,
                        onValueChange = { captureTitle = it },
                        label = { Text("Title (Optional)") },
                        placeholder = { Text("e.g. Shopping List, Interesting Article") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_capture_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    if (captureType == SavedItemType.TEXT) {
                        RichTextEditor(
                            value = captureContent,
                            onValueChange = { captureContent = it },
                            placeholder = { Text("Capture your thoughts or paste note details...") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_capture_content_input")
                        )
                    } else {
                        OutlinedTextField(
                            value = captureContent,
                            onValueChange = { captureContent = it },
                            label = { Text("URL / Link") },
                            placeholder = { Text("https://example.com/some-resource") },
                            minLines = 1,
                            maxLines = 6,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_capture_content_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (captureContent.isNotBlank()) {
                            viewModel.saveLocalTextItem(
                                title = captureTitle,
                                content = captureContent,
                                type = captureType,
                                selectedFolders = emptyList()
                            )
                            showManualCaptureOverlay = false
                            captureTitle = ""
                            captureContent = ""
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = captureContent.isNotBlank() && !isSaving,
                    modifier = Modifier.testTag("manual_capture_save_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save to Brain", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualCaptureOverlay = false },
                    modifier = Modifier.testTag("manual_capture_cancel_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

}

        // The FAB overlay has been moved to MainActivity via GlobalExpandingFab

}





@Composable
fun SwipeToDismissWrapper(
    item: SavedItem,
    onDismissToDelete: () -> Unit,
    onDismissToArchive: () -> Unit,
    modifier: Modifier = Modifier,
    enable: Boolean = true,
    isScrolling: Boolean = false,
    content: @Composable () -> Unit
) {
    val isArchived = remember(item.folders) { item.folders.contains("Archive") }
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
                    if (isPastThreshold) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer
                } else {
                    if (isPastThreshold) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.errorContainer
                },
                animationSpec = tween(200),
                label = "swipe_bg"
            )
            val iconTint = if (isArchiveDirection) {
                if (isPastThreshold) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                if (isPastThreshold) MaterialTheme.colorScheme.onError
                else MaterialTheme.colorScheme.onErrorContainer
            }
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(24.dp)) // balance close button
                Text(
                    text = "💡 Your Second Brain is Ready!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp).testTag("dismiss_onboarding_button")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Dismiss guide",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "Capture any web link, photo, code block, or note effortlessly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            SharingGuideVisualMockup()

            Text(
                text = "HOW TO GET STARTED:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 12.dp)
            )

            // Step 1
            OnboardingStepRow(
                stepNumber = "1",
                title = "Share from any app",
                description = "Open Chrome, YouTube, X, or Gallery. Tap \"Share\" on any webpage, image, or text selection."
            )
            
            Spacer(modifier = Modifier.height(14.dp))

            // Step 2
            OnboardingStepRow(
                stepNumber = "2",
                title = "Select Second Brain",
                description = "Choose \"Second Brain\" from the system share sheet to instantly save, parse, and auto-categorize it."
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step 3
            OnboardingStepRow(
                stepNumber = "3",
                title = "Or build it manually",
                description = "Tap the '+' Floating Action Button below to manually type a text note, code snippet, or link."
            )
        }
    }
}

@Composable
fun SharingGuideVisualMockup() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source card (e.g. Browser)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.size(width = 80.dp, height = 90.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_globe),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Any App\nor Site",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Animated / Dotted connection
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_custom_arrow_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Destination card (Second Brain)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.size(width = 80.dp, height = 90.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_folder_special),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Second\nBrain",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
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
                    MaterialTheme.colorScheme.primary
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

@Composable
fun PersistentCaptureForm(viewModel: SecondBrainViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("draft_prefs", android.content.Context.MODE_PRIVATE) }
    var noteText by remember { mutableStateOf(prefs.getString("quick_note", "") ?: "") }
    var noteTags by remember { mutableStateOf(prefs.getString("quick_tags", "") ?: "") }
    val focusManager = LocalFocusManager.current
    var isExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(noteText) {
        prefs.edit().putString("quick_note", noteText).apply()
    }
    LaunchedEffect(noteTags) {
        prefs.edit().putString("quick_tags", noteTags).apply()
    }

    val commonTags = listOf("Idea", "Todo", "Important", "Work", "Personal")

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isExpanded) 16.dp else 0.dp,
        shape = if (isExpanded) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isExpanded) 0.dp else 16.dp, vertical = if (isExpanded) 0.dp else 12.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isExpanded) 16.dp else 4.dp)
        ) {
            if (!isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { isExpanded = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (noteText.isEmpty()) "Quick note..." else noteText,
                        color = if (noteText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                    IconButton(
                        onClick = { 
                            if (noteText.isNotBlank()) {
                                val tagsList = noteTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                viewModel.saveLocalTextItem(
                                    title = "Quick Note",
                                    content = noteText,
                                    type = SavedItemType.TEXT,
                                    selectedFolders = tagsList
                                )
                                noteText = ""
                                noteTags = ""
                            } else {
                                isExpanded = true 
                            }
                        }
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_custom_send), contentDescription = "Save Note", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quick Capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = {
                        isExpanded = false
                        focusManager.clearFocus()
                    }) {
                        Icon(painter = painterResource(id = R.drawable.ic_custom_chevron_down), contentDescription = "Collapse")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Quick note...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 160.dp)
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = noteTags,
                    onValueChange = { noteTags = it },
                    placeholder = { Text("Tags (comma separated)...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(commonTags) { tag ->
                        FilterChip(
                            selected = false,
                            onClick = { 
                                val currentTags = noteTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                                if (!currentTags.contains(tag)) {
                                    currentTags.add(tag)
                                    noteTags = currentTags.joinToString(", ")
                                }
                            },
                            label = { Text(tag) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            val tagsList = noteTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            viewModel.saveLocalTextItem(
                                title = "Quick Note",
                                content = noteText,
                                type = SavedItemType.TEXT,
                                selectedFolders = tagsList
                            )
                            noteText = ""
                            noteTags = ""
                            isExpanded = false
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(painter = painterResource(id = R.drawable.ic_custom_send), contentDescription = "Save Note")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Note")
                }
            }
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
fun SkeletonItemCard(isListView: Boolean) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(10f, 10f),
        end = Offset(translateAnim.value, translateAnim.value)
    )

    if (isListView) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }
    }
}

@Composable
fun RecentCaptureMicroCard(
    item: SavedItem,
    onClick: () -> Unit
) {
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
                        AsyncImage(
                            model = item.getBestImagePath(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
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
                text = item.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

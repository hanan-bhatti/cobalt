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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: SecondBrainViewModel,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val items by viewModel.filteredItems.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItemIds by viewModel.selectedItemIds.collectAsState()

    var isDragging by remember { mutableStateOf(false) }
    var mutableItems by remember { mutableStateOf(items) }
    LaunchedEffect(items) {
        if (!isDragging) mutableItems = items
    }

    var showBulkTagDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var isListView by remember { mutableStateOf(true) }
    var itemToManageFolders by remember { mutableStateOf<SavedItem?>(null) }
    var showManualCaptureOverlay by remember { mutableStateOf(false) }
    var captureType by remember { mutableStateOf(SavedItemType.TEXT) }
    var captureTitle by remember { mutableStateOf("") }
    var captureContent by remember { mutableStateOf("") }

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

    BackHandler(enabled = showSearchPageOverlay || isSelectionMode) {
        if (showSearchPageOverlay) {
            showSearchPageOverlay = false
        } else if (isSelectionMode) {
            viewModel.clearSelection()
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
                                imageVector = Icons.Default.Close,
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
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select all",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showBulkTagDialog = true },
                            modifier = Modifier.testTag("bulk_tag_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = "Bulk tag folders",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showBulkDeleteConfirm = true },
                            modifier = Modifier.testTag("bulk_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
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
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "KNOWLEDGE ARCHIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 1.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isListView = !isListView },
                            modifier = Modifier.testTag("toggle_view_button")
                        ) {
                            Icon(
                                imageVector = if (isListView) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                                contentDescription = if (isListView) "Switch to Grid View" else "Switch to List View",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showSearchPageOverlay = true },
                            modifier = Modifier.testTag("open_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Open Search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onNavigateToProfile,
                            modifier = Modifier.testTag("auth_profile_button")
                        ) {
                            if (userEmail != null) {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = "Profile",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = "Profile",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                var isFabExpanded by remember { mutableStateOf(false) }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedVisibility(
                        visible = isFabExpanded,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 })
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ExtendedFloatingActionButton(
                                text = { Text("Archive") },
                                icon = { Icon(Icons.Outlined.Archive, contentDescription = "View Archive") },
                                onClick = { 
                                    viewModel.setFolderFilter("Archive")
                                    isFabExpanded = false
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            ExtendedFloatingActionButton(
                                text = { Text("New Folder") },
                                icon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New Folder") },
                                onClick = { 
                                    showAddFolderDialog = true
                                    isFabExpanded = false
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            ExtendedFloatingActionButton(
                                text = { Text("Voice Memo") },
                                icon = { Icon(Icons.Filled.Mic, contentDescription = "Voice Memo") },
                                onClick = {
                                    isFabExpanded = false
                                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak your voice memo...")
                                    }
                                    try {
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Speech recognizer not available", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            ExtendedFloatingActionButton(
                                text = { Text("New Item") },
                                icon = { Icon(Icons.Filled.Add, contentDescription = "New Item") },
                                onClick = {
                                    captureType = SavedItemType.TEXT
                                    captureTitle = ""
                                    captureContent = ""
                                    showManualCaptureOverlay = true
                                    isFabExpanded = false
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    FloatingActionButton(
                        onClick = { isFabExpanded = !isFabExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("add_item_fab")
                    ) {
                        Icon(
                            imageVector = if (isFabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = "Expand actions",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            PersistentCaptureForm(viewModel)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            
            // Horizontal Filter Chips Row
            val filteredCustomFolders = customFolders.filter { it != "Archive" }
            val systemCategories = SavedItemType.entries.map { it.displayName }

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
                        icon = Icons.Outlined.Folder,
                        onClick = { viewModel.setFolderFilter("All") }
                    )
                }

                // 2. Custom Folders
                items(filteredCustomFolders) { folder ->
                    val isSelected = selectedFolder == folder
                    FolderChipItem(
                        folder = folder,
                        isSelected = isSelected,
                        icon = Icons.Outlined.FolderSpecial,
                        onClick = { viewModel.setFolderFilter(folder) }
                    )
                }

                // 5. System Category Folders
                items(systemCategories) { category ->
                    val isSelected = selectedFolder == category
                    val icon = when (category) {
                        "Links" -> Icons.Outlined.Link
                        "Images" -> Icons.Outlined.Image
                        "Videos" -> Icons.Outlined.VideoLibrary
                        "Code" -> Icons.Outlined.Code
                        "Text" -> Icons.Outlined.Description
                        else -> Icons.Outlined.Folder
                    }
                    FolderChipItem(
                        folder = category,
                        isSelected = isSelected,
                        icon = icon,
                        onClick = { viewModel.setFolderFilter(category) }
                    )
                }
            }

            // Recent Captures
            if (items.isNotEmpty() && selectedFolder == "All" && searchQuery.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recent Captures",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                val recentItems = remember(items) { items.sortedByDescending { it.timestamp }.take(5) }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentItems, key = { it.id + "_recent" }) { item ->
                        Box(modifier = Modifier.width(160.dp).height(120.dp)) {
                            ArchiveItemCard(
                                item = item,
                                onClick = { viewModel.showDetailItem(item) },
                                onDelete = { itemToDelete = item },
                                onManageFolders = { itemToManageFolders = item },
                                onArchive = {
                                    if (item.folders.contains("Archive")) {
                                        viewModel.unarchiveItem(item)
                                    } else {
                                        viewModel.archiveItem(item)
                                    }
                                },
                                isSelected = false,
                                isSelectionMode = false,
                                onLongClick = null
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // Items Grid list
            if (items.isEmpty()) {
                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
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
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            OnboardingSharingGuide()
                        }
                    }
                }
            } else {
                if (isInitialLoading) {
                    if (isListView) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(6) {
                                SkeletonItemCard(isListView = true)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(6) {
                                SkeletonItemCard(isListView = false)
                            }
                        }
                    }
                } else if (isListView) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                            .weight(1f)
                            .fillMaxWidth()
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
                        contentPadding = PaddingValues(16.dp),
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
                            imageVector = Icons.Default.Delete,
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
                                        imageVector = Icons.Outlined.Folder,
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
                                imageVector = Icons.Default.Add,
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
                                        imageVector = Icons.Outlined.Folder,
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
                                imageVector = Icons.Default.Add,
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
                        imageVector = Icons.Outlined.FolderSpecial,
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
                            val typeIcon = if (type == SavedItemType.TEXT) Icons.Outlined.Description else Icons.Outlined.Link

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
                                        imageVector = typeIcon,
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
                            viewModel.startManualCapture(captureType)
                            viewModel.updateActiveCaptureItem { item ->
                                item.copy(
                                    title = captureTitle,
                                    content = captureContent
                                )
                            }
                            viewModel.saveActiveItem()
                            showManualCaptureOverlay = false
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = captureContent.isNotBlank(),
                    modifier = Modifier.testTag("manual_capture_save_button")
                ) {
                    Text("Save to Brain", fontWeight = FontWeight.SemiBold)
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

    AnimatedVisibility(
        visible = showSearchPageOverlay,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            SearchPageOverlay(
                viewModel = viewModel,
                onDismiss = { showSearchPageOverlay = false },
                prefs = prefs,
                isListView = isListView,
                onItemClick = { item ->
                    viewModel.showDetailItem(item)
                    showSearchPageOverlay = false
                },
                onDelete = { item ->
                    itemToDelete = item
                },
                onManageFolders = { item ->
                    itemToManageFolders = item
                },
                onArchive = { item ->
                    if (item.folders.contains("Archive")) {
                        viewModel.unarchiveItem(item)
                    } else {
                        viewModel.archiveItem(item)
                    }
                }
            )
        }
    }

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ArchiveItemCard(
    item: SavedItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onManageFolders: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Spring animation for subtle press scale
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    Box(
        modifier = modifier
            .padding(2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 8.dp else 2.dp,
                pressedElevation = 8.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick ?: { showContextMenu = true }
                )
                .testTag("item_card_${item.id}")
        ) {
            Column {
                // Media Preview / Styled Box Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    when (item.type) {
                        SavedItemType.IMAGE, SavedItemType.VIDEO -> {
                            AsyncImage(
                                model = item.getBestImagePath(),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                            // Subtle shadow overlays on image
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.35f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.25f)
                                            )
                                        )
                                    )
                              )
                            if (item.type == SavedItemType.VIDEO) {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircle,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }
                        SavedItemType.CODE -> {
                            // High fidelity syntax terminal mockup
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0F172A))
                                    .padding(8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFEF4444), CircleShape))
                                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFF59E0B), CircleShape))
                                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "snippet.kt",
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Text(
                                        text = item.content,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF38BDF8),
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 5,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        SavedItemType.LINK -> {
                            if (!item.linkImage.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.linkImage,
                                    contentDescription = item.linkTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Dark shade overlay for links with thumbnails
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.35f),
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.25f)
                                                )
                                            )
                                        )
                                )
                            } else {
                                // Premium abstract geometric background
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            androidx.compose.ui.graphics.Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                        SavedItemType.TEXT, SavedItemType.AUDIO -> {
                            // Premium notepad style with notebook side line
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                            )
                                        )
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.secondary
                                                    )
                                                ),
                                                shape = RoundedCornerShape(1.5.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = parseMarkdown(item.content),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 6,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // FLOATING BADGES (TOP LEFT)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.extractedText != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "OCR",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (item.isSynced) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                            },
                            border = BorderStroke(
                                width = 0.5.dp,
                                color = if (item.isSynced) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isSynced) Icons.Outlined.CloudDone else Icons.Outlined.CloudQueue,
                                    contentDescription = if (item.isSynced) "Synced to Cloud" else "Local Only",
                                    tint = if (item.isSynced) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    },
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                } // End of top Box

                // Title & Info Block
                Column(
                    modifier = Modifier.padding(10.dp)
                ) {
                    // Category & Date row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val icon = when (item.type) {
                                SavedItemType.LINK -> Icons.Outlined.Link
                                SavedItemType.IMAGE -> Icons.Outlined.Image
                                SavedItemType.VIDEO -> Icons.Outlined.VideoLibrary
                                SavedItemType.CODE -> Icons.Outlined.Code
                                SavedItemType.TEXT -> Icons.Outlined.Description
                                SavedItemType.AUDIO -> Icons.Outlined.Mic
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = item.type.displayName,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        val formattedDate = remember(item.timestamp) {
                            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                            sdf.format(Date(item.timestamp))
                        }
                        Text(
                            text = formattedDate,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Title
                    Text(
                        text = item.title.ifBlank { "Untitled Note" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        lineHeight = 16.sp,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Sub-description for links/text
                    if (item.type == SavedItemType.LINK) {
                        if (!item.linkTitle.isNullOrBlank()) {
                            Text(
                                text = item.linkTitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (!item.linkDescription.isNullOrBlank()) {
                            Text(
                                text = item.linkDescription,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 2,
                                lineHeight = 12.sp,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    } else if (item.type == SavedItemType.TEXT) {
                        Text(
                            text = parseMarkdown(item.content),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Render custom folders (excluding Archive) as tiny pills if present
                    val customFoldersList = item.folders.filter { it != "Archive" }
                    if (customFoldersList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            customFoldersList.take(2).forEach { folder ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = folder,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        DropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { showContextMenu = false },
        modifier = Modifier.testTag("context_menu_${item.id}")
    ) {
        DropdownMenuItem(
            text = { Text("Move to Folder") },
            onClick = {
                showContextMenu = false
                onManageFolders()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.testTag("menu_item_move_${item.id}")
        )
        DropdownMenuItem(
            text = { Text("Share") },
            onClick = {
                showContextMenu = false
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, item.content)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share with"))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.testTag("menu_item_share_${item.id}")
        )

        // Context Menu Archive/Restore (Highly Request!)
        val isArchived = item.folders.contains("Archive")
        DropdownMenuItem(
            text = { Text(if (isArchived) "Restore from Archive" else "Archive") },
            onClick = {
                showContextMenu = false
                onArchive()
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.testTag("menu_item_archive_${item.id}")
        )

        DropdownMenuItem(
            text = { Text("Delete", color = Color.Red) },
            onClick = {
                showContextMenu = false
                onDelete()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.testTag("menu_item_delete_${item.id}")
        )
    }

    if (isSelectionMode) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClick() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .testTag("select_checkbox_${item.id}")
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissWrapper(
    item: SavedItem,
    onDismissToDelete: () -> Unit,
    onDismissToArchive: () -> Unit,
    modifier: Modifier = Modifier,
    enable: Boolean = true,
    content: @Composable () -> Unit
) {
    val isArchived = remember(item.folders) { item.folders.contains("Archive") }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (!enable) {
                false
            } else {
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onDismissToArchive()
                        true
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        onDismissToDelete()
                        true
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.Settled -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> if (isArchived) Icons.Outlined.Unarchive else Icons.Filled.Archive
                SwipeToDismissBoxValue.EndToStart -> Icons.Filled.Delete
                SwipeToDismissBoxValue.Settled -> Icons.Filled.Delete
            }
            val text = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> if (isArchived) "Restore" else "Archive"
                SwipeToDismissBoxValue.EndToStart -> "Delete"
                SwipeToDismissBoxValue.Settled -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (direction == SwipeToDismissBoxValue.StartToEnd) {
                            Icon(
                                imageVector = icon,
                                contentDescription = text,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Icon(
                                imageVector = icon,
                                contentDescription = text,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        content = {
            content()
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ArchiveItemRow(
    item: SavedItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onManageFolders: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Spring animation for subtle press scale
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1.00f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "row_scale"
    )

    Box(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 6.dp else 2.dp,
                pressedElevation = 6.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick ?: { showContextMenu = true }
                )
                .testTag("item_row_${item.id}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.padding(end = 8.dp).testTag("select_checkbox_${item.id}")
                    )
                }

                // Left Icon indicating Type (Premium dynamic styling)
                val typeColor = when (item.type) {
                    SavedItemType.LINK -> MaterialTheme.colorScheme.primary
                    SavedItemType.IMAGE -> MaterialTheme.colorScheme.tertiary
                    SavedItemType.VIDEO -> Color(0xFFEF4444)
                    SavedItemType.CODE -> Color(0xFF10B981)
                    SavedItemType.TEXT -> MaterialTheme.colorScheme.secondary
                    SavedItemType.AUDIO -> Color(0xFF8B5CF6)
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(typeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO) {
                        AsyncImage(
                            model = item.getBestImagePath(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                        )
                    } else {
                        val icon = when (item.type) {
                            SavedItemType.LINK -> Icons.Outlined.Link
                            SavedItemType.IMAGE -> Icons.Outlined.Image
                            SavedItemType.VIDEO -> Icons.Outlined.VideoLibrary
                            SavedItemType.CODE -> Icons.Outlined.Code
                            SavedItemType.TEXT -> Icons.Outlined.Description
                            SavedItemType.AUDIO -> Icons.Outlined.Mic
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title, description and folders
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.type.displayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeColor
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Sync Status Pill
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (item.isSynced) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                width = 0.5.dp,
                                color = if (item.isSynced) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                }
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isSynced) Icons.Outlined.CloudDone else Icons.Outlined.CloudQueue,
                                    contentDescription = null,
                                    tint = if (item.isSynced) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    },
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (item.isSynced) "Synced" else "Local",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isSynced) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    }
                                )
                            }
                        }

                        // OCR Status Pill
                        if (item.extractedText != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "OCR",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.title.ifBlank { "Untitled Note" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Short content preview
                    if (item.type == SavedItemType.LINK && !item.linkTitle.isNullOrBlank()) {
                        Text(
                            text = item.linkTitle ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val previewText = when (item.type) {
                        SavedItemType.LINK -> if (!item.linkDescription.isNullOrBlank()) item.linkDescription else item.content
                        else -> item.content
                    }
                    Text(
                        text = parseMarkdown(previewText ?: ""),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Folders Display (excluding Archive)
                    val customFoldersList = item.folders.filter { it != "Archive" }
                    if (customFoldersList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            customFoldersList.take(3).forEach { folder ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = folder,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Right side timestamp and action buttons
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    val formattedDate = remember(item.timestamp) {
                        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                        sdf.format(Date(item.timestamp))
                    }
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier.testTag("context_menu_${item.id}")
        ) {
            DropdownMenuItem(
                text = { Text("Move to Folder") },
                onClick = {
                    showContextMenu = false
                    onManageFolders()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.testTag("menu_item_move_${item.id}")
            )
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    showContextMenu = false
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, item.content)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share with"))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.testTag("menu_item_share_${item.id}")
            )

            // Context Menu Archive/Restore
            val isArchived = item.folders.contains("Archive")
            DropdownMenuItem(
                text = { Text(if (isArchived) "Restore from Archive" else "Archive") },
                onClick = {
                    showContextMenu = false
                    onArchive()
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.testTag("menu_item_archive_${item.id}")
            )

            DropdownMenuItem(
                text = { Text("Delete", color = Color.Red) },
                onClick = {
                    showContextMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.testTag("menu_item_delete_${item.id}")
            )
        }
    }
}

@Composable
fun OnboardingSharingGuide(
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
            Text(
                text = "💡 Your Second Brain is Ready!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
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
                    imageVector = Icons.Outlined.Language,
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
                imageVector = Icons.Default.ArrowForward,
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
                    imageVector = Icons.Outlined.FolderSpecial,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                imageVector = icon,
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
                        Icon(Icons.Filled.Send, contentDescription = "Save Note", tint = MaterialTheme.colorScheme.primary)
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
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
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
                    Icon(Icons.Filled.Send, contentDescription = "Save Note")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Note")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchPageOverlay(
    viewModel: SecondBrainViewModel,
    onDismiss: () -> Unit,
    prefs: android.content.SharedPreferences,
    isListView: Boolean,
    onItemClick: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
    onManageFolders: (SavedItem) -> Unit,
    onArchive: (SavedItem) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val items by viewModel.filteredItems.collectAsState()
    var historyList by remember { mutableStateOf(getSearchHistory(prefs)) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Search Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.setSearchQuery("")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("search_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        viewModel.setSearchQuery(it)
                    },
                    placeholder = { 
                        Text(
                            text = "Search...", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        ) 
                    },
                    leadingIcon = { 
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) 
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                viewModel.setSearchQuery("") 
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (searchQuery.isNotBlank()) {
                            addQueryToHistory(prefs, searchQuery)
                            historyList = getSearchHistory(prefs)
                        }
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("search_input_overlay")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (searchQuery.isEmpty()) {
                // Show History Section
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    if (historyList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = {
                                clearSearchHistory(prefs)
                                historyList = emptyList()
                            }) {
                                Text("Clear All", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(historyList) { query ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setSearchQuery(query)
                                            addQueryToHistory(prefs, query)
                                            historyList = getSearchHistory(prefs)
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = query,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            removeQueryFromHistory(prefs, query)
                                            historyList = getSearchHistory(prefs)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove from history",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty Search State Guide
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Search your Brain",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Type keywords, folder tags, or urls to locate any capture.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // Show Live Search Results
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No results found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "We couldn't find any matches for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "Search Results (${items.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(items, key = { it.id }) { item ->
                            ArchiveItemRow(
                                item = item,
                                onClick = {
                                    addQueryToHistory(prefs, searchQuery)
                                    onItemClick(item)
                                },
                                onDelete = { onDelete(item) },
                                onManageFolders = { onManageFolders(item) },
                                onArchive = { onArchive(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getSearchHistory(prefs: android.content.SharedPreferences): List<String> {
    val historyStr = prefs.getString("search_history", "") ?: ""
    return if (historyStr.isBlank()) emptyList() else historyStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun saveSearchHistory(prefs: android.content.SharedPreferences, history: List<String>) {
    prefs.edit().putString("search_history", history.joinToString(",")).apply()
}

private fun addQueryToHistory(prefs: android.content.SharedPreferences, query: String) {
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

private fun clearSearchHistory(prefs: android.content.SharedPreferences) {
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

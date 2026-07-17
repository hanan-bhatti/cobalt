package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.SecondBrainViewModel
import com.example.data.model.SavedItemType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import java.util.Locale
import coil.compose.AsyncImage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import com.example.data.model.getBestImagePath
import androidx.compose.ui.text.style.TextOverflow

data class StorageBreakdownItem(
    val name: String,
    val sizeBytes: Long,
    val isFolder: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ManageStorageScreen(
    onNavigateBack: () -> Unit,
    viewModel: SecondBrainViewModel
) {
    val isSyncing by viewModel.isSyncing.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val cloudUsedStorageBytes by viewModel.cloudUsedStorageBytes.collectAsState()
    val selectedForBackupIds by viewModel.selectedForBackupIds.collectAsState()
    
    val categories = SavedItemType.values().map { type ->
        val itemsForType = allItems.filter { it.type == type }
        type to itemsForType
    }.filter { it.second.isNotEmpty() }

    var showDeselectDialog by remember { mutableStateOf<String?>(null) }
    var longClickedItem by remember { mutableStateOf<com.example.data.model.SavedItem?>(null) }
    var longClickedCategory by remember { mutableStateOf<SavedItemType?>(null) }
    val expandedStates = remember { mutableStateMapOf<SavedItemType, Boolean>() }

    val customFolders by viewModel.customFolders.collectAsState()

    val breakdownColors = remember {
        listOf(
            Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFAB47BC),
            Color(0xFFFFA726), Color(0xFFEF5350), Color(0xFF26A69A),
            Color(0xFFEC407A), Color(0xFF78909C), Color(0xFF8D6E63),
            Color(0xFFD4E157)
        )
    }

    val breakdownItems = remember(allItems, customFolders) {
        val folderSizes = mutableMapOf<String, Long>()
        val unassignedItems = mutableListOf<com.example.data.model.SavedItem>()

        allItems.forEach { item ->
            val itemSize = if (item.sizeBytes > 0L) {
                item.sizeBytes
            } else if ((item.thumbnailPath ?: item.content).startsWith("/")) {
                java.io.File(item.thumbnailPath ?: item.content).length()
            } else {
                item.content.length.toLong()
            }

            if (item.folders.isEmpty()) {
                unassignedItems.add(item)
            } else {
                item.folders.forEach { folderName ->
                    folderSizes[folderName] = (folderSizes[folderName] ?: 0L) + itemSize
                }
            }
        }

        val unassignedGroups = unassignedItems.groupBy { it.type }
        val allGroups = mutableListOf<StorageBreakdownItem>()

        folderSizes.forEach { (folderName, size) ->
            allGroups.add(StorageBreakdownItem(name = folderName, sizeBytes = size, isFolder = true))
        }
        unassignedGroups.forEach { (type, items) ->
            val size = items.sumOf { item ->
                if (item.sizeBytes > 0L) {
                    item.sizeBytes
                } else {
                    val path = item.thumbnailPath ?: item.content
                    if (path.startsWith("/")) java.io.File(path).length() else item.content.length.toLong()
                }
            }
            allGroups.add(StorageBreakdownItem(name = type.displayName, sizeBytes = size, isFolder = false))
        }

        val sortedGroups = allGroups.filter { it.sizeBytes > 0 }.sortedByDescending { it.sizeBytes }

        if (sortedGroups.size <= 10) {
            sortedGroups
        } else {
            val top9 = sortedGroups.take(9)
            val othersSize = sortedGroups.drop(9).sumOf { it.sizeBytes }
            top9 + StorageBreakdownItem(name = "Others", sizeBytes = othersSize, isFolder = false)
        }
    }

    val newSelectionSize = allItems.filter { it.id in selectedForBackupIds && !it.isSynced }.sumOf { item ->
        if (item.sizeBytes > 0L) {
            item.sizeBytes
        } else {
            val path = item.thumbnailPath ?: item.content
            if (path.startsWith("/")) {
                java.io.File(path).length()
            } else {
                item.content.length.toLong()
            }
        }
    }

    val totalPendingSize = cloudUsedStorageBytes + newSelectionSize
    val maxStorageBytes = viewModel.maxStorageBytes
    val limitReached = totalPendingSize >= maxStorageBytes

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Storage") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedForBackupIds.isNotEmpty()) {
                Surface(tonalElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Selected: ${selectedForBackupIds.size} item(s)")
                            Text("${formatStorageSize(newSelectionSize)} additional")
                        }
                        if (limitReached) {
                            Text("You've reached your 512MB free backup limit.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.backupSelectedItems() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !limitReached && newSelectionSize > 0
                        ) {
                            Text("Back Up Selected")
                        }
                    }
                }
            }
        }
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = { viewModel.syncData() },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isSyncing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                if (categories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(64.dp))
                            Text("No storage data available yet.")
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        val totalBytes = breakdownItems.sumOf { it.sizeBytes }
                        item(key = "storage_breakdown") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "${formatStorageSize(totalBytes)} used of 512 MB",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Only media attachments (images, video, audio) count toward cloud limits. Text entries are always free.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(20.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            breakdownItems.forEachIndexed { index, breakdownItem ->
                                                val fraction = (breakdownItem.sizeBytes.toFloat() / maxStorageBytes).coerceIn(0f, 1f)
                                                val color = breakdownColors.getOrElse(index) { Color.Gray }
                                                if (fraction > 0.005f) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .weight(fraction)
                                                            .background(color)
                                                    )
                                                }
                                            }
                                            
                                            val usedFraction = (totalBytes.toFloat() / maxStorageBytes).coerceIn(0f, 1f)
                                            val remainingFraction = 1f - usedFraction
                                            if (remainingFraction > 0.005f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .weight(remainingFraction)
                                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val columns = 2
                                        val rows = breakdownItems.chunked(columns)
                                        rows.forEach { rowItems ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                rowItems.forEach { breakdownItem ->
                                                    val index = breakdownItems.indexOf(breakdownItem)
                                                    val color = breakdownColors.getOrElse(index) { Color.Gray }
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .clip(CircleShape)
                                                                .background(color)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "${breakdownItem.name} (${formatStorageSize(breakdownItem.sizeBytes)})",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                if (rowItems.size < columns) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        categories.forEach { (type, items) ->
                            val categoryTotalSize = items.sumOf { item ->
                                if (item.sizeBytes > 0L) {
                                    item.sizeBytes
                                } else {
                                    val path = item.thumbnailPath ?: item.content
                                    if (path.startsWith("/")) {
                                        java.io.File(path).length()
                                    } else {
                                        item.content.length.toLong()
                                    }
                                }
                            }
                            
                            val baseColor = when (type) {
                                SavedItemType.LINK -> Color(0xFF42A5F5)
                                SavedItemType.IMAGE -> Color(0xFFAB47BC)
                                SavedItemType.VIDEO -> Color(0xFFEF5350)
                                SavedItemType.TEXT -> Color(0xFFFFA726)
                                SavedItemType.CODE -> Color(0xFF66BB6A)
                                SavedItemType.AUDIO -> Color(0xFF26A69A)
                            }
                            val iconResId = when (type) {
                                SavedItemType.LINK -> R.drawable.ic_custom_link
                                SavedItemType.IMAGE -> R.drawable.ic_custom_image
                                SavedItemType.VIDEO -> R.drawable.ic_custom_video
                                SavedItemType.CODE -> R.drawable.ic_custom_code
                                SavedItemType.TEXT -> R.drawable.ic_custom_text
                                SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                            }

                            val isExpanded = expandedStates[type] ?: false

                            item(key = "header_${type.name}") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = baseColor.copy(alpha = 0.08f),
                                    border = BorderStroke(1.5.dp, baseColor.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .combinedClickable(
                                            onClick = { expandedStates[type] = !isExpanded },
                                            onLongClick = { longClickedCategory = type }
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(baseColor.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = iconResId),
                                                    contentDescription = null,
                                                    tint = baseColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = type.displayName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "chevron")
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_chevron_down),
                                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                tint = baseColor,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .graphicsLayer(rotationZ = rotation)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val syncedCount = items.count { it.isSynced }
                                            val localCount = items.size - syncedCount
                                            Text(
                                                text = "${items.size} items • $syncedCount synced, $localCount local",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatStorageSize(categoryTotalSize),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        val progress = (categoryTotalSize.toFloat() / maxStorageBytes).coerceIn(0f, 1f)
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            color = baseColor,
                                            trackColor = baseColor.copy(alpha = 0.1f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                            }

                            item(key = "content_${type.name}") {
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                        items.forEach { item ->
                                            val isSelected = selectedForBackupIds.contains(item.id)
                                            val isAlreadyBackedUp = item.isSynced

                                            val itemSize = if (item.sizeBytes > 0L) {
                                                item.sizeBytes
                                            } else if ((item.thumbnailPath ?: item.content).startsWith("/")) {
                                                java.io.File(item.thumbnailPath ?: item.content).length()
                                            } else {
                                                item.content.length.toLong()
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height(40.dp)
                                                        .clip(RoundedCornerShape(1.5.dp))
                                                        .background(baseColor)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSelected) baseColor.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(0.5.dp, if (isSelected) baseColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .combinedClickable(
                                                            onClick = { viewModel.showDetailItem(item) },
                                                            onLongClick = { longClickedItem = item }
                                                        )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val bestImagePath = item.getBestImagePath()
                                                        if (item.type == SavedItemType.LINK && bestImagePath != null) {
                                                            AsyncImage(
                                                                model = bestImagePath,
                                                                contentDescription = null,
                                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                            )
                                                        } else {
                                                            val itemIconResId = when (item.type) {
                                                                SavedItemType.LINK -> R.drawable.ic_custom_link
                                                                SavedItemType.IMAGE -> R.drawable.ic_custom_image
                                                                SavedItemType.VIDEO -> R.drawable.ic_custom_video
                                                                SavedItemType.CODE -> R.drawable.ic_custom_code
                                                                SavedItemType.TEXT -> R.drawable.ic_custom_text
                                                                SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                                                            }
                                                            val itemBaseColor = when (item.type) {
                                                                SavedItemType.LINK -> Color(0xFF42A5F5)
                                                                SavedItemType.IMAGE -> Color(0xFFAB47BC)
                                                                SavedItemType.VIDEO -> Color(0xFFEF5350)
                                                                SavedItemType.TEXT -> Color(0xFFFFA726)
                                                                SavedItemType.CODE -> Color(0xFF66BB6A)
                                                                SavedItemType.AUDIO -> Color(0xFF26A69A)
                                                            }
                                                            
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(itemBaseColor.copy(alpha = 0.15f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    painter = painterResource(id = itemIconResId),
                                                                    contentDescription = null,
                                                                    tint = itemBaseColor,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                        
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = item.title.ifEmpty { "Untitled" },
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1
                                                            )
                                                            Text(
                                                                text = formatStorageSize(itemSize),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        
                                                        if (isAlreadyBackedUp) {
                                                            Icon(
                                                                imageVector = Icons.Default.CloudQueue,
                                                                contentDescription = "Backed up",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.padding(horizontal = 8.dp)
                                                            )
                                                        } else if (isSelected) {
                                                            Text(
                                                                text = "PENDING",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = baseColor,
                                                                modifier = Modifier.padding(horizontal = 8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeselectDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeselectDialog = null },
            title = { Text("Remove from Cloud?") },
            text = { Text("This will remove the item from your cloud backup. It will still be available locally.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeBackupItems(listOf(showDeselectDialog!!))
                    showDeselectDialog = null
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeselectDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (longClickedItem != null) {
        val item = longClickedItem!!
        val isAlreadyBackedUp = item.isSynced
        
        AlertDialog(
            onDismissRequest = { longClickedItem = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val baseColor = when (item.type) {
                        SavedItemType.LINK -> Color(0xFF42A5F5)
                        SavedItemType.IMAGE -> Color(0xFFAB47BC)
                        SavedItemType.VIDEO -> Color(0xFFEF5350)
                        SavedItemType.TEXT -> Color(0xFFFFA726)
                        SavedItemType.CODE -> Color(0xFF66BB6A)
                        SavedItemType.AUDIO -> Color(0xFF26A69A)
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(baseColor)
                    )
                    Text("Memory Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = item.title.ifEmpty { "Untitled Memory" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    val itemSize = if (item.sizeBytes > 0L) {
                        item.sizeBytes
                    } else if ((item.thumbnailPath ?: item.content).startsWith("/")) {
                        java.io.File(item.thumbnailPath ?: item.content).length()
                    } else {
                        item.content.length.toLong()
                    }
                    Text(
                        text = "Type: ${item.type.displayName} • Size: ${formatStorageSize(itemSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isAlreadyBackedUp) {
                        Text(
                            text = "This item is backed up securely in your Second Brain cloud storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "This item is currently local-only. Add it to a backup batch to save it in the cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (isAlreadyBackedUp) {
                        Button(
                            onClick = {
                                showDeselectDialog = item.id
                                longClickedItem = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Remove Cloud Backup")
                        }
                    } else {
                        val isSelected = selectedForBackupIds.contains(item.id)
                        Button(
                            onClick = {
                                val itemSize = if (item.sizeBytes > 0) item.sizeBytes else if ((item.thumbnailPath ?: item.content).startsWith("/")) java.io.File(item.thumbnailPath ?: item.content).length() else item.content.length.toLong()
                                if (!isSelected && (totalPendingSize + itemSize) > maxStorageBytes) {
                                    viewModel.showToast("Cannot select: exceeds remaining 512MB cloud storage limit.")
                                } else {
                                    viewModel.toggleBackupSelection(item.id)
                                }
                                longClickedItem = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSelected) "Remove from Backup Batch" else "Add to Backup Batch")
                        }
                    }
                    
                    OutlinedButton(
                        onClick = {
                            viewModel.showDetailItem(item)
                            longClickedItem = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Memory Details")
                    }

                    TextButton(
                        onClick = { longClickedItem = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (longClickedCategory != null) {
        val type = longClickedCategory!!
        val typeItems = allItems.filter { it.type == type }
        val nonSyncedItems = typeItems.filter { !it.isSynced }
        val allSelected = nonSyncedItems.isNotEmpty() && nonSyncedItems.all { selectedForBackupIds.contains(it.id) }
        
        val baseColor = when (type) {
            SavedItemType.LINK -> Color(0xFF42A5F5)
            SavedItemType.IMAGE -> Color(0xFFAB47BC)
            SavedItemType.VIDEO -> Color(0xFFEF5350)
            SavedItemType.TEXT -> Color(0xFFFFA726)
            SavedItemType.CODE -> Color(0xFF66BB6A)
            SavedItemType.AUDIO -> Color(0xFF26A69A)
        }
        
        AlertDialog(
            onDismissRequest = { longClickedCategory = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(baseColor)
                    )
                    Text("${type.displayName} Category Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Select actions for the entire ${type.displayName} category folder.")
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (nonSyncedItems.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (allSelected) {
                                    nonSyncedItems.forEach {
                                        if (selectedForBackupIds.contains(it.id)) {
                                            viewModel.toggleBackupSelection(it.id)
                                        }
                                    }
                                } else {
                                    val categoryUnsyncedSize = nonSyncedItems.sumOf {
                                        if (it.sizeBytes > 0) it.sizeBytes else if ((it.thumbnailPath ?: it.content).startsWith("/")) java.io.File(it.thumbnailPath ?: it.content).length() else it.content.length.toLong()
                                    }
                                    val otherCategoriesSelectionSize = allItems.filter { it.id in selectedForBackupIds && !it.isSynced && it.type != type }.sumOf {
                                        if (it.sizeBytes > 0) it.sizeBytes else if ((it.thumbnailPath ?: it.content).startsWith("/")) java.io.File(it.thumbnailPath ?: it.content).length() else it.content.length.toLong()
                                    }
                                    val totalPendingWithCategory = cloudUsedStorageBytes + otherCategoriesSelectionSize + categoryUnsyncedSize
                                    if (totalPendingWithCategory > maxStorageBytes) {
                                        viewModel.showToast("Exceeds 512MB free backup limit by ${formatStorageSize(totalPendingWithCategory - maxStorageBytes)}.")
                                    } else {
                                        viewModel.selectItemsForBackup(nonSyncedItems.map { it.id })
                                    }
                                }
                                longClickedCategory = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (allSelected) "Deselect All Unsynced Items" else "Select All Unsynced for Backup")
                        }
                    }
                    
                    TextButton(
                        onClick = { longClickedCategory = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0.0 KB"
    val kb = bytes / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return when {
        gb >= 1.0f -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0f -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.1f KB", kb)
    }
}

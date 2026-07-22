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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.material.icons.filled.Info
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.PaletteColor1
import com.example.ui.theme.PaletteColor2
import com.example.ui.theme.PaletteColor3
import com.example.ui.theme.PaletteColor4
import com.example.ui.theme.CategoryLink
import com.example.ui.theme.CategoryImage
import com.example.ui.theme.CategoryVideo
import com.example.ui.theme.CategoryText
import com.example.ui.theme.CategoryCode
import com.example.ui.theme.CategoryAudio
import com.example.ui.theme.CategoryMedia

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
            CategoryLink, CategoryCode, CategoryImage,
            CategoryText, CategoryVideo, CategoryAudio,
            PaletteColor1, PaletteColor2, PaletteColor3,
            PaletteColor4
        )
    }

    // --- Storage helpers (mirrors ViewModel logic) ---
    // Only IMAGE, VIDEO, and AUDIO use quota. Text / Code / Link are always free. Unavailable items consume 0 bytes.
    fun isMediaType(type: SavedItemType) =
        type == SavedItemType.IMAGE || type == SavedItemType.VIDEO || type == SavedItemType.AUDIO

    fun mediaQuotaBytes(item: com.example.data.model.SavedItem): Long {
        if (!isMediaType(item.type) || item.isUnavailable) return 0L
        if (item.sizeBytes > 0L) return item.sizeBytes
        val localPath = (item.thumbnailPath ?: item.content).takeIf { it.startsWith("/") }
        if (localPath != null) {
            val file = java.io.File(localPath)
            if (file.exists()) return file.length()
        }
        return 0L
    }
    // ---

    val breakdownItems = remember(allItems, customFolders) {
        val folderSizes = mutableMapOf<String, Long>()
        val unassignedItems = mutableListOf<com.example.data.model.SavedItem>()

        allItems.forEach { item ->
            // Only media bytes count toward storage — skip free items
            val itemSize = mediaQuotaBytes(item)
            if (itemSize == 0L) return@forEach

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
            val size = items.sumOf { mediaQuotaBytes(it) }
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

    // Only media items selected-for-backup count toward the pending quota
    val newSelectionSize = allItems.filter { it.id in selectedForBackupIds && !it.isSynced && isMediaType(it.type) }.sumOf { mediaQuotaBytes(it) }

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
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Cloud Backup Space",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${formatStorageSize(totalBytes)} of 512.0 MB used",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        val tooltipState = rememberTooltipState()
                                        val scope = rememberCoroutineScope()
                                        TooltipBox(
                                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                            tooltip = {
                                                PlainTooltip(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ) {
                                                    Text(
                                                        text = "Only media attachments (images, video, audio) count toward cloud limits. Text entries are always free.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(8.dp)
                                                    )
                                                }
                                            },
                                            state = tooltipState
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        if (tooltipState.isVisible) tooltipState.dismiss() else tooltipState.show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = "Storage Info",
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
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
                                                        .background(Color.Transparent)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        breakdownItems.forEachIndexed { index, breakdownItem ->
                                            val color = breakdownColors.getOrElse(index) { Color.Gray }
                                            val percentage = if (maxStorageBytes > 0) {
                                                (breakdownItem.sizeBytes.toFloat() / maxStorageBytes * 100).coerceIn(0f, 100f)
                                            } else 0f
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = breakdownItem.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${formatStorageSize(breakdownItem.sizeBytes)} (${String.format(Locale.US, "%.1f", percentage)}%)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        categories.forEach { (type, items) ->
                            // Only media types count toward cloud storage quota
                            val categoryTotalSize = if (isMediaType(type)) {
                                items.sumOf { mediaQuotaBytes(it) }
                            } else {
                                0L
                            }

                            val baseColor = when (type) {
                                SavedItemType.LINK -> CategoryLink
                                SavedItemType.IMAGE -> CategoryImage
                                SavedItemType.VIDEO -> CategoryVideo
                                SavedItemType.TEXT -> CategoryText
                                SavedItemType.CODE -> CategoryCode
                                SavedItemType.AUDIO -> CategoryAudio
                                SavedItemType.MEDIA -> CategoryMedia
                            }
                            val iconResId = when (type) {
                                SavedItemType.LINK -> R.drawable.ic_custom_link
                                SavedItemType.IMAGE -> R.drawable.ic_custom_image
                                SavedItemType.VIDEO -> R.drawable.ic_custom_video
                                SavedItemType.CODE -> R.drawable.ic_custom_code
                                SavedItemType.TEXT -> R.drawable.ic_custom_text
                                SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                                SavedItemType.MEDIA -> R.drawable.ic_custom_movie
                            }

                            val isExpanded = expandedStates[type] ?: false

                            item(key = "category_${type.name}") {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = baseColor.copy(alpha = 0.05f),
                                    border = BorderStroke(1.dp, baseColor.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Column {
                                        // Header Row (Clickable)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = { expandedStates[type] = !isExpanded },
                                                    onLongClick = { longClickedCategory = type }
                                                )
                                                .padding(14.dp)
                                        ) {
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
                                                val syncedCount = items.count { it.isSynced && !it.isUnavailable }
                                                val unavailableCount = items.count { it.isUnavailable }
                                                val localCount = items.size - syncedCount - unavailableCount
                                                val freeLabel = if (!isMediaType(type)) " • Free (no quota)" else ""
                                                val unavailLabel = if (unavailableCount > 0) " • $unavailableCount unavailable" else ""
                                                Text(
                                                    text = "${items.size} items • $syncedCount synced, $localCount local$unavailLabel$freeLabel",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                if (isMediaType(type)) {
                                                    Text(
                                                        text = formatStorageSize(categoryTotalSize),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                } else {
                                                    Text(
                                                        text = "Free",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            val progress = if (isMediaType(type)) {
                                                (categoryTotalSize.toFloat() / maxStorageBytes).coerceIn(0f, 1f)
                                            } else 0f
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

                                        // Collapsible item contents
                                        AnimatedVisibility(
                                            visible = isExpanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                            ) {
                                                HorizontalDivider(color = baseColor.copy(alpha = 0.15f))

                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    items.forEachIndexed { itemIndex, item ->
                                                        val isSelected = selectedForBackupIds.contains(item.id)
                                                        val isAlreadyBackedUp = item.isSynced && !item.isUnavailable
                                                        val isItemUnavailable = item.isUnavailable
                                                        val itemSize = mediaQuotaBytes(item)

                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .combinedClickable(
                                                                    onClick = { viewModel.showDetailItem(item) },
                                                                    onLongClick = { longClickedItem = item }
                                                                )
                                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            val bestImagePath = if (isItemUnavailable) null else item.getBestImagePath()
                                                            if (isItemUnavailable) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(36.dp)
                                                                        .clip(RoundedCornerShape(8.dp))
                                                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.CloudOff,
                                                                        contentDescription = "Unavailable",
                                                                        tint = MaterialTheme.colorScheme.error,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            } else if (item.type == SavedItemType.LINK && bestImagePath != null) {
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
                                                                    SavedItemType.MEDIA -> R.drawable.ic_custom_movie
                                                                }
                                                                val itemBaseColor = when (item.type) {
                                                                    SavedItemType.LINK -> CategoryLink
                                                                    SavedItemType.IMAGE -> CategoryImage
                                                                    SavedItemType.VIDEO -> CategoryVideo
                                                                    SavedItemType.TEXT -> CategoryText
                                                                    SavedItemType.CODE -> CategoryCode
                                                                    SavedItemType.AUDIO -> CategoryAudio
                                                                    SavedItemType.MEDIA -> CategoryMedia
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
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                Text(
                                                                    text = when {
                                                                        isItemUnavailable -> "Removed from cloud (0 B)"
                                                                        isMediaType(item.type) -> formatStorageSize(itemSize)
                                                                        else -> "Free"
                                                                    },
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = when {
                                                                        isItemUnavailable -> MaterialTheme.colorScheme.error
                                                                        isMediaType(item.type) -> MaterialTheme.colorScheme.onSurfaceVariant
                                                                        else -> MaterialTheme.colorScheme.tertiary
                                                                    }
                                                                )
                                                            }

                                                            Spacer(modifier = Modifier.width(8.dp))

                                                            if (isItemUnavailable) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.CloudOff,
                                                                            contentDescription = "Unavailable",
                                                                            tint = MaterialTheme.colorScheme.error,
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                        Text(
                                                                            text = "Unavailable",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = MaterialTheme.colorScheme.error,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            } else if (isAlreadyBackedUp) {
                                                                Icon(
                                                                    imageVector = Icons.Default.CloudQueue,
                                                                    contentDescription = "Backed up",
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier
                                                                        .padding(horizontal = 8.dp)
                                                                        .size(24.dp)
                                                                )
                                                            } else {
                                                                Checkbox(
                                                                    checked = isSelected,
                                                                    onCheckedChange = { checked ->
                                                                        if (checked) {
                                                                            if (isMediaType(item.type) && (totalPendingSize + itemSize) > maxStorageBytes) {
                                                                                viewModel.showToast("Cannot select: exceeds remaining 512MB cloud storage limit.")
                                                                            } else {
                                                                                viewModel.toggleBackupSelection(item.id)
                                                                            }
                                                                        } else {
                                                                            viewModel.toggleBackupSelection(item.id)
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }

                                                        if (itemIndex < items.size - 1) {
                                                            HorizontalDivider(
                                                                color = baseColor.copy(alpha = 0.08f),
                                                                modifier = Modifier.padding(start = 64.dp, end = 16.dp)
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
                        SavedItemType.LINK -> CategoryLink
                        SavedItemType.IMAGE -> CategoryImage
                        SavedItemType.VIDEO -> CategoryVideo
                        SavedItemType.TEXT -> CategoryText
                        SavedItemType.CODE -> CategoryCode
                        SavedItemType.AUDIO -> CategoryAudio
                        SavedItemType.MEDIA -> CategoryMedia
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
                    val itemSize = mediaQuotaBytes(item)
                    Text(
                        text = if (isMediaType(item.type)) "Type: ${item.type.displayName} • Size: ${formatStorageSize(itemSize)}"
                              else "Type: ${item.type.displayName} • Free (no quota)",
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
                                val itemSize = mediaQuotaBytes(item)
                                if (!isSelected && isMediaType(item.type) && (totalPendingSize + itemSize) > maxStorageBytes) {
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
            SavedItemType.LINK -> CategoryLink
            SavedItemType.IMAGE -> CategoryImage
            SavedItemType.VIDEO -> CategoryVideo
            SavedItemType.TEXT -> CategoryText
            SavedItemType.CODE -> CategoryCode
            SavedItemType.AUDIO -> CategoryAudio
            SavedItemType.MEDIA -> CategoryMedia
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
                                    val categoryUnsyncedSize = nonSyncedItems.sumOf { mediaQuotaBytes(it) }
                                    val otherCategoriesSelectionSize = allItems.filter { it.id in selectedForBackupIds && !it.isSynced && it.type != type }.sumOf { mediaQuotaBytes(it) }
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
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return when {
        gb >= 1.0f -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0f -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.1f KB", kb)
    }
}

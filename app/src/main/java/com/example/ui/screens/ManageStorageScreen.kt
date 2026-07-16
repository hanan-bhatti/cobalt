package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.SecondBrainViewModel
import com.example.data.model.SavedItemType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageStorageScreen(
    onNavigateBack: () -> Unit,
    viewModel: SecondBrainViewModel
) {
    val isSyncing by viewModel.isSyncing.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val cloudUsedStorageBytes by viewModel.cloudUsedStorageBytes.collectAsState()
    val selectedForBackupIds by viewModel.selectedForBackupIds.collectAsState()
    
    // Grouping for categories
    val categories = SavedItemType.values().map { type ->
        val itemsForType = allItems.filter { it.type == type }
        type to itemsForType
    }.filter { it.second.isNotEmpty() }

    var showDeselectDialog by remember { mutableStateOf<String?>(null) } // store item ID if we want to remove backup

    // Calculate total selected size (that isn't already backed up) to check limits
    val newSelectionSize = allItems.filter { it.id in selectedForBackupIds && !it.isSynced }.sumOf {
        val path = it.thumbnailPath ?: it.content
        if (path.startsWith("/")) {
            java.io.File(path).length()
        } else {
            it.content.length.toLong()
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
                            Text(String.format(Locale.US, "%.1f MB additional", newSelectionSize / (1024f * 1024f)))
                        }
                        if (limitReached) {
                            Text("You've reached your 512MB free backup limit.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncData() },
            modifier = Modifier.padding(padding)
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
                    categories.forEach { (type, items) ->
                        val nonSyncedItems = items.filter { !it.isSynced }
                        val allSelected = nonSyncedItems.isNotEmpty() && nonSyncedItems.all { selectedForBackupIds.contains(it.id) }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = type.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (nonSyncedItems.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            if (allSelected) {
                                                viewModel.deselectItemsForBackup(nonSyncedItems.map { it.id })
                                            } else {
                                                val toSelect = mutableListOf<String>()
                                                var currentPendingSize = totalPendingSize
                                                for (item in nonSyncedItems) {
                                                    if (selectedForBackupIds.contains(item.id)) continue
                                                    val itemSize = if ((item.thumbnailPath ?: item.content).startsWith("/")) {
                                                        java.io.File(item.thumbnailPath ?: item.content).length()
                                                    } else {
                                                        item.content.length.toLong()
                                                    }
                                                    if (currentPendingSize + itemSize <= maxStorageBytes) {
                                                        toSelect.add(item.id)
                                                        currentPendingSize += itemSize
                                                    }
                                                }
                                                if (toSelect.isNotEmpty()) {
                                                    viewModel.selectItemsForBackup(toSelect)
                                                }
                                            }
                                        }
                                    ) {
                                        Text(if (allSelected) "Deselect All" else "Select All", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        items(items) { item ->
                            val isSelected = selectedForBackupIds.contains(item.id)
                            val isAlreadyBackedUp = item.isSynced

                            val itemSize = if ((item.thumbnailPath ?: item.content).startsWith("/")) {
                                java.io.File(item.thumbnailPath ?: item.content).length()
                            } else {
                                item.content.length.toLong()
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected || isAlreadyBackedUp,
                                        onCheckedChange = { checked ->
                                            if (isAlreadyBackedUp && !checked) {
                                                showDeselectDialog = item.id
                                            } else if (!isAlreadyBackedUp) {
                                                // Enforce limit if checking
                                                if (checked && (totalPendingSize + itemSize) > maxStorageBytes) {
                                                    // Prevent selection visually
                                                } else {
                                                    viewModel.toggleBackupSelection(item.id)
                                                }
                                            }
                                        }
                                    )
                                    
                                    val iconResId = when (item.type) {
                                        SavedItemType.LINK -> R.drawable.ic_custom_link
                                        SavedItemType.IMAGE -> R.drawable.ic_custom_image
                                        SavedItemType.VIDEO -> R.drawable.ic_custom_video
                                        SavedItemType.CODE -> R.drawable.ic_custom_code
                                        SavedItemType.TEXT -> R.drawable.ic_custom_text
                                        SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                                    }
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
                                            text = item.title.ifEmpty { "Untitled" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.2f MB", itemSize / (1024f * 1024f)),
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
}

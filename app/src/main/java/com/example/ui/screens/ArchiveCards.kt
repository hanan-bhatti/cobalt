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

import android.content.Intent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.getBestImagePath
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Any?.isNotNullOrBlank(): Boolean {
    return this != null && this.toString().isNotBlank()
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
    folderColors: Map<String, String> = emptyMap(),
    onLongClick: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )

    // Dynamic coloring based on type
    val typeColor = when (item.type) {
        SavedItemType.LINK -> Color(0xFF2196F3) // Blue
        SavedItemType.IMAGE, SavedItemType.VIDEO -> Color(0xFFFF9800) // Orange
        SavedItemType.CODE -> Color(0xFF4CAF50) // Green
        SavedItemType.TEXT -> Color(0xFF9C27B0) // Purple
        SavedItemType.AUDIO -> Color(0xFFE91E63) // Pink
    }

    Box(
        modifier = modifier
            .padding(4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick ?: { showContextMenu = true }
                )
                .testTag("item_card_${item.id}")
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header: Icon + Type + Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val iconResId = when (item.type) {
                            SavedItemType.LINK -> R.drawable.ic_custom_link
                            SavedItemType.IMAGE -> R.drawable.ic_custom_image
                            SavedItemType.VIDEO -> R.drawable.ic_custom_video
                            SavedItemType.CODE -> R.drawable.ic_custom_code
                            SavedItemType.TEXT -> R.drawable.ic_custom_text
                            SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                        }
                        Icon(
                            painter = painterResource(id = iconResId),
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = item.type.displayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeColor
                        )
                    }

                    val formattedDate = remember(item.timestamp) {
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(item.timestamp))
                    }
                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title.ifBlank { "Untitled Note" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (item.type == SavedItemType.LINK) {
                        val context = LocalContext.current
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.content))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in browser",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Sub-description
                val cDesc = item.linkDescription?.trim() ?: item.content.trim()
                if (cDesc.isNotBlank() && cDesc.lowercase() != item.title.trim().lowercase()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = parseMarkdown(cDesc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        lineHeight = 14.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Media Preview (if applicable)
                val displayImage = item.getBestImagePath()
                val showMediaPreview = displayImage.isNotNullOrBlank() || 
                        item.type == SavedItemType.VIDEO || 
                        item.type == SavedItemType.AUDIO || 
                        item.type == SavedItemType.CODE
                
                if (showMediaPreview) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        val hasImage = displayImage.isNotNullOrBlank() && 
                                (item.type != SavedItemType.AUDIO && item.type != SavedItemType.VIDEO || displayImage?.endsWith(".mp4") == false)

                        if (hasImage) {
                            AsyncImage(
                                model = displayImage,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (item.type == SavedItemType.VIDEO) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .align(Alignment.Center),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_play),
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        } else {
                            when (item.type) {
                                SavedItemType.VIDEO -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xFFFF9800).copy(alpha = 0.15f),
                                                        Color(0xFFFF5722).copy(alpha = 0.25f)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_video),
                                                contentDescription = "Video",
                                                tint = Color(0xFFFF9800),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text(
                                                text = "Video Media",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF9800)
                                            )
                                        }
                                    }
                                }
                                SavedItemType.AUDIO -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xFFE91E63).copy(alpha = 0.15f),
                                                        Color(0xFF9C27B0).copy(alpha = 0.25f)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_voice),
                                                contentDescription = "Audio",
                                                tint = Color(0xFFE91E63),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text(
                                                text = "Audio Recording",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE91E63)
                                            )
                                        }
                                    }
                                }
                                SavedItemType.CODE -> {
                                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                                    val codeText = remember(item.content, isDark) {
                                        com.example.ui.components.CodeHighlighter.highlight(item.content, isDark)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = codeText,
                                            fontSize = 7.sp,
                                            lineHeight = 9.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            maxLines = 8,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_link),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tags / Folders
                val customFoldersList = item.folders
                if (customFoldersList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        customFoldersList.take(3).forEach { folder ->
                            val isArchiveBadge = folder == "Archive"
                            val folderColor = if (isArchiveBadge) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                val hex = folderColors[folder]
                                parseHexColor(hex, MaterialTheme.colorScheme.primary)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = folderColor
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    if (isArchiveBadge) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_archive),
                                            contentDescription = null,
                                            modifier = Modifier.size(10.dp).padding(end = 2.dp),
                                            tint = Color.White
                                        )
                                    }
                                    Text(
                                        text = folder,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 60.dp)
                                    )
                                }
                            }
                        }
                        if (customFoldersList.size > 3) {
                            Text(
                                text = "+${customFoldersList.size - 3}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Context Menu Dropdown
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Move to Folder") },
                onClick = { showContextMenu = false; onManageFolders() },
                leadingIcon = { Icon(painterResource(id = R.drawable.ic_custom_folder_open), null, modifier = Modifier.size(20.dp)) }
            )
            val isArchived = item.folders.contains("Archive")
            DropdownMenuItem(
                text = { Text(if (isArchived) "Restore from Archive" else "Archive") },
                onClick = { showContextMenu = false; onArchive() },
                leadingIcon = { Icon(painterResource(id = if (isArchived) R.drawable.ic_custom_unarchive else R.drawable.ic_custom_archive), null, modifier = Modifier.size(20.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { showContextMenu = false; onDelete() },
                leadingIcon = { Icon(painterResource(id = R.drawable.ic_custom_delete), null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
            )
        }

        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
    }
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
    folderColors: Map<String, String> = emptyMap(),
    onLongClick: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    // Similar to Card but horizontally aligned
    var showContextMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "row_scale"
    )

    val typeColor = when (item.type) {
        SavedItemType.LINK -> Color(0xFF2196F3)
        SavedItemType.IMAGE, SavedItemType.VIDEO -> Color(0xFFFF9800)
        SavedItemType.CODE -> Color(0xFF4CAF50)
        SavedItemType.TEXT -> Color(0xFF9C27B0)
        SavedItemType.AUDIO -> Color(0xFFE91E63)
    }

    Box(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick ?: { showContextMenu = true }
                )
                .testTag("item_row_${item.id}")
        ) {
            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Left thumbnail
                val displayImage = item.getBestImagePath()
                val hasImage = displayImage.isNotNullOrBlank() && 
                        (item.type != SavedItemType.AUDIO && item.type != SavedItemType.VIDEO || displayImage?.endsWith(".mp4") == false)

                if (hasImage) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        AsyncImage(
                            model = displayImage,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (item.type == SavedItemType.VIDEO) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_play),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                } else if (item.type == SavedItemType.CODE) {
                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val codeText = remember(item.content, isDark) {
                        com.example.ui.components.CodeHighlighter.highlight(item.content, isDark)
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = codeText,
                            fontSize = 6.sp,
                            lineHeight = 7.5.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 7,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    val iconResId = when (item.type) {
                        SavedItemType.LINK -> R.drawable.ic_custom_link
                        SavedItemType.TEXT -> R.drawable.ic_custom_text
                        SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                        SavedItemType.VIDEO -> R.drawable.ic_custom_video
                        SavedItemType.IMAGE -> R.drawable.ic_custom_image
                        SavedItemType.CODE -> R.drawable.ic_custom_code
                    }
                    val placeholderBg = typeColor.copy(alpha = 0.15f)
                    val placeholderTint = typeColor

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(placeholderBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = iconResId),
                            contentDescription = null,
                            tint = placeholderTint,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Middle Content
                Column(modifier = Modifier.weight(1f)) {
                    // Type & Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.type.displayName.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = typeColor,
                            letterSpacing = 1.sp
                        )
                        val formattedDate = remember(item.timestamp) {
                            SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(item.timestamp))
                        }
                        Text(
                            text = formattedDate,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.title.ifBlank { "Untitled Note" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val cDesc = item.linkDescription?.trim() ?: item.content.trim()
                    if (cDesc.isNotBlank() && cDesc.lowercase() != item.title.trim().lowercase()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = parseMarkdown(cDesc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Context Menu Dropdown
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Move to Folder") },
                onClick = { showContextMenu = false; onManageFolders() },
                leadingIcon = { Icon(painterResource(id = R.drawable.ic_custom_folder_open), null, modifier = Modifier.size(20.dp)) }
            )
            val isArchived = item.folders.contains("Archive")
            DropdownMenuItem(
                text = { Text(if (isArchived) "Restore from Archive" else "Archive") },
                onClick = { showContextMenu = false; onArchive() },
                leadingIcon = { Icon(painterResource(id = if (isArchived) R.drawable.ic_custom_unarchive else R.drawable.ic_custom_archive), null, modifier = Modifier.size(20.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { showContextMenu = false; onDelete() },
                leadingIcon = { Icon(painterResource(id = R.drawable.ic_custom_delete), null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
            )
        }

        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp)
            )
        }
    }
}

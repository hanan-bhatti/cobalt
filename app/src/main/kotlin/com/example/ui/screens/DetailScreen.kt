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

import androidx.compose.animation.EnterExitState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.components.bounceClick
import com.example.ui.components.MarkdownText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.getBestImagePath
import com.example.ui.viewmodel.CobaltViewModel
import java.text.SimpleDateFormat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.example.utils.DevicePerformance
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.painterResource
import com.example.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.ui.theme.CategoryLink
import com.example.ui.theme.CategoryImage
import com.example.ui.theme.CategoryVideo
import com.example.ui.theme.CategoryText
import com.example.ui.theme.CategoryCode
import com.example.ui.theme.CategoryAudio
import com.example.ui.theme.CategoryMedia
import com.example.ui.theme.DefaultFolderColor
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ui.theme.toThemeColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import com.example.utils.isRtlText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DetailScreen(
    viewModel: CobaltViewModel,
    onClose: () -> Unit,
    onEdit: (SavedItem) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    itemId: String? = null
) {
    val activeItem by viewModel.activeDetailItem.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val item = remember(activeItem, allItems, itemId) {
        activeItem?.takeIf { itemId.isNullOrBlank() || it.id == itemId }
            ?: (if (!itemId.isNullOrBlank()) allItems.find { it.id == itemId } else null)
            ?: activeItem
    }
    if (item == null) {
        LaunchedEffect(Unit) {
            onClose()
        }
        return
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val localHazeState = remember { HazeState() }

    val customFolders by viewModel.customFolderEntities.collectAsState()
    val isDark = isSystemInDarkTheme()
    val folderColor = remember(item, customFolders, isDark) {
        val firstFolder = item.folders.firstOrNull()
        val customFolder = if (firstFolder != null) {
            customFolders.find { it.name.equals(firstFolder, ignoreCase = true) }
        } else {
            null
        }
        (if (customFolder != null && !customFolder.colorHex.isNullOrBlank()) {
            try {
                Color(android.graphics.Color.parseColor(customFolder.colorHex))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        } ?: when (item.type) {
            SavedItemType.LINK -> CategoryLink
            SavedItemType.IMAGE -> CategoryImage
            SavedItemType.VIDEO -> CategoryVideo
            SavedItemType.TEXT -> CategoryText
            SavedItemType.CODE -> CategoryCode
            SavedItemType.AUDIO -> CategoryAudio
            SavedItemType.MEDIA -> CategoryMedia
        }).toThemeColor(isDark)
    }

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val blurOpacity by viewModel.blurOpacity.collectAsState()
    
    val userEmail by viewModel.userEmail.collectAsState()
    val isUserSignedIn = !userEmail.isNullOrBlank()

    var showLeaveAppDialog by remember { mutableStateOf(false) }
    var urlToOpen by remember { mutableStateOf("") }

    val formatter = remember { SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()) }
    val formattedDate = formatter.format(Date(item.timestamp))

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.id) {
        scale = 1f
        offset = Offset.Zero
    }

    val shareDownloadProgress by viewModel.shareDownloadProgress.collectAsState()
    if (shareDownloadProgress != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparing Share...", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Downloading media from the cloud so it can be shared.")
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { shareDownloadProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {}
        )
    }

    val isScreenVisible = animatedVisibilityScope?.transition?.targetState == EnterExitState.Visible || animatedVisibilityScope == null
    val barsAlpha by animateFloatAsState(
        targetValue = if (isScreenVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 240, delayMillis = 80, easing = FastOutSlowInEasing),
        label = "detailBarsAlpha"
    )
    val barsOffsetY by animateDpAsState(
        targetValue = if (isScreenVisible) 0.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "detailBarsOffsetY"
    )

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = barsAlpha
                        translationY = -barsOffsetY.toPx()
                    }
                    .onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInRoot()
                        android.util.Log.d("CobaltAnim", "Detail TopBar: alpha=$barsAlpha, pos=(${pos.x}, ${pos.y}), size=${coordinates.size}")
                    }
            ) {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onClose, modifier = Modifier.bounceClick().testTag("detail_back_button")) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_close),
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.bounceClick().testTag("detail_delete_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_delete),
                                contentDescription = "Delete item",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.shareItem(item, context)
                            },
                            modifier = Modifier.bounceClick().testTag("detail_share_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_share),
                                contentDescription = "Share item",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = barsAlpha
                        translationY = barsOffsetY.toPx()
                    }
                    .onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInRoot()
                        android.util.Log.d("CobaltAnim", "Detail BottomBar (Edit Item): alpha=$barsAlpha, pos=(${pos.x}, ${pos.y}), size=${coordinates.size}")
                    }
            ) {
                val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur
                val barModifier = if (useBlur) {
                    Modifier
                        .fillMaxWidth()
                        .hazeEffect(state = localHazeState, style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.background,
                            tint = HazeTint(MaterialTheme.colorScheme.background.copy(alpha = blurOpacity)),
                            blurRadius = blurRadius.dp,
                            noiseFactor = 0.02f
                        ))
                } else {
                    Modifier.fillMaxWidth()
                }
                Surface(
                    modifier = barModifier,
                    tonalElevation = 0.dp,
                    color = if (useBlur) Color.Transparent else MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.shareItem(item, context)
                            },
                            modifier = Modifier.bounceClick().testTag("detail_bottom_share_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_share),
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Button(
                            onClick = { onEdit(item) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 24.dp)
                                .height(48.dp)
                                .bounceClick()
                                .testTag("detail_bottom_edit_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = folderColor,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_edit),
                                contentDescription = "Edit",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Item", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .maybeSharedBounds(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    key = "card_${item.id}",
                    clipShape = RoundedCornerShape(0.dp)
                )
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInRoot()
                    android.util.Log.d("CobaltAnim", "Detail Card Morph: pos=(${pos.x}, ${pos.y}), size=${coordinates.size}, itemId=${item.id}")
                },
            color = MaterialTheme.colorScheme.background
        ) {
            var showFullscreenImage by remember { mutableStateOf(false) }
            SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(state = localHazeState)
                            .verticalScroll(scrollState)
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding()
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
            // HEADER SECTION (Only for non-MEDIA types; MEDIA items use redesigned MediaDetailSection hero header)
            if (item.type != SavedItemType.MEDIA) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Title
                    val displayTitle = item.title.ifBlank { item.linkTitle ?: "Untitled Item" }
                    val isTitleRtl = remember(displayTitle) { isRtlText(displayTitle) }
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isTitleRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        Text(
                            text = displayTitle,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isTitleRtl) TextAlign.Right else TextAlign.Left,
                            style = MaterialTheme.typography.displayMedium.copy(
                                textDirection = androidx.compose.ui.text.style.TextDirection.Content
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Meta row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = formattedDate,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val iconResId = when (item.type) {
                                SavedItemType.LINK -> R.drawable.ic_custom_link
                                SavedItemType.IMAGE -> R.drawable.ic_custom_image
                                SavedItemType.VIDEO, SavedItemType.MEDIA -> R.drawable.ic_custom_video
                                SavedItemType.CODE -> R.drawable.ic_custom_code
                                SavedItemType.TEXT -> R.drawable.ic_custom_text
                                SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                            }
                            Icon(
                                painter = painterResource(id = iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = item.type.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isUserSignedIn && !item.isBackedUp) {
                            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_cloud_queue),
                                contentDescription = "Local Only",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Folders
                    if (item.folders.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item.folders.forEach { folder ->
                                val folderEntity = customFolders.find { it.name.equals(folder, ignoreCase = true) }
                                val resolvedColor = if (folderEntity != null && !folderEntity.colorHex.isNullOrBlank()) {
                                    parseHexColor(folderEntity.colorHex, defaultColor = MaterialTheme.colorScheme.primary, isDark = isDark)
                                } else {
                                    when (folder) {
                                        "Links" -> CategoryLink.toThemeColor(isDark)
                                        "Images" -> CategoryImage.toThemeColor(isDark)
                                        "Videos" -> CategoryVideo.toThemeColor(isDark)
                                        "Code" -> CategoryCode.toThemeColor(isDark)
                                        "Text" -> CategoryText.toThemeColor(isDark)
                                        "Audio" -> CategoryAudio.toThemeColor(isDark)
                                        "Movies & Anime" -> CategoryMedia.toThemeColor(isDark)
                                        "Archive" -> MaterialTheme.colorScheme.outline
                                        else -> DefaultFolderColor.toThemeColor(isDark)
                                    }
                                }
                                val folderIconRes = if (folderEntity != null) {
                                    getFolderIconRes(folderEntity.iconName)
                                } else {
                                    when (folder) {
                                        "Links" -> R.drawable.ic_custom_link
                                        "Images" -> R.drawable.ic_custom_image
                                        "Videos" -> R.drawable.ic_custom_video
                                        "Code" -> R.drawable.ic_custom_code
                                        "Text" -> R.drawable.ic_custom_text
                                        "Audio" -> R.drawable.ic_custom_voice
                                        "Movies & Anime" -> R.drawable.ic_custom_movie
                                        "Archive" -> R.drawable.ic_custom_archive
                                        else -> R.drawable.ic_custom_folder
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = resolvedColor.copy(alpha = if (isDark) 0.15f else 0.08f),
                                    border = BorderStroke(1.dp, resolvedColor),
                                    modifier = Modifier.testTag("detail_folder_chip_$folder")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = folderIconRes),
                                            contentDescription = null,
                                            tint = resolvedColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = folder,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = resolvedColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // VISUAL MEDIA
            if (item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO) {
                if (item.isUnavailable) {
                    com.example.ui.components.UnavailableMediaPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    )
                } else if (item.type == SavedItemType.VIDEO) {
                    com.example.ui.components.VideoPlayer(
                        videoUri = item.content,
                        itemId = item.id,
                        thumbnailUrl = item.thumbnailPath,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    )
                } else {
                    AsyncImage(
                        model = item.getBestImagePath(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.05f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .clickable { showFullscreenImage = true }
                    )
                }
            }

            // LINK PREVIEW IMAGE
            if (item.type == SavedItemType.LINK && !item.linkImage.isNullOrBlank()) {
                AsyncImage(
                    model = item.linkImage,
                    contentDescription = item.linkTitle,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                )
            }

            if (item.type == SavedItemType.AUDIO && !item.thumbnailPath.isNullOrBlank()) {
                if (item.isUnavailable) {
                    com.example.ui.components.UnavailableMediaPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    )
                } else {
                    com.example.ui.components.AudioPlayerComponent(
                        audioUri = item.thumbnailPath
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // DESCRIPTION / LINK META
            val descriptionToShow = when (item.type) {
                SavedItemType.LINK -> item.linkDescription
                SavedItemType.IMAGE, SavedItemType.VIDEO -> {
                    item.extractedText?.takeIf { it.isNotBlank() }
                        ?: item.linkDescription?.takeIf { it.isNotBlank() }
                }
                SavedItemType.AUDIO -> {
                    item.extractedText?.takeIf { it.isNotBlank() }
                        ?: item.content.takeIf { it.isNotBlank() }
                }
                SavedItemType.CODE, SavedItemType.MEDIA -> null
                else -> item.content.takeIf { it.isNotBlank() }
            }

            if (!descriptionToShow.isNullOrBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header row with Section Label and Copy Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sectionIcon = when (item.type) {
                                SavedItemType.TEXT -> R.drawable.ic_custom_text
                                SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                                SavedItemType.LINK -> R.drawable.ic_custom_link
                                SavedItemType.IMAGE -> R.drawable.ic_custom_image
                                SavedItemType.VIDEO -> R.drawable.ic_custom_video
                                else -> R.drawable.ic_custom_text
                            }
                            Icon(
                                painter = painterResource(id = sectionIcon),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = when (item.type) {
                                    SavedItemType.TEXT -> "NOTE CONTENT"
                                    SavedItemType.AUDIO -> "TRANSCRIPTION"
                                    SavedItemType.LINK -> "DESCRIPTION"
                                    SavedItemType.IMAGE, SavedItemType.VIDEO -> "DESCRIPTION"
                                    else -> "CONTENT"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Copy Button
                        DisableSelection {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        coroutineScope.launch {
                                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Cobalt Content", descriptionToShow)))
                                        }
                                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_copy),
                                    contentDescription = "Copy",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Copy",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Rendered Markdown/Text with full bidirectional support
                    MarkdownText(
                        markdown = descriptionToShow,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        onMarkdownChange = { updated ->
                            viewModel.updateSavedItem(item.copy(content = updated))
                        }
                    )
                }
            }

            // RAW URL / CONTENT SNIPPET
            if (item.type == SavedItemType.LINK || item.type == SavedItemType.CODE) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (item.type == SavedItemType.LINK) "SOURCE LINK" else "CODE SNIPPET",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            DisableSelection {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (item.type == SavedItemType.LINK) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_link_external),
                                            contentDescription = "Open Link",
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable {
                                                    com.example.util.CobaltCustomTabsLauncher.launch(
                                                        context = context,
                                                        url = item.content,
                                                        isDarkTheme = isDark,
                                                        itemId = item.id,
                                                        itemTitle = item.title
                                                    )
                                                },
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_copy),
                                        contentDescription = "Copy",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                coroutineScope.launch {
                                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Cobalt Content", item.content)))
                                                }
                                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                            },
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        val codeText = if (item.type == SavedItemType.CODE) {
                            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                            remember(item.content, isDark) {
                                com.example.ui.components.CodeHighlighter.highlight(item.content, isDark)
                            }
                        } else {
                            parseMarkdown(item.content, MaterialTheme.colorScheme.primary)
                        }

                        val isCodeRtl = remember(item.content) { isRtlText(item.content) }
                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (item.type != SavedItemType.CODE && isCodeRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        ) {
                            Text(
                                text = codeText,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = if (item.type != SavedItemType.CODE && isCodeRtl) TextAlign.Right else TextAlign.Left,
                                style = if (item.type == SavedItemType.CODE) {
                                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                } else {
                                    MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = androidx.compose.ui.text.style.TextDirection.Content
                                    )
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (item.type == SavedItemType.MEDIA) {
                MediaDetailSection(
                    item = item,
                    viewModel = viewModel,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }


            
            Spacer(modifier = Modifier.height(40.dp))
            
            if (showLeaveAppDialog) {
                com.example.ui.components.ExpressiveConfirmationDialog(
                    onDismissRequest = { showLeaveAppDialog = false },
                    title = "Leaving App",
                    message = "You are about to visit an external link in your browser. Do you want to leave the application?",
                    confirmButtonText = "Leave App",
                    onConfirm = {
                        showLeaveAppDialog = false
                        com.example.util.CobaltCustomTabsLauncher.launch(
                            context = context,
                            url = urlToOpen,
                            isDarkTheme = isDark,
                            itemId = item.id,
                            itemTitle = item.title
                        )
                    },
                    dismissButtonText = "Cancel",
                    isDestructive = false,
                    icon = painterResource(id = R.drawable.ic_custom_link_external)
                )
            }
            if (showFullscreenImage) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showFullscreenImage = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        var dialogScale by remember { mutableStateOf(1f) }
                        var dialogOffset by remember { mutableStateOf(Offset.Zero) }
                        
                        AsyncImage(
                            model = item.getBestImagePath(),
                            contentDescription = "Fullscreen Image",
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (dialogScale * zoom).coerceIn(1f, 5f)
                                        if (newScale > 1f) {
                                            val maxOffset = (newScale - 1f) * 400f
                                            val newOffset = dialogOffset + pan
                                            dialogOffset = Offset(
                                                x = newOffset.x.coerceIn(-maxOffset, maxOffset),
                                                y = newOffset.y.coerceIn(-maxOffset, maxOffset)
                                            )
                                        } else {
                                            dialogOffset = Offset.Zero
                                        }
                                        dialogScale = newScale
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (dialogScale > 1f) {
                                                dialogScale = 1f
                                                dialogOffset = Offset.Zero
                                            } else {
                                                dialogScale = 2.5f
                                            }
                                        }
                                    )
                                }
                                .graphicsLayer(
                                    scaleX = dialogScale,
                                    scaleY = dialogScale,
                                    translationX = dialogOffset.x,
                                    translationY = dialogOffset.y
                                ),
                            contentScale = ContentScale.Fit
                        )
                        
                        IconButton(
                            onClick = { showFullscreenImage = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(32.dp)
                                .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_custom_close), contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 16.dp))
        }
        }
    }
    }

    if (showDeleteConfirm) {
        com.example.ui.components.ExpressiveConfirmationDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete Archive",
            message = "Are you sure you want to delete this item? This action cannot be undone.",
            confirmButtonText = "Delete",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSavedItem(item)
                onClose()
            },
            dismissButtonText = "Cancel",
            isDestructive = true
        )
    }
}

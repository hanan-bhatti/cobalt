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

import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.example.ui.components.bounceClick
import com.example.ui.components.AudioRecorderComponent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.ui.components.VideoPlayer
import com.example.ui.components.InlineNewFolderCreator
import com.example.ui.viewmodel.CobaltViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.ui.theme.CategoryAudio
import com.example.ui.theme.CategoryCode
import com.example.ui.theme.CategoryImage
import com.example.ui.theme.CategoryLink
import com.example.ui.theme.CategoryMedia
import com.example.ui.theme.CategoryText
import com.example.ui.theme.CategoryVideo
import com.example.utils.DevicePerformance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

private fun getCategoryMeta(type: SavedItemType): Pair<Int, Color> {
    return when (type) {
        SavedItemType.LINK -> Pair(R.drawable.ic_custom_link, CategoryLink)
        SavedItemType.IMAGE -> Pair(R.drawable.ic_custom_image, CategoryImage)
        SavedItemType.VIDEO -> Pair(R.drawable.ic_custom_video, CategoryVideo)
        SavedItemType.TEXT -> Pair(R.drawable.ic_custom_text, CategoryText)
        SavedItemType.CODE -> Pair(R.drawable.ic_custom_code, CategoryCode)
        SavedItemType.AUDIO -> Pair(R.drawable.ic_custom_voice, CategoryAudio)
        SavedItemType.MEDIA -> Pair(R.drawable.ic_custom_movie, CategoryMedia)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: CobaltViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeItem by viewModel.activeCaptureItem.collectAsState()

    BackHandler { viewModel.cancelCapture() }
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val isOcrLoading by viewModel.isOcrLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val ocrError by viewModel.ocrError.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()
    val extractedLinks by viewModel.extractedLinksToReview.collectAsState()

    val prefs = remember { context.getSharedPreferences("cobalt_prefs", android.content.Context.MODE_PRIVATE) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var dontAskAgainBackupNotifs by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.saveActiveItem()
    }

    val handleSaveClick = {
        val dontAskAgain = prefs.getBoolean("dont_ask_backup_notifications", false)
        if (com.example.BuildConfig.FLAVOR != "foss" && !dontAskAgain && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (status != PackageManager.PERMISSION_GRANTED) {
                showPermissionRationale = true
            } else {
                viewModel.saveActiveItem()
            }
        } else {
            viewModel.saveActiveItem()
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = {
                if (dontAskAgainBackupNotifs) {
                    prefs.edit().putBoolean("dont_ask_backup_notifications", true).apply()
                }
                showPermissionRationale = false
                viewModel.saveActiveItem()
            },
            title = { Text("Backup Notifications") },
            text = {
                Column {
                    Text("Allow Cobalt to send notifications so you can see live progress of your background backups and know when they complete.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dontAskAgainBackupNotifs = !dontAskAgainBackupNotifs }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = dontAskAgainBackupNotifs,
                            onCheckedChange = { dontAskAgainBackupNotifs = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Don't ask again",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontAskAgainBackupNotifs) {
                        prefs.edit().putBoolean("dont_ask_backup_notifications", true).apply()
                    }
                    showPermissionRationale = false
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (dontAskAgainBackupNotifs) {
                        prefs.edit().putBoolean("dont_ask_backup_notifications", true).apply()
                    }
                    showPermissionRationale = false
                    viewModel.saveActiveItem()
                }) {
                    Text("No thanks")
                }
            }
        )
    }

    LaunchedEffect(ocrError) {
        ocrError?.let {
            Toast.makeText(context, com.example.util.GoogleApiErrorMapper.formatUserFriendlyError(it), Toast.LENGTH_LONG).show()
            viewModel.clearOcrError()
        }
    }

    val localHazeState = remember { HazeState() }
    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val blurOpacity by viewModel.blurOpacity.collectAsState()
    val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur
    val clipboard = LocalClipboard.current

    val item = activeItem ?: return

    var clipboardUrl by remember { mutableStateOf("") }
    LaunchedEffect(item.type, clipboard) {
        if (item.type == SavedItemType.LINK) {
            val entry = clipboard.getClipEntry()
            val text = entry?.clipData?.let { data ->
                if (data.itemCount > 0) data.getItemAt(0).text?.toString()?.trim().orEmpty() else ""
            } ?: ""
            if (text.isNotBlank() && (text.startsWith("http://") || text.startsWith("https://") || text.contains("."))) {
                clipboardUrl = text
            } else {
                clipboardUrl = ""
            }
        }
    }

    val saveProgress by viewModel.saveProgress.collectAsState()
    val isSavingActive = saveProgress != null
    val animatedProgress by animateFloatAsState(
        targetValue = saveProgress ?: 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "saveProgress"
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Scaffold(
        topBar = {
            val topBarModifier = if (useBlur) {
                Modifier
                    .fillMaxWidth()
                    .hazeEffect(
                        state = localHazeState,
                        style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.background,
                            tint = HazeTint(MaterialTheme.colorScheme.background.copy(alpha = blurOpacity)),
                            blurRadius = blurRadius.dp,
                            noiseFactor = 0.02f
                        )
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            }

            Surface(
                modifier = topBarModifier,
                color = if (useBlur) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = if (useBlur) 0.dp else 2.dp,
                border = if (useBlur) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)) else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Pull indicator pill indicating bottom sheet behavior
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                    }

                    // Expressive Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (currentIconRes, currentCategoryColor) = getCategoryMeta(item.type)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = currentCategoryColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, currentCategoryColor.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = currentIconRes),
                                    contentDescription = null,
                                    tint = currentCategoryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Capturing ${item.type.displayName}",
                                    color = currentCategoryColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Swipe down to close",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        bottomBar = {
            val bottomBarModifier = if (useBlur) {
                Modifier
                    .fillMaxWidth()
                    .hazeEffect(
                        state = localHazeState,
                        style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.background,
                            tint = HazeTint(MaterialTheme.colorScheme.background.copy(alpha = blurOpacity)),
                            blurRadius = blurRadius.dp,
                            noiseFactor = 0.02f
                        )
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            }

            Surface(
                modifier = bottomBarModifier,
                color = if (useBlur) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = if (useBlur) 0.dp else 4.dp,
                border = if (useBlur) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel Button: right under the left thumb!
                    OutlinedButton(
                        onClick = { viewModel.cancelCapture() },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .bounceClick()
                            .testTag("cancel_capture_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_close),
                            contentDescription = "Cancel",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // Save Button: right under the right thumb!
                    Button(
                        onClick = handleSaveClick,
                        enabled = !isSavingActive,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSavingActive) primaryColor.copy(alpha = 0.35f) else primaryColor,
                            contentColor = onPrimaryColor
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .bounceClick()
                            .testTag("save_capture_button")
                            .clip(RoundedCornerShape(20.dp))
                            .drawWithContent {
                                if (isSavingActive) {
                                    drawRoundRect(
                                        color = primaryColor,
                                        size = size.copy(width = size.width * animatedProgress),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
                                    )
                                    val shimmerBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                                            androidx.compose.ui.graphics.Color.Transparent
                                        ),
                                        start = androidx.compose.ui.geometry.Offset(shimmerTranslate, 0f),
                                        end = androidx.compose.ui.geometry.Offset(shimmerTranslate + 300f, size.height)
                                    )
                                    drawRoundRect(
                                        brush = shimmerBrush,
                                        size = size.copy(width = size.width * animatedProgress),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
                                    )
                                }
                                drawContent()
                            }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_archive),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSavingActive) "Saving..." else "Save Archive",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .hazeSource(state = localHazeState)
        ) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = SavedItemType.entries.indexOf(item.type).coerceAtLeast(0),
                pageCount = { SavedItemType.entries.size }
            )

            // Keep category tab bar scrolled to active item
            androidx.compose.runtime.LaunchedEffect(item.type) {
                val index = SavedItemType.entries.indexOf(item.type)
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                }
            }

            // Sync pager swipe to viewModel only when settled (prevents gesture jank/conflicts)
            androidx.compose.runtime.LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { settledPage ->
                    val pageType = SavedItemType.entries.getOrNull(settledPage) ?: return@collect
                    if (pageType != item.type) {
                        viewModel.switchActiveCaptureType(pageType)
                    }
                }
            }

            // Sync external item.type change to pager when not actively dragging
            androidx.compose.runtime.LaunchedEffect(item.type) {
                val index = SavedItemType.entries.indexOf(item.type)
                if (index >= 0 && index != pagerState.currentPage && !pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(index)
                }
            }

            // ITEM TYPE SELECTOR (Horizontal chip bar at top)
            androidx.compose.foundation.lazy.LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(SavedItemType.entries.size) { index ->
                    val t = SavedItemType.entries[index]
                    val isSelected = item.type == t
                    val (iconRes, catColor) = getCategoryMeta(t)
                    
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) catColor.copy(alpha = 0.18f) 
                                else if (useBlur) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) catColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .bounceClick()
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                                viewModel.switchActiveCaptureType(t)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                tint = if (isSelected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = t.displayName,
                                color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // HORIZONTAL PAGER (Fill available height with independent scrollable pages)
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = true,
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val pageType = SavedItemType.entries[pageIndex]
                val (pageIconRes, pageCategoryColor) = getCategoryMeta(pageType)
                var showNewFolderCreator by remember { mutableStateOf(false) }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val minContentHeight = maxHeight
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minContentHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
            if (pageType != SavedItemType.MEDIA) {
                // TITLE FIELD CARD
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (useBlur) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(pageCategoryColor)
                            )
                            Text(
                                text = "Title",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        OutlinedTextField(
                            value = item.title,
                            onValueChange = { viewModel.updateActiveCaptureItem { item -> item.copy(title = it) } },
                            placeholder = { Text("Add an archive title...", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                textDirection = androidx.compose.ui.text.style.TextDirection.Content
                            ),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = pageCategoryColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            trailingIcon = {
                                if (item.title.isNotBlank()) {
                                    IconButton(onClick = { viewModel.updateActiveCaptureItem { item -> item.copy(title = "") } }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_close),
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("capture_title_input")
                        )
                    }
                }
            }

            // MEDIA DRAWING CANVAS (OCR Region Selection)
            val mediaPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri != null) {
                    viewModel.handleMediaSelected(uri, pageType)
                }
            }

            // UNIFIED MEDIA PREVIEW
            if (pageType == SavedItemType.IMAGE || pageType == SavedItemType.VIDEO) {
                var isDrawMode by remember { mutableStateOf(false) }
                val mediaUrl = item.content
                
                // Show Media file picker buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            mediaPicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    if (pageType == SavedItemType.IMAGE) androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    else androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).bounceClick(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = pageCategoryColor.copy(alpha = 0.16f),
                            contentColor = pageCategoryColor
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = if (pageType == SavedItemType.IMAGE) R.drawable.ic_custom_image else R.drawable.ic_custom_video),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (pageType == SavedItemType.IMAGE) "Select Image File" else "Select Video File",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (mediaUrl.isNotBlank() || capturedBitmap != null) {
                        IconButton(
                            onClick = {
                                viewModel.updateActiveCaptureItem { it.copy(content = "") }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .bounceClick()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_delete),
                                contentDescription = "Delete File",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (pageType == SavedItemType.IMAGE && capturedBitmap != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shared Image OCR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Draw Mode", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Switch(
                                checked = isDrawMode,
                                onCheckedChange = { isDrawMode = it },
                                modifier = Modifier.graphicsLayer { scaleX = 0.7f; scaleY = 0.7f }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, if(isDrawMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .background(Color.Black)
                    ) {
                        ImageMarkingCanvas(
                            bitmap = capturedBitmap!!,
                            onRegionSelected = { x, y, w, h ->
                                viewModel.performRegionOcr(x, y, w, h)
                            },
                            enabled = isDrawMode && !isOcrLoading,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (pageType == SavedItemType.IMAGE && mediaUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    ) {
                        AsyncImage(
                            model = mediaUrl,
                            contentDescription = "Selected Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (pageType == SavedItemType.VIDEO && mediaUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_video),
                            contentDescription = "Video Loaded",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // EXTRACTED TEXT FIELD / NOTES FIELD
            if (isOcrLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Gemini AI OCR extracting text...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (item.extractedText != null || pageType == SavedItemType.IMAGE || pageType == SavedItemType.VIDEO) {
                Text(
                    text = if (item.extractedText != null && capturedBitmap != null) "Extracted OCR Result" else "Notes / Description",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = item.extractedText ?: "",
                    onValueChange = { viewModel.updateActiveCaptureItem { item -> item.copy(extractedText = it) } },
                    shape = RoundedCornerShape(20.dp),
                    minLines = 3,
                    maxLines = Int.MAX_VALUE,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                        lineHeight = 22.sp
                    ),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("ocr_result_output")
                )

                if (extractedLinks.isNotEmpty()) {
                    Text(
                        text = "Review Extracted Links",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        extractedLinks.forEach { reviewItem ->
                            val host = remember(reviewItem.url) {
                                try {
                                    val cleanUrl = if (!reviewItem.url.startsWith("http://") && !reviewItem.url.startsWith("https://")) {
                                        "https://${reviewItem.url}"
                                    } else reviewItem.url
                                    java.net.URI(cleanUrl).host?.removePrefix("www.") ?: "link"
                                } catch (_: Exception) {
                                    "link"
                                }
                            }

                            Surface(
                                onClick = {
                                    viewModel.toggleExtractedLinkSelection(reviewItem.id, !reviewItem.isSelected)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (reviewItem.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    1.dp,
                                    if (reviewItem.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (reviewItem.isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                border = BorderStroke(
                                                    1.5.dp,
                                                    if (reviewItem.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .testTag("review_link_checkbox_${reviewItem.id}")
                                            ) {
                                                if (reviewItem.isSelected) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_custom_check),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_custom_link),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = host,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("URL", reviewItem.url))
                                                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp).bounceClick()
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_copy),
                                                contentDescription = "Copy URL",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    if (reviewItem.description.isNotBlank()) {
                                        Text(
                                            text = reviewItem.description,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                textDirection = androidx.compose.ui.text.style.TextDirection.Content
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    androidx.compose.foundation.text.BasicTextField(
                                        value = reviewItem.url,
                                        onValueChange = { newValue ->
                                            viewModel.updateExtractedLink(reviewItem.id, newValue)
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                            .border(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                            .testTag("review_link_input_${reviewItem.id}")
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.confirmAndSaveExtractedLinks() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick()
                                .testTag("confirm_save_extracted_links_button")
                        ) {
                            Text("Confirm & Save Selected Links", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (pageType == SavedItemType.MEDIA) {
                // Media capture view
                val searchResults by viewModel.mediaSearchResults.collectAsState()
                val isSearchingMedia by viewModel.isSearchingMedia.collectAsState()
                var searchQuery by remember { mutableStateOf("") }

                LaunchedEffect(searchQuery) {
                    delay(350)
                    if (searchQuery.isNotBlank()) { viewModel.searchMedia(searchQuery) }
                }

                if (item.title.isBlank()) {
                    // Show search bar and results list
                    Text(
                        text = "Search Movies, TV Shows & Anime",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search movies, TV shows, anime...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_search),
                                contentDescription = "Search",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_close),
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    if (isSearchingMedia) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            CircularWavyProgressIndicator()
                        }
                    } else if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results found for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Render Search Results List
                        searchResults.forEach { result ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.updateActiveCaptureItem { current ->
                                            current.copy(
                                                id = result.id,
                                                title = result.title,
                                                content = result.overview ?: "",
                                                thumbnailPath = result.posterUrl,
                                                backdropUrl = result.backdropUrl,
                                                mediaType = result.mediaType,
                                                watchStatus = "Plan to Watch",
                                                releaseYear = result.releaseYear,
                                                genres = result.genres,
                                                watchProviders = result.watchProviders,
                                                trailerUrl = result.trailerUrl,
                                                folders = listOf("Media")
                                            )
                                        }
                                        val tempItem = item.copy(
                                            id = result.id,
                                            title = result.title,
                                            content = result.overview ?: "",
                                            thumbnailPath = result.posterUrl,
                                            backdropUrl = result.backdropUrl,
                                            mediaType = result.mediaType,
                                            watchStatus = "Plan to Watch",
                                            releaseYear = result.releaseYear,
                                            genres = result.genres,
                                            watchProviders = result.watchProviders,
                                            trailerUrl = result.trailerUrl,
                                            folders = listOf("Media")
                                        )
                                        viewModel.enrichMediaItem(tempItem, saveToDb = false)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Poster
                                    if (!result.posterUrl.isNullOrBlank()) {
                                        coil.compose.AsyncImage(
                                            model = result.posterUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(width = 60.dp, height = 90.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val typeLabel = when (result.mediaType.lowercase()) {
                                            "movie" -> "Movie"
                                            "tv" -> "TV Show"
                                            "anime" -> "Anime"
                                            else -> result.mediaType
                                        }
                                        Text(
                                            text = "${result.releaseYear ?: "N/A"} • $typeLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        if (!result.overview.isNullOrBlank()) {
                                            Text(
                                                text = result.overview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Show Preview with a nice "Change" Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected Media Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = {
                                viewModel.updateActiveCaptureItem { current ->
                                    current.copy(
                                        title = "",
                                        content = "",
                                        thumbnailPath = null,
                                        backdropUrl = null,
                                        mediaType = null,
                                        watchStatus = null,
                                        releaseYear = null,
                                        genres = emptyList(),
                                        watchProviders = emptyList(),
                                        trailerUrl = null,
                                        folders = emptyList()
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Change",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Render the full rich MediaDetailSection
                    MediaDetailSection(
                        item = item,
                        viewModel = viewModel,
                        onWatchStatusChanged = { status ->
                            viewModel.updateActiveCaptureItem { current ->
                                current.copy(watchStatus = status)
                            }
                        }
                    )
                }
            } else if (pageType != SavedItemType.IMAGE && pageType != SavedItemType.VIDEO) {
                // MAIN CONTENT EDITOR (for non-media types: Link, Code, Audio, Text)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(pageCategoryColor)
                    )
                    Text(
                        text = when (pageType) {
                            SavedItemType.LINK -> "URL / Link"
                            SavedItemType.CODE -> "Code Snippet Source"
                            SavedItemType.AUDIO -> "Voice Memo"
                            else -> "Note Content"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                val mediaPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        viewModel.handleMediaSelected(uri, pageType)
                    }
                }

                val editorFont = if (pageType == SavedItemType.CODE) FontFamily.Monospace else FontFamily.SansSerif
                val isMultiLine = pageType == SavedItemType.TEXT || pageType == SavedItemType.CODE

                if (pageType == SavedItemType.AUDIO) {
                    AudioRecorderComponent(
                        onRecordComplete = { file: java.io.File ->
                            viewModel.transcribeAudioMemo(file)
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (!item.thumbnailPath.isNullOrBlank()) {
                        com.example.ui.components.AudioPlayerComponent(
                            audioUri = item.thumbnailPath,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    if (isOcrLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Gemini AI transcribing audio...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Text("Transcription / Notes:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        RichTextEditor(
                            value = item.content,
                            onValueChange = { newContent -> viewModel.updateActiveCaptureItem { it.copy(content = newContent) } },
                            placeholder = { Text("Audio transcription will appear here, or you can type your notes...") },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                    }
                } else if (pageType == SavedItemType.TEXT) {
                    RichTextEditor(
                        value = item.content,
                        onValueChange = { newContent ->
                            viewModel.updateActiveCaptureItem { captured ->
                                captured.copy(
                                    content = newContent
                                )
                            }
                        },
                        placeholder = { Text("Capture your thoughts or paste clipboard contents...") },
                        minLines = if (isMultiLine) 5 else 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("capture_content_input")
                    )
                } else {
                    if (pageType == SavedItemType.LINK && item.content.isBlank() && clipboardUrl.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CategoryLink.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, CategoryLink.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .bounceClick()
                                .clickable {
                                    val urlToSet = if (!clipboardUrl.startsWith("http://") && !clipboardUrl.startsWith("https://")) {
                                        "https://$clipboardUrl"
                                    } else clipboardUrl
                                    viewModel.updateActiveCaptureItem { it.copy(content = urlToSet) }
                                    viewModel.fetchLinkPreviewForActiveItem(urlToSet)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_link),
                                    contentDescription = null,
                                    tint = CategoryLink,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Paste Link: ${clipboardUrl.take(28)}...",
                                    color = CategoryLink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = item.content,
                        onValueChange = { viewModel.updateActiveCaptureItem { captured -> captured.copy(content = it) } },
                        visualTransformation = if (pageType == SavedItemType.CODE) {
                            com.example.ui.components.CodeSyntaxHighlightTransformation(androidx.compose.foundation.isSystemInDarkTheme())
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                        keyboardOptions = if (pageType == SavedItemType.LINK) {
                            KeyboardOptions(keyboardType = KeyboardType.Uri)
                        } else {
                            KeyboardOptions.Default
                        },
                        placeholder = {
                            Text(
                                when (pageType) {
                                    SavedItemType.LINK -> "https://example.com/shared-resource"
                                    SavedItemType.CODE -> "Write or paste source code..."
                                    else -> ""
                                }
                            )
                        },
                        shape = RoundedCornerShape(18.dp),
                        minLines = if (isMultiLine) 5 else 1,
                        maxLines = Int.MAX_VALUE,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = editorFont),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = pageCategoryColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        trailingIcon = {
                            if (item.content.isNotBlank()) {
                                IconButton(onClick = { viewModel.updateActiveCaptureItem { it.copy(content = "") } }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_close),
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("capture_content_input")
                    )
                }
                if (pageType == SavedItemType.LINK && item.content.isNotBlank()) {
                    val isExtracting by viewModel.isMetadataExtracting.collectAsState()
                    val metadataError by viewModel.metadataError.collectAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                viewModel.updateActiveCaptureItem {
                                    it.copy(
                                        linkTitle = null,
                                        linkDescription = null,
                                        linkImage = null
                                    )
                                }
                                viewModel.fetchLinkPreviewForActiveItem(item.content)
                            }
                            .testTag("retry_link_extraction")
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_sync),
                            contentDescription = "Retry Extraction",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Retry Extraction",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        if (isExtracting) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Extracting real-time link preview...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (metadataError != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Extraction Failed",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = metadataError!!,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (!item.linkTitle.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!item.linkImage.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.linkImage,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_globe),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.linkTitle ?: "",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!item.linkDescription.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.linkDescription ?: "",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val uri = try {
                                        android.net.Uri.parse(item.content)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    val domain = uri?.host ?: item.content
                                    Text(
                                        text = domain,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            // Empty state (no data, not extracting)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.fetchLinkPreviewForActiveItem(item.content)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_link),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "No preview available",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Click to attempt metadata extraction",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

                        // White space eliminator: pushes folders and category assurance note to the bottom of the screen
                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.height(16.dp))

                        // ORGANIZATIONAL FOLDERS SELECTION
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (useBlur) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_folder),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Assign to Custom Folders",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showNewFolderCreator = true }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .bounceClick()
                                            .testTag("capture_new_folder_button")
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_plus),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "New Folder",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (showNewFolderCreator) {
                                    InlineNewFolderCreator(
                                        onAddFolder = { name, icon ->
                                            viewModel.createFolder(name, iconName = icon)
                                            viewModel.updateActiveCaptureItem { it.copy(folders = it.folders + name) }
                                            showNewFolderCreator = false
                                        },
                                        onCancel = { showNewFolderCreator = false }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                if (customFolders.isEmpty()) {
                                    Text(
                                        text = "No custom folders created yet. Create folder tags above or on the Home Screen to tag your items.",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        customFolders.forEach { folder ->
                                            val isAssigned = item.folders.contains(folder)
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isAssigned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                contentColor = if (isAssigned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isAssigned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier
                                                    .bounceClick()
                                                    .clickable {
                                                        viewModel.updateActiveCaptureItem { item ->
                                                            val newFoldersList = if (isAssigned) {
                                                                item.folders - folder
                                                            } else {
                                                                item.folders + folder
                                                            }
                                                            item.copy(folders = newFoldersList)
                                                        }
                                                    }
                                                    .testTag("tag_chip_$folder")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (isAssigned) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_custom_check),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                    Text(
                                                        folder,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isAssigned) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // AUTO-CATEGORIZATION ASSURANCE BOX (Design polish)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (useBlur) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_info),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = buildAnnotatedString {
                                        append("This item will be automatically archived into system category '")
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(pageType.displayName)
                                        }
                                        append("'.")
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // Bottom clearance for the thumb action bar
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ImageMarkingCanvas(
    bitmap: Bitmap,
    onRegionSelected: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var pathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var containerWidth by remember { mutableStateOf(0f) }
    var containerHeight by remember { mutableStateOf(0f) }

    val currentEnabled by androidx.compose.runtime.rememberUpdatedState(enabled)
    val currentOnRegionSelected by androidx.compose.runtime.rememberUpdatedState(onRegionSelected)
    val currentBitmap by androidx.compose.runtime.rememberUpdatedState(bitmap)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width.toFloat()
                containerHeight = coordinates.size.height.toFloat()
            }
            .pointerInput(bitmap, currentEnabled) {
                if (currentEnabled) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            pathPoints = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            pathPoints = pathPoints + change.position
                        },
                        onDragEnd = {
                            if (pathPoints.isNotEmpty() && containerWidth > 0 && containerHeight > 0) {
                                val minX = pathPoints.minOf { it.x }.coerceIn(0f, containerWidth)
                            val maxX = pathPoints.maxOf { it.x }.coerceIn(0f, containerWidth)
                            val minY = pathPoints.minOf { it.y }.coerceIn(0f, containerHeight)
                            val maxY = pathPoints.maxOf { it.y }.coerceIn(0f, containerHeight)

                            // Map coordinates to original bitmap dimensions
                            val scaleX = currentBitmap.width / containerWidth
                            val scaleY = currentBitmap.height / containerHeight

                            val cropX = (minX * scaleX).toInt()
                            val cropY = (minY * scaleY).toInt()
                            val cropWidth = ((maxX - minX) * scaleX).toInt()
                            val cropHeight = ((maxY - minY) * scaleY).toInt()

                            currentOnRegionSelected(cropX, cropY, cropWidth, cropHeight)
                        }
                    }
                )
                }
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Shared visual asset",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (pathPoints.size > 1) {
                val path = Path().apply {
                    moveTo(pathPoints.first().x, pathPoints.first().y)
                    for (i in 1 until pathPoints.size) {
                        lineTo(pathPoints[i].x, pathPoints[i].y)
                    }
                }
                // Light transparent primary freehand outline stroke
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.6f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                val minX = pathPoints.minOf { it.x }
                val maxX = pathPoints.maxOf { it.x }
                val minY = pathPoints.minOf { it.y }
                val maxY = pathPoints.maxOf { it.y }

                // Draw a nice translucent region bounding rectangle box
                drawRect(
                    color = primaryColor.copy(alpha = 0.15f),
                    topLeft = Offset(minX, minY),
                    size = Size(maxX - minX, maxY - minY),
                    style = Fill
                )
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(minX, minY),
                    size = Size(maxX - minX, maxY - minY),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

// FlowRow implementation (Since FlowRow wasn't included in early Compose, standard wrapping Row with horizontal scroll is perfect)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Basic Layout fallback that supports beautiful horizontal scrolling for folder chips
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

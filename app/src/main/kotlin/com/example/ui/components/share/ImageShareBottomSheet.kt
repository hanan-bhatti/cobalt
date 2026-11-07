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

package com.example.ui.components.share

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SavedItem
import com.example.ui.theme.LocalCobaltTypography
import com.example.ui.theme.MyApplicationTheme
import com.example.util.displayTitle
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive bottom sheet for sharing and exporting saved image items.
 * Offers live preview of the Polaroid / Minimal photo card with adaptive aspect ratios,
 * original uncompressed file sharing, and in-button clipboard confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageShareBottomSheet(
    item: SavedItem,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val systemInDark = isSystemInDarkTheme()

    // Persistent preferences
    val initialStyle = remember { SharePreferencesManager.getImageShareStyle(context) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }
    var showAttribution by remember { mutableStateOf(SharePreferencesManager.isAttributionEnabled(context)) }

    // Dynamic color preference directly from cobalt_settings
    val userDynamicColor = remember {
        context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)
            .getBoolean("dynamic_color", true)
    }
    val typographyPrefs = LocalCobaltTypography.current.prefs

    var currentTagline by remember { mutableStateOf(CobaltTaglines.getRandom()) }
    var isPreviewDark by remember { mutableStateOf(systemInDark) }
    var isExporting by remember { mutableStateOf(false) }
    var isPhotoCopied by remember { mutableStateOf(false) }
    var isCaptionCopied by remember { mutableStateOf(false) }

    // Swipeable pager state
    val pagerState = rememberPagerState(
        initialPage = if (initialStyle == ImageShareStyle.POLAROID) 0 else 1
    ) { 2 }

    // Sync selectedStyle only when pager completely settles (prevents gesture jank)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            val newStyle = if (settledPage == 0) ImageShareStyle.POLAROID else ImageShareStyle.MINIMAL
            if (selectedStyle != newStyle) {
                selectedStyle = newStyle
                SharePreferencesManager.setImageShareStyle(context, newStyle)
            }
        }
    }

    var triggerCapture by remember { mutableStateOf(false) }
    var capturedCallback by remember { mutableStateOf<((Bitmap) -> Unit)?>(null) }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SHARE IMAGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.displayTitle(context),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Close share sheet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Style Switcher (Polaroid vs Minimal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ImageShareStyle.entries.forEach { style ->
                    val isSelected = selectedStyle == style
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedStyle = style
                                SharePreferencesManager.setImageShareStyle(context, style)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(if (style == ImageShareStyle.POLAROID) 0 else 1)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (style == ImageShareStyle.POLAROID) R.drawable.ic_custom_image else R.drawable.ic_custom_grid
                                ),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = style.displayName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Preferences Bar: Attribution, Shuffle, and Text-Only Theme Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (showAttribution) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.clickable {
                            val updated = !showAttribution
                            showAttribution = updated
                            SharePreferencesManager.setAttributionEnabled(context, updated)
                        }
                    ) {
                        Text(
                            text = if (showAttribution) "Tagline: On" else "Tagline: Off",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (showAttribution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    if (showAttribution) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { currentTagline = CobaltTaglines.getRandom() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_sync),
                                    contentDescription = "Shuffle tagline",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Shuffle",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.clickable { isPreviewDark = !isPreviewDark }
                ) {
                    Text(
                        text = if (isPreviewDark) "Dark" else "Light",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Pass horizontal touch deltas cleanly to HorizontalPager while absorbing
            // boundary overscroll so the parent bottom sheet does not bounce or stutter
            val pagerScrollInterop = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        // Return Zero to let the child HorizontalPager consume horizontal gestures
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        // Absorb unconsumed horizontal delta at pager boundaries to prevent sheet bounce
                        return Offset(available.x, 0f)
                    }
                }
            }

            // Live Swipeable Preview Container
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .nestedScroll(pagerScrollInterop)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val pageStyle = if (page == 0) ImageShareStyle.POLAROID else ImageShareStyle.MINIMAL
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    drawContent()
                                    if (triggerCapture && pagerState.currentPage == page) {
                                        val w = size.width.toInt()
                                        val h = size.height.toInt()
                                        if (w > 0 && h > 0) {
                                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                            val composeCanvas = Canvas(android.graphics.Canvas(bitmap))
                                            val originalCanvas = drawContext.canvas
                                            drawContext.canvas = composeCanvas
                                            drawContent()
                                            drawContext.canvas = originalCanvas
                                            val cb = capturedCallback
                                            capturedCallback = null
                                            triggerCapture = false
                                            cb?.invoke(bitmap)
                                        }
                                    }
                                }
                        ) {
                            MyApplicationTheme(
                                darkTheme = isPreviewDark,
                                dynamicColor = userDynamicColor,
                                typographyPrefs = typographyPrefs
                            ) {
                                ImageShareCard(
                                    item = item,
                                    style = pageStyle,
                                    showAttribution = showAttribution,
                                    tagline = currentTagline
                                )
                            }
                        }
                    }
                }
            }

            // Action Group Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option 1: Share Photo Card (PNG)
                    ShareActionRowItem(
                        icon = painterResource(id = R.drawable.ic_custom_image),
                        title = "Share photo card",
                        subtitle = "Framed photo with caption & copies text",
                        enabled = !isExporting,
                        trailing = {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_share),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            if (isExporting) return@ShareActionRowItem
                            isExporting = true
                            capturedCallback = { bitmap ->
                                coroutineScope.launch {
                                    try {
                                        val caption = item.title.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: item.extractedText
                                        ShareImageExporter.exportAndShareBitmap(
                                            context = context,
                                            bitmap = bitmap,
                                            linkUrl = caption,
                                            chooserTitle = "Share Photo Card"
                                        )
                                        onDismissRequest()
                                    } catch (e: Throwable) {
                                        android.util.Log.e("CobaltShare", "Error exporting photo card", e)
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            }
                            triggerCapture = true
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                        thickness = 0.5.dp
                    )

                    // Option 2: Share Original Image (Raw, uncompressed)
                    ShareActionRowItem(
                        icon = painterResource(id = R.drawable.ic_custom_share),
                        title = "Share original image",
                        subtitle = "Uncompressed, unaltered photo file",
                        onClick = {
                            ShareImageExporter.shareOriginalImage(context, item)
                            onDismissRequest()
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                        thickness = 0.5.dp
                    )

                    // Option 3: Copy Photo Card to Clipboard
                    ShareActionRowItem(
                        icon = painterResource(id = if (isPhotoCopied) R.drawable.ic_custom_copy_success else R.drawable.ic_custom_copy),
                        title = if (isPhotoCopied) "Photo copied!" else "Copy photo",
                        subtitle = if (isPhotoCopied) "Copied to clipboard" else "Copy photo card to clipboard",
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBgColor = if (isPhotoCopied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        titleColor = if (isPhotoCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        trailing = if (isPhotoCopied) {
                            {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_check),
                                    contentDescription = "Copied",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            if (isPhotoCopied) return@ShareActionRowItem
                            capturedCallback = { bitmap ->
                                coroutineScope.launch {
                                    ShareImageExporter.copyImageToClipboard(context, bitmap)
                                    isPhotoCopied = true
                                    kotlinx.coroutines.delay(650)
                                    onDismissRequest()
                                }
                            }
                            triggerCapture = true
                        }
                    )

                    // Option 4 (Optional): Copy Caption or OCR text if present
                    val hasText = item.title.isNotBlank() || !item.extractedText.isNullOrBlank()
                    if (hasText) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                            thickness = 0.5.dp
                        )
                        val textToCopy = item.extractedText?.takeIf { it.isNotBlank() } ?: item.title
                        ShareActionRowItem(
                            icon = painterResource(id = if (isCaptionCopied) R.drawable.ic_custom_copy_success else R.drawable.ic_custom_text),
                            title = if (isCaptionCopied) "Caption copied!" else "Copy caption text",
                            subtitle = textToCopy,
                            onClick = {
                                if (isCaptionCopied) return@ShareActionRowItem
                                isCaptionCopied = true
                                ShareImageExporter.copyToClipboard(context, textToCopy, showToast = false)
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(650)
                                    onDismissRequest()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual action row inside the share action group card.
 */
@Composable
private fun ShareActionRowItem(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBgColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconBgColor.copy(alpha = iconBgColor.alpha * alpha),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = alpha),
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor.copy(alpha = alpha)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f * alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}

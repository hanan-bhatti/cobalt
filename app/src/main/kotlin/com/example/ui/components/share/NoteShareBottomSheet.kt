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
import com.example.data.model.SavedItemType
import com.example.notes.NotePersistence
import com.example.ui.theme.LocalCobaltTypography
import com.example.ui.theme.MyApplicationTheme
import com.example.util.displayTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Material 3 Expressive bottom sheet for sharing and exporting notes and code snippets.
 * Features live adaptive 4K image preview, synchronized swipeable pager between Editorial and
 * Minimal styles, clean .md file export, and large-note clipboard error handling.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteShareBottomSheet(
    item: SavedItem,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val systemInDark = isSystemInDarkTheme()

    // Persistent preferences
    val initialStyle = remember { SharePreferencesManager.getNoteShareStyle(context) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }
    var showAttribution by remember { mutableStateOf(SharePreferencesManager.isAttributionEnabled(context)) }

    val userDynamicColor = remember {
        context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)
            .getBoolean("dynamic_color", true)
    }
    val typographyPrefs = LocalCobaltTypography.current.prefs

    var currentTagline by remember { mutableStateOf(CobaltTaglines.getRandom()) }
    var isPreviewDark by remember { mutableStateOf(systemInDark) }
    var isExporting by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    var clipboardError by remember { mutableStateOf<String?>(null) }

    // Asynchronously load note body
    var noteBody by remember(item.id) {
        mutableStateOf(item.content.takeIf { it.isNotBlank() } ?: item.extractedText.orEmpty())
    }

    LaunchedEffect(item.id) {
        if (noteBody.isBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val hash = item.contentHash
                    if (hash != null) {
                        val persistence = NotePersistence(context)
                        persistence.store.open(item.id, hash).use { input ->
                            val reader = input.bufferedReader()
                            val chars = CharArray(120_000)
                            val read = reader.read(chars, 0, 120_000)
                            if (read > 0) {
                                noteBody = String(chars, 0, read)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("CobaltShare", "Error loading note body for share", t)
                }
            }
        }
    }

    // Swipeable pager state
    val pagerState = rememberPagerState(
        initialPage = if (initialStyle == NoteShareStyle.EDITORIAL) 0 else 1
    ) { 2 }

    // Sync selectedStyle only when pager completely settles
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            val newStyle = if (settledPage == 0) NoteShareStyle.EDITORIAL else NoteShareStyle.MINIMAL
            if (selectedStyle != newStyle) {
                selectedStyle = newStyle
                SharePreferencesManager.setNoteShareStyle(context, newStyle)
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
                        text = if (item.type == SavedItemType.CODE) "SHARE CODE" else "SHARE NOTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.title.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: item.displayTitle(context),
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

            // Style Switcher (Synchronized with Swipeable Pager)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NoteShareStyle.entries.forEach { style ->
                    val isSelected = selectedStyle == style
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedStyle = style
                                SharePreferencesManager.setNoteShareStyle(context, style)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(if (style == NoteShareStyle.EDITORIAL) 0 else 1)
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
                                    id = if (style == NoteShareStyle.EDITORIAL) R.drawable.ic_custom_markdown else R.drawable.ic_custom_quote
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

            // Quick Preferences Bar: Attribution, Shuffle, and Theme Toggle
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
            // boundary overscroll so parent bottom sheet does not bounce or stutter
            val pagerScrollInterop = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset = Offset.Zero

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset = Offset(available.x, 0f)
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
                        val pageStyle = if (page == 0) NoteShareStyle.EDITORIAL else NoteShareStyle.MINIMAL
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    drawContent()
                                    if (triggerCapture && pagerState.currentPage == page) {
                                        // 4K Ultra-Crisp Rendering Scale Factor (~2.5x - 3.0x scaling)
                                        val scale = 2.5f
                                        val w = (size.width * scale).toInt()
                                        val h = (size.height * scale).toInt()
                                        if (w > 0 && h > 0) {
                                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                            val composeCanvas = Canvas(android.graphics.Canvas(bitmap))
                                            composeCanvas.scale(scale, scale)
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
                                NoteShareCard(
                                    item = item,
                                    noteBody = noteBody,
                                    style = pageStyle,
                                    showAttribution = showAttribution,
                                    tagline = currentTagline
                                )
                            }
                        }
                    }
                }
            }

            // Inline Clipboard Warning Message (Shown if note exceeds safe clipboard memory limits)
            if (clipboardError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = clipboardError.orEmpty(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Contextual Export Actions
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Option 1: Share as Card (High-Resolution 4K Image)
                    ShareActionRowItem(
                        icon = painterResource(id = R.drawable.ic_custom_image),
                        title = "Share card image (4K)",
                        subtitle = "High-resolution ${if (selectedStyle == NoteShareStyle.EDITORIAL) "Document" else "Quote"} visual card",
                        trailing = {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                                        ShareImageExporter.exportAndShareBitmap(
                                            context = context,
                                            bitmap = bitmap,
                                            chooserTitle = "Share Note Card"
                                        )
                                        onDismissRequest()
                                    } catch (e: Throwable) {
                                        android.util.Log.e("CobaltShare", "Error exporting note card image", e)
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            }
                            triggerCapture = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )

                    // Option 2: Share as Markdown File (.md) with clean sanitized filename
                    ShareActionRowItem(
                        icon = painterResource(id = R.drawable.ic_custom_markdown),
                        title = "Share as Markdown (.md)",
                        subtitle = "Clean file with sanitized title",
                        trailing = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            coroutineScope.launch {
                                ShareImageExporter.shareNoteAsMarkdownFile(
                                    context = context,
                                    item = item,
                                    noteBody = noteBody
                                )
                                onDismissRequest()
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )

                    // Option 3: Share as Text
                    ShareActionRowItem(
                        icon = painterResource(id = R.drawable.ic_custom_text),
                        title = "Share as text",
                        subtitle = "Direct text to messaging or notes",
                        trailing = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            ShareImageExporter.shareNoteAsText(context, item, noteBody)
                            onDismissRequest()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )

                    // Option 4: Copy Note Text (with large-note memory protection)
                    ShareActionRowItem(
                        icon = painterResource(id = if (isCopied) R.drawable.ic_custom_copy_success else R.drawable.ic_custom_copy),
                        title = if (isCopied) "Text copied!" else "Copy note text",
                        subtitle = if (isCopied) "Copied to clipboard" else "${noteBody.length} characters",
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBgColor = if (isCopied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        titleColor = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        trailing = if (isCopied) {
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
                            if (isCopied) return@ShareActionRowItem
                            clipboardError = null
                            val status = ShareImageExporter.copyToClipboardSafe(context, noteBody, label = "Cobalt Note")
                            when (status) {
                                ShareImageExporter.CopyStatus.SUCCESS -> {
                                    isCopied = true
                                    coroutineScope.launch {
                                        delay(650)
                                        onDismissRequest()
                                    }
                                }
                                ShareImageExporter.CopyStatus.TOO_LARGE -> {
                                    clipboardError = "Note is too large for clipboard (~${noteBody.length / 1000}KB). Please share as a .md file."
                                }
                                ShareImageExporter.CopyStatus.ERROR -> {
                                    clipboardError = "Could not copy note text to clipboard."
                                }
                            }
                        }
                    )
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

        trailing?.invoke()
    }
}


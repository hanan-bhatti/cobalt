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
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.notes.NotePersistence
import com.example.ui.theme.LocalCobaltTypography
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Material 3 Expressive bottom sheet for sharing and exporting notes and code snippets.
 * Redesigned with a floating hero card that overlaps the top edge of the sheet into the
 * backdrop scrim, connected style switchers, persisted user preferences, and high-resolution export.
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

    var triggerCapture by remember { mutableStateOf(false) }
    var capturedCallback by remember { mutableStateOf<((Bitmap) -> Unit)?>(null) }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = BottomSheetDefaults.ScrimColor,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            FloatingHeroSheetLayout(
                modifier = Modifier.fillMaxWidth(),
                heroCard = {
                    val heroWidth = when (selectedStyle) {
                        NoteShareStyle.DOCUMENT -> 280.dp
                        NoteShareStyle.MINIMAL -> 280.dp
                        NoteShareStyle.COMPACT -> 310.dp
                    }
                    val heroShape = when (selectedStyle) {
                        NoteShareStyle.DOCUMENT -> RoundedCornerShape(4.dp)
                        NoteShareStyle.MINIMAL -> RoundedCornerShape(18.dp)
                        NoteShareStyle.COMPACT -> RoundedCornerShape(18.dp)
                    }
                    Box(
                        modifier = Modifier
                            .width(heroWidth)
                            .padding(horizontal = 4.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = heroShape,
                                spotColor = Color.Black.copy(alpha = 0.22f),
                                ambientColor = Color.Black.copy(alpha = 0.12f)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = heroShape
                            )
                            .clip(heroShape)
                            .drawWithContent {
                                drawContent()
                                if (triggerCapture) {
                                    val scale = 3.5f
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = selectedStyle,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.97f, animationSpec = tween(200)))
                                    .togetherWith(fadeOut(animationSpec = tween(150)))
                            },
                            label = "NoteCardStyleTransition"
                        ) { targetStyle ->
                            MyApplicationTheme(
                                darkTheme = isPreviewDark,
                                dynamicColor = userDynamicColor,
                                typographyPrefs = typographyPrefs
                            ) {
                                NoteShareCard(
                                    item = item,
                                    noteBody = noteBody,
                                    style = targetStyle,
                                    showAttribution = showAttribution,
                                    tagline = currentTagline
                                )
                            }
                        }
                    }
                },
                sheetContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp)
                            .padding(horizontal = 20.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Control Toolbar Row (Connected Style Switcher, Connected Tagline + Shuffle, Theme Capsule)
                        ShareControlToolbar(
                            styles = NoteShareStyle.entries,
                            selectedStyle = selectedStyle,
                            onStyleSelected = {
                                selectedStyle = it
                                SharePreferencesManager.setNoteShareStyle(context, it)
                            },
                            styleLabel = { it.displayName },
                            styleIcon = {
                                when (it) {
                                    NoteShareStyle.DOCUMENT -> R.drawable.ic_custom_markdown
                                    NoteShareStyle.MINIMAL -> R.drawable.ic_custom_text
                                    NoteShareStyle.COMPACT -> R.drawable.ic_custom_grid
                                }
                            },
                            showAttribution = showAttribution,
                            onToggleAttribution = {
                                val updated = !showAttribution
                                showAttribution = updated
                                SharePreferencesManager.setAttributionEnabled(context, updated)
                            },
                            onShuffleTagline = { currentTagline = CobaltTaglines.getRandom() },
                            isDark = isPreviewDark,
                            onToggleTheme = { isPreviewDark = !isPreviewDark }
                        )

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
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Option 1: Share as Card (High-Resolution 4K Image)
                                ShareActionRowItem(
                                    icon = painterResource(id = R.drawable.ic_custom_image),
                                    title = "Share card image",
                                    subtitle = "High-resolution ${selectedStyle.displayName} visual card",
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
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
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
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
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
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
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
            )
        }
    }
}

/**
 * Custom layout that places the hero preview card so it protrudes above the rounded
 * top edge of the bottom sheet container into the scrim backdrop.
 */
@Composable
private fun FloatingHeroSheetLayout(
    modifier: Modifier = Modifier,
    heroCard: @Composable () -> Unit,
    sheetContent: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            // 0: Sheet Background Surface
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {}

            // 1: Hero Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                heroCard()
            }

            // 2: Sheet Content (Controls + Actions)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                sheetContent()
            }
        }
    ) { measurables, constraints ->
        val cardPlaceable = measurables[1].measure(constraints.copy(minHeight = 0))
        val cardHeight = cardPlaceable.height

        // Top ~52% of card protrudes above the sheet container
        val protrudeAmount = (cardHeight * 0.52f).roundToInt()
        val overlapInSheet = cardHeight - protrudeAmount
        val spacingBelowCard = 16.dp.roundToPx()

        val contentPlaceable = measurables[2].measure(constraints.copy(minHeight = 0))

        val sheetSurfaceHeight = overlapInSheet + spacingBelowCard + contentPlaceable.height
        val sheetSurfacePlaceable = measurables[0].measure(
            Constraints.fixed(constraints.maxWidth, sheetSurfaceHeight)
        )

        val totalHeight = protrudeAmount + sheetSurfaceHeight

        layout(constraints.maxWidth, totalHeight) {
            // 1. Draw sheet background starting at protrudeAmount
            sheetSurfacePlaceable.placeRelative(0, protrudeAmount)

            // 2. Draw hero card floating at top (y = 0)
            val cardX = (constraints.maxWidth - cardPlaceable.width) / 2
            cardPlaceable.placeRelative(cardX, 0)

            // 3. Draw controls and actions below the card
            val contentX = (constraints.maxWidth - contentPlaceable.width) / 2
            val contentY = cardHeight + spacingBelowCard
            contentPlaceable.placeRelative(contentX, contentY)
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

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
import com.example.ui.theme.LocalCobaltTypography
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Material 3 Expressive bottom sheet for sharing and exporting saved image items.
 * Redesigned with a floating hero card that overlaps the top edge of the sheet into the
 * backdrop scrim, connected style switchers, persisted user preferences, and high-resolution export.
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
                    val cardShape = if (selectedStyle == ImageShareStyle.POLAROID) RectangleShape else RoundedCornerShape(20.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                            .padding(horizontal = 20.dp)
                            .shadow(
                                elevation = 14.dp,
                                shape = cardShape,
                                spotColor = Color.Black.copy(alpha = 0.28f),
                                ambientColor = Color.Black.copy(alpha = 0.16f)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = cardShape
                            )
                            .clip(cardShape)
                            .drawWithContent {
                                drawContent()
                                if (triggerCapture) {
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = selectedStyle,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.97f, animationSpec = tween(200)))
                                    .togetherWith(fadeOut(animationSpec = tween(150)))
                            },
                            label = "ImageCardStyleTransition"
                        ) { targetStyle ->
                            MyApplicationTheme(
                                darkTheme = isPreviewDark,
                                dynamicColor = userDynamicColor,
                                typographyPrefs = typographyPrefs
                            ) {
                                ImageShareCard(
                                    item = item,
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
                            styles = ImageShareStyle.entries,
                            selectedStyle = selectedStyle,
                            onStyleSelected = {
                                selectedStyle = it
                                SharePreferencesManager.setImageShareStyle(context, it)
                            },
                            styleLabel = { it.displayName },
                            styleIcon = { if (it == ImageShareStyle.POLAROID) R.drawable.ic_custom_image else R.drawable.ic_custom_grid },
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

        if (trailing != null) {
            trailing()
        }
    }
}

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
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Modern floating-hero bottom sheet for sharing and exporting saved movie and anime items.
 * Features an elevated hero card protruding ~52% above the rounded sheet surface into the dimmed backdrop,
 * removing redundant header clutter and providing direct control pills and high-contrast export actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieShareBottomSheet(
    item: SavedItem,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val systemInDark = isSystemInDarkTheme()

    // Persistent preferences
    val initialStyle = remember { SharePreferencesManager.getMovieShareStyle(context) }
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
    var isCardCopied by remember { mutableStateOf(false) }
    var isDetailsCopied by remember { mutableStateOf(false) }

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 440.dp)
                            .padding(horizontal = 20.dp)
                            .shadow(
                                elevation = 14.dp,
                                shape = RoundedCornerShape(26.dp),
                                spotColor = Color.Black.copy(alpha = 0.28f),
                                ambientColor = Color.Black.copy(alpha = 0.16f)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .clip(RoundedCornerShape(26.dp))
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
                            label = "MovieCardStyleTransition"
                        ) { targetStyle ->
                            MyApplicationTheme(
                                darkTheme = isPreviewDark,
                                dynamicColor = userDynamicColor,
                                typographyPrefs = typographyPrefs
                            ) {
                                MovieShareCard(
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
                            styles = MovieShareStyle.entries,
                            selectedStyle = selectedStyle,
                            onStyleSelected = {
                                selectedStyle = it
                                SharePreferencesManager.setMovieShareStyle(context, it)
                            },
                            styleLabel = { it.displayName },
                            styleIcon = { if (it == MovieShareStyle.THEATRICAL) R.drawable.ic_custom_movie else R.drawable.ic_custom_video },
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
                                // Option 1: Share Movie Card (PNG)
                                ShareActionRowItem(
                                    icon = painterResource(id = R.drawable.ic_custom_image),
                                    title = "Share movie card (4K)",
                                    subtitle = "High-res ${selectedStyle.displayName} cinematic card",
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
                                                    val shareText = buildString {
                                                        append(item.title)
                                                        if (!item.releaseYear.isNullOrBlank()) append(" (${item.releaseYear})")
                                                        if (item.rating != null && item.rating > 0.0) append(" • ★ ${String.format(Locale.US, "%.1f", item.rating)}")
                                                    }
                                                    ShareImageExporter.exportAndShareBitmap(
                                                        context = context,
                                                        bitmap = bitmap,
                                                        linkUrl = shareText,
                                                        chooserTitle = "Share Movie Card"
                                                    )
                                                    onDismissRequest()
                                                } catch (e: Throwable) {
                                                    android.util.Log.e("CobaltShare", "Error exporting movie card", e)
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

                                // Option 2: Share Poster Image Only
                                ShareActionRowItem(
                                    icon = painterResource(id = R.drawable.ic_custom_movie),
                                    title = "Share poster image",
                                    subtitle = "Theatrical 2:3 clean poster artwork",
                                    trailing = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        ShareImageExporter.shareOriginalImage(context, item, "Share Movie Poster")
                                        onDismissRequest()
                                    }
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                                    thickness = 0.5.dp
                                )

                                // Option 3: Copy Movie Card to Clipboard
                                ShareActionRowItem(
                                    icon = painterResource(id = if (isCardCopied) R.drawable.ic_custom_copy_success else R.drawable.ic_custom_copy),
                                    title = if (isCardCopied) "Card copied!" else "Copy movie card",
                                    subtitle = if (isCardCopied) "Copied to clipboard" else "Copy movie card image to clipboard",
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    iconBgColor = if (isCardCopied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    titleColor = if (isCardCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    trailing = if (isCardCopied) {
                                        {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_check),
                                                contentDescription = "Copied",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (isCardCopied) return@ShareActionRowItem
                                        capturedCallback = { bitmap ->
                                            coroutineScope.launch {
                                                ShareImageExporter.copyImageToClipboard(context, bitmap, "Movie Card")
                                                isCardCopied = true
                                                kotlinx.coroutines.delay(650)
                                                onDismissRequest()
                                            }
                                        }
                                        triggerCapture = true
                                    }
                                )

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                                    thickness = 0.5.dp
                                )

                                // Option 4: Copy Movie Details Text
                                val movieDetailsText = buildString {
                                    append(item.title)
                                    if (!item.releaseYear.isNullOrBlank()) append(" (${item.releaseYear})")
                                    if (item.rating != null && item.rating > 0.0) append(" • ★ ${String.format(Locale.US, "%.1f", item.rating)}/10")
                                    val overview = item.extractedText?.takeIf { it.isNotBlank() } ?: item.tagline
                                    if (!overview.isNullOrBlank()) {
                                        appendLine()
                                        appendLine()
                                        append(overview)
                                    }
                                }

                                ShareActionRowItem(
                                    icon = painterResource(id = if (isDetailsCopied) R.drawable.ic_custom_copy_success else R.drawable.ic_custom_text),
                                    title = if (isDetailsCopied) "Details copied!" else "Copy movie details",
                                    subtitle = "${item.title} synopsis & metadata",
                                    trailing = if (isDetailsCopied) {
                                        {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_check),
                                                contentDescription = "Copied",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (isDetailsCopied) return@ShareActionRowItem
                                        isDetailsCopied = true
                                        ShareImageExporter.copyToClipboard(context, movieDetailsText, label = "Movie Details", showToast = false)
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
            )
        }
    }
}

/**
 * Custom 2-pass Layout that elevates the hero card so that its top ~52% protrudes above the
 * rounded bottom sheet container into the dimmed backdrop scrim, eliminating dead space.
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

            // 3. Draw sheet content below card overlap + spacing
            val contentY = protrudeAmount + overlapInSheet + spacingBelowCard
            val contentX = (constraints.maxWidth - contentPlaceable.width) / 2
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

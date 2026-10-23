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

package com.example.ui.dock

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.getBestImagePath
import com.example.data.remote.MediaSearchResultItem
import com.example.data.repository.CobaltRepository
import com.example.util.CobaltCustomTabsLauncher
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SideDockMode {
    MAIN,
    SEARCH
}

private enum class DockInputMode {
    NOTE,
    LINK
}

/**
 * Modern Jetpack Compose UI for CobaltSideDock.
 * Fully GPU-accelerated via RenderThread graphicsLayer transforms,
 * providing 60fps/120fps fluid performance without resizing WindowManager window bounds.
 */
@Composable
fun SideDockContent(
    side: String,
    yPercent: Float,
    handleHeightDp: Int = 100,
    handleThicknessDp: Int = 6,
    handleOpacity: Float = 0.7f,
    animPreset: String,
    repository: CobaltRepository,
    onClose: () -> Unit,
    onCloseFinished: () -> Unit,
    onLaunchOcr: () -> Unit,
    onLaunchLink: () -> Unit = {},
    onOpenMainApp: (itemId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isRightSide = side == "Right"

    // Animation Specs based on selected preset with fluid Oppo-style water-drop spring physics
    val (enterSpec, exitSpec) = remember(animPreset) {
        when (animPreset) {
            "Bouncy" -> Pair(
                spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 320f),
                spring<Float>(dampingRatio = 0.78f, stiffness = 420f)
            )
            "Snappy" -> Pair(
                spring<Float>(dampingRatio = 0.85f, stiffness = 650f),
                spring<Float>(dampingRatio = 0.95f, stiffness = 750f)
            )
            "Fluid" -> Pair(
                // Oppo ColorOS Water-drop elastic spring
                spring<Float>(dampingRatio = 0.68f, stiffness = 320f),
                spring<Float>(dampingRatio = 0.82f, stiffness = 460f)
            )
            "Instant" -> Pair(
                snap<Float>(),
                snap<Float>()
            )
            else -> Pair( // "Smooth" (Default Oppo-like fluid water-drop physics)
                spring<Float>(dampingRatio = 0.72f, stiffness = 360f),
                spring<Float>(dampingRatio = 0.84f, stiffness = 480f)
            )
        }
    }

    val animProgress = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(1f, animationSpec = enterSpec)
    }

    fun dismissDock(onDone: () -> Unit = {}) {
        if (isDismissing) return
        isDismissing = true
        keyboardController?.hide()
        coroutineScope.launch {
            animProgress.animateTo(0f, animationSpec = exitSpec)
            onDone()
            onCloseFinished()
        }
    }

    var currentMode by remember { mutableStateOf(SideDockMode.MAIN) }
    var dockInputMode by remember { mutableStateOf(DockInputMode.NOTE) }
    var noteText by remember { mutableStateOf("") }
    var linkText by remember { mutableStateOf("") }
    val inputFocusRequester = remember { FocusRequester() }

    // Clipboard helper for link mode
    val clipboardText = remember(dockInputMode) {
        clipboardManager.getText()?.text?.trim().orEmpty()
    }
    val hasClipboardUrl = remember(clipboardText) {
        clipboardText.isNotBlank() && (clipboardText.startsWith("http://") || clipboardText.startsWith("https://") || clipboardText.contains("."))
    }

    // Live Recent items from Room Database Flow
    val allItems by repository.getAllItemsFlow().collectAsState(initial = emptyList())
    val recentItems = remember(allItems) {
        allItems.sortedByDescending { it.timestamp }.take(4)
    }

    // Geometry & Dynamic Morphing Bounds
    val screenHeight = configuration.screenHeightDp.dp
    val cardWidth = if (isLandscape) 320.dp else 280.dp
    val cardHeight = if (isLandscape) 300.dp else 450.dp

    val handleCenterY = screenHeight * yPercent
    val handleTop = handleCenterY - (handleHeightDp.dp / 2)

    val targetTop = handleCenterY - (cardHeight / 2)
    val minTop = if (isLandscape) 16.dp else 36.dp
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val availableHeight = screenHeight - imeBottom
    val maxTop = (availableHeight - cardHeight - (if (isLandscape) 16.dp else 36.dp)).coerceAtLeast(minTop)
    val effectiveTop = targetTop.coerceIn(minTop, maxTop)

    // Animated morph parameters
    val p = animProgress.value
    val currentWidth = lerp(handleThicknessDp.dp, cardWidth, p).coerceAtLeast(handleThicknessDp.dp)
    val currentHeight = lerp(handleHeightDp.dp, cardHeight, p).coerceAtLeast(handleHeightDp.dp)
    val currentTop = lerp(handleTop, effectiveTop, p)
    val currentMargin = lerp(0.dp, 12.dp, p.coerceIn(0f, 1f))

    // Morphing Corner Shape (starts as flush handle pill, expands into rounded 24.dp card)
    val innerCornerRadius = lerp(8.dp, 24.dp, p.coerceIn(0f, 1f))
    val outerCornerRadius = lerp(0.dp, 24.dp, p.coerceIn(0f, 1f))
    val morphShape = if (isRightSide) {
        RoundedCornerShape(
            topStart = innerCornerRadius,
            bottomStart = innerCornerRadius,
            topEnd = outerCornerRadius,
            bottomEnd = outerCornerRadius
        )
    } else {
        RoundedCornerShape(
            topEnd = innerCornerRadius,
            bottomEnd = innerCornerRadius,
            topStart = outerCornerRadius,
            bottomStart = outerCornerRadius
        )
    }

    // Morphing Colors & Borders
    val handleAccentColor = Color(0xFF3B82F6).copy(alpha = handleOpacity)
    val dockSurfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = lerp(
        start = handleAccentColor,
        stop = dockSurfaceColor,
        fraction = ((p - 0.08f) / 0.45f).coerceIn(0f, 1f)
    )

    val borderAlpha = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f) * 0.45f

    // Unified submit handler for Note and Link modes
    fun submitInput() {
        if (dockInputMode == DockInputMode.LINK) {
            val rawUrl = linkText.trim()
            if (rawUrl.isBlank()) return
            val cleanUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                "https://$rawUrl"
            } else {
                rawUrl
            }
            val hostName = try {
                java.net.URI(cleanUrl).host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: cleanUrl
            } catch (_: Exception) {
                cleanUrl
            }

            coroutineScope.launch {
                val newItem = SavedItem(
                    title = hostName,
                    content = cleanUrl,
                    type = SavedItemType.LINK,
                    folders = listOf("Links")
                )
                repository.saveItem(newItem)
                WidgetUpdater.update(context)
                Toast.makeText(context, "✓ Link saved", Toast.LENGTH_SHORT).show()
                linkText = ""
                keyboardController?.hide()

                // Asynchronous background OpenGraph metadata extraction
                launch(Dispatchers.IO) {
                    try {
                        val meta = repository.fetchLinkMetadata(cleanUrl)
                        val hasTitle = !meta.title.isNullOrBlank()
                        val hasDesc = !meta.description.isNullOrBlank()
                        val hasImage = !meta.imageUrl.isNullOrBlank()
                        if (hasTitle || hasDesc || hasImage) {
                            val enriched = newItem.copy(
                                title = meta.title?.takeIf { it.isNotBlank() } ?: hostName,
                                linkTitle = meta.title,
                                linkDescription = meta.description,
                                linkImage = meta.imageUrl
                            )
                            repository.saveItem(enriched)
                            WidgetUpdater.update(context)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CobaltSideDock", "Metadata extraction failed: ${e.message}")
                    }
                }
            }
        } else {
            val content = noteText.trim()
            if (content.isBlank()) return
            coroutineScope.launch {
                val lines = content.lines().filter { it.isNotBlank() }
                val title = if (lines.isNotEmpty()) {
                    val first = lines.first().trim()
                    if (first.length > 40) first.take(40) + "..." else first
                } else {
                    "Quick Note"
                }
                val newItem = SavedItem(
                    title = title,
                    content = content,
                    type = SavedItemType.TEXT
                )
                repository.saveItem(newItem)
                WidgetUpdater.update(context)
                Toast.makeText(context, "✓ Note saved", Toast.LENGTH_SHORT).show()
                noteText = ""
                keyboardController?.hide()
            }
        }
    }

    // Full screen container
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. 100% Transparent Outside Touch Interceptor with click to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    dismissDock()
                }
        )

        // 2. Hardware-accelerated Morphing SideDock Card
        val directionMultiplier = if (isRightSide) 1f else -1f

        Surface(
            modifier = Modifier
                .align(if (isRightSide) Alignment.TopEnd else Alignment.TopStart)
                .padding(
                    top = currentTop,
                    start = if (!isRightSide) currentMargin else 0.dp,
                    end = if (isRightSide) currentMargin else 0.dp
                )
                .width(currentWidth)
                .height(currentHeight)
                .clip(morphShape)
                .pointerInput(isRightSide) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        // Swipe toward the screen edge dismisses the dock
                        if (isRightSide && dragAmount > 25f) {
                            dismissDock()
                        } else if (!isRightSide && dragAmount < -25f) {
                            dismissDock()
                        }
                    }
                },
            shape = morphShape,
            color = containerColor,
            border = if (borderAlpha > 0.01f) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha))
            } else null,
            tonalElevation = lerp(2.dp, 6.dp, p.coerceIn(0f, 1f)),
            shadowElevation = lerp(1.dp, 8.dp, p.coerceIn(0f, 1f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isRightSide) Alignment.TopEnd else Alignment.TopStart
            ) {
                // Fixed-dimension inner card content (reveals gracefully without awkward reflows)
                val contentProgress = ((p - 0.22f) / 0.68f).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .graphicsLayer {
                            alpha = contentProgress
                            translationX = directionMultiplier * (1f - contentProgress) * 20.dp.toPx()
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        if (currentMode == SideDockMode.MAIN) {
                            // ════════════ HEADER ════════════
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_tools),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Cobalt SideDock",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { dismissDock() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_close),
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // ════════════ QUICK ACTIONS (5 ICONS) ════════════
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                QuickActionItem(
                                    iconRes = R.drawable.ic_custom_ocr,
                                    label = "OCR",
                                    isSelected = false,
                                    onClick = {
                                        dismissDock { onLaunchOcr() }
                                    }
                                )
                                QuickActionItem(
                                    iconRes = R.drawable.ic_custom_text,
                                    label = "Note",
                                    isSelected = dockInputMode == DockInputMode.NOTE,
                                    onClick = {
                                        dockInputMode = DockInputMode.NOTE
                                        inputFocusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                )
                                QuickActionItem(
                                    iconRes = R.drawable.ic_custom_link,
                                    label = "Link",
                                    isSelected = dockInputMode == DockInputMode.LINK,
                                    onClick = {
                                        dockInputMode = DockInputMode.LINK
                                        inputFocusRequester.requestFocus()
                                        keyboardController?.show()
                                        if (linkText.isBlank() && hasClipboardUrl) {
                                            linkText = clipboardText
                                        }
                                    }
                                )
                                QuickActionItem(
                                    iconRes = R.drawable.ic_custom_movie,
                                    label = "Media",
                                    isSelected = false,
                                    onClick = {
                                        currentMode = SideDockMode.SEARCH
                                    }
                                )
                                QuickActionItem(
                                    iconRes = R.drawable.ic_custom_home,
                                    label = "Home",
                                    isSelected = false,
                                    onClick = {
                                        dismissDock { onOpenMainApp(null) }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // ════════════ INLINE EXPANDABLE QUICK NOTE / LINK ════════════
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(animationSpec = tween(150)),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Leading Mode Icon
                                    Icon(
                                        painter = painterResource(
                                            id = if (dockInputMode == DockInputMode.LINK) R.drawable.ic_custom_link else R.drawable.ic_custom_text
                                        ),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 2.dp)
                                    ) {
                                        val currentText = if (dockInputMode == DockInputMode.LINK) linkText else noteText
                                        if (currentText.isEmpty()) {
                                            Text(
                                                text = if (dockInputMode == DockInputMode.LINK) "Paste or type link..." else "Quick thought...",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp
                                                )
                                            )
                                        }
                                        BasicTextField(
                                            value = currentText,
                                            onValueChange = { newText ->
                                                if (dockInputMode == DockInputMode.LINK) {
                                                    linkText = newText
                                                } else {
                                                    noteText = newText
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(inputFocusRequester),
                                            textStyle = TextStyle(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                lineHeight = 16.sp
                                            ),
                                            singleLine = dockInputMode == DockInputMode.LINK,
                                            minLines = 1,
                                            maxLines = if (dockInputMode == DockInputMode.LINK) 1 else 3,
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = if (dockInputMode == DockInputMode.LINK) KeyboardType.Uri else KeyboardType.Text,
                                                imeAction = if (dockInputMode == DockInputMode.LINK) ImeAction.Done else ImeAction.Default
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    submitInput()
                                                }
                                            )
                                        )
                                    }

                                    // Quick Paste Pill when in Link mode, text is empty, and clipboard has a link
                                    if (dockInputMode == DockInputMode.LINK && linkText.isBlank() && hasClipboardUrl) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .clickable {
                                                    linkText = clipboardText
                                                    inputFocusRequester.requestFocus()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_custom_copy),
                                                    contentDescription = "Paste",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Paste",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Send Button
                                    val isSendable = if (dockInputMode == DockInputMode.LINK) linkText.isNotBlank() else noteText.isNotBlank()
                                    IconButton(
                                        onClick = {
                                            submitInput()
                                        },
                                        enabled = isSendable,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(
                                                if (isSendable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                shape = CircleShape
                                            )
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_send),
                                            contentDescription = if (dockInputMode == DockInputMode.LINK) "Save Link" else "Save Note",
                                            tint = if (isSendable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // ════════════ RECENTS LIST ════════════
                            Text(
                                text = "RECENTS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                            )

                            if (recentItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No recent items yet",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(recentItems, key = { it.id }) { item ->
                                        RecentItemRow(
                                            item = item,
                                            onClick = {
                                                dismissDock {
                                                    if (item.type == SavedItemType.LINK && item.content.startsWith("http")) {
                                                        CobaltCustomTabsLauncher.launch(context, item.content)
                                                    } else {
                                                        onOpenMainApp(item.id)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            // ════════════ SEARCH SUBPAGE ════════════
                            SearchSubpageContent(
                                repository = repository,
                                onBack = { currentMode = SideDockMode.MAIN },
                                onClose = { dismissDock() }
                            )
                        }
                    }
                }

                // During initial elastic pull, show subtle handle pill indicator in center
                if (p < 0.35f) {
                    val pillAlpha = (1f - (p / 0.30f)).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = pillAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(28.dp)
                                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(1.5.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    iconRes: Int,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(13.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun RecentItemRow(
    item: SavedItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (iconRes, iconTint) = when (item.type) {
                SavedItemType.LINK -> Pair(R.drawable.ic_custom_link, Color(0xFF42A5F5))
                SavedItemType.IMAGE -> Pair(R.drawable.ic_custom_image, Color(0xFF66BB6A))
                SavedItemType.VIDEO -> Pair(R.drawable.ic_custom_video, Color(0xFFAB47BC))
                SavedItemType.AUDIO -> Pair(R.drawable.ic_custom_voice, MaterialTheme.colorScheme.primary)
                SavedItemType.MEDIA -> Pair(R.drawable.ic_custom_movie, Color(0xFFE91E63))
                SavedItemType.CODE -> Pair(R.drawable.ic_custom_code, Color(0xFFFFB74D))
                else -> Pair(R.drawable.ic_custom_text, MaterialTheme.colorScheme.primary)
            }

            val thumbUrl = remember(item) { item.getBestImagePath() }

            if (!thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = iconTint.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            val displayTitle = remember(item) {
                if (item.title.isNotBlank() && item.title != "Untitled") {
                    item.title
                } else {
                    when (item.type) {
                        SavedItemType.LINK -> {
                            try {
                                val uri = java.net.URI(item.content)
                                uri.host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: item.content
                            } catch (_: Exception) {
                                item.content.takeIf { it.isNotBlank() } ?: "Saved Link"
                            }
                        }
                        SavedItemType.IMAGE -> "Image Attachment"
                        SavedItemType.VIDEO -> "Video Recording"
                        SavedItemType.AUDIO -> "Voice Memo"
                        SavedItemType.MEDIA -> "Media"
                        SavedItemType.CODE -> "Code Snippet"
                        else -> {
                            val firstLine = item.content.lines().firstOrNull { it.isNotBlank() }?.trim()
                            if (!firstLine.isNullOrBlank()) {
                                if (firstLine.length > 35) firstLine.take(35) + "..." else firstLine
                            } else {
                                "Untitled Note"
                            }
                        }
                    }
                }
            }

            val displaySnippet = remember(item) {
                when {
                    !item.linkDescription.isNullOrBlank() -> item.linkDescription
                    item.type == SavedItemType.LINK -> item.content
                    item.content.startsWith("http://") || item.content.startsWith("https://") -> "Cloud attachment"
                    item.content.startsWith("/data/") || item.content.startsWith("/storage/") -> "Local media file"
                    item.content.isNotBlank() -> {
                        val lines = item.content.lines().filter { it.isNotBlank() }
                        if (lines.size > 1 && item.title == lines.first().trim()) {
                            lines.drop(1).firstOrNull()?.trim() ?: item.content
                        } else {
                            item.content
                        }
                    }
                    else -> ""
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (displaySnippet.isNotBlank()) {
                    Text(
                        text = displaySnippet,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SearchSubpageContent(
    repository: CobaltRepository,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MediaSearchResultItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            searchResults = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(350) // Debounce
        try {
            searchResults = repository.searchMedia(trimmed)
        } catch (_: Exception) {
            searchResults = emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Media & Content Search",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_close),
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Input
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search movies, TV shows, anime...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { query = "" },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_close),
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Results Container
        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (query.isBlank()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Type a title to search...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                )
            }
        } else if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(searchResults, key = { it.id }) { item ->
                    var isSaved by remember { mutableStateOf(false) }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.posterUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(item.posterUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 30.dp, height = 42.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(width = 30.dp, height = 42.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_movie),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                val typeLabel = when (item.mediaType.lowercase()) {
                                    "movie" -> "Movie"
                                    "tv", "tv show", "tv_show" -> "TV"
                                    "anime" -> "Anime"
                                    else -> item.mediaType.replaceFirstChar { it.uppercase() }
                                }
                                val meta = if (!item.releaseYear.isNullOrBlank()) "$typeLabel • ${item.releaseYear}" else typeLabel
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Button(
                                onClick = {
                                    if (isSaved) return@Button
                                    coroutineScope.launch {
                                        try {
                                            val savedItem = SavedItem(
                                                id = item.id,
                                                type = SavedItemType.MEDIA,
                                                title = item.title,
                                                content = item.overview ?: "",
                                                thumbnailPath = item.posterUrl,
                                                backdropUrl = item.backdropUrl,
                                                mediaType = item.mediaType,
                                                watchStatus = "Plan to Watch",
                                                releaseYear = item.releaseYear,
                                                genres = item.genres,
                                                watchProviders = item.watchProviders,
                                                trailerUrl = item.trailerUrl,
                                                rating = item.rating,
                                                folders = listOf("Media")
                                            )
                                            val enriched = repository.enrichMediaItemDetails(savedItem, saveToDb = false)
                                            repository.saveItem(enriched)
                                            WidgetUpdater.update(context)
                                            isSaved = true
                                            Toast.makeText(context, "✓ Saved ${item.title}", Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSaved) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (isSaved) "✓" else "Save",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

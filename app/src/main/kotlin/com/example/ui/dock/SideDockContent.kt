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

import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import android.content.ClipData
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
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
import com.example.ui.screens.parseMarkdown
import com.example.util.CobaltCustomTabsLauncher
import com.example.widget.WidgetUpdater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SideDockMode {
    MAIN,
    HUB,
    DETAIL
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
    handleCenterYDp: Float? = null,
    handleHeightDp: Int = 100,
    handleThicknessDp: Int = 6,
    handleOpacity: Float = 0.7f,
    animPreset: String = "Smooth",
    animStiffness: Float = 340f,
    animDamping: Float = 0.72f,
    useBlur: Boolean = false,
    blurRadius: Int = 25,
    blurOpacity: Float = 0.30f,
    repository: CobaltRepository,
    onRegisterDismiss: ((() -> Unit) -> Unit)? = null,
    onProgressChanged: (Float) -> Unit = {},
    onClose: () -> Unit,
    onCloseFinished: () -> Unit,
    onLaunchOcr: () -> Unit,
    onLaunchLink: () -> Unit = {},
    onOpenMainApp: (itemId: String?, destination: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isRightSide = side == "Right"

    // Animation Specs based on selected preset or custom spring physics
    val (enterSpec, exitSpec) = remember(animPreset, animStiffness, animDamping) {
        when (animPreset) {
            "Bouncy" -> Pair(
                spring<Float>(dampingRatio = 0.58f, stiffness = 320f),
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
            "Custom" -> Pair(
                spring<Float>(
                    dampingRatio = animDamping.coerceIn(0.45f, 1.0f),
                    stiffness = animStiffness.coerceIn(120f, 900f)
                ),
                spring<Float>(
                    dampingRatio = (animDamping + 0.10f).coerceIn(0.55f, 1.0f),
                    stiffness = (animStiffness + 60f).coerceIn(180f, 1000f)
                )
            )
            else -> Pair( // "Smooth" (Default Oppo-like fluid water-drop physics)
                spring<Float>(dampingRatio = 0.72f, stiffness = 340f),
                spring<Float>(dampingRatio = 0.82f, stiffness = 380f)
            )
        }
    }

    val animProgress = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

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
    var selectedItemForDetail by remember { mutableStateOf<SavedItem?>(null) }
    var dockInputMode by remember { mutableStateOf(DockInputMode.NOTE) }
    var noteText by remember { mutableStateOf("") }
    var linkText by remember { mutableStateOf("") }
    val inputFocusRequester = remember { FocusRequester() }

    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    // Predictive back handler for Detail Mode -> return to Main Mode
    PredictiveBackHandler(enabled = currentMode == SideDockMode.DETAIL) { progress ->
        try {
            progress.collect { /* detail predictive progress */ }
            currentMode = SideDockMode.MAIN
            selectedItemForDetail = null
        } catch (_: CancellationException) {
        }
    }

    // Predictive back handler for Hub Mode -> return to Main Mode
    PredictiveBackHandler(enabled = currentMode == SideDockMode.HUB) { progress ->
        try {
            progress.collect { /* hub predictive progress */ }
            currentMode = SideDockMode.MAIN
        } catch (_: CancellationException) {
        }
    }

    // Predictive back handler for Main Mode -> fluid gesture dismiss
    PredictiveBackHandler(enabled = currentMode == SideDockMode.MAIN && !isDismissing) { progress ->
        try {
            progress.collect { backEvent ->
                predictiveBackProgress = backEvent.progress
            }
            predictiveBackProgress = 0f
            dismissDock()
        } catch (e: CancellationException) {
            Animatable(predictiveBackProgress).animateTo(
                0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)
            ) {
                predictiveBackProgress = value
            }
            predictiveBackProgress = 0f
        }
    }

    // Clipboard helper for link mode
    var clipboardText by remember { mutableStateOf("") }
    LaunchedEffect(dockInputMode, clipboard) {
        val entry = clipboard.getClipEntry()
        val text = entry?.clipData?.let { data ->
            if (data.itemCount > 0) data.getItemAt(0).text?.toString()?.trim().orEmpty() else ""
        } ?: ""
        clipboardText = text
    }
    val hasClipboardUrl = remember(clipboardText) {
        clipboardText.isNotBlank() && (clipboardText.startsWith("http://") || clipboardText.startsWith("https://") || clipboardText.contains("."))
    }

    // Live Recent items from Room Database Flow (expanded to first 15 recent items)
    val allItems by repository.getAllItemsFlow().collectAsState(initial = emptyList())
    val recentItems = remember(allItems) {
        allItems.sortedByDescending { it.timestamp }.take(15)
    }

    // Geometry & Dynamic Morphing Bounds
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val cardWidth = if (isLandscape) 330.dp else 280.dp
    val cardHeight = if (isLandscape) (screenHeight - 48.dp).coerceAtMost(330.dp) else 460.dp

    // Dynamically compensate for any ComposeView window screen top offset (e.g. status bar insets)
    val view = LocalView.current
    val density = LocalDensity.current.density
    var viewOffsetY by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val listener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            viewOffsetY = loc[1]
        }
        view.addOnLayoutChangeListener(listener)
        val initialLoc = IntArray(2)
        view.getLocationOnScreen(initialLoc)
        viewOffsetY = initialLoc[1]
        onDispose {
            view.removeOnLayoutChangeListener(listener)
        }
    }

    // Exact handle center Y: measured directly from the physical handle View on screen,
    // converted into local Compose window coordinates
    val rawCenterYDp = handleCenterYDp ?: (screenHeight.value * yPercent)
    val handleCenterY = rawCenterYDp.dp
    val baseTop = handleCenterY - (cardHeight / 2)

    val minTop = if (isLandscape) 12.dp else 36.dp
    val maxTop = (screenHeight - cardHeight - (if (isLandscape) 12.dp else 36.dp)).coerceAtLeast(minTop)
    val effectiveTop = baseTop.coerceIn(minTop, maxTop)

    // Ergonomics & Thumb Reachability:
    // If the dock sits in the upper half of the screen, user's thumb naturally reaches the bottom of the card.
    // In that position, we put Quick Actions and Input Box at the BOTTOM, and Recents above them.
    // If the dock sits in the lower half of the screen, the top of the card is already in thumb reach.
    val isTopDock = (effectiveTop + cardHeight / 2) < (screenHeight / 2)

    // Animated morph parameters
    val p = animProgress.value

    LaunchedEffect(p) {
        onProgressChanged(p)
    }

    LaunchedEffect(Unit) {
        onRegisterDismiss?.invoke {
            dismissDock()
        }
        // Wait 1 frame for initial layout & GPU pipeline warm-up so the spring clock doesn't advance during setup
        withFrameNanos { }
        animProgress.animateTo(1f, animationSpec = enterSpec)
    }

    // Relative vertical shift: starts at handle center, settles at 0 inside window
    val startOffsetY = baseTop - effectiveTop

    val pivotFractionX = if (isRightSide) 1.0f else 0.0f

    // Min scale factors corresponding to the physical handle dimensions
    val minScaleX = (handleThicknessDp.toFloat() / cardWidth.value).coerceIn(0.01f, 0.25f)
    val minScaleY = (handleHeightDp.toFloat() / cardHeight.value).coerceIn(0.05f, 0.45f)

    // Oppo ColorOS Aquamorphic Water-drop squash & stretch:
    // Scale X leads with elastic spring tension, while Scale Y smoothly unfolds
    val pClamped = p.coerceAtLeast(0f)
    val backScale = 1f - (predictiveBackProgress * 0.12f)
    val currentScaleX = ((minScaleX + (1f - minScaleX) * pClamped).coerceAtLeast(minScaleX)) * backScale
    val currentScaleY = ((minScaleY + (1f - minScaleY) * pClamped).coerceAtLeast(minScaleY)) * backScale

    // Edge translation: starts flush against the screen bezel (0dp), smoothly settles at 12dp margin
    val edgeMargin = 12.dp

    // Morphing Corner Shape (starts as flush handle pill against bezel, blossoms into rounded 24.dp card)
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

    // Container alpha for graceful exit handoff
    val containerAlpha = if (isDismissing) (p / 0.04f).coerceIn(0f, 1f) else 1f

    // Seamless Color Morphing:
    // When blur is supported and active, we use a frosted-glass translucent surface tint
    // so the hardware-blurred OS background shines through.
    // If blur is unsupported by hardware or disabled by user settings, we use a 100% SOLID opaque
    // Material 3 container (never transparent!) ensuring pristine contrast and zero visual artifacts.
    val baseSurfaceColor = if (useBlur) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = (blurOpacity + 0.35f).coerceIn(0.45f, 0.82f)
        )
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val handleBlueColor = if (useBlur) {
        Color(0xFF3B82F6).copy(alpha = handleOpacity.coerceIn(0.6f, 1f))
    } else {
        Color(0xFF3B82F6)
    }

    val dockSurfaceColor = lerp(
        start = handleBlueColor,
        stop = baseSurfaceColor,
        fraction = (p / 0.32f).coerceIn(0f, 1f)
    )
    val borderColor = lerp(
        start = Color.Transparent,
        stop = if (useBlur) {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        fraction = p.coerceIn(0f, 1f)
    )

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
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // 1. Outside Touch Interceptor within dock window margins to dismiss
        val effectiveP = (p * (1f - predictiveBackProgress * 0.35f)).coerceIn(0f, 1f)
        val scrimAlpha = (effectiveP * 0.12f).coerceIn(0f, 0.12f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    dismissDock()
                }
        )

        // 2. Hardware-accelerated Morphing SideDock Card
        val directionMultiplier = if (isRightSide) 1f else -1f
        val backTranslationX = cardWidth.value * density * 0.35f * predictiveBackProgress * directionMultiplier

        Surface(
            modifier = Modifier
                .align(if (isRightSide) Alignment.TopEnd else Alignment.TopStart)
                .padding(
                    start = if (!isRightSide) edgeMargin else 0.dp,
                    end = if (isRightSide) edgeMargin else 0.dp,
                    top = effectiveTop
                )
                .width(cardWidth)
                .height(cardHeight)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(pivotFractionX, 0.5f)
                    scaleX = currentScaleX
                    scaleY = currentScaleY
                    translationX = (directionMultiplier * (1f - p.coerceIn(0f, 1f)) * edgeMargin.toPx()) + backTranslationX
                    translationY = (startOffsetY * (1f - p.coerceIn(0f, 1f))).toPx()
                    alpha = containerAlpha
                    shape = morphShape
                    clip = true
                }
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
            color = dockSurfaceColor,
            border = BorderStroke(1.dp, borderColor),
            tonalElevation = if (useBlur) 0.dp else 4.dp,
            shadowElevation = lerp(0.dp, if (useBlur) 12.dp else 6.dp, p.coerceIn(0f, 1f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isRightSide) Alignment.TopEnd else Alignment.TopStart
            ) {
                if (useBlur) {
                    // Delicate top glass specular sheen for frosted glass realism
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.05f * p.coerceIn(0f, 1f)),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Fixed-dimension inner card content (reveals gracefully without awkward reflows)
                val contentProgress = ((p - 0.38f) / 0.62f).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = contentProgress
                            translationX = directionMultiplier * (1f - contentProgress) * 16.dp.toPx()
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        // ── MODULAR REUSABLE UI BLOCKS FOR REACHABILITY UX ──
                        val dockHeader = @Composable {
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
                        }

                        val dockQuickActions = @Composable {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                QuickActionItem(
                                    iconRes = R.drawable.ic_custom_ocr,
                                    label = "OCR",
                                    isSelected = false,
                                    useBlur = useBlur,
                                    onClick = {
                                        dismissDock { onLaunchOcr() }
                                    }
                                )
                                QuickActionItem(
                                    iconRes = R.drawable.ic_custom_text,
                                    label = "Note",
                                    isSelected = dockInputMode == DockInputMode.NOTE,
                                    useBlur = useBlur,
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
                                    useBlur = useBlur,
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
                                    label = "Hub",
                                    isSelected = currentMode == SideDockMode.HUB,
                                    useBlur = useBlur,
                                    onClick = {
                                        currentMode = SideDockMode.HUB
                                    }
                                )
                                QuickActionItem(
                                    iconRes = R.drawable.ic_custom_home,
                                    label = "Home",
                                    isSelected = false,
                                    useBlur = useBlur,
                                    onClick = {
                                        dismissDock { onOpenMainApp(null, null) }
                                    }
                                )
                            }
                        }

                        val dockInputField = @Composable {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(animationSpec = tween(150)),
                                shape = RoundedCornerShape(14.dp),
                                color = if (useBlur) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceContainer,
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
                        }

                        val dockRecentsSection: @Composable ColumnScope.() -> Unit = {
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
                                            useBlur = useBlur,
                                            onClick = {
                                                selectedItemForDetail = item
                                                currentMode = SideDockMode.DETAIL
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        when (currentMode) {
                            SideDockMode.MAIN -> {
                                if (isTopDock) {
                                    // ═════════ TOP DOCK REACHABILITY: Quick Actions at Bottom ═════════
                                    dockHeader()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    dockRecentsSection()
                                    Spacer(modifier = Modifier.height(10.dp))
                                    dockInputField()
                                    Spacer(modifier = Modifier.height(10.dp))
                                    dockQuickActions()
                                } else {
                                    // ═════════ BOTTOM DOCK REACHABILITY: Quick Actions at Top ═════════
                                    dockHeader()
                                    Spacer(modifier = Modifier.height(10.dp))
                                    dockQuickActions()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    dockInputField()
                                    Spacer(modifier = Modifier.height(10.dp))
                                    dockRecentsSection()
                                }
                            }
                            SideDockMode.HUB -> {
                                // ════════════ MEDIA HUB SUBPAGE ════════════
                                MediaHubSubpageContent(
                                    repository = repository,
                                    isTopDock = isTopDock,
                                    useBlur = useBlur,
                                    onBack = { currentMode = SideDockMode.MAIN },
                                    onClose = { dismissDock() },
                                    onOpenFullHub = {
                                        dismissDock { onOpenMainApp(null, "media_hub") }
                                    },
                                    onOpenItemInApp = { mediaId ->
                                        dismissDock { onOpenMainApp(mediaId, "media_hub") }
                                    }
                                )
                            }
                            SideDockMode.DETAIL -> {
                                // ════════════ INLINE DETAIL VIEWER ════════════
                                selectedItemForDetail?.let { item ->
                                    DockItemDetailContent(
                                        item = item,
                                        isTopDock = isTopDock,
                                        useBlur = useBlur,
                                        onBack = {
                                            currentMode = SideDockMode.MAIN
                                            selectedItemForDetail = null
                                        },
                                        onClose = { dismissDock() },
                                        onOpenInApp = {
                                            dismissDock { onOpenMainApp(item.id, null) }
                                        },
                                        onOpenLink = { url ->
                                            dismissDock {
                                                CobaltCustomTabsLauncher.launch(context, url)
                                            }
                                        }
                                    )
                                } ?: run {
                                    currentMode = SideDockMode.MAIN
                                }
                            }
                        }
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
    useBlur: Boolean = false,
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
            color = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (useBlur) {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.70f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
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

private fun isRawUrlOrPath(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val t = text.trim()
    return t.startsWith("/") ||
            t.startsWith("file://") ||
            t.startsWith("content://") ||
            t.startsWith("https://firebasestorage.") ||
            t.startsWith("http://") ||
            t.startsWith("https://")
}

private fun getCleanItemTitle(item: SavedItem): String {
    val rawTitle = item.title.trim()
    if (rawTitle.isNotBlank() && !isRawUrlOrPath(rawTitle)) {
        return rawTitle
    }
    // Title is blank or a raw file path / storage url
    if (item.type == SavedItemType.LINK && !item.linkTitle.isNullOrBlank() && !isRawUrlOrPath(item.linkTitle)) {
        return item.linkTitle.trim()
    }
    // Exactly matches the home card default in ArchiveCards.kt and DetailScreen.kt
    return "Untitled Item"
}

private fun getCleanItemDescription(item: SavedItem): String {
    return when (item.type) {
        SavedItemType.IMAGE, SavedItemType.VIDEO, SavedItemType.AUDIO -> {
            // For images and videos, NEVER show raw file paths or storage URLs!
            // Only show clean extracted OCR text / transcription if available.
            item.extractedText?.trim().orEmpty()
        }
        SavedItemType.LINK -> {
            if (!item.linkDescription.isNullOrBlank() && !isRawUrlOrPath(item.linkDescription)) {
                item.linkDescription.trim()
            } else {
                try {
                    val uri = java.net.URI(item.content)
                    uri.host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: ""
                } catch (_: Exception) {
                    ""
                }
            }
        }
        SavedItemType.MEDIA -> {
            if (!item.content.isNullOrBlank() && !isRawUrlOrPath(item.content)) {
                item.content.trim()
            } else {
                ""
            }
        }
        SavedItemType.TEXT, SavedItemType.CODE -> {
            val cleanTitle = getCleanItemTitle(item)
            val lines = item.content.lines().filter { it.isNotBlank() }
            if (lines.size > 1 && cleanTitle == lines.first().trim()) {
                lines.drop(1).joinToString(" ").trim()
            } else if (item.content.trim() != cleanTitle) {
                item.content.trim()
            } else {
                ""
            }
        }
    }
}

@Composable
private fun RecentItemRow(
    item: SavedItem,
    useBlur: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val (iconRes, iconTint) = when (item.type) {
        SavedItemType.LINK -> Pair(R.drawable.ic_custom_link, Color(0xFF42A5F5))
        SavedItemType.IMAGE -> Pair(R.drawable.ic_custom_image, Color(0xFF66BB6A))
        SavedItemType.VIDEO -> Pair(R.drawable.ic_custom_video, Color(0xFFAB47BC))
        SavedItemType.AUDIO -> Pair(R.drawable.ic_custom_voice, MaterialTheme.colorScheme.primary)
        SavedItemType.MEDIA -> Pair(R.drawable.ic_custom_movie, Color(0xFFE91E63))
        SavedItemType.CODE -> Pair(R.drawable.ic_custom_code, Color(0xFFFFB74D))
        else -> Pair(R.drawable.ic_custom_text, MaterialTheme.colorScheme.primary)
    }

    val thumbUrl = remember(item) { item.getBestImagePath() ?: item.linkImage }
    val displayTitle = remember(item) { getCleanItemTitle(item) }
    val displayDescription = remember(item) { getCleanItemDescription(item) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (useBlur) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = iconTint.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (displayDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayDescription,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val oneMinute = 60 * 1000L
    val oneHour = 60 * oneMinute
    val oneDay = 24 * oneHour

    return when {
        diff < 0L -> "Just now"
        diff < oneMinute -> "Just now"
        diff < oneHour -> "${diff / oneMinute}m ago"
        diff < oneDay -> "${diff / oneHour}h ago"
        diff < 7 * oneDay -> "${diff / oneDay}d ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun DockItemDetailContent(
    item: SavedItem,
    isTopDock: Boolean,
    useBlur: Boolean = false,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenInApp: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val detailContainerColor = if (useBlur) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceContainer

    val (iconRes, iconTint, typeName) = when (item.type) {
        SavedItemType.LINK -> Triple(R.drawable.ic_custom_link, Color(0xFF42A5F5), item.type.displayName)
        SavedItemType.IMAGE -> Triple(R.drawable.ic_custom_image, Color(0xFF66BB6A), item.type.displayName)
        SavedItemType.VIDEO -> Triple(R.drawable.ic_custom_video, Color(0xFFAB47BC), item.type.displayName)
        SavedItemType.AUDIO -> Triple(R.drawable.ic_custom_voice, MaterialTheme.colorScheme.primary, item.type.displayName)
        SavedItemType.MEDIA -> Triple(R.drawable.ic_custom_movie, Color(0xFFE91E63), item.type.displayName)
        SavedItemType.CODE -> Triple(R.drawable.ic_custom_code, Color(0xFFFFB74D), item.type.displayName)
        else -> Triple(R.drawable.ic_custom_text, MaterialTheme.colorScheme.primary, item.type.displayName)
    }

    val displayTitle = remember(item) { getCleanItemTitle(item) }

    val copyContent = {
        val textToCopy = when {
            item.type == SavedItemType.LINK -> item.content
            !item.extractedText.isNullOrBlank() -> item.extractedText
            else -> item.content
        }
        coroutineScope.launch {
            val clipData = ClipData.newPlainText("Copied content", textToCopy)
            clipboard.setClipEntry(clipData.toClipEntry())
            Toast.makeText(context, "✓ Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    val shareContent = {
        onClose()
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, if (item.type == SavedItemType.LINK) item.content else item.extractedText ?: item.content)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ════════════ HEADER (CLEAN & NON-DUPLICATED) ════════════
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

            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = typeName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = iconTint,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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

        Spacer(modifier = Modifier.height(10.dp))

        // ════════════ SCROLLABLE CONTENT BODY ════════════
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Title
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            // Metadata row
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(item.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                )
                if (item.folders.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${item.folders.first()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // Visual attachment or link preview if present
            val thumbUrl = item.getBestImagePath() ?: item.linkImage
            if (!thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Adaptive content representation:
            when (item.type) {
                SavedItemType.IMAGE, SavedItemType.VIDEO, SavedItemType.AUDIO -> {
                    // NEVER show raw file paths or storage URLs!
                    // Only show clean extracted text / transcription if present:
                    if (!item.extractedText.isNullOrBlank()) {
                        Text(
                            text = if (item.type == SavedItemType.AUDIO) "TRANSCRIPT" else "EXTRACTED TEXT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = detailContainerColor,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    text = parseMarkdown(
                                        text = item.extractedText.trim(),
                                        primaryColor = MaterialTheme.colorScheme.primary,
                                        onLinkClick = onOpenLink
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    ),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                    // If no extracted text, keep it clean and empty (no raw path dumped!)
                }

                SavedItemType.LINK -> {
                    val host = try { java.net.URI(item.content).host?.removePrefix("www.") } catch (_: Exception) { null }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = detailContainerColor,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenLink(item.content)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_globe),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = host ?: "Visit Link",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_link_external),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    if (!item.linkDescription.isNullOrBlank() && !isRawUrlOrPath(item.linkDescription)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = parseMarkdown(
                                text = item.linkDescription.trim(),
                                primaryColor = MaterialTheme.colorScheme.primary,
                                onLinkClick = onOpenLink
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }
                }

                SavedItemType.MEDIA -> {
                    if (item.genres.isNotEmpty() || !item.releaseYear.isNullOrBlank()) {
                        val genreMeta = buildList {
                            if (!item.releaseYear.isNullOrBlank()) add(item.releaseYear)
                            if (item.rating != null && item.rating > 0) add("★ ${String.format(Locale.US, "%.1f", item.rating)}")
                            addAll(item.genres.take(2))
                        }.joinToString(" • ")

                        Text(
                            text = genreMeta,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (item.content.isNotBlank() && !isRawUrlOrPath(item.content)) {
                        Text(
                            text = parseMarkdown(
                                text = item.content.trim(),
                                primaryColor = MaterialTheme.colorScheme.primary,
                                onLinkClick = onOpenLink
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }
                }

                SavedItemType.TEXT, SavedItemType.CODE -> {
                    if (item.content.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = parseMarkdown(
                                    text = item.content,
                                    primaryColor = MaterialTheme.colorScheme.primary,
                                    onLinkClick = onOpenLink
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }

                    if (!item.extractedText.isNullOrBlank() && item.extractedText != item.content) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "EXTRACTED TEXT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = detailContainerColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    text = parseMarkdown(
                                        text = item.extractedText.trim(),
                                        primaryColor = MaterialTheme.colorScheme.primary,
                                        onLinkClick = onOpenLink
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    ),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // ════════════ FOOTER ACTION BAR (CLEAN, ADAPTIVE & IN THUMB REACH) ════════════
        val hasTextToCopy = when (item.type) {
            SavedItemType.TEXT, SavedItemType.CODE -> item.content.isNotBlank()
            SavedItemType.IMAGE, SavedItemType.VIDEO, SavedItemType.AUDIO -> !item.extractedText.isNullOrBlank()
            SavedItemType.LINK -> item.content.isNotBlank()
            SavedItemType.MEDIA -> item.content.isNotBlank()
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = detailContainerColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Adaptive Action 1: Copy (only when meaningful!)
                if (hasTextToCopy) {
                    TextButton(
                        onClick = { copyContent() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_copy),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val copyLabel = when (item.type) {
                            SavedItemType.LINK -> "Copy Link"
                            SavedItemType.IMAGE, SavedItemType.VIDEO -> "Copy OCR"
                            SavedItemType.AUDIO -> "Copy Transcript"
                            else -> "Copy"
                        }
                        Text(copyLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Adaptive Action 2: Open Link (if link), Hub (if media), or Share
                if (item.type == SavedItemType.LINK && item.content.startsWith("http")) {
                    TextButton(
                        onClick = {
                            onOpenLink(item.content)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_globe),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Link", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (item.type == SavedItemType.MEDIA) {
                    TextButton(
                        onClick = onOpenInApp,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_movie),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hub", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(
                        onClick = { shareContent() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_share),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Adaptive Action 3: Open in App
                TextButton(
                    onClick = onOpenInApp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_link_external),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("In App", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MediaHubSubpageContent(
    repository: CobaltRepository,
    isTopDock: Boolean,
    useBlur: Boolean = false,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenFullHub: () -> Unit,
    onOpenItemInApp: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hubContainerColor = if (useBlur) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceContainer
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

    val searchHeader = @Composable {
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
            Icon(
                painter = painterResource(id = R.drawable.ic_custom_movie),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Media Hub",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onOpenFullHub,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_link_external),
                    contentDescription = "Open Full Media Hub",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
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
    }

    val searchInputBar = @Composable {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (useBlur) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_search),
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search movies, TV shows, anime...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                fontSize = 12.5.sp,
                                lineHeight = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        )
                    )
                }
                if (isLoading) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (query.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { query = "" },
                        modifier = Modifier
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape)
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
    }

    val searchResultsContent: @Composable ColumnScope.() -> Unit = {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
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
                                    } catch (_: Exception) {}
                                    onOpenItemInApp(item.id)
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = hubContainerColor,
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (isTopDock) {
            // ═════════ TOP DOCK REACHABILITY: Search Bar at Bottom ═════════
            searchHeader()
            Spacer(modifier = Modifier.height(6.dp))
            searchResultsContent()
            Spacer(modifier = Modifier.height(8.dp))
            searchInputBar()
        } else {
            // ═════════ BOTTOM DOCK REACHABILITY: Search Bar at Top ═════════
            searchHeader()
            Spacer(modifier = Modifier.height(8.dp))
            searchInputBar()
            Spacer(modifier = Modifier.height(8.dp))
            searchResultsContent()
        }
    }
}

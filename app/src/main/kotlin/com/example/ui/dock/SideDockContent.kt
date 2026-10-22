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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.remote.MediaSearchResultItem
import com.example.data.repository.CobaltRepository
import com.example.util.CobaltCustomTabsLauncher
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SideDockMode {
    MAIN,
    SEARCH
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
    animPreset: String,
    repository: CobaltRepository,
    onClose: () -> Unit,
    onCloseFinished: () -> Unit,
    onLaunchOcr: () -> Unit,
    onLaunchLink: () -> Unit,
    onOpenMainApp: (itemId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isRightSide = side == "Right"

    // Animation Specs based on selected preset
    val (enterSpec, exitSpec) = remember(animPreset) {
        when (animPreset) {
            "Bouncy" -> Pair(
                spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
            )
            "Snappy" -> Pair(
                tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing),
                tween<Float>(durationMillis = 150, easing = FastOutLinearInEasing)
            )
            "Fluid" -> Pair(
                tween<Float>(durationMillis = 420, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
                tween<Float>(durationMillis = 260, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
            )
            "Instant" -> Pair(
                snap<Float>(),
                snap<Float>()
            )
            else -> Pair( // "Smooth"
                spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
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
    var noteText by remember { mutableStateOf("") }
    val noteFocusRequester = remember { FocusRequester() }

    // Live Recent items from Room Database Flow
    val allItems by repository.getAllItemsFlow().collectAsState(initial = emptyList())
    val recentItems = remember(allItems) {
        allItems.sortedByDescending { it.timestamp }.take(4)
    }

    // Full screen container
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // 1. Dimmed Backdrop / Scrim with click to dismiss
        val backdropAlpha = (animProgress.value * 0.35f).coerceIn(0f, 0.35f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    dismissDock()
                }
        )

        // 2. Hardware-accelerated SideDock Card
        val slideDistancePx = with(density) { 340.dp.toPx() }
        val directionMultiplier = if (isRightSide) 1f else -1f

        val cardWidth = if (isLandscape) 320.dp else 280.dp
        val cardHeight = if (isLandscape) 300.dp else 450.dp

        Surface(
            modifier = Modifier
                .align(if (isRightSide) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(
                    start = if (!isRightSide) 12.dp else 0.dp,
                    end = if (isRightSide) 12.dp else 0.dp
                )
                .width(cardWidth)
                .height(cardHeight)
                .graphicsLayer {
                    translationX = directionMultiplier * (1f - animProgress.value) * slideDistancePx
                    alpha = animProgress.value.coerceIn(0f, 1f)
                    scaleX = 0.94f + 0.06f * animProgress.value
                    scaleY = 0.94f + 0.06f * animProgress.value
                    clip = true
                    shape = RoundedCornerShape(24.dp)
                    shadowElevation = 8.dp.toPx()
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
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            tonalElevation = 6.dp
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
                                    painter = painterResource(id = R.drawable.ic_custom_school),
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
                            onClick = {
                                dismissDock { onLaunchOcr() }
                            }
                        )
                        QuickActionItem(
                            iconRes = R.drawable.ic_custom_text,
                            label = "Note",
                            onClick = {
                                noteFocusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        )
                        QuickActionItem(
                            iconRes = R.drawable.ic_custom_movie,
                            label = "Search",
                            onClick = {
                                currentMode = SideDockMode.SEARCH
                            }
                        )
                        QuickActionItem(
                            iconRes = R.drawable.ic_custom_link,
                            label = "Link",
                            onClick = {
                                dismissDock { onLaunchLink() }
                            }
                        )
                        QuickActionItem(
                            iconRes = R.drawable.ic_custom_home,
                            label = "Cobalt",
                            onClick = {
                                dismissDock { onOpenMainApp(null) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ════════════ INLINE QUICK NOTE ════════════
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (noteText.isEmpty()) {
                                    Text(
                                        text = "Quick thought...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                                BasicTextField(
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(noteFocusRequester),
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        val content = noteText.trim()
                                        if (content.isNotBlank()) {
                                            coroutineScope.launch {
                                                val newItem = SavedItem(
                                                    title = "Quick Dock Note",
                                                    content = content,
                                                    type = SavedItemType.TEXT
                                                )
                                                repository.saveItem(newItem)
                                                WidgetUpdater.update(context)
                                                Toast.makeText(context, "✓ Saved to Cobalt", Toast.LENGTH_SHORT).show()
                                                noteText = ""
                                                keyboardController?.hide()
                                            }
                                        }
                                    })
                                )
                            }

                            IconButton(
                                onClick = {
                                    val content = noteText.trim()
                                    if (content.isNotBlank()) {
                                        coroutineScope.launch {
                                            val newItem = SavedItem(
                                                title = "Quick Dock Note",
                                                content = content,
                                                type = SavedItemType.TEXT
                                            )
                                            repository.saveItem(newItem)
                                            WidgetUpdater.update(context)
                                            Toast.makeText(context, "✓ Saved to Cobalt", Toast.LENGTH_SHORT).show()
                                            noteText = ""
                                            keyboardController?.hide()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_send),
                                    contentDescription = "Save Note",
                                    tint = MaterialTheme.colorScheme.onPrimary,
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
    }
}

@Composable
private fun QuickActionItem(
    iconRes: Int,
    label: String,
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
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun RecentItemRow(
    item: SavedItem,
    onClick: () -> Unit
) {
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
                else -> Pair(R.drawable.ic_custom_text, MaterialTheme.colorScheme.primary)
            }

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

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.title.isNotBlank()) item.title else "Untitled",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.content.isNotBlank()) {
                    Text(
                        text = item.content,
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

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

package com.example.ui.overlay

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.remote.MediaSearchResultItem
import com.example.data.repository.CobaltRepository
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class OverlayState {
    COLLAPSED,
    EXPANDED
}

private enum class OverlayPage {
    MAIN,
    MEDIA_SEARCH
}

/**
 * Unified Morphing Overlay:
 * The edge handle and the expanded panel are the EXACT SAME Compose Surface.
 * It morphs continuously in width, height, corner radius, margin, elevation, and color
 * using Jetpack Compose 1.7 spring physics.
 */
@Composable
fun UnifiedOverlay(
    overlayState: OverlayState,
    side: String,
    yPercent: Float,
    thickness: Int,
    handleHeight: Int,
    opacity: Float,
    animPreset: String,
    repository: CobaltRepository,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onCollapseFinished: () -> Unit,
    onLaunchOcr: () -> Unit,
    onLaunchLinkCapture: () -> Unit,
    onOpenMainApp: (itemId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isHighPosition = yPercent < 0.5f

    // Spring physics configuration
    val dampingRatio = remember(animPreset) {
        when (animPreset) {
            "Bouncy" -> Spring.DampingRatioMediumBouncy
            "Snappy" -> Spring.DampingRatioNoBouncy
            "Fluid" -> 0.82f
            "Instant" -> Spring.DampingRatioNoBouncy
            else -> Spring.DampingRatioLowBouncy // "Smooth" and default
        }
    }

    val stiffness = remember(animPreset) {
        when (animPreset) {
            "Bouncy" -> Spring.StiffnessMediumLow
            "Snappy" -> Spring.StiffnessMedium
            "Fluid" -> Spring.StiffnessLow
            "Instant" -> Spring.StiffnessHigh
            else -> Spring.StiffnessMediumLow
        }
    }

    val springDpSpec = remember(dampingRatio, stiffness) {
        spring<Dp>(dampingRatio = dampingRatio, stiffness = stiffness)
    }

    val springFloatSpec = remember(dampingRatio, stiffness) {
        spring<Float>(dampingRatio = dampingRatio, stiffness = stiffness)
    }

    // Unified morph transition
    val transition = updateTransition(targetState = overlayState, label = "UnifiedOverlayTransition")

    // Notify caller when collapse animation is fully settled
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (transition.currentState == OverlayState.COLLAPSED && transition.targetState == OverlayState.COLLAPSED) {
            onCollapseFinished()
        }
    }

    val expandedWidth = if (isLandscape) 300.dp else 260.dp
    val expandedHeight = if (isLandscape) 280.dp else 400.dp

    // Morph Dimensions
    val morphWidth by transition.animateDp(
        transitionSpec = { springDpSpec },
        label = "MorphWidth"
    ) { state ->
        if (state == OverlayState.EXPANDED) expandedWidth else thickness.dp
    }

    val morphHeight by transition.animateDp(
        transitionSpec = { springDpSpec },
        label = "MorphHeight"
    ) { state ->
        if (state == OverlayState.EXPANDED) expandedHeight else handleHeight.dp
    }

    val morphMargin by transition.animateDp(
        transitionSpec = { springDpSpec },
        label = "MorphMargin"
    ) { state ->
        if (state == OverlayState.EXPANDED) 12.dp else 0.dp
    }

    val morphCornerRadius by transition.animateDp(
        transitionSpec = { springDpSpec },
        label = "MorphCornerRadius"
    ) { state ->
        if (state == OverlayState.EXPANDED) 22.dp else 8.dp
    }

    val morphElevation by transition.animateDp(
        transitionSpec = { springDpSpec },
        label = "MorphElevation"
    ) { state ->
        if (state == OverlayState.EXPANDED) 8.dp else 0.dp
    }

    // Colors
    val surfaceColor = MaterialTheme.colorScheme.surface
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = opacity)

    val morphBgColor by transition.animateColor(
        transitionSpec = { spring(stiffness = stiffness) },
        label = "MorphBgColor"
    ) { state ->
        if (state == OverlayState.EXPANDED) surfaceColor else accentColor
    }

    // Content Alpha: Fades in during expand, fades out immediately on collapse
    val contentAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState == OverlayState.EXPANDED) {
                spring(stiffness = Spring.StiffnessMedium)
            } else {
                spring(stiffness = Spring.StiffnessHigh)
            }
        },
        label = "ContentAlpha"
    ) { state ->
        if (state == OverlayState.EXPANDED) 1f else 0f
    }

    val safeMargin = morphMargin.coerceAtLeast(0.dp)
    val safeWidth = morphWidth.coerceAtLeast(1.dp)
    val safeHeight = morphHeight.coerceAtLeast(1.dp)
    val safeRadius = morphCornerRadius.coerceAtLeast(0.dp)
    val safeElevation = morphElevation.coerceAtLeast(0.dp)
    val safeContentAlpha = contentAlpha.coerceIn(0f, 1f)

    // Morphing corners: flat on screen edge when collapsed, fully rounded when expanded
    val cornerTopStart = if (side == "Right") safeRadius else if (overlayState == OverlayState.EXPANDED) safeRadius else 0.dp
    val cornerBottomStart = if (side == "Right") safeRadius else if (overlayState == OverlayState.EXPANDED) safeRadius else 0.dp
    val cornerTopEnd = if (side == "Right") if (overlayState == OverlayState.EXPANDED) safeRadius else 0.dp else safeRadius
    val cornerBottomEnd = if (side == "Right") if (overlayState == OverlayState.EXPANDED) safeRadius else 0.dp else safeRadius

    val morphShape = RoundedCornerShape(
        topStart = cornerTopStart,
        bottomStart = cornerBottomStart,
        topEnd = cornerTopEnd,
        bottomEnd = cornerBottomEnd
    )

    var currentPage by remember { mutableStateOf(OverlayPage.MAIN) }

    LaunchedEffect(overlayState) {
        if (overlayState == OverlayState.COLLAPSED) {
            currentPage = OverlayPage.MAIN
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (overlayState == OverlayState.COLLAPSED) {
                    Modifier.clickable { onExpand() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = if (side == "Right") Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier
                .padding(
                    start = if (side == "Right") 0.dp else safeMargin,
                    end = if (side == "Right") safeMargin else 0.dp
                )
                .width(safeWidth)
                .height(safeHeight),
            shape = morphShape,
            color = morphBgColor,
            tonalElevation = if (overlayState == OverlayState.EXPANDED) 6.dp else 0.dp,
            shadowElevation = safeElevation,
            border = if (overlayState == OverlayState.EXPANDED && safeContentAlpha > 0.1f) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = safeContentAlpha * 0.6f))
            } else null
        ) {
            // Panel contents are visible only when expanding/expanded
            if (contentAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = {
                            if (targetState == OverlayPage.MEDIA_SEARCH) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "SubpageTransition"
                    ) { page ->
                        when (page) {
                            OverlayPage.MAIN -> {
                                MainOverlayPage(
                                    isHighPosition = isHighPosition,
                                    repository = repository,
                                    onDismiss = onDismiss,
                                    onLaunchOcr = onLaunchOcr,
                                    onOpenMediaSearch = { currentPage = OverlayPage.MEDIA_SEARCH },
                                    onLaunchLinkCapture = onLaunchLinkCapture,
                                    onOpenMainApp = onOpenMainApp
                                )
                            }
                            OverlayPage.MEDIA_SEARCH -> {
                                MediaSearchOverlayPage(
                                    repository = repository,
                                    onBack = { currentPage = OverlayPage.MAIN },
                                    onClose = onDismiss
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainOverlayPage(
    isHighPosition: Boolean,
    repository: CobaltRepository,
    onDismiss: () -> Unit,
    onLaunchOcr: () -> Unit,
    onOpenMediaSearch: () -> Unit,
    onLaunchLinkCapture: () -> Unit,
    onOpenMainApp: (itemId: String?) -> Unit
) {
    val noteFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // Header
        OverlayHeader(onClose = onDismiss)

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic ergonomic thumb-reachable layout
        if (isHighPosition) {
            Text(
                text = "RECENTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
            )

            RecentItemsList(
                repository = repository,
                onDismiss = onDismiss,
                onOpenMainApp = onOpenMainApp,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            QuickActionsGrid(
                onOcrClick = {
                    onDismiss()
                    onLaunchOcr()
                },
                onNoteClick = { noteFocusRequester.requestFocus() },
                onMediaClick = onOpenMediaSearch,
                onLinkClick = {
                    onDismiss()
                    onLaunchLinkCapture()
                },
                onOpenClick = {
                    onDismiss()
                    onOpenMainApp(null)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            QuickNoteInputBar(
                repository = repository,
                focusRequester = noteFocusRequester
            )
        } else {
            QuickActionsGrid(
                onOcrClick = {
                    onDismiss()
                    onLaunchOcr()
                },
                onNoteClick = { noteFocusRequester.requestFocus() },
                onMediaClick = onOpenMediaSearch,
                onLinkClick = {
                    onDismiss()
                    onLaunchLinkCapture()
                },
                onOpenClick = {
                    onDismiss()
                    onOpenMainApp(null)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            QuickNoteInputBar(
                repository = repository,
                focusRequester = noteFocusRequester
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "RECENTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
            )

            RecentItemsList(
                repository = repository,
                onDismiss = onDismiss,
                onOpenMainApp = onOpenMainApp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OverlayHeader(
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_school),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Cobalt",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .clickable { onClose() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_close),
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onOcrClick: () -> Unit,
    onNoteClick: () -> Unit,
    onMediaClick: () -> Unit,
    onLinkClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    data class QuickActionItem(
        val iconRes: Int,
        val label: String,
        val onClick: () -> Unit
    )

    val actions = remember {
        listOf(
            QuickActionItem(R.drawable.ic_custom_ocr, "OCR", onOcrClick),
            QuickActionItem(R.drawable.ic_custom_text, "Note", onNoteClick),
            QuickActionItem(R.drawable.ic_custom_movie, "Cobalt", onMediaClick),
            QuickActionItem(R.drawable.ic_custom_link, "Link", onLinkClick),
            QuickActionItem(R.drawable.ic_custom_home, "Open", onOpenClick)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { action.onClick() }
                    .padding(vertical = 4.dp, horizontal = 1.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = action.iconRes),
                            contentDescription = action.label,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QuickNoteInputBar(
    repository: CobaltRepository,
    focusRequester: FocusRequester
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var noteText by remember { mutableStateOf("") }

    fun sendNote() {
        val content = noteText.trim()
        if (content.isNotBlank()) {
            coroutineScope.launch {
                val newItem = SavedItem(
                    title = "Quick Edge Note",
                    content = content,
                    type = SavedItemType.TEXT
                )
                repository.saveItem(newItem)
                WidgetUpdater.update(context)
                Toast.makeText(context, "✓ Saved to Cobalt", Toast.LENGTH_SHORT).show()
                noteText = ""
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (noteText.isEmpty()) {
                    Text(
                        text = "Quick thought...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                BasicTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendNote() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(30.dp)
                    .clickable { sendNote() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_send),
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentItemsList(
    repository: CobaltRepository,
    onDismiss: () -> Unit,
    onOpenMainApp: (itemId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allItems by repository.getAllItemsFlow().collectAsState(initial = emptyList())
    val recentItems = remember(allItems) {
        allItems.sortedByDescending { it.timestamp }.take(5)
    }

    if (recentItems.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No recent items yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(recentItems, key = { it.id }) { item ->
                RecentItemRow(
                    item = item,
                    onClick = {
                        onDismiss()
                        if (item.type == SavedItemType.LINK) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.content)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            onOpenMainApp(item.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RecentItemRow(
    item: SavedItem,
    onClick: () -> Unit
) {
    val iconRes = when (item.type) {
        SavedItemType.LINK -> R.drawable.ic_custom_link
        SavedItemType.IMAGE -> R.drawable.ic_custom_image
        SavedItemType.VIDEO -> R.drawable.ic_custom_video
        SavedItemType.AUDIO -> R.drawable.ic_custom_voice
        SavedItemType.MEDIA -> R.drawable.ic_custom_movie
        else -> R.drawable.ic_custom_text
    }

    val iconTint = when (item.type) {
        SavedItemType.LINK -> Color(0xFF42A5F5)
        SavedItemType.IMAGE -> Color(0xFF66BB6A)
        SavedItemType.VIDEO -> Color(0xFFAB47BC)
        SavedItemType.AUDIO -> MaterialTheme.colorScheme.primary
        SavedItemType.MEDIA -> Color(0xFFE91E63)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(32.dp)
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

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.content.take(60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun MediaSearchOverlayPage(
    repository: CobaltRepository,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MediaSearchResultItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            searchResults = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(350)
        try {
            searchResults = repository.searchMedia(query)
        } catch (_: Exception) {
            searchResults = emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Cobalt",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onClose() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search title...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                isLoading -> {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 30.dp)
                    )
                }
                searchQuery.isBlank() -> {
                    Text(
                        text = "Search movies, TV shows, or anime...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 30.dp)
                    )
                }
                searchResults.isEmpty() -> {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 30.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(searchResults, key = { it.id }) { item ->
                            MediaSearchResultRow(
                                item = item,
                                onSave = {
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
                                            Toast.makeText(context, "✓ Saved ${item.title}", Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaSearchResultRow(
    item: MediaSearchResultItem,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    var isSaved by remember { mutableStateOf(false) }

    val mediaTypeLabel = remember(item.mediaType) {
        when (item.mediaType.lowercase()) {
            "movie" -> "Movie"
            "tv", "tv show", "tv_show" -> "TV"
            "anime" -> "Anime"
            else -> item.mediaType.replaceFirstChar { it.uppercase() }
        }
    }
    val metaText = if (!item.releaseYear.isNullOrBlank()) "$mediaTypeLabel • ${item.releaseYear}" else mediaTypeLabel

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_movie),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = {
                    if (!isSaved) {
                        isSaved = true
                        onSave()
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaved) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = if (isSaved) "✓" else "Save",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

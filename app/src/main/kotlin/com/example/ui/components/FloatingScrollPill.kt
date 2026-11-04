package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Universal adapter enabling any scrollable container to synchronize with [FloatingScrollPill].
 */
interface ScrollAdapter {
    val scrollFraction: Float
    val isScrolling: Boolean
    val isScrollable: Boolean
    suspend fun scrollToFraction(fraction: Float)
    suspend fun scrollToTop()
    suspend fun scrollToBottom()
}

@Composable
fun rememberLazyListScrollAdapter(state: LazyListState): ScrollAdapter {
    return remember(state) {
        object : ScrollAdapter {
            override val scrollFraction: Float
                get() {
                    val layout = state.layoutInfo
                    val total = layout.totalItemsCount
                    val visible = layout.visibleItemsInfo
                    if (total <= visible.size || total == 0 || visible.isEmpty()) return 0f

                    val first = state.firstVisibleItemIndex
                    val offset = state.firstVisibleItemScrollOffset
                    val avgItemSize = visible.map { it.size }.average().toFloat().coerceAtLeast(1f)
                    val maxScrollable = (total - visible.size).coerceAtLeast(1)

                    val exactPosition = first.toFloat() + (offset.toFloat() / avgItemSize)
                    return (exactPosition / maxScrollable.toFloat()).coerceIn(0f, 1f)
                }

            override val isScrolling: Boolean
                get() = state.isScrollInProgress

            override val isScrollable: Boolean
                get() = state.layoutInfo.totalItemsCount > state.layoutInfo.visibleItemsInfo.size

            override suspend fun scrollToFraction(fraction: Float) {
                val layout = state.layoutInfo
                val total = layout.totalItemsCount
                if (total == 0) return
                val visible = layout.visibleItemsInfo
                val avgItemSize = if (visible.isNotEmpty()) {
                    visible.map { it.size }.average().toInt().coerceAtLeast(1)
                } else 100

                val maxScrollable = (total - visible.size).coerceAtLeast(1)
                val targetFloat = fraction.coerceIn(0f, 1f) * maxScrollable
                val targetIndex = targetFloat.toInt().coerceIn(0, total - 1)
                val remainder = targetFloat - targetIndex
                val offsetPx = (remainder * avgItemSize).roundToInt()

                state.scrollToItem(targetIndex, offsetPx)
            }

            override suspend fun scrollToTop() {
                state.animateScrollToItem(0, 0)
            }

            override suspend fun scrollToBottom() {
                val total = state.layoutInfo.totalItemsCount
                if (total > 0) state.animateScrollToItem(total - 1, 0)
            }
        }
    }
}

@Composable
fun rememberLazyGridScrollAdapter(state: LazyGridState): ScrollAdapter {
    return remember(state) {
        object : ScrollAdapter {
            override val scrollFraction: Float
                get() {
                    val layout = state.layoutInfo
                    val total = layout.totalItemsCount
                    val visible = layout.visibleItemsInfo
                    if (total <= visible.size || total == 0 || visible.isEmpty()) return 0f

                    var maxItemsInRow = 1
                    var currentY = Int.MIN_VALUE
                    var countInCurrentY = 0
                    var totalHeight = 0L
                    for (i in visible.indices) {
                        val item = visible[i]
                        totalHeight += item.size.height
                        if (item.offset.y == currentY) {
                            countInCurrentY++
                            if (countInCurrentY > maxItemsInRow) maxItemsInRow = countInCurrentY
                        } else {
                            currentY = item.offset.y
                            countInCurrentY = 1
                        }
                    }
                    val itemsPerRow = maxItemsInRow.coerceAtLeast(1)
                    val totalRows = (total + itemsPerRow - 1) / itemsPerRow
                    val visibleRows = (visible.size + itemsPerRow - 1) / itemsPerRow
                    val maxScrollableRows = (totalRows - visibleRows).coerceAtLeast(1)

                    val firstRow = state.firstVisibleItemIndex / itemsPerRow
                    val firstOffset = state.firstVisibleItemScrollOffset
                    val avgRowHeight = (totalHeight.toFloat() / visible.size).coerceAtLeast(1f)

                    val exactRow = firstRow.toFloat() + (firstOffset.toFloat() / avgRowHeight)
                    return (exactRow / maxScrollableRows.toFloat()).coerceIn(0f, 1f)
                }

            override val isScrolling: Boolean
                get() = state.isScrollInProgress

            override val isScrollable: Boolean
                get() = state.layoutInfo.totalItemsCount > state.layoutInfo.visibleItemsInfo.size

            override suspend fun scrollToFraction(fraction: Float) {
                val layout = state.layoutInfo
                val total = layout.totalItemsCount
                if (total == 0) return
                val visible = layout.visibleItemsInfo
                var maxItemsInRow = 1
                var currentY = Int.MIN_VALUE
                var countInCurrentY = 0
                var totalHeight = 0L
                for (i in visible.indices) {
                    val item = visible[i]
                    totalHeight += item.size.height
                    if (item.offset.y == currentY) {
                        countInCurrentY++
                        if (countInCurrentY > maxItemsInRow) maxItemsInRow = countInCurrentY
                    } else {
                        currentY = item.offset.y
                        countInCurrentY = 1
                    }
                }
                val itemsPerRow = if (visible.isNotEmpty()) maxItemsInRow.coerceAtLeast(1) else 2

                val totalRows = (total + itemsPerRow - 1) / itemsPerRow
                val visibleRows = if (visible.isNotEmpty()) (visible.size + itemsPerRow - 1) / itemsPerRow else 1
                val maxScrollableRows = (totalRows - visibleRows).coerceAtLeast(1)

                val avgRowHeight = if (visible.isNotEmpty()) {
                    (totalHeight / visible.size).toInt().coerceAtLeast(1)
                } else 200

                val targetFloatRow = fraction.coerceIn(0f, 1f) * maxScrollableRows
                val targetRow = targetFloatRow.toInt().coerceIn(0, totalRows - 1)
                val remainder = targetFloatRow - targetRow
                val offsetPx = (remainder * avgRowHeight).roundToInt()

                val targetItemIndex = (targetRow * itemsPerRow).coerceIn(0, total - 1)
                state.scrollToItem(targetItemIndex, offsetPx)
            }

            override suspend fun scrollToTop() {
                state.animateScrollToItem(0, 0)
            }

            override suspend fun scrollToBottom() {
                val total = state.layoutInfo.totalItemsCount
                if (total > 0) state.animateScrollToItem(total - 1, 0)
            }
        }
    }
}

@Composable
fun rememberLazyStaggeredGridScrollAdapter(state: LazyStaggeredGridState): ScrollAdapter {
    return remember(state) {
        object : ScrollAdapter {
            override val scrollFraction: Float
                get() {
                    val layout = state.layoutInfo
                    val total = layout.totalItemsCount
                    val visible = layout.visibleItemsInfo
                    if (total <= visible.size || total == 0 || visible.isEmpty()) return 0f

                    var maxLane = 1
                    var totalHeight = 0L
                    for (i in visible.indices) {
                        val item = visible[i]
                        if (item.lane > maxLane) maxLane = item.lane
                        totalHeight += item.size.height
                    }
                    val lanes = (maxLane + 1).coerceAtLeast(2)
                    val totalRows = (total + lanes - 1) / lanes
                    val visibleRows = (visible.size + lanes - 1) / lanes
                    val maxScrollableRows = (totalRows - visibleRows).coerceAtLeast(1)

                    val firstRow = state.firstVisibleItemIndex / lanes
                    val firstOffset = state.firstVisibleItemScrollOffset
                    val avgHeight = (totalHeight.toFloat() / visible.size).coerceAtLeast(1f)

                    val exactRow = firstRow.toFloat() + (firstOffset.toFloat() / avgHeight)
                    return (exactRow / maxScrollableRows.toFloat()).coerceIn(0f, 1f)
                }

            override val isScrolling: Boolean
                get() = state.isScrollInProgress

            override val isScrollable: Boolean
                get() = state.layoutInfo.totalItemsCount > state.layoutInfo.visibleItemsInfo.size

            override suspend fun scrollToFraction(fraction: Float) {
                val layout = state.layoutInfo
                val total = layout.totalItemsCount
                if (total == 0) return
                val visible = layout.visibleItemsInfo
                var maxLane = 1
                var totalHeight = 0L
                for (i in visible.indices) {
                    val item = visible[i]
                    if (item.lane > maxLane) maxLane = item.lane
                    totalHeight += item.size.height
                }
                val lanes = if (visible.isNotEmpty()) (maxLane + 1).coerceAtLeast(2) else 2
                val totalRows = (total + lanes - 1) / lanes
                val visibleRows = if (visible.isNotEmpty()) (visible.size + lanes - 1) / lanes else 1
                val maxScrollableRows = (totalRows - visibleRows).coerceAtLeast(1)

                val avgHeight = if (visible.isNotEmpty()) {
                    (totalHeight / visible.size).toInt().coerceAtLeast(1)
                } else 180

                val targetFloatRow = fraction.coerceIn(0f, 1f) * maxScrollableRows
                val targetRow = targetFloatRow.toInt().coerceIn(0, totalRows - 1)
                val remainder = targetFloatRow - targetRow
                val offsetPx = (remainder * avgHeight).roundToInt()

                val targetItemIndex = (targetRow * lanes).coerceIn(0, total - 1)
                state.scrollToItem(targetItemIndex, offsetPx)
            }

            override suspend fun scrollToTop() {
                state.animateScrollToItem(0, 0)
            }

            override suspend fun scrollToBottom() {
                val total = state.layoutInfo.totalItemsCount
                if (total > 0) state.animateScrollToItem(total - 1, 0)
            }
        }
    }
}

@Composable
fun rememberScrollStateAdapter(state: ScrollState): ScrollAdapter {
    return remember(state) {
        object : ScrollAdapter {
            override val scrollFraction: Float
                get() {
                    val max = state.maxValue
                    return if (max > 0) (state.value.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
                }
            override val isScrolling: Boolean
                get() = state.isScrollInProgress
            override val isScrollable: Boolean
                get() = state.maxValue > 0
            override suspend fun scrollToFraction(fraction: Float) {
                val target = (fraction.coerceIn(0f, 1f) * state.maxValue).roundToInt()
                state.scrollTo(target)
            }
            override suspend fun scrollToTop() {
                state.animateScrollTo(0)
            }
            override suspend fun scrollToBottom() {
                state.animateScrollTo(state.maxValue)
            }
        }
    }
}

/**
 * Animated tactile glassmorphic floating pill scrollbar with drag-scrubbing and top/bottom jump buttons.
 * Uses frame-paced 60fps/120fps display refresh synchronization matching the native editor logic.
 */
@Composable
fun FloatingScrollPill(
    adapter: ScrollAdapter,
    localHazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useBlur: Boolean = true,
    blurRadius: Int = 25,
    blurOpacity: Float = 0.55f,
    topPadding: Dp = 80.dp,
    bottomPadding: Dp = 100.dp
) {
    if (!enabled || !adapter.isScrollable) return

    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var scrubTargetFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(adapter.isScrolling, isDragging) {
        if (adapter.isScrolling || isDragging) {
            hideJob?.cancel()
            isVisible = true
        } else if (isVisible) {
            hideJob?.cancel()
            hideJob = launch {
                delay(1500)
                isVisible = false
            }
        }
    }

    // Frame-paced scrubbing loop matching the native editor: continuously syncs with display refresh
    LaunchedEffect(isDragging) {
        if (isDragging) {
            while (isActive && isDragging) {
                adapter.scrollToFraction(scrubTargetFraction)
                withFrameMillis { }
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "floating_pill_alpha"
    )

    if (alpha <= 0.005f) return

    val pillHeight = 76.dp
    val pillWidth = 36.dp
    val isDark = isSystemInDarkTheme()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
    ) {
        val density = LocalDensity.current
        val availableTrackHeight = maxHeight - topPadding - bottomPadding - pillHeight
        val trackHeightPx = with(density) { availableTrackHeight.coerceAtLeast(0.dp).toPx() }

        val smoothFraction by animateFloatAsState(
            targetValue = adapter.scrollFraction,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "floating_pill_fraction"
        )

        // When dragging, follow the user's touch instantly; when scrolling naturally, use smooth spring
        val displayFraction = if (isDragging) dragFraction else smoothFraction
        val targetOffsetY = topPadding + (availableTrackHeight.coerceAtLeast(0.dp) * displayFraction)

        val scale by animateFloatAsState(
            targetValue = if (isDragging) 1.08f else 1.0f,
            animationSpec = tween(150),
            label = "floating_pill_scale"
        )

        val containerModifier = if (useBlur && localHazeState != null) {
            Modifier
                .size(width = pillWidth, height = pillHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = blurOpacity.coerceAtLeast(0.4f)))
                .hazeEffect(
                    state = localHazeState,
                    style = HazeStyle(
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = blurOpacity)),
                        blurRadius = blurRadius.dp,
                        noiseFactor = 0.02f
                    )
                )
                .border(
                    width = if (isDragging) 1.5.dp else 0.75.dp,
                    color = if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.5f),
                    shape = RoundedCornerShape(18.dp)
                )
        } else {
            Modifier
                .size(width = pillWidth, height = pillHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
                .border(
                    width = if (isDragging) 1.5.dp else 0.75.dp,
                    color = if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.4f),
                    shape = RoundedCornerShape(18.dp)
                )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp)
                .offset(y = targetOffsetY)
                .scale(scale)
                .then(containerModifier)
                .pointerInput(trackHeightPx) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragFraction = adapter.scrollFraction
                            scrubTargetFraction = dragFraction
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (trackHeightPx > 0) {
                                val delta = dragAmount.y / trackHeightPx
                                dragFraction = (dragFraction + delta).coerceIn(0f, 1f)
                                scrubTargetFraction = dragFraction
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Chevron (Tap to jump to top)
                IconButton(
                    onClick = {
                        coroutineScope.launch { adapter.scrollToTop() }
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .padding(top = 2.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_chevron_up),
                        contentDescription = "Scroll to top",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Center tactile grip handle
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                )

                // Bottom Chevron (Tap to jump to bottom)
                IconButton(
                    onClick = {
                        coroutineScope.launch { adapter.scrollToBottom() }
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .padding(bottom = 2.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_chevron_down),
                        contentDescription = "Scroll to bottom",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

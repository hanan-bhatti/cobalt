package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.example.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Stable
class CollapsibleTopBarState(
    private val topBarHeightAnim: Animatable<Float, *>,
    val minHeightPx: Float,
    val maxHeightPx: Float,
    val nestedScrollConnection: NestedScrollConnection
) {
    val heightPx: Float get() = topBarHeightAnim.value

    val collapseFraction: Float
        get() = 1f - ((topBarHeightAnim.value - minHeightPx) / (maxHeightPx - minHeightPx)).coerceIn(0f, 1f)
}

/**
 * Creates and remembers a CollapsibleTopBarState linked to a LazyListState.
 * When the list scrolls down, the header smoothly collapses; when scrolling back to top, it expands.
 */
@Composable
fun rememberCollapsibleTopBarState(
    lazyListState: LazyListState,
    maxHeight: Dp = 175.dp
): CollapsibleTopBarState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minHeight = 64.dp + statusBarHeight

    val minHeightPx = with(density) { minHeight.toPx() }
    val maxHeightPx = with(density) { (maxHeight + statusBarHeight).toPx() }

    val topBarHeightAnim = remember(maxHeightPx) { Animatable(maxHeightPx) }

    val nestedScrollConnection = remember(minHeightPx, maxHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeightAnim.value
                val newHeight = (previousHeight + delta).coerceIn(minHeightPx, maxHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeightAnim.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeightAnim.value > (minHeightPx + maxHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxHeightPx else minHeightPx

            if (topBarHeightAnim.value != targetValue) {
                coroutineScope.launch {
                    topBarHeightAnim.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    return remember(topBarHeightAnim, minHeightPx, maxHeightPx, nestedScrollConnection) {
        CollapsibleTopBarState(topBarHeightAnim, minHeightPx, maxHeightPx, nestedScrollConnection)
    }
}

/**
 * Collapsible Top Bar matching PixelPlayer's expressive hero header.
 * Transforms a large headline title into a compact top app bar on scroll with spring physics.
 */
@Composable
fun CollapsibleCommonTopBar(
    title: String,
    collapseFraction: Float,
    headerHeight: Dp,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val clampedFraction = collapseFraction.coerceIn(0f, 1f)
    val titleScale = lerp(1.22f, 0.82f, clampedFraction)
    val titlePaddingStart = lerp(20.dp, 68.dp, clampedFraction)
    val titleVerticalBias = lerp(0.85f, 0.0f, clampedFraction)
    val subtitleAlpha = (1f - clampedFraction * 2.2f).coerceIn(0f, 1f)
    val solidAlpha = (clampedFraction * 2f).coerceIn(0f, 1f)

    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = solidAlpha)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(backgroundColor)
            .zIndex(5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Title & Subtitle Animated Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = titlePaddingStart,
                        end = 20.dp,
                        bottom = lerp(18.dp, 0.dp, clampedFraction)
                    ),
                contentAlignment = BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
            ) {
                Column(
                    modifier = if (clampedFraction > 0.85f) Modifier.height(64.dp) else Modifier,
                    verticalArrangement = if (clampedFraction > 0.85f) Arrangement.Center else Arrangement.Top
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = (26.sp * titleScale),
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null && subtitleAlpha > 0.01f) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = subtitleAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Top-left Navigation Icon (Back Button)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .height(64.dp)
                    .padding(start = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                FilledIconButton(
                    onClick = onBackClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (clampedFraction > 0.5f) {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_back),
                        contentDescription = "Back",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

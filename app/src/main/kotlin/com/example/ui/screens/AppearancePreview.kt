package com.example.ui.screens

import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.dock.sideDockCardGeometry
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

/** One static scene: the entire Home screen and its dock use the same uniform scale. */
@Composable
fun StaticHomeAndDockPreview(
    useBlur: Boolean,
    blurRadius: Int,
    blurOpacity: Float,
    modifier: Modifier = Modifier,
    isListView: Boolean = true,
    recentCapturesExpanded: Boolean = false,
    dockSide: String = "Right",
    dockPosition: Float = 0.4f
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val root = LocalView.current.rootView
    var homeSize by remember(root) { mutableStateOf(IntSize(root.width, root.height)) }
    DisposableEffect(root) {
        val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            homeSize = IntSize(view.width, view.height)
        }
        root.addOnLayoutChangeListener(listener)
        homeSize = IntSize(root.width, root.height)
        onDispose { root.removeOnLayoutChangeListener(listener) }
    }
    val homeWidth = if (homeSize.width > 0) (homeSize.width / density.density).dp else configuration.screenWidthDp.dp
    val homeHeight = if (homeSize.height > 0) (homeSize.height / density.density).dp else configuration.screenHeightDp.dp
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dock = sideDockCardGeometry(configuration.screenHeightDp, landscape)
    val edgeMargin = 12.dp
    val verticalMargin = if (landscape) 12.dp else 36.dp
    val dockTop = (homeHeight * dockPosition - dock.heightDp.dp / 2)
        .coerceIn(verticalMargin, (homeHeight - dock.heightDp.dp - verticalMargin).coerceAtLeast(verticalMargin))
    val dockLeft = if (dockSide == "Left") edgeMargin else homeWidth - dock.widthDp.dp - edgeMargin
    val actionsAtBottom = dockTop + dock.heightDp.dp / 2 < homeHeight / 2
    val homeDescription = stringResource(R.string.settings_home_preview_description)
    val dockDescription = stringResource(R.string.settings_dock_preview_description)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            val previewHeight = minOf(236.dp, maxWidth / (homeWidth / homeHeight))
            ScaledAppearanceScene(homeWidth, homeHeight, previewHeight, Modifier.testTag("appearance_home_preview")) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp))) {
                    HomeSkeleton(
                        isListView, recentCapturesExpanded, useBlur, blurRadius, blurOpacity,
                        Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = homeDescription }
                    )
                    DockSkeleton(
                        actionsAtBottom,
                        Modifier.offset(dockLeft, dockTop).size(dock.widthDp.dp, dock.heightDp.dp)
                            .testTag("appearance_dock_preview").clearAndSetSemantics { contentDescription = dockDescription }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScaledAppearanceScene(sourceWidth: Dp, sourceHeight: Dp, previewHeight: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, _ ->
        val sourceWidthPx = sourceWidth.roundToPx()
        val sourceHeightPx = sourceHeight.roundToPx()
        val scale = previewHeight.toPx() / sourceHeightPx
        val scene = measurables.single().measure(Constraints.fixed(sourceWidthPx, sourceHeightPx))
        layout((sourceWidthPx * scale).roundToInt(), (sourceHeightPx * scale).roundToInt()) {
            scene.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

/** Home's actual header, search, recent section, archive layout and floating navigation. */
@Composable
internal fun HomeSkeleton(isListView: Boolean, recentExpanded: Boolean, useBlur: Boolean, blurRadius: Int, blurOpacity: Float, modifier: Modifier) {
    val hazeState = remember { HazeState() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(topInset.coerceAtLeast(24.dp)).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonLine(Modifier.width(36.dp), height = 5.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonLine(Modifier.width(12.dp), height = 7.dp)
                    SkeletonLine(Modifier.width(12.dp), height = 7.dp)
                    SkeletonLine(Modifier.width(20.dp), height = 7.dp)
                }
            }
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cobalt", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, color = MaterialTheme.colorScheme.onBackground)
                        SkeletonLine(Modifier.width(42.dp).padding(top = 6.dp), height = 8.dp, color = MaterialTheme.colorScheme.primary)
                    }
                    SkeletonLine(Modifier.width(100.dp), height = 7.dp, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                }
                listOf(R.drawable.ic_custom_filter, if (isListView) R.drawable.ic_custom_grid else R.drawable.ic_custom_list).forEach {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(it), null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_custom_search), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    SkeletonLine(Modifier.width(170.dp), height = 9.dp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SkeletonLine(Modifier.width(116.dp), height = 8.dp, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                Icon(painterResource(if (recentExpanded) R.drawable.ic_custom_chevron_up else R.drawable.ic_custom_chevron_down), null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            }
            if (recentExpanded) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(10.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(painterResource(previewIcon(index)), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                SkeletonLine(Modifier.weight(1f), height = 6.dp)
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().hazeSource(hazeState)) {
                if (isListView) {
                    LazyColumn(
                        userScrollEnabled = false,
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()
                    ) { items((0..11).toList()) { ArchiveListSkeleton(it) } }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2), userScrollEnabled = false,
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalItemSpacing = 12.dp, modifier = Modifier.fillMaxSize()
                    ) { items(12) { ArchiveGridSkeleton(it) } }
                }
            }
        }
        HomeNavigationSkeleton(hazeState, useBlur, blurRadius, blurOpacity, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = bottomInset + 16.dp))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).width(108.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f), CircleShape))
    }
}

@Composable
private fun ArchiveListSkeleton(index: Int) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(72.dp).background(accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(previewIcon(index)), null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SkeletonLine(Modifier.width(36.dp), height = 5.dp, color = accent.copy(alpha = 0.7f))
                    SkeletonLine(Modifier.width(32.dp), height = 5.dp)
                }
                SkeletonLine(Modifier.fillMaxWidth(0.9f), height = 9.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                SkeletonLine(Modifier.fillMaxWidth(if (index % 2 == 0) 0.7f else 0.85f), height = 6.dp)
                SkeletonLine(Modifier.width(52.dp), height = 10.dp, color = accent.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
private fun ArchiveGridSkeleton(index: Int) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)), modifier = Modifier.padding(4.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.background(accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(previewIcon(index)), null, tint = accent, modifier = Modifier.size(12.dp))
                    SkeletonLine(Modifier.width(32.dp), height = 4.dp, color = accent.copy(alpha = 0.6f))
                }
                SkeletonLine(Modifier.width(28.dp), height = 4.dp)
            }
            if (index % 3 == 1) {
                Box(Modifier.fillMaxWidth().height(110.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_custom_image), null, tint = accent.copy(alpha = 0.45f), modifier = Modifier.size(28.dp))
                }
            }
            SkeletonLine(Modifier.fillMaxWidth(0.9f), height = 9.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            repeat(if (index % 3 == 2) 5 else 3) { line -> SkeletonLine(Modifier.fillMaxWidth(if (line % 2 == 0) 0.95f else 0.65f), height = 6.dp) }
            SkeletonLine(Modifier.width(52.dp), height = 10.dp, color = accent.copy(alpha = 0.15f))
        }
    }
}

@Composable
private fun HomeNavigationSkeleton(hazeState: HazeState, useBlur: Boolean, blurRadius: Int, blurOpacity: Float, modifier: Modifier) {
    val glass = if (useBlur) Modifier.hazeEffect(state = hazeState, style = HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        tint = HazeTint(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = blurOpacity)), blurRadius = blurRadius.dp, noiseFactor = 0.02f
    )) else Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(32.dp)).then(glass).padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ic_nav_home_bold), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.settings_home), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
            }
            listOf(R.drawable.ic_nav_search_outline, R.drawable.ic_nav_media_outline, R.drawable.ic_nav_folders_outline, R.drawable.ic_nav_settings_outline).forEach {
                Icon(painterResource(it), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp).size(22.dp))
            }
        }
        Box(Modifier.size(56.dp).clip(CircleShape).then(glass), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_custom_plus), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}

/** Resting dock geometry and reachability order mirror SideDockContent. */
@Composable
private fun DockSkeleton(actionsAtBottom: Boolean, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)), shadowElevation = 6.dp, modifier = modifier
    ) {
        DockSkeletonContent(actionsAtBottom, Modifier)
    }
}

@Composable
internal fun DockSkeletonContent(actionsAtBottom: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_custom_sidedock), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    SkeletonLine(Modifier.width(90.dp), height = 7.dp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                }
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_dock_close), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(if (actionsAtBottom) 8.dp else 10.dp))
            if (!actionsAtBottom) {
                DockQuickActionsSkeleton()
                Spacer(Modifier.height(12.dp))
                DockInputSkeleton()
                Spacer(Modifier.height(10.dp))
            }
            SkeletonLine(Modifier.width(48.dp).padding(bottom = 4.dp), height = 6.dp)
            LazyColumn(userScrollEnabled = false, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                items((0..7).toList()) { index ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                                Icon(painterResource(previewIcon(index)), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                SkeletonLine(Modifier.fillMaxWidth(0.9f), height = 7.dp)
                                SkeletonLine(Modifier.fillMaxWidth(0.65f), height = 5.dp)
                            }
                        }
                    }
                }
            }
            if (actionsAtBottom) {
                Spacer(Modifier.height(10.dp))
                DockInputSkeleton()
                Spacer(Modifier.height(10.dp))
                DockQuickActionsSkeleton()
            }
        }
}

@Composable
private fun DockInputSkeleton() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_custom_text), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            SkeletonLine(Modifier.weight(1f), height = 7.dp)
            Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_custom_send), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun DockQuickActionsSkeleton() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(R.drawable.ic_custom_ocr, R.drawable.ic_custom_text, R.drawable.ic_custom_link, R.drawable.ic_custom_movie, R.drawable.ic_nav_home_outline).forEachIndexed { index, icon ->
            Column(Modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Surface(color = if (index == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, if (index == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, tint = if (index == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                }
                SkeletonLine(Modifier.width(22.dp), height = 4.dp)
            }
        }
    }
}

private fun previewIcon(index: Int) = listOf(R.drawable.ic_custom_text, R.drawable.ic_custom_link, R.drawable.ic_custom_image, R.drawable.ic_custom_code, R.drawable.ic_custom_voice, R.drawable.ic_custom_movie)[index % 6]

@Composable
private fun SkeletonLine(modifier: Modifier, height: Dp = 8.dp, color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)) {
    Box(modifier.height(height).background(color, RoundedCornerShape(height / 2)))
}

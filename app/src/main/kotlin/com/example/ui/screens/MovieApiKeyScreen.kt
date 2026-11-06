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

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.ui.components.CollapsibleCommonTopBar
import com.example.ui.components.bounceClick
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.CobaltCustomTabsLauncher
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieApiKeyScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tmdbApiKey by viewModel.tmdbApiKey.collectAsState()
    val allItems by viewModel.allItems.collectAsState()

    val mediaItems = remember(allItems) {
        allItems.filter { it.type == SavedItemType.MEDIA }
    }

    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 160.dp)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(topBarState.nestedScrollConnection)
    ) {
        val currentTopBarHeightDp = with(density) { topBarState.heightPx.toDp() }
        val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 12.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = bottomNavPadding + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Card 1: API Key Configuration
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val hasKey = tmdbApiKey.isNotBlank()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_movie),
                                    contentDescription = null,
                                    tint = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (hasKey) "TMDb API Connected" else "API Key Missing",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (hasKey) Color(0xFF10B981) else Color(0xFFF59E0B))
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (hasKey) {
                                        "Movies, TV metadata, streaming providers and trailers active"
                                    } else {
                                        "Anime lookup active via Jikan. Add free TMDb key for Movies & TV."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        var tmdbKeyInput by remember(tmdbApiKey) { mutableStateOf(tmdbApiKey) }
                        var keyVisibility by remember { mutableStateOf(false) }
                        val focusManager = LocalFocusManager.current

                        OutlinedTextField(
                            value = tmdbKeyInput,
                            onValueChange = { tmdbKeyInput = it },
                            placeholder = { Text("Enter TMDb API Key (v3 auth)") },
                            singleLine = true,
                            visualTransformation = if (keyVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val iconResId = if (keyVisibility) R.drawable.ic_custom_eye else R.drawable.ic_custom_eye_off
                                val description = if (keyVisibility) "Hide API Key" else "Show API Key"
                                IconButton(onClick = { keyVisibility = !keyVisibility }) {
                                    Icon(
                                        painter = painterResource(id = iconResId),
                                        contentDescription = description,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                viewModel.updateTmdbApiKey(tmdbKeyInput)
                                focusManager.clearFocus()
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .bounceClick()
                        ) {
                            Text(
                                text = "Save Integration Key",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Get your free API Key on TMDb ↗",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable {
                                    CobaltCustomTabsLauncher.launch(
                                        context = context,
                                        url = "https://www.themoviedb.org/settings/api"
                                    )
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }

            // Card 2: Clean Media & Query Activity Line Graph
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MediaActivityLineChart(
                        mediaItems = mediaItems,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }

        CollapsibleCommonTopBar(
            title = "Movie & TV Integration",
            subtitle = "TMDb API key, metadata & query analytics",
            collapseFraction = topBarState.collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigateBack
        )
    }
}

enum class MediaChartRange(val label: String) {
    PAST_24_HOURS("24H"),
    PAST_7_DAYS("7D"),
    PAST_30_DAYS("30D")
}

data class ActivityPoint(
    val timestamp: Long,
    val label: String,
    val count: Int
)

private fun computeActivityPoints(mediaItems: List<SavedItem>, range: MediaChartRange): List<ActivityPoint> {
    val now = System.currentTimeMillis()
    return when (range) {
        MediaChartRange.PAST_24_HOURS -> {
            val intervals = 6
            val stepMillis = TimeUnit.HOURS.toMillis(4)
            val timeFormat = SimpleDateFormat("h a", Locale.getDefault())
            (intervals - 1 downTo 0).map { i ->
                val end = now - (i * stepMillis)
                val start = end - stepMillis
                val count = mediaItems.count { it.timestamp in start..end }
                ActivityPoint(
                    timestamp = end,
                    label = timeFormat.format(Date(end)),
                    count = count
                )
            }
        }
        MediaChartRange.PAST_7_DAYS -> {
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            (6 downTo 0).map { i ->
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -i)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                val end = start + TimeUnit.DAYS.toMillis(1) - 1
                val count = mediaItems.count { it.timestamp in start..end }
                ActivityPoint(
                    timestamp = start,
                    label = dayFormat.format(Date(start)),
                    count = count
                )
            }
        }
        MediaChartRange.PAST_30_DAYS -> {
            val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
            val intervals = 6
            val stepMillis = TimeUnit.DAYS.toMillis(5)
            (intervals - 1 downTo 0).map { i ->
                val end = now - (i * stepMillis)
                val start = end - stepMillis
                val count = mediaItems.count { it.timestamp in start..end }
                ActivityPoint(
                    timestamp = end,
                    label = dateFormat.format(Date(end)),
                    count = count
                )
            }
        }
    }
}

@Composable
private fun MediaActivityLineChart(
    mediaItems: List<SavedItem>,
    modifier: Modifier = Modifier
) {
    var selectedRange by remember { mutableStateOf(MediaChartRange.PAST_7_DAYS) }
    val points = remember(mediaItems, selectedRange) {
        computeActivityPoints(mediaItems, selectedRange)
    }

    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val totalInRange = remember(points) { points.sumOf { it.count } }
    val activePoint = scrubbedIndex?.let { points.getOrNull(it) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row: Title & Range Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_bar_chart),
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Media Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Minimal Pill Selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp)
            ) {
                MediaChartRange.values().forEach { range ->
                    val isSelected = selectedRange == range
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) primaryColor else Color.Transparent)
                            .clickable {
                                selectedRange = range
                                scrubbedIndex = null
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = range.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Clean Stat Display (smoothly responds to touch scrub)
        Column {
            val displayCount = activePoint?.count ?: totalInRange
            val displayLabel = if (activePoint != null) {
                "activity on ${activePoint.label}"
            } else {
                "total items in ${selectedRange.label}"
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$displayCount",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        // Clean Line Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectTapGestures(
                            onPress = { offset ->
                                val horizontalPadding = 12.dp.toPx()
                                val drawWidth = size.width - (2 * horizontalPadding)
                                if (drawWidth > 0 && points.size > 1) {
                                    val step = drawWidth / (points.size - 1)
                                    val idx = ((offset.x - horizontalPadding) / step)
                                        .roundToInt()
                                        .coerceIn(0, points.lastIndex)
                                    scrubbedIndex = idx
                                    tryAwaitRelease()
                                    scrubbedIndex = null
                                }
                            }
                        )
                    }
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val horizontalPadding = 12.dp.toPx()
                                val drawWidth = size.width - (2 * horizontalPadding)
                                if (drawWidth > 0 && points.size > 1) {
                                    val step = drawWidth / (points.size - 1)
                                    val idx = ((offset.x - horizontalPadding) / step)
                                        .roundToInt()
                                        .coerceIn(0, points.lastIndex)
                                    scrubbedIndex = idx
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val horizontalPadding = 12.dp.toPx()
                                val drawWidth = size.width - (2 * horizontalPadding)
                                if (drawWidth > 0 && points.size > 1) {
                                    val step = drawWidth / (points.size - 1)
                                    val idx = ((change.position.x - horizontalPadding) / step)
                                        .roundToInt()
                                        .coerceIn(0, points.lastIndex)
                                    scrubbedIndex = idx
                                }
                            },
                            onDragEnd = {
                                scrubbedIndex = null
                            },
                            onDragCancel = {
                                scrubbedIndex = null
                            }
                        )
                    }
            ) {
                if (points.isEmpty()) return@Canvas

                val topPadding = 16.dp.toPx()
                val bottomPadding = 16.dp.toPx()
                val horizontalPadding = 12.dp.toPx()
                val drawWidth = size.width - (2 * horizontalPadding)
                val drawHeight = size.height - topPadding - bottomPadding

                val maxCount = points.maxOf { it.count }
                val effectiveMax = if (maxCount == 0) 4 else (maxCount * 1.35f).roundToInt().coerceAtLeast(3)

                // 1. Subtle horizontal dashed grid lines
                val guideLines = 3
                for (i in 0..guideLines) {
                    val y = topPadding + (i * (drawHeight / guideLines))
                    drawLine(
                        color = outlineVariant.copy(alpha = 0.20f),
                        start = Offset(horizontalPadding, y),
                        end = Offset(size.width - horizontalPadding, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // 2. Compute point coordinates
                val stepX = if (points.size > 1) drawWidth / (points.size - 1) else drawWidth
                val coords = points.mapIndexed { idx, p ->
                    val x = horizontalPadding + (idx * stepX)
                    val norm = p.count.toFloat() / effectiveMax
                    val y = size.height - bottomPadding - (norm * drawHeight)
                    Offset(x, y)
                }

                // 3. Draw smooth cubic curve and gradient fill
                val strokePath = Path()
                val fillPath = Path()

                strokePath.moveTo(coords[0].x, coords[0].y)
                fillPath.moveTo(coords[0].x, size.height - bottomPadding)
                fillPath.lineTo(coords[0].x, coords[0].y)

                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val cx1 = (p0.x + p1.x) / 2f
                    val cy1 = p0.y
                    val cx2 = (p0.x + p1.x) / 2f
                    val cy2 = p1.y

                    strokePath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                    fillPath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                }

                fillPath.lineTo(coords.last().x, size.height - bottomPadding)
                fillPath.close()

                // Gradient fill underneath curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.28f),
                            primaryColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = topPadding,
                        endY = size.height - bottomPadding
                    )
                )

                // Smooth stroke line
                drawPath(
                    path = strokePath,
                    color = primaryColor,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 4. Highlighted / scrubbed point indicator
                val highlightIndex = scrubbedIndex ?: if (maxCount > 0) coords.lastIndex else null
                if (highlightIndex != null && highlightIndex in coords.indices) {
                    val target = coords[highlightIndex]

                    // Vertical dashed guideline
                    drawLine(
                        color = primaryColor.copy(alpha = 0.45f),
                        start = Offset(target.x, topPadding),
                        end = Offset(target.x, size.height - bottomPadding),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Outer glowing halo
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.22f),
                        radius = 8.dp.toPx(),
                        center = target
                    )

                    // Surface background circle
                    drawCircle(
                        color = surfaceColor,
                        radius = 4.5.dp.toPx(),
                        center = target
                    )

                    // Center accent dot
                    drawCircle(
                        color = primaryColor,
                        radius = 3.dp.toPx(),
                        center = target
                    )
                }
            }
        }

        // X-Axis Labels Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { idx, point ->
                val isHighlighted = idx == (scrubbedIndex ?: points.lastIndex)
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    color = if (isHighlighted) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

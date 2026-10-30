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

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SavedItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class HeatmapDay(
    val dateMillis: Long,
    val count: Int,
    val dayOfWeek: Int, // 0 = Sunday, 6 = Saturday
    val weekIndex: Int,
    val dateString: String
)

@Composable
fun KnowledgeHeatmap(
    items: List<SavedItem>,
    modifier: Modifier = Modifier,
    weeksToShow: Int = 20
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val emptyCellColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Precompute daily counts and streaks
    val (heatmapDays, monthLabels, currentStreak, maxStreak, totalRecentCaptures) = remember(items, weeksToShow) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val todayMillis = cal.timeInMillis

        // Count per day key: "YYYY-MM-DD"
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val readableFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val monthShortFormat = SimpleDateFormat("MMM", Locale.getDefault())

        val countsMap = mutableMapOf<String, Int>()
        for (item in items) {
            val key = dayFormat.format(Date(item.timestamp))
            countsMap[key] = (countsMap[key] ?: 0) + 1
        }

        // Generate grid: 7 rows x weeksToShow columns
        // Determine end date: end of current week (Saturday)
        val endCal = Calendar.getInstance()
        endCal.set(Calendar.HOUR_OF_DAY, 0)
        endCal.set(Calendar.MINUTE, 0)
        endCal.set(Calendar.SECOND, 0)
        endCal.set(Calendar.MILLISECOND, 0)
        val dayOfEndWeek = endCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun..6=Sat
        endCal.add(Calendar.DAY_OF_YEAR, 6 - dayOfEndWeek)

        val totalDays = weeksToShow * 7
        val startCal = endCal.clone() as Calendar
        startCal.add(Calendar.DAY_OF_YEAR, -(totalDays - 1))

        val daysList = mutableListOf<HeatmapDay>()
        val months = mutableListOf<Pair<Int, String>>() // (weekIndex, "Jan")
        var lastMonth = -1

        val iterateCal = startCal.clone() as Calendar
        for (i in 0 until totalDays) {
            val currentMillis = iterateCal.timeInMillis
            val dateKey = dayFormat.format(Date(currentMillis))
            val count = if (currentMillis <= todayMillis + TimeUnit.DAYS.toMillis(1)) {
                countsMap[dateKey] ?: 0
            } else 0
            val dayOfWeek = iterateCal.get(Calendar.DAY_OF_WEEK) - 1
            val weekIndex = i / 7

            val month = iterateCal.get(Calendar.MONTH)
            if (month != lastMonth && dayOfWeek == 0) {
                months.add(weekIndex to monthShortFormat.format(iterateCal.time))
                lastMonth = month
            }

            daysList.add(
                HeatmapDay(
                    dateMillis = currentMillis,
                    count = count,
                    dayOfWeek = dayOfWeek,
                    weekIndex = weekIndex,
                    dateString = readableFormat.format(Date(currentMillis))
                )
            )
            iterateCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Compute streak
        var cStreak = 0
        var mStreak = 0
        var tempStreak = 0
        val streakCheckCal = Calendar.getInstance()
        streakCheckCal.set(Calendar.HOUR_OF_DAY, 0)
        streakCheckCal.set(Calendar.MINUTE, 0)
        streakCheckCal.set(Calendar.SECOND, 0)
        streakCheckCal.set(Calendar.MILLISECOND, 0)

        // Count backward from today for current streak
        val todayKey = dayFormat.format(streakCheckCal.time)
        val hasToday = (countsMap[todayKey] ?: 0) > 0
        if (!hasToday) {
            // Check yesterday
            streakCheckCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val k = dayFormat.format(streakCheckCal.time)
            if ((countsMap[k] ?: 0) > 0) {
                cStreak++
                streakCheckCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        // Max streak over past year
        val yearAgoCal = Calendar.getInstance()
        yearAgoCal.add(Calendar.DAY_OF_YEAR, -365)
        while (yearAgoCal.timeInMillis <= todayMillis) {
            val k = dayFormat.format(yearAgoCal.time)
            if ((countsMap[k] ?: 0) > 0) {
                tempStreak++
                if (tempStreak > mStreak) mStreak = tempStreak
            } else {
                tempStreak = 0
            }
            yearAgoCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val totalCaptures = daysList.sumOf { it.count }

        HeatmapCalculation(
            days = daysList,
            months = months,
            currentStreak = cStreak,
            maxStreak = mStreak.coerceAtLeast(cStreak),
            totalCaptures = totalCaptures
        )
    }

    var selectedDay by remember { mutableStateOf<HeatmapDay?>(null) }
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)

    // Cell layout geometry (in dp)
    val cellSizeDp = 13.dp
    val cellSpacingDp = 3.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Streak & Total Info Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings_intelligence),
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = "$totalRecentCaptures captures",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentStreak > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "🔥 $currentStreak day streak",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "Best: $maxStreak d",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Heatmap Grid with Horizontal Scrolling
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            // Day of week labels (M, W, F)
            Column(
                modifier = Modifier
                    .padding(top = 18.dp, end = 6.dp)
                    .width(16.dp),
                verticalArrangement = Arrangement.spacedBy(cellSizeDp + cellSpacingDp - 9.dp)
            ) {
                Text(text = "M", fontSize = 9.sp, color = onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(cellSizeDp - 3.dp))
                Text(text = "W", fontSize = 9.sp, color = onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(cellSizeDp - 3.dp))
                Text(text = "F", fontSize = 9.sp, color = onSurfaceVariant.copy(alpha = 0.6f))
            }

            // Scrollable Weeks Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Column {
                    // Month labels row
                    Row(
                        modifier = Modifier
                            .height(16.dp)
                            .width(((cellSizeDp + cellSpacingDp) * weeksToShow))
                    ) {
                        monthLabels.forEach { (weekIdx, label) ->
                            val offset = ((cellSizeDp + cellSpacingDp) * weekIdx)
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.absoluteOffset(x = offset)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Canvas Grid
                    val gridWidth = ((cellSizeDp + cellSpacingDp) * weeksToShow)
                    val gridHeight = ((cellSizeDp + cellSpacingDp) * 7)

                    Canvas(
                        modifier = Modifier
                            .size(width = gridWidth, height = gridHeight)
                            .pointerInput(heatmapDays) {
                                detectTapGestures { offset ->
                                    val cellTotalPx = (cellSizeDp + cellSpacingDp).toPx()
                                    val col = (offset.x / cellTotalPx).toInt()
                                    val row = (offset.y / cellTotalPx).toInt()
                                    if (col in 0 until weeksToShow && row in 0..6) {
                                        val match = heatmapDays.find { it.weekIndex == col && it.dayOfWeek == row }
                                        selectedDay = if (selectedDay == match) null else match
                                    }
                                }
                            }
                    ) {
                        val cellPx = cellSizeDp.toPx()
                        val spacingPx = cellSpacingDp.toPx()
                        val cornerPx = 3.dp.toPx()

                        for (day in heatmapDays) {
                            val x = day.weekIndex * (cellPx + spacingPx)
                            val y = day.dayOfWeek * (cellPx + spacingPx)

                            val cellColor = when {
                                day.count == 0 -> emptyCellColor
                                day.count in 1..2 -> primaryColor.copy(alpha = 0.35f)
                                day.count in 3..5 -> primaryColor.copy(alpha = 0.60f)
                                day.count in 6..9 -> primaryColor.copy(alpha = 0.85f)
                                else -> primaryColor
                            }

                            drawRoundRect(
                                color = cellColor,
                                topLeft = Offset(x, y),
                                size = Size(cellPx, cellPx),
                                cornerRadius = CornerRadius(cornerPx, cornerPx)
                            )

                            // Outline if selected
                            if (selectedDay == day) {
                                drawRoundRect(
                                    color = onSurfaceVariant,
                                    topLeft = Offset(x - 1, y - 1),
                                    size = Size(cellPx + 2, cellPx + 2),
                                    cornerRadius = CornerRadius(cornerPx + 1, cornerPx + 1),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Selected Day Pill or Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selected day feedback
            AnimatedVisibility(
                visible = selectedDay != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedDay?.let { day ->
                    Text(
                        text = "${day.count} ${if (day.count == 1) "item" else "items"} on ${day.dateString}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }
            }
            if (selectedDay == null) {
                Text(
                    text = "Tap any day to view activity",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Legend: Less [][][][] More
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(text = "Less", fontSize = 9.sp, color = onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.width(2.dp))
                Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(emptyCellColor))
                Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(primaryColor.copy(alpha = 0.35f)))
                Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(primaryColor.copy(alpha = 0.60f)))
                Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(primaryColor.copy(alpha = 0.85f)))
                Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(primaryColor))
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "More", fontSize = 9.sp, color = onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

private data class HeatmapCalculation(
    val days: List<HeatmapDay>,
    val months: List<Pair<Int, String>>,
    val currentStreak: Int,
    val maxStreak: Int,
    val totalCaptures: Int
)

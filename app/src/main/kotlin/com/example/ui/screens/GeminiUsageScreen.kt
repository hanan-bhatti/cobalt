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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.CollapsibleCommonTopBar
import com.example.ui.components.bounceClick
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.GeminiUsageTracker
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private data class DayActivity(
    val key: String,
    val label: String,
    val dateSub: String,
    val tokens: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiUsageScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        GeminiUsageTracker.init(context)
    }

    val stats by GeminiUsageTracker.usageStats.collectAsState()
    val geminiApiKey by viewModel.settingsRepository.geminiApiKey.collectAsState()
    val selectedModel by viewModel.settingsRepository.selectedModel.collectAsState()

    var showResetConfirm by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 160.dp)
    val density = LocalDensity.current

    val modelDisplay = remember(selectedModel) {
        val clean = selectedModel.substringAfterLast("/")
        when {
            clean.contains("flash-lite", ignoreCase = true) -> "Gemini Flash Lite"
            clean.contains("flash", ignoreCase = true) -> "Gemini 2.5 Flash"
            clean.contains("pro", ignoreCase = true) -> "Gemini 2.5 Pro"
            clean.isNotBlank() -> clean.replace("-", " ").replaceFirstChar { it.uppercase() }
            else -> "Gemini AI"
        }
    }

    val maskedKey = remember(geminiApiKey) {
        if (geminiApiKey.length > 8) {
            geminiApiKey.take(4) + "••••••••" + geminiApiKey.takeLast(4)
        } else "••••••••"
    }

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
            // Status Banner Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val hasKey = geminiApiKey.isNotBlank()
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_ai_brain),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = modelDisplay,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (hasKey) Color(0xFF10B981) else Color(0xFFEF4444))
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (hasKey) "Key: $maskedKey" else "API key required in Intelligence settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Top Metric Counters Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Tokens",
                        value = NumberFormat.getNumberInstance().format(stats.totalTokens),
                        subtitle = "${NumberFormat.getNumberInstance().format(stats.promptTokens)} in · ${NumberFormat.getNumberInstance().format(stats.candidateTokens)} out",
                        iconRes = R.drawable.ic_custom_api_chip,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "API Calls",
                        value = "${stats.totalRequests}",
                        subtitle = "${stats.ocrRequests} OCR · ${stats.audioRequests} Voice",
                        iconRes = R.drawable.ic_custom_ai_brain,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Token Breakdown: Prompt vs Output
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Token Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        val promptRatio = if (stats.totalTokens > 0) {
                            (stats.promptTokens.toFloat() / stats.totalTokens.toFloat()).coerceIn(0f, 1f)
                        } else 0.5f

                        val promptPct = (promptRatio * 100).roundToInt()
                        val candPct = (100 - promptPct).coerceAtLeast(0)

                        // Clean Segmented Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(promptRatio.coerceAtLeast(0.04f))
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight((1f - promptRatio).coerceAtLeast(0.04f))
                                    .fillMaxHeight()
                                    .background(Color(0xFF8B5CF6))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Prompt: ${NumberFormat.getNumberInstance().format(stats.promptTokens)} ($promptPct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8B5CF6)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Response: ${NumberFormat.getNumberInstance().format(stats.candidateTokens)} ($candPct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Task Activity (Cleaned of verbose boilerplate)
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Task Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        TaskRow(
                            title = "Screen Capture OCR",
                            requests = stats.ocrRequests.toLong(),
                            tokens = stats.ocrTokens,
                            iconRes = R.drawable.ic_custom_ocr,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        TaskRow(
                            title = "Voice Transcription",
                            requests = stats.audioRequests.toLong(),
                            tokens = stats.audioTokens,
                            iconRes = R.drawable.ic_custom_voice,
                            tint = Color(0xFFF59E0B)
                        )
                    }
                }
            }

            // Daily History Section: Sleek 7-Day Bar Activity
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        val days = remember(stats.dailyUsage) {
                            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val subFormat = SimpleDateFormat("MMM d", Locale.US)
                            (6 downTo 0).map { offset ->
                                val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
                                val key = dayFormat.format(c.time)
                                val label = when (offset) {
                                    0 -> "Today"
                                    1 -> "Yesterday"
                                    else -> SimpleDateFormat("EEE", Locale.US).format(c.time)
                                }
                                val dateSub = subFormat.format(c.time)
                                val tokens = stats.dailyUsage[key] ?: stats.dailyUsage[dateSub] ?: 0L
                                DayActivity(key, label, dateSub, tokens)
                            }
                        }

                        var selectedDayIndex by remember(days) {
                            // Default to yesterday if it has tokens, otherwise today
                            val yesterdayIdx = days.indexOfFirst { it.label == "Yesterday" }
                            val initial = if (yesterdayIdx >= 0 && days[yesterdayIdx].tokens > 0) {
                                yesterdayIdx
                            } else {
                                days.lastIndex
                            }
                            mutableStateOf(initial.coerceAtLeast(0))
                        }

                        val selectedDay = days.getOrNull(selectedDayIndex) ?: days.last()
                        val maxDayTokens = remember(days) {
                            days.maxOfOrNull { it.tokens }?.coerceAtLeast(100L) ?: 100L
                        }

                        // Header with selected day callout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Activity (Past 7 Days)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${selectedDay.label}: ${NumberFormat.getNumberInstance().format(selectedDay.tokens)} tokens",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedDay.tokens > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // 7 Vertical Activity Bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            days.forEachIndexed { index, day ->
                                val isSelected = index == selectedDayIndex
                                val ratio = (day.tokens.toFloat() / maxDayTokens.toFloat()).coerceIn(0f, 1f)
                                val barHeightDp = (80 * ratio).dp.coerceAtLeast(6.dp)

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { selectedDayIndex = index }
                                        .padding(horizontal = 2.dp)
                                ) {
                                    // Top micro label for token count
                                    if (day.tokens > 0) {
                                        Text(
                                            text = if (day.tokens >= 1000) "${(day.tokens / 1000f).roundToInt()}k" else "${day.tokens}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(14.dp))
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Bar pill
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height(barHeightDp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected && day.tokens > 0 -> MaterialTheme.colorScheme.primary
                                                    day.tokens > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                                                    isSelected -> MaterialTheme.colorScheme.outlineVariant
                                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                }
                                            )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Day label at bottom
                                    Text(
                                        text = when (day.label) {
                                            "Today" -> "Today"
                                            "Yesterday" -> "Yest"
                                            else -> day.label.take(3)
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reset Usage Button (Minimal & Unobtrusive)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.bounceClick()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_delete),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset Usage Statistics",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        CollapsibleCommonTopBar(
            title = "Gemini AI Usage",
            subtitle = "Tokens, requests & quota metrics",
            collapseFraction = topBarState.collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigateBack
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Usage Metrics?") },
            text = { Text("This will permanently clear token counters and request history recorded on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        GeminiUsageTracker.resetStats(context)
                        showResetConfirm = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    iconRes: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TaskRow(
    title: String,
    requests: Long,
    tokens: Long,
    iconRes: Int,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$requests reqs",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${NumberFormat.getNumberInstance().format(tokens)} tok",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

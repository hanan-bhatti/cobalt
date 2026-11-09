package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.NoteStorageManager

private data class StorageSegment(val label: String, val bytes: Long, val color: Color)

/**
 * Clean app storage breakdown card showing high-level storage composition,
 * segmented meter, and transparent breakdown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageUsageCard(report: NoteStorageManager.Report?, busy: Boolean) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val cacheAndSystem = (report?.cache ?: 0) + (report?.unattached ?: 0) +
        (report?.recovery ?: 0) + (report?.database ?: 0) + (report?.other ?: 0)

    val segments = listOf(
        StorageSegment("Notes & media", (report?.current ?: 0) + (report?.media ?: 0), colors.primary),
        StorageSegment("History", report?.older ?: 0, colors.tertiary),
        StorageSegment("Trash", report?.trash ?: 0, colors.error),
        StorageSegment("Cache & system", cacheAndSystem, colors.secondary)
    )

    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Stored by Cobalt", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                if (report != null) {
                    Text(
                        formatNoteSize(context, report.total),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (busy) {
                    SkeletonBox(
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 2.dp)
                            .width(135.dp)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Text(
                        "—",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (report == null) {
                if (busy) {
                    // Segmented bar skeleton matching the full height and rounded corners
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                        shape = RoundedCornerShape(9.dp)
                    )

                    // Legend breakdown skeleton (4 items matching the high-level categories)
                    val skeletonLegendWidths = remember {
                        listOf(125.dp, 85.dp, 70.dp, 120.dp)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (itemWidth in skeletonLegendWidths) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                SkeletonBox(Modifier.size(8.dp), shape = CircleShape)
                                SkeletonBox(Modifier.width(itemWidth).height(14.dp), shape = RoundedCornerShape(4.dp))
                            }
                        }
                    }

                    // Footnotes and descriptions skeleton placeholders
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBox(Modifier.fillMaxWidth(0.65f).height(12.dp), shape = RoundedCornerShape(4.dp))
                        SkeletonBox(Modifier.fillMaxWidth(0.92f).height(12.dp), shape = RoundedCornerShape(4.dp))
                    }
                } else {
                    Text(
                        "Storage details unavailable. Pull down to refresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                val description = segments.joinToString { "${it.label}: ${formatNoteSize(context, it.bytes)}" }
                Row(
                    Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))
                        .background(colors.surfaceContainerHighest).testTag("storage_usage_bar")
                        .semantics { contentDescription = "App storage breakdown. $description" },
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (segment in segments.filter { it.bytes > 0 }) key(segment.label) {
                        val fraction by animateFloatAsState(
                            (segment.bytes.toDouble() / report.total.coerceAtLeast(1)).toFloat(),
                            label = "storage-${segment.label}"
                        )
                        Box(Modifier.fillMaxHeight().weight(fraction.coerceAtLeast(0.0001f)).background(segment.color))
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (segment in segments) Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(Modifier.size(8.dp).background(segment.color, CircleShape))
                        Text(
                            "${segment.label} · ${formatNoteSize(context, segment.bytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "Media: ${formatNoteSize(context, report.images)} images · ${formatNoteSize(context, report.video)} video · ${formatNoteSize(context, report.audio)} audio" +
                        if (report.otherMedia > 0) " · ${formatNoteSize(context, report.otherMedia)} attachments" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    "All notes and media are stored locally on this device. Cache and temporary space can be cleared safely anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

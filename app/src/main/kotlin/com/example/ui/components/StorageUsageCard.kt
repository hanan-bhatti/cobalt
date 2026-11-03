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

/** App-data composition, not a misleading phone-capacity meter. Labels also convey every category. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageUsageCard(report: NoteStorageManager.Report?, busy: Boolean) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val segments = listOf(
        StorageSegment("Notes & media", (report?.current ?: 0) + (report?.media ?: 0), colors.primary),
        StorageSegment("History", report?.older ?: 0, colors.tertiary),
        StorageSegment("Trash", report?.trash ?: 0, colors.error),
        StorageSegment("Unlinked", report?.unattached ?: 0, colors.secondary),
        StorageSegment("Recovery", report?.recovery ?: 0, colors.tertiaryContainer),
        StorageSegment("Cache", report?.cache ?: 0, colors.primaryContainer),
        StorageSegment("Database & settings", (report?.database ?: 0) + (report?.other ?: 0), colors.outline)
    )
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Stored by Cobalt", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(if (report == null) "—" else formatNoteSize(context, report.total),
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            }
            if (report == null) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Text("Storage details unavailable. Try Refresh.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val description = segments.joinToString { "${it.label}: ${formatNoteSize(context, it.bytes)}" }
                Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))
                    .background(colors.surfaceContainerHighest).testTag("storage_usage_bar")
                    .semantics { contentDescription = "App storage breakdown. $description" },
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (segment in segments.filter { it.bytes > 0 }) key(segment.label) {
                        val fraction by animateFloatAsState((segment.bytes.toDouble() / report.total.coerceAtLeast(1)).toFloat(),
                            label = "storage-${segment.label}")
                        Box(Modifier.fillMaxHeight().weight(fraction.coerceAtLeast(0.0001f)).background(segment.color))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (segment in segments) Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(8.dp).background(segment.color, CircleShape))
                        Text("${segment.label} · ${formatNoteSize(context, segment.bytes)}",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

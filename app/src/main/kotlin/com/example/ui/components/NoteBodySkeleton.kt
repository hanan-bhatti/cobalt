package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Static placeholders keep idle CPU low; no endless shimmer animation or parsing label. */
@Composable
fun NoteBodySkeleton(modifier: Modifier = Modifier, lines: Int = 6) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(lines) { line ->
            Box(Modifier.fillMaxWidth(when (line % 4) { 1 -> 0.86f; 3 -> 0.62f; else -> 1f })
                .height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.09f)))
        }
    }
}

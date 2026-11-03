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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Reusable modifier that applies a smooth, subtle shining/shimmer sweep animation
 * to indicate an AI or smart-suggested folder recommendation.
 */
fun Modifier.folderSuggestionShimmer(
    color: Color,
    enabled: Boolean = true
): Modifier = if (!enabled) this else composed {
    val transition = rememberInfiniteTransition(label = "folder_suggestion_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_sweep"
    )

    drawWithContent {
        drawContent()
        val width = size.width
        val height = size.height
        if (width > 0f && height > 0f) {
            val startX = width * translateAnim
            val bandWidth = (width * 0.7f).coerceAtLeast(80f)
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    color.copy(alpha = 0.08f),
                    color.copy(alpha = 0.26f),
                    color.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                start = Offset(startX - bandWidth, 0f),
                end = Offset(startX + bandWidth, height)
            )
            drawRect(brush = shimmerBrush)
        }
    }
}

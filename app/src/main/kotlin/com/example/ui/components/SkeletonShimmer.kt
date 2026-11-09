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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a smooth and gentle shimmer highlight sweep over a placeholder shape.
 * Fully adapts to both Light and Dark themes.
 */
fun Modifier.skeletonShimmer(
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color? = null,
    highlightColor: Color? = null
): Modifier = composed {
    val bg = baseColor ?: MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
    val hl = highlightColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_sweep"
    )

    this
        .clip(shape)
        .background(bg)
        .drawWithContent {
            drawContent()
            val width = size.width
            val height = size.height
            if (width > 0f && height > 0f) {
                val startX = width * translateAnim
                val bandWidth = (width * 0.7f).coerceAtLeast(60f)
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        hl,
                        Color.Transparent
                    ),
                    start = Offset(startX - bandWidth, 0f),
                    end = Offset(startX + bandWidth, height)
                )
                drawRect(brush = shimmerBrush)
            }
        }
}

/**
 * Generic placeholder block for titles, cards, and bars with shimmer animation.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color? = null,
    highlightColor: Color? = null
) {
    Box(modifier = modifier.skeletonShimmer(shape = shape, baseColor = baseColor, highlightColor = highlightColor))
}

/**
 * Skeleton pill placeholder for settings badge values (e.g. "3.1 kB").
 */
@Composable
fun SkeletonBadge(
    width: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(22.dp)
            .skeletonShimmer(
                shape = CircleShape,
                baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
    )
}

/**
 * Skeleton placeholder item matching [ExpressiveSettingsNavigationItem] and storage list rows.
 */
@Composable
fun SkeletonStorageRow(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    hasBadge: Boolean = false,
    badgeWidth: Dp = 44.dp
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.48f).height(16.dp),
                    shape = RoundedCornerShape(4.dp)
                )
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.72f).height(12.dp),
                    shape = RoundedCornerShape(4.dp)
                )
            }
            if (hasBadge) {
                SkeletonBadge(width = badgeWidth)
            }
        }
    }
}

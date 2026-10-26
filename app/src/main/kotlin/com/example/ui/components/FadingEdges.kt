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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.horizontalFadingEdges(
    scrollState: ScrollState,
    fadeWidth: Dp = 20.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val canScrollBack = scrollState.canScrollBackward
        val canScrollForward = scrollState.canScrollForward
        val fadeWidthPx = fadeWidth.toPx()

        if (canScrollBack && fadeWidthPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = fadeWidthPx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (canScrollForward && fadeWidthPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - fadeWidthPx,
                    endX = size.width
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

fun Modifier.horizontalFadingEdges(
    lazyListState: LazyListState,
    fadeWidth: Dp = 20.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val canScrollBack = lazyListState.canScrollBackward
        val canScrollForward = lazyListState.canScrollForward
        val fadeWidthPx = fadeWidth.toPx()

        if (canScrollBack && fadeWidthPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = fadeWidthPx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (canScrollForward && fadeWidthPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - fadeWidthPx,
                    endX = size.width
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/*
 * Second Brain - A universal capture and personal knowledge archive
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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Spring animations per STANDARDS: dampingRatio ≈ 0.75–0.8 for smooth settlement
private val DRAG_SCALE_SPRING = spring<Float>(dampingRatio = 0.8f, stiffness = 300f)
private val DRAG_ELEVATION_SPRING = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.8f, stiffness = 300f)
private val DROP_SETTLEMENT_SPRING = spring<Float>(dampingRatio = 0.65f, stiffness = 250f) // ~0.2 bounce

fun Modifier.dragToReorder(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    draggingItemId: Int? = null,
    onDraggingItemChange: (Int?) -> Unit = {}
): Modifier {
    return this.then(
        Modifier.pointerInput(Unit) {
            var initialDragPosition: Offset? = null
            var currentDragPosition: Offset? = null
            var draggedItem: LazyListItemInfo? = null
            var draggingJob: Job? = null
            var isDragActive = false

            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    lazyListState.layoutInfo.visibleItemsInfo.firstOrNull<LazyListItemInfo> { item ->
                        offset.y.toInt() in item.offset..(item.offset + item.size)
                    }?.also {
                        draggedItem = it
                        initialDragPosition = offset
                        currentDragPosition = offset
                        isDragActive = true
                        onDraggingItemChange(it.index)
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    currentDragPosition = currentDragPosition?.plus(dragAmount)

                    val currentPos = currentDragPosition?.y ?: return@detectDragGesturesAfterLongPress
                    val dragged = draggedItem ?: return@detectDragGesturesAfterLongPress

                    val targetItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull<LazyListItemInfo> { item ->
                        currentPos.toInt() in item.offset..(item.offset + item.size)
                    }

                    if (targetItem != null && targetItem.index != dragged.index) {
                        onMove(dragged.index, targetItem.index)
                        draggedItem = targetItem
                    }
                },
                onDragEnd = {
                    isDragActive = false
                    initialDragPosition = null
                    currentDragPosition = null
                    draggedItem = null
                    onDraggingItemChange(null)
                    onDragEnd()
                },
                onDragCancel = {
                    isDragActive = false
                    initialDragPosition = null
                    currentDragPosition = null
                    draggedItem = null
                    onDraggingItemChange(null)
                    onDragEnd()
                }
            )
        }
    )
}

/**
 * Apply visual feedback (scale + elevation) to a dragged item.
 * Use this on individual list items to show selection state during drag.
 *
 * Must be @Composable: it calls animateFloatAsState/animateDpAsState, which are
 * @Composable APIs and cannot be invoked from a plain Modifier extension function.
 */
@Composable
fun Modifier.draggedItemVisuals(
    isDragging: Boolean,
    reduceMotion: Boolean = false
): Modifier {
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = if (reduceMotion) snapSpec<Float>() else DRAG_SCALE_SPRING,
        label = "dragScale"
    )

    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = if (reduceMotion) snapSpec<androidx.compose.ui.unit.Dp>() else DRAG_ELEVATION_SPRING,
        label = "dragElevation"
    )

    return this.graphicsLayer(
        scaleX = dragScale,
        scaleY = dragScale,
        shadowElevation = dragElevation.value
    )
}

// Renamed from snap() to avoid shadowing androidx.compose.animation.core.snap()
private fun <T> snapSpec() = spring<T>(dampingRatio = 1f, stiffness = 10000f)


fun Modifier.dragToReorderGrid(
    lazyGridState: LazyGridState,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    draggingItemId: Int? = null,
    onDraggingItemChange: (Int?) -> Unit = {}
): Modifier {
    return this.then(
        Modifier.pointerInput(Unit) {
            var initialDragPosition: Offset? = null
            var currentDragPosition: Offset? = null
            var draggedItem: LazyGridItemInfo? = null
            var isDragActive = false

            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    lazyGridState.layoutInfo.visibleItemsInfo.firstOrNull<LazyGridItemInfo> { item ->
                        val rect = Rect(
                            offset = Offset(item.offset.x.toFloat(), item.offset.y.toFloat()),
                            size = Size(item.size.width.toFloat(), item.size.height.toFloat())
                        )
                        rect.contains(offset)
                    }?.also {
                        draggedItem = it
                        initialDragPosition = offset
                        currentDragPosition = offset
                        isDragActive = true
                        onDraggingItemChange(it.index)
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    currentDragPosition = currentDragPosition?.plus(dragAmount)

                    val currentPos = currentDragPosition ?: return@detectDragGesturesAfterLongPress
                    val dragged = draggedItem ?: return@detectDragGesturesAfterLongPress

                    val targetItem = lazyGridState.layoutInfo.visibleItemsInfo.firstOrNull<LazyGridItemInfo> { item ->
                        val rect = Rect(
                            offset = Offset(item.offset.x.toFloat(), item.offset.y.toFloat()),
                            size = Size(item.size.width.toFloat(), item.size.height.toFloat())
                        )
                        rect.contains(currentPos)
                    }

                    if (targetItem != null && targetItem.index != dragged.index) {
                        onMove(dragged.index, targetItem.index)
                        draggedItem = targetItem
                    }
                },
                onDragEnd = {
                    isDragActive = false
                    initialDragPosition = null
                    currentDragPosition = null
                    draggedItem = null
                    onDraggingItemChange(null)
                    onDragEnd()
                },
                onDragCancel = {
                    isDragActive = false
                    initialDragPosition = null
                    currentDragPosition = null
                    draggedItem = null
                    onDraggingItemChange(null)
                    onDragEnd()
                }
            )
        }
    )
}

package com.example.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun Modifier.dragToReorder(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    return this.then(
        Modifier.pointerInput(Unit) {
            var initialDragPosition: Offset? = null
            var currentDragPosition: Offset? = null
            var draggedItem: LazyListItemInfo? = null
            var draggingJob: Job? = null

            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                        offset.y.toInt() in item.offset..(item.offset + item.size)
                    }?.also {
                        draggedItem = it
                        initialDragPosition = offset
                        currentDragPosition = offset
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    currentDragPosition = currentDragPosition?.plus(dragAmount)

                    val currentPos = currentDragPosition?.y ?: return@detectDragGesturesAfterLongPress
                    val dragged = draggedItem ?: return@detectDragGesturesAfterLongPress

                    val targetItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                        currentPos.toInt() in item.offset..(item.offset + item.size)
                    }

                    if (targetItem != null && targetItem.index != dragged.index) {
                        onMove(dragged.index, targetItem.index)
                        draggedItem = targetItem
                    }
                },
                onDragEnd = {
                    initialDragPosition = null
                    currentDragPosition = null
                    draggedItem = null
                    onDragEnd()
                },
                onDragCancel = {
                    initialDragPosition = null
                    currentDragPosition = null
                    draggedItem = null
                    onDragEnd()
                }
            )
        }
    )
}


fun Modifier.dragToReorderGrid(
    lazyGridState: LazyGridState,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    return this.then(
        Modifier.pointerInput(Unit) {
            var initialDragPosition: Offset? = null
            var currentDragPosition: Offset? = null
            var draggedItem: androidx.compose.foundation.lazy.grid.LazyGridItemInfo? = null

            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    lazyGridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                        val rect = androidx.compose.ui.geometry.Rect(
                            offset = androidx.compose.ui.geometry.Offset(item.offset.x.toFloat(), item.offset.y.toFloat()),
                            size = androidx.compose.ui.geometry.Size(item.size.width.toFloat(), item.size.height.toFloat())
                        )
                        rect.contains(offset)
                    }?.also {
                        draggedItem = it
                        initialDragPosition = offset
                        currentDragPosition = offset
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    currentDragPosition = currentDragPosition?.plus(dragAmount)

                    val currentPos = currentDragPosition ?: return@detectDragGesturesAfterLongPress
                    val dragged = draggedItem ?: return@detectDragGesturesAfterLongPress

                    val targetItem = lazyGridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                        val rect = androidx.compose.ui.geometry.Rect(
                            offset = androidx.compose.ui.geometry.Offset(item.offset.x.toFloat(), item.offset.y.toFloat()),
                            size = androidx.compose.ui.geometry.Size(item.size.width.toFloat(), item.size.height.toFloat())
                        )
                        rect.contains(currentPos)
                    }

                    if (targetItem != null && targetItem.index != dragged.index) {
                        onMove(dragged.index, targetItem.index)
                        draggedItem = targetItem
                    }
                },
                onDragEnd = {
                    initialDragPosition = null
                    currentDragPosition = null
                    draggedItem = null
                    onDragEnd()
                },
                onDragCancel = {
                    initialDragPosition = null
                    currentDragPosition = null
                    draggedItem = null
                    onDragEnd()
                }
            )
        }
    )
}

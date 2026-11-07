package com.example.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.viewmodel.CobaltViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared screen overlay. The ViewModel owns expiry, independent of a screen's composition. */
@Composable
fun DeleteUndoHost(viewModel: CobaltViewModel, modifier: Modifier = Modifier) {
    val item by viewModel.lastDeletedItemForUndo.collectAsState()
    val remaining = remember(item?.id) { Animatable(1f) }
    var offset by remember(item?.id) { mutableFloatStateOf(0f) }
    val dismissThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    LaunchedEffect(item) {
        if (item != null) {
            val duration = (viewModel.undoExpiresAt - SystemClock.elapsedRealtime()).coerceIn(0, 4_000).toInt()
            remaining.snapTo(duration / 4_000f)
            remaining.animateTo(0f, tween(duration, easing = LinearEasing))
        }
    }
    AnimatedVisibility(item != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        val color = MaterialTheme.colorScheme.primary
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp, shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("undo_toast")
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(item?.id) {
                    detectHorizontalDragGestures(onDragEnd = {
                        if (abs(offset) >= dismissThreshold) viewModel.clearUndoToast()
                        offset = 0f
                    }, onDragCancel = { offset = 0f }) { change, amount -> change.consume(); offset += amount }
                }
                .semantics { customActions = listOf(CustomAccessibilityAction("Dismiss") { viewModel.clearUndoToast(); true }) }
                .drawWithCache {
                    val inset = 1.dp.toPx()
                    val outline = Path().apply { addRoundRect(RoundRect(Rect(inset, inset, size.width - inset, size.height - inset), CornerRadius(21.dp.toPx()))) }
                    val measure = PathMeasure().apply { setPath(outline, false) }
                    val segment = Path()
                    onDrawWithContent {
                        drawContent()
                        segment.reset()
                        measure.getSegment(0f, measure.length * remaining.value, segment)
                        drawPath(segment, color, style = Stroke(2.dp.toPx()))
                    }
                }) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_custom_delete), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(if (viewModel.trashEnabled()) "Moved to Trash" else "Item deleted", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { viewModel.restoreLastDeletedItems() }, modifier = Modifier.testTag("undo_button")) { Text("Undo") }
            }
        }
    }
}

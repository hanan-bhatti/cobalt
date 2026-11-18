package com.example.ui.screens

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.SettingsBottomSheet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** A draft time: dismissal never changes the saved reminder schedule. */
@Composable
internal fun ReminderTimeSheet(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val hours = rememberLazyListState(initialFirstVisibleItemIndex = 1200 + (initialHour + 11) % 12)
    val minutes = rememberLazyListState(initialFirstVisibleItemIndex = 6000 + initialMinute)
    val period = rememberLazyListState(initialFirstVisibleItemIndex = initialHour / 12)
    val hourIndex by remember { derivedStateOf { hours.centeredIndex() } }
    val minuteIndex by remember { derivedStateOf { minutes.centeredIndex() } }
    val periodIndex by remember { derivedStateOf { period.centeredIndex() } }
    val selectedHour = (hourIndex % 12 + 1) % 12 + periodIndex.coerceIn(0, 1) * 12
    val selectedMinute = minuteIndex % 60
    var presetRequest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var applyingPreset by remember { mutableStateOf(false) }
    val moving = applyingPreset || hours.isScrollInProgress || minutes.isScrollInProgress || period.isScrollInProgress
    val timeFormat = remember(context.resources.configuration) {
        java.text.SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "hma"),
            Locale.getDefault()
        )
    }
    fun timeLabel(hour: Int, minute: Int): String = timeFormat.format(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.time)

    LaunchedEffect(presetRequest) {
        val targetHour = presetRequest?.first ?: return@LaunchedEffect
        applyingPreset = true
        try {
            coroutineScope {
                launch { hours.rollTo((targetHour + 11) % 12, 12, reduceMotion) }
                launch { minutes.rollTo(0, 60, reduceMotion) }
                launch {
                    if (reduceMotion) period.scrollToItem(targetHour / 12)
                    else period.animateScrollToItem(targetHour / 12)
                }
            }
        } finally {
            applyingPreset = false
        }
    }

    SettingsBottomSheet("Reminder time", "Choose a daily reminder time. Android may delay delivery.", onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(9, 14, 20).forEach { hour ->
                    val selected = !moving && selectedHour == hour && selectedMinute == 0
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton, onClick = {
                            // A new request also handles reselecting the same preset after a manual edit.
                            presetRequest = hour to ((presetRequest?.second ?: 0) + 1)
                        })
                    ) {
                        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(timeLabel(hour, 0), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            val rowHeight = 48.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Hour", "Minute", "AM / PM").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
                Box(Modifier.fillMaxWidth().height(rowHeight).align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderWheel("Hour", hours, 12, 2400, rowHeight, reduceMotion, Modifier.weight(1f)) { (it + 1).toString() }
                    ReminderWheel("Minute", minutes, 60, 12000, rowHeight, reduceMotion, Modifier.weight(1f)) {
                        String.format(Locale.getDefault(), "%02d", it)
                    }
                    ReminderWheel("AM or PM", period, 2, 2, rowHeight, reduceMotion, Modifier.weight(1f)) { if (it == 0) "AM" else "PM" }
                }
            }
            Button(
                enabled = !moving,
                onClick = { onSave(selectedHour, selectedMinute) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Save time") }
        }
    }
}

/** Padding places the first/last values in the same central selection slot as every other value. */
private fun LazyListState.centeredIndex(): Int {
    val layout = layoutInfo
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    return layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
        ?: firstVisibleItemIndex
}

private suspend fun LazyListState.rollTo(value: Int, cycle: Int, reduceMotion: Boolean) {
    val current = centeredIndex()
    val forward = (value - current % cycle + cycle) % cycle
    val delta = if (forward > cycle / 2) forward - cycle else forward
    val target = (current + delta).coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
    if (reduceMotion) scrollToItem(target) else animateScrollToItem(target)
}

@Composable
private fun ReminderWheel(
    label: String,
    state: LazyListState,
    cycle: Int,
    count: Int,
    rowHeight: androidx.compose.ui.unit.Dp,
    reduceMotion: Boolean,
    modifier: Modifier,
    format: (Int) -> String
) {
    val scope = rememberCoroutineScope()
    val selected by remember(state) { derivedStateOf { state.centeredIndex() } }
    fun step(delta: Int): Boolean {
        scope.launch {
            val target = (state.centeredIndex() + delta).coerceIn(0, count - 1)
            state.scrollToItem(target)
        }
        return true
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Center),
        contentPadding = PaddingValues(vertical = rowHeight * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(rowHeight * 5).semantics {
            contentDescription = label
            stateDescription = format(selected % cycle)
            customActions = listOf(
                CustomAccessibilityAction("Previous $label") { step(-1) },
                CustomAccessibilityAction("Next $label") { step(1) }
            )
        }
    ) {
        items(count, key = { it }) { index ->
            Box(
                Modifier.fillMaxWidth().height(rowHeight).graphicsLayer {
                    val layout = state.layoutInfo
                    val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                    val distance = if (item == null) 0f else (item.offset + item.size / 2f - center) / item.size
                    val amount = abs(distance).coerceAtMost(2f) / 2f
                    alpha = 1f - amount * 0.72f
                    if (!reduceMotion) {
                        rotationX = -distance * 16f
                        scaleX = 1f - amount * 0.12f
                        scaleY = scaleX
                        cameraDistance = 16f * density
                    }
                }.selectable(index == selected, role = Role.RadioButton, onClick = {
                    scope.launch {
                        if (reduceMotion) state.scrollToItem(index) else state.animateScrollToItem(index)
                    }
                }),
                contentAlignment = Alignment.Center
            ) {
                Text(format(index % cycle), style = MaterialTheme.typography.headlineMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

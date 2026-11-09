package com.example.ui.components

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import kotlinx.coroutines.*

/**
 * Clean outlined button that fills itself from left to right as the user presses and holds.
 * Releasing, moving out, losing focus or backgrounding cancels the hold immediately.
 */
@Composable
fun HoldToClearStorageButton(enabled: Boolean, onConfirmed: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onConfirmed)
    val currentlyEnabled by rememberUpdatedState(enabled)
    var progress by remember { mutableFloatStateOf(0f) }
    var hold by remember { mutableStateOf<Job?>(null) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    fun cancel() { hold?.cancel(); hold = null; progress = 0f }
    fun begin() {
        if (!enabled || !resumed || hold?.isActive == true) return
        hold = scope.launch {
            val start = SystemClock.elapsedRealtime()
            try {
                while (true) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    progress = (elapsed / 5000f).coerceIn(0f, 1f)
                    if (elapsed >= 5000) { if (currentlyEnabled && resumed) callback(); break }
                    delay(25)
                }
            } finally { progress = 0f }
        }
    }

    LaunchedEffect(enabled) { if (!enabled) cancel() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) cancel()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); cancel() }
    }

    val shape = RoundedCornerShape(16.dp)
    val errorColor = MaterialTheme.colorScheme.error
    val text = if (progress == 0f) "Hold to clear storage (5s)"
               else "Keep holding · ${kotlin.math.ceil((1f - progress) * 5).toInt()}s"

    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (enabled) errorColor else errorColor.copy(alpha = 0.38f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .testTag("clear_disposable_hold")
            .semantics { role = Role.Button; contentDescription = "Press and hold for five seconds to clear disposable storage" }
            .onFocusChanged { if (!it.hasFocus) cancel() }
            .onKeyEvent {
                if (it.key != Key.Enter && it.key != Key.Spacebar) false
                else { if (it.type == KeyEventType.KeyDown) { if (it.nativeKeyEvent.repeatCount == 0) begin() } else cancel(); true }
            }
            .focusable(enabled)
            .pointerInput(enabled, resumed) {
                detectTapGestures(onPress = {
                    begin()
                    try { tryAwaitRelease() } finally { cancel() }
                })
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Fill layer that animates / fills from left to right as progress increases
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .align(Alignment.CenterStart)
                    .background(errorColor)
            )

            // Centered label with delete icon and high contrast
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_delete),
                    contentDescription = null,
                    tint = if (progress > 0.45f) MaterialTheme.colorScheme.onError else errorColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (progress > 0.45f) MaterialTheme.colorScheme.onError else errorColor
                )
            }
        }
    }
}

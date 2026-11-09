package com.example.ui.components

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*

/** Releasing, moving out, losing focus or backgrounding cancels the entire hold. */
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
    val text = if (progress == 0f) "Hold for 5 seconds" else "Keep holding · ${kotlin.math.ceil((1f-progress)*5).toInt()}s"
    Surface(color = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
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
            }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onError, trackColor = MaterialTheme.colorScheme.onError.copy(alpha = .25f))
        }
    }
}

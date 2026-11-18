package com.example.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.media.recommendation.*
import com.example.ui.components.*
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ScreeningMorningSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ScreeningStore(context) }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(store.enabled) }
    var hour by remember { mutableIntStateOf(store.hour) }
    var minute by remember { mutableIntStateOf(store.minute) }
    var weekends by remember { mutableStateOf(store.weekends) }
    var reset by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            enabled = store.enabled; hour = store.hour; minute = store.minute; weekends = store.weekends
        } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun schedule(change: () -> Unit) {
        scope.launch {
            try { withContext(Dispatchers.IO) { change(); ScreeningWork.schedule(context, replace = true) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                enabled = store.enabled; hour = store.hour; minute = store.minute; weekends = store.weekends
                Toast.makeText(context, "Couldn't update the schedule. Try again.", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun enable(value: Boolean) { enabled = value; schedule { store.enabled = value; if (!value) ScreeningNotifications.cancelAll(context) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enable(true) else Toast.makeText(context, "Allow notifications to enable morning questions.", Toast.LENGTH_LONG).show()
    }
    NoteSettingsPage("Morning check-in", "One gentle question, when you want it.", onNavigateBack) {
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cobalt · Screening", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("How was this pick?", style = MaterialTheme.typography.titleMedium)
                    Text("Tap a question to give feedback or update progress.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SettingsSubsectionHeader("Check-in", description = "An optional question about your picks.",
                tooltipText = "At most one automatic question per day. Unanswered questions reduce how often we ask. Opening or ignoring a question never marks a title watched or read.")
        }
        item {
            ExpressiveSettingsGroup {
                ExpressiveSettingsSwitchItem("Morning questions", enabled, { target ->
                    if (!target) enable(false)
                    else if (ScreeningNotifications.available(context)) enable(true)
                    else if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
                }, subtitle = "Ask about your saved picks", shape = connectedPillShape(0, 3))
                ExpressiveSettingsNavigationItem("Morning time", {
                    TimePickerDialog(context, { _, h, m ->
                        if (h in 5..11) { hour = h; minute = m; schedule { store.hour = h; store.minute = m } }
                        else Toast.makeText(context, "Choose a morning time between 5:00 and 11:59.", Toast.LENGTH_LONG).show()
                    }, hour, minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                }, trailingBadge = String.format(Locale.getDefault(), "%02d:%02d", hour, minute), subtitle = "Local time · Android may deliver a little later", shape = connectedPillShape(1, 3), enabled = enabled)
                ExpressiveSettingsSwitchItem("Include weekends", weekends, { value -> weekends = value; schedule { store.weekends = value } }, enabled = enabled, shape = connectedPillShape(2, 3))
            }
        }
        item { Text("At most once a day. Unanswered questions reduce how often we ask.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (enabled) item {
            TextButton(onClick = {
                scope.launch {
                    try {
                        val shown = withContext(Dispatchers.IO) {
                            val pick = ScreeningService.ranked(context, cooldowns = false).firstOrNull()
                            if (pick == null || !ScreeningNotifications.available(context)) false else {
                                val now = System.currentTimeMillis()
                                val delivery = store.createDelivery(pick, now, ScreeningSchedule.day(now), false)
                                delivery != null && ScreeningNotifications.show(context, pick, delivery)
                            }
                        }
                        Toast.makeText(context, if (shown) "Preview question sent" else "No available pick, or Screening notifications are disabled.", Toast.LENGTH_LONG).show()
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { Toast.makeText(context, "Couldn't send the preview. Try again.", Toast.LENGTH_LONG).show() }
                }
            }) { Text("Preview notification") }
        }
        item { ExpressiveSettingsNavigationItem("Reset recommendation learning", { reset = true }, enabled = !busy, subtitle = "Your saved titles and progress are kept") }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Reset recommendation learning?") },
        text = { Text("Clear local preferences, hidden picks and pauses. Your saved titles and progress are kept.") },
        confirmButton = { TextButton(onClick = {
            reset = false; busy = true
            scope.launch {
                try { withContext(Dispatchers.IO) { store.resetLearning(); ScreeningNotifications.cancelAll(context) }; WidgetUpdater.update(context) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { Toast.makeText(context, "Couldn't reset learning. Please try again.", Toast.LENGTH_LONG).show() }
                finally { busy = false }
            }
        }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } })
}

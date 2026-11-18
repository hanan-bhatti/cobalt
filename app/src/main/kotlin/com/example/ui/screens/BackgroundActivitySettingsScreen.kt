package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.components.SettingsGroupLabel
import com.example.ui.components.SettingsIconBadge
import com.example.utils.BatteryOptimizationHelper

@Composable
fun BackgroundActivitySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var exempt by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    val extraDeviceSettings = remember { BatteryOptimizationHelper.isColorOSDevice() }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun openAppSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            error = null
        } catch (_: Exception) { error = "Could not open settings. Open Cobalt’s app settings from Android Settings." }
    }
    AiSettingsPage("Background activity", "Battery settings and background updates", onBack) {
        item {
            Surface(color = if (exempt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SettingsIconBadge(R.drawable.ic_settings_system_health,
                        containerColor = if (exempt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = if (exempt) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (exempt) "Battery optimization off" else "Battery optimization on", style = MaterialTheme.typography.headlineSmall,
                            color = if (exempt) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        Text(if (exempt) "Android’s battery optimization does not restrict Cobalt. Other device settings can still affect background updates."
                            else "Android may delay background updates to save battery. You can request an exemption for Cobalt.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (exempt) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        if (exempt) openAppSettings() else BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (exempt) "Open app settings" else "Allow background activity")
                    }
                    if (!exempt) TextButton(onClick = ::openAppSettings, modifier = Modifier.fillMaxWidth()) { Text("Open app settings") }
                }
            }
        }
        item {
            SettingsGroupLabel("Uses background activity")
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    BackgroundFeature(R.drawable.ic_settings_widgets, "Widget updates", "Refresh content on your home screen.")
                    BackgroundFeature(R.drawable.ic_custom_reminder, "Reminders", "Deliver reminders you have enabled.")
                    BackgroundFeature(R.drawable.ic_custom_sync, "Trakt sync", "Run enabled account sync tasks.")
                }
            }
        }
        if (extraDeviceSettings) item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device battery settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Your device may have additional background restrictions. If updates are delayed, review Cobalt’s background activity options in app settings.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        item { Text("Allowing background activity does not turn on reminders or Trakt sync. Each feature follows its own settings.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        error?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun BackgroundFeature(icon: Int, title: String, description: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingsIconBadge(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

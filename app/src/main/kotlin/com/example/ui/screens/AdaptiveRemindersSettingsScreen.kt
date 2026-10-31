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

package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.components.CollapsibleCommonTopBar
import com.example.ui.components.ExpressiveSettingsGroup
import com.example.ui.components.ExpressiveSettingsNavigationItem
import com.example.ui.components.ExpressiveSettingsSwitchItem
import com.example.ui.components.SettingsSubsectionHeader
import com.example.ui.components.bounceClick
import com.example.ui.components.connectedPillShape
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.AdaptiveReminderEngine
import com.example.util.AdaptiveReminderNotificationHelper
import com.example.util.AdaptiveReminderStore
import com.example.util.AdaptiveReminderWorker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveRemindersSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { AdaptiveReminderStore(context) }
    val coroutineScope = rememberCoroutineScope()

    var isEnabled by remember { mutableStateOf(store.isRemindersEnabled) }
    var deliveryHour by remember { mutableIntStateOf(store.deliveryHour) }
    var deliveryMinute by remember { mutableIntStateOf(store.deliveryMinute) }
    var inactivityDays by remember { mutableIntStateOf(store.inactivityDaysThreshold) }
    var prioritizeFolders by remember { mutableStateOf(store.prioritizeActiveFolders) }
    var includeWeekends by remember { mutableStateOf(store.includeWeekends) }

    var showResetDialog by remember { mutableStateOf(false) }
    val allItems by viewModel.allItems.collectAsState()

    // Permission launcher for Android 13+ (API 33+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                store.isRemindersEnabled = true
                isEnabled = true
                AdaptiveReminderWorker.schedule(context)
                Toast.makeText(context, "Adaptive reminders activated", Toast.LENGTH_SHORT).show()
            } else {
                store.isRemindersEnabled = false
                isEnabled = false
                Toast.makeText(context, "Notification permission is required for reminders.", Toast.LENGTH_LONG).show()
            }
        }
    )

    fun checkAndToggle(targetState: Boolean) {
        if (targetState) {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }

            if (hasPermission) {
                store.isRemindersEnabled = true
                isEnabled = true
                AdaptiveReminderWorker.schedule(context)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Toast.makeText(context, "Please enable notifications in device settings.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            store.isRemindersEnabled = false
            isEnabled = false
            AdaptiveReminderWorker.cancel(context)
        }
    }

    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 160.dp)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(topBarState.nestedScrollConnection)
    ) {
        val currentTopBarHeightDp = with(density) { topBarState.heightPx.toDp() }
        val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 12.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = bottomNavPadding + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Subsection 1: Service Status
            item {
                SettingsSubsectionHeader(
                    title = "Adaptive Reminders",
                    description = "Resurface notes and links you haven't reviewed recently.",
                    tooltipText = "Cobalt monitors notes you save and periodically resurfaces forgotten knowledge based on your reading patterns and inactivity period."
                )

                ExpressiveSettingsGroup {
                    ExpressiveSettingsSwitchItem(
                        title = "Enable Reminders",
                        subtitle = if (isEnabled) "Daily review notifications active" else "Notifications paused",
                        checked = isEnabled,
                        onCheckedChange = { checkAndToggle(it) },
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }

            // Subsection 2: Delivery Schedule (Shown when enabled)
            if (isEnabled) {
                item {
                    SettingsSubsectionHeader(
                        title = "Delivery Schedule",
                        description = "Choose when and how often forgotten items resurface.",
                        tooltipText = "Notifications will be delivered at your chosen time for notes that haven't been opened for at least the specified threshold."
                    )

                    val presets = listOf(
                        Triple(9, 0, "9:00 AM"),
                        Triple(14, 0, "2:00 PM"),
                        Triple(20, 0, "8:00 PM")
                    )
                    val isPresetSelected = presets.any { it.first == deliveryHour && it.second == deliveryMinute }

                    val formattedCustomTime = remember(deliveryHour, deliveryMinute) {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, deliveryHour)
                            set(Calendar.MINUTE, deliveryMinute)
                        }
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
                    }

                    ExpressiveSettingsGroup {
                        // Delivery Time Selector Item
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = connectedPillShape(0, 2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Delivery Time",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isPresetSelected) {
                                                presets.first { it.first == deliveryHour && it.second == deliveryMinute }.third
                                            } else {
                                                "Custom: $formattedCustomTime"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    presets.forEach { (hour, minute, label) ->
                                        val isSelected = deliveryHour == hour && deliveryMinute == minute
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    deliveryHour = hour
                                                    deliveryMinute = minute
                                                    store.deliveryHour = hour
                                                    store.deliveryMinute = minute
                                                    AdaptiveReminderWorker.schedule(context)
                                                }
                                                .bounceClick()
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    // Custom Pill Button
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (!isPresetSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                android.app.TimePickerDialog(
                                                    context,
                                                    { _, pickedHour, pickedMinute ->
                                                        deliveryHour = pickedHour
                                                        deliveryMinute = pickedMinute
                                                        store.deliveryHour = pickedHour
                                                        store.deliveryMinute = pickedMinute
                                                        AdaptiveReminderWorker.schedule(context)
                                                    },
                                                    deliveryHour,
                                                    deliveryMinute,
                                                    false
                                                ).show()
                                            }
                                            .bounceClick()
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = if (!isPresetSelected) formattedCustomTime else "Custom",
                                                fontSize = 12.sp,
                                                fontWeight = if (!isPresetSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (!isPresetSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Inactivity Threshold Selector Item
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = connectedPillShape(1, 2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Minimum Inactivity",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Only resurface items untouched for at least this period",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val thresholds = listOf(3 to "3 Days", 7 to "7 Days", 14 to "14 Days")
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    thresholds.forEach { (days, label) ->
                                        val isSelected = inactivityDays == days
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    inactivityDays = days
                                                    store.inactivityDaysThreshold = days
                                                }
                                                .bounceClick()
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Subsection 3: Preferences
                item {
                    SettingsSubsectionHeader(
                        title = "Preferences",
                        description = "Tune resurfacing synergy and weekend delivery.",
                        tooltipText = "Folder Focus Synergy prioritizes forgotten notes inside folders you are actively working in."
                    )

                    var ruleIdx = 0
                    val totalRules = 2

                    ExpressiveSettingsGroup {
                        ExpressiveSettingsSwitchItem(
                            title = "Folder Focus Synergy",
                            subtitle = "Prioritize overlooked notes inside folders you recently worked in",
                            checked = prioritizeFolders,
                            onCheckedChange = {
                                prioritizeFolders = it
                                store.prioritizeActiveFolders = it
                            },
                            shape = connectedPillShape(ruleIdx++, totalRules)
                        )

                        ExpressiveSettingsSwitchItem(
                            title = "Weekend Delivery",
                            subtitle = "Allow reminder notifications on Saturdays and Sundays",
                            checked = includeWeekends,
                            onCheckedChange = {
                                includeWeekends = it
                                store.includeWeekends = it
                            },
                            shape = connectedPillShape(ruleIdx++, totalRules)
                        )
                    }
                }

                // Subsection 4: Verification & Reset
                item {
                    SettingsSubsectionHeader(
                        title = "Verification",
                        description = "Test notification delivery or reset learned telemetry."
                    )

                    var diagIdx = 0
                    val totalDiag = 2

                    ExpressiveSettingsGroup {
                        ExpressiveSettingsNavigationItem(
                            title = "Send Test Reminder",
                            subtitle = "Preview how an adaptive reminder appears right now",
                            onClick = {
                                coroutineScope.launch {
                                    val candidate = withContext(Dispatchers.Default) {
                                        AdaptiveReminderEngine.selectBestCandidate(context, allItems)
                                    }
                                    if (candidate != null) {
                                        val posted = AdaptiveReminderNotificationHelper.showReminderNotification(
                                            context = context,
                                            item = candidate.item,
                                            reason = candidate.reason
                                        )
                                        if (posted) {
                                            Toast.makeText(context, "Reminder notification sent", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Could not post: check notification permission.", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "No candidate note found matching inactivity threshold.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            shape = connectedPillShape(diagIdx++, totalDiag)
                        )

                        ExpressiveSettingsNavigationItem(
                            title = "Reset Learning History",
                            subtitle = "Clear on-device review history and open counters",
                            onClick = { showResetDialog = true },
                            shape = connectedPillShape(diagIdx++, totalDiag)
                        )
                    }
                }
            }
        }

        // Top Bar
        CollapsibleCommonTopBar(
            title = "Adaptive Reminders",
            subtitle = "Self-learning knowledge resurfacing",
            collapseFraction = topBarState.collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigateBack
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Learning History?") },
            text = { Text("This will clear local interaction data (open frequencies and reminder response rates). Future reminders will start learning from scratch.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.resetLearningStats()
                        showResetDialog = false
                        Toast.makeText(context, "Learning history reset.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

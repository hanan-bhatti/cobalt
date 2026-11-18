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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.AdaptiveReminderEngine
import com.example.util.AdaptiveReminderNotificationHelper
import com.example.util.AdaptiveReminderStore
import com.example.util.AdaptiveReminderWorker
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveRemindersSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    onScreening: () -> Unit,
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

    var showResetSheet by remember { mutableStateOf(false) }
    var showTimeSheet by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }
    val allItems by viewModel.allItems.collectAsState()

    // Permission launcher for Android 13+ (API 33+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                store.isRemindersEnabled = true
                isEnabled = true
                AdaptiveReminderWorker.schedule(context)
                Toast.makeText(context, "Reminders enabled", Toast.LENGTH_SHORT).show()
            } else {
                store.isRemindersEnabled = false
                isEnabled = false
                Toast.makeText(context, "Reminders are off. Allow notifications for Cobalt in device settings to enable them.", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(context, "Enable notifications for Cobalt in device settings.", Toast.LENGTH_LONG).show()
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
    val topPadding = with(LocalDensity.current) { topBarState.heightPx.toDp() }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val formattedTime = remember(deliveryHour, deliveryMinute, context.resources.configuration) {
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, deliveryHour)
            set(Calendar.MINUTE, deliveryMinute)
        }
        android.text.format.DateFormat.getTimeFormat(context).format(time.time)
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(topBarState.nestedScrollConnection)) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = topPadding + 12.dp, start = 20.dp, end = 20.dp, bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsGroupLabel("Saved items")
                ExpressiveSettingsGroup {
                    ExpressiveSettingsSwitchItem(
                        title = "Reminders",
                        subtitle = "Revisit saved items you haven’t opened recently",
                        checked = isEnabled, onCheckedChange = ::checkAndToggle,
                        shape = connectedPillShape(0, 2), compact = true
                    )
                    ExpressiveSettingsNavigationItem(
                        title = "How reminders work",
                        subtitle = "How items are chosen and when reminders arrive",
                        leadingIcon = { SettingsIconBadge(R.drawable.ic_custom_info) },
                        onClick = { showHelpSheet = true },
                        shape = connectedPillShape(1, 2), compact = true
                    )
                }
            }
            if (isEnabled) {
                item {
                    SettingsGroupLabel("Schedule")
                    ExpressiveSettingsGroup {
                        ExpressiveSettingsNavigationItem(
                            title = "Reminder time", trailingBadge = formattedTime,
                            subtitle = "Android may delay delivery",
                            onClick = { showTimeSheet = true },
                            shape = connectedPillShape(0, 2), compact = true
                        )
                        ExpressiveSettingsSwitchItem(
                            title = "Include weekends", subtitle = "Send reminders on Saturdays and Sundays too",
                            checked = includeWeekends,
                            onCheckedChange = { includeWeekends = it; store.includeWeekends = it },
                            shape = connectedPillShape(1, 2), compact = true
                        )
                    }
                }
                item {
                    SettingsGroupLabel("Choose items")
                    ExpressiveSettingsGroup {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = connectedPillShape(0, 2)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Not opened for", style = MaterialTheme.typography.titleMedium)
                                    Text("Choose how long an item stays unopened before it can be suggested.",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(3, 7, 14).forEach { days ->
                                        val selected = inactivityDays == days
                                        Surface(
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape,
                                            modifier = Modifier.weight(1f).selectable(selected = selected, role = Role.RadioButton,
                                                onClick = { inactivityDays = days; store.inactivityDaysThreshold = days })
                                        ) {
                                            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                                Text("$days days", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        ExpressiveSettingsSwitchItem(
                            title = "Prioritize active folders",
                            subtitle = "Favor overlooked items in folders used recently",
                            checked = prioritizeFolders,
                            onCheckedChange = { prioritizeFolders = it; store.prioritizeActiveFolders = it },
                            shape = connectedPillShape(1, 2), compact = true
                        )
                    }
                }
                item {
                    SettingsGroupLabel("Test and reset")
                    ExpressiveSettingsGroup {
                        ExpressiveSettingsNavigationItem(
                            title = "Send test reminder",
                            subtitle = "Send a notification if an eligible item is available",
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
                                            Toast.makeText(context, "Could not send reminder. Enable notifications for Cobalt in device settings.", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "No eligible items yet. Try a shorter inactivity period or wait until an item qualifies.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            shape = connectedPillShape(0, 2), compact = true
                        )
                        ExpressiveSettingsNavigationItem(
                            title = "Reset reminder history",
                            subtitle = "Clear the activity used to choose reminders",
                            onClick = { showResetSheet = true },
                            shape = connectedPillShape(1, 2), compact = true
                        )
                    }
                }
            }
            item {
                SettingsGroupLabel("Movies and shows")
                ExpressiveSettingsNavigationItem(
                    title = "Screening picks", subtitle = "Recommendations and morning check-ins",
                    leadingIcon = { SettingsIconBadge(R.drawable.ic_custom_movie) },
                    onClick = onScreening, compact = true
                )
            }
        }
        CollapsibleCommonTopBar(
            title = "Reminders", subtitle = "Revisit saved items",
            collapseFraction = topBarState.collapseFraction, headerHeight = topPadding,
            onBackClick = onNavigateBack
        )
    }

    if (showTimeSheet) {
        ReminderTimeSheet(
            initialHour = deliveryHour,
            initialMinute = deliveryMinute,
            onDismiss = { showTimeSheet = false },
            onSave = { hour, minute ->
                deliveryHour = hour
                deliveryMinute = minute
                store.deliveryHour = hour
                store.deliveryMinute = minute
                AdaptiveReminderWorker.schedule(context)
                showTimeSheet = false
            }
        )
    }
    if (showResetSheet) {
        SettingsBottomSheet(
            "Reset reminder history",
            "Clear reading and reminder activity used to choose reminders. Saved items and reminder settings stay unchanged. This cannot be undone.",
            { showResetSheet = false }
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Press and hold for five seconds. Release to cancel.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HoldToConfirmButton(
                    enabled = true, label = "Hold to reset (5s)",
                    accessibilityLabel = "Press and hold for five seconds to reset reminder history",
                    testTag = "reset_reminder_history_hold",
                    onConfirmed = {
                        store.resetLearningStats()
                        showResetSheet = false
                        Toast.makeText(context, "Reminder history reset", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
    if (showHelpSheet) ReminderHelpSheet { showHelpSheet = false }
}

@Composable
private fun ReminderHelpSheet(onDismiss: () -> Unit) {
    SettingsBottomSheet("How reminders work", "Cobalt uses activity saved on this device to choose an item to revisit.", onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ReminderExplanation(R.drawable.ic_custom_history, "Items you haven’t opened",
                "An item must be at least a day old and unopened for your chosen 3, 7 or 14 days. For items never opened, the time starts when they were saved. Archived, deleted and media items are skipped.")
            ReminderExplanation(R.drawable.ic_custom_folder, "How items are chosen",
                "Items never opened get extra priority. Longer inactivity, previous reminder opens and descriptive titles or content also affect the choice. With active folders enabled, recent folder activity and reminder opens from those folders also count.")
            ReminderExplanation(R.drawable.ic_custom_history, "Space between reminders",
                "Cobalt chooses one eligible item per scheduled check. After a reminder, that item is skipped for four days. If no item qualifies, no notification is sent.")
            ReminderExplanation(R.drawable.ic_custom_info, "When reminders arrive",
                "Reminders are scheduled daily around your chosen time. Android may delay them. Notifications must be allowed. Turning off weekends skips scheduled reminders on Saturdays and Sundays; a test reminder can still be sent.")
        }
    }
}

@Composable
private fun ReminderExplanation(icon: Int, title: String, description: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp).size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

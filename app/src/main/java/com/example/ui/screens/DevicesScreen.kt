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

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DeviceSession
import com.example.ui.viewmodel.SecondBrainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: SecondBrainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val isLoading by viewModel.isDevicesLoading.collectAsState()

    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val blurOpacity by viewModel.blurOpacity.collectAsState()
    val useBlur = com.example.utils.DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur

    val hazeState = remember { HazeState() }
    val currentDeviceId = remember(context) {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }

    LaunchedEffect(Unit) {
        viewModel.loadDeviceSessions()
    }

    val topBarModifier = if (useBlur) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = MaterialTheme.colorScheme.background,
                tint = HazeTint(MaterialTheme.colorScheme.background.copy(alpha = blurOpacity)),
                blurRadius = blurRadius.dp,
                noiseFactor = 0.02f
            )
        )
    } else {
        Modifier
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (useBlur) Color.Transparent else MaterialTheme.colorScheme.background
                ),
                modifier = topBarModifier
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .hazeSource(state = hazeState)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_settings),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No active sessions",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your signed-in devices will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.deviceId }) { session ->
                        DeviceSessionRow(
                            session = session,
                            isCurrentDevice = session.deviceId == currentDeviceId
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceSessionRow(
    session: DeviceSession,
    isCurrentDevice: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    val iconRes = when (session.deviceType.uppercase()) {
        "MOBILE" -> R.drawable.ic_custom_device_mobile
        "TABLET" -> R.drawable.ic_custom_device_tablet
        "DESKTOP" -> R.drawable.ic_custom_device_desktop
        "WEB" -> R.drawable.ic_custom_device_web
        else -> R.drawable.ic_custom_device_mobile
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "chevron_rotation"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = session.deviceType,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = session.deviceName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isCurrentDevice) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "This device",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCurrentDevice) "Active now" else getRelativeTimeSpanString(session.lastActive),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.graphicsLayer(rotationZ = rotationAngle)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_chevron_down),
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DeviceDetailItem(label = "Device Type", value = session.deviceType)
                    DeviceDetailItem(label = "OS Version", value = session.osVersion)
                    DeviceDetailItem(label = "App Version", value = session.appVersion)
                    DeviceDetailItem(label = "First Connected", value = formatSimpleDate(session.firstSeen))
                    DeviceDetailItem(label = "Last Active", value = formatFullDateTime(session.lastActive))
                    DeviceDetailItem(label = "Device ID", value = session.deviceId)
                }
            }
        }
    }
}

@Composable
fun DeviceDetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun getRelativeTimeSpanString(time: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - time
    if (diff < 0) return "Active now"
    val diffSeconds = diff / 1000
    if (diffSeconds < 60) return "Active now"
    val diffMinutes = diffSeconds / 60
    if (diffMinutes < 60) return "$diffMinutes ${if (diffMinutes == 1L) "minute" else "minutes"} ago"
    val diffHours = diffMinutes / 60
    if (diffHours < 24) return "$diffHours ${if (diffHours == 1L) "hour" else "hours"} ago"
    val diffDays = diffHours / 24
    if (diffDays < 7) return "$diffDays ${if (diffDays == 1L) "day" else "days"} ago"
    val diffWeeks = diffDays / 7
    if (diffWeeks < 4) return "$diffWeeks ${if (diffWeeks == 1L) "week" else "weeks"} ago"
    val diffMonths = diffDays / 30
    return "$diffMonths ${if (diffMonths == 1L) "month" else "months"} ago"
}

fun formatFullDateTime(time: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(time))
}

fun formatSimpleDate(time: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(time))
}

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

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import com.example.web.WebCompanionManager

/**
 * Web Companion Subpage inside Settings.
 *
 * Matches Cobalt's Material 3 Expressive architecture:
 * - CollapsibleCommonTopBar with smooth scroll-driven collapse
 * - Hero Connection Status Card with live IP & 4-digit PIN
 * - ExpressiveSettingsGroup with connected pill shapes
 * - ExpressiveSettingsSwitchItem and ModalBottomSheet selector
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebCompanionSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isRunning by WebCompanionManager.isRunning.collectAsState()
    val serverUrl by WebCompanionManager.serverUrl.collectAsState()
    val activePin by WebCompanionManager.activePin.collectAsState()
    val activePort by WebCompanionManager.activePort.collectAsState()

    var selectedPort by remember { mutableStateOf(activePort.toString()) }

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
                bottom = bottomNavPadding + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ==========================================
            // 1. HERO CONNECTION STATUS CARD
            // ==========================================
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Status Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_globe),
                                            contentDescription = null,
                                            tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = if (isRunning) "Web Server Running" else "Web Server Inactive",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isRunning) "Listening on local Wi-Fi" else "Zero battery and CPU usage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Pulsing dot indicator
                            Surface(
                                shape = CircleShape,
                                color = if (isRunning) androidx.compose.ui.graphics.Color(0xFF22C55E) else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(12.dp)
                            ) {}
                        }

                        if (isRunning && serverUrl != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Local IP Address Row
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "DESKTOP BROWSER ADDRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Surface(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(serverUrl!!))
                                        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = serverUrl!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_copy),
                                            contentDescription = "Copy URL",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // 4-Digit Security PIN Display
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "AUTHENTICATION PIN",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    TextButton(
                                        onClick = {
                                            WebCompanionManager.rotatePin(context)
                                            Toast.makeText(context, "New PIN generated", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_sync),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Rotate PIN", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = if (activePin.length == 4) "${activePin[0]}  ${activePin[1]}  ${activePin[2]}  ${activePin[3]}" else activePin,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 4.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. SERVER CONTROLS
            // ==========================================
            item {
                SettingsSubsectionHeader(
                    title = "Server Controls",
                    description = "Activate the local offline server to browse notes from your laptop.",
                    tooltipText = "The server runs strictly on your local Wi-Fi. No data is sent over the internet."
                )

                ExpressiveSettingsGroup {
                    ExpressiveSettingsSwitchItem(
                        title = "Enable Web Companion",
                        subtitle = if (isRunning) "Active at ${serverUrl ?: ""}" else "Offline (0% CPU)",
                        checked = isRunning,
                        onCheckedChange = { enable ->
                            if (enable) {
                                val port = selectedPort.toIntOrNull() ?: 8080
                                val started = viewModel.startWebCompanion(port)
                                if (!started) {
                                    Toast.makeText(context, "Could not start server. Ensure Wi-Fi is connected.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                viewModel.stopWebCompanion()
                            }
                        },
                        shape = connectedPillShape(0, 2)
                    )

                    val portOptions = mapOf(
                        "8080" to "8080 (Recommended)",
                        "8888" to "8888",
                        "9090" to "9090",
                        "53317" to "53317 (LocalSend Port)"
                    )

                    ExpressiveSettingsSelectorItem(
                        title = "Network Port",
                        description = "Local port used by desktop browsers",
                        options = portOptions,
                        selectedKey = selectedPort,
                        onSelectionChanged = { newPort ->
                            selectedPort = newPort
                            if (isRunning) {
                                val port = newPort.toIntOrNull() ?: 8080
                                viewModel.stopWebCompanion()
                                viewModel.startWebCompanion(port)
                            }
                        },
                        shape = connectedPillShape(1, 2)
                    )
                }
            }

            // ==========================================
            // 3. SECURITY & CONFLICT PREVENTION
            // ==========================================
            item {
                SettingsSubsectionHeader(
                    title = "Security & Protection",
                    description = "Multi-tier defense protecting your private knowledge base.",
                    tooltipText = "Cobalt enforces 4-digit PIN authentication, rate limiting, and editor conflict locking."
                )

                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem(
                        title = "Rotating 4-Digit PIN",
                        subtitle = "Fresh PIN generated on every server start and rotation",
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_lock),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            if (isRunning) {
                                viewModel.rotateWebCompanionPin()
                                Toast.makeText(context, "PIN rotated", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = connectedPillShape(0, 3)
                    )

                    ExpressiveSettingsNavigationItem(
                        title = "Brute-Force Lockout",
                        subtitle = "5 failed attempts triggers an automatic 10-minute IP block",
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_privacy),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {},
                        shape = connectedPillShape(1, 3)
                    )

                    ExpressiveSettingsNavigationItem(
                        title = "Active Editor Conflict Lock",
                        subtitle = "Notes currently open in mobile editor are locked from web overwrites",
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_edit),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {},
                        shape = connectedPillShape(2, 3)
                    )
                }
            }
        }

        // Standard Collapsible Top Bar
        CollapsibleCommonTopBar(
            title = "Web Companion",
            subtitle = "Access notes from desktop via local Wi-Fi",
            collapseFraction = topBarState.collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigateBack
        )
    }
}

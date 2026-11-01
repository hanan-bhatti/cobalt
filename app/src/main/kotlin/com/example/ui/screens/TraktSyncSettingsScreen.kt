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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.remote.trakt.TraktAuthManager
import com.example.data.remote.trakt.TraktDeviceAuthState
import com.example.data.remote.trakt.TraktSyncEngine
import com.example.data.remote.trakt.TraktSyncStatus
import com.example.ui.components.CollapsibleCommonTopBar
import com.example.ui.components.ExpressiveSettingsGroup
import com.example.ui.components.ExpressiveSettingsNavigationItem
import com.example.ui.components.ExpressiveSettingsSwitchItem
import com.example.ui.components.SettingsSubsectionHeader
import com.example.ui.components.bounceClick
import com.example.ui.components.connectedPillShape
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.CobaltCustomTabsLauncher
import com.example.util.TraktSyncWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraktSyncSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authManager = remember { TraktAuthManager.getInstance(context) }

    val isConnected by authManager.isConnected.collectAsState()
    val currentUser by authManager.currentUser.collectAsState()
    val syncStatus by TraktSyncEngine.syncStatus.collectAsState()
    val deviceAuthState by authManager.deviceAuthState.collectAsState()

    var isSyncWatched by remember { mutableStateOf(authManager.isSyncWatchedEnabled()) }
    var isSyncTv by remember { mutableStateOf(authManager.isSyncTvProgressEnabled()) }
    var isSyncWatchlist by remember { mutableStateOf(authManager.isSyncWatchlistEnabled()) }
    var isSyncRatings by remember { mutableStateOf(authManager.isSyncRatingsEnabled()) }

    var showAdvancedDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 160.dp)
    val density = LocalDensity.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            TraktSyncWorker.enqueueOneTimeSync(context)
            Toast.makeText(
                context,
                if (isGranted) "Trakt background sync started. You'll receive a notification when finished."
                else "Trakt background sync started.",
                Toast.LENGTH_SHORT
            ).show()
        }
    )

    fun startSyncWithBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        TraktSyncWorker.enqueueOneTimeSync(context)
        Toast.makeText(context, "Trakt background sync started.", Toast.LENGTH_SHORT).show()
    }

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
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = bottomNavPadding + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ──────────────────────────────────────────────
            // SECTION 1: ACCOUNT CONNECTION STATUS
            // ──────────────────────────────────────────────
            item {
                SettingsSubsectionHeader(
                    title = "TRAKT ACCOUNT",
                    description = "Sync your watch history, watchlist, and ratings with Trakt.tv"
                )
                Spacer(modifier = Modifier.height(4.dp))

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
                        if (isConnected) {
                            // User is connected
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val avatarUrl = currentUser?.images?.avatar?.full
                                if (!avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "Trakt Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFED1C24).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_trakt),
                                            contentDescription = null,
                                            tint = Color(0xFFED1C24),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = currentUser?.name?.takeIf { it.isNotBlank() } ?: currentUser?.username ?: "Trakt User",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (currentUser?.vip == true) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFF59E0B),
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = "VIP",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.Black,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "@${currentUser?.username ?: "connected"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val lastSummary = authManager.getLastSyncSummary()
                                    Text(
                                        text = lastSummary ?: "Connected and ready to sync",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Sync & Disconnect Action Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val isSyncing = syncStatus is TraktSyncStatus.Syncing
                                Button(
                                    onClick = { startSyncWithBackgroundPermission() },
                                    enabled = !isSyncing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .bounceClick()
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Syncing...", fontWeight = FontWeight.SemiBold)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sync Now", fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                FilledTonalButton(
                                    onClick = { showDisconnectDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Disconnect", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            // User is NOT connected
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFED1C24).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_trakt),
                                        contentDescription = null,
                                        tint = Color(0xFFED1C24),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Connect Trakt.tv",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Sign in using device activation. No passwords stored on device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { authManager.startDeviceAuth() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFED1C24)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .bounceClick()
                            ) {
                                Text(
                                    text = "Connect with Trakt",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ──────────────────────────────────────────────
            // SECTION 2: SYNCHRONIZATION SETTINGS
            // ──────────────────────────────────────────────
            item {
                SettingsSubsectionHeader(
                    title = "SYNCHRONIZATION OPTIONS",
                    description = "Choose which categories sync between Trakt and your local archive"
                )
                Spacer(modifier = Modifier.height(4.dp))

                ExpressiveSettingsGroup {
                    ExpressiveSettingsSwitchItem(
                        title = "Sync Watched Movies",
                        subtitle = "Imports watched movies and play history",
                        checked = isSyncWatched,
                        onCheckedChange = {
                            isSyncWatched = it
                            authManager.setSyncWatchedEnabled(it)
                        },
                        shape = connectedPillShape(0, 4)
                    )

                    ExpressiveSettingsSwitchItem(
                        title = "Sync TV Shows & Seasons",
                        subtitle = "Imports watched series progress and episode breakdowns",
                        checked = isSyncTv,
                        onCheckedChange = {
                            isSyncTv = it
                            authManager.setSyncTvProgressEnabled(it)
                        },
                        shape = connectedPillShape(1, 4)
                    )

                    ExpressiveSettingsSwitchItem(
                        title = "Sync Watchlist",
                        subtitle = "Imports planned watchlists into your archive",
                        checked = isSyncWatchlist,
                        onCheckedChange = {
                            isSyncWatchlist = it
                            authManager.setSyncWatchlistEnabled(it)
                        },
                        shape = connectedPillShape(2, 4)
                    )

                    ExpressiveSettingsSwitchItem(
                        title = "Sync Personal Ratings",
                        subtitle = "Imports your 1–10 scores from Trakt",
                        checked = isSyncRatings,
                        onCheckedChange = {
                            isSyncRatings = it
                            authManager.setSyncRatingsEnabled(it)
                        },
                        shape = connectedPillShape(3, 4)
                    )
                }
            }

            // ──────────────────────────────────────────────
            // SECTION 3: ADVANCED & SELF-HOSTED CONFIGURATION
            // ──────────────────────────────────────────────
            item {
                SettingsSubsectionHeader(
                    title = "ADVANCED & SELF-HOSTED",
                    description = "Custom Trakt API developer credentials or private proxies"
                )
                Spacer(modifier = Modifier.height(4.dp))

                ExpressiveSettingsGroup {
                    val isCustom = authManager.isCustomConfigured()
                    ExpressiveSettingsNavigationItem(
                        title = "API Credentials & Endpoint",
                        subtitle = if (isCustom) "Custom developer keys or proxy active" else "Default Cobalt Trakt client",
                        trailingBadge = if (isCustom) "Custom" else null,
                        onClick = { showAdvancedDialog = true },
                        shape = connectedPillShape(0, 1)
                    )
                }
            }
        }

        // Top bar overlay
        CollapsibleCommonTopBar(
            title = "Trakt.tv Sync",
            subtitle = "Two-way watch history, ratings & progress",
            collapseFraction = topBarState.collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigateBack
        )
    }

    // ──────────────────────────────────────────────
    // DEVICE CODE ACTIVATION DIALOG
    // ──────────────────────────────────────────────
    val authState = deviceAuthState
    if (authState !is TraktDeviceAuthState.Idle) {
        Dialog(
            onDismissRequest = {
                // Keep background auth polling active when user is in Custom Tabs or switching apps
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFED1C24).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_trakt),
                            contentDescription = null,
                            tint = Color(0xFFED1C24),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Activate Trakt",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    when (authState) {
                        is TraktDeviceAuthState.RequestingCode -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            CircularProgressIndicator(
                                color = Color(0xFFED1C24),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Requesting secure code from Trakt...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { authManager.cancelDeviceAuth() }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        is TraktDeviceAuthState.PendingApproval -> {
                            Text(
                                text = "Enter this activation code at the link below to connect Cobalt:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            // Display Big Activation Code
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, Color(0xFFED1C24).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp, horizontal = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = authState.userCode,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 4.sp,
                                        color = Color(0xFFED1C24)
                                    )
                                }
                            }

                            // Copy Code & Open Browser Buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Trakt Code", authState.userCode))
                                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Copy Code", fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        CobaltCustomTabsLauncher.launch(context, authState.verificationUrl)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open Trakt", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            // Polling Status Message
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = authState.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextButton(
                                onClick = { authManager.cancelDeviceAuth() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel", color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        is TraktDeviceAuthState.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Successfully Connected!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = "Logged in as @${authState.user?.username ?: "user"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is TraktDeviceAuthState.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = authState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = { authManager.cancelDeviceAuth() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Dismiss", color = MaterialTheme.colorScheme.outline)
                                }
                                Button(
                                    onClick = { authManager.startDeviceAuth() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Try Again", color = Color.White)
                                }
                            }
                        }

                        is TraktDeviceAuthState.Idle -> {}
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // ADVANCED / SELF-HOSTED SETTINGS DIALOG
    // ──────────────────────────────────────────────
    if (showAdvancedDialog) {
        var customClientIdInput by remember { mutableStateOf(authManager.getCustomClientId() ?: "") }
        var customBaseUrlInput by remember { mutableStateOf(authManager.getCustomBaseUrl() ?: "") }

        Dialog(
            onDismissRequest = { showAdvancedDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "API Credentials & Endpoint",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Leave empty to use official Cobalt Trakt credentials. Enter custom keys if using a personal Trakt app or self-hosted proxy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customClientIdInput,
                        onValueChange = { customClientIdInput = it },
                        label = { Text("Client ID") },
                        placeholder = { Text("Default Cobalt Client ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customBaseUrlInput,
                        onValueChange = { customBaseUrlInput = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.trakt.tv/") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                authManager.resetToDefaults()
                                customClientIdInput = ""
                                customBaseUrlInput = ""
                                Toast.makeText(context, "Reset to default credentials", Toast.LENGTH_SHORT).show()
                                showAdvancedDialog = false
                            }
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.error)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showAdvancedDialog = false }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    authManager.setCustomClientId(customClientIdInput)
                                    authManager.setCustomBaseUrl(customBaseUrlInput)
                                    Toast.makeText(context, "Credentials saved", Toast.LENGTH_SHORT).show()
                                    showAdvancedDialog = false
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // DISCONNECT CONFIRMATION DIALOG
    // ──────────────────────────────────────────────
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Trakt?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will remove your Trakt account session from Cobalt. Your existing saved movies and shows in Cobalt will remain intact.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        authManager.disconnect()
                        showDisconnectDialog = false
                        Toast.makeText(context, "Disconnected from Trakt", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

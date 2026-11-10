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
import kotlinx.coroutines.launch

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

                            // Live Sync Status Card (Feedback & Progress)
                            when (val status = syncStatus) {
                                is TraktSyncStatus.Syncing -> {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = status.message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${(status.progress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { status.progress.coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        }
                                    }
                                }
                                is TraktSyncStatus.Success -> {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_check),
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = status.summary,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                    }
                                }
                                is TraktSyncStatus.Error -> {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_close),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Sync Encountered an Issue",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = status.errorMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                                else -> {}
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
                                        text = "Sign in securely via browser. Your password stays with Trakt.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { authManager.startPkceAuth(context) },
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

                            TextButton(
                                onClick = { authManager.startDeviceAuth() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Sign in with device code instead",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        subtitle = "Pause or resume movie history uploads and imports",
                        checked = isSyncWatched,
                        onCheckedChange = {
                            isSyncWatched = it
                            authManager.setSyncWatchedEnabled(it)
                        },
                        shape = connectedPillShape(0, 4)
                    )

                    ExpressiveSettingsSwitchItem(
                        title = "Sync TV Shows & Seasons",
                        subtitle = "Pause or resume TV progress uploads and imports",
                        checked = isSyncTv,
                        onCheckedChange = {
                            isSyncTv = it
                            authManager.setSyncTvProgressEnabled(it)
                        },
                        shape = connectedPillShape(1, 4)
                    )

                    ExpressiveSettingsSwitchItem(
                        title = "Sync Watchlist",
                        subtitle = "Pause or resume watchlist uploads and imports",
                        checked = isSyncWatchlist,
                        onCheckedChange = {
                            isSyncWatchlist = it
                            authManager.setSyncWatchlistEnabled(it)
                        },
                        shape = connectedPillShape(2, 4)
                    )

                    ExpressiveSettingsSwitchItem(
                        title = "Sync Personal Ratings",
                        subtitle = "Pause or resume rating uploads and imports; local changes are kept",
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
                authManager.cancelAuth()
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
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
                        text = "Connect Trakt",
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
                            TextButton(onClick = { authManager.cancelAuth() }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        is TraktDeviceAuthState.AuthorizingBrowser -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please approve access in your web browser. Cobalt will automatically connect when authorized.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFED1C24)
                                )
                                Text(
                                    text = authState.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = { authManager.cancelAuth() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.outline)
                                }
                                Button(
                                    onClick = {
                                        CobaltCustomTabsLauncher.launch(context, authState.authUri.toString())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFED1C24)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open Browser", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        is TraktDeviceAuthState.ExchangingToken -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            CircularProgressIndicator(
                                color = Color(0xFFED1C24),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Exchanging authorization code with Trakt...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
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
                                onClick = { authManager.cancelAuth() },
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
                                    onClick = { authManager.cancelAuth() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Dismiss", color = MaterialTheme.colorScheme.outline)
                                }
                                Button(
                                    onClick = { authManager.startPkceAuth(context) },
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
        var customRedirectUriInput by remember { mutableStateOf(authManager.getCustomRedirectUri() ?: "") }

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

                    OutlinedTextField(
                        value = customRedirectUriInput,
                        onValueChange = { customRedirectUriInput = it },
                        label = { Text("Redirect URI") },
                        placeholder = { Text("https://cobalt.hanan-bhatti.site/callback") },
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
                                customRedirectUriInput = ""
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
                                    authManager.setCustomRedirectUri(customRedirectUriInput)
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
        val coroutineScope = rememberCoroutineScope()
        var isDisconnectingInProgress by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isDisconnectingInProgress) showDisconnectDialog = false
            },
            title = { Text("Disconnect Trakt?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will remove your Trakt account session from Cobalt and attempt server-side token revocation. Your existing saved movies and shows in Cobalt will remain intact.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isDisconnectingInProgress,
                    onClick = {
                        isDisconnectingInProgress = true
                        coroutineScope.launch {
                            val result = authManager.disconnect()
                            isDisconnectingInProgress = false
                            showDisconnectDialog = false
                            Toast.makeText(context, result.userMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    if (isDisconnectingInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDisconnectingInProgress,
                    onClick = { showDisconnectDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

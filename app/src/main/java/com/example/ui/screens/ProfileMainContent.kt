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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SavedItemType
import androidx.compose.ui.res.painterResource
import java.util.Locale
import com.example.ui.viewmodel.SecondBrainViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.ui.components.bounceClick
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.os.Build
import android.content.Context
import com.example.service.DataDownloadManager
import com.example.service.DataDownloadService
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.toThemeColor
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ui.theme.CategoryLink
import com.example.ui.theme.CategoryImage
import com.example.ui.theme.CategoryVideo
import com.example.ui.theme.CategoryText
import com.example.ui.theme.CategoryCode
import com.example.ui.theme.CategoryAudio
import com.example.ui.theme.CategoryMedia
import com.example.ui.theme.CloudStorageBlue
import com.example.ui.theme.LocalStorageGreen
import android.net.Uri
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileMainContent(
    viewModel: SecondBrainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToMovieApiKey: () -> Unit = {},
    onNavigateToManageStorage: () -> Unit
) {
    val userEmail by viewModel.userEmail.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userPhotoUrl by viewModel.userPhotoUrl.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val usedStorageBytes by viewModel.usedStorageBytes.collectAsState()
    val tmdbApiKey by viewModel.tmdbApiKey.collectAsState()
    var showSurveySheet by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteConfirmChecked by remember { mutableStateOf(false) }

    val totalItems = allItems.size
    val totalLinks = allItems.count { it.type == SavedItemType.LINK }
    val totalImages = allItems.count { it.type == SavedItemType.IMAGE }
    val totalVideos = allItems.count { it.type == SavedItemType.VIDEO }
    val totalText = allItems.count { it.type == SavedItemType.TEXT }
    val totalCode = allItems.count { it.type == SavedItemType.CODE }
    val totalAudio = allItems.count { it.type == SavedItemType.AUDIO }
    val totalMedia = allItems.count { it.type == SavedItemType.MEDIA }

    val isSyncing by viewModel.isSyncing.collectAsState()

    val context = LocalContext.current
    val downloadProgress by DataDownloadManager.progress.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val serviceIntent = Intent(context, DataDownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    val hasCloudMedia = remember(allItems) {
        allItems.any { item ->
            val url = if (item.type == SavedItemType.AUDIO) item.thumbnailPath ?: "" else item.content
            val hasWebUrl = url.startsWith("http://") || url.startsWith("https://")
            val isMedia = item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO || item.type == SavedItemType.AUDIO
            isMedia && hasWebUrl
        }
    }

    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showSignOutOnlyConfirmDialog by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("second_brain_prefs", Context.MODE_PRIVATE) }
    var showInterruptedBackupDialog by remember {
        mutableStateOf(
            prefs.getBoolean("interrupted_backup_in_progress", false) &&
            !DataDownloadManager.progress.value.isDownloading
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val pullToRefreshState = rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = { viewModel.syncData() },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = isSyncing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_profile),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Profile",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "SETTINGS, STORAGE & DEVICES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    scrollBehavior = scrollBehavior
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            // DOWNLOAD PROGRESS
            if (downloadProgress.isDownloading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Archiving Media Files...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${downloadProgress.downloadedFiles}/${downloadProgress.totalFiles}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        val progressVal = if (downloadProgress.totalBytes > 0) {
                            downloadProgress.downloadedBytes.toFloat() / downloadProgress.totalBytes.toFloat()
                        } else {
                            0.0f
                        }

                        LinearProgressIndicator(
                            progress = { progressVal.coerceIn(0.0f, 1.0f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        if (downloadProgress.currentFileName.isNotEmpty()) {
                            Text(
                                text = "File: ${downloadProgress.currentFileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val speedMb = downloadProgress.speedBytesPerSec / (1024f * 1024f)
                            val speedText = if (speedMb >= 0.1f) {
                                String.format(Locale.US, "%.1f MB/s", speedMb)
                            } else {
                                String.format(Locale.US, "%.1f KB/s", downloadProgress.speedBytesPerSec / 1024f)
                            }

                            Text(
                                text = "Speed: $speedText",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            val etaText = if (downloadProgress.estRemainingSeconds >= 0) {
                                if (downloadProgress.estRemainingSeconds >= 60) {
                                    "${downloadProgress.estRemainingSeconds / 60}m ${downloadProgress.estRemainingSeconds % 60}s remaining"
                                } else {
                                    "${downloadProgress.estRemainingSeconds}s remaining"
                                }
                            } else {
                                "Estimating time..."
                            }

                            Text(
                                text = etaText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Text(
                            text = "Downloaded ${formatStorageSize(downloadProgress.downloadedBytes)} / ${formatStorageSize(downloadProgress.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            // HEADER
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (userEmail == null) {
                    // Signed out
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_profile),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Text(
                            text = "Guest",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sign in to sync captures across all your devices",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onNavigateToAuth,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sign in to backup", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // Signed in
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!userPhotoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userPhotoUrl,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    val initials = userEmail!!.substringBefore("@").take(1).uppercase()
                                    Text(text = initials, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userName ?: userEmail!!.substringBefore("@").replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = userEmail ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val indicatorColor = if (isSyncing) MaterialTheme.colorScheme.primary else SuccessGreen
                                    val syncText = if (isSyncing) "Syncing..." else "Synced"
                                    Box(modifier = Modifier.size(8.dp).background(indicatorColor, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(syncText, style = MaterialTheme.typography.bodySmall, color = indicatorColor, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        // Sign Out Button
                        OutlinedButton(
                            onClick = {
                                showSignOutConfirmDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .bounceClick(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_logout),
                                contentDescription = "Sign Out",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // YOUR ARCHIVE
            SectionContainer(title = "YOUR ARCHIVE") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.Bottom) {
                        Text(text = "$totalItems", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "items captured", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ArchiveStatRow(
                                iconResId = R.drawable.ic_custom_link, count = totalLinks, label = "Links",
                                baseColor = CategoryLink,
                                modifier = Modifier.weight(1f)
                            )
                            ArchiveStatRow(
                                iconResId = R.drawable.ic_custom_image, count = totalImages, label = "Images",
                                baseColor = CategoryImage,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ArchiveStatRow(
                                iconResId = R.drawable.ic_custom_video, count = totalVideos, label = "Videos",
                                baseColor = CategoryVideo,
                                modifier = Modifier.weight(1f)
                            )
                            ArchiveStatRow(
                                iconResId = R.drawable.ic_custom_text, count = totalText, label = "Text",
                                baseColor = CategoryText,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ArchiveStatRow(
                                iconResId = R.drawable.ic_custom_code, count = totalCode, label = "Code",
                                baseColor = CategoryCode,
                                modifier = Modifier.weight(1f)
                            )
                            ArchiveStatRow(
                                iconResId = R.drawable.ic_custom_voice, count = totalAudio, label = "Audio",
                                baseColor = CategoryAudio,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ArchiveStatRow(
                                iconResId = R.drawable.ic_custom_movie, count = totalMedia, label = "Movies & Anime",
                                baseColor = CategoryMedia,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // APP
            SectionContainer(title = "APP") {
                ClickableRow(title = "Settings", onClick = onNavigateToSettings)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                ClickableRow(title = "Devices", onClick = onNavigateToDevices)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                ClickableRow(title = "Movie & TV Integration", onClick = onNavigateToMovieApiKey)
            }

            // STORAGE SECTION
            SectionContainer(title = "STORAGE") {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    val cloudUsedStorageBytes by viewModel.cloudUsedStorageBytes.collectAsState()
                    val maxStorageBytes = 512f * 1024f * 1024f

                    // Calculate fractions
                    val cloudFraction = (cloudUsedStorageBytes.toFloat() / maxStorageBytes).coerceIn(0f, 1f)
                    val localOnlyBytes = (usedStorageBytes - cloudUsedStorageBytes).coerceAtLeast(0L)
                    val localFraction = (localOnlyBytes.toFloat() / maxStorageBytes).coerceIn(0f, 1f - cloudFraction)
                    val freeFraction = (1f - cloudFraction - localFraction).coerceAtLeast(0f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storage Space",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatStorageSize(usedStorageBytes.coerceAtLeast(cloudUsedStorageBytes))} of 512 MB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Thick Segmented Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Cloud backed up segment (Primary/Blue Accent)
                            if (cloudFraction > 0.001f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(cloudFraction)
                                        .background(CategoryLink) // Cloud blue color
                                )
                            }
                            // Local-only segment (Secondary/Green Accent)
                            if (localFraction > 0.001f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(localFraction)
                                        .background(CategoryCode) // Local green color
                                )
                            }
                            // Free segment
                            if (freeFraction > 0.001f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(freeFraction)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Details Legend (Cloud vs Local Only)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cloud backed up detail
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(CategoryLink)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cloud Backup",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = formatStorageSize(cloudUsedStorageBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Local only detail
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(CategoryCode)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Local Only",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = formatStorageSize(localOnlyBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (userEmail == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Sign in to back up your content to the cloud.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Manage Storage Button with bounce effect animation (jelly click scale down)
                    Button(
                        onClick = onNavigateToManageStorage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick()
                            .height(50.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("Manage Storage", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 1. HELP & COMMUNITY
            SectionContainer(title = "HELP & COMMUNITY") {
                Column {
                    ClickableRow(
                        title = "Help & Support Diagnostics",
                        subtitle = "System health, FAQs, & diagnostic tests",
                        onClick = { onNavigateToLegal("support") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
                    ClickableRow(
                        title = "Feedback & Bug Reports",
                        subtitle = "Report bugs or request new features",
                        onClick = { onNavigateToLegal("feedback") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
                    ClickableRow(
                        title = "Take App Experience Survey",
                        subtitle = "Help shape v1.0 • Earn Contributor Badge",
                        onClick = { showSurveySheet = true }
                    )
                }
            }

            // 2. UPDATES & ABOUT
            SectionContainer(title = "UPDATES & ABOUT") {
                Column {
                    ClickableRow(
                        title = "App Updates & Release Notes",
                        subtitle = "v${com.example.util.AppVersionManager.currentVersionName} • Check for updates",
                        onClick = { onNavigateToLegal("release_updates") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
                    ClickableRow(
                        title = "About Second Brain",
                        subtitle = "Universal capture & personal archive",
                        onClick = { onNavigateToLegal("about") }
                    )
                }
            }

            // 3. LEGAL & PRIVACY
            SectionContainer(title = "LEGAL & PRIVACY") {
                Column {
                    ClickableRow(
                        title = "Privacy Policy",
                        subtitle = "Data protection & privacy rights",
                        onClick = { onNavigateToLegal("privacy") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
                    ClickableRow(
                        title = "Terms & Conditions",
                        subtitle = "GNU AGPL v3 license terms",
                        onClick = { onNavigateToLegal("terms") }
                    )
                }
            }

            // 4. ACCOUNT & DATA SECURITY (Hidden deep at bottom)
            SectionContainer(title = "ACCOUNT & DATA SECURITY") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Permanently delete your account and erase all local notes, media, and cloud records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { showDeleteAccountDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick()
                            .testTag("delete_account_button")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Account & Erase All Data", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showSurveySheet) {
                com.example.ui.components.SurveyBottomSheet(
                    onDismissRequest = { showSurveySheet = false }
                )
            }

            // App Version Info Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigateToLegal("release_updates") }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Second Brain",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.example.ui.components.AppVersionBadge(
                        tag = com.example.util.AppVersionManager.currentTag,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version ${com.example.util.AppVersionManager.currentVersionName} (Build #${com.example.util.AppVersionManager.currentVersionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // Delete Account Confirmation Dialog
        if (showDeleteAccountDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("Delete Account & Erase Data?", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "This action is PERMANENT and CANNOT be undone. All your archived notes, links, OCR images, voice memos, and cloud records will be permanently erased.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable { deleteConfirmChecked = !deleteConfirmChecked }
                        ) {
                            Checkbox(
                                checked = deleteConfirmChecked,
                                onCheckedChange = { deleteConfirmChecked = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error)
                            )
                            Text(
                                text = "I understand that all my data will be permanently wiped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteAccountDialog = false
                            try {
                                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.delete()
                            } catch (e: Exception) {
                                // Handled
                            }
                            viewModel.signOut()
                        },
                        enabled = deleteConfirmChecked,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Permanently Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAccountDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showSignOutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutConfirmDialog = false },
                title = { Text("Sign Out") },
                text = {
                    if (hasCloudMedia) {
                        Text("Would you like to download a backup of your cloud-synced photos, videos, and audios before signing out? This will save them to your device Storage. If you choose not to download, your cloud-synced media will remain in the cloud and can be accessed when you sign back in.")
                    } else {
                        Text("Are you sure you want to sign out?")
                    }
                },
                confirmButton = {
                    if (hasCloudMedia) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    showSignOutConfirmDialog = false
                                    // Start Service
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            val serviceIntent = Intent(context, DataDownloadService::class.java)
                                            context.startForegroundService(serviceIntent)
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        val serviceIntent = Intent(context, DataDownloadService::class.java)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(serviceIntent)
                                        } else {
                                            context.startService(serviceIntent)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Download Backup & Sign Out")
                            }
                            OutlinedButton(
                                onClick = {
                                    showSignOutConfirmDialog = false
                                    showSignOutOnlyConfirmDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sign Out Only (No Backup)")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                showSignOutConfirmDialog = false
                                viewModel.signOut()
                                onNavigateToAuth()
                            }
                        ) {
                            Text("Sign Out")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showSignOutOnlyConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutOnlyConfirmDialog = false },
                title = { Text("Confirm Sign Out without Backup") },
                text = {
                    Text("Your cloud-synced photos, videos, and audio will not be accessible on this device until you sign back in online. Local copies of that media will not be created. Are you sure you want to sign out without downloading a backup?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSignOutOnlyConfirmDialog = false
                            viewModel.signOut()
                            onNavigateToAuth()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Sign Out Anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutOnlyConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showInterruptedBackupDialog) {
            AlertDialog(
                onDismissRequest = { showInterruptedBackupDialog = false },
                title = { Text("Backup Interrupted") },
                text = {
                    Text("Your last backup attempt was interrupted. Would you like to retry downloading the remaining media files now?")
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                showInterruptedBackupDialog = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                    if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        val serviceIntent = Intent(context, DataDownloadService::class.java).apply {
                                            putExtra("resume_interrupted", true)
                                        }
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    val serviceIntent = Intent(context, DataDownloadService::class.java).apply {
                                        putExtra("resume_interrupted", true)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        context.startService(serviceIntent)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry Backup")
                        }
                        OutlinedButton(
                            onClick = {
                                showInterruptedBackupDialog = false
                                prefs.edit().apply {
                                    putBoolean("interrupted_backup_in_progress", false)
                                    putStringSet("interrupted_backup_all_ids", emptySet())
                                    putStringSet("interrupted_backup_completed_ids", emptySet())
                                    putStringSet("interrupted_backup_failed_ids", emptySet())
                                    apply()
                                }
                                viewModel.signOut()
                                onNavigateToAuth()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sign Out Anyway")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prefs.edit().putBoolean("interrupted_backup_in_progress", false).apply()
                            showInterruptedBackupDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (downloadProgress.failedItems.isNotEmpty()) {
            val failedCount = downloadProgress.failedItems.size
            val totalCount = downloadProgress.totalFiles
            val succeededCount = downloadProgress.downloadedFiles
            val titlesList = downloadProgress.failedItems.map { it.title }.joinToString(", ")
            val truncatedTitles = if (titlesList.length > 150) titlesList.take(150) + "..." else titlesList

            AlertDialog(
                onDismissRequest = { /* Cannot dismiss by tapping outside */ },
                title = { Text("Backup Completed with Failures") },
                text = {
                    Text("$succeededCount of $totalCount items backed up locally. $failedCount failed:\n\n$truncatedTitles\n\nWould you like to retry the failed items, or sign out anyway?")
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                    if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        val serviceIntent = Intent(context, DataDownloadService::class.java).apply {
                                            putExtra("retry_failed", true)
                                        }
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    val serviceIntent = Intent(context, DataDownloadService::class.java).apply {
                                        putExtra("retry_failed", true)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        context.startService(serviceIntent)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry Failed")
                        }
                        OutlinedButton(
                            onClick = {
                                prefs.edit().apply {
                                    putBoolean("interrupted_backup_in_progress", false)
                                    putStringSet("interrupted_backup_all_ids", emptySet())
                                    putStringSet("interrupted_backup_completed_ids", emptySet())
                                    putStringSet("interrupted_backup_failed_ids", emptySet())
                                    apply()
                                }
                                DataDownloadManager.reset()
                                viewModel.signOut()
                                onNavigateToAuth()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sign Out Anyway")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prefs.edit().apply {
                                putBoolean("interrupted_backup_in_progress", false)
                                putStringSet("interrupted_backup_all_ids", emptySet())
                                putStringSet("interrupted_backup_completed_ids", emptySet())
                                putStringSet("interrupted_backup_failed_ids", emptySet())
                                apply()
                            }
                            DataDownloadManager.reset()
                        }
                    ) {
                        Text("Cancel Sign Out")
                    }
                }
            )
        }
        }
    }
}

@Composable
fun SectionContainer(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun ArchiveStatRow(
    iconResId: Int,
    count: Int,
    label: String,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val resolvedColor = baseColor.toThemeColor(isDark)
    val cardAlpha = if (isDark) 0.20f else 0.12f
    val iconBgAlpha = if (isDark) 0.30f else 0.20f
    Row(
        modifier = modifier
            .background(resolvedColor.copy(alpha = cardAlpha), CircleShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(resolvedColor.copy(alpha = iconBgAlpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = resolvedColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            text = "$count",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun ClickableRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_custom_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0.0 KB"
    val kb = bytes / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return when {
        gb >= 1.0f -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0f -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.1f KB", kb)
    }
}

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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import com.example.ui.viewmodel.CobaltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.ui.components.bounceClick
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.os.Build
import android.content.Context
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
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMovieApiKey: () -> Unit = {},
    onNavigateToTraktSync: () -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {}
) {

    val allItems by viewModel.allItems.collectAsState()
    val tmdbApiKey by viewModel.tmdbApiKey.collectAsState()
    var showSurveySheet by remember { mutableStateOf(false) }

    val totalItems = allItems.size
    val totalLinks = allItems.count { it.type == SavedItemType.LINK }
    val totalImages = allItems.count { it.type == SavedItemType.IMAGE }
    val totalVideos = allItems.count { it.type == SavedItemType.VIDEO }
    val totalText = allItems.count { it.type == SavedItemType.TEXT }
    val totalCode = allItems.count { it.type == SavedItemType.CODE }
    val totalAudio = allItems.count { it.type == SavedItemType.AUDIO }
    val totalMedia = allItems.count { it.type == SavedItemType.MEDIA }

    val profileArchiveStyle by viewModel.settingsRepository.profileArchiveStyle.collectAsState()

    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_settings_outline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Settings",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "SETTINGS",
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

            // YOUR ARCHIVE
            SectionContainer(title = "YOUR ARCHIVE") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(text = "$totalItems", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "items captured", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                        }

                        // Style Toggle (Pills vs GitHub-Style Heatmap)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
                                val isHeatmap = profileArchiveStyle == "heatmap"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (!isHeatmap) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { viewModel.settingsRepository.setProfileArchiveStyle("pills") }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Piles",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isHeatmap) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isHeatmap) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { viewModel.settingsRepository.setProfileArchiveStyle("heatmap") }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Heatmap",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHeatmap) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    if (profileArchiveStyle == "heatmap") {
                        com.example.ui.components.KnowledgeHeatmap(
                            items = allItems,
                            weeksToShow = 20,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
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
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToCategory("Links") }
                                )
                                ArchiveStatRow(
                                    iconResId = R.drawable.ic_custom_image, count = totalImages, label = "Images",
                                    baseColor = CategoryImage,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToCategory("Images") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ArchiveStatRow(
                                    iconResId = R.drawable.ic_custom_video, count = totalVideos, label = "Videos",
                                    baseColor = CategoryVideo,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToCategory("Videos") }
                                )
                                ArchiveStatRow(
                                    iconResId = R.drawable.ic_custom_text, count = totalText, label = "Text",
                                    baseColor = CategoryText,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToCategory("Text") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ArchiveStatRow(
                                    iconResId = R.drawable.ic_custom_code, count = totalCode, label = "Code",
                                    baseColor = CategoryCode,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToCategory("Code") }
                                )
                                ArchiveStatRow(
                                    iconResId = R.drawable.ic_custom_voice, count = totalAudio, label = "Audio",
                                    baseColor = CategoryAudio,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToCategory("Audio") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ArchiveStatRow(
                                    iconResId = R.drawable.ic_custom_movie, count = totalMedia, label = "Movies & Anime",
                                    baseColor = CategoryMedia,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToCategory("Movies & Anime") }
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // PREFERENCES & TOOLS
            SectionContainer(title = "PREFERENCES & TOOLS") {
                ClickableRow(
                    title = "Appearance & Preferences",
                    subtitle = "Theme, frosted blur, Gemini AI, and floating capture dock",
                    leadingIconRes = R.drawable.ic_custom_settings,
                    onClick = onNavigateToSettings
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                ClickableRow(
                    title = "Media & Providers",
                    subtitle = "Trakt, TMDB, Jikan and search preferences",
                    leadingIconRes = R.drawable.ic_custom_movie,
                    onClick = onNavigateToTraktSync
                )
            }

            // 1. HELP & COMMUNITY
            SectionContainer(title = "HELP & COMMUNITY") {
                Column {
                    ClickableRow(
                        title = "Help & Support",
                        subtitle = "Frequently asked questions & guides",
                        leadingIconRes = R.drawable.ic_custom_help,
                        onClick = { onNavigateToLegal("support") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                    ClickableRow(
                        title = "Feedback & Bug Reports",
                        subtitle = "Report bugs or request new features",
                        leadingIconRes = R.drawable.ic_custom_feedback,
                        onClick = { onNavigateToLegal("feedback") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                    ClickableRow(
                        title = "App experience survey",
                        subtitle = "A few taps to share your experience by email",
                        leadingIconRes = R.drawable.ic_custom_survey,
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
                        leadingIconRes = R.drawable.ic_custom_sync,
                        onClick = { onNavigateToLegal("release_updates") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                    ClickableRow(
                        title = "About Cobalt",
                        subtitle = "Universal capture & personal archive",
                        leadingIconRes = R.drawable.ic_custom_info,
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
                        leadingIconRes = R.drawable.ic_custom_lock,
                        onClick = { onNavigateToLegal("privacy") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                    ClickableRow(
                        title = "Terms & Conditions",
                        subtitle = "GNU AGPL v3 license terms",
                        leadingIconRes = R.drawable.ic_custom_terms,
                        onClick = { onNavigateToLegal("terms") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                    ClickableRow(
                        title = "Open Source Licenses",
                        subtitle = "Third-party libraries & bundled OFL fonts",
                        leadingIconRes = R.drawable.ic_custom_code,
                        onClick = { onNavigateToLegal("licenses") }
                    )
                }
            }

            if (showSurveySheet) {
                com.example.ui.components.SurveyBottomSheet(
                    viewModel = viewModel,
                    onDismissRequest = { showSurveySheet = false }
                )
            }

            // App Version Info Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigateToLegal("release_updates") }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cobalt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Version ${com.example.util.AppVersionManager.currentVersionName} (${com.example.util.AppVersionManager.currentVersionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }


        }
    }
}

@Composable
fun SectionContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val resolvedColor = baseColor.toThemeColor(isDark)
    val cardAlpha = if (isDark) 0.20f else 0.12f
    val iconBgAlpha = if (isDark) 0.30f else 0.20f
    Row(
        modifier = modifier
            .background(resolvedColor.copy(alpha = cardAlpha), CircleShape)
            .clickable(onClick = onClick)
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
fun ClickableRow(
    title: String,
    subtitle: String? = null,
    leadingIconRes: Int? = null,
    iconTint: Color? = null,
    iconContainerColor: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (leadingIconRes != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconContainerColor ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = leadingIconRes),
                        contentDescription = null,
                        tint = iconTint ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_custom_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

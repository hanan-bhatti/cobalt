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

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import com.example.ui.components.ExpressiveSettingsNavigationItem
import com.example.ui.components.SettingsBottomSheet
import com.example.ui.components.SettingsGroupLabel
import com.example.ui.components.SettingsIconBadge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileMainContent(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMovieApiKey: () -> Unit = {},
    onNavigateToTraktSync: () -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {},
    onNavigateToPreferences: (SettingsCategory) -> Unit = { onNavigateToSettings() },
    onNavigateToWidgets: () -> Unit = { onNavigateToPreferences(SettingsCategory.WIDGETS) },
    onNavigateToSideDock: () -> Unit = { onNavigateToPreferences(SettingsCategory.FLOATING_DOCK) },
    showBackButton: Boolean = false
) {
    val items by viewModel.allItems.collectAsState()
    val archiveStyle by viewModel.settingsRepository.profileArchiveStyle.collectAsState()
    val dockEnabled by viewModel.settingsRepository.isFloatingOcrEnabled.collectAsState()
    val context = LocalContext.current
    val aiRevision by com.example.data.remote.ai.AiProviderStore.changes.collectAsState()
    val aiConnection = remember(aiRevision) { com.example.data.remote.ai.AiProviderStore(context).connection() }
    val geminiKey = aiConnection.key
    val reminders = remember(context) { com.example.util.AdaptiveReminderStore(context) }
    var remindersEnabled by remember { mutableStateOf(reminders.isRemindersEnabled) }
    var overlayAllowed by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    var notificationsAllowed by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var showActivity by rememberSaveable { mutableStateOf(false) }
    var showSurvey by rememberSaveable { mutableStateOf(false) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                remindersEnabled = reminders.isRemindersEnabled
                overlayAllowed = android.provider.Settings.canDrawOverlays(context)
                notificationsAllowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val stats = remember(items) { archiveCategoryStats(items) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    val off = stringResource(R.string.settings_off)
    val on = stringResource(R.string.settings_on)
    val permissionNeeded = stringResource(R.string.settings_permission_needed)

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge) },
                navigationIcon = {
                    if (showBackButton) IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_custom_back), stringResource(R.string.settings_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("settings_overview_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "archive_preview") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_archive), style = MaterialTheme.typography.titleLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                                    Text(
                                        pluralStringResource(R.plurals.settings_saved_item_count, items.size, java.text.NumberFormat.getInstance().format(items.size)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)
                                    )
                                    TextButton(onClick = { showActivity = true }, contentPadding = PaddingValues(0.dp)) {
                                        Text(stringResource(R.string.settings_view_activity), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            ArchiveViewToggle(archiveStyle == "heatmap", viewModel.settingsRepository::setProfileArchiveStyle)
                        }
                        if (archiveStyle == "heatmap") {
                            com.example.ui.components.KnowledgeHeatmap(items, compact = true)
                        } else {
                            ArchiveCategoryGrid(stats, onNavigateToCategory, isDark)
                        }
                    }
                }
            }
            item(key = "personalize") {
                SettingsOverviewGroup(stringResource(R.string.settings_personalize)) {
                    SettingsOverviewRow(R.string.settings_appearance, R.string.settings_appearance_summary, R.drawable.ic_settings_appearance, SettingsCategory.APPEARANCE, isDark, onClick = { onNavigateToPreferences(SettingsCategory.APPEARANCE) })
                    SettingsOverviewRow(R.string.settings_notes_folders, R.string.settings_notes_folders_summary, R.drawable.ic_custom_organization, SettingsCategory.ORGANIZATION, isDark, onClick = { onNavigateToPreferences(SettingsCategory.ORGANIZATION) })
                    SettingsOverviewRow(R.string.settings_widgets, R.string.settings_widgets_summary, R.drawable.ic_settings_widgets, SettingsCategory.WIDGETS, isDark, onClick = onNavigateToWidgets)
                }
            }
            item(key = "capture_reminders") {
                SettingsOverviewGroup(stringResource(R.string.settings_capture_reminders)) {
                    SettingsOverviewRow(R.string.settings_side_dock, R.string.settings_side_dock_summary, R.drawable.ic_settings_floating_dock, SettingsCategory.FLOATING_DOCK, isDark, if (!dockEnabled) off else if (!overlayAllowed) permissionNeeded else on, onNavigateToSideDock)
                    SettingsOverviewRow(R.string.settings_gemini, R.string.settings_gemini_summary, R.drawable.ic_settings_intelligence, SettingsCategory.INTELLIGENCE, isDark, if (aiConnection.provider == com.example.data.remote.ai.AiProvider.CUSTOM && aiConnection.baseUrl.isNotBlank() && geminiKey.isBlank()) "Endpoint saved" else stringResource(if (geminiKey.isBlank()) R.string.settings_not_set_up else R.string.settings_key_saved)) { onNavigateToPreferences(SettingsCategory.INTELLIGENCE) }
                    SettingsOverviewRow(R.string.settings_reminders, R.string.settings_reminders_summary, R.drawable.ic_custom_reminder, SettingsCategory.REMINDERS, isDark, if (!remindersEnabled) off else if (!notificationsAllowed) permissionNeeded else on) { onNavigateToPreferences(SettingsCategory.REMINDERS) }
                }
            }
            item(key = "library_device") {
                SettingsOverviewGroup(stringResource(R.string.settings_library_device)) {
                    SettingsOverviewRow(R.string.settings_media_providers, R.string.settings_media_providers_summary, R.drawable.ic_custom_movie, null, isDark, onClick = onNavigateToTraktSync)
                    SettingsOverviewRow(R.string.settings_import_export, R.string.settings_import_export_summary, R.drawable.ic_transfer_export, null, isDark) { onNavigateToLegal("import_export") }
                    SettingsOverviewRow(R.string.settings_storage, R.string.settings_storage_summary, R.drawable.ic_custom_storage, SettingsCategory.STORAGE_RECOVERY, isDark) { onNavigateToPreferences(SettingsCategory.STORAGE_RECOVERY) }
                    SettingsOverviewRow(R.string.settings_storage_update, R.string.settings_storage_update_summary, R.drawable.ic_custom_sync, null, isDark) { onNavigateToPreferences(SettingsCategory.DATA_MIGRATION) }
                    if (com.example.util.AppFeatureFlags.webCompanion) {
                        SettingsOverviewRow(R.string.settings_web_companion, R.string.settings_web_companion_summary, R.drawable.ic_custom_globe, SettingsCategory.WEB_COMPANION, isDark) { onNavigateToPreferences(SettingsCategory.WEB_COMPANION) }
                    }
                    SettingsOverviewRow(R.string.settings_background, R.string.settings_background_summary, R.drawable.ic_settings_system_health, SettingsCategory.SYSTEM_HEALTH, isDark) { onNavigateToPreferences(SettingsCategory.SYSTEM_HEALTH) }
                }
            }
            item(key = "help_about") {
                SettingsOverviewGroup(stringResource(R.string.settings_help_about)) {
                    SettingsOverviewRow(R.string.settings_help, R.string.settings_help_summary, R.drawable.ic_custom_help, null, isDark) { onNavigateToLegal("support") }
                    SettingsOverviewRow(R.string.settings_feedback, R.string.settings_feedback_summary, R.drawable.ic_custom_feedback, null, isDark) { onNavigateToLegal("feedback") }
                    SettingsOverviewRow(R.string.settings_survey, R.string.settings_survey_summary, R.drawable.ic_custom_survey, null, isDark) { showSurvey = true }
                    SettingsOverviewRow(R.string.settings_updates, R.string.settings_updates_summary, R.drawable.ic_custom_sync, null, isDark, com.example.util.AppVersionManager.currentVersionName) { onNavigateToLegal("release_updates") }
                    SettingsOverviewRow(R.string.settings_about, R.string.settings_about_summary, R.drawable.ic_custom_info, null, isDark) { onNavigateToLegal("about") }
                }
            }
            item(key = "legal_version") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onNavigateToLegal("privacy") }) { Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.labelLarge) }
                        TextButton(onClick = { onNavigateToLegal("terms") }) { Text(stringResource(R.string.settings_terms), style = MaterialTheme.typography.labelLarge) }
                        TextButton(onClick = { onNavigateToLegal("licenses") }) { Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.labelLarge) }
                    }
                    Text(stringResource(R.string.settings_app_version, com.example.util.AppVersionManager.currentVersionName, com.example.util.AppVersionManager.currentVersionCode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showActivity) {
        SettingsBottomSheet(stringResource(R.string.settings_activity_title), stringResource(R.string.settings_activity_description), { showActivity = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                com.example.ui.components.KnowledgeHeatmap(items, compact = true)
                SettingsGroupLabel(stringResource(R.string.settings_categories))
                ArchiveCategoryGrid(stats, { category -> showActivity = false; onNavigateToCategory(category) }, isDark)
            }
        }
    }
    if (showSurvey) {
        com.example.ui.components.SurveyBottomSheet(viewModel, onDismissRequest = { showSurvey = false })
    }
}

@Composable
private fun SettingsOverviewGroup(title: String, content: @Composable () -> Unit) {
    Column {
        SettingsGroupLabel(title)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsOverviewRow(
    title: Int,
    subtitle: Int,
    icon: Int,
    category: SettingsCategory?,
    isDark: Boolean,
    value: String? = null,
    onClick: () -> Unit
) {
    val accent = when (title) {
        R.string.settings_appearance -> com.example.ui.theme.MainSettingsAccent.APPEARANCE
        R.string.settings_notes_folders -> com.example.ui.theme.MainSettingsAccent.NOTES_FOLDERS
        R.string.settings_widgets -> com.example.ui.theme.MainSettingsAccent.WIDGETS
        R.string.settings_side_dock -> com.example.ui.theme.MainSettingsAccent.SIDE_DOCK
        R.string.settings_gemini -> com.example.ui.theme.MainSettingsAccent.AI_MODELS
        R.string.settings_reminders -> com.example.ui.theme.MainSettingsAccent.REMINDERS
        R.string.settings_media_providers -> com.example.ui.theme.MainSettingsAccent.MEDIA_ACCOUNTS
        R.string.settings_import_export -> com.example.ui.theme.MainSettingsAccent.IMPORT_EXPORT
        R.string.settings_storage -> com.example.ui.theme.MainSettingsAccent.STORAGE
        R.string.settings_storage_update -> com.example.ui.theme.MainSettingsAccent.STORAGE_UPDATE
        R.string.settings_background -> com.example.ui.theme.MainSettingsAccent.BACKGROUND_ACTIVITY
        R.string.settings_web_companion -> com.example.ui.theme.MainSettingsAccent.WEB_COMPANION
        R.string.settings_help -> com.example.ui.theme.MainSettingsAccent.HELP
        R.string.settings_feedback -> com.example.ui.theme.MainSettingsAccent.FEEDBACK
        R.string.settings_survey -> com.example.ui.theme.MainSettingsAccent.SURVEY
        R.string.settings_updates -> com.example.ui.theme.MainSettingsAccent.UPDATES
        else -> com.example.ui.theme.MainSettingsAccent.ABOUT
    }.colors(isDark)
    ExpressiveSettingsNavigationItem(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        leadingIcon = {
            SettingsIconBadge(icon, containerColor = accent.container, iconColor = accent.icon)
        },
        trailingBadge = value,
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        compact = true
    )
}

@Composable
private fun ArchiveViewToggle(isHeatmap: Boolean, onSelect: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
        Row {
            listOf(
                Triple(true, R.drawable.ic_archive_heatmap, R.string.settings_show_heatmap),
                Triple(false, R.drawable.ic_archive_categories, R.string.settings_show_categories)
            ).forEach { (heatmap, icon, description) ->
                Box(
                    modifier = Modifier.size(48.dp).selectable(selected = isHeatmap == heatmap, role = Role.RadioButton, onClick = { onSelect(if (heatmap) "heatmap" else "pills") }),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(36.dp).background(if (isHeatmap == heatmap) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), stringResource(description), tint = if (isHeatmap == heatmap) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

private data class ArchiveCategoryStat(val label: Int, val filter: String, val icon: Int, val count: Int, val color: Color)

private fun archiveCategoryStats(items: List<com.example.data.model.SavedItem>): List<ArchiveCategoryStat> = listOf(
    ArchiveCategoryStat(R.string.settings_category_links, "Links", R.drawable.ic_custom_link, items.count { it.type == SavedItemType.LINK }, CategoryLink),
    ArchiveCategoryStat(R.string.settings_category_images, "Images", R.drawable.ic_custom_image, items.count { it.type == SavedItemType.IMAGE }, CategoryImage),
    ArchiveCategoryStat(R.string.settings_category_videos, "Videos", R.drawable.ic_custom_video, items.count { it.type == SavedItemType.VIDEO }, CategoryVideo),
    ArchiveCategoryStat(R.string.settings_category_text, "Text", R.drawable.ic_custom_text, items.count { it.type == SavedItemType.TEXT }, CategoryText),
    ArchiveCategoryStat(R.string.settings_category_code, "Code", R.drawable.ic_custom_code, items.count { it.type == SavedItemType.CODE }, CategoryCode),
    ArchiveCategoryStat(R.string.settings_category_audio, "Audio", R.drawable.ic_custom_voice, items.count { it.type == SavedItemType.AUDIO }, CategoryAudio),
    ArchiveCategoryStat(R.string.settings_category_media, "Movies & Anime", R.drawable.ic_custom_movie, items.count { it.type == SavedItemType.MEDIA }, CategoryMedia)
)

@Composable
private fun ArchiveCategoryGrid(stats: List<ArchiveCategoryStat>, onSelect: (String) -> Unit, isDark: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { stat ->
                    val color = stat.color.toThemeColor(isDark)
                    Surface(onClick = { onSelect(stat.filter) }, color = color.copy(alpha = if (isDark) 0.16f else 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(stat.icon), null, tint = color, modifier = Modifier.size(20.dp))
                            Text(stringResource(stat.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(java.text.NumberFormat.getInstance().format(stat.count), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
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

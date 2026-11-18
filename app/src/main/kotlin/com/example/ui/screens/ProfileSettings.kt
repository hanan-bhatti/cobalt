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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.util.AppFeatureFlags
import com.example.data.repository.SettingsRepository
import com.example.ui.components.CollapsibleCommonTopBar
import com.example.ui.components.ExpressiveSearchableModelSelectorItem
import com.example.ui.components.ExpressiveSegmentedChoiceItem
import com.example.ui.components.ExpressiveSettingsGroup
import com.example.ui.components.ExpressiveSettingsNavigationItem
import com.example.ui.components.SettingsSubsectionHeader
import com.example.ui.components.ExpressiveSettingsSelectorItem
import com.example.ui.components.ExpressiveSettingsSliderItem
import com.example.ui.components.ExpressiveSettingsSwitchItem
import com.example.ui.components.connectedPillShape
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.viewmodel.CobaltViewModel
import com.example.utils.DevicePerformance

/**
 * Settings Categories representing distinct configuration areas in Cobalt.
 * Inspired by PixelPlayer's Material 3 Expressive category architecture.
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val iconRes: Int
) {
    APPEARANCE(
        title = "Appearance & Display",
        subtitle = "Themes, dynamic wallpaper palette, and frosted blur",
        iconRes = R.drawable.ic_settings_appearance
    ),
    ORGANIZATION(
        title = "Archive & Organization",
        subtitle = "Folder ranking, adaptive suggestions, and activity heatmap",
        iconRes = R.drawable.ic_custom_organization
    ),
    REMINDERS(
        title = "Adaptive Reminders",
        subtitle = "Self-learning resurfacing for neglected notes and folders",
        iconRes = R.drawable.ic_custom_reminder
    ),
    INTELLIGENCE(
        title = "AI models",
        subtitle = "Providers, text capture and voice notes",
        iconRes = R.drawable.ic_custom_ai_brain
    ),
    FLOATING_DOCK(
        title = "Floating Dock & Capture",
        subtitle = "Screen-edge handle, instant OCR trigger, and physics",
        iconRes = R.drawable.ic_settings_floating_dock
    ),
    WIDGETS(
        title = "Home Screen Widgets",
        subtitle = "Preview, personalize, and place your collections",
        iconRes = R.drawable.ic_settings_widgets
    ),
    STORAGE_RECOVERY(
        title = "Storage",
        subtitle = "Saved notes, draft recovery, Trash, saved history and cache",
        iconRes = R.drawable.ic_custom_storage
    ),
    DATA_MIGRATION(
        title = "Note storage update",
        subtitle = "Note storage update and verification results",
        iconRes = R.drawable.ic_custom_sync
    ),
    WEB_COMPANION(
        title = "Web Companion",
        subtitle = "Local browser access, rotating PIN & LAN sync",
        iconRes = R.drawable.ic_custom_globe
    ),
    SYSTEM_HEALTH(
        title = "Background activity",
        subtitle = "Battery settings and background updates",
        iconRes = R.drawable.ic_settings_system_health
    )
}

/**
 * Dual-tone color mapping for Category avatar badges matching PixelPlayer's style.
 * Uses high-contrast, vibrant, and theme-harmonized pairs for pristine clarity in both Light and Dark themes.
 */
fun getSettingsCategoryColors(category: SettingsCategory, isDark: Boolean): Pair<Color, Color> {
    return if (isDark) {
        when (category) {
            SettingsCategory.APPEARANCE -> Color(0xFF2E1C44) to Color(0xFFD8B4FE)
            SettingsCategory.ORGANIZATION -> Color(0xFF382A12) to Color(0xFFFDE68A)
            SettingsCategory.REMINDERS -> Color(0xFF3B1D0E) to Color(0xFFFDBA74)
            SettingsCategory.INTELLIGENCE -> Color(0xFF0F2D3F) to Color(0xFF7DD3FC)
            SettingsCategory.FLOATING_DOCK -> Color(0xFF3D141F) to Color(0xFFFDA4AF)
            SettingsCategory.WIDGETS -> Color(0xFF0F3320) to Color(0xFF86EFAC)
            SettingsCategory.STORAGE_RECOVERY -> Color(0xFF243529) to Color(0xFFA7F3D0)
            SettingsCategory.DATA_MIGRATION -> Color(0xFF243529) to Color(0xFFA7F3D0)
            SettingsCategory.WEB_COMPANION -> Color(0xFF162D3B) to Color(0xFF38BDF8)
            SettingsCategory.SYSTEM_HEALTH -> Color(0xFF1B2144) to Color(0xFFA5B4FC)
        }
    } else {
        when (category) {
            SettingsCategory.APPEARANCE -> Color(0xFFF3E8FF) to Color(0xFF7E22CE)
            SettingsCategory.ORGANIZATION -> Color(0xFFFEF3C7) to Color(0xFFB45309)
            SettingsCategory.REMINDERS -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
            SettingsCategory.INTELLIGENCE -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
            SettingsCategory.FLOATING_DOCK -> Color(0xFFFFE4E6) to Color(0xFFBE123C)
            SettingsCategory.WIDGETS -> Color(0xFFD1FAE5) to Color(0xFF047857)
            SettingsCategory.STORAGE_RECOVERY -> Color(0xFFD1FAE5) to Color(0xFF065F46)
            SettingsCategory.DATA_MIGRATION -> Color(0xFFD1FAE5) to Color(0xFF065F46)
            SettingsCategory.WEB_COMPANION -> Color(0xFFE0F2FE) to Color(0xFF0284C7)
            SettingsCategory.SYSTEM_HEALTH -> Color(0xFFE0E7FF) to Color(0xFF4338CA)
        }
    }
}

/**
 * 88dp Category Card with 56dp dual-tone circular avatar, title, subtitle, and chevron.
 */
@Composable
fun ExpressiveCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = remember(category, isDark) { getSettingsCategoryColors(category, isDark) }

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.first)
            ) {
                Icon(
                    painter = painterResource(id = category.iconRes),
                    contentDescription = null,
                    tint = colors.second,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Main Settings Screen featuring PixelPlayer's collapsible hero header
 * and modular Category Cards leading to dedicated subpages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCategory: ((SettingsCategory) -> Unit)? = null,
    onNavigateToEdgePanelSettings: (() -> Unit)? = null,
    onNavigateToWidgetSettings: (() -> Unit)? = null,
    onNavigateToEdgeAnimSettings: (() -> Unit)? = null,
    onNavigateToGeminiUsage: (() -> Unit)? = null,
    onNavigateToArchiveSettings: (() -> Unit)? = null,
    onNavigateToRemindersSettings: (() -> Unit)? = null,
    onNavigateToTextFontsSettings: (() -> Unit)? = null
    , onNavigateToScreeningWidgetSettings: (() -> Unit)? = null
) {
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var showEdgePanelSettings by remember { mutableStateOf(false) }
    var showWidgetSettings by remember { mutableStateOf(false) }
    var showScreeningWidgetSettings by remember { mutableStateOf(false) }
    var showEdgeAnimSettings by remember { mutableStateOf(false) }
    var showTextFontsSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = (onNavigateToCategory == null && selectedCategory != null) || (onNavigateToEdgePanelSettings == null && showEdgePanelSettings) || (onNavigateToWidgetSettings == null && showWidgetSettings) || (onNavigateToScreeningWidgetSettings == null && showScreeningWidgetSettings) || (onNavigateToEdgeAnimSettings == null && showEdgeAnimSettings) || (onNavigateToTextFontsSettings == null && showTextFontsSettings)) {
        when {
            showScreeningWidgetSettings -> showScreeningWidgetSettings = false
            showTextFontsSettings -> showTextFontsSettings = false
            showEdgeAnimSettings -> showEdgeAnimSettings = false
            showWidgetSettings -> showWidgetSettings = false
            showEdgePanelSettings -> showEdgePanelSettings = false
            selectedCategory != null -> selectedCategory = null
        }
    }

    if (showTextFontsSettings && onNavigateToTextFontsSettings == null) {
        TextAndFontsSettingsScreen(viewModel = viewModel, onNavigateBack = { showTextFontsSettings = false })
        return
    }

    if (showEdgePanelSettings && !showEdgeAnimSettings && onNavigateToEdgePanelSettings == null) {
        EdgePanelSettingsScreen(viewModel = viewModel, onNavigateBack = { showEdgePanelSettings = false },
            onNavigateToAnimation = onNavigateToEdgeAnimSettings ?: { showEdgeAnimSettings = true })
        return
    }

    if (showWidgetSettings && onNavigateToWidgetSettings == null) {
        WidgetSettingsScreen(viewModel = viewModel, onNavigateBack = { showWidgetSettings = false })
        return
    }
    if (showScreeningWidgetSettings && onNavigateToScreeningWidgetSettings == null) {
        ScreeningWidgetSettingsScreen(viewModel, onNavigateBack = { showScreeningWidgetSettings = false })
        return
    }

    if (showEdgeAnimSettings && onNavigateToEdgeAnimSettings == null) {
        EdgePanelAnimationSettingsScreen(viewModel = viewModel, onNavigateBack = { showEdgeAnimSettings = false })
        return
    }

    if (selectedCategory != null && onNavigateToCategory == null) {
        SettingsCategoryDetailScreen(
            category = selectedCategory!!,
            viewModel = viewModel,
            onNavigateBack = { selectedCategory = null },
            onNavigateToEdgePanelSettings = onNavigateToEdgePanelSettings ?: { showEdgePanelSettings = true },
            onNavigateToWidgetSettings = onNavigateToWidgetSettings ?: { showWidgetSettings = true },
            onNavigateToScreeningWidgetSettings = onNavigateToScreeningWidgetSettings ?: { showScreeningWidgetSettings = true },
            onNavigateToEdgeAnimSettings = onNavigateToEdgeAnimSettings ?: { showEdgeAnimSettings = true },
            onNavigateToGeminiUsage = onNavigateToGeminiUsage ?: {},
            onNavigateToArchiveSettings = onNavigateToArchiveSettings ?: {},
            onNavigateToTextFontsSettings = onNavigateToTextFontsSettings ?: { showTextFontsSettings = true }
        )
        return
    }

    // Top-level Category Overview Screen
    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 170.dp)
    val density = LocalDensity.current

    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { transitionState.targetState = true }
    val transition = rememberTransition(transitionState, label = "SettingsAppearTransition")
    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 350) }
    ) { if (it) 1f else 0f }
    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 350, easing = FastOutSlowInEasing) }
    ) { if (it) 0.dp else 24.dp }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(topBarState.nestedScrollConnection)
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ExpressiveSettingsGroup {
                    val categories = SettingsCategory.entries.filter {
                        it != SettingsCategory.WEB_COMPANION || AppFeatureFlags.webCompanion
                    }
                    val total = categories.size

                    categories.forEachIndexed { index, category ->
                        ExpressiveCategoryItem(
                            category = category,
                            onClick = {
                                if (category == SettingsCategory.ORGANIZATION && onNavigateToArchiveSettings != null) {
                                    onNavigateToArchiveSettings()
                                } else if (category == SettingsCategory.REMINDERS && onNavigateToRemindersSettings != null) {
                                    onNavigateToRemindersSettings()
                                } else if (onNavigateToCategory != null) {
                                    onNavigateToCategory(category)
                                } else {
                                    selectedCategory = category
                                }
                            },
                            shape = connectedPillShape(index, total)
                        )
                        if (index < total - 1) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }

        CollapsibleCommonTopBar(
            title = "Settings",
            subtitle = "Preferences, intelligence & capture dock",
            collapseFraction = topBarState.collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigateBack
        )
    }
}

/**
 * Dedicated Subpage for a single Settings Category.
 * Features its own collapsible header, informative tooltip headers, and connected capsules.
 */
@Composable
fun SettingsCategoryDetailScreen(
    category: SettingsCategory,
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdgePanelSettings: () -> Unit,
    onNavigateToWidgetSettings: () -> Unit,
    onNavigateToEdgeAnimSettings: () -> Unit,
    onNavigateToGeminiUsage: () -> Unit = {},
    onNavigateToArchiveSettings: () -> Unit = {},
    onNavigateToRemindersSettings: () -> Unit = {},
    onNavigateToTextFontsSettings: () -> Unit = {}
    , onNavigateToScreeningWidgetSettings: () -> Unit = {}
) {
    if (category == SettingsCategory.SYSTEM_HEALTH) {
        BackgroundActivitySettingsScreen(onNavigateBack)
        return
    }
    if (category == SettingsCategory.INTELLIGENCE) {
        AiModelsSettingsScreen(viewModel, onNavigateBack, onNavigateToGeminiUsage)
        return
    }
    if (category == SettingsCategory.APPEARANCE) {
        AppearanceSettingsScreen(viewModel, onNavigateBack, onNavigateToTextFontsSettings)
        return
    }
    if (category == SettingsCategory.ORGANIZATION) {
        ArchiveOrganizationSettingsScreen(viewModel, onNavigateBack)
        return
    }
    if (category == SettingsCategory.STORAGE_RECOVERY) {
        NoteStorageManagementScreen(onBack = onNavigateBack)
        return
    }
    if (category == SettingsCategory.DATA_MIGRATION) {
        NoteMigrationScreen(startup = false, onDone = {}, onBack = onNavigateBack)
        return
    }
    if (category == SettingsCategory.WEB_COMPANION) {
        WebCompanionSettingsScreen(viewModel = viewModel, onNavigateBack = onNavigateBack)
        return
    }

    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 160.dp)
    val density = LocalDensity.current

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
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
            when (category) {
                SettingsCategory.DATA_MIGRATION, SettingsCategory.STORAGE_RECOVERY, SettingsCategory.WEB_COMPANION -> Unit
                // ==========================================
                // SUBPAGE: APPEARANCE & DISPLAY
                // ==========================================
                SettingsCategory.APPEARANCE -> Unit

                // ==========================================
                // SUBPAGE: ARCHIVE & ORGANIZATION
                // ==========================================
                SettingsCategory.ORGANIZATION -> Unit

                // ==========================================
                // SUBPAGE: ADAPTIVE REMINDERS
                // ==========================================
                SettingsCategory.REMINDERS -> {
                    item {
                        SettingsSubsectionHeader(
                            title = "Adaptive Knowledge Reminders",
                            description = "Configure review schedule for forgotten notes and captures.",
                            tooltipText = "Cobalt analyzes inactive notes and active folder synergy to suggest items before you forget them."
                        )

                        ExpressiveSettingsGroup {
                            ExpressiveSettingsNavigationItem(
                                title = "Adaptive Reminder Schedule",
                                subtitle = "Delivery time, sensitivity & notification preview",
                                onClick = onNavigateToRemindersSettings,
                                shape = RoundedCornerShape(24.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // SUBPAGE: INTELLIGENCE & GEMINI AI
                // ==========================================
                SettingsCategory.INTELLIGENCE -> Unit

                SettingsCategory.FLOATING_DOCK -> {
                    item {
                        SettingsSubsectionHeader(
                            title = "Screen-Edge Floating Handle",
                            description = "Docked handle for instant capture and OCR over any active application.",
                            tooltipText = "The floating edge dock stays accessible while browsing or reading in other apps, letting you capture text or images in one tap without switching."
                        )

                        if (!hasOverlayPermission) {
                            ExpressiveSettingsGroup {
                                Surface(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.errorContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_custom_warning_triangle),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Grant Overlay Permission",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Required by Android to display the handle over other apps. Tap to open Settings.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            val isEdgeDockEnabled by viewModel.settingsRepository.isFloatingOcrEnabled.collectAsState()
                            val totalItems = if (isEdgeDockEnabled) 3 else 1
                            var dockIdx = 0

                            ExpressiveSettingsGroup {
                                ExpressiveSettingsSwitchItem(
                                    title = "Floating Edge Handle",
                                    subtitle = "Show docked handle on screen edge for fast capture",
                                    checked = isEdgeDockEnabled,
                                    onCheckedChange = { enable ->
                                        viewModel.settingsRepository.setFloatingOcrEnabled(enable)
                                        val serviceIntent = Intent(context, com.example.CobaltSideDockService::class.java)
                                        if (enable) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }
                                        } else {
                                            context.stopService(serviceIntent)
                                        }
                                    },
                                    shape = connectedPillShape(dockIdx++, totalItems)
                                )

                                if (isEdgeDockEnabled) {
                                    ExpressiveSettingsNavigationItem(
                                        title = "Handle Position & Geometry",
                                        subtitle = "Screen edge anchor, height, width, and transparency",
                                        onClick = onNavigateToEdgePanelSettings,
                                        shape = connectedPillShape(dockIdx++, totalItems)
                                    )

                                    ExpressiveSettingsNavigationItem(
                                        title = "Motion & Expand Physics",
                                        subtitle = "Spring stiffness, bounce damping, and expand animations",
                                        onClick = onNavigateToEdgeAnimSettings,
                                        shape = connectedPillShape(dockIdx++, totalItems)
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SUBPAGE: HOME SCREEN WIDGETS
                // ==========================================
                SettingsCategory.WIDGETS -> {
                    item { WidgetGalleryList(viewModel, onOpenFolio = onNavigateToWidgetSettings, onOpenScreening = onNavigateToScreeningWidgetSettings) }
                }

                // ==========================================
                // SUBPAGE: SYSTEM & BACKGROUND HEALTH
                // ==========================================
                SettingsCategory.SYSTEM_HEALTH -> Unit
            }
        }

        CollapsibleCommonTopBar(
            title = category.title,
            subtitle = category.subtitle,
            collapseFraction = topBarState.collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigateBack
        )
    }
}

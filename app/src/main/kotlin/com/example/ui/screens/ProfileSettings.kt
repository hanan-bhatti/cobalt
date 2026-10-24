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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Info
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
import com.example.widget.QuickCaptureWidgetReceiver
import com.example.widget.RecentItemsWidgetReceiver

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
    INTELLIGENCE(
        title = "Intelligence & Gemini AI",
        subtitle = "API key, vision OCR models, and detection sensitivity",
        iconRes = R.drawable.ic_settings_intelligence
    ),
    FLOATING_DOCK(
        title = "Floating Dock & Capture",
        subtitle = "Screen-edge handle, instant OCR trigger, and physics",
        iconRes = R.drawable.ic_settings_floating_dock
    ),
    WIDGETS(
        title = "Home Screen Widgets",
        subtitle = "Launcher shortcuts, quick capture action, and recent notes",
        iconRes = R.drawable.ic_settings_widgets
    ),
    SYSTEM_HEALTH(
        title = "System & Background Health",
        subtitle = "Battery optimization, background persistence, and sync",
        iconRes = R.drawable.ic_settings_system_health
    )
}

/**
 * Dual-tone color mapping for Category avatar badges matching PixelPlayer's style.
 */
fun getSettingsCategoryColors(category: SettingsCategory, isDark: Boolean): Pair<Color, Color> {
    return if (isDark) {
        when (category) {
            SettingsCategory.APPEARANCE -> Color(0xFF532342) to Color(0xFFFFD8EC)
            SettingsCategory.INTELLIGENCE -> Color(0xFF004F58) to Color(0xFF88FAFF)
            SettingsCategory.FLOATING_DOCK -> Color(0xFF5E3000) to Color(0xFFFFDCC1)
            SettingsCategory.WIDGETS -> Color(0xFF1E4624) to Color(0xFFB8F5C2)
            SettingsCategory.SYSTEM_HEALTH -> Color(0xFF263A64) to Color(0xFFD8E2FF)
        }
    } else {
        when (category) {
            SettingsCategory.APPEARANCE -> Color(0xFFFFD8EC) to Color(0xFF631B4B)
            SettingsCategory.INTELLIGENCE -> Color(0xFFCCE8EA) to Color(0xFF004F58)
            SettingsCategory.FLOATING_DOCK -> Color(0xFFFFDCC1) to Color(0xFF8B4A00)
            SettingsCategory.WIDGETS -> Color(0xFFD4F8DC) to Color(0xFF14532D)
            SettingsCategory.SYSTEM_HEALTH -> Color(0xFFD8E2FF) to Color(0xFF1E3A8A)
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
    val isDark = isSystemInDarkTheme()
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
    onNavigateToEdgeAnimSettings: (() -> Unit)? = null
) {
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var showEdgePanelSettings by remember { mutableStateOf(false) }
    var showWidgetSettings by remember { mutableStateOf(false) }
    var showEdgeAnimSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = (onNavigateToCategory == null && selectedCategory != null) || (onNavigateToEdgePanelSettings == null && showEdgePanelSettings) || (onNavigateToWidgetSettings == null && showWidgetSettings) || (onNavigateToEdgeAnimSettings == null && showEdgeAnimSettings)) {
        when {
            showEdgeAnimSettings -> showEdgeAnimSettings = false
            showWidgetSettings -> showWidgetSettings = false
            showEdgePanelSettings -> showEdgePanelSettings = false
            selectedCategory != null -> selectedCategory = null
        }
    }

    if (showEdgePanelSettings && onNavigateToEdgePanelSettings == null) {
        EdgePanelSettingsScreen(viewModel = viewModel, onNavigateBack = { showEdgePanelSettings = false })
        return
    }

    if (showWidgetSettings && onNavigateToWidgetSettings == null) {
        WidgetSettingsScreen(viewModel = viewModel, onNavigateBack = { showWidgetSettings = false })
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
            onNavigateToEdgeAnimSettings = onNavigateToEdgeAnimSettings ?: { showEdgeAnimSettings = true }
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
                    val categories = SettingsCategory.entries
                    val total = categories.size

                    categories.forEachIndexed { index, category ->
                        ExpressiveCategoryItem(
                            category = category,
                            onClick = {
                                if (onNavigateToCategory != null) {
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
    onNavigateToEdgeAnimSettings: () -> Unit
) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 160.dp)
    val density = LocalDensity.current

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isIgnoringBattery by remember {
        mutableStateOf(com.example.utils.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isIgnoringBattery = com.example.utils.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
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
                // ==========================================
                // SUBPAGE: APPEARANCE & DISPLAY
                // ==========================================
                SettingsCategory.APPEARANCE -> {
                    item {
                        SettingsSubsectionHeader(
                            title = "Theme & Color Palette",
                            description = "Choose app visual mode and wallpaper dynamic color extraction.",
                            tooltipText = "When Dynamic Color is enabled on Android 12+, Cobalt extracts harmonious accent colors directly from your active wallpaper."
                        )

                        val themeMode by viewModel.settingsRepository.themeMode.collectAsState()
                        val themeOptions = mapOf(
                            "System Default" to "System Default",
                            "Light" to "Light Mode",
                            "Dark" to "Dark Mode"
                        )
                        val hasDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        val dynamicColorEnabled by viewModel.settingsRepository.dynamicColor.collectAsState()
                        val totalThemeItems = if (hasDynamicColor) 2 else 1
                        var idx = 0

                        ExpressiveSettingsGroup {
                            ExpressiveSettingsSelectorItem(
                                title = "App Theme",
                                description = "Light, dark, or system-following interface mode",
                                options = themeOptions,
                                selectedKey = themeMode,
                                onSelectionChanged = { viewModel.settingsRepository.setThemeMode(it) },
                                shape = connectedPillShape(idx++, totalThemeItems)
                            )
                            if (hasDynamicColor) {
                                ExpressiveSettingsSwitchItem(
                                    title = "Dynamic Color",
                                    subtitle = "Adapt accent palette dynamically from wallpaper",
                                    checked = dynamicColorEnabled,
                                    onCheckedChange = { viewModel.settingsRepository.setDynamicColor(it) },
                                    shape = connectedPillShape(idx++, totalThemeItems)
                                )
                            }
                        }
                    }

                    item {
                        val isBlurCapable = DevicePerformance.isDeviceCapableOfBlur(context)
                        val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
                        val blurRadius by viewModel.blurRadius.collectAsState()
                        val blurOpacity by viewModel.blurOpacity.collectAsState()

                        if (isBlurCapable) {
                            SettingsSubsectionHeader(
                                title = "Frosted Glass Blur",
                                description = "Translucent background blur on toolbars, side dock, and dialogs.",
                                tooltipText = "Frosted glass blur requires hardware acceleration. You can adjust the radius and opacity to balance readability and aesthetics."
                            )

                            val totalBlurItems = if (!forceDisableBlur) 4 else 1
                            var blurIdx = 0

                            ExpressiveSettingsGroup {
                                ExpressiveSettingsSwitchItem(
                                    title = "Enable Blur Effects",
                                    subtitle = "Translucent frosted glass across navigation and dock",
                                    checked = !forceDisableBlur,
                                    onCheckedChange = { viewModel.setForceDisableBlur(!it) },
                                    shape = connectedPillShape(blurIdx++, totalBlurItems)
                                )

                                if (!forceDisableBlur) {
                                    ExpressiveSettingsSliderItem(
                                        title = "Blur Diffusion Radius",
                                        subtitle = "Softness intensity of background blur",
                                        value = blurRadius.toFloat(),
                                        valueDisplay = "${blurRadius}dp",
                                        onValueChange = { viewModel.setBlurRadius(it.toInt()) },
                                        valueRange = 5f..50f,
                                        shape = connectedPillShape(blurIdx++, totalBlurItems)
                                    )

                                    ExpressiveSettingsSliderItem(
                                        title = "Surface Tint Opacity",
                                        subtitle = "Density of tinted overlay over blurred elements",
                                        value = blurOpacity,
                                        valueDisplay = "${(blurOpacity * 100).toInt()}%",
                                        onValueChange = { viewModel.setBlurOpacity(it) },
                                        valueRange = 0.05f..0.80f,
                                        shape = connectedPillShape(blurIdx++, totalBlurItems)
                                    )

                                    // Dynamic 10x10 (100 combinations) debounced single-line guidance
                                    val currentGuidance = remember(blurRadius, blurOpacity) {
                                        val rBucket = ((blurRadius - 5) / 45f * 9f).roundToInt().coerceIn(0, 9)
                                        val oBucket = ((blurOpacity - 0.05f) / 0.75f * 9f).roundToInt().coerceIn(0, 9)
                                        blurGuidanceMatrix[rBucket][oBucket]
                                    }
                                    var debouncedGuidance by remember { mutableStateOf(currentGuidance) }
                                    LaunchedEffect(currentGuidance) {
                                        delay(200) // 200ms debounce prevents CPU strain during fast sliding
                                        debouncedGuidance = currentGuidance
                                    }

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = connectedPillShape(blurIdx++, totalBlurItems),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                    ) {
                                        AnimatedContent(
                                            targetState = debouncedGuidance,
                                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                                            label = "blur_slider_guidance"
                                        ) { guidanceText ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = guidanceText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SettingsSubsectionHeader(
                            title = "Haptic Tactile Feedback",
                            description = "Physical tactile vibrations for navigation, switches, and capture.",
                            tooltipText = "Cobalt uses Android's haptic vibrator engine to provide subtle tactile clicks when toggling switches or capturing memories."
                        )

                        var hapticsEnabled by remember {
                            mutableStateOf(com.example.util.HapticManager.isHapticsEnabled(context))
                        }

                        ExpressiveSettingsGroup {
                            ExpressiveSettingsSwitchItem(
                                title = "Tactile Vibrations",
                                subtitle = "Subtle vibration for switches, gestures, and actions",
                                checked = hapticsEnabled,
                                onCheckedChange = { enabled ->
                                    com.example.util.HapticManager.setHapticsEnabled(context, enabled)
                                    hapticsEnabled = enabled
                                    if (enabled) {
                                        com.example.util.HapticManager.performSuccess(context)
                                    }
                                },
                                shape = RoundedCornerShape(24.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // SUBPAGE: INTELLIGENCE & GEMINI AI
                // ==========================================
                SettingsCategory.INTELLIGENCE -> {
                    item {
                        SettingsSubsectionHeader(
                            title = "Gemini API Credentials",
                            description = "Enter your Google AI Studio key to power image OCR and smart note summarization.",
                            tooltipText = "Your API key is stored securely in encrypted local private preferences and only used for your own document extraction requests."
                        )

                        var apiKey by remember { mutableStateOf("") }
                        val currentKey by viewModel.settingsRepository.geminiApiKey.collectAsState()
                        LaunchedEffect(currentKey) { apiKey = currentKey }
                        var keyVisibility by remember { mutableStateOf(false) }
                        val focusManager = LocalFocusManager.current

                        ExpressiveSettingsGroup {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = connectedPillShape(0, 2),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "API Key",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Used for on-device OCR and memory extraction",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = apiKey,
                                        onValueChange = {
                                            apiKey = it
                                            viewModel.settingsRepository.setGeminiApiKey(it)
                                        },
                                        placeholder = { Text("Enter your Gemini API key") },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_custom_key),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            val iconResId = if (keyVisibility) R.drawable.ic_custom_eye else R.drawable.ic_custom_eye_off
                                            val description = if (keyVisibility) "Hide key" else "Show key"
                                            IconButton(onClick = { keyVisibility = !keyVisibility }) {
                                                Icon(
                                                    painter = painterResource(id = iconResId),
                                                    contentDescription = description,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        visualTransformation = if (keyVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                viewModel.settingsRepository.setGeminiApiKey(apiKey)
                                                focusManager.clearFocus()
                                            }
                                        )
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    com.example.util.CobaltCustomTabsLauncher.launch(
                                        context = context,
                                        url = "https://aistudio.google.com/apikey"
                                    )
                                },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = connectedPillShape(1, 2),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Get your Gemini API Key from Google AI Studio",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsSubsectionHeader(
                            title = "Vision OCR & Multimodal Model",
                            description = "Select the Gemini model variant used to analyze screenshots and documents.",
                            tooltipText = "Gemini Flash models are faster and cost-efficient for quick OCR, while Pro models offer deeper reasoning for structured notes."
                        )

                        val models by viewModel.availableModels.collectAsState()
                        val selectedModel by viewModel.settingsRepository.selectedModel.collectAsState()

                        ExpressiveSettingsGroup {
                            ExpressiveSearchableModelSelectorItem(
                                title = "OCR Vision Model",
                                description = "Multimodal model for OCR recognition",
                                models = models,
                                selectedModel = selectedModel,
                                onModelSelected = { viewModel.settingsRepository.setSelectedModel(it) },
                                onRefreshModels = { viewModel.fetchAvailableModels(isUserTriggered = true) },
                                shape = RoundedCornerShape(24.dp)
                            )
                        }
                    }

                    item {
                        SettingsSubsectionHeader(
                            title = "Custom OCR System Prompt",
                            description = "Customize the prompt used by Gemini when extracting text and formatting captured screenshots.",
                            tooltipText = "Leave empty to use the system default extraction rules (which include markdown formatting, RTL script preservation, and exact URL extraction). Custom prompts let you inject specialized instructions or tailored outputs."
                        )

                        val customPrompt by viewModel.settingsRepository.customOcrPrompt.collectAsState()
                        var promptTextState by remember { mutableStateOf("") }
                        LaunchedEffect(customPrompt) {
                            promptTextState = customPrompt
                        }

                        ExpressiveSettingsGroup {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "System Prompt",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                color = if (customPrompt.isNotBlank()) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (customPrompt.isNotBlank()) "Custom Active" else "Default Active",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (customPrompt.isNotBlank()) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        if (customPrompt.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.settingsRepository.resetCustomOcrPrompt()
                                                    promptTextState = ""
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text(
                                                    text = "Reset Default",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (customPrompt.isNotBlank()) {
                                            "Using your custom system prompt for image and document text recognition."
                                        } else {
                                            "Using default prompt with markdown rules, RTL preservation, and URL extraction."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = promptTextState,
                                        onValueChange = {
                                            promptTextState = it
                                            viewModel.settingsRepository.setCustomOcrPrompt(it)
                                        },
                                        placeholder = {
                                            Text(
                                                text = SettingsRepository.DEFAULT_OCR_PROMPT,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        },
                                        minLines = 4,
                                        maxLines = 10,
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsSubsectionHeader(
                            title = "Detection Sensitivity",
                            description = "Threshold for text recognition in low-contrast screenshots.",
                            tooltipText = "High sensitivity captures small or faint text but may pick up background noise. Medium is balanced for everyday captures."
                        )

                        val sensitivity by viewModel.settingsRepository.ocrSensitivity.collectAsState()

                        ExpressiveSettingsGroup {
                            ExpressiveSegmentedChoiceItem(
                                title = "Sensitivity Level",
                                subtitle = "Low, Medium, or High extraction threshold",
                                options = listOf("Low", "Medium", "High"),
                                selectedOption = sensitivity,
                                onOptionSelected = { viewModel.settingsRepository.setOcrSensitivity(it) },
                                shape = RoundedCornerShape(24.dp)
                            )
                        }
                    }

                    item {
                        SettingsSubsectionHeader(
                            title = "Audio Speech-to-Text Model",
                            description = "Select the dedicated Gemini model used to transcribe voice memos and spoken audio.",
                            tooltipText = "Dedicated transcription models convert speech to structured text directly with high accuracy and speed without needing complex prompts."
                        )

                        val audioModels by viewModel.availableAudioModels.collectAsState()
                        val selectedAudioModel by viewModel.settingsRepository.selectedAudioModel.collectAsState()

                        ExpressiveSettingsGroup {
                            ExpressiveSearchableModelSelectorItem(
                                title = "Speech-to-Text Model",
                                description = "Model for audio memo transcription",
                                models = audioModels,
                                selectedModel = selectedAudioModel,
                                onModelSelected = { viewModel.settingsRepository.setSelectedAudioModel(it) },
                                onRefreshModels = { viewModel.fetchAvailableModels(isUserTriggered = true) },
                                shape = RoundedCornerShape(24.dp)
                            )
                        }
                    }
                }

                // ==========================================
                // SUBPAGE: FLOATING DOCK & CAPTURE
                // ==========================================
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
                                                imageVector = Icons.Outlined.Warning,
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
                    item {
                        val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
                        val recentWidgetIds = remember {
                            appWidgetManager.getAppWidgetIds(ComponentName(context, RecentItemsWidgetReceiver::class.java))
                        }
                        val quickWidgetIds = remember {
                            appWidgetManager.getAppWidgetIds(ComponentName(context, QuickCaptureWidgetReceiver::class.java))
                        }
                        val totalActiveWidgets = recentWidgetIds.size + quickWidgetIds.size

                        SettingsSubsectionHeader(
                            title = "Home Screen Widgets",
                            description = if (totalActiveWidgets > 0) "$totalActiveWidgets active widget${if (totalActiveWidgets > 1) "s" else ""} on your launcher."
                                          else "No widgets added to home screen yet.",
                            tooltipText = "Widgets provide instant one-tap note capture and dynamic feeds of your recent ideas directly on your Android launcher."
                        )

                        if (totalActiveWidgets == 0) {
                            ExpressiveSettingsGroup {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Add Widgets to Your Home Screen",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Pin quick capture or recent note widgets directly to your launcher for instant access.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                                            val component = ComponentName(context, QuickCaptureWidgetReceiver::class.java)
                                                            appWidgetManager.requestPinAppWidget(component, null, null)
                                                        } else {
                                                            Toast.makeText(context, "Long-press home screen to add Cobalt widget", Toast.LENGTH_LONG).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(text = "Add Quick Action", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }

                                                Button(
                                                    onClick = {
                                                        if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                                            val component = ComponentName(context, RecentItemsWidgetReceiver::class.java)
                                                            appWidgetManager.requestPinAppWidget(component, null, null)
                                                        } else {
                                                            Toast.makeText(context, "Long-press home screen to add Cobalt widget", Toast.LENGTH_LONG).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(text = "Add Recent Notes", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            ExpressiveSettingsGroup {
                                ExpressiveSettingsNavigationItem(
                                    title = "Widget Customization & Actions",
                                    subtitle = "Configure action buttons, category filters, and opacity",
                                    onClick = onNavigateToWidgetSettings,
                                    shape = connectedPillShape(0, 2)
                                )

                                ExpressiveSettingsNavigationItem(
                                    title = "Force Refresh All Widgets",
                                    subtitle = "Immediately sync active widgets with latest notes",
                                    onClick = {
                                        com.example.widget.WidgetUpdater.update(context)
                                        Toast.makeText(context, "All widgets refreshed successfully!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = connectedPillShape(1, 2)
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // SUBPAGE: SYSTEM & BACKGROUND HEALTH
                // ==========================================
                SettingsCategory.SYSTEM_HEALTH -> {
                    item {
                        val isColorOS = remember { com.example.utils.BatteryOptimizationHelper.isColorOSDevice() }
                        val titleText = if (isColorOS) "Fix Widget & Dock Loading" else "Allow Background Activity"
                        val subtextText = if (isColorOS) {
                            "ColorOS/Oppo/Realme battery managers can aggressively suspend background tasks. Allowing background activity ensures widgets and the floating dock stay updated."
                        } else {
                            "Android battery optimization may throttle background updates. Allow background activity to ensure widgets and edge capture function reliably."
                        }

                        SettingsSubsectionHeader(
                            title = "Battery & Background Persistence",
                            description = subtextText,
                            tooltipText = "Cobalt only uses background execution when updating home screen widgets or synchronizing your local archive."
                        )

                        ExpressiveSettingsGroup {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = if (isIgnoringBattery) "Background Activity Allowed" else "Background Throttling Active",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isIgnoringBattery) "Cobalt is exempt from aggressive battery optimization. Widgets and capture services will run reliably."
                                               else "Tap below to request battery exemption in Android system settings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (!isIgnoringBattery) {
                                        Button(
                                            onClick = {
                                                com.example.utils.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(text = titleText, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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

/**
 * Handle Position & Geometry Settings Screen with Collapsible Top Bar.
 */
@Composable
fun EdgePanelSettingsScreen(viewModel: CobaltViewModel, onNavigateBack: () -> Unit) {
    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 160.dp)
    val density = LocalDensity.current

    val side by viewModel.settingsRepository.edgePanelSide.collectAsState()
    val positionY by viewModel.settingsRepository.edgePanelYPercent.collectAsState()
    val opacity by viewModel.settingsRepository.edgePanelOpacity.collectAsState()
    val height by viewModel.settingsRepository.edgePanelHeight.collectAsState()
    val thickness by viewModel.settingsRepository.edgePanelThickness.collectAsState()

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
            item {
                SettingsSubsectionHeader(
                    title = "Screen Anchor Side",
                    description = "Choose which edge of the screen the floating handle docks to."
                )

                ExpressiveSettingsGroup {
                    ExpressiveSegmentedChoiceItem(
                        title = "Dock Side",
                        subtitle = "Select Left or Right screen boundary",
                        options = listOf("Left", "Right"),
                        selectedOption = side,
                        onOptionSelected = { viewModel.settingsRepository.setEdgePanelSide(it) },
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }

            item {
                SettingsSubsectionHeader(
                    title = "Handle Geometry & Appearance",
                    description = "Fine-tune vertical position, dimensions, and idle surface opacity."
                )

                ExpressiveSettingsGroup {
                    ExpressiveSettingsSliderItem(
                        title = "Vertical Position",
                        subtitle = "Percentage offset along the screen edge",
                        value = positionY,
                        valueDisplay = "${(positionY * 100).toInt()}%",
                        onValueChange = { viewModel.settingsRepository.setEdgePanelYPercent(it) },
                        valueRange = 0f..1f,
                        shape = connectedPillShape(0, 4)
                    )

                    ExpressiveSettingsSliderItem(
                        title = "Handle Opacity",
                        subtitle = "Transparency when idle on screen",
                        value = opacity,
                        valueDisplay = "${(opacity * 100).toInt()}%",
                        onValueChange = { viewModel.settingsRepository.setEdgePanelOpacity(it) },
                        valueRange = 0.1f..1f,
                        shape = connectedPillShape(1, 4)
                    )

                    ExpressiveSettingsSliderItem(
                        title = "Handle Height",
                        subtitle = "Touch target height in dp",
                        value = height.toFloat(),
                        valueDisplay = "${height}dp",
                        onValueChange = { viewModel.settingsRepository.setEdgePanelHeight(it.toInt()) },
                        valueRange = 50f..200f,
                        shape = connectedPillShape(2, 4)
                    )

                    ExpressiveSettingsSliderItem(
                        title = "Handle Width / Thickness",
                        subtitle = "Touch target protrusion in dp",
                        value = thickness.toFloat(),
                        valueDisplay = "${thickness}dp",
                        onValueChange = { viewModel.settingsRepository.setEdgePanelThickness(it.toInt()) },
                        valueRange = 5f..30f,
                        shape = connectedPillShape(3, 4)
                    )
                }
            }
        }

        CollapsibleCommonTopBar(
            title = "Handle Position & Style",
            subtitle = "Screen edge anchor, size, and opacity",
            collapseFraction = topBarState.collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigateBack
        )
    }
}

/**
 * 10x10 (100 combinations) guidance matrix providing a concise, user-friendly
 * 1-line explanation of the trade-offs (GPU speed, blur aesthetics, legibility, and contrast)
 * for each blur radius (0..9) and tint opacity (0..9) combination.
 */
private val blurGuidanceMatrix: Array<Array<String>> = arrayOf(
    // Radius 0: 5-9dp (Ultra Crisp / Minimal Diffusion)
    arrayOf(
        "⚡ Ultra-crisp & zero GPU lag; minimal glass frost effect.",
        "⚡ Sharp underlying text with light tint; near-instant rendering.",
        "⚡ Crisp details with faint tint; good for rapid glanceability.",
        "⚡ Sharp edges with clear separation; very low battery impact.",
        "⚡ Crisp background with balanced tint; text is easily readable.",
        "⚡ Crisp background with medium-dark tint; high contrast.",
        "⚡ Near-solid surface with razor-sharp edges; high legibility.",
        "⚡ Solid card feel with crisp backdrop; minimal frosted sheen.",
        "⚡ Deep dark tint with sharp underlying shadows; ultra-fast.",
        "⚡ Near-opaque solid card with zero blur; maximum readability."
    ),
    // Radius 1: 10-14dp (Very Light Diffusion)
    arrayOf(
        "✨ Subtle frost with faint tint; underlying shapes clearly visible.",
        "✨ Delicate frosted shimmer; smooth speed with light glass feel.",
        "✨ Light frosted texture; good balance of clarity and glass tone.",
        "✨ Clean light frost; text stands out softly over wallpaper.",
        "✨ Moderate tint over light blur; comfortable everyday contrast.",
        "✨ Noticeable card presence; softly dims background elements.",
        "✨ Strong card contrast with light frosted edges; clean reading.",
        "✨ Deep tint with light frost; focused content, high speed.",
        "✨ Dark solid tint with subtle frosted borders; high contrast.",
        "✨ Heavy protective tint with faint frost; maximum text priority."
    ),
    // Radius 2: 15-19dp (Gentle Diffusion)
    arrayOf(
        "✨ Silky light frost; bright background colors shine through.",
        "✨ Gentle frosted glass; elegant look with fast frame rates.",
        "✨ Soft glass layer; wallpaper details gently smoothed out.",
        "✨ Well-defined frosted card; comfortable daily balance.",
        "✨ Balanced glass feel; background clutter gently reduced.",
        "✨ Cozy frosted depth; good separation from vibrant wallpapers.",
        "✨ Deep glass tint; prevents wallpaper patterns from bleeding.",
        "✨ High-contrast glass card; crisp typography, subtle frost.",
        "✨ Rich dark glass; heavy contrast with smooth peripheral blur.",
        "✨ Near-solid frosted container; high readability on any screen."
    ),
    // Radius 3: 20-24dp (Soft Diffusion)
    arrayOf(
        "💎 Smooth glass diffusion; wallpaper shines through softly.",
        "💎 Graceful frosted sheen; soft background with nimble UI.",
        "💎 Modern frosted acrylic; pleasant aesthetic with clear text.",
        "💎 Balanced everyday glass: gentle blur with comfortable tint.",
        "💎 Harmonious glass tone; clean legibility on bright wallpapers.",
        "💎 Deep frosted panel; background noise comfortably faded.",
        "💎 Bold frosted presence; strong contrast against busy apps.",
        "💎 High-legibility frosted shield; sharp icons, low distraction.",
        "💎 Deep tinted glass; smooth background masking, clear focus.",
        "💎 Very dark frosted card; excellent daytime outdoor clarity."
    ),
    // Radius 4: 25-29dp (Balanced Mid-Diffusion)
    arrayOf(
        "💎 Silky authentic glass; background softened into glowing shapes.",
        "💎 Natural frosted glass; refined blur with light glass shine.",
        "💎 Classic frosted acrylic: balanced blur, light tint, fluid motion.",
        "💎 Optimal sweet spot: authentic glass look and crisp typography.",
        "💎 Rich frosted glass: great depth, smooth contrast, fluid FPS.",
        "💎 Solid frosted glass: background softened, zero eye strain.",
        "💎 Pronounced card surface: excellent contrast on bright photos.",
        "💎 Strong frosted shield: dark tint prevents wallpaper glare.",
        "💎 Deep velvet frost: heavy tint with smooth Gaussian blur.",
        "💎 Bold velvet card: strong contrast with deep frosted backdrop."
    ),
    // Radius 5: 30-34dp (Medium-Heavy Diffusion)
    arrayOf(
        "🌟 Dreamy glass diffusion; background colors glow softly.",
        "🌟 Velvety frosted glass; smooth bokeh with bright backdrop.",
        "🌟 Luxurious glass feel; deep background softening, clear text.",
        "🌟 Premium frosted acrylic: soft background bokeh, crisp labels.",
        "🌟 Elegant depth of field: smooth blur with balanced contrast.",
        "🌟 Substantial frosted depth: completely diffuses background.",
        "🌟 High-contrast acrylic: deep tint ensures effortless reading.",
        "🌟 Heavy tinted frost: masks complex wallpapers completely.",
        "🌟 Deep studio frost: bold contrast with velvety smooth blur.",
        "🌟 Near-opaque frosted card: zero background distraction."
    ),
    // Radius 6: 35-39dp (Heavy Diffusion)
    arrayOf(
        "🌟 Atmospheric glass glow; background icons turn into soft color.",
        "🌟 Rich frosted depth; smooth bokeh feel with bright tint.",
        "🌟 Silky studio glass: heavy background softening with clarity.",
        "🌟 Plush frosted glass: deep blur hides launcher icon clutter.",
        "🌟 Refined frosted acrylic: deep depth of field, balanced tint.",
        "🌟 High-depth frosted card: background noise completely gone.",
        "🌟 Strong contrast acrylic: high legibility with heavy bokeh.",
        "🌟 Dense frosted tint: strong protection against busy backgrounds.",
        "🌟 Deep luxury frost: rich dark backdrop with velvet blur.",
        "🌟 Heavy dark acrylic: maximum contrast with profound blur."
    ),
    // Radius 7: 40-44dp (Profound Diffusion)
    arrayOf(
        "🔮 Ethereal glass diffusion; background becomes abstract art.",
        "🔮 Deep bokeh glass; smooth atmospheric blur with light tint.",
        "🔮 Velvety atmospheric glass: dreamy background, sharp text.",
        "🔮 Heavy frosted glass: background elements completely merged.",
        "🔮 Lush frosted acrylic: deep diffusion with balanced tint.",
        "🔮 Substantial dark frost: heavy background concealment.",
        "🔮 Strong frosted contrast: excellent reading comfort.",
        "🔮 Dense protective glass: strong tint prevents any visual clash.",
        "🔮 Deep velvet shield: rich dark blur with studio-grade depth.",
        "🔮 Maximal dark frost: heavy diffusion with dark solid feel."
    ),
    // Radius 8: 45-49dp (Very Heavy Diffusion)
    arrayOf(
        "🔮 Extreme blur diffusion; background details completely erased.",
        "🔮 Ultra-soft frosted glass; profound bokeh with light tint.",
        "🔮 Dreamy luxury glass: rich blur diffusion, clear foreground.",
        "🔮 Deep atmospheric acrylic: high blur hides background noise.",
        "🔮 Balanced heavy frost: full background blur with smooth tint.",
        "🔮 Dense frosted surface: high legibility, velvet background.",
        "🔮 Strong dark acrylic: deep tint with profound background blur.",
        "🔮 Heavy dark frosted card: zero background interference.",
        "🔮 Deep studio velvet: ultra-heavy blur with rich dark tint.",
        "🔮 Maximum dark studio frost: deep blur, maximum contrast."
    ),
    // Radius 9: 50dp (Maximum Diffusion)
    arrayOf(
        "🔮 Maximum blur: background is pure abstract gradient light.",
        "🔮 Ultra-deep bokeh: maximum diffusion with bright transparency.",
        "🔮 Velvet atmospheric frost: total background smoothing.",
        "🔮 High-end frosted glass: maximum blur with light clean tint.",
        "🔮 Supreme frosted balance: ultra-deep blur with optimal tint.",
        "🔮 Luxurious dark frost: complete background concealment.",
        "🔮 Heavy dark acrylic: supreme contrast with maximal blur.",
        "🔮 Maximum frosted shield: complete background distraction shield.",
        "🔮 Deep velvet acrylic: ultra-heavy blur with deep dark tint.",
        "🔮 Studio-grade frosted glass: maximum opacity and maximum blur."
    )
)

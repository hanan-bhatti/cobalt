package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.HapticManager
import com.example.utils.DevicePerformance
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsScreen(viewModel: CobaltViewModel, onNavigateBack: () -> Unit, onNavigateToTextFonts: () -> Unit) {
    val repository = viewModel.settingsRepository
    val themeMode by repository.themeMode.collectAsState()
    val dynamicColor by repository.dynamicColor.collectAsState()
    val hiddenRecents by repository.hideRecents.collectAsState()
    val typography by viewModel.typographyPrefs.collectAsState()
    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val blurOpacity by viewModel.blurOpacity.collectAsState()
    val context = LocalContext.current
    val canUseBlur = remember(context) { DevicePerformance.isDeviceCapableOfBlur(context) }
    var haptics by remember { mutableStateOf(HapticManager.isHapticsEnabled(context)) }
    var showBlurSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(listState, maxHeight = 160.dp)
    val topPadding = with(LocalDensity.current) { topBarState.heightPx.toDp() }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val themeTitle = stringResource(R.string.settings_app_theme)
    val themeDescription = stringResource(R.string.settings_app_theme_sheet_description)
    val themeChoices = listOf(
        SettingsChoice("System Default", stringResource(R.string.settings_theme_system), stringResource(R.string.settings_theme_system_description), R.drawable.ic_settings_system),
        SettingsChoice("Light", stringResource(R.string.settings_theme_light), stringResource(R.string.settings_theme_light_description), R.drawable.ic_settings_sun),
        SettingsChoice("Dark", stringResource(R.string.settings_theme_dark), stringResource(R.string.settings_theme_dark_description), R.drawable.ic_settings_moon)
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(topBarState.nestedScrollConnection)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPadding + 12.dp, start = 20.dp, end = 20.dp, bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize().testTag("appearance_settings_list")
        ) {
            item(key = "theme_colors") {
                SettingsGroupLabel(stringResource(R.string.settings_theme_colors))
                val count = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 2 else 1
                ExpressiveSettingsGroup {
                    ExpressiveSettingsSelectorItem(
                        title = themeTitle,
                        description = stringResource(R.string.settings_app_theme_summary),
                        options = themeChoices.associate { it.key to it.label },
                        selectedKey = themeMode,
                        onSelectionChanged = repository::setThemeMode,
                        leadingIcon = { SettingsIconBadge(R.drawable.ic_settings_theme) },
                        shape = connectedPillShape(0, count),
                        compact = true,
                        sheetContent = { dismiss -> SettingsChoiceSheet(themeTitle, themeDescription, themeChoices, themeMode, repository::setThemeMode, dismiss) }
                    )
                    if (count == 2) {
                        ExpressiveSettingsSwitchItem(
                            title = stringResource(R.string.settings_wallpaper_colors),
                            subtitle = stringResource(R.string.settings_wallpaper_colors_description),
                            checked = dynamicColor,
                            onCheckedChange = repository::setDynamicColor,
                            shape = connectedPillShape(1, count), compact = true
                        )
                    }
                }
            }
            item(key = "text_surfaces") {
                SettingsGroupLabel(stringResource(R.string.settings_reading_surfaces))
                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem(
                        title = stringResource(R.string.text_fonts_title),
                        subtitle = stringResource(R.string.settings_text_fonts_summary),
                        trailingBadge = stringResource(R.string.settings_font_size_value, if (typography.useCustomFont) typography.fontSize else 16),
                        leadingIcon = { SettingsIconBadge(R.drawable.ic_settings_font) },
                        onClick = onNavigateToTextFonts,
                        shape = connectedPillShape(0, 2), compact = true
                    )
                    ExpressiveSettingsNavigationItem(
                        title = stringResource(R.string.settings_blur),
                        subtitle = if (canUseBlur) stringResource(R.string.settings_blur_summary) else stringResource(R.string.settings_blur_unavailable),
                        trailingBadge = stringResource(if (canUseBlur && !forceDisableBlur) R.string.settings_on else R.string.settings_off),
                        leadingIcon = { SettingsIconBadge(R.drawable.ic_settings_blur) },
                        onClick = { showBlurSheet = true },
                        shape = connectedPillShape(1, 2), compact = true
                    )
                }
            }
            item(key = "interaction_privacy") {
                SettingsGroupLabel(stringResource(R.string.settings_interaction_privacy))
                ExpressiveSettingsGroup {
                    FloatingScrollbarSettingsItem(viewModel, shape = connectedPillShape(0, 3), compact = true)
                    ExpressiveSettingsSwitchItem(
                        title = stringResource(R.string.settings_haptics),
                        subtitle = stringResource(R.string.settings_haptics_description),
                        checked = haptics,
                        onCheckedChange = {
                            haptics = it
                            HapticManager.setHapticsEnabled(context, it)
                            if (it) HapticManager.performSuccess(context)
                        },
                        shape = connectedPillShape(1, 3), compact = true
                    )
                    ExpressiveSettingsSwitchItem(
                        title = stringResource(R.string.settings_screen_privacy),
                        subtitle = stringResource(R.string.settings_screen_privacy_description),
                        checked = hiddenRecents,
                        onCheckedChange = repository::setHideRecents,
                        shape = connectedPillShape(2, 3), compact = true
                    )
                }
            }
        }
        CollapsibleCommonTopBar(
            title = stringResource(R.string.settings_appearance),
            subtitle = stringResource(R.string.settings_appearance_subtitle),
            collapseFraction = topBarState.collapseFraction,
            headerHeight = topPadding,
            onBackClick = onNavigateBack
        )
    }
    if (showBlurSheet) {
        SettingsBottomSheet(stringResource(R.string.settings_blur), stringResource(R.string.settings_blur_sheet_description), { showBlurSheet = false }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpressiveSettingsSwitchItem(
                    title = stringResource(R.string.settings_enable_blur),
                    subtitle = stringResource(if (canUseBlur) R.string.settings_enable_blur_description else R.string.settings_blur_unavailable),
                    checked = canUseBlur && !forceDisableBlur,
                    enabled = canUseBlur,
                    onCheckedChange = { viewModel.setForceDisableBlur(!it) }, compact = true
                )
                if (canUseBlur && !forceDisableBlur) {
                    ExpressiveTypographySliderItem(
                        title = stringResource(R.string.settings_blur_softness),
                        value = blurRadius.toFloat(), valueDisplay = stringResource(R.string.settings_dp_value, blurRadius),
                        valueRange = 5f..50f,
                        onValueChange = { viewModel.setBlurRadius(it.roundToInt()) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_settings_blur), null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                        compact = true
                    )
                    ExpressiveTypographySliderItem(
                        title = stringResource(R.string.settings_tint_strength),
                        value = blurOpacity, valueDisplay = stringResource(R.string.settings_code_percent_value, (blurOpacity * 100).roundToInt()),
                        valueRange = 0.05f.. 0.8f,
                        onValueChange = viewModel::setBlurOpacity,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_settings_blur), null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                        compact = true
                    )
                    val currentGuidance = blurAppearanceGuidance(blurRadius, blurOpacity)
                    var displayedGuidance by remember { mutableIntStateOf(currentGuidance) }
                    LaunchedEffect(currentGuidance) {
                        delay(200)
                        displayedGuidance = currentGuidance
                    }
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_custom_info), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                stringResource(displayedGuidance), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).testTag("blur_value_guidance").semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }
                    }
                }
            }
        }
    }
}

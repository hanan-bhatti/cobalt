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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CodeFontFamily
import com.example.data.model.TextFontFamily
import com.example.data.model.TypographyPrefs
import com.example.data.model.TypographyPreset
import com.example.ui.components.CollapsibleCommonTopBar
import com.example.ui.components.ExpressiveSettingsGroup
import com.example.ui.components.ExpressiveSettingsSelectorItem
import com.example.ui.components.ExpressiveSettingsSwitchItem
import com.example.ui.components.ExpressiveTypographySliderItem
import com.example.ui.components.SettingsChoice
import com.example.ui.components.SettingsChoiceSheet
import com.example.ui.components.SettingsGroupLabel
import com.example.ui.components.SettingsIconBadge
import com.example.ui.components.connectedPillShape
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.theme.CobaltTypography
import com.example.ui.theme.resolveCodeFontFamily
import com.example.ui.theme.resolveTextFontFamily
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** A separate reading-settings route; NavHost owns predictive Back and its depth animation. */
@Composable
fun TextAndFontsSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storedPrefs by viewModel.typographyPrefs.collectAsState()
    var localPrefs by remember(storedPrefs) { mutableStateOf(storedPrefs) }
    val latestPrefs by rememberUpdatedState(localPrefs)
    val latestStoredPrefs by rememberUpdatedState(storedPrefs)

    // The preview updates immediately. Flush on leaving so a quick Back cannot lose the last edit.
    LaunchedEffect(localPrefs) {
        if (localPrefs != storedPrefs) {
            delay(150)
            viewModel.setTypographyPrefs(localPrefs)
        }
    }
    DisposableEffect(viewModel) {
        onDispose {
            if (latestPrefs != latestStoredPrefs) viewModel.setTypographyPrefs(latestPrefs)
        }
    }

    val liveTypography = remember(localPrefs) { CobaltTypography.fromPrefs(localPrefs) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resetMessage = stringResource(R.string.settings_reset_text_done)
    val undoLabel = stringResource(R.string.reset_typography_undo_action)
    val listState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(listState, maxHeight = 160.dp)
    val topPadding = with(LocalDensity.current) { topBarState.heightPx.toDp() }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val updatePrefs: (TypographyPrefs) -> Unit = { prefs ->
        snackbarHost.currentSnackbarData?.dismiss()
        localPrefs = prefs
    }
    val resetPrefs: () -> Unit = {
        val beforeReset = localPrefs
        localPrefs = TypographyPrefs.DEFAULT
        viewModel.setTypographyPrefs(localPrefs)
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            if (snackbarHost.showSnackbar(resetMessage, undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                localPrefs = beforeReset
                viewModel.setTypographyPrefs(beforeReset)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (maxWidth >= 640.dp) {
            Column(Modifier.fillMaxSize()) {
                CollapsibleCommonTopBar(
                    title = stringResource(R.string.text_fonts_title),
                    subtitle = stringResource(R.string.settings_fonts_subtitle),
                    collapseFraction = 0f,
                    headerHeight = 90.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    onBackClick = onNavigateBack
                )
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
                    ) {
                        item { TextFontsPreviewCard(liveTypography) }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        textFontsSettingsItems(localPrefs, updatePrefs, resetPrefs)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().nestedScroll(topBarState.nestedScrollConnection)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = topPadding + 12.dp, start = 20.dp, end = 20.dp, bottom = bottomPadding + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize().testTag("text_fonts_settings_list")
                ) {
                    item(key = "reading_preview") { TextFontsPreviewCard(liveTypography) }
                    textFontsSettingsItems(localPrefs, updatePrefs, resetPrefs)
                }
                CollapsibleCommonTopBar(
                    title = stringResource(R.string.text_fonts_title),
                    subtitle = stringResource(R.string.settings_fonts_subtitle),
                    collapseFraction = topBarState.collapseFraction,
                    headerHeight = topPadding,
                    onBackClick = onNavigateBack
                )
            }
        }
        SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
    }
}

internal fun LazyListScope.textFontsSettingsItems(
    localPrefs: TypographyPrefs,
    onUpdatePrefs: (TypographyPrefs) -> Unit,
    onResetDefaults: () -> Unit
) {
    item(key = "font_activation") {
        SettingsGroupLabel(stringResource(R.string.settings_font_activation))
        ExpressiveSettingsGroup {
            ExpressiveSettingsSwitchItem(
                title = stringResource(R.string.settings_use_custom_font),
                subtitle = stringResource(R.string.settings_use_custom_font_description),
                checked = localPrefs.useCustomFont,
                onCheckedChange = { onUpdatePrefs(localPrefs.copy(useCustomFont = it).validated()) },
                shape = connectedPillShape(0, 2),
                compact = true
            )
            ExpressiveSettingsSwitchItem(
                title = stringResource(R.string.settings_apply_font_to_app),
                subtitle = stringResource(R.string.settings_apply_font_to_app_description),
                checked = localPrefs.applyToAppUi,
                enabled = localPrefs.useCustomFont,
                onCheckedChange = { onUpdatePrefs(localPrefs.copy(applyToAppUi = it).validated()) },
                shape = connectedPillShape(1, 2),
                compact = true
            )
        }
    }
    item(key = "font_families_and_presets") {
        SettingsGroupLabel(stringResource(R.string.settings_font_families))
        val presetOptions = TypographyPreset.entries.associate { it.key to it.displayName }
        val presetDescriptions = listOf(
            R.string.settings_preset_comfortable_description,
            R.string.settings_preset_compact_description,
            R.string.settings_preset_reading_description,
            R.string.settings_preset_custom_description
        )
        val presetOrder = listOf(TypographyPreset.COMFORTABLE, TypographyPreset.COMPACT, TypographyPreset.READING, TypographyPreset.CUSTOM)
        val presetChoices = presetOrder.mapIndexed { index, preset ->
            SettingsChoice(preset.key, preset.displayName, stringResource(presetDescriptions[index]))
        }
        val fontSample = stringResource(R.string.settings_font_sample)
        val textChoices = TextFontFamily.entries.map {
            SettingsChoice(it.key, it.displayName, fontSample, fontFamily = resolveTextFontFamily(it, localPrefs.fontWeight))
        }
        val codeChoices = CodeFontFamily.entries.map {
            SettingsChoice(
                it.key, it.displayName,
                if (it == CodeFontFamily.SYSTEM_MONOSPACE) stringResource(R.string.settings_system_monospace_description) else stringResource(R.string.settings_code_font_sample),
                fontFamily = resolveCodeFontFamily(it)
            )
        }
        ExpressiveSettingsGroup {
            val presetTitle = stringResource(R.string.settings_reading_preset)
            val presetDescription = stringResource(R.string.settings_reading_preset_sheet_description)
            val selectPreset: (String) -> Unit = { key ->
                // Custom is the current set of slider values, rather than another canned preset.
                if (key != TypographyPreset.CUSTOM.key) onUpdatePrefs(localPrefs.withPreset(TypographyPreset.fromKey(key)))
            }
            ExpressiveSettingsSelectorItem(
                title = presetTitle,
                description = stringResource(R.string.settings_reading_preset_summary),
                options = presetOptions,
                selectedKey = localPrefs.preset.key,
                onSelectionChanged = selectPreset,
                leadingIcon = { SettingsIconBadge(R.drawable.ic_settings_reading) },
                shape = connectedPillShape(0, 3),
                compact = true,
                selectedValueInDescription = true,
                enabled = localPrefs.useCustomFont,
                sheetContent = { dismiss -> SettingsChoiceSheet(presetTitle, presetDescription, presetChoices, localPrefs.preset.key, selectPreset, dismiss) }
            )
            val textTitle = stringResource(R.string.settings_text_font)
            val textDescription = stringResource(R.string.settings_text_font_sheet_description)
            val selectTextFont: (String) -> Unit = { key -> onUpdatePrefs(localPrefs.copy(textFont = TextFontFamily.fromKey(key)).validated()) }
            ExpressiveSettingsSelectorItem(
                title = textTitle,
                description = stringResource(R.string.settings_text_font_description),
                options = TextFontFamily.entries.associate { it.key to it.displayName },
                selectedKey = localPrefs.textFont.key,
                onSelectionChanged = selectTextFont,
                leadingIcon = { SettingsIconBadge(R.drawable.ic_settings_font) },
                shape = connectedPillShape(1, 3),
                compact = true,
                selectedValueInDescription = true,
                enabled = localPrefs.useCustomFont,
                sheetContent = { dismiss -> SettingsChoiceSheet(textTitle, textDescription, textChoices, localPrefs.textFont.key, selectTextFont, dismiss) }
            )
            val codeTitle = stringResource(R.string.settings_code_font)
            val codeDescription = stringResource(R.string.settings_code_font_sheet_description)
            val selectCodeFont: (String) -> Unit = { key -> onUpdatePrefs(localPrefs.copy(codeFont = CodeFontFamily.fromKey(key)).validated()) }
            ExpressiveSettingsSelectorItem(
                title = codeTitle,
                description = stringResource(R.string.settings_code_font_description),
                options = CodeFontFamily.entries.associate { it.key to it.displayName },
                selectedKey = localPrefs.codeFont.key,
                onSelectionChanged = selectCodeFont,
                leadingIcon = { SettingsIconBadge(R.drawable.ic_settings_code) },
                shape = connectedPillShape(2, 3),
                compact = true,
                selectedValueInDescription = true,
                enabled = localPrefs.useCustomFont,
                sheetContent = { dismiss -> SettingsChoiceSheet(codeTitle, codeDescription, codeChoices, localPrefs.codeFont.key, selectCodeFont, dismiss) }
            )
        }
    }
    item(key = "typography_metrics") {
        SettingsGroupLabel(stringResource(R.string.settings_size_spacing))
        ExpressiveSettingsGroup {
            ExpressiveTypographySliderItem(
                title = stringResource(R.string.settings_font_size),
                value = localPrefs.fontSize.toFloat(),
                valueDisplay = stringResource(R.string.settings_font_size_value, localPrefs.fontSize),
                valueRange = 12f..24f,
                steps = 11,
                onValueChange = { onUpdatePrefs(localPrefs.copy(fontSize = it.toInt()).validated()) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_settings_size), null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                shape = connectedPillShape(0, 5), compact = true, enabled = localPrefs.useCustomFont
            )
            ExpressiveTypographySliderItem(
                title = stringResource(R.string.settings_font_weight),
                value = localPrefs.fontWeight.toFloat(),
                valueDisplay = localPrefs.fontWeight.toString(),
                valueRange = if (localPrefs.textFont.isVariable) 300f..700f else 400f..700f,
                steps = if (localPrefs.textFont.isVariable) 7 else 0,
                onValueChange = { onUpdatePrefs(localPrefs.copy(fontWeight = it.toInt()).validated()) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_settings_weight), null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                shape = connectedPillShape(1, 5), compact = true, enabled = localPrefs.useCustomFont
            )
            ExpressiveTypographySliderItem(
                title = stringResource(R.string.settings_line_spacing),
                value = localPrefs.lineHeight,
                valueDisplay = String.format(Locale.getDefault(), "%.2f×", localPrefs.lineHeight),
                valueRange = 1.2f..2f,
                steps = 15,
                onValueChange = { onUpdatePrefs(localPrefs.copy(lineHeight = it).validated()) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_settings_line), null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                shape = connectedPillShape(2, 5), compact = true, enabled = localPrefs.useCustomFont
            )
            ExpressiveTypographySliderItem(
                title = stringResource(R.string.settings_letter_spacing),
                value = localPrefs.letterSpacing,
                valueDisplay = String.format(Locale.getDefault(), "%.2f em", localPrefs.letterSpacing),
                valueRange = -0.02f..0.1f,
                steps = 11,
                onValueChange = { onUpdatePrefs(localPrefs.copy(letterSpacing = it).validated()) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_settings_letters), null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                shape = connectedPillShape(3, 5), compact = true, enabled = localPrefs.useCustomFont
            )
            ExpressiveTypographySliderItem(
                title = stringResource(R.string.settings_paragraph_spacing),
                value = localPrefs.paragraphSpacing.toFloat(),
                valueDisplay = stringResource(R.string.settings_dp_value, localPrefs.paragraphSpacing),
                valueRange = 0f..16f,
                steps = 7,
                onValueChange = { onUpdatePrefs(localPrefs.copy(paragraphSpacing = it.toInt()).validated()) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_settings_paragraph), null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                shape = connectedPillShape(4, 5), compact = true, enabled = localPrefs.useCustomFont
            )
        }
    }
    item(key = "reset_text_settings") {
        TextButton(onClick = onResetDefaults, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.settings_reset_text), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A sample note, with the effective reading preferences applied to every note style. */
@Composable
fun TextFontsPreviewCard(typography: CobaltTypography, modifier: Modifier = Modifier) {
    val prefs = typography.prefs
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().testTag("reading_preview")
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(typography.paragraphSpacingDp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_reading_preview), style = typography.h2, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.settings_font_preview_label, if (prefs.useCustomFont) prefs.textFont.displayName else TextFontFamily.SYSTEM.displayName, if (prefs.useCustomFont) prefs.fontSize else 16),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
            }
            Text(AnnotatedString.fromHtml(stringResource(R.string.settings_preview_paragraph)), style = typography.body, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.settings_preview_inline_code), style = typography.inlineCode, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = true, onCheckedChange = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.settings_preview_checklist), style = typography.checklist, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.settings_preview_quote), style = typography.quote, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_preview_code_block), style = typography.codeBlock, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp))
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(stringResource(R.string.settings_preview_urdu), style = typography.body.copy(textDirection = TextDirection.Rtl), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.settings_preview_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

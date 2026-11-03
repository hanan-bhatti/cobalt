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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.components.SettingsSubsectionHeader
import com.example.ui.components.connectedPillShape
import com.example.ui.components.rememberCollapsibleTopBarState
import com.example.ui.theme.CobaltTypography
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Text & fonts Subpage inside Appearance settings.
 * Features:
 * - Pinned Live Preview card (updating on every drag frame with 0 jank)
 * - Two-pane responsive layout for wide displays / landscape
 * - Same bottom-sheet selectors and tokens as the rest of Settings
 * - Debounced persistence to SettingsRepository (150ms) to protect other screens
 * - Confirmation and Undo snackbar on Reset to defaults
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextAndFontsSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storedPrefs by viewModel.typographyPrefs.collectAsState()
    var localPrefs by remember(storedPrefs) { mutableStateOf(storedPrefs) }

    // Debounced persistence: changes apply to preview immediately, but
    // persist to repository only after 150ms of quiet time.
    LaunchedEffect(localPrefs) {
        if (localPrefs != storedPrefs) {
            delay(150)
            viewModel.setTypographyPrefs(localPrefs)
        }
    }

    val liveTypography = remember(localPrefs) {
        CobaltTypography.fromPrefs(localPrefs)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showResetDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val topBarState = rememberCollapsibleTopBarState(lazyListState, maxHeight = 160.dp)
    val density = LocalDensity.current

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { scaffoldPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            val isWideLayout = maxWidth >= 640.dp
            val currentTopBarHeightDp = with(density) { topBarState.heightPx.toDp() }
            val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            if (isWideLayout) {
                // Two-pane responsive layout for tablets / wide screens
                Column(modifier = Modifier.fillMaxSize()) {
                    CollapsibleCommonTopBar(
                        title = stringResource(R.string.text_fonts_title),
                        subtitle = stringResource(R.string.text_fonts_subtitle),
                        collapseFraction = 0f,
                        headerHeight = 90.dp,
                        onBackClick = onNavigateBack
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Left Pane: Pinned Live Preview
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            TextFontsPreviewCard(
                                typography = liveTypography,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Right Pane: Scrolling Settings Controls
                        LazyColumn(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = bottomNavPadding + 32.dp)
                        ) {
                            textFontsSettingsItems(
                                localPrefs = localPrefs,
                                onUpdatePrefs = { localPrefs = it },
                                onShowResetDialog = { showResetDialog = true }
                            )
                        }
                    }
                }
            } else {
                // Phone single-pane layout with Pinned Preview Card at top
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(topBarState.nestedScrollConnection)
                ) {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(
                            top = currentTopBarHeightDp + 12.dp,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = bottomNavPadding + 48.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Pinned Live Preview Item
                        item(key = "live_preview_card") {
                            TextFontsPreviewCard(
                                typography = liveTypography,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        textFontsSettingsItems(
                            localPrefs = localPrefs,
                            onUpdatePrefs = { localPrefs = it },
                            onShowResetDialog = { showResetDialog = true }
                        )
                    }

                    CollapsibleCommonTopBar(
                        title = stringResource(R.string.text_fonts_title),
                        subtitle = stringResource(R.string.text_fonts_subtitle),
                        collapseFraction = topBarState.collapseFraction,
                        headerHeight = currentTopBarHeightDp,
                        onBackClick = onNavigateBack
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.reset_typography_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.reset_typography_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val previousSnapshot = localPrefs
                        showResetDialog = false
                        localPrefs = TypographyPrefs.DEFAULT
                        viewModel.setTypographyPrefs(TypographyPrefs.DEFAULT)

                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Typography reset to defaults",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                localPrefs = previousSnapshot
                                viewModel.setTypographyPrefs(previousSnapshot)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.reset_typography_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.reset_typography_cancel_action))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

/**
 * Shared item builder for controls in phone and tablet layouts.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.textFontsSettingsItems(
    localPrefs: TypographyPrefs,
    onUpdatePrefs: (TypographyPrefs) -> Unit,
    onShowResetDialog: () -> Unit
) {
    // Subsection 1: Master Toggles
    item(key = "master_toggles") {
        SettingsSubsectionHeader(
            title = "Font Activation",
            description = "Control custom typography in notes and across Cobalt's interface.",
            tooltipText = "When disabled, notes, reader and code render in Android's default system typeface."
        )

        ExpressiveSettingsGroup {
            ExpressiveSettingsSwitchItem(
                title = "Use custom font",
                subtitle = "Apply selected font family and metrics to notes, reader, and editor",
                checked = localPrefs.useCustomFont,
                onCheckedChange = { onUpdatePrefs(localPrefs.copy(useCustomFont = it).validated()) },
                shape = connectedPillShape(0, 2)
            )

            ExpressiveSettingsSwitchItem(
                title = "Apply to app UI",
                subtitle = "Extend the text font to all screens, buttons, and system dialogs",
                checked = localPrefs.applyToAppUi,
                enabled = localPrefs.useCustomFont,
                onCheckedChange = { onUpdatePrefs(localPrefs.copy(applyToAppUi = it).validated()) },
                shape = connectedPillShape(1, 2)
            )
        }
    }

    // Subsection 2: Font Families & Presets
    item(key = "font_families_and_presets") {
        SettingsSubsectionHeader(
            title = "Font Families & Presets",
            description = "Select bundled font families and quick reading presets.",
            tooltipText = "All fonts are bundled locally with SIL Open Font License. No network connection is used."
        )

        val presetOptions = mapOf(
            "comfortable" to "Comfortable (Default)",
            "compact" to "Compact",
            "reading" to "Reading",
            "custom" to "Custom"
        )

        val textFontOptions = TextFontFamily.entries.associate { it.key to it.displayName }
        val codeFontOptions = CodeFontFamily.entries.associate { it.key to it.displayName }

        ExpressiveSettingsGroup {
            ExpressiveSettingsSelectorItem(
                title = "Reading Preset",
                description = "Ergonomic presets for size, line height, and spacing",
                options = presetOptions,
                selectedKey = localPrefs.preset.key,
                onSelectionChanged = { key ->
                    val chosen = TypographyPreset.fromKey(key)
                    onUpdatePrefs(localPrefs.withPreset(chosen))
                },
                shape = connectedPillShape(0, 3)
            )

            ExpressiveSettingsSelectorItem(
                title = "Text Font",
                description = "Primary typeface for note bodies, markdown, and reader",
                options = textFontOptions,
                selectedKey = localPrefs.textFont.key,
                onSelectionChanged = { key ->
                    val chosen = TextFontFamily.fromKey(key)
                    onUpdatePrefs(localPrefs.copy(textFont = chosen).validated())
                },
                shape = connectedPillShape(1, 3)
            )

            ExpressiveSettingsSelectorItem(
                title = "Code Font",
                description = "Monospace typeface for code blocks and inline tokens",
                options = codeFontOptions,
                selectedKey = localPrefs.codeFont.key,
                onSelectionChanged = { key ->
                    val chosen = CodeFontFamily.fromKey(key)
                    onUpdatePrefs(localPrefs.copy(codeFont = chosen).validated())
                },
                shape = connectedPillShape(2, 3)
            )
        }
    }

    // Subsection 3: Metric Sliders
    item(key = "metric_sliders") {
        SettingsSubsectionHeader(
            title = "Typography Metrics",
            description = "Fine-tune font size, weight, line height, letter spacing, and paragraph spacing.",
            tooltipText = "Adjusting any metric that deviates from a standard preset automatically flips preset to Custom."
        )

        val textFamily = localPrefs.textFont
        val currentFontFamily = CobaltTypography.fromPrefs(localPrefs).textFontFamily
        val totalSliders = 5
        var sliderIdx = 0

        ExpressiveSettingsGroup {
            // 1. Font Size (12..24 sp, step 1, default 16)
            ExpressiveTypographySliderItem(
                title = "Font size",
                subtitle = "Base body text size in scalable points",
                value = localPrefs.fontSize.toFloat(),
                valueDisplay = "${localPrefs.fontSize} sp",
                valueRange = 12f..24f,
                steps = 11, // 12 to 24 = 11 intermediate steps
                onValueChange = { newVal ->
                    val rounded = newVal.toInt()
                    onUpdatePrefs(localPrefs.copy(fontSize = rounded).validated())
                },
                startHint = {
                    Text("A", fontSize = 12.sp, fontFamily = currentFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                endHint = {
                    Text("A", fontSize = 22.sp, fontFamily = currentFontFamily, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                },
                shape = connectedPillShape(sliderIdx++, totalSliders)
            )

            // 2. Font Weight (300..700, step 50, default 400)
            val isWeightSupported = textFamily.isVariable
            val weightDisplay = if (!isWeightSupported) {
                if (localPrefs.fontWeight > 550) "700 (Bold)" else "400 (Regular)"
            } else {
                "${localPrefs.fontWeight}"
            }
            ExpressiveTypographySliderItem(
                title = "Font weight",
                subtitle = "Stroke weight of body text glyphs",
                value = localPrefs.fontWeight.toFloat(),
                valueDisplay = weightDisplay,
                valueRange = 300f..700f,
                steps = 7, // 300, 350, 400, 450, 500, 550, 600, 650, 700 = 7 intermediate steps
                enabled = true,
                disabledHint = if (!isWeightSupported) "${textFamily.displayName} is static: snaps between 400 (Regular) and 700 (Bold)" else null,
                onValueChange = { newVal ->
                    val snapped = if (!isWeightSupported) {
                        if (newVal > 550) 700 else 400
                    } else {
                        ((newVal + 25) / 50).toInt() * 50
                    }
                    onUpdatePrefs(localPrefs.copy(fontWeight = snapped).validated())
                },
                startHint = {
                    Text("Aa", fontSize = 14.sp, fontWeight = FontWeight.W300, fontFamily = currentFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                endHint = {
                    Text("Aa", fontSize = 14.sp, fontWeight = FontWeight.W700, fontFamily = currentFontFamily, color = MaterialTheme.colorScheme.primary)
                },
                shape = connectedPillShape(sliderIdx++, totalSliders)
            )

            // 3. Line Height (1.2 to 2.0, step 0.05, default 1.5)
            val formattedLineHeight = String.format(Locale.ROOT, "%.2fx", localPrefs.lineHeight)
            ExpressiveTypographySliderItem(
                title = "Line height",
                subtitle = "Vertical distance between baselines",
                value = localPrefs.lineHeight,
                valueDisplay = formattedLineHeight,
                valueRange = 1.20f..2.00f,
                steps = 15, // (2.0 - 1.2) / 0.05 = 16 intervals -> 15 steps
                onValueChange = { newVal ->
                    val rounded = kotlin.math.round(newVal * 20f) / 20f
                    onUpdatePrefs(localPrefs.copy(lineHeight = rounded).validated())
                },
                startHint = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("—", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("—", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                endHint = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("—", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        Text("—", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                shape = connectedPillShape(sliderIdx++, totalSliders)
            )

            // 4. Letter Spacing (-0.02 to +0.10 em, step 0.01, default 0)
            val formattedLetterSpacing = String.format(Locale.ROOT, "%+.2f em", localPrefs.letterSpacing)
            ExpressiveTypographySliderItem(
                title = "Letter spacing",
                subtitle = "Tracking between individual glyphs",
                value = localPrefs.letterSpacing,
                valueDisplay = formattedLetterSpacing,
                valueRange = -0.02f..0.10f,
                steps = 11, // 12 intervals -> 11 steps
                onValueChange = { newVal ->
                    val rounded = kotlin.math.round(newVal * 100f) / 100f
                    onUpdatePrefs(localPrefs.copy(letterSpacing = rounded).validated())
                },
                startHint = {
                    Text("AV", fontSize = 13.sp, letterSpacing = (-0.02).sp, fontFamily = currentFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                endHint = {
                    Text("A V", fontSize = 13.sp, letterSpacing = 0.10.sp, fontFamily = currentFontFamily, color = MaterialTheme.colorScheme.primary)
                },
                shape = connectedPillShape(sliderIdx++, totalSliders)
            )

            // 5. Paragraph Spacing (0 to 16 dp, step 2, default 8)
            ExpressiveTypographySliderItem(
                title = "Paragraph spacing",
                subtitle = "Vertical margin between separate blocks",
                value = localPrefs.paragraphSpacing.toFloat(),
                valueDisplay = "${localPrefs.paragraphSpacing} dp",
                valueRange = 0f..16f,
                steps = 7, // 8 intervals -> 7 steps
                onValueChange = { newVal ->
                    val rounded = (((newVal + 1) / 2).toInt() * 2).coerceIn(0, 16)
                    onUpdatePrefs(localPrefs.copy(paragraphSpacing = rounded).validated())
                },
                startHint = {
                    Text("¶", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                endHint = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("¶", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text("¶", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                shape = connectedPillShape(sliderIdx++, totalSliders)
            )
        }
    }

    // Subsection 4: Reset Action
    item(key = "reset_section") {
        OutlinedButton(
            onClick = onShowResetDialog,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_custom_undo),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Reset typography to defaults",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Pinned Live Preview Card demonstrating real-time typography styling across:
 * - Headings
 * - Rich paragraph formatting (bold, italic, strike, link)
 * - Inline code token
 * - Interactive checklist item
 * - Block quote with accent bar
 * - Fenced code block
 * - Right-to-Left Urdu line with platform fallback glyphs
 */
@Composable
fun TextFontsPreviewCard(
    typography: CobaltTypography,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(typography.paragraphSpacingDp)
        ) {
            // Live Preview Header Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "LIVE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "${typography.prefs.textFont.displayName} • ${typography.prefs.fontSize}sp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 1. Heading
            Text(
                text = stringResource(R.string.preview_heading),
                style = typography.h2,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 2. Paragraph with Bold, Italic, Strikethrough, Link, and Inline Code
            val paragraphAnnotated = buildAnnotatedString {
                append(stringResource(R.string.preview_paragraph_prefix))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(stringResource(R.string.preview_paragraph_bold))
                }
                append(stringResource(R.string.preview_paragraph_middle))
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(stringResource(R.string.preview_paragraph_italic))
                }
                append(", ")
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(stringResource(R.string.preview_paragraph_strike))
                }
                append(stringResource(R.string.preview_paragraph_and))
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(stringResource(R.string.preview_paragraph_link))
                }
                append(". Try inline code: ")
                withStyle(
                    SpanStyle(
                        fontFamily = typography.codeFontFamily,
                        fontSize = typography.inlineCode.fontSize,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        color = MaterialTheme.colorScheme.primary
                    )
                ) {
                    append(" " + stringResource(R.string.preview_inline_code) + " ")
                }
            }

            Text(
                text = paragraphAnnotated,
                style = typography.body,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 3. Checklist Item
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = true,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.preview_checklist),
                    style = typography.checklist,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 4. Block Quote with Left Accent Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.preview_quote),
                    style = typography.quote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 5. Fenced Code Block
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(R.string.preview_code_block),
                        style = typography.codeBlock,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 6. Right-to-Left Urdu Line (System Font Fallback Verification)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = stringResource(R.string.preview_rtl_urdu),
                    style = typography.body.copy(textDirection = TextDirection.Rtl),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

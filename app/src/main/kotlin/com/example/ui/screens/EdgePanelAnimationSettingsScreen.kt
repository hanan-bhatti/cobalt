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

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.CobaltSideDockService
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import kotlin.math.roundToInt

@Composable
fun EdgePanelAnimationSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = viewModel.settingsRepository
    val settings = rememberSideDockPreviewSettings(repository)
    val enabled by repository.isFloatingOcrEnabled.collectAsState()
    val overlayAllowed = rememberSideDockOverlayPermission()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var error by remember { mutableStateOf<String?>(null) }
    val startError = stringResource(R.string.settings_dock_start_error)
    val permissionError = stringResource(R.string.settings_dock_permission_error)
    LaunchedEffect(enabled, overlayAllowed) {
        try {
            if (enabled && overlayAllowed) startSideDock(context)
            else context.stopService(Intent(context, CobaltSideDockService::class.java))
        } catch (_: Exception) { error = startError }
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); error = null } }
    Box(modifier.fillMaxSize()) {
        SideDockAnimationSettingsContent(
            settings, enabled, overlayAllowed,
            onEnabledChanged = repository::setFloatingOcrEnabled,
            onRequestPermission = {
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                } catch (_: Exception) { error = permissionError }
            },
            onPresetChanged = repository::applyEdgePanelAnimPreset,
            onCustomChanged = repository::setCustomEdgePanelAnimation,
            onOpenDock = { try { startSideDock(context, open = true) } catch (_: Exception) { error = startError } },
            onNavigateBack = onNavigateBack
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
internal fun SideDockAnimationSettingsContent(
    settings: SideDockPreviewSettings,
    enabled: Boolean,
    overlayAllowed: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onPresetChanged: (String) -> Unit,
    onCustomChanged: (Float, Float) -> Unit,
    onOpenDock: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controlsAvailable = enabled && overlayAllowed
    val presetTitle = stringResource(R.string.settings_dock_motion_preset)
    val choices = listOf(
        SettingsChoice("Smooth", stringResource(R.string.settings_dock_preset_smooth), stringResource(R.string.settings_dock_preset_smooth_description)),
        SettingsChoice("Fluid", stringResource(R.string.settings_dock_preset_fluid), stringResource(R.string.settings_dock_preset_fluid_description)),
        SettingsChoice("Bouncy", stringResource(R.string.settings_dock_preset_bouncy), stringResource(R.string.settings_dock_preset_bouncy_description)),
        SettingsChoice("Snappy", stringResource(R.string.settings_dock_preset_snappy), stringResource(R.string.settings_dock_preset_snappy_description)),
        SettingsChoice("Instant", stringResource(R.string.settings_dock_preset_instant), stringResource(R.string.settings_dock_preset_instant_description))
    )
    val instant = settings.preset == "Instant"
    val strengthHint = stringResource(when {
        instant -> R.string.settings_dock_spring_unused
        settings.stiffness < 250f -> R.string.settings_dock_strength_soft
        settings.stiffness < 500f -> R.string.settings_dock_strength_balanced
        else -> R.string.settings_dock_strength_firm
    })
    val dampingHint = stringResource(when {
        instant -> R.string.settings_dock_spring_unused
        settings.damping < 0.65f -> R.string.settings_dock_damping_bouncy
        settings.damping < 0.80f -> R.string.settings_dock_damping_gentle
        settings.damping < 1f -> R.string.settings_dock_damping_restrained
        else -> R.string.settings_dock_damping_no_overshoot
    })

    SideDockSettingsLayout(
        title = stringResource(R.string.settings_dock_animation),
        subtitle = stringResource(R.string.settings_dock_animation_subtitle),
        listTag = "side_dock_animation_list", onNavigateBack = onNavigateBack, modifier = modifier,
        twoColumns = false,
        leadingContent = {
            if (controlsAvailable) SideDockAnimationPreview(settings, true, onOpenDock)
            else SideDockAccessRows(enabled, overlayAllowed, onEnabledChanged, onRequestPermission)
        }
    ) {
        if (!controlsAvailable) return@SideDockSettingsLayout
        item(key = "motion_preset") {
            Column {
                SettingsGroupLabel(presetTitle)
                BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
                    val minPillWidth = 112.dp * LocalDensity.current.fontScale
                    val columns = ((maxWidth + 8.dp) / (minPillWidth + 8.dp)).toInt().coerceIn(1, 3)
                    val pillWidth = (maxWidth - 8.dp * (columns - 1)) / columns
                    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { choice ->
                            FilterChip(selected = settings.preset == choice.key, onClick = { onPresetChanged(choice.key) },
                                label = { Text(choice.label) }, shape = CircleShape, border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.width(pillWidth).heightIn(min = 48.dp)
                                    .testTag("side_dock_preset_${choice.key.lowercase()}").semantics { role = Role.RadioButton },
                                leadingIcon = if (settings.preset == choice.key) {
                                    { Icon(painterResource(R.drawable.ic_custom_check), null, modifier = Modifier.size(18.dp)) }
                                } else null)
                        }
                    }
                }
            }
        }
        item(key = "spring_controls") {
            Column {
                SettingsGroupLabel(stringResource(R.string.settings_dock_spring_controls))
                ExpressiveSettingsGroup {
                    SideDockSpringSlider(
                        title = stringResource(R.string.settings_dock_strength),
                        subtitle = strengthHint,
                        value = settings.stiffness.coerceIn(120f, 900f),
                        valueDisplay = if (instant) stringResource(R.string.settings_off) else settings.stiffness.roundToInt().toString(),
                        onValueChange = { onCustomChanged(it.roundToInt().toFloat(), settings.damping) },
                        valueRange = 120f..900f, enabled = !instant, index = 0
                    )
                    SideDockSpringSlider(
                        title = stringResource(R.string.settings_dock_damping),
                        subtitle = dampingHint,
                        value = settings.damping.coerceIn(0.45f, 1f),
                        valueDisplay = if (instant) stringResource(R.string.settings_off) else dockDampingDisplay(settings.damping),
                        onValueChange = { onCustomChanged(settings.stiffness, (it * 100).roundToInt() / 100f) },
                        valueRange = 0.45f..1f, enabled = !instant, index = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SideDockSpringSlider(
    title: String, subtitle: String, value: Float, valueDisplay: String,
    onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, enabled: Boolean, index: Int
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = connectedPillShape(index, 2)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f))
                }
                Text(valueDisplay, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            SideDockSlider(title, value, valueDisplay, valueRange, onValueChange, enabled = enabled)
        }
    }
}

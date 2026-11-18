package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.CobaltSideDockService
import com.example.R
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import kotlin.math.roundToInt

@Composable
fun EdgePanelSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAnimation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = viewModel.settingsRepository
    val settings = rememberSideDockPreviewSettings(repository)
    val enabled by repository.isFloatingOcrEnabled.collectAsState()
    val context = LocalContext.current
    val overlayAllowed = rememberSideDockOverlayPermission()
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

    val requestPermission: () -> Unit = {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        } catch (_: Exception) { error = permissionError }
    }
    Box(modifier.fillMaxSize()) {
        SideDockSettingsContent(
            settings, enabled, overlayAllowed,
            onEnabledChanged = repository::setFloatingOcrEnabled,
            onRequestPermission = requestPermission,
            onSideChanged = repository::setEdgePanelSide,
            onPositionChanged = repository::setEdgePanelYPercent,
            onHeightChanged = repository::setEdgePanelHeight,
            onThicknessChanged = repository::setEdgePanelThickness,
            onOpacityChanged = repository::setEdgePanelOpacity,
            onNavigateToAnimation = onNavigateToAnimation,
            onNavigateBack = onNavigateBack
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
internal fun SideDockSettingsContent(
    settings: SideDockPreviewSettings,
    enabled: Boolean,
    overlayAllowed: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onSideChanged: (String) -> Unit,
    onPositionChanged: (Float) -> Unit,
    onHeightChanged: (Int) -> Unit,
    onThicknessChanged: (Int) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onNavigateToAnimation: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preset = sideDockPresetLabel(settings.preset)
    val controlsAvailable = enabled && overlayAllowed
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    SideDockSettingsLayout(
        title = stringResource(R.string.settings_side_dock),
        subtitle = stringResource(R.string.settings_dock_subtitle),
        listTag = "side_dock_settings_list", onNavigateBack = onNavigateBack, modifier = modifier,
        twoColumns = controlsAvailable,
        leadingKey = "dock_access",
        leadingContent = {
            Column {
                if (controlsAvailable) SettingsGroupLabel(stringResource(R.string.settings_dock_access))
                SideDockAccessRows(enabled, overlayAllowed, onEnabledChanged, onRequestPermission,
                    onNavigateToAnimation = onNavigateToAnimation, preset = preset)
            }
        }
    ) {
        if (!controlsAvailable) return@SideDockSettingsLayout
        item(key = "dock_handle") {
            Column {
                SettingsGroupLabel(stringResource(R.string.settings_dock_handle))
                ExpressiveSettingsGroup {
                    DockScreenEdgeSelector(settings.side, onSideChanged)
                    DockHandleSlider("position", stringResource(R.string.settings_dock_position), settings.position,
                        stringResource(R.string.settings_dock_percent, (settings.position * 100).roundToInt()),
                        0f..1f, R.drawable.ic_settings_scroll, 1,
                        stringResource(R.string.settings_dock_top), stringResource(R.string.settings_dock_bottom), onPositionChanged)
                    DockHandleSlider("height", stringResource(R.string.settings_dock_height), settings.heightDp.toFloat(),
                        stringResource(R.string.settings_dock_dp, settings.heightDp), 50f..200f, R.drawable.ic_settings_dock_height, 2,
                        stringResource(R.string.settings_dock_short), stringResource(R.string.settings_dock_tall),
                        { onHeightChanged(it.roundToInt()) },
                        subtitle = if (landscape) stringResource(R.string.settings_dock_height_landscape) else null)
                    DockHandleSlider("width", stringResource(R.string.settings_dock_width), settings.thicknessDp.toFloat(),
                        stringResource(R.string.settings_dock_dp, settings.thicknessDp), 5f..30f, R.drawable.ic_settings_dock_width, 3,
                        stringResource(R.string.settings_dock_thin), stringResource(R.string.settings_dock_wide), { onThicknessChanged(it.roundToInt()) })
                    DockHandleSlider("opacity", stringResource(R.string.settings_dock_opacity), settings.opacity,
                        stringResource(R.string.settings_dock_percent, (settings.opacity * 100).roundToInt()),
                        0.1f..1f, R.drawable.ic_custom_eye, 4,
                        stringResource(R.string.settings_dock_faint), stringResource(R.string.settings_dock_solid), onOpacityChanged)
                }
            }
        }
    }
}

@Composable
private fun DockScreenEdgeSelector(side: String, onSideChanged: (String) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    Surface(
        shape = connectedPillShape(0, 5),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("side_dock_setting_side")
    ) {
        BoxWithConstraints(Modifier.padding(16.dp)) {
            if (maxWidth >= 360.dp && fontScale <= 1.15f) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_dock_edge), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    DockScreenEdgeChoices(side, onSideChanged, Modifier.width(224.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_dock_edge), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                    DockScreenEdgeChoices(side, onSideChanged, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DockScreenEdgeChoices(side: String, onSideChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.heightIn(min = 48.dp)) {
        listOf("Left", "Right").forEachIndexed { index, edge ->
            val label = stringResource(if (edge == "Left") R.string.settings_dock_left else R.string.settings_dock_right)
            val accessibleLabel = stringResource(if (edge == "Left") R.string.settings_dock_left_edge else R.string.settings_dock_right_edge)
            SegmentedButton(
                selected = side == edge,
                onClick = { onSideChanged(edge) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                colors = SegmentedButtonDefaults.colors(inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.heightIn(min = 48.dp).testTag("side_dock_edge_${edge.lowercase()}")
                    .semantics { contentDescription = accessibleLabel },
                icon = {
                    Icon(painterResource(if (edge == "Left") R.drawable.ic_settings_dock_left else R.drawable.ic_settings_dock_right),
                        contentDescription = null, modifier = Modifier.size(20.dp))
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun DockHandleSlider(
    key: String, title: String, value: Float, valueDisplay: String, range: ClosedFloatingPointRange<Float>,
    icon: Int, index: Int, startLabel: String, endLabel: String, onValueChanged: (Float) -> Unit,
    subtitle: String? = null
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = connectedPillShape(index, 5),
        modifier = Modifier.fillMaxWidth().testTag("side_dock_setting_$key")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(valueDisplay, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SideDockSlider(title, value, valueDisplay, range, onValueChanged,
                modifier = Modifier.testTag("side_dock_slider_$key"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(startLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(endLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

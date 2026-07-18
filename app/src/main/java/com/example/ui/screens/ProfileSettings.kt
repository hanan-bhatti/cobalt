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

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.viewmodel.SecondBrainViewModel
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.style.TextOverflow
import com.example.utils.DevicePerformance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SecondBrainViewModel,
    onNavigateBack: () -> Unit
) {
    var showEdgePanelSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = showEdgePanelSettings) {
        showEdgePanelSettings = false
    }

    if (showEdgePanelSettings) {
        EdgePanelSettingsScreen(viewModel = viewModel, onNavigateBack = { showEdgePanelSettings = false })
        return
    }

    val context = LocalContext.current
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400), initialOffsetY = { it / 4 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 100.dp)
            ) {

                SettingsSection(title = "App Theme") {
                    val themeMode by viewModel.settingsRepository.themeMode.collectAsState()
                    val themeOptions = listOf("System Default", "Light", "Dark")
                    var expanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Theme", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(themeMode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            themeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        viewModel.settingsRepository.setThemeMode(option)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                SettingsSection(title = "Edge Panel", subtext = "Enable a floating panel to quickly capture thoughts from anywhere.") {
                    if (!hasOverlayPermission) {
                        SettingsRow(
                            title = "Grant Overlay Permission",
                            value = "Required",
                            onClick = {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                        )
                    } else {
                        val isEnabled by viewModel.settingsRepository.isFloatingOcrEnabled.collectAsState()
                        SettingsToggleRow(
                            title = "Enable Edge Panel",
                            checked = isEnabled,
                            onCheckedChange = { enable ->
                                viewModel.settingsRepository.setFloatingOcrEnabled(enable)
                                val serviceIntent = Intent(context, com.example.BrainOcrOverlayService::class.java)
                                if (enable) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        context.startService(serviceIntent)
                                    }
                                } else {
                                    context.stopService(serviceIntent)
                                }
                            }
                        )

                        if (isEnabled) {
                            SettingsRow(title = "Edge Panel Settings", onClick = { showEdgePanelSettings = true })
                        }
                    }
                }

                SettingsSection(title = "Gemini API", subtext = "Configure your API key for advanced AI features.") {
                    var apiKey by remember { mutableStateOf("") }
                    val currentKey by viewModel.settingsRepository.geminiApiKey.collectAsState()
                    LaunchedEffect(currentKey) { apiKey = currentKey }

                    var keyVisibility by remember { mutableStateOf(false) }
                    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            viewModel.settingsRepository.setGeminiApiKey(it)
                        },
                        placeholder = { Text("Enter your Gemini API Key") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_lock),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            val iconResId = if (keyVisibility) R.drawable.ic_custom_eye else R.drawable.ic_custom_eye_off
                            val description = if (keyVisibility) "Hide API Key" else "Show API Key"
                            IconButton(onClick = { keyVisibility = !keyVisibility }) {
                                Icon(
                                    painter = painterResource(id = iconResId),
                                    contentDescription = description,
                                    modifier = Modifier.size(20.dp)
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
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                viewModel.settingsRepository.setGeminiApiKey(apiKey)
                                focusManager.clearFocus()
                            }
                        )
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_link_external),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Get your Gemini API Key here",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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

                SettingsSection(title = "OCR Model", subtext = "Select ML model used for image text extraction.") {
                    val models by viewModel.availableModels.collectAsState()
                    val selectedModel by viewModel.settingsRepository.selectedModel.collectAsState()
                    var modelExpanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        SettingsRow(
                            title = "Selected Model",
                            value = selectedModel,
                            onClick = { modelExpanded = true }
                        )
                        Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopEnd).padding(end = 16.dp)) {
                            DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                                if (models.isNotEmpty()) {
                                    models.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                viewModel.settingsRepository.setSelectedModel(model)
                                                modelExpanded = false
                                            }
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("No models loaded.") },
                                        onClick = { modelExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.fetchAvailableModels(isUserTriggered = true) }
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_custom_sync), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh Models")
                        }
                    }
                }

                SettingsSection(title = "OCR Sensitivity", subtext = "Adjust OCR precision. 'High' extracts more detail but might include background noise.") {
                    val sensitivity by viewModel.settingsRepository.ocrSensitivity.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val levels = listOf("Low", "Medium", "High")
                        levels.forEach { level ->
                            val isSelected = (sensitivity == level)
                            Button(
                                onClick = { viewModel.settingsRepository.setOcrSensitivity(level) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = level, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                if (DevicePerformance.shouldUseBlur(context)) {
                    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
                    val blurRadius by viewModel.blurRadius.collectAsState()
                    val blurOpacity by viewModel.blurOpacity.collectAsState()

                    SettingsSection(
                        title = "Blur Effects",
                        subtext = "Frosted glass blur applied to the bottom bar, FAB, and detail toolbar. Requires a capable device."
                    ) {
                        SettingsToggleRow(
                            title = "Enable Blur",
                            subtitle = "Toggle frosted glass effect app-wide",
                            checked = !forceDisableBlur,
                            onCheckedChange = { enabled -> viewModel.setForceDisableBlur(!enabled) }
                        )

                        if (!forceDisableBlur) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SliderRow(
                                title = "Blur Radius",
                                value = blurRadius.toFloat(),
                                valueStr = "${blurRadius}dp",
                                onValueChange = { viewModel.setBlurRadius(it.toInt()) },
                                valueRange = 5f..50f
                            )
                            SliderRow(
                                title = "Blur Opacity",
                                value = blurOpacity,
                                valueStr = "${(blurOpacity * 100).toInt()}%",
                                onValueChange = { viewModel.setBlurOpacity(it) },
                                valueRange = 0.05f..0.80f
                            )
                        }
                    }
                }

                if (!isIgnoringBattery) {
                    val isColorOS = remember { com.example.utils.BatteryOptimizationHelper.isColorOSDevice() }
                    val titleText = if (isColorOS) "Fix widget not loading" else "Background activity"
                    val subtextText = if (isColorOS) {
                        "OPPO's battery manager can stop the home screen widget from updating. Please allow background activity to keep it updated."
                    } else {
                        "Allow background activity to ensure home screen widgets update reliably."
                    }

                    SettingsSection(
                        title = titleText,
                        subtext = subtextText
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = {
                                    com.example.utils.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Allow Background Activity")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, subtext: String? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
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
        if (subtext != null) {
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        }
    }
}

@Composable
fun SettingsRow(title: String, value: String? = null, showChevron: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (value != null || (onClick != null && showChevron)) {
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = if (value != null) Modifier.weight(1f) else Modifier.wrapContentWidth()
            ) {
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (onClick != null && showChevron) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            } else {
                null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgePanelSettingsScreen(viewModel: SecondBrainViewModel, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edge Panel", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
        ) {
            val side by viewModel.settingsRepository.edgePanelSide.collectAsState()
            val positionY by viewModel.settingsRepository.edgePanelYPercent.collectAsState()
            val opacity by viewModel.settingsRepository.edgePanelOpacity.collectAsState()
            val height by viewModel.settingsRepository.edgePanelHeight.collectAsState()
            val thickness by viewModel.settingsRepository.edgePanelThickness.collectAsState()

            SettingsSection(title = "Anchor Side", subtext = "Choose which side of the screen the handle sits on.") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val sides = listOf("Left", "Right")
                    sides.forEach { s ->
                        val isSelected = (side == s)
                        Button(
                            onClick = { viewModel.settingsRepository.setEdgePanelSide(s) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "$s Side", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            SettingsSection(title = "Appearance", subtext = "Customize the look and size of the screen-edge handle.") {
                SliderRow(
                    title = "Vertical Position",
                    value = positionY,
                    valueStr = "${(positionY * 100).toInt()}%",
                    onValueChange = { viewModel.settingsRepository.setEdgePanelYPercent(it) },
                    valueRange = 0f..1f
                )
                SliderRow(
                    title = "Opacity",
                    value = opacity,
                    valueStr = "${(opacity * 100).toInt()}%",
                    onValueChange = { viewModel.settingsRepository.setEdgePanelOpacity(it) },
                    valueRange = 0.1f..1f
                )
                SliderRow(
                    title = "Handle Height",
                    value = height.toFloat(),
                    valueStr = "${height}dp",
                    onValueChange = { viewModel.settingsRepository.setEdgePanelHeight(it.toInt()) },
                    valueRange = 50f..200f
                )
                SliderRow(
                    title = "Handle Width / Thickness",
                    value = thickness.toFloat(),
                    valueStr = "${thickness}dp",
                    onValueChange = { viewModel.settingsRepository.setEdgePanelThickness(it.toInt()) },
                    valueRange = 5f..30f
                )
            }
        }
    }
}

@Composable
fun SliderRow(title: String, value: Float, valueStr: String, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = valueStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

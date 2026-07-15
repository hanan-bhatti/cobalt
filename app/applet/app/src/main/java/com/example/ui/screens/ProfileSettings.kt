package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.SecondBrainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SecondBrainViewModel,
    onNavigateBack: () -> Unit
) {
    var showEdgePanelSettings by remember { mutableStateOf(false) }

    if (showEdgePanelSettings) {
        EdgePanelSettingsScreen(onNavigateBack = { showEdgePanelSettings = false })
        return
    }

    val geminiApiKey by viewModel.settingsRepository.geminiApiKey.collectAsState()
    val displayKey = if (geminiApiKey.isBlank()) "Not set" else "••••${geminiApiKey.takeLast(4)}"
    var enableEdgePanel by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 28.sp) },
                navigationIcon = {
                    Row(
                        modifier = Modifier
                            .clickable { onNavigateBack() }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Profile", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // GEMINI AI
            SettingsSection(title = "GEMINI AI", subtext = "Your API key is stored securely on this device only.") {
                SettingsRow(title = "API Key", value = displayKey)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(title = "OCR Model", value = "flash-lite")
            }

            // EXTRACTION
            SettingsSection(title = "EXTRACTION") {
                SettingsRow(title = "Extraction Sensitivity", value = "Medium")
            }

            // EDGE ASSISTANT
            SettingsSection(title = "EDGE ASSISTANT") {
                SettingsToggleRow(
                    title = "Enable Edge Panel", 
                    subtitle = "Trigger OCR from any app with a draggable screen-edge handle",
                    checked = enableEdgePanel,
                    onCheckedChange = { enableEdgePanel = it }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(title = "Edge Panel Settings", onClick = { showEdgePanelSettings = true })
            }

            // APPEARANCE
            SettingsSection(title = "APPEARANCE") {
                SettingsRow(title = "App Theme", value = "System")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                SettingsToggleRow(title = "Notifications", checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
            }

            // ABOUT
            SettingsSection(title = "ABOUT") {
                SettingsRow(title = "About Second Brain")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(title = "Privacy Policy")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(title = "Terms & Conditions")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgePanelSettingsScreen(onNavigateBack: () -> Unit) {
    var anchorLeft by remember { mutableStateOf(false) }
    var verticalPos by remember { mutableFloatStateOf(0.4f) }
    var opacity by remember { mutableFloatStateOf(0.2f) }
    var height by remember { mutableFloatStateOf(93f) }
    var width by remember { mutableFloatStateOf(11f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edge Panel", fontWeight = FontWeight.Bold, fontSize = 28.sp) },
                navigationIcon = {
                    Row(
                        modifier = Modifier
                            .clickable { onNavigateBack() }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Settings", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "A draggable handle sits on the edge of your screen so you can trigger OCR instantly from any other app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ANCHOR EDGE
            Column {
                Text(
                    text = "ANCHOR EDGE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { anchorLeft = true }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (anchorLeft) {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {}
                            }
                            Text("Left Side", color = if (anchorLeft) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if(anchorLeft) FontWeight.SemiBold else FontWeight.Normal)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { anchorLeft = false }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!anchorLeft) {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize(), shadowElevation = 1.dp) {}
                            }
                            Text("Right Side", color = if (!anchorLeft) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if(!anchorLeft) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SliderRow(title = "Vertical Position", value = verticalPos, valueStr = "${(verticalPos*100).toInt()}%", onValueChange = { verticalPos = it }, valueRange = 0f..1f)
                SliderRow(title = "Handle Opacity", value = opacity, valueStr = "${(opacity*100).toInt()}%", onValueChange = { opacity = it }, valueRange = 0.1f..1f)
                SliderRow(title = "Handle Height", value = height, valueStr = "${height.toInt()}dp", onValueChange = { height = it }, valueRange = 50f..200f)
                SliderRow(title = "Handle Width", value = width, valueStr = "${width.toInt()}dp", onValueChange = { width = it }, valueRange = 5f..30f)
            }
            
            Text(
                "You can also drag the handle on screen to reposition it vertically on the fly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SliderRow(title: String, value: Float, valueStr: String, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(valueStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun SettingsSection(title: String, subtext: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
fun SettingsRow(title: String, value: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

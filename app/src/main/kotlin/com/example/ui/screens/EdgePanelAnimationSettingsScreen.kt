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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CobaltSideDockService
import com.example.R
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgePanelAnimationSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val currentPreset by viewModel.settingsRepository.edgePanelAnimPreset.collectAsState()
    val stiffness by viewModel.settingsRepository.edgePanelAnimStiffness.collectAsState()
    val damping by viewModel.settingsRepository.edgePanelAnimDamping.collectAsState()

    var activeTooltipId by remember { mutableStateOf<String?>(null) }

    val presets = remember {
        listOf(
            PhysicsPresetInfo(
                id = "Smooth",
                title = "Balanced Smooth",
                subtitle = "Natural Material 3 spring curve with gentle cushion",
                stiffness = 340f,
                damping = 0.72f,
                badgeText = "340 Tension · 0.72 Damping",
                tooltip = "The default recommended profile. Strikes an optimal balance between responsiveness and smooth visual clarity with a cushioned finish."
            ),
            PhysicsPresetInfo(
                id = "Fluid",
                title = "Fluid Elastic",
                subtitle = "Relaxed spring with organic liquid stretch",
                stiffness = 320f,
                damping = 0.68f,
                badgeText = "320 Tension · 0.68 Damping",
                tooltip = "Delivers a soft, liquid elasticity that unfolds organically from the screen edge using calibrated spring physics."
            ),
            PhysicsPresetInfo(
                id = "Bouncy",
                title = "Bouncy Spring",
                subtitle = "Playful overshoot rebound with pronounced elastic oscillation",
                stiffness = 320f,
                damping = 0.58f,
                badgeText = "320 Tension · 0.58 Damping",
                tooltip = "Adds a noticeable physical bounce when the card blooms open, creating an energetic and playful tactile sensation."
            ),
            PhysicsPresetInfo(
                id = "Snappy",
                title = "Snappy Rapid",
                subtitle = "High-tension spring with swift snap and immediate settling",
                stiffness = 650f,
                damping = 0.85f,
                badgeText = "650 Tension · 0.85 Damping",
                tooltip = "Optimized for speed and minimal visual latency. The dock snaps into place almost instantly with zero wasted travel time."
            ),
            PhysicsPresetInfo(
                id = "Instant",
                title = "Instant Snap",
                subtitle = "Zero-latency direct display without motion (low power / instant)",
                stiffness = 1000f,
                damping = 1.0f,
                badgeText = "0ms · No Motion",
                tooltip = "Bypasses animation physics entirely. The dock appears instantly upon tapping, ideal for low-power operation or maximum speed."
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Side Dock Animation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ════════════ OVERVIEW HEADER CARD ════════════
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DockPhysicsAnimatedIcon(
                            presetId = currentPreset,
                            stiffness = stiffness,
                            damping = damping,
                            isSelected = true,
                            modifier = Modifier.size(52.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fluid Morphing Dynamics",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "The side dock smoothly morphs from the screen edge handle using genuine spring-damper physics with zero layout overhead.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Active Profile Badge & Test Trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Active: ${presets.find { it.id.equals(currentPreset, true) }?.title ?: currentPreset}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val intent = Intent(context, CobaltSideDockService::class.java).apply {
                                    action = "com.example.ACTION_TOGGLE_PANEL"
                                }
                                try {
                                    context.startService(intent)
                                } catch (_: Exception) {}
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_tools),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Test Dock Now",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ════════════ SECTION 1: PRESET PROFILES ════════════
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "PHYSICS PROFILES",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select a tuned mass-spring profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (currentPreset.equals("Custom", ignoreCase = true)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Text(
                                text = "Custom Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEach { preset ->
                        val isSelected = currentPreset.equals(preset.id, ignoreCase = true)
                        val isTooltipOpen = activeTooltipId == preset.id

                        Surface(
                            onClick = {
                                activeTooltipId = null
                                viewModel.settingsRepository.applyEdgePanelAnimPreset(preset.id)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Animated dock icon showcasing exact spring motion
                                    DockPhysicsAnimatedIcon(
                                        presetId = preset.id,
                                        stiffness = preset.stiffness,
                                        damping = preset.damping,
                                        isSelected = isSelected,
                                        modifier = Modifier.size(48.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = preset.subtitle,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    // Info Tooltip Trigger Icon
                                    IconButton(
                                        onClick = {
                                            activeTooltipId = if (isTooltipOpen) null else preset.id
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_info),
                                            contentDescription = "Details",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isTooltipOpen) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Specs Badge & Expandable Tooltip Info
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = preset.badgeText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isTooltipOpen,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                    ) {
                                        Text(
                                            text = preset.tooltip,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 17.sp,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ════════════ SECTION 2: CUSTOM PHYSICS ENGINE ════════════
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        text = "CUSTOM PHYSICS ENGINE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fine-tune mass, stiffness tension, and damping dissipation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Live Interactive Custom Simulator Icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            DockPhysicsAnimatedIcon(
                                presetId = "Custom",
                                stiffness = stiffness,
                                damping = damping,
                                isSelected = currentPreset.equals("Custom", ignoreCase = true),
                                modifier = Modifier.size(50.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Live Physics Simulator",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Real-time preview of your custom tension and rebound settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // 1. SPRING STIFFNESS (TENSION)
                        var showStiffnessTooltip by remember { mutableStateOf(false) }
                        val stiffnessDesc = when {
                            stiffness < 240f -> "Gentle · Leisurely"
                            stiffness < 400f -> "Natural · Balanced"
                            stiffness < 600f -> "Snappy · Responsive"
                            else -> "Hyper · Rapid Snap"
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Spring Tension (Speed)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_info),
                                        contentDescription = "Info",
                                        tint = if (showStiffnessTooltip) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable { showStiffnessTooltip = !showStiffnessTooltip }
                                    )
                                }
                                Text(
                                    text = "${stiffness.toInt()} · $stiffnessDesc",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = stiffness,
                                onValueChange = { newValue ->
                                    viewModel.settingsRepository.setEdgePanelAnimPreset("Custom")
                                    viewModel.settingsRepository.setEdgePanelAnimStiffness(newValue)
                                },
                                valueRange = 150f..800f,
                                steps = 12
                            )

                            AnimatedVisibility(visible = showStiffnessTooltip) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Governs how forcefully the spring expands the card from the handle. Higher values make the dock pop out faster; lower values give a relaxed, cinematic glide.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // 2. DAMPING RATIO (ELASTIC REBOUND)
                        var showDampingTooltip by remember { mutableStateOf(false) }
                        val dampingDesc = when {
                            damping < 0.62f -> "Elastic Rebound"
                            damping < 0.78f -> "Fluid Cushion"
                            damping < 0.90f -> "Low Oscillation"
                            else -> "Zero Rebound (Critically Damped)"
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Elastic Rebound (Bounciness)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_info),
                                        contentDescription = "Info",
                                        tint = if (showDampingTooltip) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable { showDampingTooltip = !showDampingTooltip }
                                    )
                                }
                                Text(
                                    text = "${(damping * 100).toInt()}% · $dampingDesc",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = damping,
                                onValueChange = { newValue ->
                                    viewModel.settingsRepository.setEdgePanelAnimPreset("Custom")
                                    viewModel.settingsRepository.setEdgePanelAnimDamping(newValue)
                                },
                                valueRange = 0.50f..1.0f,
                                steps = 9
                            )

                            AnimatedVisibility(visible = showDampingTooltip) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Controls energy absorption upon expansion. Lower percentages let the card overshoot and bounce elastically; higher percentages absorb the motion for a gentle, bounce-free landing.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimal, clean, animated vector preview icon for each SideDock physics profile.
 * Renders an interactive phone frame where a miniature edge handle morphs into an expanded
 * dock card following the profile's exact mass-spring physics curve.
 */
@Composable
fun DockPhysicsAnimatedIcon(
    presetId: String,
    stiffness: Float = 340f,
    damping: Float = 0.72f,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(0f) }

    val (enterSpec, exitSpec) = remember(presetId, stiffness, damping) {
        when (presetId) {
            "Bouncy" -> Pair(
                spring<Float>(dampingRatio = 0.58f, stiffness = 320f),
                spring<Float>(dampingRatio = 0.78f, stiffness = 420f)
            )
            "Snappy" -> Pair(
                spring<Float>(dampingRatio = 0.85f, stiffness = 650f),
                spring<Float>(dampingRatio = 0.95f, stiffness = 750f)
            )
            "Fluid" -> Pair(
                spring<Float>(dampingRatio = 0.68f, stiffness = 320f),
                spring<Float>(dampingRatio = 0.82f, stiffness = 460f)
            )
            "Instant" -> Pair(
                snap<Float>(),
                snap<Float>()
            )
            "Custom" -> Pair(
                spring<Float>(
                    dampingRatio = damping.coerceIn(0.45f, 1.0f),
                    stiffness = stiffness.coerceIn(120f, 900f)
                ),
                spring<Float>(
                    dampingRatio = (damping + 0.10f).coerceIn(0.55f, 1.0f),
                    stiffness = (stiffness + 60f).coerceIn(180f, 1000f)
                )
            )
            else -> Pair( // "Smooth"
                spring<Float>(dampingRatio = 0.72f, stiffness = 340f),
                spring<Float>(dampingRatio = 0.82f, stiffness = 380f)
            )
        }
    }

    LaunchedEffect(presetId, stiffness, damping) {
        while (true) {
            delay(500)
            animProgress.animateTo(1f, animationSpec = enterSpec)
            delay(1300)
            animProgress.animateTo(0f, animationSpec = exitSpec)
            delay(800)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentBlue = Color(0xFF3B82F6)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) primaryColor.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = animProgress.value
            val w = size.width
            val h = size.height

            // 1. Device Mockup Frame
            val strokeWidth = 1.2.dp.toPx()
            val cornerRadius = 6.dp.toPx()
            val frameRect = Rect(
                left = strokeWidth / 2f,
                top = strokeWidth / 2f,
                right = w - strokeWidth / 2f,
                bottom = h - strokeWidth / 2f
            )
            drawRoundRect(
                color = if (isSelected) primaryColor.copy(alpha = 0.45f) else outlineColor.copy(alpha = 0.5f),
                topLeft = Offset(frameRect.left, frameRect.top),
                size = Size(frameRect.width, frameRect.height),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = strokeWidth)
            )

            // Phone speaker slit
            val slitWidth = 6.dp.toPx()
            drawLine(
                color = if (isSelected) primaryColor.copy(alpha = 0.5f) else outlineColor.copy(alpha = 0.6f),
                start = Offset(w / 2f - slitWidth / 2f, frameRect.top + 2.dp.toPx()),
                end = Offset(w / 2f + slitWidth / 2f, frameRect.top + 2.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 2. Morphing Handle & Dock Card
            val rightBezel = frameRect.right
            val centerY = h / 2f
            val idleWidth = 2.5.dp.toPx()
            val idleHeight = 13.dp.toPx()
            val targetDockWidth = 18.dp.toPx()
            val targetDockHeight = 23.dp.toPx()

            val pClamped = p.coerceAtLeast(0f)
            val currentWidth = (idleWidth + (targetDockWidth - idleWidth) * p).coerceAtLeast(idleWidth)
            val currentHeight = (idleHeight + (targetDockHeight - idleHeight) * pClamped).coerceAtLeast(idleHeight)

            val margin = 2.dp.toPx() * p.coerceIn(0f, 1f)
            val cardRight = rightBezel - margin
            val cardLeft = cardRight - currentWidth
            val cardTop = centerY - currentHeight / 2f

            val innerRadius = 1.5.dp.toPx() + (4.dp.toPx() - 1.5.dp.toPx()) * p.coerceIn(0f, 1f)
            val outerRadius = 4.dp.toPx() * p.coerceIn(0f, 1f)

            val dockPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = cardLeft,
                        top = cardTop,
                        right = cardRight,
                        bottom = cardTop + currentHeight,
                        topLeftCornerRadius = CornerRadius(innerRadius, innerRadius),
                        bottomLeftCornerRadius = CornerRadius(innerRadius, innerRadius),
                        topRightCornerRadius = CornerRadius(outerRadius, outerRadius),
                        bottomRightCornerRadius = CornerRadius(outerRadius, outerRadius)
                    )
                )
            }

            // Fill color transition from Blue handle to Solid Card
            val fillColor = lerp(
                start = accentBlue,
                stop = if (isSelected) primaryColor.copy(alpha = 0.25f) else surfaceColor,
                fraction = (p / 0.35f).coerceIn(0f, 1f)
            )
            drawPath(dockPath, color = fillColor)

            // Card outline stroke
            if (p > 0.08f) {
                drawPath(
                    dockPath,
                    color = if (isSelected) primaryColor.copy(alpha = 0.75f) else outlineColor.copy(alpha = 0.7f),
                    style = Stroke(width = 0.9.dp.toPx())
                )
            }

            // 3. Miniature Skeleton Elements Inside Dock Card
            if (p > 0.45f) {
                val contentAlpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)

                // Mini header pill
                val headerW = 6.dp.toPx()
                val headerH = 2.dp.toPx()
                drawRoundRect(
                    color = accentBlue.copy(alpha = contentAlpha * 0.85f),
                    topLeft = Offset(cardLeft + 2.5.dp.toPx(), cardTop + 2.5.dp.toPx()),
                    size = Size(headerW, headerH),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )

                // Mini lines
                val lineStart = cardLeft + 2.5.dp.toPx()
                val lineEnd1 = (cardRight - 2.5.dp.toPx()).coerceAtLeast(lineStart + 2f)
                val lineEnd2 = (cardRight - 5.dp.toPx()).coerceAtLeast(lineStart + 2f)

                drawLine(
                    color = onSurfaceColor.copy(alpha = contentAlpha * 0.45f),
                    start = Offset(lineStart, cardTop + 7.5.dp.toPx()),
                    end = Offset(lineEnd1, cardTop + 7.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = onSurfaceColor.copy(alpha = contentAlpha * 0.35f),
                    start = Offset(lineStart, cardTop + 11.dp.toPx()),
                    end = Offset(lineEnd2, cardTop + 11.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Bottom 3 action dots
                val dotY = cardTop + currentHeight - 3.dp.toPx()
                val dotRadius = 1.dp.toPx()
                drawCircle(
                    color = primaryColor.copy(alpha = contentAlpha * 0.8f),
                    radius = dotRadius,
                    center = Offset(cardLeft + 3.5.dp.toPx(), dotY)
                )
                drawCircle(
                    color = onSurfaceColor.copy(alpha = contentAlpha * 0.45f),
                    radius = dotRadius,
                    center = Offset(cardLeft + 7.5.dp.toPx(), dotY)
                )
                drawCircle(
                    color = onSurfaceColor.copy(alpha = contentAlpha * 0.45f),
                    radius = dotRadius,
                    center = Offset(cardLeft + 11.5.dp.toPx(), dotY)
                )
            }
        }
    }
}

private data class PhysicsPresetInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val stiffness: Float,
    val damping: Float,
    val badgeText: String,
    val tooltip: String
)

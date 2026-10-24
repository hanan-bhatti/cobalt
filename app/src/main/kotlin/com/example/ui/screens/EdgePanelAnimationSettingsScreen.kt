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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.CobaltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgePanelAnimationSettingsScreen(
    viewModel: CobaltViewModel,
    onNavigateBack: () -> Unit
) {
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
                tooltip = "The default recommended profile. Strikes an optimal balance between responsiveness and smooth visual clarity with a cushioned finish.",
                iconRes = R.drawable.ic_custom_star
            ),
            PhysicsPresetInfo(
                id = "Fluid",
                title = "Aquamorphic Fluid",
                subtitle = "Relaxed water-drop spring with organic elastic stretch",
                stiffness = 320f,
                damping = 0.68f,
                badgeText = "320 Tension · 0.68 Damping",
                tooltip = "Inspired by Oppo ColorOS Aquamorphic design. Delivers a soft, liquid elasticity that unfolds organically from the screen edge.",
                iconRes = R.drawable.ic_custom_sync
            ),
            PhysicsPresetInfo(
                id = "Bouncy",
                title = "Bouncy Spring",
                subtitle = "Playful overshoot rebound with pronounced elastic oscillation",
                stiffness = 320f,
                damping = 0.58f,
                badgeText = "320 Tension · 0.58 Damping",
                tooltip = "Adds a noticeable physical bounce when the card blooms open, creating an energetic and playful tactile sensation.",
                iconRes = R.drawable.ic_custom_heart
            ),
            PhysicsPresetInfo(
                id = "Snappy",
                title = "Snappy Rapid",
                subtitle = "High-tension spring with swift snap and immediate settling",
                stiffness = 650f,
                damping = 0.85f,
                badgeText = "650 Tension · 0.85 Damping",
                tooltip = "Optimized for speed and minimal visual latency. The dock snaps into place almost instantly with zero wasted travel time.",
                iconRes = R.drawable.ic_custom_history
            ),
            PhysicsPresetInfo(
                id = "Instant",
                title = "Instant Snap",
                subtitle = "Zero-latency direct display without motion (low power / instant)",
                stiffness = 1000f,
                damping = 1.0f,
                badgeText = "0ms · No Motion",
                tooltip = "Bypasses animation physics entirely. The dock appears instantly upon tapping, ideal for low-power operation or maximum speed.",
                iconRes = R.drawable.ic_custom_close
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
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_tools),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fluid Morphing Dynamics",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
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
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = preset.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = preset.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
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

private data class PhysicsPresetInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val stiffness: Float,
    val damping: Float,
    val badgeText: String,
    val tooltip: String,
    val iconRes: Int
)

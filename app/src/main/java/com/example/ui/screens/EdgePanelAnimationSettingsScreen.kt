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
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BrainOcrOverlayService
import com.example.R
import com.example.ui.viewmodel.SecondBrainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgePanelAnimationSettingsScreen(
    viewModel: SecondBrainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val currentPreset by viewModel.settingsRepository.edgePanelAnimPreset.collectAsState()
    val duration by viewModel.settingsRepository.edgePanelAnimDuration.collectAsState()
    val interpolator by viewModel.settingsRepository.edgePanelAnimInterpolator.collectAsState()
    val scale by viewModel.settingsRepository.edgePanelAnimScale.collectAsState()

    val presets = listOf(
        AnimPresetInfo("Smooth", "Smooth / Balanced", "Balanced 350ms with natural easing", R.drawable.ic_custom_sync, MaterialTheme.colorScheme.primary),
        AnimPresetInfo("Snappy", "Snappy Fast", "Ultra responsive 180ms with quick decelerate", R.drawable.ic_custom_history, MaterialTheme.colorScheme.secondary),
        AnimPresetInfo("Bouncy", "Bouncy Spring", "Playful 450ms with elastic overshoot bounce", R.drawable.ic_custom_star, MaterialTheme.colorScheme.tertiary),
        AnimPresetInfo("Fluid", "Fluid Slow", "Cinematic 550ms smooth transition", R.drawable.ic_custom_globe, MaterialTheme.colorScheme.primaryContainer),
        AnimPresetInfo("Instant", "Instant / Minimal", "Direct 100ms linear pop without scaling", R.drawable.ic_custom_close, MaterialTheme.colorScheme.error)
    )

    fun triggerTestAnimation() {
        val intent = Intent(context, BrainOcrOverlayService::class.java).apply {
            action = "com.example.ACTION_TOGGLE_PANEL"
        }
        try {
            context.startService(intent)
            Toast.makeText(context, "Testing Edge Panel Animation...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Make sure Edge Panel is enabled to test!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edge Panel Animations", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { triggerTestAnimation() },
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Live", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ANIMATION PRESETS SECTION
            Text(
                text = "Animation Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { preset ->
                    val isSelected = currentPreset.equals(preset.id, ignoreCase = true)
                    Surface(
                        onClick = { viewModel.settingsRepository.applyEdgePanelAnimPreset(preset.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(preset.tint.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = preset.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = preset.subtitle,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // CUSTOM TUNING CONTROLS
            Text(
                text = "Custom Animation Parameters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // DURATION SLIDER
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Duration (Speed)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${duration}ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = duration.toFloat(),
                    onValueChange = {
                        viewModel.settingsRepository.setEdgePanelAnimPreset("Custom")
                        viewModel.settingsRepository.setEdgePanelAnimDuration(it.toInt())
                    },
                    valueRange = 100f..800f,
                    steps = 13
                )
            }

            // SCALE EFFECT SLIDER
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Initial Zoom / Scale Effect", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = scale,
                    onValueChange = {
                        viewModel.settingsRepository.setEdgePanelAnimPreset("Custom")
                        viewModel.settingsRepository.setEdgePanelAnimScale(it)
                    },
                    valueRange = 0.70f..1.0f,
                    steps = 5
                )
            }

            // INTERPOLATOR CHIPS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Easing Curve", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Emphasized", "Decelerate", "Overshoot", "Bounce", "Linear").forEach { mode ->
                        val isSelected = interpolator.equals(mode, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.settingsRepository.setEdgePanelAnimPreset("Custom")
                                viewModel.settingsRepository.setEdgePanelAnimInterpolator(mode)
                            },
                            label = { Text(mode, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BOTTOM TEST BUTTON
            Button(
                onClick = { triggerTestAnimation() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Edge Panel Animation Now")
            }
        }
    }
}

private data class AnimPresetInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val tint: androidx.compose.ui.graphics.Color
)

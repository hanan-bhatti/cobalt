package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Modern Material 3 Expressive Switch with animated thumb icon morphing, custom colors, and haptic feedback.
 * Inspired by PixelPlayer's expressive toggle switch design.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(targetValue = if (checked) 1.05f else 1.0f, label = "switch_scale")

    Switch(
        checked = checked,
        onCheckedChange = { newValue ->
            if (enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange?.invoke(newValue)
            }
        },
        enabled = enabled,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                label = "switch_thumb_icon"
            ) { isChecked ->
                Icon(
                    imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedIconColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.scale(scale)
    )
}

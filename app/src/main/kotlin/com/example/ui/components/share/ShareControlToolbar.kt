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

package com.example.ui.components.share

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Unified control toolbar for all Cobalt share sheets.
 * Features:
 * 1. Connected M3 Expressive layout switcher (outer pill, 6dp inner corners, 3dp gap)
 * 2. Connected Tagline On/Off toggle + Shuffle icon button
 * 3. Theme mode toggle capsule
 *
 * Includes automatic horizontal scroll protection to guarantee buttons never clip on narrow screens.
 */
@Composable
fun <T> ShareControlToolbar(
    styles: List<T>,
    selectedStyle: T,
    onStyleSelected: (T) -> Unit,
    styleLabel: (T) -> String,
    styleIcon: (T) -> Int,
    showAttribution: Boolean,
    onToggleAttribution: () -> Unit,
    onShuffleTagline: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Group 1: Connected Layout Switcher (like InlineNewFolderCreator connected buttons)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            styles.forEachIndexed { index, style ->
                val isSelected = selectedStyle == style
                val isFirst = index == 0
                val isLast = index == styles.size - 1

                Surface(
                    onClick = { onStyleSelected(style) },
                    shape = RoundedCornerShape(
                        topStart = if (isFirst) 18.dp else 6.dp,
                        bottomStart = if (isFirst) 18.dp else 6.dp,
                        topEnd = if (isLast) 18.dp else 6.dp,
                        bottomEnd = if (isLast) 18.dp else 6.dp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = styleIcon(style)),
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = styleLabel(style),
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Group 2: Connected Tagline Toggle + Shuffle Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Tagline Toggle
            Surface(
                onClick = onToggleAttribution,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    bottomStart = 18.dp,
                    topEnd = 6.dp,
                    bottomEnd = 6.dp
                ),
                color = if (showAttribution) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (showAttribution) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (showAttribution) "Tagline" else "Tagline: Off",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (showAttribution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Shuffle Button
            Surface(
                onClick = onShuffleTagline,
                enabled = showAttribution,
                shape = RoundedCornerShape(
                    topStart = 6.dp,
                    bottomStart = 6.dp,
                    topEnd = 18.dp,
                    bottomEnd = 18.dp
                ),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_sync),
                        contentDescription = "Shuffle tagline",
                        tint = if (showAttribution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Group 3: Theme Toggle Capsule
        Surface(
            onClick = onToggleTheme,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.height(38.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = if (isDark) "Dark" else "Light",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

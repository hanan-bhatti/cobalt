/*
 * Second Brain - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 */

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UnavailableMediaPlaceholder(
    modifier: Modifier = Modifier,
    message: String? = null,
    compactMessage: String? = null
) {
    BoxWithConstraints(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        val isCompact = maxHeight < 110.dp || maxWidth < 160.dp
        val isUltraCompact = maxHeight < 70.dp

        val padding = if (isUltraCompact) 4.dp else if (isCompact) 8.dp else 16.dp
        val iconSize = if (isUltraCompact) 18.dp else if (isCompact) 24.dp else 36.dp
        val spacerHeight = if (isUltraCompact) 2.dp else if (isCompact) 4.dp else 8.dp
        val fontSize = if (isUltraCompact) 10.sp else if (isCompact) 11.sp else 13.sp
        val maxLines = if (isUltraCompact) 1 else if (isCompact) 2 else 3

        val textToDisplay = when {
            isCompact -> compactMessage ?: "Media unavailable"
            else -> message ?: "Removed from cloud on another device. Item unavailable."
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Media Unavailable",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.height(spacerHeight))
            Text(
                text = textToDisplay,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                ),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

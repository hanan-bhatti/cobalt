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

package com.example.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun AudioPlayerComponent(
    audioUri: String,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var durationSeconds by remember { mutableStateOf(0) }
    var positionSeconds by remember { mutableStateOf(0) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(audioUri) {
        try {
            mediaPlayer.setDataSource(audioUri)
            mediaPlayer.prepare()
            durationSeconds = mediaPlayer.duration / 1000
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            positionSeconds = 0
        }
        onDispose {
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(500)
            positionSeconds = mediaPlayer.currentPosition / 1000
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceShape = MaterialTheme.shapes.medium

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceVariantColor.copy(alpha = 0.5f), surfaceShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val pos = positionSeconds
            val dur = durationSeconds
            val posStr = "${pos / 60}:${if (pos % 60 < 10) "0" else ""}${pos % 60}"
            val durStr = "${dur / 60}:${if (dur % 60 < 10) "0" else ""}${dur % 60}"
            Text(
                text = "$posStr / $durStr",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = onSurfaceVariantColor
            )
            if (durationSeconds > 0) {
                Slider(
                    value = positionSeconds.toFloat(),
                    onValueChange = { newPos ->
                        positionSeconds = newPos.toInt()
                        mediaPlayer.seekTo(newPos.toInt() * 1000)
                    },
                    valueRange = 0f..durationSeconds.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer.pause()
                    isPlaying = false
                } else {
                    try {
                        if (positionSeconds >= durationSeconds && durationSeconds > 0) {
                            mediaPlayer.seekTo(0)
                            positionSeconds = 0
                        }
                        mediaPlayer.start()
                        isPlaying = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            modifier = Modifier
                .background(primaryContainerColor, CircleShape)
        ) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.ic_custom_stop else R.drawable.ic_custom_voice),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = onPrimaryContainerColor
            )
        }
    }
}

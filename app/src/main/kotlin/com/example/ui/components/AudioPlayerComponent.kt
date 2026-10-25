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

package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun AudioPlayerComponent(
    audioUri: String,
    modifier: Modifier = Modifier,
    isUnavailable: Boolean = false
) {
    if (isUnavailable || audioUri.isBlank()) {
        UnavailableMediaPlaceholder(modifier = modifier)
        return
    }

    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioUri) {
        val player = MediaPlayer()
        mediaPlayerRef = player
        isPrepared = false
        isPlaying = false
        durationMs = 0
        positionMs = 0

        try {
            if (audioUri.startsWith("content://")) {
                player.setDataSource(context, Uri.parse(audioUri))
            } else {
                player.setDataSource(audioUri)
            }
            player.setOnPreparedListener { mp ->
                isPrepared = true
                durationMs = mp.duration
            }
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = 0
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e("AudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                isPlaying = false
                isPrepared = false
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to setDataSource for $audioUri: ${e.message}", e)
        }

        onDispose {
            mediaPlayerRef = null
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            isPrepared = false
            isPlaying = false
        }
    }

    LaunchedEffect(isPlaying, isPrepared) {
        while (isPlaying && isPrepared) {
            delay(50)
            mediaPlayerRef?.let { player ->
                try {
                    if (isPrepared) {
                        positionMs = player.currentPosition
                    }
                } catch (_: Exception) {
                }
            }
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
            val pos = positionMs / 1000
            val dur = durationMs / 1000
            val posStr = "${pos / 60}:${if (pos % 60 < 10) "0" else ""}${pos % 60}"
            val durStr = "${dur / 60}:${if (dur % 60 < 10) "0" else ""}${dur % 60}"
            Text(
                text = "$posStr / $durStr",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = onSurfaceVariantColor
            )
            if (durationMs > 0) {
                Slider(
                    value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { newPos ->
                        if (isPrepared) {
                            positionMs = newPos.toInt()
                            try {
                                mediaPlayerRef?.seekTo(newPos.toInt())
                            } catch (e: Exception) {
                                Log.e("AudioPlayer", "Seek error: ${e.message}")
                            }
                        }
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        IconButton(
            onClick = {
                val player = mediaPlayerRef ?: return@IconButton
                if (!isPrepared) return@IconButton
                if (isPlaying) {
                    try {
                        player.pause()
                    } catch (e: Exception) {
                        Log.e("AudioPlayer", "Pause error: ${e.message}")
                    }
                    isPlaying = false
                } else {
                    try {
                        if (positionMs >= durationMs && durationMs > 0) {
                            player.seekTo(0)
                            positionMs = 0
                        }
                        player.start()
                        isPlaying = true
                    } catch (e: Exception) {
                        Log.e("AudioPlayer", "Start error: ${e.message}")
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

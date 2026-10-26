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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun AudioPlayerComponent(
    audioUri: String,
    modifier: Modifier = Modifier,
    isUnavailable: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onRetryTranscript: (() -> Unit)? = null,
    onReRecord: (() -> Unit)? = null,
    isOcrLoading: Boolean = false
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

    val pos = positionMs / 1000
    val dur = durationMs / 1000
    val posStr = "${pos / 60}:${if (pos % 60 < 10) "0" else ""}${pos % 60}"
    val durStr = "${dur / 60}:${if (dur % 60 < 10) "0" else ""}${dur % 60}"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        border = BorderStroke(0.75.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Play / Pause Button: shows ic_custom_play or ic_custom_pause
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
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.18f), CircleShape)
                        .bounceClick()
                ) {
                    Icon(
                        painter = painterResource(id = if (isPlaying) R.drawable.ic_custom_pause else R.drawable.ic_custom_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Voice Memo",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$posStr / $durStr",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = accentColor.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )
                    }
                }
            }

            if (onRetryTranscript != null || onReRecord != null) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onRetryTranscript != null) {
                        TextButton(
                            onClick = onRetryTranscript,
                            enabled = !isOcrLoading,
                            colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .bounceClick()
                        ) {
                            if (isOcrLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = accentColor
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Transcribing...", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_sync),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry Transcript", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (onRetryTranscript != null && onReRecord != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (onReRecord != null) {
                        TextButton(
                            onClick = onReRecord,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .bounceClick()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_voice),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-record", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

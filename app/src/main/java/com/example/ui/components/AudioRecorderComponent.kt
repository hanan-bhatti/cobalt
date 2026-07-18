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
import androidx.compose.ui.res.painterResource
import com.example.R

import android.media.MediaRecorder
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.*

@Composable
fun AudioRecorderComponent(
    onRecordComplete: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current

    val amplitudes = remember { mutableStateListOf<Float>() }
    var recordingDurationSeconds by remember { mutableStateOf(0) }

    val startRecording = {
        val file = File(context.cacheDir, "memo_${System.currentTimeMillis()}.mp4")
        outputFile = file
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
            recorder.release()
            mediaRecorder = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        }
    }

    // Polling maxAmplitude and duration loop
    LaunchedEffect(isRecording) {
        if (isRecording) {
            amplitudes.clear()
            repeat(40) { amplitudes.add(0.05f) }
            recordingDurationSeconds = 0
            var ticker = 0
            while (isRecording) {
                kotlinx.coroutines.delay(100)
                ticker++
                if (ticker % 10 == 0) {
                    recordingDurationSeconds++
                }
                val amp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }
                // Normalize to a pleasant range (0.05f to 1.0f)
                val rawNormalized = (amp.toFloat() / 32767f)
                // Boost normal/low voices so the wave is nice and active
                val normalized = (rawNormalized * 2.5f).coerceIn(0.05f, 1.0f)
                
                amplitudes.removeAt(0)
                amplitudes.add(normalized)
            }
        } else {
            amplitudes.clear()
        }
    }

    // Pulsing indicator dot animation
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color.Red.copy(alpha = pulseAlpha) else Color.Gray)
            )
            Spacer(modifier = Modifier.width(8.dp))
            
            if (isRecording) {
                // Timer
                val mins = recordingDurationSeconds / 60
                val secs = recordingDurationSeconds % 60
                val timeStr = "${mins}:${if (secs < 10) "0" else ""}$secs"
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.width(42.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Real-time Waveform Canvas
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                ) {
                    val barWidth = 3.dp.toPx()
                    val gap = 3.dp.toPx()
                    val count = amplitudes.size
                    
                    for (i in 0 until count) {
                        val amp = amplitudes[count - 1 - i]
                        val x = size.width - (i * (barWidth + gap)) - barWidth
                        if (x < 0) break
                        
                        val barHeight = (size.height * amp).coerceAtLeast(3.dp.toPx())
                        val y = (size.height - barHeight) / 2f
                        
                        drawRoundRect(
                            color = Color.Red.copy(alpha = 0.8f),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                }
            } else {
                Text(
                    text = "Tap mic to record memo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        IconButton(
            onClick = {
                if (isRecording) {
                    try {
                        mediaRecorder?.stop()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    mediaRecorder?.release()
                    mediaRecorder = null
                    isRecording = false
                    outputFile?.let { onRecordComplete(it) }
                } else {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            modifier = Modifier
                .background(if (isRecording) Color.Red.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer, CircleShape)
        ) {
            Icon(
                painter = painterResource(id = if (isRecording) R.drawable.ic_custom_stop else R.drawable.ic_custom_voice),
                contentDescription = "Record",
                tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

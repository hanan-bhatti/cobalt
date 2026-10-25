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

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private const val TAG = "VideoPlayer"

@OptIn(UnstableApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayer(
    videoUri: String,
    modifier: Modifier = Modifier,
    isUnavailable: Boolean = false,
    itemId: String? = null,
    thumbnailUrl: String? = null
) {
    if (isUnavailable || videoUri.isBlank()) {
        UnavailableMediaPlaceholder(modifier = modifier)
        return
    }

    val isYouTube = remember(videoUri) {
        com.example.ui.screens.extractYouTubeVideoId(videoUri) != null
    }
    if (isYouTube) {
        com.example.ui.screens.YouTubeTrailerPlayer(
            trailerUrl = videoUri,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. Resolve Best Playable URI (Prefer local persistent file or cache if present)
    val resolvedPlayableUri = remember(videoUri, itemId) {
        resolveLocalOrRemotePath(context, videoUri, itemId)
    }

    // 2. Pre-extract exact dimensions (Zero layout shift on initial load)
    val precomputedAspect = remember(resolvedPlayableUri) {
        extractExactAspectRatio(context, resolvedPlayableUri)
    }
    var exactAspectRatio by remember(resolvedPlayableUri) { mutableFloatStateOf(precomputedAspect ?: (16f / 9f)) }

    // 3. Player State
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<PlaybackException?>(null) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }

    // Controls overlay visibility with auto-hide timer
    var areControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPositionMs by remember { mutableFloatStateOf(0f) }

    // 4. Build resilient ExoPlayer instance
    var retryTrigger by remember { mutableIntStateOf(0) }
    val exoPlayer = remember(resolvedPlayableUri, retryTrigger) {
        playbackError = null
        isBuffering = true
        isEnded = false

        val parsedUri = if (resolvedPlayableUri.startsWith("/")) {
            Uri.fromFile(File(resolvedPlayableUri))
        } else {
            Uri.parse(resolvedPlayableUri)
        }

        // Configure resilient HTTP data source with timeouts and cross-protocol redirect support
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setUserAgent("Cobalt-Android-Media/1.0")

        // DefaultDataSource delegates content://, file://, and asset:// locally,
        // and routes remote http/https streams through httpDataSourceFactory.
        val universalDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(universalDataSourceFactory)

        val mediaItem = MediaItem.fromUri(parsedUri)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(mediaItem)
                volume = if (isMuted) 0f else 1f
                prepare()
            }
    }

    // 5. Player Listeners & Position Tracking
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val rotation = videoSize.unappliedRotationDegrees
                    val calculatedAspect = if (rotation == 90 || rotation == 270) {
                        videoSize.height.toFloat() / videoSize.width.toFloat()
                    } else {
                        videoSize.width.toFloat() / videoSize.height.toFloat()
                    }
                    if (calculatedAspect > 0.1f) {
                        exactAspectRatio = calculatedAspect
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isEnded = playbackState == Player.STATE_ENDED
                isPlaying = exoPlayer.isPlaying
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0) durationMs = d
                    playbackError = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error [${error.errorCodeName}]: ${error.message}", error)
                playbackError = error
                isBuffering = false
                isPlaying = false
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic position ticker while playing
    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0) durationMs = d
            delay(250)
        }
    }

    // Auto-dismiss controls after 3.5 seconds of inactivity (unless paused or dragging)
    LaunchedEffect(areControlsVisible, isPlaying, lastInteractionTime, isDraggingSlider) {
        if (areControlsVisible && isPlaying && !isDraggingSlider) {
            delay(3500)
            areControlsVisible = false
        }
    }

    // Auto-pause when app moves to background
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 6. UI Structure with exact adaptive aspect ratio
    val safeAspect = exactAspectRatio.coerceIn(0.5625f, 2.4f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(safeAspect)
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                areControlsVisible = !areControlsVisible
                lastInteractionTime = System.currentTimeMillis()
            },
        contentAlignment = Alignment.Center
    ) {
        // Video View
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Poster Preview placeholder during initial buffering (avoids black screen flash)
        if (isBuffering && currentPositionMs == 0L && !thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Circular Buffering Indicator
        if (isBuffering && playbackError == null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }

        // Error State Overlay
        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Playback Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Unable to play video",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val errorMessage = when (playbackError?.errorCode) {
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                            "Network connection failed. Please verify your internet connection."
                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                            "Access denied or cloud link expired. Re-authentication may be required."
                        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                            "Video file could not be found locally."
                        else ->
                            "Unable to play video (${playbackError?.errorCodeName ?: "format error"}). Tap below to retry."
                    }
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            playbackError = null
                            isBuffering = true
                            retryTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = areControlsVisible && playbackError == null,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                // Top controls bar (Mute / Unmute & Quick Info)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Centered Play / Pause / Replay Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clip(CircleShape)
                        .clickable {
                            lastInteractionTime = System.currentTimeMillis()
                            if (isEnded) {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                            } else if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val iconRes = when {
                        isEnded -> R.drawable.ic_custom_sync
                        isPlaying -> R.drawable.ic_custom_pause
                        else -> R.drawable.ic_custom_play
                    }
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Bottom Bar: Progress Slider & Timestamp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val activePosition = if (isDraggingSlider) sliderDragPositionMs.toLong() else currentPositionMs
                    val totalDuration = if (durationMs > 0) durationMs else 1L
                    val progressFraction = (activePosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

                    Slider(
                        value = progressFraction,
                        onValueChange = { fraction ->
                            isDraggingSlider = true
                            sliderDragPositionMs = fraction * totalDuration
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(sliderDragPositionMs.toLong())
                            isDraggingSlider = false
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimeMs(activePosition),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 11.sp
                        )

                        Text(
                            text = formatTimeMs(durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Resolves local file cache or persistent media directories before falling back to remote streaming.
 */
private fun resolveLocalOrRemotePath(context: Context, videoUri: String, itemId: String?): String {
    // 1. Direct local file path
    if (videoUri.startsWith("/")) {
        val file = File(videoUri)
        if (file.exists() && file.length() > 0) {
            return videoUri
        }
    }

    // 2. Check app persistent media directory: media/videos/$itemId.mp4
    if (!itemId.isNullOrBlank()) {
        val persistentFile = File(context.filesDir, "media/videos/$itemId.mp4")
        if (persistentFile.exists() && persistentFile.length() > 0) {
            return persistentFile.absolutePath
        }

        // Check common cache patterns
        val cacheFiles = context.cacheDir.listFiles { _, name ->
            name.contains(itemId) && name.endsWith(".mp4")
        }
        if (!cacheFiles.isNullOrEmpty() && cacheFiles[0].length() > 0) {
            return cacheFiles[0].absolutePath
        }
    }

    // 3. Fallback to passed videoUri (e.g. Firebase Storage HTTPS URL)
    return videoUri
}

/**
 * Extracts exact video aspect ratio via MediaMetadataRetriever before video playback starts.
 * Only extracts synchronously for local files or content URIs to prevent NetworkOnMainThreadException.
 */
private fun extractExactAspectRatio(context: Context, uriString: String): Float? {
    val isLocal = uriString.startsWith("/") || 
                  uriString.startsWith("file://") || 
                  uriString.startsWith("content://")
    if (!isLocal) return null

    val retriever = MediaMetadataRetriever()
    return try {
        if (uriString.startsWith("/")) {
            retriever.setDataSource(uriString)
        } else {
            retriever.setDataSource(context, Uri.parse(uriString))
        }
        val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

        val w = widthStr?.toFloatOrNull() ?: return null
        val h = heightStr?.toFloatOrNull() ?: return null
        val rot = rotationStr?.toIntOrNull() ?: 0

        if (w <= 0f || h <= 0f) return null

        if (rot == 90 || rot == 270) {
            h / w
        } else {
            w / h
        }
    } catch (_: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {}
    }
}

/**
 * Formats milliseconds into standard mm:ss or hh:mm:ss.
 */
private fun formatTimeMs(totalMs: Long): String {
    if (totalMs <= 0L) return "00:00"
    val totalSeconds = totalMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

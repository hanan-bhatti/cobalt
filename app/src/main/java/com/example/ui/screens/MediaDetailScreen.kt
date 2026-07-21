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

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.getBestImagePath
import com.example.ui.components.MarkdownText
import com.example.ui.components.VideoPlayer
import com.example.ui.viewmodel.SecondBrainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaDetailSection(
    item: SavedItem,
    viewModel: SecondBrainViewModel,
    modifier: Modifier = Modifier
) {
    var showPosterZoom by remember { mutableStateOf(false) }

    val formattedMediaType = remember(item.mediaType) {
        when (item.mediaType?.lowercase()) {
            "movie" -> "Movie"
            "tv", "tv show", "tv_show" -> "TV Show"
            "anime" -> "Anime"
            else -> item.mediaType?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            } ?: "Media"
        }
    }

    val posterPath = remember(item) { item.getBestImagePath() ?: item.thumbnailPath }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // BACKDROP HEADER (if present)
        if (!item.backdropUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = item.backdropUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Bottom Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }
        }

        // POSTER & INFO SECTION
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Interactive Poster Thumbnail
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        if (!posterPath.isNullOrBlank()) {
                            showPosterZoom = true
                        }
                    }
            ) {
                if (!posterPath.isNullOrBlank()) {
                    AsyncImage(
                        model = posterPath,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_movie),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Media Info Column (Release Year, Media Type Badge)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Media Type Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = formattedMediaType.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Release Year
                if (!item.releaseYear.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Released: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.releaseYear,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Quick Status Summary
                val currentStatus = item.watchStatus ?: "Plan to Watch"
                Text(
                    text = "Status: $currentStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ONE-TAP WATCH STATUS SELECTOR CHIPS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "WATCH STATUS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            val watchStatuses = listOf("Plan to Watch", "Watching", "Completed", "Dropped")
            val currentStatus = item.watchStatus ?: "Plan to Watch"

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(watchStatuses, key = { it }) { status ->
                    val isSelected = currentStatus.equals(status, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                viewModel.updateSavedItem(item.copy(watchStatus = status))
                            }
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // GENRES SECTION
        if (item.genres.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "GENRES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(item.genres, key = { it }) { genre ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // WATCH PROVIDERS ("Where to Watch")
        if (item.watchProviders.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_movie),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "WHERE TO WATCH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(item.watchProviders, key = { it }) { provider ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = provider,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // SYNOPSIS / OVERVIEW SECTION
        if (item.content.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SYNOPSIS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                MarkdownText(
                    markdown = item.content,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
            }
        }

        // TRAILER VIDEO PLAYER SECTION
        if (!item.trailerUrl.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "TRAILER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                YouTubeTrailerPlayer(trailerUrl = item.trailerUrl!!)
            }
        }
    }

    // POSTER ZOOM DIALOG
    if (showPosterZoom && !posterPath.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { showPosterZoom = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = posterPath,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                if (newScale > 1f) {
                                    val maxOffset = (newScale - 1f) * 300f
                                    val newOffset = offset + pan
                                    offset = Offset(
                                        x = newOffset.x.coerceIn(-maxOffset, maxOffset),
                                        y = newOffset.y.coerceIn(-maxOffset, maxOffset)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                                scale = newScale
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )

                IconButton(
                    onClick = { showPosterZoom = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .statusBarsPadding()
                        .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouTubeTrailerPlayer(
    trailerUrl: String,
    modifier: Modifier = Modifier
) {
    val videoId = remember(trailerUrl) { extractYouTubeVideoId(trailerUrl) }

    if (videoId != null) {
        var isLoading by remember { mutableStateOf(true) }
        val embedHtml = remember(videoId) {
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { background-color: #000000; height: 100vh; display: flex; align-items: center; justify-content: center; }
                    .video-container { position: relative; width: 100%; padding-bottom: 56.25%; height: 0; overflow: hidden; }
                    .video-container iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
                </style>
            </head>
            <body>
                <div class="video-container">
                    <iframe src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=0&rel=0&playsinline=1" 
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" 
                            allowfullscreen></iframe>
                </div>
            </body>
            </html>
            """.trimIndent()
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                        }
                        loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "utf-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    } else {
        VideoPlayer(
            videoUri = trailerUrl,
            modifier = modifier.clip(RoundedCornerShape(16.dp))
        )
    }
}

fun extractYouTubeVideoId(url: String): String? {
    if (url.isBlank()) return null
    val pattern = "(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/" +
            "|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/\\s]{11})"
    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    val matchResult = regex.find(url)
    return matchResult?.groupValues?.getOrNull(1)
}

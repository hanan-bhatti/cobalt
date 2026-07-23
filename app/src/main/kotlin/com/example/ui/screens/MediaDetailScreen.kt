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
import android.net.Uri
import android.webkit.WebChromeClient
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.getBestImagePath
import com.example.ui.components.MarkdownText
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.*
import com.example.ui.viewmodel.SecondBrainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaDetailSection(
    item: SavedItem,
    viewModel: SecondBrainViewModel,
    modifier: Modifier = Modifier,
    onWatchStatusChanged: ((String) -> Unit)? = null
) {
    var showPosterZoom by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        viewModel.enrichMediaItem(item)
    }

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
    val backdropImage = remember(item) { item.backdropUrl?.takeIf { it.isNotBlank() } ?: posterPath }
    val formattedDate = remember(item.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // HERO BACKDROP HEADER WITH PARALLAX & TITLE OVERLAY
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!backdropImage.isNullOrBlank()) {
                AsyncImage(
                    model = backdropImage,
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
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            // Multi-stop Gradient Overlay for rich readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.70f),
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            // Overlay Content at the bottom of the image banner
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Badges Row (Media Type, Release Year, Star Rating)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Media Type Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ) {
                        Text(
                            text = formattedMediaType.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Release Year Badge
                    if (!item.releaseYear.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = item.releaseYear,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Rating Badge
                    if (item.rating != null && item.rating > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("★", color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = String.format(Locale.US, "%.1f", item.rating),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Main Title Overlay
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // POSTER THUMBNAIL & RICH METADATA PANEL
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive Poster Thumbnail
            Box(
                modifier = Modifier
                    .width(105.dp)
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
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Rich Metadata Summary Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Current Watch Status Pill
                val currentStatus = item.watchStatus ?: "Plan to Watch"
                val (statusIconRes, statusBgColor, statusContentColor) = when (currentStatus.lowercase()) {
                    "watching" -> Triple(R.drawable.ic_custom_eye, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    "completed" -> Triple(R.drawable.ic_custom_check, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    "dropped" -> Triple(R.drawable.ic_custom_close, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    else -> Triple(R.drawable.ic_custom_star, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBgColor,
                    contentColor = statusContentColor
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = statusIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = statusContentColor
                        )
                        Text(
                            text = currentStatus,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusContentColor
                        )
                    }
                }

                // Media Type & Release Year
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val formattedMediaType = when (item.mediaType?.lowercase()) {
                        "movie" -> "Movie"
                        "tv", "tv show", "tv_show" -> "TV Show"
                        "anime" -> "Anime"
                        else -> item.mediaType?.replaceFirstChar { it.uppercase() } ?: "Media"
                    }
                    Text(
                        text = formattedMediaType,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!item.releaseYear.isNullOrBlank()) {
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text(
                            text = item.releaseYear,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Rating Score
                if (item.rating != null && item.rating > 0.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("★", color = StarGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${String.format(Locale.US, "%.1f", item.rating)} / 10",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Preservation Date
                Text(
                    text = "Preserved ${formattedDate}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // ONE-TAP WATCH STATUS SELECTOR CHIPS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "CHANGE STATUS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            val statuses = listOf("Plan to Watch", "Watching", "Completed", "Dropped")
            val currentStatus = item.watchStatus ?: "Plan to Watch"

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(statuses, key = { it }) { status ->
                    val isSelected = status.equals(currentStatus, ignoreCase = true)
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
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                if (!isSelected) {
                                    if (onWatchStatusChanged != null) {
                                        onWatchStatusChanged(status)
                                    } else {
                                        val updated = item.copy(watchStatus = status)
                                        viewModel.updateSavedItem(updated)
                                        viewModel.showToast("Status updated to $status")
                                    }
                                }
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
                    color = MaterialTheme.colorScheme.secondary
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
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(item.watchProviders, key = { it }) { provider ->
                        val config = getStreamingProviderConfig(provider)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = config.backgroundColor,
                            contentColor = config.contentColor,
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (config.iconResId != null) {
                                    Icon(
                                        painter = painterResource(id = config.iconResId),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = config.contentColor
                                    )
                                }
                                Text(
                                    text = config.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = config.contentColor
                                )
                            }
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
                    color = MaterialTheme.colorScheme.secondary
                )
                MarkdownText(
                    markdown = item.content,
                    color = MaterialTheme.colorScheme.onBackground
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
                        color = MaterialTheme.colorScheme.secondary
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
    val context = LocalContext.current
    val videoId = remember(trailerUrl) { extractYouTubeVideoId(trailerUrl) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (videoId != null) {
            var isLoading by remember { mutableStateOf(true) }
            val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=0&rel=0&playsinline=1&modestbranding=1"

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                allowFileAccess = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                            }
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }
                            }
                            loadUrl(embedUrl)
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

        // Quick Action: Watch directly on YouTube App
        Text(
            text = "Watch on YouTube App ↗",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                .padding(vertical = 4.dp)
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

data class StreamingProviderConfig(
    val backgroundColor: Color,
    val contentColor: Color,
    val iconResId: Int? = null,
    val label: String
)

@Composable
fun getStreamingProviderConfig(name: String): StreamingProviderConfig {
    val clean = name.lowercase().trim()
    return when {
        clean.contains("netflix") -> StreamingProviderConfig(StreamingNetflix, Color.White, R.drawable.ic_provider_netflix, "Netflix")
        clean.contains("prime") || clean.contains("amazon") -> StreamingProviderConfig(StreamingPrime, Color.White, R.drawable.ic_provider_prime, "Prime Video")
        clean.contains("disney") -> StreamingProviderConfig(StreamingDisney, Color.White, R.drawable.ic_provider_disney, "Disney+")
        clean.contains("crunchyroll") -> StreamingProviderConfig(StreamingCrunchyroll, Color.White, R.drawable.ic_provider_crunchyroll, "Crunchyroll")
        clean.contains("hulu") -> StreamingProviderConfig(StreamingHulu, StreamingHuluDark, R.drawable.ic_provider_hulu, "Hulu")
        clean.contains("apple") || clean.contains("itunes") -> StreamingProviderConfig(StreamingApple, Color.White, R.drawable.ic_provider_apple, "Apple TV+")
        clean.contains("hbo") || clean.contains("max") -> StreamingProviderConfig(StreamingMax, Color.White, R.drawable.ic_provider_hbo, "Max")
        clean.contains("paramount") -> StreamingProviderConfig(StreamingParamount, Color.White, null, "Paramount+")
        clean.contains("peacock") -> StreamingProviderConfig(StreamingPeacock, Color.White, null, "Peacock")
        clean.contains("youtube") -> StreamingProviderConfig(StreamingYouTube, Color.White, R.drawable.ic_provider_youtube, "YouTube")
        clean.contains("sony") || clean.contains("liv") -> StreamingProviderConfig(StreamingSony, Color.White, null, "Sony LIV")
        clean.contains("zee5") -> StreamingProviderConfig(StreamingZee5, Color.White, null, "ZEE5")
        clean.contains("hotstar") -> StreamingProviderConfig(StreamingHotstar, Color.White, null, "Hotstar")
        clean.contains("jio") -> StreamingProviderConfig(StreamingJio, Color.White, null, "JioCinema")
        clean.contains("funimation") -> StreamingProviderConfig(StreamingFunimation, Color.White, null, "Funimation")
        clean.contains("vudu") || clean.contains("fandango") -> StreamingProviderConfig(StreamingVudu, Color.White, null, "Vudu")
        clean.contains("google play") -> StreamingProviderConfig(StreamingGooglePlay, Color.White, null, "Google Play")
        clean.contains("tubi") -> StreamingProviderConfig(StreamingTubi, Color.White, null, "Tubi")
        clean.contains("pluto") -> StreamingProviderConfig(StreamingPluto, Color.Black, null, "Pluto TV")
        clean.contains("starz") -> StreamingProviderConfig(StreamingStarz, Color.White, null, "STARZ")
        clean.contains("showtime") -> StreamingProviderConfig(StreamingShowtime, Color.White, null, "Showtime")
        clean.contains("iplayer") || clean.contains("bbc") -> StreamingProviderConfig(StreamingBbc, Color.White, null, "BBC iPlayer")
        clean.contains("hidive") -> StreamingProviderConfig(StreamingHidive, Color.White, null, "HIDIVE")
        else -> StreamingProviderConfig(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, null, name)
    }
}

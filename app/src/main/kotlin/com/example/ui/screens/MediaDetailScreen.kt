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

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import com.example.utils.isRtlText
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
import com.example.ui.viewmodel.CobaltViewModel
import java.util.Locale

private fun formatCount(count: Long?): String {
    if (count == null || count <= 0) return "-"
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatVotes(votes: Int?): String {
    if (votes == null || votes <= 0) return ""
    return when {
        votes >= 1_000_000 -> String.format(Locale.US, "%.1fM votes", votes / 1_000_000.0)
        votes >= 1_000 -> String.format(Locale.US, "%.1fK votes", votes / 1_000.0)
        else -> "$votes votes"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MediaDetailSection(
    item: SavedItem,
    viewModel: CobaltViewModel,
    modifier: Modifier = Modifier,
    onWatchStatusChanged: ((String) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    var showPosterZoom by remember { mutableStateOf(false) }
    var showMoreInfoSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val progress = item.showProgress

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
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
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
                val isTitleRtl = remember(item.title) { isRtlText(item.title) }
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isTitleRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = if (isTitleRtl) TextAlign.Right else TextAlign.Left,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            textDirection = androidx.compose.ui.text.style.TextDirection.Content
                        ),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

        // OFFICIAL RATINGS ROW (Trakt, IMDb, Rotten Tomatoes, Popcorn)
        val hasRatings = progress?.traktRatingPercent != null || progress?.imdbRating != null || progress?.rottenTomatoesScore != null || item.rating != null
        if (hasRatings) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Trakt Score
                item {
                    val traktScore = progress?.traktRatingPercent ?: item.rating?.let { (it * 10).toInt() }
                    if (traktScore != null && traktScore > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_trakt),
                                    contentDescription = null,
                                    tint = Color(0xFFED1C24),
                                    modifier = Modifier.size(14.dp)
                                )
                                Column {
                                    Text(
                                        text = "$traktScore%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val votes = progress?.traktVotes
                                    Text(
                                        text = if (votes != null && votes > 0) formatVotes(votes) else "Trakt",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // IMDb Score
                item {
                    val imdbScore = progress?.imdbRating
                    if (!imdbScore.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF5C518).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFF5C518).copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFF5C518)
                                ) {
                                    Text(
                                        text = "IMDb",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = imdbScore,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = progress.imdbVotes ?: "Score",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Rotten Tomatoes Score
                item {
                    val rtScore = progress?.rottenTomatoesScore
                    if (!rtScore.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFA320A).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFFA320A).copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🍅", fontSize = 14.sp)
                                Column {
                                    Text(
                                        text = rtScore,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Fresh",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Popcorn Score
                item {
                    val popScore = progress?.popcornScore
                    if (!popScore.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🍿", fontSize = 14.sp)
                                Column {
                                    Text(
                                        text = popScore,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Hot",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
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

        // TV SERIES PROGRESS (Seasons & Episodes)
        if (progress != null && (progress.totalWatched > 0 || progress.seasons.isNotEmpty() || progress.totalAired > 0)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_trakt),
                            contentDescription = null,
                            tint = Color(0xFFED1C24),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "WATCH PROGRESS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (progress.lastWatchedSeason != null && progress.lastWatchedEpisode != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFED1C24).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Last: S%02dE%02d".format(progress.lastWatchedSeason, progress.lastWatchedEpisode),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFED1C24),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (progress.totalAired > 0) "${progress.totalWatched} of ${progress.totalAired} episodes"
                                       else "${progress.totalWatched} episodes watched",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${progress.progressPercent}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = if (progress.completed) Color(0xFF10B981) else Color(0xFFED1C24)
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progress.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = if (progress.completed) Color(0xFF10B981) else Color(0xFFED1C24),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )

                        // Next Up Card
                        if (progress.nextEpisodeSeason != null && progress.nextEpisodeNumber != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_play),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "NEXT UP",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "S%02dE%02d • %s".format(
                                                progress.nextEpisodeSeason,
                                                progress.nextEpisodeNumber,
                                                progress.nextEpisodeTitle ?: "Episode ${progress.nextEpisodeNumber}"
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Season breakdown chips
                        if (progress.seasons.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(progress.seasons.sortedBy { it.seasonNumber }, key = { it.seasonNumber }) { season ->
                                    val isSeasonDone = season.isCompleted
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSeasonDone) Color(0xFF10B981).copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSeasonDone) Color(0xFF10B981).copy(alpha = 0.35f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = season.title?.takeIf { it.isNotBlank() && it != season.seasonNumber.toString() } ?: "Season ${season.seasonNumber}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSeasonDone) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "• ${season.watchedEpisodes}/${season.totalEpisodes}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // QUALITY OVER TIME (Season Ratings Sparkline)
        if (progress != null && progress.seasonRatings.size >= 2) {
            val seasonRatings = progress.seasonRatings.sortedBy { it.seasonNumber }
            val peak = seasonRatings.maxByOrNull { it.ratingPercent }
            val low = seasonRatings.minByOrNull { it.ratingPercent }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUALITY OVER TIME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (peak != null && low != null) {
                        Text(
                            text = "Peak S${peak.seasonNumber} · ${peak.ratingPercent}% · Low S${low.seasonNumber} · ${low.ratingPercent}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(bottom = 18.dp)) {
                            val w = size.width
                            val h = size.height
                            val minVal = (low?.ratingPercent ?: 40).toFloat().coerceAtLeast(20f)
                            val maxVal = (peak?.ratingPercent ?: 90).toFloat().coerceAtLeast(minVal + 10f)
                            val range = (maxVal - minVal).coerceAtLeast(1f)

                            val stepX = if (seasonRatings.size > 1) w / (seasonRatings.size - 1) else w
                            val path = androidx.compose.ui.graphics.Path()

                            seasonRatings.forEachIndexed { idx, point ->
                                val x = idx * stepX
                                val normY = (point.ratingPercent - minVal) / range
                                val y = h - (normY * h)
                                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            drawPath(
                                path = path,
                                color = Color(0xFFFFB300),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )

                            seasonRatings.forEachIndexed { idx, point ->
                                val x = idx * stepX
                                val normY = (point.ratingPercent - minVal) / range
                                val y = h - (normY * h)
                                drawCircle(
                                    color = Color(0xFFFFB300),
                                    radius = 3.5.dp.toPx(),
                                    center = androidx.compose.ui.geometry.Offset(x, y)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            seasonRatings.forEach { pt ->
                                Text(
                                    text = "S${pt.seasonNumber}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
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
        val allProviders = if (item.richWatchProviders.isNotEmpty()) {
            item.richWatchProviders
        } else {
            item.watchProviders.map { com.example.data.model.MediaWatchProvider(name = it, type = "Stream") }
        }

        if (allProviders.isNotEmpty()) {
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

                val distinctProviders = allProviders.distinctBy { "${it.name}_${it.type}" }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(distinctProviders, key = { "${it.name}_${it.type}" }) { provider ->
                        val config = getStreamingProviderConfig(provider.name)
                        val watchUrl = getStreamingWatchUrl(provider.name, item.title)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .width(112.dp)
                                .height(74.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    com.example.util.CobaltCustomTabsLauncher.launch(context, watchUrl)
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (provider.price != null) "${provider.type} (${provider.price})" else provider.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // SYNOPSIS / OVERVIEW SECTION
        val cleanSynopsis = item.extractedText?.takeIf { it.isNotBlank() }
            ?: item.content.takeIf { !it.startsWith("http://") && !it.startsWith("https://") }
            ?: ""

        if (cleanSynopsis.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SYNOPSIS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                MarkdownText(
                    markdown = cleanSynopsis,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (item.tagline?.isNotBlank() == true || item.runtime != null || item.budget != null || item.revenue != null || item.status != null || item.productionCompanies.isNotEmpty()) {
                    TextButton(
                        onClick = { showMoreInfoSheet = true },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "View More Info",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // SOURCE / WEB LINK (e.g. Trakt.tv page)
        if (item.content.startsWith("http://") || item.content.startsWith("https://")) {
            OutlinedButton(
                onClick = {
                    com.example.util.CobaltCustomTabsLauncher.launch(context, item.content)
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.traktId != null && item.traktId > 0) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_trakt),
                            contentDescription = null,
                            tint = Color(0xFFED1C24),
                            modifier = Modifier.size(16.dp)
                        )
                        Text("View on Trakt.tv", color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_link_external),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Open Source Link", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
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

    // MORE INFO BOTTOM SHEET
    if (showMoreInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreInfoSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Details & Information",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // COMMUNITY STATS (Plays, Watchers, Lists, Favorites)
                if (progress?.plays != null || progress?.watchers != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "▶ Plays", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = formatCount(progress.plays), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "👥 Watchers", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = formatCount(progress.watchers), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "📋 Lists", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = formatCount(progress.listsCount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "♥ Favorites", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = formatCount(progress.favoritesCount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }

                // AIRS & PREMIERED
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val airs = progress?.airsTime
                    if (!airs.isNullOrBlank()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = "AIRS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                            Text(text = airs, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!item.releaseYear.isNullOrBlank()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = "PREMIERED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                            Text(text = item.releaseYear, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // STATUS & NETWORK
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val statusStr = item.status ?: "Returning Series"
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = statusStr, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFB300), fontWeight = FontWeight.SemiBold)
                    }
                    val network = progress?.network
                    if (!network.isNullOrBlank()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = "NETWORK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                            Text(text = network, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // TOTAL RUNTIME
                val totalRuntime = progress?.totalRuntimeFormatted
                if (!totalRuntime.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "TOTAL RUNTIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = totalRuntime, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // CREATORS
                val creators = progress?.creators ?: emptyList()
                if (creators.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "CREATOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = creators.joinToString(", ") { "$it (Creator)" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // STUDIOS
                val studios = progress?.studios?.takeIf { it.isNotEmpty() } ?: item.productionCompanies
                if (studios.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "STUDIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = studios.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // COUNTRY & LANGUAGE
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val country = progress?.country ?: "United States"
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "COUNTRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = country, style = MaterialTheme.typography.bodyMedium)
                    }
                    val language = progress?.language ?: "English"
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "LANGUAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = language, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // GENRES
                if (item.genres.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = "GENRE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = item.genres.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // PARENTAL GUIDANCE
                val cert = progress?.contentRating ?: "TV-MA"
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "PARENTAL GUIDANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val isSevere = cert == "TV-MA" || cert == "R" || cert == "18+" || cert == "18"
                            val warnings = listOf(
                                "Sex & Nudity" to (if (isSevere) "Severe" to 0.9f else "Moderate" to 0.5f),
                                "Violence & Gore" to (if (isSevere) "Severe" to 0.95f else "Moderate" to 0.5f),
                                "Profanity" to (if (isSevere) "Severe" to 0.85f else "Mild" to 0.3f),
                                "Alcohol, Drugs & Smoking" to (if (isSevere) "Severe" to 0.8f else "Mild" to 0.25f),
                                "Frightening & Intense Scenes" to (if (isSevere) "Severe" to 0.95f else "Moderate" to 0.6f)
                            )
                            warnings.forEach { (cat, ratingPair) ->
                                val (severityText, fraction) = ratingPair
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = cat, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f))
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.weight(1.5f).height(6.dp).clip(CircleShape),
                                        color = if (severityText == "Severe") Color(0xFFEF4444) else Color(0xFFFFB300),
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = severityText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (severityText == "Severe") Color(0xFFEF4444) else Color(0xFFFFB300),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(55.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }

                // EXTERNAL LINKS
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "OFFICIAL & OTHER LINKS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!item.imdbId.isNullOrBlank()) {
                            AssistChip(
                                onClick = { com.example.util.CobaltCustomTabsLauncher.launch(context, "https://www.imdb.com/title/${item.imdbId}") },
                                label = { Text("IMDb") }
                            )
                        }
                        if (item.tmdbId != null && item.tmdbId > 0) {
                            AssistChip(
                                onClick = { com.example.util.CobaltCustomTabsLauncher.launch(context, "https://www.themoviedb.org/${item.mediaType ?: "tv"}/${item.tmdbId}") },
                                label = { Text("TMDb") }
                            )
                        }
                        if (item.traktId != null && item.traktId > 0) {
                            AssistChip(
                                onClick = { com.example.util.CobaltCustomTabsLauncher.launch(context, item.content) },
                                label = { Text("Trakt") }
                            )
                        }
                    }
                }

                if (!item.tagline.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "TAGLINE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = "“${item.tagline}”", style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

class YouTubePlayerBridge(
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit
) {
    @JavascriptInterface
    fun onReady() {
        onReady()
    }

    @JavascriptInterface
    fun onError(errorCode: String) {
        onError(errorCode)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouTubeTrailerPlayer(
    trailerUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoId = remember(trailerUrl) { extractYouTubeVideoId(trailerUrl) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (videoId != null) {
            var isLoading by remember { mutableStateOf(true) }
            var hasPlaybackError by remember { mutableStateOf(false) }
            var webViewRef by remember { mutableStateOf<WebView?>(null) }

            val appOrigin = remember(context) { "https://${context.packageName}" }

            val embedHtml = remember(videoId, appOrigin) {
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <meta name="referrer" content="strict-origin-when-cross-origin">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; background-color: #000000; }
                        html, body { width: 100%; height: 100%; overflow: hidden; background-color: #000000; }
                        .container { position: relative; width: 100%; height: 100%; }
                        iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <iframe 
                            id="player"
                            src="https://www.youtube.com/embed/$videoId?enablejsapi=1&playsinline=1&rel=0&modestbranding=1&iv_load_policy=3&origin=$appOrigin" 
                            frameborder="0" 
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" 
                            allowfullscreen 
                            referrerpolicy="strict-origin-when-cross-origin">
                        </iframe>
                    </div>
                    <script>
                        window.addEventListener('message', function(event) {
                            try {
                                var data = (typeof event.data === 'string') ? JSON.parse(event.data) : event.data;
                                if (data && data.event === 'onError') {
                                    if (window.AndroidBridge) {
                                        window.AndroidBridge.onError('' + data.info);
                                    }
                                } else if (data && data.event === 'onReady') {
                                    if (window.AndroidBridge) {
                                        window.AndroidBridge.onReady();
                                    }
                                }
                            } catch (e) {}
                        });
                    </script>
                </body>
                </html>
                """.trimIndent()
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> {
                            webViewRef?.onPause()
                            webViewRef?.pauseTimers()
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            webViewRef?.onResume()
                            webViewRef?.resumeTimers()
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    try {
                        webViewRef?.stopLoading()
                        webViewRef?.loadUrl("about:blank")
                        webViewRef?.destroy()
                    } catch (_: Exception) {}
                    webViewRef = null
                }
            }

            if (hasPlaybackError) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .clickable {
                            com.example.util.CobaltCustomTabsLauncher.launch(
                                context = context,
                                url = trailerUrl,
                                isDarkTheme = true
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                        contentDescription = "Trailer Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Trailer",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = "Watch Trailer on YouTube",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap to open external player",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
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
                                webViewRef = this
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    loadsImagesAutomatically = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                    val defaultUa = userAgentString ?: ""
                                    if (defaultUa.contains("; wv")) {
                                        userAgentString = defaultUa.replace("; wv", "")
                                    }
                                }

                                addJavascriptInterface(
                                    YouTubePlayerBridge(
                                        onReady = { isLoading = false },
                                        onError = { errorCode ->
                                            android.util.Log.w("YouTubeTrailerPlayer", "YouTube embed error: $errorCode")
                                            hasPlaybackError = true
                                            isLoading = false
                                        }
                                    ),
                                    "AndroidBridge"
                                )

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            isLoading = false
                                            hasPlaybackError = true
                                        }
                                    }
                                }
                                loadDataWithBaseURL(appOrigin, embedHtml, "text/html", "UTF-8", null)
                            }
                        },
                        update = { webView ->
                            webViewRef = webView
                            if (webView.tag != videoId) {
                                webView.tag = videoId
                                hasPlaybackError = false
                                isLoading = true
                                webView.loadDataWithBaseURL(appOrigin, embedHtml, "text/html", "UTF-8", null)
                            }
                        },
                        onRelease = { webView ->
                            try {
                                webView.stopLoading()
                                webView.loadUrl("about:blank")
                                webView.destroy()
                            } catch (_: Exception) {}
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
                    com.example.util.CobaltCustomTabsLauncher.launch(
                        context = context,
                        url = trailerUrl,
                        isDarkTheme = true
                    )
                }
                .padding(vertical = 4.dp)
        )
    }
}

fun extractYouTubeVideoId(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
        return trimmed
    }
    val pattern = "(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/" +
            "|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/\\s]{11})"
    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    val matchResult = regex.find(trimmed)
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

fun getStreamingWatchUrl(providerName: String, title: String): String {
    val clean = providerName.lowercase().trim()
    val encoded = Uri.encode(title.trim())
    return when {
        clean.contains("netflix") -> "https://www.netflix.com/search?q=$encoded"
        clean.contains("prime") || clean.contains("amazon") -> "https://www.amazon.com/s?k=$encoded&i=instant-video"
        clean.contains("disney") -> "https://www.disneyplus.com/search?q=$encoded"
        clean.contains("crunchyroll") -> "https://www.crunchyroll.com/search?q=$encoded"
        clean.contains("hulu") -> "https://www.hulu.com/search?q=$encoded"
        clean.contains("apple") || clean.contains("itunes") -> "https://tv.apple.com/search?term=$encoded"
        clean.contains("hbo") || clean.contains("max") -> "https://play.max.com/search?q=$encoded"
        clean.contains("paramount") -> "https://www.paramountplus.com/search/?q=$encoded"
        clean.contains("peacock") -> "https://www.peacocktv.com/search?q=$encoded"
        clean.contains("youtube") -> "https://www.youtube.com/results?search_query=$encoded"
        clean.contains("tubi") -> "https://tubitv.com/search/$encoded"
        clean.contains("pluto") -> "https://pluto.tv/search/details?q=$encoded"
        clean.contains("vudu") || clean.contains("fandango") -> "https://www.vudu.com/content/movies/search?searchString=$encoded"
        else -> "https://www.justwatch.com/us/search?q=$encoded"
    }
}

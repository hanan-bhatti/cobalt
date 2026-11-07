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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.getBestImagePath
import com.example.ui.components.hasTraktIdentity
import com.example.ui.components.MarkdownText
import com.example.ui.theme.*
import com.example.ui.viewmodel.CobaltViewModel
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

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
    val coroutineScope = rememberCoroutineScope()

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
                            text = formattedMediaType.uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
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

        com.example.ui.components.MediaRatingsRow(item.showProgress)

        com.example.ui.components.TraktMediaControls(item, viewModel::showToast)

        // Trakt progress is read only; local-only entries retain their existing status controls.
        if (!item.hasTraktIdentity()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    val updated = item.copy(watchStatus = status)
                                    if (onWatchStatusChanged != null) {
                                        onWatchStatusChanged(status)
                                    } else {
                                        viewModel.updateSavedItem(updated)
                                        viewModel.showToast("Status updated to $status")
                                    }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            when (status.lowercase()) {
                                                "completed" -> {
                                                    if (item.mediaType?.lowercase() != "tv") {
                                                        com.example.data.remote.trakt.TraktSyncEngine.markMovieWatched(context, updated, true)
                                                    }
                                                }
                                                "plan to watch" -> {
                                                    com.example.data.remote.trakt.TraktSyncEngine.toggleWatchlist(context, updated, true)
                                                }
                                                else -> {
                                                    com.example.data.remote.trakt.TraktSyncEngine.toggleWatchlist(context, updated, false)
                                                }
                                            }
                                        } catch (_: Exception) {}
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

        item.mediaFacts?.let { facts ->
            val details = listOfNotNull(facts.episodeCount?.let { "$it episodes" }, facts.chapterCount?.let { "$it chapters" },
                facts.volumeCount?.let { "$it volumes" }, facts.publicationStatus)
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        // Provider logos remain untinted; show prices only when supplied, never infer them.
        val providers = item.richWatchProviders.ifEmpty { item.watchProviders.map { com.example.data.model.MediaWatchProvider(it) } }
            .distinctBy { Triple(it.name, it.type, it.url) }
        if (providers.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Where to watch", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                providers.forEach { provider ->
                    val url = provider.url?.takeIf { Uri.parse(it).scheme == "https" } ?: getStreamingWatchUrl(provider.name, item.title)
                    AssistChip(onClick = { com.example.util.CobaltCustomTabsLauncher.launch(context, url) },
                        leadingIcon = provider.logoUrl?.let { logo -> { AsyncImage(logo, null, Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))) } },
                        label = { Text(listOfNotNull(provider.name, provider.price?.takeIf { it.isNotBlank() } ?: provider.type.takeIf { it != "Stream" }).joinToString(" · ")) })
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
                    text = "Additional Information",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (!item.tagline.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "TAGLINE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = "“${item.tagline}”", style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (item.runtime != null && item.runtime > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(text = "RUNTIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                            Text(text = "${item.runtime} mins", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!item.status.isNullOrBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(text = "STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                            Text(text = item.status, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (item.budget != null && item.budget > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(text = "BUDGET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                            val formattedBudget = java.text.NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(item.budget)
                            Text(text = formattedBudget, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (item.revenue != null && item.revenue > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(text = "REVENUE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                            val formattedRevenue = java.text.NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(item.revenue)
                            Text(text = formattedRevenue, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                
                if (item.productionCompanies.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "PRODUCTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Text(text = item.productionCompanies.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (item.traktId != null || item.tmdbId != null || !item.imdbId.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "EXTERNAL IDS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            item.traktId?.let { Text(text = "Trakt: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item.tmdbId?.let { Text(text = "TMDb: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item.imdbId?.let { Text(text = "IMDb: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouTubeTrailerPlayer(trailerUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val video = remember(trailerUrl) { extractYouTubeVideoId(trailerUrl) }
    val url = video?.let { "https://www.youtube.com/watch?v=$it" } ?: trailerUrl.takeIf {
        val parsed = Uri.parse(it); parsed.scheme == "https" && !parsed.host.isNullOrBlank()
    }
    if (url == null) { Text("Trailer unavailable", style = MaterialTheme.typography.bodySmall); return }
    // YouTube embed restrictions (including error 153) must not strand playback in an app WebView.
    // The installed player/browser handles playback, captions and provider restrictions.
    Surface(onClick = {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context, "No app can open this trailer", android.widget.Toast.LENGTH_SHORT).show() }
    }, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Box(Modifier.aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (video != null) AsyncImage("https://img.youtube.com/vi/$video/hqdefault.jpg", null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.PlayArrow, "Watch trailer", Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

fun extractYouTubeVideoId(url: String): String? {
    val value = url.trim()
    val valid = Regex("[a-zA-Z0-9_-]{11}")
    if (valid.matches(value)) return value
    val uri = Uri.parse(value)
    if (uri.scheme !in listOf("https", "http")) return null
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
    val parts = uri.pathSegments
    val candidate = when {
        host == "youtu.be" -> parts.firstOrNull()
        host in listOf("youtube.com", "m.youtube.com", "youtube-nocookie.com") ->
            if (parts.firstOrNull() in listOf("embed", "shorts", "live", "v")) parts.getOrNull(1) else uri.getQueryParameter("v")
        else -> null
    }
    return candidate?.takeIf { valid.matches(it) }
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

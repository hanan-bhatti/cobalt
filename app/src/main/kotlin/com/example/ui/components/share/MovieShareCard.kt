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

package com.example.ui.components.share

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.getBestImagePath
import com.example.ui.theme.LocalCobaltTypography
import com.example.util.displayTitle
import java.util.Locale

/**
 * Aesthetic Movie & Anime Card for social sharing and high-resolution PNG export.
 * Supports Theatrical Poster (2:3) and Cinematic Backdrop (16:9) styles, handling long titles,
 * overflow genre counts (+N), runtime formatting, and strict exclusion of watch status.
 */
@Composable
fun MovieShareCard(
    item: SavedItem,
    style: MovieShareStyle = MovieShareStyle.THEATRICAL,
    showAttribution: Boolean = true,
    tagline: String = remember { CobaltTaglines.getRandom() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cobaltTypography = LocalCobaltTypography.current

    val posterUrl = remember(item) { item.getBestImagePath() ?: item.thumbnailPath }
    val backdropUrl = remember(item) { item.backdropUrl?.takeIf { it.isNotBlank() } ?: posterUrl }

    val formattedRuntime = remember(item.runtime) {
        val runtimeMin = item.runtime
        if (runtimeMin != null && runtimeMin > 0) {
            val hours = runtimeMin / 60
            val mins = runtimeMin % 60
            if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        } else null
    }

    val formattedMediaType = remember(item.mediaType) {
        when (item.mediaType?.lowercase()) {
            "movie" -> "MOVIE"
            "tv", "tv show", "tv_show" -> "TV SERIES"
            "anime" -> "ANIME"
            else -> item.mediaType?.uppercase(Locale.getDefault()) ?: "MEDIA"
        }
    }

    val titleText = remember(item) {
        item.title.takeIf { it.isNotBlank() } ?: item.displayTitle(context)
    }

    // Outer export frame guaranteeing solid background and high contrast
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(26.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.End
        ) {
            when (style) {
                MovieShareStyle.THEATRICAL -> {
                    TheatricalStyleCard(
                        item = item,
                        title = titleText,
                        mediaType = formattedMediaType,
                        runtime = formattedRuntime,
                        posterUrl = posterUrl,
                        cobaltTypography = cobaltTypography
                    )
                }
                MovieShareStyle.BACKDROP -> {
                    BackdropStyleCard(
                        item = item,
                        title = titleText,
                        mediaType = formattedMediaType,
                        runtime = formattedRuntime,
                        backdropUrl = backdropUrl,
                        cobaltTypography = cobaltTypography
                    )
                }
            }

            // High-contrast footer with Cobalt Logo Badge + Tagline
            if (showAttribution && tagline.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cobalt_logo),
                                contentDescription = "Cobalt",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(12.5.dp)
                            )
                        }
                    }

                    Text(
                        text = tagline,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

/**
 * Style A: Theatrical Poster Card
 * Features a 2:3 vertical theatrical poster with a side metadata column, genre badges with overflow counter (+N),
 * and clean cinematic typography.
 */
@Composable
private fun TheatricalStyleCard(
    item: SavedItem,
    title: String,
    mediaType: String,
    runtime: String?,
    posterUrl: String?,
    cobaltTypography: com.example.ui.theme.CobaltTypography,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val posterModel = remember(posterUrl, context) {
        if (posterUrl.isNullOrBlank()) null
        else {
            ImageRequest.Builder(context)
                .data(posterUrl)
                .size(1600, 2400)
                .precision(Precision.INEXACT)
                .allowHardware(false)
                .crossfade(false)
                .build()
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 2:3 Theatrical Poster
            Box(
                modifier = Modifier
                    .width(106.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (posterModel != null) {
                    AsyncImage(
                        model = posterModel,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_movie),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Movie Information Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Media Type Tag
                Text(
                    text = mediaType,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Title (Handles long titles cleanly)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = cobaltTypography.textFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Year • Runtime • Rating Row
                val metadataParts = buildList {
                    if (!item.releaseYear.isNullOrBlank()) add(item.releaseYear)
                    if (!runtime.isNullOrBlank()) add(runtime)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (metadataParts.isNotEmpty()) {
                        Text(
                            text = metadataParts.joinToString("  •  "),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (item.rating != null && item.rating > 0.0) {
                        if (metadataParts.isNotEmpty()) {
                            Text(
                                text = "•",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        Text(
                            text = "★",
                            color = Color(0xFFFFB300),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", item.rating),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Frameless Genres with Bullet Dots (•)
                if (item.genres.isNotEmpty()) {
                    FramelessGenresText(genres = item.genres, maxVisible = 3)
                }

                // Tagline in Italics
                val taglineText = item.tagline?.takeIf { it.isNotBlank() } ?: item.extractedText?.takeIf { it.isNotBlank() }
                if (!taglineText.isNullOrBlank()) {
                    Text(
                        text = "\"$taglineText\"",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = cobaltTypography.textFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 11.5.sp,
                            lineHeight = 15.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Style B: Cinematic Backdrop Card
 * Features a 16:9 cinematic landscape banner on top, rating star overlay, movie details,
 * and genre badges with overflow counter (+N).
 */
@Composable
private fun BackdropStyleCard(
    item: SavedItem,
    title: String,
    mediaType: String,
    runtime: String?,
    backdropUrl: String?,
    cobaltTypography: com.example.ui.theme.CobaltTypography,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backdropModel = remember(backdropUrl, context) {
        if (backdropUrl.isNullOrBlank()) null
        else {
            ImageRequest.Builder(context)
                .data(backdropUrl)
                .size(2560, 1440)
                .precision(Precision.INEXACT)
                .allowHardware(false)
                .crossfade(false)
                .build()
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 16:9 Widescreen Backdrop Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (backdropModel != null) {
                    AsyncImage(
                        model = backdropModel,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_movie),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Rating Badge Floating Over Backdrop
                if (item.rating != null && item.rating > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("★", color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format(Locale.US, "%.1f", item.rating),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Information Section Below Backdrop
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Media Type & Year & Runtime in one clean top meta row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = mediaType,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!item.releaseYear.isNullOrBlank()) {
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = item.releaseYear,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!runtime.isNullOrBlank()) {
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = runtime,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Title (Handles long titles cleanly)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = cobaltTypography.textFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.5.sp,
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Frameless Genres with Bullet Dots (•)
                if (item.genres.isNotEmpty()) {
                    FramelessGenresText(genres = item.genres, maxVisible = 3)
                }

                // Tagline in Italics
                val taglineText = item.tagline?.takeIf { it.isNotBlank() } ?: item.extractedText?.takeIf { it.isNotBlank() }
                if (!taglineText.isNullOrBlank()) {
                    Text(
                        text = "\"$taglineText\"",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = cobaltTypography.textFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Clean, editorial frameless genres line separated by subtle bullet dots (•),
 * with an accent overflow count (+N) when more than [maxVisible] genres exist.
 */
@Composable
private fun FramelessGenresText(
    genres: List<String>,
    maxVisible: Int = 3,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) return

    val visibleGenres = remember(genres, maxVisible) { genres.take(maxVisible) }
    val extraCount = remember(genres, maxVisible) { (genres.size - maxVisible).coerceAtLeast(0) }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val extraColor = MaterialTheme.colorScheme.primary

    val annotatedString = remember(visibleGenres, extraCount, textColor, dotColor, extraColor) {
        buildAnnotatedString {
            visibleGenres.forEachIndexed { index, genre ->
                if (index > 0) {
                    withStyle(SpanStyle(color = dotColor)) {
                        append("  •  ")
                    }
                }
                withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium)) {
                    append(genre)
                }
            }
            if (extraCount > 0) {
                withStyle(SpanStyle(color = dotColor)) {
                    append("  •  ")
                }
                withStyle(SpanStyle(color = extraColor, fontWeight = FontWeight.Bold)) {
                    append("+$extraCount")
                }
            }
        }
    }

    Text(
        text = annotatedString,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

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

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.getBestImagePath
import com.example.ui.theme.LocalCobaltTypography
import com.example.util.displayTitle

/**
 * Reusable, adaptive-height link card component for sharing and image export.
 * Rendered on a framed background with the Cobalt app icon badge and high-contrast typography.
 */
@Composable
fun LinkShareCard(
    item: SavedItem,
    style: LinkShareStyle = LinkShareStyle.CARD,
    showAttribution: Boolean = true,
    tagline: String = remember(item.id) { CobaltTaglines.getForSeed(item.id) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cobaltTypography = LocalCobaltTypography.current

    val domain = remember(item.content) {
        try {
            val uri = Uri.parse(item.content)
            uri.host?.removePrefix("www.")?.ifBlank { null } ?: item.content.take(32)
        } catch (_: Throwable) {
            item.content.take(32)
        }
    }

    val displayTitle = remember(item) {
        item.linkTitle?.takeIf { it.isNotBlank() }
            ?: item.title.takeIf { it.isNotBlank() && !it.startsWith("http://") && !it.startsWith("https://") }
            ?: item.displayTitle(context)
    }

    val displayDescription = remember(item) {
        item.linkDescription?.takeIf { it.isNotBlank() }
    }

    val imageUrl = remember(item) {
        item.linkImage?.takeIf { it.isNotBlank() } ?: item.getBestImagePath()
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
            // Main Card Surface
            when (style) {
                LinkShareStyle.CARD -> {
                    CardStyleLayout(
                        domain = domain,
                        title = displayTitle,
                        description = displayDescription,
                        url = item.content,
                        imageUrl = imageUrl,
                        cobaltTypography = cobaltTypography
                    )
                }
                LinkShareStyle.LIST -> {
                    ListStyleLayout(
                        domain = domain,
                        title = displayTitle,
                        description = displayDescription,
                        url = item.content,
                        imageUrl = imageUrl,
                        cobaltTypography = cobaltTypography
                    )
                }
            }

            // High-contrast footer with proper Cobalt App Icon Badge + Tagline
            if (showAttribution && tagline.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                ) {
                    // Proper Cobalt App Icon with rounded background container
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
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Card-like layout: Hero media on top, expansive typography, creative frameless domain, adaptive height.
 */
@Composable
private fun CardStyleLayout(
    domain: String,
    title: String,
    description: String?,
    url: String,
    imageUrl: String?,
    cobaltTypography: com.example.ui.theme.CobaltTypography,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Creative Frameless Domain with Accent Dot (No Icon)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Text(
                        text = domain.lowercase(),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // High-contrast Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = cobaltTypography.textFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        lineHeight = 23.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // High-contrast Description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = cobaltTypography.textFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 18.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // High-contrast Canonical URL
                Text(
                    text = url,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * List-like layout: Compact, balanced horizontal presentation with zero wasted dead space.
 * Features 1-line truncated title, 2-line description, and creative frameless domain.
 */
@Composable
private fun ListStyleLayout(
    domain: String,
    title: String,
    description: String?,
    url: String,
    imageUrl: String?,
    cobaltTypography: com.example.ui.theme.CobaltTypography,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.5.dp)
            ) {
                // Creative Frameless Domain with Accent Dot (No Icon)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.5.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Text(
                        text = domain.lowercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 1-Line Truncated Title (Bold & Crisp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = cobaltTypography.textFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 2-Line Description (Rich & Legible)
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = cobaltTypography.textFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // High-contrast Canonical URL
                Text(
                    text = url,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

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

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Reads intrinsic dimensions from file headers with minimal memory footprint (0 MB decode).
 * Safely handles 100MP and 1GB files without OOM crashes.
 */
fun resolveImageDimensions(context: Context, path: String?): Pair<Int, Int>? {
    if (path.isNullOrBlank()) return null
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } else if (!path.startsWith("http://") && !path.startsWith("https://")) {
            val file = java.io.File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath, options)
            }
        }
        if (options.outWidth > 0 && options.outHeight > 0) {
            val rotation = getImageRotation(context, path)
            if (rotation == 90 || rotation == 270) {
                Pair(options.outHeight, options.outWidth)
            } else {
                Pair(options.outWidth, options.outHeight)
            }
        } else null
    } catch (_: Throwable) {
        null
    }
}

/**
 * Extracts EXIF rotation tag to detect whether portrait photos require width/height swapping.
 */
fun getImageRotation(context: Context, path: String?): Int {
    if (path.isNullOrBlank()) return 0
    return try {
        val exif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && path.startsWith("content://")) {
            val uri = Uri.parse(path)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.media.ExifInterface(stream)
            }
        } else if (!path.startsWith("http://") && !path.startsWith("https://") && !path.startsWith("content://")) {
            android.media.ExifInterface(path)
        } else null

        when (exif?.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Throwable) {
        0
    }
}

/**
 * Aesthetic, adaptive-aspect-ratio image card component for sharing and high-resolution PNG export.
 * Implements a classic Polaroid / Instant Print design that fully adapts to the photo's aspect ratio.
 */
@Composable
fun ImageShareCard(
    item: SavedItem,
    style: ImageShareStyle = ImageShareStyle.POLAROID,
    showAttribution: Boolean = true,
    tagline: String = remember { CobaltTaglines.getRandom() },
    customCaption: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cobaltTypography = LocalCobaltTypography.current
    val imagePath = remember(item) { item.getBestImagePath() }

    val captionText = remember(item, customCaption) {
        if (!customCaption.isNullOrBlank()) customCaption
        else {
            val title = item.title.takeIf { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("/") }
            val desc = item.extractedText?.takeIf { it.isNotBlank() }
            title ?: desc ?: item.displayTitle(context)
        }
    }

    val initialDimensions = remember(imagePath, context) {
        resolveImageDimensions(context, imagePath)
    }

    // Fully adapt to the image's intrinsic aspect ratio across a wide free-form range (0.2f to 5.0f)
    var adaptiveAspectRatio by remember(imagePath, initialDimensions) {
        val initialRatio = initialDimensions?.let { (w, h) ->
            if (w > 0 && h > 0) (w.toFloat() / h.toFloat()).coerceIn(0.2f, 5.0f) else null
        } ?: 1f
        mutableFloatStateOf(initialRatio)
    }

    val imageModel = remember(imagePath, context) {
        if (imagePath.isNullOrBlank()) null
        else {
            ImageRequest.Builder(context)
                .data(imagePath)
                .size(2560, 2560) // Safely downsamples 100MP / 1GB photos to prevent OutOfMemoryError
                .precision(Precision.INEXACT)
                .allowHardware(false) // Essential for canvas export compatibility
                .crossfade(false)
                .build()
        }
    }

    val cardShape = if (style == ImageShareStyle.POLAROID) RectangleShape else RoundedCornerShape(20.dp)

    // Outer export frame guaranteeing solid background and high contrast
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = cardShape,
        modifier = modifier.fillMaxWidth()
    ) {
        when (style) {
            ImageShareStyle.POLAROID -> {
                PolaroidStyleCard(
                    imageModel = imageModel,
                    aspectRatio = adaptiveAspectRatio,
                    caption = captionText,
                    showAttribution = showAttribution,
                    tagline = tagline,
                    cobaltTypography = cobaltTypography,
                    onImageDimensionsResolved = { w, h ->
                        if (w > 0 && h > 0) {
                            adaptiveAspectRatio = (w.toFloat() / h.toFloat()).coerceIn(0.2f, 5.0f)
                        }
                    }
                )
            }
            ImageShareStyle.MINIMAL -> {
                MinimalStyleCard(
                    imageModel = imageModel,
                    aspectRatio = adaptiveAspectRatio,
                    caption = captionText,
                    showAttribution = showAttribution,
                    tagline = tagline,
                    cobaltTypography = cobaltTypography,
                    onImageDimensionsResolved = { w, h ->
                        if (w > 0 && h > 0) {
                            adaptiveAspectRatio = (w.toFloat() / h.toFloat()).coerceIn(0.2f, 5.0f)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Classic Polaroid Instant Print Layout:
 * Features authentic sharp corners (no rounding), equal top/side margins, and a generous bottom chin
 * with caption and attribution pinned to the bottom right.
 */
@Composable
private fun PolaroidStyleCard(
    imageModel: Any?,
    aspectRatio: Float,
    caption: String,
    showAttribution: Boolean,
    tagline: String,
    cobaltTypography: com.example.ui.theme.CobaltTypography,
    onImageDimensionsResolved: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var isLoadError by remember { mutableStateOf(false) }

            // Adaptive Photo Print Container (Authentic sharp 90-degree corners)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RectangleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null && !isLoadError) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = caption,
                        contentScale = ContentScale.Crop,
                        onSuccess = { state ->
                            val drawable = state.result.drawable
                            onImageDimensionsResolved(
                                drawable.intrinsicWidth,
                                drawable.intrinsicHeight
                            )
                        },
                        onError = { isLoadError = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_image),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(44.dp)
                        )
                        if (isLoadError) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Image unavailable",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Polaroid Chin: Caption & Attribution pinned to bottom right
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = cobaltTypography.textFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showAttribution && tagline.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.size(17.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_cobalt_logo),
                                    contentDescription = "Cobalt",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = tagline,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Minimal Modern Frame Layout:
 * Refined presentation with edge-to-edge photo, sleek rounded corners, and attribution pinned to the bottom right.
 */
@Composable
private fun MinimalStyleCard(
    imageModel: Any?,
    aspectRatio: Float,
    caption: String,
    showAttribution: Boolean,
    tagline: String,
    cobaltTypography: com.example.ui.theme.CobaltTypography,
    onImageDimensionsResolved: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            var isLoadError by remember { mutableStateOf(false) }

            // Edge-to-edge photo with top rounded corners
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null && !isLoadError) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = caption,
                        contentScale = ContentScale.Crop,
                        onSuccess = { state ->
                            val drawable = state.result.drawable
                            onImageDimensionsResolved(
                                drawable.intrinsicWidth,
                                drawable.intrinsicHeight
                            )
                        },
                        onError = { isLoadError = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_image),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(44.dp)
                        )
                        if (isLoadError) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Image unavailable",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Minimal metadata footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = cobaltTypography.textFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showAttribution && tagline.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.size(17.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_cobalt_logo),
                                    contentDescription = "Cobalt",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = tagline,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SavedItem
import com.example.ui.components.MarkdownText
import com.example.ui.components.parseSafeMarkdown
import com.example.ui.theme.CobaltTypography
import com.example.ui.theme.LocalCobaltTypography

/**
 * Scales CobaltTypography proportionally for card formats (A4 Document, Sticky Note, Quote).
 * Preserves the user's chosen text and code font families, line height ratio, and letter spacing.
 */
private fun CobaltTypography.scaleForCard(targetBaseSize: Float): CobaltTypography {
    val lineMultiplier = lineHeightMultiplier
    val paraSpacing = (paragraphSpacingDp.value * (targetBaseSize / 16f)).coerceIn(4f, 8f).dp
    val letterSpacingSp = targetBaseSize * (prefs.letterSpacing)

    val baseBody = TextStyle(
        fontFamily = textFontFamily,
        fontWeight = body.fontWeight ?: FontWeight.Normal,
        fontSize = targetBaseSize.sp,
        lineHeight = (targetBaseSize * lineMultiplier).sp,
        letterSpacing = letterSpacingSp.sp,
        textDirection = TextDirection.Content
    )
    val bodyMedium = baseBody.copy(
        fontSize = (targetBaseSize * 0.9375f).sp,
        lineHeight = (targetBaseSize * 0.9375f * lineMultiplier).sp
    )
    val bodySmall = baseBody.copy(
        fontSize = (targetBaseSize * 0.75f).sp,
        lineHeight = (targetBaseSize * 0.75f * lineMultiplier).sp
    )
    val h1 = TextStyle(
        fontFamily = textFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (targetBaseSize * 1.45f).sp,
        lineHeight = (targetBaseSize * 1.45f * 1.25f).sp,
        letterSpacing = letterSpacingSp.sp,
        textDirection = TextDirection.Content
    )
    val h2 = TextStyle(
        fontFamily = textFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (targetBaseSize * 1.28f).sp,
        lineHeight = (targetBaseSize * 1.28f * 1.25f).sp,
        letterSpacing = letterSpacingSp.sp,
        textDirection = TextDirection.Content
    )
    val h3 = TextStyle(
        fontFamily = textFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (targetBaseSize * 1.14f).sp,
        lineHeight = (targetBaseSize * 1.14f * 1.25f).sp,
        letterSpacing = letterSpacingSp.sp,
        textDirection = TextDirection.Content
    )
    val h4 = TextStyle(
        fontFamily = textFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = targetBaseSize.sp,
        lineHeight = (targetBaseSize * 1.25f).sp,
        letterSpacing = letterSpacingSp.sp,
        textDirection = TextDirection.Content
    )
    val codeSize = (targetBaseSize * 0.88f).sp
    val inlineCode = TextStyle(
        fontFamily = codeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = codeSize,
        lineHeight = (targetBaseSize * 0.88f * 1.3f).sp,
        textDirection = TextDirection.Ltr
    )
    val codeBlock = TextStyle(
        fontFamily = codeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = codeSize,
        lineHeight = (targetBaseSize * 0.88f * 1.4f).sp,
        textDirection = TextDirection.Ltr
    )
    val quote = baseBody.copy(
        fontStyle = FontStyle.Italic,
        lineHeight = (targetBaseSize * lineMultiplier * 1.05f).sp
    )

    return CobaltTypography(
        prefs = prefs,
        textFontFamily = textFontFamily,
        codeFontFamily = codeFontFamily,
        body = baseBody,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        h1 = h1,
        h2 = h2,
        h3 = h3,
        h4 = h4,
        inlineCode = inlineCode,
        codeBlock = codeBlock,
        quote = quote,
        checklist = baseBody,
        paragraphSpacingDp = paraSpacing,
        lineHeightMultiplier = lineMultiplier,
        letterSpacingSp = letterSpacingSp
    )
}

/**
 * Aesthetic Note Card component for social sharing and 4K high-resolution PNG export.
 * Features 3 distinct layouts:
 * 1. Document (Sharp paper corners, A4 proportions, centered top title, no metadata clutter, continuation ellipsis)
 * 2. Minimal (Sticky note square size, small book header at top-left, clean memo body, no metadata)
 * 3. Compact (Small wide quote format, NO HEADING, focused quote presentation)
 */
@Composable
fun NoteShareCard(
    item: SavedItem,
    noteBody: String,
    style: NoteShareStyle = NoteShareStyle.DOCUMENT,
    showAttribution: Boolean = true,
    tagline: String = remember { CobaltTaglines.getRandom() },
    modifier: Modifier = Modifier
) {
    val currentTypography = LocalCobaltTypography.current
    val cardTypography = remember(currentTypography, style) {
        val targetBaseSize = when (style) {
            NoteShareStyle.DOCUMENT -> 10.5f
            NoteShareStyle.MINIMAL -> 11.5f
            NoteShareStyle.COMPACT -> 13.5f
        }
        currentTypography.scaleForCard(targetBaseSize)
    }

    val displayTitle = remember(item.title, noteBody) {
        val t = item.title.trim()
        val cleanedFirstLine = noteBody.lines().firstOrNull { it.isNotBlank() }
            ?.replace(Regex("^#+\\s*"), "")
            ?.replace(Regex("^>+\\s*"), "")
            ?.trim()
            ?.take(80)

        if (t.isNotBlank() && !t.startsWith("http") && !t.startsWith("/")) {
            val fullBodyCleaned = noteBody.trim().removePrefix(">").trim()
            if (t.equals(fullBodyCleaned, ignoreCase = true) || (t.equals(cleanedFirstLine, ignoreCase = true) && noteBody.lines().count { it.isNotBlank() } <= 1)) {
                null
            } else {
                t
            }
        } else {
            val restOfBody = noteBody.lines().filter { it.isNotBlank() }.drop(1).joinToString(" ").trim()
            if (restOfBody.isBlank()) null else cleanedFirstLine
        }
    }

    val cardWidthModifier = when (style) {
        NoteShareStyle.DOCUMENT -> Modifier.width(280.dp)
        NoteShareStyle.MINIMAL -> Modifier.width(280.dp)
        NoteShareStyle.COMPACT -> Modifier.width(310.dp)
    }

    val outerCardShape = when (style) {
        NoteShareStyle.DOCUMENT -> RoundedCornerShape(3.dp)
        NoteShareStyle.MINIMAL -> RoundedCornerShape(16.dp)
        NoteShareStyle.COMPACT -> RoundedCornerShape(16.dp)
    }

    val outerCardPadding = when (style) {
        NoteShareStyle.DOCUMENT -> PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)
        NoteShareStyle.MINIMAL -> PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
        NoteShareStyle.COMPACT -> PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
    }

    // Outer export frame
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = outerCardShape,
        modifier = modifier
            .then(cardWidthModifier)
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(outerCardPadding),
            horizontalAlignment = Alignment.End
        ) {
            CompositionLocalProvider(
                LocalCobaltTypography provides cardTypography
            ) {
                when (style) {
                    NoteShareStyle.DOCUMENT -> {
                        EditorialNoteCard(
                            title = displayTitle,
                            noteBody = noteBody,
                            
                            cobaltTypography = cardTypography
                        )
                    }
                    NoteShareStyle.MINIMAL -> {
                        MinimalNoteCard(
                            title = displayTitle,
                            noteBody = noteBody,
                            
                            cobaltTypography = cardTypography
                        )
                    }
                    NoteShareStyle.COMPACT -> {
                        CompactNoteCard(
                            noteBody = noteBody,
                            cobaltTypography = cardTypography
                        )
                    }
                }
            }

            // High-contrast footer with Cobalt Logo Badge + Tagline at bottom right
            if (showAttribution && tagline.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.size(18.dp)
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

                    Text(
                        text = tagline,
                        fontSize = 11.sp,
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
 * Style 1: Editorial Document Card
 * Sharp corners, standard A4 proportion (1 : 1.414), centered top title,
 * full native Markdown body with continuation indicator (...), and zero metadata clutter.
 */
@Composable
private fun EditorialNoteCard(
    title: String?,
    noteBody: String,
    cobaltTypography: CobaltTypography,
    modifier: Modifier = Modifier
) {
    val truncation = remember(noteBody) { computeA4Content(noteBody, maxLines = 14) }
    val contentScroll = rememberScrollState()
    val pageDoc = remember(truncation.content) {
        parseSafeMarkdown(truncation.content)
    }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f / 1.414f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Centered Top Title (if present)
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = cobaltTypography.textFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        lineHeight = 21.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            // Native Markdown Engine Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                MarkdownText(
                    markdown = truncation.content,
                    prepared = pageDoc,
                    modifier = Modifier.fillMaxWidth().verticalScroll(contentScroll, enabled = false)
                )
            }

            // Continuation ellipsis at bottom when note exceeds A4 page
            if (truncation.hasContinuation || contentScroll.maxValue > 0) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * Style 2: Minimalist Clean Sticky Note Memo Card
 * Square sticky note geometry (1:1), top-left small book heading,
 * distraction-free content with soft bottom fade and continuation, zero metadata.
 */
@Composable
private fun MinimalNoteCard(
    title: String?,
    noteBody: String,
    cobaltTypography: CobaltTypography,
    modifier: Modifier = Modifier
) {
    val truncation = remember(noteBody) { computeMinimalContent(noteBody, maxLines = 8) }
    val contentScroll = rememberScrollState()
    val truncatedDoc = remember(truncation.truncatedMarkdown) {
        parseSafeMarkdown(truncation.truncatedMarkdown)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Book-style small heading at top-left
            if (!title.isNullOrBlank()) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = cobaltTypography.textFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }

            // Note Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                MarkdownText(
                    markdown = truncation.truncatedMarkdown,
                    prepared = truncatedDoc,
                    modifier = Modifier.fillMaxWidth().verticalScroll(contentScroll, enabled = false)
                )

                if (truncation.hasContinuation || contentScroll.maxValue > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    0.5f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    1.0f to MaterialTheme.colorScheme.surface
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Style 3: Compact Small Wide Card
 * Small, focused card just tall enough for medium-sized quotes and insights.
 * Features NO TITLE heading, elegant italic quote styling, and zero metadata clutter.
 */
@Composable
private fun CompactNoteCard(
    noteBody: String,
    cobaltTypography: CobaltTypography,
    modifier: Modifier = Modifier
) {
    val excerpt = remember(noteBody) { compactNoteExcerpt(noteBody) }
    val compactText = excerpt.text

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 180.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val formattedQuote = remember(compactText, excerpt.isQuote) {
                if (!excerpt.isQuote || compactText.startsWith("\"") || compactText.startsWith("“")) compactText
                else "“$compactText”"
            }
            Text(
                text = formattedQuote,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = cobaltTypography.textFontFamily,
                    fontStyle = if (excerpt.isQuote) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.5.sp,
                    lineHeight = 23.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

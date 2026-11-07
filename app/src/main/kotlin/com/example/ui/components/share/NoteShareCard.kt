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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.ui.components.MarkdownText
import com.example.ui.components.SafeMarkdownDocument
import com.example.ui.components.parseSafeMarkdown
import com.example.ui.theme.CobaltTypography
import com.example.ui.theme.LocalCobaltTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aesthetic Note Card component for social sharing and 4K high-resolution PNG export.
 * Features 3 distinct layouts powered by Cobalt's native Markdown engine:
 * 1. Document (Editorial layout with metadata header, bold title, divider, complete Markdown document)
 * 2. Minimal (Clean, distraction-free memo presentation with prominent title and pure Markdown body)
 * 3. Compact (Punchy card format optimized for sharing to Twitter/X, Instagram, or WhatsApp)
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
    val cobaltTypography = LocalCobaltTypography.current
    val preparedDoc = remember(noteBody) { parseSafeMarkdown(noteBody) }

    val wordCount = remember(noteBody) {
        noteBody.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    val readingTimeMin = remember(wordCount) {
        (wordCount / 200).coerceAtLeast(1)
    }

    val formattedDate = remember(item.timestamp) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    val displayTitle = remember(item.title, noteBody) {
        val t = item.title.trim()
        if (t.isNotBlank() && !t.startsWith("http") && !t.startsWith("/")) {
            t
        } else {
            // Derive title from first line of note
            noteBody.lines().firstOrNull { it.isNotBlank() }
                ?.replace(Regex("^#+\\s*"), "")
                ?.trim()
                ?.take(80)
        }
    }

    val cardWidthModifier = when (style) {
        NoteShareStyle.COMPACT -> Modifier.widthIn(min = 280.dp, max = 380.dp)
        NoteShareStyle.MINIMAL -> Modifier.widthIn(min = 320.dp, max = 500.dp)
        NoteShareStyle.DOCUMENT -> Modifier.widthIn(min = 340.dp, max = 560.dp)
    }

    // Outer export frame with variable adaptive width
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(26.dp),
        modifier = modifier
            .then(cardWidthModifier)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.End
        ) {
            when (style) {
                NoteShareStyle.DOCUMENT -> {
                    EditorialNoteCard(
                        item = item,
                        title = displayTitle,
                        noteBody = noteBody,
                        preparedDoc = preparedDoc,
                        wordCount = wordCount,
                        readingTimeMin = readingTimeMin,
                        formattedDate = formattedDate,
                        cobaltTypography = cobaltTypography
                    )
                }
                NoteShareStyle.MINIMAL -> {
                    MinimalNoteCard(
                        item = item,
                        title = displayTitle,
                        noteBody = noteBody,
                        preparedDoc = preparedDoc,
                        formattedDate = formattedDate,
                        cobaltTypography = cobaltTypography
                    )
                }
                NoteShareStyle.COMPACT -> {
                    CompactNoteCard(
                        item = item,
                        title = displayTitle,
                        noteBody = noteBody,
                        preparedDoc = preparedDoc,
                        wordCount = wordCount,
                        formattedDate = formattedDate,
                        cobaltTypography = cobaltTypography
                    )
                }
            }

            // High-contrast footer with Cobalt Logo Badge + Pinned Tagline at bottom right
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
 * Style 1: Editorial Document Card
 * Structured document layout featuring category pill, creation date, reading metrics,
 * bold title with subtle divider, and Cobalt's full native markdown engine.
 */
@Composable
private fun EditorialNoteCard(
    item: SavedItem,
    title: String?,
    noteBody: String,
    preparedDoc: SafeMarkdownDocument?,
    wordCount: Int,
    readingTimeMin: Int,
    formattedDate: String,
    cobaltTypography: CobaltTypography,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Category • Date • Word Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (item.type == SavedItemType.CODE) "CODE" else "NOTE",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = formattedDate,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (wordCount > 0) {
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "$wordCount words",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Title (if present and distinct)
            if (!title.isNullOrBlank()) {
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

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            // Native Markdown Engine (Tables, Code Blocks, Blockquotes, Lists, Spans)
            MarkdownText(
                markdown = noteBody,
                prepared = preparedDoc,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Style 2: Minimalist Clean Memo Card
 * Distraction-free, pure focus on the note content with no top metadata pill bar or heavy divider.
 * Clean, generous typography and subtle timestamp footer.
 */
@Composable
private fun MinimalNoteCard(
    item: SavedItem,
    title: String?,
    noteBody: String,
    preparedDoc: SafeMarkdownDocument?,
    formattedDate: String,
    cobaltTypography: CobaltTypography,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title: Clean prominent title without clutter
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = cobaltTypography.textFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Native Markdown Engine
            MarkdownText(
                markdown = noteBody,
                prepared = preparedDoc,
                modifier = Modifier.fillMaxWidth()
            )

            // Discreet Date Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Style 3: Compact Punchy Card
 * Tighter, high-density format optimized for sharing to Twitter/X, Instagram, or WhatsApp.
 * Compact padding, single-row metadata header, prominent title, and focused excerpt.
 */
@Composable
private fun CompactNoteCard(
    item: SavedItem,
    title: String?,
    noteBody: String,
    preparedDoc: SafeMarkdownDocument?,
    wordCount: Int,
    formattedDate: String,
    cobaltTypography: CobaltTypography,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact Header: Type badge & Date in one sleek row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (item.type == SavedItemType.CODE) "CODE" else "NOTE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formattedDate,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            // Title: High-impact punchy title
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Focused Markdown Excerpt
            MarkdownText(
                markdown = noteBody,
                prepared = preparedDoc,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )

            if (wordCount > 60) {
                Text(
                    text = "$wordCount words total",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

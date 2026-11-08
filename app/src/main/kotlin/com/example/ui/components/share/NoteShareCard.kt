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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * Truncation result for Minimal Note memo layout.
 */
internal data class MinimalTruncation(
    val truncatedMarkdown: String,
    val remainingLines: Int
)

/**
 * Extracts a concise excerpt for the Compact style, specifically tailored for small notes.
 * Extracts quotes, one-liners, or the primary opening thought without heavy headers or tables.
 */
internal fun extractCompactNoteContent(noteBody: String): String {
    val clean = noteBody.trim()
    if (clean.isBlank()) return ""

    // 1. If the note is already short and not a complex markdown structure
    val lines = clean.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size <= 3 && lines.none { it.startsWith("|") || it.startsWith("#") }) {
        return clean.removePrefix(">").trim()
    }

    // 2. If the note contains a blockquote, quotes make perfect compact punchy cards
    val quoteLines = lines.filter { it.startsWith(">") }
    if (quoteLines.isNotEmpty()) {
        return quoteLines.joinToString(" ") { it.removePrefix(">").trim() }.take(180)
    }

    // 3. Extract the first meaningful body paragraph (skipping raw headers, table bars, or list markers)
    val bodyParagraph = lines.firstOrNull { line ->
        !line.startsWith("#") &&
        !line.startsWith("|") &&
        !line.startsWith("-") &&
        !line.startsWith("*") &&
        !line.startsWith("```")
    }

    if (!bodyParagraph.isNullOrBlank()) {
        return bodyParagraph.take(180)
    }

    // 4. Fallback: clean the first line of markdown symbols
    val firstLine = lines.firstOrNull() ?: clean
    return firstLine.replace(Regex("^#+\\s*"), "").replace(Regex("^>+\\s*"), "").take(140)
}

/**
 * Computes line-limited content for the Minimal memo card with line counting and soft fade.
 */
internal fun computeMinimalContent(noteBody: String, maxLines: Int = 7): MinimalTruncation {
    val clean = noteBody.trim()
    if (clean.isBlank()) return MinimalTruncation("", 0)

    val lines = clean.lines()
    val nonBlankLines = lines.filter { it.isNotBlank() }

    // If explicit non-blank lines exceed maxLines
    if (nonBlankLines.size > maxLines) {
        var count = 0
        var cutIndex = 0
        for ((index, line) in lines.withIndex()) {
            if (line.isNotBlank()) count++
            if (count == maxLines) {
                cutIndex = index + 1
                break
            }
        }
        val preview = lines.take(cutIndex).joinToString("\n").trimEnd()
        val remaining = nonBlankLines.size - maxLines
        return MinimalTruncation(preview, remaining)
    }

    // If there are few explicit lines but words wrap heavily
    val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    val wordsPerLineEstimate = 9
    val estimatedLines = words.size / wordsPerLineEstimate
    if (estimatedLines > maxLines && words.size > maxLines * wordsPerLineEstimate) {
        val keepWords = maxLines * wordsPerLineEstimate
        val preview = words.take(keepWords).joinToString(" ")
        val remaining = maxOf(1, (words.size - keepWords) / wordsPerLineEstimate)
        return MinimalTruncation(preview, remaining)
    }

    return MinimalTruncation(clean, 0)
}

/**
 * Aesthetic Note Card component for social sharing and 4K high-resolution PNG export.
 * Features 3 distinct layouts powered by Cobalt's native Markdown engine:
 * 1. Document (Editorial layout with metadata header, bold title, divider, complete Markdown document)
 * 2. Minimal (Clean, distraction-free memo presentation with prominent title, line limitation, and soft fade with +N more lines)
 * 3. Compact (Small wide card format with NO TITLE, optimized for small notes and quick punchy sharing)
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
        val cleanedFirstLine = noteBody.lines().firstOrNull { it.isNotBlank() }
            ?.replace(Regex("^#+\\s*"), "")
            ?.replace(Regex("^>+\\s*"), "")
            ?.trim()
            ?.take(80)

        if (t.isNotBlank() && !t.startsWith("http") && !t.startsWith("/")) {
            // Avoid duplicate header if the title is identical to the single quote or body line
            val fullBodyCleaned = noteBody.trim().removePrefix(">").trim()
            if (t.equals(fullBodyCleaned, ignoreCase = true) || (t.equals(cleanedFirstLine, ignoreCase = true) && noteBody.lines().count { it.isNotBlank() } <= 1)) {
                null
            } else {
                t
            }
        } else {
            // Derived from note body - only show if there is more content following it
            val restOfBody = noteBody.lines().filter { it.isNotBlank() }.drop(1).joinToString(" ").trim()
            if (restOfBody.isBlank()) null else cleanedFirstLine
        }
    }

    val cardWidthModifier = when (style) {
        NoteShareStyle.COMPACT -> Modifier.widthIn(min = 300.dp, max = 460.dp)
        NoteShareStyle.MINIMAL -> Modifier.widthIn(min = 320.dp, max = 480.dp)
        NoteShareStyle.DOCUMENT -> Modifier.widthIn(min = 340.dp, max = 620.dp)
    }

    val outerCardPadding = when (style) {
        NoteShareStyle.DOCUMENT -> PaddingValues(horizontal = 8.dp, vertical = 10.dp)
        NoteShareStyle.MINIMAL -> PaddingValues(horizontal = 12.dp, vertical = 12.dp)
        NoteShareStyle.COMPACT -> PaddingValues(horizontal = 12.dp, vertical = 10.dp)
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
                .padding(outerCardPadding),
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
 * Structured document layout featuring creation date, reading metrics,
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
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Date • Word Count • Reading Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "$readingTimeMin min read",
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
                        fontSize = 18.sp,
                        lineHeight = 23.sp
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
 * Distraction-free, pure focus on the note content with no top metadata bar or divider.
 * Keeps a calibrated line limit and renders a soft fade with "+N more lines" when content overflows.
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
    val truncation = remember(noteBody) { computeMinimalContent(noteBody, maxLines = 7) }
    val truncatedDoc = remember(truncation.truncatedMarkdown) {
        if (truncation.truncatedMarkdown == noteBody) preparedDoc else parseSafeMarkdown(truncation.truncatedMarkdown)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Clean prominent title without clutter (if present)
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

            // Note Content with soft fade + "+N more lines" when truncated
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                MarkdownText(
                    markdown = truncation.truncatedMarkdown,
                    prepared = truncatedDoc,
                    modifier = Modifier.fillMaxWidth()
                )

                if (truncation.remainingLines > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    0.45f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    1.0f to MaterialTheme.colorScheme.surface
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = "+${truncation.remainingLines} more lines",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.2.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

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
 * Style 3: Compact Small Wide Card
 * Specially designed for small notes, quotes, punchy one-liners, and short insights.
 * Features a wide landscape geometry, compact vertical height, NO TITLE header,
 * clean typography, and a discreet metadata footer.
 */
@Composable
private fun CompactNoteCard(
    item: SavedItem,
    noteBody: String,
    preparedDoc: SafeMarkdownDocument?,
    wordCount: Int,
    formattedDate: String,
    cobaltTypography: CobaltTypography,
    modifier: Modifier = Modifier
) {
    val compactText = remember(noteBody) { extractCompactNoteContent(noteBody) }
    val compactWords = remember(compactText) {
        compactText.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Content: Focused markdown text with NO TITLE (tailored for small notes)
            MarkdownText(
                markdown = compactText,
                modifier = Modifier.fillMaxWidth()
            )

            // Compact Bottom Row: Discreet Date & Word Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                if (compactWords > 25) {
                    Text(
                        text = "$compactWords words",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

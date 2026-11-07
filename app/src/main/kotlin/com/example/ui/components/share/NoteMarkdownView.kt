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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalCobaltTypography

/**
 * Lightweight, dedicated Markdown and Table renderer for Cobalt Note Share Cards.
 * Supports headings, code blocks, blockquotes, bullet lists, inline styles,
 * and compact responsive tables (fitting 3-4 columns cleanly without overflow).
 */
@Composable
fun NoteMarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    maxVisibleBlocks: Int = 18,
    isDark: Boolean = false
) {
    val typography = LocalCobaltTypography.current
    val parsedBlocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val visibleBlocks = remember(parsedBlocks, maxVisibleBlocks) { parsedBlocks.take(maxVisibleBlocks) }
    val hiddenCount = (parsedBlocks.size - maxVisibleBlocks).coerceAtLeast(0)

    val wordCount = remember(markdown) {
        markdown.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        visibleBlocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Text(
                        text = block.text,
                        fontSize = when (block.level) {
                            1 -> 16.5.sp
                            2 -> 15.sp
                            else -> 13.5.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = typography.textFontFamily,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = formatInlineMarkdown(block.text, MaterialTheme.colorScheme.primary),
                        fontSize = 12.5.sp,
                        lineHeight = 17.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                        fontFamily = typography.textFontFamily
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(block.text, MaterialTheme.colorScheme.primary),
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            fontFamily = typography.textFontFamily,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}.",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(block.text, MaterialTheme.colorScheme.primary),
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            fontFamily = typography.textFontFamily,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = block.text,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = typography.textFontFamily,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                is MarkdownBlock.Table -> {
                    CompactMarkdownTable(table = block)
                }
            }
        }

        // Elegant overflow badge if note is long
        if (hiddenCount > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
            ) {
                Text(
                    text = "+$hiddenCount more sections • $wordCount words total",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Compact, responsive Markdown table layout that gracefully fits 3-4 columns
 * without overflowing or requiring horizontal scrolling.
 */
@Composable
fun CompactMarkdownTable(
    table: MarkdownBlock.Table,
    modifier: Modifier = Modifier
) {
    val columnCount = table.headers.size.coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                table.headers.forEach { header ->
                    Text(
                        text = header.trim(),
                        fontSize = if (columnCount >= 4) 10.5.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Data Rows
            table.rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until columnCount) {
                        val cellText = row.getOrNull(i)?.trim().orEmpty()
                        Text(
                            text = cellText,
                            fontSize = if (columnCount >= 4) 10.5.sp else 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parses markdown string into a structured list of blocks.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val text: String) : MarkdownBlock
    data class NumberedItem(val number: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String? = null) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
}

fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isBlank()) {
            i++
            continue
        }

        // Code block
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language))
            continue
        }

        // Table detection (Line contains pipes and next line has dashes)
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && lines[i + 1].trim().contains("---")) {
            val headers = trimmed.split("|").filter { it.isNotBlank() }.map { it.trim() }
            i += 2 // skip header and divider
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                val rowCells = lines[i].trim().split("|").filter { it.isNotBlank() }.map { it.trim() }
                rows.add(rowCells)
                i++
            }
            blocks.add(MarkdownBlock.Table(headers, rows))
            continue
        }

        // Headings
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            val text = trimmed.drop(level).trim()
            blocks.add(MarkdownBlock.Heading(level.coerceIn(1, 3), text))
            i++
            continue
        }

        // Bullet lists
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            val text = trimmed.drop(2).trim()
            blocks.add(MarkdownBlock.BulletItem(text))
            i++
            continue
        }

        // Numbered lists
        val numberMatch = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
        if (numberMatch != null) {
            val number = numberMatch.groupValues[1].toIntOrNull() ?: 1
            val text = numberMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.NumberedItem(number, text))
            i++
            continue
        }

        // Blockquotes
        if (trimmed.startsWith(">")) {
            val text = trimmed.removePrefix(">").trim()
            blocks.add(MarkdownBlock.Quote(text))
            i++
            continue
        }

        // Regular paragraph
        blocks.add(MarkdownBlock.Paragraph(trimmed))
        i++
    }

    return blocks
}

/**
 * Formats inline bold, italic, and code markdown tags into an AnnotatedString.
 */
fun formatInlineMarkdown(text: String, primaryColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*\\*|__)(.*?)\\1|(\\*|_)(.*?)\\3|(`)(.*?)\\5")
        val matches = regex.findAll(text)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            when {
                // Bold (**text** or __text__)
                match.value.startsWith("**") || match.value.startsWith("__") -> {
                    val inner = match.value.substring(2, match.value.length - 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(inner)
                    }
                }
                // Inline code (`code`)
                match.value.startsWith("`") -> {
                    val inner = match.value.substring(1, match.value.length - 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = primaryColor)) {
                        append(inner)
                    }
                }
                // Italic (*text* or _text_)
                match.value.startsWith("*") || match.value.startsWith("_") -> {
                    val inner = match.value.substring(1, match.value.length - 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inner)
                    }
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

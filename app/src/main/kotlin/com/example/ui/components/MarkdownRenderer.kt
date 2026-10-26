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

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import java.util.concurrent.atomic.AtomicInteger
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import com.example.util.CobaltCustomTabsLauncher
import com.example.utils.isRtlText

/**
 * Toggles a checklist task item between unchecked `- [ ]` and checked `- [x]`.
 * Accurately tracks line index outside fenced code blocks to match AST traversal.
 */
fun toggleTaskListItem(markdown: String, targetTaskIndex: Int): String {
    val lines = markdown.lines().toMutableList()
    var inCodeBlock = false
    var currentTaskIndex = 0

    val taskRegex = Regex("""^(\s*[-*+]|\s*\d+\.)\s+\[([ xX])\](.*)$""")

    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            continue
        }
        if (!inCodeBlock) {
            val match = taskRegex.matchEntire(line)
            if (match != null) {
                if (currentTaskIndex == targetTaskIndex) {
                    val prefix = match.groupValues[1]
                    val status = match.groupValues[2]
                    val rest = match.groupValues[3]
                    val newStatus = if (status == " ") "x" else " "
                    lines[i] = "$prefix [$newStatus]$rest"
                    return lines.joinToString("\n")
                }
                currentTaskIndex++
            }
        }
    }
    return markdown
}

/**
 * A beautiful, fast, and robust Markdown renderer built on top of CommonMark.
 * Fully supports bi-directional mixed script rendering (e.g. Urdu, Arabic, and English).
 * Each paragraph and heading automatically detects its content directionality, aligning
 * and formatting naturally without breaking bidirectional sentences.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    onLinkClick: ((String) -> Unit)? = null,
    onMarkdownChange: ((String) -> Unit)? = null
) {
    if (markdown.isBlank()) return

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val parser = remember {
        val extensions = listOf(
            StrikethroughExtension.create(),
            TablesExtension.create()
        )
        Parser.builder()
            .extensions(extensions)
            .build()
    }
    val document = remember(markdown) { parser.parse(markdown) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val taskCounter = remember(markdown) { AtomicInteger(0) }
    taskCounter.set(0)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RenderNode(
            node = document,
            color = color,
            primaryColor = primaryColor,
            maxLines = maxLines,
            onLinkClick = onLinkClick,
            context = context,
            isDark = isDark,
            markdown = markdown,
            onMarkdownChange = onMarkdownChange,
            taskCounter = taskCounter
        )
    }
}

@Composable
private fun ColumnScope.RenderNode(
    node: Node,
    color: Color,
    primaryColor: Color,
    maxLines: Int,
    onLinkClick: ((String) -> Unit)?,
    context: android.content.Context,
    isDark: Boolean,
    markdown: String = "",
    onMarkdownChange: ((String) -> Unit)? = null,
    taskCounter: AtomicInteger? = null,
    isTaskChecked: Boolean = false,
    stripTaskPrefix: Boolean = false
) {
    var child = node.firstChild
    var bulletIndex = 1
    val isOrderedList = node is OrderedList

    while (child != null) {
        val currentChild = child
        when (currentChild) {
            is org.commonmark.node.Paragraph -> {
                val annotatedString = buildAnnotatedString {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix)
                }
                val isRtl = isRtlText(annotatedString.text)
                val blockDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Text(
                        text = annotatedString,
                        color = if (isTaskChecked) color.copy(alpha = 0.55f) else color,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.Content,
                            lineHeight = 22.sp,
                            textDecoration = if (isTaskChecked) TextDecoration.LineThrough else null
                        ),
                        textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = maxLines
                    )
                }
            }
            is Heading -> {
                val annotatedString = buildAnnotatedString {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark)
                }
                val isRtl = isRtlText(annotatedString.text)
                val blockDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                val (fontSize, fontWeight, topPadding) = when (currentChild.level) {
                    1 -> Triple(26.sp, FontWeight.ExtraBold, 16.dp)
                    2 -> Triple(21.sp, FontWeight.Bold, 12.dp)
                    3 -> Triple(17.sp, FontWeight.SemiBold, 8.dp)
                    else -> Triple(15.sp, FontWeight.SemiBold, 6.dp)
                }
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Text(
                        text = annotatedString,
                        color = color,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDirection = TextDirection.Content,
                            lineHeight = (fontSize.value * 1.3f).sp
                        ),
                        textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topPadding, bottom = 3.dp),
                        maxLines = maxLines
                    )
                }
            }
            is BulletList -> {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenderNode(currentChild, color, primaryColor, maxLines, onLinkClick, context, isDark, markdown, onMarkdownChange, taskCounter)
                }
            }
            is OrderedList -> {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenderNode(currentChild, color, primaryColor, maxLines, onLinkClick, context, isDark, markdown, onMarkdownChange, taskCounter)
                }
            }
            is ListItem -> {
                val itemText = extractPlainText(currentChild).trim()
                val isTaskUnchecked = itemText.startsWith("[ ]")
                val isTaskCheckedItem = itemText.startsWith("[x]", ignoreCase = true)
                val isTaskItem = isTaskUnchecked || isTaskCheckedItem
                val thisTaskIndex = if (isTaskItem && taskCounter != null) taskCounter.getAndIncrement() else -1
                val isRtl = isRtlText(itemText)
                val blockDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTaskItem) {
                            @OptIn(ExperimentalMaterial3Api::class)
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                Checkbox(
                                    checked = isTaskCheckedItem,
                                    onCheckedChange = if (onMarkdownChange != null && thisTaskIndex >= 0) {
                                        {
                                            val updated = toggleTaskListItem(markdown, thisTaskIndex)
                                            onMarkdownChange(updated)
                                        }
                                    } else null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = primaryColor,
                                        uncheckedColor = color.copy(alpha = 0.55f),
                                        checkmarkColor = if (isDark) Color.Black else Color.White
                                    ),
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        } else {
                            Text(
                                text = if (isOrderedList) "$bulletIndex." else "•",
                                color = color,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (isOrderedList) bulletIndex++
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (isTaskItem && onMarkdownChange != null && thisTaskIndex >= 0) {
                                        Modifier.clickable {
                                            val updated = toggleTaskListItem(markdown, thisTaskIndex)
                                            onMarkdownChange(updated)
                                        }
                                    } else Modifier
                                )
                        ) {
                            RenderNode(
                                node = currentChild,
                                color = color,
                                primaryColor = primaryColor,
                                maxLines = maxLines,
                                onLinkClick = onLinkClick,
                                context = context,
                                isDark = isDark,
                                markdown = markdown,
                                onMarkdownChange = onMarkdownChange,
                                taskCounter = taskCounter,
                                isTaskChecked = isTaskCheckedItem,
                                stripTaskPrefix = isTaskItem
                            )
                        }
                    }
                }
            }
            is BlockQuote -> {
                val quoteText = extractPlainText(currentChild)
                val isRtl = isRtlText(quoteText)
                val blockDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                val borderColor = primaryColor.copy(alpha = 0.6f)
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .drawBehind {
                                val lineX = if (isRtl) size.width - 1.5.dp.toPx() else 1.5.dp.toPx()
                                drawLine(
                                    color = borderColor,
                                    start = Offset(lineX, 0f),
                                    end = Offset(lineX, size.height),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                            .padding(start = if (isRtl) 0.dp else 12.dp, end = if (isRtl) 12.dp else 0.dp)
                    ) {
                        RenderNode(currentChild, color.copy(alpha = 0.85f), primaryColor, maxLines, onLinkClick, context, isDark, markdown, onMarkdownChange, taskCounter)
                    }
                }
            }
            is FencedCodeBlock -> {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = currentChild.literal.trim(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDirection = TextDirection.Ltr
                            )
                        )
                    }
                }
            }
            is IndentedCodeBlock -> {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = currentChild.literal.trim(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDirection = TextDirection.Ltr
                            )
                        )
                    }
                }
            }
            is TableBlock -> {
                MarkdownTable(
                    tableBlock = currentChild,
                    color = color,
                    primaryColor = primaryColor,
                    onLinkClick = onLinkClick,
                    context = context,
                    isDark = isDark
                )
            }
            is ThematicBreak -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )
            }
        }
        child = currentChild.next
    }
}

private data class TableRowModel(
    val isHeader: Boolean,
    val cells: List<Node>
)

@Composable
private fun MarkdownTable(
    tableBlock: TableBlock,
    color: Color,
    primaryColor: Color,
    onLinkClick: ((String) -> Unit)?,
    context: android.content.Context,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val rows = remember(tableBlock) {
        val result = mutableListOf<TableRowModel>()
        var child = tableBlock.firstChild
        while (child != null) {
            when (child) {
                is TableHead -> {
                    var r = child.firstChild
                    while (r != null) {
                        if (r is TableRow) {
                            val cells = mutableListOf<Node>()
                            var c = r.firstChild
                            while (c != null) {
                                if (c is TableCell) cells.add(c)
                                c = c.next
                            }
                            result.add(TableRowModel(isHeader = true, cells = cells))
                        }
                        r = r.next
                    }
                }
                is TableBody -> {
                    var r = child.firstChild
                    while (r != null) {
                        if (r is TableRow) {
                            val cells = mutableListOf<Node>()
                            var c = r.firstChild
                            while (c != null) {
                                if (c is TableCell) cells.add(c)
                                c = c.next
                            }
                            result.add(TableRowModel(isHeader = false, cells = cells))
                        }
                        r = r.next
                    }
                }
            }
            child = child.next
        }
        result
    }

    if (rows.isEmpty()) return
    val columnCount = rows.maxOf { it.cells.size }
    if (columnCount == 0) return

    val scrollState = rememberScrollState()

    val colMaxChars = remember(rows, columnCount) {
        (0 until columnCount).map { colIndex ->
            rows.maxOfOrNull { row ->
                val cell = row.cells.getOrNull(colIndex)
                if (cell != null) extractPlainText(cell).trim().length else 0
            }?.coerceAtLeast(3) ?: 3
        }
    }
    val totalChars = remember(colMaxChars) { colMaxChars.sum().coerceAtLeast(1) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val availableWidth = maxWidth
        val minColumnWidth = 84.dp

        val minTableWidth = minColumnWidth * columnCount
        val totalTableWidth = if (availableWidth > minTableWidth) availableWidth else minTableWidth

        val colWidths = remember(columnCount, totalTableWidth, colMaxChars, totalChars) {
            (0 until columnCount).map { c ->
                val ratio = colMaxChars[c].toFloat() / totalChars
                maxOf(minColumnWidth, totalTableWidth * ratio)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    RoundedCornerShape(8.dp)
                )
                .horizontalFadingEdges(scrollState, fadeWidth = 16.dp)
                .horizontalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier.width(colWidths.fold(0.dp) { acc, w -> acc + w })
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    val isHeader = row.isHeader
                    val rowBg = if (isHeader) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    } else if (rowIndex % 2 == 1) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .drawBehind {
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.2f),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 0.5.dp.toPx()
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (colIndex in 0 until columnCount) {
                            val cellNode = row.cells.getOrNull(colIndex)
                            val cellWidth = colWidths[colIndex]
                            val isLastCol = colIndex == columnCount - 1

                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .drawBehind {
                                        if (!isLastCol) {
                                            drawLine(
                                                color = Color.Gray.copy(alpha = 0.15f),
                                                start = Offset(size.width, 0f),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 0.5.dp.toPx()
                                            )
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                if (cellNode != null) {
                                    val cellText = buildAnnotatedString {
                                        AppendInlineNodes(
                                            node = cellNode,
                                            color = color,
                                            primaryColor = primaryColor,
                                            onLinkClick = onLinkClick,
                                            context = context,
                                            isDark = isDark
                                        )
                                    }
                                    Text(
                                        text = cellText,
                                        color = if (isHeader) MaterialTheme.colorScheme.onSurface else color,
                                        fontSize = if (isHeader) 13.sp else 12.5.sp,
                                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                        lineHeight = 18.sp
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

private fun extractPlainText(node: Node): String {
    val sb = StringBuilder()
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is org.commonmark.node.Text -> sb.append(child.literal)
            is Code -> sb.append(child.literal)
            else -> sb.append(extractPlainText(child))
        }
        child = child.next
    }
    return sb.toString()
}

private fun AnnotatedString.Builder.AppendInlineNodes(
    node: Node,
    color: Color,
    primaryColor: Color,
    onLinkClick: ((String) -> Unit)?,
    context: android.content.Context,
    isDark: Boolean,
    isTaskChecked: Boolean = false,
    stripTaskPrefix: Boolean = false
) {
    var child = node.firstChild
    var hasStripped = false
    while (child != null) {
        val currentChild = child
        when (currentChild) {
            is org.commonmark.node.Text -> {
                var literal = currentChild.literal
                if (stripTaskPrefix && !hasStripped) {
                    literal = literal.replaceFirst(Regex("""^\[[ xX]\]\s*"""), "")
                    hasStripped = true
                }
                val urlRegex = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""")
                var lastIndex = 0
                val matches = urlRegex.findAll(literal)
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                for (match in matches) {
                    if (match.range.first > lastIndex) {
                        append(literal.substring(lastIndex, match.range.first))
                    }
                    val url = match.value
                    val linkListener = LinkInteractionListener { annotation ->
                        val clickedUrl = (annotation as? LinkAnnotation.Url)?.url ?: url
                        if (onLinkClick != null) {
                            onLinkClick(clickedUrl)
                        } else {
                            CobaltCustomTabsLauncher.launch(
                                context = context,
                                url = clickedUrl,
                                isDarkTheme = isDark
                            )
                        }
                    }
                    withLink(LinkAnnotation.Url(url = url, styles = linkStyles, linkInteractionListener = linkListener)) {
                        append(url)
                    }
                    lastIndex = match.range.last + 1
                }
                if (lastIndex < literal.length) {
                    append(literal.substring(lastIndex))
                }
            }
            is Strikethrough -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix)
                }
            }
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix)
                }
            }
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix)
                }
            }
            is Code -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = color.copy(alpha = 0.08f),
                        color = primaryColor,
                        fontSize = 13.sp
                    )
                ) {
                    append(currentChild.literal)
                }
            }
            is Link -> {
                val destination = currentChild.destination
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                val linkListener = LinkInteractionListener { annotation ->
                    val clickedUrl = (annotation as? LinkAnnotation.Url)?.url ?: destination
                    if (onLinkClick != null) {
                        onLinkClick(clickedUrl)
                    } else {
                        CobaltCustomTabsLauncher.launch(
                            context = context,
                            url = clickedUrl,
                            isDarkTheme = isDark
                        )
                    }
                }
                withLink(LinkAnnotation.Url(url = destination, styles = linkStyles, linkInteractionListener = linkListener)) {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix)
                }
            }
            is HardLineBreak -> {
                append("\n")
            }
            is SoftLineBreak -> {
                append("\n") // Use clean single line break instead of double space
            }
            else -> {
                AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix)
            }
        }
        child = currentChild.next
    }
}

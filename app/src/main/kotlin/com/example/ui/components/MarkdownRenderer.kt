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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.notes.NoteSizePolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import com.example.utils.isRtlText

/**
 * Toggles a checklist task item between unchecked `- [ ]` and checked `- [x]`.
 * Accurately tracks line index outside fenced code blocks to match AST traversal.
 */
fun toggleTaskListItem(markdown: String, targetTaskIndex: Int): String =
    com.example.notes.MarkdownTaskEdits.toggle(markdown, targetTaskIndex)

data class SearchHighlight(val query: String, val isActive: Boolean)
val LocalSearchHighlight = compositionLocalOf<SearchHighlight?> { null }

fun AnnotatedString.withSearchHighlight(highlight: SearchHighlight?, primaryColor: Color): AnnotatedString {
    if (highlight == null || highlight.query.isBlank() || this.text.isBlank()) return this
    val textLower = this.text.lowercase(java.util.Locale.ROOT)
    val queryLower = highlight.query.lowercase(java.util.Locale.ROOT)
    var startIndex = 0
    val builder = AnnotatedString.Builder(this)
    val highlightStyle = SpanStyle(
        background = if (highlight.isActive) primaryColor.copy(alpha = 0.38f) else primaryColor.copy(alpha = 0.18f),
        fontWeight = FontWeight.Bold
    )
    while (true) {
        val index = textLower.indexOf(queryLower, startIndex)
        if (index < 0) break
        builder.addStyle(highlightStyle, index, index + highlight.query.length)
        startIndex = index + highlight.query.length
    }
    return builder.toAnnotatedString()
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
    onMarkdownChange: ((String) -> Unit)? = null,
    prepared: SafeMarkdownDocument? = null,
    highlightQuery: String? = null,
    isHighlightActive: Boolean = false,
    codeContinuesBefore: Boolean = false,
    codeContinuesAfter: Boolean = false
) {
    if (markdown.isBlank() && prepared == null) return

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    if (markdown.length > NoteSizePolicy.MARKDOWN_BLOCK_CHARACTERS ||
        markdown.asSequence().filter { it == '\n' }.take(NoteSizePolicy.MARKDOWN_BLOCK_LINES).count() >= NoteSizePolicy.MARKDOWN_BLOCK_LINES) {
        Text("This Markdown needs large-note mode. Open the note to read or export its complete body.", modifier = modifier, color = color)
        return
    }
    val parsed by produceState<SafeMarkdownDocument?>(prepared, markdown, prepared) {
        value = prepared ?: withContext(Dispatchers.Default) { parseSafeMarkdown(markdown) }
    }
    val document = parsed?.node
    if (parsed == null) { NoteBodySkeleton(modifier, lines = 2); return }
    if (document == null) {
        Text(markdown, modifier = modifier, color = color, maxLines = minOf(maxLines, NoteSizePolicy.MARKDOWN_BLOCK_LINES))
        return
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val taskCounter = remember(markdown) { AtomicInteger(0) }
    taskCounter.set(0)

    val searchHighlight = if (!highlightQuery.isNullOrBlank()) SearchHighlight(highlightQuery, isHighlightActive) else null
    CompositionLocalProvider(
        LocalCodeContinuation provides (codeContinuesBefore to codeContinuesAfter),
        LocalMarkdownLinkDecoration provides (parsed?.decorateLinks != false),
        LocalSearchHighlight provides searchHighlight
    ) {
        val typo = com.example.ui.theme.LocalCobaltTypography.current
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(typo.paragraphSpacingDp)
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
}

private val LocalMarkdownLinkDecoration = staticCompositionLocalOf { true }
data class SafeMarkdownDocument(val node: Node?, val links: Int = 0, val nodeCount: Int = 0,
    val decorateLinks: Boolean = true)

private val safeMarkdownParser = object : ThreadLocal<Parser>() {
    override fun initialValue(): Parser = Parser.builder()
        .extensions(listOf(StrikethroughExtension.create(), TablesExtension.create())).build()
}

/** Bounded parser input plus iterative depth/link checks before recursive Compose rendering. */
internal fun parseSafeMarkdown(markdown: String): SafeMarkdownDocument {
    val document = safeMarkdownParser.get()!!.parse(markdown)
    val pending = ArrayDeque<Pair<Node, Int>>()
    pending.addLast(document to 0)
    var links = 0
    var count = 0
    while (pending.isNotEmpty()) {
        val (node, depth) = pending.removeLast()
        count++
        // Flatten excessive container nesting while retaining its rendered children. Do not
        // downgrade an entire note/paragraph to raw Markdown when a safety guard is reached.
        val flatten = depth > 8 && (node is BlockQuote || node is BulletList || node is OrderedList ||
            node is ListItem || node is Emphasis || node is StrongEmphasis)
        var child = node.firstChild
        if (flatten) {
            while (child != null) {
                val next = child.next
                child.unlink(); node.insertBefore(child)
                pending.addLast(child to depth)
                child = next
            }
            node.unlink()
            continue
        }
        if (node is Link || node is Image) links++
        if (node is org.commonmark.node.Text && node.parent !is Link) links += com.example.notes.MarkdownAutoLinks.find(node.literal).count()
        while (child != null) { pending.addLast(child to depth + 1); child = child.next }
    }
    return SafeMarkdownDocument(document, links, count, links <= NoteSizePolicy.DECORATED_LINK_LIMIT)
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
    stripTaskPrefix: Boolean = false,
    isInBlockQuote: Boolean = false
) {
    val decorateLinks = LocalMarkdownLinkDecoration.current
    val typo = com.example.ui.theme.LocalCobaltTypography.current
    var child = node.firstChild
    var bulletIndex = 1
    val isOrderedList = node is OrderedList

    while (child != null) {
        val currentChild = child
        when (currentChild) {
            is org.commonmark.node.Paragraph -> {
                val annotatedString = buildAnnotatedString {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix, decorateLinks, typography = typo)
                }
                val isRtl = isRtlText(annotatedString.text)
                val blockDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                val finalString = annotatedString.withSearchHighlight(LocalSearchHighlight.current, primaryColor)
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Text(
                        text = finalString,
                        color = if (isTaskChecked) color.copy(alpha = 0.55f) else color,
                        style = typo.body.copy(
                            fontStyle = if (isInBlockQuote) FontStyle.Italic else FontStyle.Normal,
                            textDirection = TextDirection.Content,
                            lineHeight = if (isInBlockQuote) (typo.body.fontSize.value * typo.lineHeightMultiplier * 1.05f).sp else typo.body.lineHeight,
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
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, decorateLinks = decorateLinks, typography = typo)
                }
                val isRtl = isRtlText(annotatedString.text)
                val blockDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                val (headingStyle, topPadding) = when (currentChild.level) {
                    1 -> typo.h1 to 16.dp
                    2 -> typo.h2 to 12.dp
                    3 -> typo.h3 to 8.dp
                    else -> typo.h4 to 6.dp
                }
                val finalString = annotatedString.withSearchHighlight(LocalSearchHighlight.current, primaryColor)
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Text(
                        text = finalString,
                        color = color,
                        style = headingStyle.copy(
                            textDirection = TextDirection.Content
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
                                style = typo.body.copy(fontWeight = FontWeight.Bold)
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
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_quote),
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            RenderNode(
                                node = currentChild,
                                color = MaterialTheme.colorScheme.onSurface,
                                primaryColor = primaryColor,
                                maxLines = maxLines,
                                onLinkClick = onLinkClick,
                                context = context,
                                isDark = isDark,
                                markdown = markdown,
                                onMarkdownChange = onMarkdownChange,
                                taskCounter = taskCounter,
                                isInBlockQuote = true
                            )
                        }
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_quote),
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier
                                .align(Alignment.End)
                                .size(18.dp)
                                .rotate(180f)
                        )
                    }
                }
            }
            is FencedCodeBlock -> {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { MarkdownCodeBlock(AnnotatedString(currentChild.literal.removeSuffix("\n")).withSearchHighlight(LocalSearchHighlight.current, primaryColor)) }
            }
            is IndentedCodeBlock -> {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { MarkdownCodeBlock(AnnotatedString(currentChild.literal.removeSuffix("\n")).withSearchHighlight(LocalSearchHighlight.current, primaryColor)) }
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
    val decorateLinks = LocalMarkdownLinkDecoration.current
    val typo = com.example.ui.theme.LocalCobaltTypography.current
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
        val colMinWidths = remember(columnCount, colMaxChars) {
            (0 until columnCount).map { c ->
                val chars = colMaxChars.getOrElse(c) { 3 }
                when {
                    chars <= 4 -> 80.dp
                    chars <= 8 -> 110.dp
                    else -> 175.dp
                }
            }
        }
        val minTableWidth = remember(colMinWidths) { colMinWidths.fold(0.dp) { acc, w -> acc + w } }
        val totalTableWidth = if (availableWidth > minTableWidth) availableWidth else minTableWidth

        val colWidths = remember(columnCount, totalTableWidth, colMaxChars, totalChars, colMinWidths) {
            (0 until columnCount).map { c ->
                val ratio = colMaxChars[c].toFloat() / totalChars
                maxOf(colMinWidths[c], totalTableWidth * ratio)
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
                                            isDark = isDark,
                                            decorateLinks = decorateLinks,
                                            typography = typo
                                        )
                                    }
                                    val finalCellText = cellText.withSearchHighlight(LocalSearchHighlight.current, primaryColor)
                                    Text(
                                        text = finalCellText,
                                        color = if (isHeader) MaterialTheme.colorScheme.onSurface else color,
                                        fontSize = if (isHeader) (typo.body.fontSize.value * 0.85f).sp else (typo.body.fontSize.value * 0.8f).sp,
                                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = typo.textFontFamily,
                                        lineHeight = (typo.body.fontSize.value * 0.85f * typo.lineHeightMultiplier).sp
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
    stripTaskPrefix: Boolean = false,
    decorateLinks: Boolean = true,
    typography: com.example.ui.theme.CobaltTypography? = null
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
                if (!decorateLinks) { append(literal); child = currentChild.next; continue }
                var lastIndex = 0
                val matches = com.example.notes.MarkdownAutoLinks.find(literal)
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                for (match in matches) {
                    if (match.start > lastIndex) {
                        append(literal.substring(lastIndex, match.start))
                    }
                    val url = match.destination
                    val linkListener = LinkInteractionListener { annotation ->
                        val clickedUrl = (annotation as? LinkAnnotation.Url)?.url ?: url
                        if (onLinkClick != null) {
                            onLinkClick(clickedUrl)
                        } else {
                            openMarkdownLink(context, clickedUrl, isDark)
                        }
                    }
                    withLink(LinkAnnotation.Url(url = url, styles = linkStyles, linkInteractionListener = linkListener)) {
                        append(match.label)
                    }
                    lastIndex = match.end
                }
                if (lastIndex < literal.length) {
                    append(literal.substring(lastIndex))
                }
            }
            is Strikethrough -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix, decorateLinks, typography)
                }
            }
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix, decorateLinks, typography)
                }
            }
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix, decorateLinks, typography)
                }
            }
            is Code -> {
                val codeFamily = typography?.codeFontFamily ?: FontFamily.Monospace
                val codeSize = typography?.inlineCode?.fontSize ?: 13.sp
                withStyle(
                    SpanStyle(
                        fontFamily = codeFamily,
                        background = color.copy(alpha = 0.08f),
                        color = primaryColor,
                        fontSize = codeSize
                    )
                ) {
                    append(currentChild.literal)
                }
            }
            is Link -> {
                if (!decorateLinks) {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix, false, typography)
                    child = currentChild.next
                    continue
                }
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
                        openMarkdownLink(context, clickedUrl, isDark)
                    }
                }
                withLink(LinkAnnotation.Url(url = destination, styles = linkStyles, linkInteractionListener = linkListener)) {
                    AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix, decorateLinks, typography)
                }
            }
            is HardLineBreak -> {
                append("\n")
            }
            is SoftLineBreak -> {
                append("\n") // Use clean single line break instead of double space
            }
            else -> {
                AppendInlineNodes(currentChild, color, primaryColor, onLinkClick, context, isDark, isTaskChecked, stripTaskPrefix, decorateLinks, typography)
            }
        }
        child = currentChild.next
    }
}

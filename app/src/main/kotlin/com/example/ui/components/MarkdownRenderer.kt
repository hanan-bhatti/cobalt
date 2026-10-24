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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import org.commonmark.node.*
import org.commonmark.parser.Parser
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import com.example.utils.isRtlText

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
    maxLines: Int = Int.MAX_VALUE
) {
    if (markdown.isBlank()) return

    val parser = remember { Parser.builder().build() }
    val document = remember(markdown) { parser.parse(markdown) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RenderNode(document, color, primaryColor, maxLines)
    }
}

@Composable
private fun ColumnScope.RenderNode(node: Node, color: Color, primaryColor: Color, maxLines: Int) {
    var child = node.firstChild
    var bulletIndex = 1
    val isOrderedList = node is OrderedList

    while (child != null) {
        val currentChild = child
        when (currentChild) {
            is org.commonmark.node.Paragraph -> {
                val annotatedString = buildAnnotatedString {
                    AppendInlineNodes(currentChild, color, primaryColor)
                }
                val isRtl = isRtlText(annotatedString.text)
                val blockDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Text(
                        text = annotatedString,
                        color = color,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.Content,
                            lineHeight = 22.sp
                        ),
                        textAlign = if (isRtl) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = maxLines
                    )
                }
            }
            is Heading -> {
                val annotatedString = buildAnnotatedString {
                    AppendInlineNodes(currentChild, color, primaryColor)
                }
                val isRtl = isRtlText(annotatedString.text)
                val blockDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                val style = when (currentChild.level) {
                    1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content)
                    2 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content)
                    3 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content)
                    else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content)
                }
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Text(
                        text = annotatedString,
                        color = color,
                        style = style,
                        textAlign = if (isRtl) TextAlign.End else TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        maxLines = maxLines
                    )
                }
            }
            is BulletList -> {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenderNode(currentChild, color, primaryColor, maxLines)
                }
            }
            is OrderedList -> {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenderNode(currentChild, color, primaryColor, maxLines)
                }
            }
            is ListItem -> {
                val itemText = extractPlainText(currentChild)
                val isRtl = isRtlText(itemText)
                val blockDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isOrderedList) "$bulletIndex." else "•",
                            color = color,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (isOrderedList) bulletIndex++
                        Column(modifier = Modifier.weight(1f)) {
                            RenderNode(currentChild, color, primaryColor, maxLines)
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
                        RenderNode(currentChild, color.copy(alpha = 0.85f), primaryColor, maxLines)
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

private fun AnnotatedString.Builder.AppendInlineNodes(node: Node, color: Color, primaryColor: Color) {
    var child = node.firstChild
    while (child != null) {
        val currentChild = child
        when (currentChild) {
            is org.commonmark.node.Text -> {
                val literal = currentChild.literal
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
                    withLink(LinkAnnotation.Url(url = url, styles = linkStyles)) {
                        append(url)
                    }
                    lastIndex = match.range.last + 1
                }
                if (lastIndex < literal.length) {
                    append(literal.substring(lastIndex))
                }
            }
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    AppendInlineNodes(currentChild, color, primaryColor)
                }
            }
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    AppendInlineNodes(currentChild, color, primaryColor)
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
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                withLink(LinkAnnotation.Url(url = currentChild.destination, styles = linkStyles)) {
                    AppendInlineNodes(currentChild, color, primaryColor)
                }
            }
            is HardLineBreak -> {
                append("\n")
            }
            is SoftLineBreak -> {
                append("\n") // Use clean single line break instead of double space
            }
            else -> {
                AppendInlineNodes(currentChild, color, primaryColor)
            }
        }
        child = currentChild.next
    }
}

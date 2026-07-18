/*
 * Second Brain - A universal capture and personal knowledge archive
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.node.*
import org.commonmark.parser.Parser

/**
 * A beautiful, fast, and robust Markdown renderer built on top of CommonMark.
 * It translates Markdown strings into native Jetpack Compose Material 3 components.
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
        when (child) {
            is org.commonmark.node.Paragraph -> {
                val annotatedString = buildAnnotatedString {
                    AppendInlineNodes(child, color, primaryColor)
                }
                Text(
                    text = annotatedString,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = maxLines
                )
            }
            is Heading -> {
                val annotatedString = buildAnnotatedString {
                    AppendInlineNodes(child, color, primaryColor)
                }
                val style = when (child.level) {
                    1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    2 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    3 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                }
                Text(
                    text = annotatedString,
                    color = color,
                    style = style,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    maxLines = maxLines
                )
            }
            is BulletList -> {
                Column(
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenderNode(child, color, primaryColor, maxLines)
                }
            }
            is OrderedList -> {
                Column(
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenderNode(child, color, primaryColor, maxLines)
                }
            }
            is ListItem -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isOrderedList) {
                        Text(
                            text = "$bulletIndex.",
                            color = color,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        bulletIndex++
                    } else {
                        Text(
                            text = "•",
                            color = color,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        RenderNode(child, color, primaryColor, maxLines)
                    }
                }
            }
            is BlockQuote -> {
                val borderColor = primaryColor.copy(alpha = 0.6f)
                Column(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .drawBehind {
                            drawLine(
                                color = borderColor,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                        .padding(start = 12.dp)
                ) {
                    RenderNode(child, color.copy(alpha = 0.8f), primaryColor, maxLines)
                }
            }
            is FencedCodeBlock -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = child.literal.trim(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is IndentedCodeBlock -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = child.literal.trim(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
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
        child = child.next
    }
}

private fun AnnotatedString.Builder.AppendInlineNodes(node: Node, color: Color, primaryColor: Color) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is org.commonmark.node.Text -> {
                append(child.literal)
            }
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    AppendInlineNodes(child, color, primaryColor)
                }
            }
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    AppendInlineNodes(child, color, primaryColor)
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
                    append(child.literal)
                }
            }
            is Link -> {
                withStyle(
                    SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    AppendInlineNodes(child, color, primaryColor)
                }
            }
            is HardLineBreak -> {
                append("\n")
            }
            is SoftLineBreak -> {
                append("\n") // Use clean single line break instead of double space
            }
            else -> {
                AppendInlineNodes(child, color, primaryColor)
            }
        }
        child = child.next
    }
}

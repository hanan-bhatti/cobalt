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

package com.example.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.CobaltCustomTabsLauncher
import com.example.utils.isRtlText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val tokenRegex = Regex(
    """(\[[^\]]+]\([^)]+\))|(\*\*[^*]+\*\*)|(`[^`]+`)|(https?://[^\s()<>]+(?:\([^\s()<>]+\)|[^\s`!()\[\]{};:'".,<>?«»“”‘’]))"""
)
private val markdownLinkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
private val boldRegex = Regex("""\*\*([^*]+)\*\*""")
private val inlineCodeRegex = Regex("""`([^`]+)`""")
private val headerRegex = Regex("""^(#{1,6})\s+(.+)$""")
private val dividerRegex = Regex("""^[-*_]{3,}$""")
private val boldHeaderRegex = Regex("""^\*\*([^*]+)\*\*$""")

private sealed class MdBlock {
    data class Header(val text: String, val level: Int = 1) : MdBlock()
    data class Paragraph(val raw: String) : MdBlock()
    data class Bullet(val raw: String) : MdBlock()
    data object Divider : MdBlock()
}

private fun parseBlocks(markdown: String): List<MdBlock> {
    val lines = markdown.split("\n")
    val blocks = mutableListOf<MdBlock>()
    for (line in lines) {
        if (line.isBlank()) continue

        // Check for indented continuation of a previous bullet
        if ((line.startsWith("  ") || line.startsWith("\t")) && blocks.isNotEmpty() && blocks.last() is MdBlock.Bullet) {
            val prev = blocks.last() as MdBlock.Bullet
            blocks[blocks.lastIndex] = MdBlock.Bullet(prev.raw + "\n" + line.trim())
            continue
        }

        val trimmed = line.trim()
        val atxMatch = headerRegex.find(trimmed)
        val isBoldHeader = boldHeaderRegex.matches(trimmed) && !trimmed.contains(":")

        when {
            dividerRegex.matches(trimmed) -> {
                blocks.add(MdBlock.Divider)
            }
            atxMatch != null -> {
                val level = atxMatch.groupValues[1].length
                val text = atxMatch.groupValues[2].trim().removeSurrounding("**").trim()
                blocks.add(MdBlock.Header(text = text, level = level))
            }
            isBoldHeader -> {
                val text = boldHeaderRegex.find(trimmed)!!.groupValues[1].trim()
                blocks.add(MdBlock.Header(text = text, level = 2))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MdBlock.Bullet(trimmed.substring(2).trim()))
            }
            else -> {
                blocks.add(MdBlock.Paragraph(trimmed))
            }
        }
    }
    return blocks
}

private fun appendLink(
    builder: AnnotatedString.Builder,
    label: String,
    url: String,
    context: android.content.Context,
    isDark: Boolean,
    primaryColor: Color,
    onLinkClick: ((String) -> Unit)?
) {
    val targetUrl = url.trim().let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
    }
    if (targetUrl.isBlank()) {
        builder.append(label)
        return
    }
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = primaryColor,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.SemiBold
        )
    )
    val linkListener = LinkInteractionListener { annotation ->
        val clickedUrl = (annotation as? LinkAnnotation.Url)?.url ?: targetUrl
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
    builder.withLink(LinkAnnotation.Url(url = targetUrl, styles = linkStyles, linkInteractionListener = linkListener)) {
        append(label)
    }
}

@Composable
private fun buildRichText(raw: String, onLinkClick: ((String) -> Unit)? = null): AnnotatedString {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val primaryColor = MaterialTheme.colorScheme.primary
    val builder = AnnotatedString.Builder()
    var cursor = 0

    for (match in tokenRegex.findAll(raw)) {
        if (match.range.first > cursor) {
            builder.append(raw.substring(cursor, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("[") && token.endsWith(")") && token.contains("](") -> {
                val m = markdownLinkRegex.find(token)
                if (m != null) {
                    val label = m.groupValues[1]
                    val url = m.groupValues[2]
                    appendLink(builder, label, url, context, isDark, primaryColor, onLinkClick)
                } else {
                    builder.append(token)
                }
            }
            token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                val inner = boldRegex.find(token)?.groupValues?.get(1) ?: token.substring(2, token.length - 2)
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(inner)
                }
            }
            token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                val inner = inlineCodeRegex.find(token)?.groupValues?.get(1) ?: token.substring(1, token.length - 1)
                builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(inner)
                }
            }
            token.startsWith("http://") || token.startsWith("https://") -> {
                appendLink(builder, token, token, context, isDark, primaryColor, onLinkClick)
            }
            else -> builder.append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        builder.append(raw.substring(cursor))
    }
    return builder.toAnnotatedString()
}

@Composable
private fun RichTextLine(
    raw: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    val text = buildRichText(raw, onLinkClick)
    val isRtl = isRtlText(text.text)
    CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Text(
            text = text,
            style = style.copy(
                color = LocalContentColor.current,
                textDirection = TextDirection.Content
            ),
            textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ExpressiveMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEachIndexed { index, block ->
            val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
            val offsetY = remember { androidx.compose.animation.core.Animatable(24f) }
            LaunchedEffect(markdown, index) {
                delay((index * 35L).coerceAtMost(400))
                launch {
                    alpha.animateTo(1f, tween(320, easing = LinearOutSlowInEasing))
                }
                launch {
                    offsetY.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                    )
                }
            }

            Box(
                modifier = Modifier.graphicsLayer {
                    this.alpha = alpha.value
                    translationY = offsetY.value
                }
            ) {
                when (block) {
                    is MdBlock.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 1.dp
                        )
                    }
                    is MdBlock.Header -> {
                        val isRtl = isRtlText(block.text)
                        CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = if (index == 0) 0.dp else if (block.level <= 2) 20.dp else 12.dp,
                                        bottom = if (block.level <= 2) 6.dp else 4.dp
                                    ),
                                horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
                            ) {
                                val headerStyle = when (block.level) {
                                    1 -> MaterialTheme.typography.titleLarge
                                    2 -> MaterialTheme.typography.titleMedium
                                    else -> MaterialTheme.typography.titleSmall
                                }
                                Text(
                                    text = block.text,
                                    style = headerStyle.copy(textDirection = TextDirection.Content),
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (block.level <= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                )
                                if (block.level <= 2) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .height(3.dp)
                                            .width(32.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                    is MdBlock.Paragraph -> {
                        Box(modifier = Modifier.padding(bottom = 6.dp)) {
                            RichTextLine(
                                raw = block.raw,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                onLinkClick = onLinkClick
                            )
                        }
                    }
                    is MdBlock.Bullet -> {
                        Row(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                            RichTextLine(
                                raw = block.raw,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                modifier = Modifier.weight(1f),
                                onLinkClick = onLinkClick
                            )
                        }
                    }
                }
            }
        }
    }
}

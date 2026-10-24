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
import androidx.compose.ui.text.withLink
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.CobaltCustomTabsLauncher
import com.example.utils.isRtlText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val linkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
private val boldRegex = Regex("""\*\*([^*]+)\*\*""")

private sealed class MdBlock {
    data class Header(val text: String) : MdBlock()
    data class Paragraph(val raw: String) : MdBlock()
    data class Bullet(val raw: String) : MdBlock()
}

private fun parseBlocks(markdown: String): List<MdBlock> {
    val lines = markdown.split("\n").map { it.trim() }
    val blocks = mutableListOf<MdBlock>()
    for (line in lines) {
        if (line.isBlank()) continue
        val isHeaderOnly = boldRegex.matches(line) && !line.contains(":")
        when {
            isHeaderOnly -> blocks.add(MdBlock.Header(boldRegex.find(line)!!.groupValues[1]))
            line.startsWith("- ") || line.startsWith("* ") -> blocks.add(MdBlock.Bullet(line.removePrefix("- ").removePrefix("* ")))
            else -> blocks.add(MdBlock.Paragraph(line))
        }
    }
    return blocks
}

@Composable
private fun buildRichText(raw: String, onLinkClick: ((String) -> Unit)? = null): AnnotatedString {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val builder = AnnotatedString.Builder()
    var cursor = 0
    val combined = Regex("""(\*\*[^*]+\*\*)|(\[[^\]]+]\([^)]+\))""")
    for (match in combined.findAll(raw)) {
        if (match.range.first > cursor) {
            builder.append(raw.substring(cursor, match.range.first))
        }
        val token = match.value
        if (token.startsWith("**")) {
            val inner = boldRegex.find(token)!!.groupValues[1]
            builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inner) }
        } else {
            val m = linkRegex.find(token)!!
            val label = m.groupValues[1]
            val url = m.groupValues[2]
            val linkStyles = TextLinkStyles(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold
                )
            )
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
            builder.withLink(LinkAnnotation.Url(url = url, styles = linkStyles, linkInteractionListener = linkListener)) {
                append(label)
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) builder.append(raw.substring(cursor))
    return builder.toAnnotatedString()
}

@Composable
private fun RichTextLine(raw: String, style: androidx.compose.ui.text.TextStyle, onLinkClick: ((String) -> Unit)? = null) {
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
            modifier = Modifier.fillMaxWidth()
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
                    is MdBlock.Header -> {
                        val isRtl = isRtlText(block.text)
                        CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (index == 0) 0.dp else 20.dp, bottom = 6.dp),
                                horizontalAlignment = if (isRtl) Alignment.End else Alignment.Start
                            ) {
                                Text(
                                    text = block.text,
                                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                    is MdBlock.Paragraph -> {
                        RichTextLine(
                            raw = block.raw,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            onLinkClick = onLinkClick
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                                onLinkClick = onLinkClick
                            )
                        }
                    }
                }
            }
        }
    }
}

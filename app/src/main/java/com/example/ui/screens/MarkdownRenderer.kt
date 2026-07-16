package com.example.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private fun buildRichText(raw: String): Pair<AnnotatedString, Map<IntRange, String>> {
    val links = mutableMapOf<IntRange, String>()
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
            val start = builder.length
            builder.withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold
                )
            ) { append(label) }
            links[start until builder.length] = url
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) builder.append(raw.substring(cursor))
    return builder.toAnnotatedString() to links
}

@Composable
private fun RichTextLine(raw: String, style: androidx.compose.ui.text.TextStyle) {
    val uriHandler = LocalUriHandler.current
    val (text, links) = buildRichText(raw)
    ClickableText(
        text = text,
        style = style.copy(color = LocalContentColor.current),
        onClick = { offset ->
            links.entries.firstOrNull { offset in it.key }?.let { uriHandler.openUri(it.value) }
        }
    )
}

@Composable
fun ExpressiveMarkdown(markdown: String, modifier: Modifier = Modifier) {
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
                        Column(modifier = Modifier.padding(top = if (index == 0) 0.dp else 20.dp, bottom = 6.dp)) {
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
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
                    is MdBlock.Paragraph -> {
                        RichTextLine(
                            raw = block.raw,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
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
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

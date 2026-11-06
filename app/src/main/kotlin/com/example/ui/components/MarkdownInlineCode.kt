package com.example.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

internal const val INLINE_CODE_TAG = "cobalt-inline-code"

/** Draw padded code backgrounds without inserting padding characters into copied text. */
@Composable
internal fun MarkdownInlineText(text: AnnotatedString, color: Color,
    modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null, maxLines: Int = Int.MAX_VALUE,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null, lineHeight: TextUnit = TextUnit.Unspecified) {
    val ranges = remember(text) { text.getStringAnnotations(INLINE_CODE_TAG, 0, text.length) }
    var rectangles by remember(text) { mutableStateOf<List<Rect>>(emptyList()) }
    val background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Text(text, color = color, style = style, textAlign = textAlign, maxLines = maxLines,
        fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily, lineHeight = lineHeight,
        modifier = modifier.drawBehind {
            val padding = 3.dp.toPx(); val vertical = 1.dp.toPx()
            for (rect in rectangles) {
                val height = (rect.height - vertical * 2).coerceAtLeast(0f)
                drawRoundRect(background, Offset(rect.left - padding, rect.top + vertical),
                    Size(rect.width + padding * 2, height), CornerRadius(4.dp.toPx()))
            }
        }, onTextLayout = { layout ->
            rectangles = buildList {
                for (range in ranges) {
                    if (range.start >= range.end || range.start >= text.length) continue
                    val first = layout.getLineForOffset(range.start)
                    val last = layout.getLineForOffset((range.end - 1).coerceAtMost(text.lastIndex))
                    for (line in first..last) {
                        val start = maxOf(range.start, layout.getLineStart(line))
                        val end = minOf(range.end, layout.getLineEnd(line), text.length)
                        if (end > start) add(layout.getPathForRange(start, end).getBounds())
                    }
                }
            }
        })
}

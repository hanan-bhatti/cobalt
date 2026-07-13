package com.example.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        // Match: 1: Bold, 2: Italic, 3: Inline Code, 4: Link Text, 5: Link URL, 6: List items
        val regex = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|`(.*?)`|\\[(.*?)\\]\\((.*?)\\)|(?:^|\\n)(\\s*[\\-\\*]\\s+.*)")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            append(text.substring(currentIndex, match.range.first))
            
            if (match.groups[1] != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groups[1]!!.value) }
            } else if (match.groups[2] != null) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groups[2]!!.value) }
            } else if (match.groups[3] != null) {
                withStyle(SpanStyle(background = Color.LightGray.copy(alpha=0.3f), fontFamily = FontFamily.Monospace)) { 
                    append(match.groups[3]!!.value) 
                }
            } else if (match.groups[4] != null && match.groups[5] != null) {
                // Link
                withStyle(SpanStyle(color = Color(0xFF6B4EFF), textDecoration = TextDecoration.Underline)) {
                    append(match.groups[4]!!.value)
                }
            } else if (match.groups[6] != null) {
                // List item
                val itemText = match.groups[6]!!.value
                val formattedItemText = itemText.replaceFirst(Regex("\\s*[\\-\\*]\\s+"), "• ")
                append(formattedItemText)
            }
            currentIndex = match.range.last + 1
        }
        append(text.substring(currentIndex))
    }
}

class MarkdownVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // We will just return the original text styled, without replacing characters
        // to maintain 1:1 offset mapping and not crash the text field cursor.
        val styled = buildAnnotatedString {
            var currentIndex = 0
            val regex = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|`(.*?)`|\\[(.*?)\\]\\((.*?)\\)|(?:^|\\n)(\\s*[\\-\\*]\\s+.*)")
            val matches = regex.findAll(text.text)
            
            for (match in matches) {
                append(text.text.substring(currentIndex, match.range.first))
                if (match.groups[1] != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.value) }
                } else if (match.groups[2] != null) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.value) }
                } else if (match.groups[3] != null) {
                    withStyle(SpanStyle(background = Color.LightGray.copy(alpha=0.3f), fontFamily = FontFamily.Monospace)) { 
                        append(match.value) 
                    }
                } else if (match.groups[4] != null && match.groups[5] != null) {
                    withStyle(SpanStyle(color = Color(0xFF6B4EFF), textDecoration = TextDecoration.Underline)) {
                        append(match.value)
                    }
                } else if (match.groups[6] != null) {
                    // Just style it a bit, keep exact text
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(match.value) }
                } else {
                    append(match.value)
                }
                currentIndex = match.range.last + 1
            }
            append(text.text.substring(currentIndex))
        }
        
        return TransformedText(
            text = styled,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

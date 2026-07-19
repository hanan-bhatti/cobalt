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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.CodeKeywordDark
import com.example.ui.theme.CodeKeywordLight
import com.example.ui.theme.CodeTypeDark
import com.example.ui.theme.CodeTypeLight
import com.example.ui.theme.CodeStringDark
import com.example.ui.theme.CodeStringLight
import com.example.ui.theme.CodeCommentDark
import com.example.ui.theme.CodeCommentLight
import com.example.ui.theme.CodeNumberDark
import com.example.ui.theme.CodeNumberLight
import com.example.ui.theme.CodeAnnotationDark
import com.example.ui.theme.CodeAnnotationLight

object CodeHighlighter {
    private val keywords = setOf(
        "fun", "val", "var", "class", "interface", "import", "package", "return", "if", "else", 
        "for", "while", "when", "const", "let", "function", "def", "from", "as", "try", "catch", 
        "finally", "throw", "override", "private", "public", "protected", "internal", "null", 
        "true", "false", "this", "super", "new", "in", "is", "break", "continue", "object", "typealias",
        "struct", "enum", "func", "self", "nil", "switch", "case", "default", "defer", "go", "select",
        "chan", "map", "range", "extern", "sizeof", "typedef", "union", "register", "volatile"
    )

    private val types = setOf(
        "String", "Int", "Boolean", "Double", "Float", "Long", "Short", "Byte", "Char", "List", 
        "Map", "Set", "Unit", "Any", "Nothing", "void", "number", "any", "int", "float", "double",
        "bool", "char", "string", "struct", "class"
    )

    // Token regex for: comments (// or # or /* */), strings ("" or '' or ``), annotations (@name), numbers, and words
    private val tokenRegex = Regex(
        "(//.*|#.*|/\\*[\\s\\S]*?\\*/)|" + 
        "(\"[^\"]*\"|'[^']*'|`[^`]*`)|" + 
        "(@[a-zA-Z_][a-zA-Z0-9_]*)|" + 
        "(\\b\\d+\\b)|" + 
        "(\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)"
    )

    fun highlight(text: String, isDark: Boolean): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
        
        // Custom Dracula / Modern VS Code theme color palettes
        val keywordColor = if (isDark) CodeKeywordDark else CodeKeywordLight
        val typeColor = if (isDark) CodeTypeDark else CodeTypeLight
        val stringColor = if (isDark) CodeStringDark else CodeStringLight
        val commentColor = if (isDark) CodeCommentDark else CodeCommentLight
        val numberColor = if (isDark) CodeNumberDark else CodeNumberLight
        val annotationColor = if (isDark) CodeAnnotationDark else CodeAnnotationLight

        tokenRegex.findAll(text).forEach { matchResult ->
            val groups = matchResult.groups
            val range = matchResult.range
            
            when {
                // Comments
                groups[1] != null -> {
                    builder.addStyle(SpanStyle(color = commentColor.copy(alpha = 0.8f), fontStyle = FontStyle.Italic), range.first, range.last + 1)
                }
                // Strings
                groups[2] != null -> {
                    builder.addStyle(SpanStyle(color = stringColor), range.first, range.last + 1)
                }
                // Annotations
                groups[3] != null -> {
                    builder.addStyle(SpanStyle(color = annotationColor), range.first, range.last + 1)
                }
                // Numbers
                groups[4] != null -> {
                    builder.addStyle(SpanStyle(color = numberColor), range.first, range.last + 1)
                }
                // Identifiers (Words)
                groups[5] != null -> {
                    val word = matchResult.value
                    if (word in keywords) {
                        builder.addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), range.first, range.last + 1)
                    } else if (word in types) {
                        builder.addStyle(SpanStyle(color = typeColor, fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
                    }
                }
            }
        }
        
        return builder.toAnnotatedString()
    }
}

class CodeSyntaxHighlightTransformation(private val isDark: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = CodeHighlighter.highlight(text.text, isDark)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

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
        val keywordColor = if (isDark) Color(0xFFF07178) else Color(0xFFD32F2F) // Coral / Red
        val typeColor = if (isDark) Color(0xFF82B1FF) else Color(0xFF0D47A1)    // Pale Blue / Navy
        val stringColor = if (isDark) Color(0xFFC3E88D) else Color(0xFF2E7D32)  // Lime Green / Forest Green
        val commentColor = if (isDark) Color(0xFF89DDFF) else Color(0xFF00ACC1) // Cyan (Comments / documentation)
        val numberColor = if (isDark) Color(0xFFF78C6C) else Color(0xFFE65100)  // Peach / Dark Orange
        val annotationColor = if (isDark) Color(0xFFFFCB6B) else Color(0xFFF57F17) // Gold / Dark Yellow

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

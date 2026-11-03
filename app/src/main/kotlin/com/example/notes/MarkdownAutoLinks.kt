package com.example.notes

/** Bounded to the caller's render block. Explicit Markdown links/code never use this path. */
internal object MarkdownAutoLinks {
    data class Match(val start: Int, val end: Int, val label: String, val destination: String)
    private val pattern = Regex("""https?://[^\s<>"{}|\\^`\[\]]+|(?<![\w.+-])[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+""")
    fun find(text: String): Sequence<Match> = pattern.findAll(text).mapNotNull { match ->
        var label = match.value.trimEnd('.', ',', ';', ':', '!', '?')
        if (label.startsWith("http")) {
            while (label.endsWith(')') && label.count { it == ')' } > label.count { it == '(' }) label = label.dropLast(1)
        }
        if (label.isEmpty()) null else Match(match.range.first, match.range.first + label.length, label,
            if (label.startsWith("http")) label else "mailto:$label")
    }
}

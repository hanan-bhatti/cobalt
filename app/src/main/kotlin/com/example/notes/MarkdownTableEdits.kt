package com.example.notes

/** Bounded GFM table transactions. Readers may serve disk ranges; no whole-document String required. */
object MarkdownTableEdits {
    const val MAX_TABLE_CHARACTERS = 131_072
    const val MAX_COLUMNS = 32
    const val MAX_ROWS = 2_000
    data class Edit(val start: Int, val end: Int, val text: String, val caret: Int)
    private data class Line(val start: Int, val end: Int, val text: String, val oversized: Boolean = false)
    private val delimiter = Regex(":?-{2,}:?")
    private val fence = Regex("^\\s*(?:[-+*] |\\d+[.)] )?(`{3,}|~{3,})(.*)$")

    fun plan(length: Int, caret: Int, command: String, read: (Int, Int) -> String,
        checkCancelled: () -> Unit = {}): Edit {
        require(caret in 0..length)
        var cache = ""; var cacheStart = -1
        fun charAt(index: Int): Char {
            if (index !in cacheStart until cacheStart + cache.length) {
                cacheStart = index; cache = read(index, minOf(8192, length - index))
                check(cache.isNotEmpty()) { "The note changed. Try again." }
            }
            return cache[index - cacheStart]
        }
        fun lineAt(start: Int): Line {
            val out = StringBuilder(); var end = start; var oversized = false
            while (end < length && charAt(end) != '\n') {
                if (end % 4096 == 0) checkCancelled()
                if (out.length < MAX_TABLE_CHARACTERS) out.append(charAt(end)) else oversized = true
                end++
            }
            return Line(start, end, out.toString().removeSuffix("\r"), oversized)
        }
        // Track fences using streamed lines. Table syntax inside code is never rewritten.
        var position = 0; var marker: Char? = null; var markerSize = 0
        val candidate = ArrayList<Line>(); var candidateChars = 0; var candidateOversized = false
        var current = Line(0, 0, "")
        while (position <= caret) {
            checkCancelled(); current = lineAt(position)
            val match = fence.matchEntire(current.text)
            if (match != null) {
                val run = match.groupValues[1]
                if (marker == null) { marker = run[0]; markerSize = run.length }
                else if (run[0] == marker && run.length >= markerSize && match.groupValues[2].isBlank()) marker = null
                candidate.clear(); candidateChars = 0; candidateOversized = false
            } else if (marker == null && cells(current.text) != null) {
                candidateChars += current.text.length
                if (current.oversized || candidateChars > MAX_TABLE_CHARACTERS || candidate.size >= MAX_ROWS) candidateOversized = true
                if (!candidateOversized) candidate += current
            } else { candidate.clear(); candidateChars = 0; candidateOversized = false }
            if (current.end >= caret || current.end == length) break
            position = current.end + 1
        }
        require(marker == null) { "Move the cursor outside the code block to edit a table." }
        val newline = if (current.end > current.start && charAt(current.end - 1) == '\r') "\r\n" else "\n"
        if (command.startsWith("table:new:")) {
            val dimensions = command.split(':')
            val rows = dimensions.getOrNull(2)?.toIntOrNull() ?: 3
            val columns = dimensions.getOrNull(3)?.toIntOrNull() ?: 3
            require(rows in 1..20 && columns in 1..MAX_COLUMNS) { "Choose 1–20 rows and 1–32 columns." }
            val lines = mutableListOf(render(List(columns) { "Column ${it + 1}" }), render(List(columns) { "---" }))
            repeat(rows) { lines += render(List(columns) { "" }) }
            var insertion = caret
            if (candidate.isNotEmpty() || candidateOversized) {
                var end = current.end; var next = current.end + 1
                while (next < length) {
                    checkCancelled(); val row = lineAt(next)
                    if (cells(row.text) == null) break
                    end = row.end; next = row.end + 1
                }
                insertion = end - if (end > 0 && charAt(end - 1) == '\r') 1 else 0
            }
            val prefix = if (insertion > 0 && charAt(insertion - 1) != '\n') newline + newline else ""
            val value = prefix + lines.joinToString(newline) + newline + newline
            return Edit(insertion, insertion, value, insertion + prefix.length + 2)
        }
        require(!current.oversized && !candidateOversized) { "This table or line is too large for table tools. Edit its Markdown directly." }
        require(candidate.size >= 2) { "Place the cursor in a Markdown table first." }
        // Include following rows, but stop before a second table or surrounding prose.
        var following = current.end + 1
        while (following < length) {
            checkCancelled(); val row = lineAt(following)
            if (cells(row.text) == null) break
            require(!row.oversized) { "This table is too large for table tools. Edit its Markdown directly." }
            candidate += row; candidateChars += row.text.length
            require(candidateChars <= MAX_TABLE_CHARACTERS && candidate.size <= MAX_ROWS) {
                "This table is too large for table tools. Edit its Markdown directly."
            }
            following = row.end + 1
        }
        val rows = candidate.map { requireNotNull(cells(it.text)).toMutableList() }.toMutableList()
        val columns = rows[0].size
        require(columns in 1..MAX_COLUMNS && rows[1].size == columns && rows[1].all { delimiter.matches(it) }) {
            "A table needs a header and a separator row, such as | --- | --- |."
        }
        require(rows.all { it.size <= columns }) { "Some rows have extra cells. Fix them before using table tools; no data was changed." }
        rows.forEach { while (it.size < columns) it += "" }
        val rowIndex = candidate.indexOfLast { it.start <= caret }.coerceAtLeast(0)
        val column = columnAt(current.text, caret - current.start).coerceIn(0, columns - 1)
        var targetRow = rowIndex; var targetColumn = column
        when (command) {
            "table:row" -> {
                targetRow = (rowIndex + 1).coerceAtLeast(2)
                rows.add(targetRow, MutableList(columns) { "" })
                targetColumn = 0
            }
            "table:column" -> {
                require(columns < MAX_COLUMNS) { "Table tools support up to $MAX_COLUMNS columns." }
                targetColumn = column + 1
                rows.forEachIndexed { index, row -> row.add(targetColumn, if (index == 1) "---" else "") }
            }
            "table:remove-row" -> {
                require(rowIndex >= 2) { "Select a body row. The table header is retained." }
                rows.removeAt(rowIndex); targetRow = rowIndex.coerceAtMost(rows.lastIndex)
            }
            "table:remove-column" -> {
                require(columns > 1) { "The last column is retained. Select the table text to delete the whole table." }
                rows.forEach { it.removeAt(column) }; targetColumn = column.coerceAtMost(columns - 2)
            }
            "table:left", "table:center", "table:right" -> rows[1][column] = when (command) {
                "table:left" -> ":---"; "table:center" -> ":---:"; else -> "---:"
            }
            "table:next-cell" -> {
                targetColumn++
                if (targetColumn >= columns) { targetColumn = 0; targetRow++ }
                if (targetRow == 1) targetRow = 2
                if (targetRow > rows.lastIndex) rows += MutableList(columns) { "" }
            }
            else -> error("Unknown table action")
        }
        require(rows.size <= MAX_ROWS) { "Table tools support up to $MAX_ROWS rows." }
        val rendered = rows.map(::render)
        val replacement = rendered.joinToString(newline)
        require(replacement.length <= MAX_TABLE_CHARACTERS) { "The table is too large for table tools. No data was changed." }
        val beforeCell = rows[targetRow].take(targetColumn).sumOf { it.length + 3 }
        val nextCaret = candidate.first().start + rendered.take(targetRow).sumOf { it.length + newline.length } + 2 + beforeCell
        return Edit(candidate.first().start, candidate.last().end -
            (if (candidate.last().end > 0 && charAt(candidate.last().end - 1) == '\r') 1 else 0), replacement, nextCaret)
    }
    private fun render(cells: List<String>) = "| " + cells.joinToString(" | ") + " |"
    private fun columnAt(line: String, position: Int): Int {
        var column = if (line.trimStart().startsWith('|')) -1 else 0
        var escaped = false
        line.take(position.coerceIn(0, line.length)).forEach {
            if (it == '|' && !escaped) column++
            escaped = it == '\\' && !escaped
        }
        return column.coerceAtLeast(0)
    }
    internal fun cells(line: String): List<String>? {
        val trimmed = line.trim()
        if (!trimmed.contains('|') || trimmed.startsWith('>')) return null
        val cells = ArrayList<String>(); val part = StringBuilder(); var escaped = false
        trimmed.forEach { char ->
            if (char == '|' && !escaped) { cells += part.toString().trim(); part.clear() }
            else part.append(char)
            escaped = char == '\\' && !escaped
        }
        cells += part.toString().trim()
        if (trimmed.startsWith('|')) cells.removeAt(0)
        if (trimmed.endsWith('|') && cells.lastOrNull() == "") cells.removeAt(cells.lastIndex)
        return cells.takeIf { it.isNotEmpty() }
    }
}

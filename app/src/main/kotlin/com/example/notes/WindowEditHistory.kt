package com.example.notes

/** Undo stores changed ranges only; this history never owns a complete note. */
class WindowEditHistory {
    private data class Edit(val start: Int, val removed: String, val inserted: String)
    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun record(before: String, after: String) {
        if (before == after) return
        var start = 0
        while (start < minOf(before.length, after.length) && before[start] == after[start]) start++
        if (start > 0 && Character.isHighSurrogate(before[start - 1])) start--
        var oldEnd = before.length
        var newEnd = after.length
        while (oldEnd > start && newEnd > start && before[oldEnd - 1] == after[newEnd - 1]) { oldEnd--; newEnd-- }
        if (oldEnd < before.length && oldEnd > start && Character.isLowSurrogate(before[oldEnd])) { oldEnd++; newEnd++ }
        undo.addLast(Edit(start, before.substring(start, oldEnd), after.substring(start, newEnd)))
        if (undo.size > PieceTable.MAX_UNDO_OPERATIONS) undo.removeFirst()
        redo.clear()
    }

    private fun apply(current: String, edit: Edit, inverse: Boolean): String? {
        val expected = if (inverse) edit.inserted else edit.removed
        val replacement = if (inverse) edit.removed else edit.inserted
        if (edit.start > current.length || !current.regionMatches(edit.start, expected, 0, expected.length)) return null
        return current.replaceRange(edit.start, edit.start + expected.length, replacement)
    }

    fun undo(current: String): String? {
        val edit = undo.lastOrNull() ?: return null
        val result = apply(current, edit, true) ?: return null
        undo.removeLast(); redo.addLast(edit)
        return result
    }

    fun redo(current: String): String? {
        val edit = redo.lastOrNull() ?: return null
        val result = apply(current, edit, false) ?: return null
        redo.removeLast(); undo.addLast(edit)
        return result
    }
}

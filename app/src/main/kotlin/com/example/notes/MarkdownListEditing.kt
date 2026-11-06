package com.example.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicInteger

/** Prefix syntax cache: bounded reads, invalidation is atomic and never blocks the UI thread. */
class MarkdownFenceIndex {
    private data class State(val marker: Char? = null, val size: Int = 0)
    private val checkpoints = TreeMap<Int, State>().apply { put(0, State()) }
    private val dirty = AtomicInteger(Int.MAX_VALUE)
    fun invalidate(from: Int) { dirty.getAndUpdate { minOf(it, from.coerceAtLeast(0)) } }
    /** One background owner at a time; read() may serve immutable snapshot or disk ranges. */
    fun inside(caret: Int, read: (Int, Int) -> String, check: () -> Unit = {}): Boolean {
        val changed = dirty.getAndSet(Int.MAX_VALUE)
        if (changed != Int.MAX_VALUE) checkpoints.tailMap(changed, false).clear()
        val entry = checkpoints.floorEntry(caret) ?: checkpoints.firstEntry()
        var state = entry.value; var start = entry.key; var lastCheckpoint = start
        var prefix = StringBuilder(); var remainderNonBlank = false
        var offset = start
        fun line() {
            val match = FENCE.matchEntire(prefix.toString().removeSuffix("\r"))
            if (match != null) {
                val run = match.groupValues[1]
                state = if (state.marker == null) State(run[0], run.length)
                else if (run[0] == state.marker && run.length >= state.size && match.groupValues[2].isBlank() && !remainderNonBlank) State()
                else state
            }
            prefix = StringBuilder(); remainderNonBlank = false
        }
        while (offset < caret) {
            check(); val chunk = read(offset, minOf(8192, caret - offset))
            require(chunk.isNotEmpty()) { "The note changed. Retry the edit." }
            chunk.forEach { char ->
                offset++
                if (char == '\n') {
                    line(); start = offset
                    if (start - lastCheckpoint >= 8192) { checkpoints[start] = state; lastCheckpoint = start }
                } else if (prefix.length < 1024) prefix.append(char)
                else if (!char.isWhitespace()) remainderNonBlank = true
            }
        }
        if (prefix.isNotEmpty()) line()
        return state.marker != null
    }
    companion object { private val FENCE = Regex("^\\s*(?:[-+*] |\\d+[.)] )?(`{3,}|~{3,})(.*)$") }
}

object MarkdownListEditing {
    const val MAX_LINE_CHARACTERS = 4096
    data class Edit(val start: Int, val end: Int, val text: String, val caret: Int)
    private val prefix = Regex("^([ \\t]*)(?:(?:>[ \\t]*)*)(?:([-+*])|([0-9]{1,18})([.)])|([a-z]{1,3}|[A-Z]{1,3})([.)]))[ \\t]+(?:\\[([ xX])]([ \\t]+))?")

    fun canHandle(line: String, column: Int, backspace: Boolean): Boolean {
        if (line.length > MAX_LINE_CHARACTERS) return false
        val found = prefix.find(line) ?: return false
        return if (backspace) column == found.value.length else column >= found.value.length
    }
    fun around(length: Int, caret: Int, read: (Int, Int) -> String): Pair<Int, String>? {
        val start = (caret - MAX_LINE_CHARACTERS).coerceAtLeast(0)
        val text = read(start, minOf(MAX_LINE_CHARACTERS * 2, length - start))
        val local = caret - start
        val previousBreak = if (local == 0) -1 else text.lastIndexOf('\n', local - 1)
        if (previousBreak < 0 && start > 0) return null
        val nextBreak = text.indexOf('\n', local)
        if (nextBreak < 0 && start + text.length < length) return null
        val end = if (nextBreak < 0) text.length else nextBreak
        val line = text.substring(previousBreak + 1, end).removeSuffix("\r")
        return (start + previousBreak + 1) to line
    }
    fun enter(line: String, column: Int, lineStart: Int, insideFence: Boolean): Edit? {
        if (insideFence || line.length > MAX_LINE_CHARACTERS || column !in 0..line.length) return null
        val match = prefix.find(line) ?: return null
        if (column < match.value.length) return null
        val base = match.value
        val empty = line.substring(base.length).isBlank()
        val indent = match.groupValues[1]
        if (empty) {
            val replacement = if (indent.isNotEmpty()) {
                val less = if (indent.endsWith('\t')) indent.dropLast(1) else indent.dropLast(minOf(2, indent.takeLastWhile { it == ' ' }.length))
                less + base.drop(indent.length)
            } else base.substringBeforeLast(match.groupValues[2].ifEmpty { match.groupValues[3].ifEmpty { match.groupValues[5] } }).takeWhile { it == '>' || it.isWhitespace() }
            return Edit(lineStart, lineStart + line.length, replacement, lineStart + replacement.length)
        }
        val leader = base.takeWhile { it.isWhitespace() || it == '>' }
        val marker = when {
            match.groupValues[2].isNotEmpty() -> match.groupValues[2]
            match.groupValues[3].isNotEmpty() -> {
                val number = match.groupValues[3].toLongOrNull() ?: return null
                if (number == Long.MAX_VALUE) return null
                (number + 1).toString() + match.groupValues[4]
            }
            else -> nextLetters(match.groupValues[5]) + match.groupValues[6]
        }
        val next = "\n" + leader + marker + " " + if (match.groupValues[7].isNotEmpty()) "[ ] " else ""
        return Edit(lineStart + column, lineStart + column, next, lineStart + column + next.length)
    }
    fun backspace(line: String, column: Int, lineStart: Int, insideFence: Boolean): Edit? {
        if (insideFence || line.length > MAX_LINE_CHARACTERS) return null
        val match = prefix.find(line) ?: return null
        if (column != match.value.length) return null
        val keep = match.value.takeWhile { it.isWhitespace() || it == '>' }
        return Edit(lineStart, lineStart + column, keep, lineStart + keep.length)
    }
    private fun nextLetters(value: String): String {
        val upper = value[0].isUpperCase(); val base = if (upper) 'A' else 'a'
        var number = value.fold(0) { n, ch -> n * 26 + (ch - base + 1) } + 1
        val result = StringBuilder()
        while (number > 0) { number--; result.append(base + number % 26); number /= 26 }
        return result.reverse().toString()
    }
    /** Only a single typed Enter/Backspace, never a bulk paste, toolbar edit or IME composition. */
    fun adapt(previous: TextFieldValue, incoming: TextFieldValue): TextFieldValue {
        if (!previous.selection.collapsed || !incoming.selection.collapsed || previous.composition != null || incoming.composition != null) return incoming
        val caret = previous.selection.start
        val isEnter = incoming.text.length == previous.text.length + 1 && incoming.selection.start == caret + 1 &&
            incoming.text.getOrNull(caret) == '\n'
        val isBackspace = caret > 0 && incoming.text.length == previous.text.length - 1 && incoming.selection.start == caret - 1
        if (!isEnter && !isBackspace) return incoming
        val changed = if (isEnter) caret else caret - 1
        if (!incoming.text.regionMatches(0, previous.text, 0, changed) ||
            !incoming.text.regionMatches(changed + if (isEnter) 1 else 0, previous.text,
                changed + if (isBackspace) 1 else 0, previous.text.length - changed - if (isBackspace) 1 else 0)) return incoming
        val start = if (caret == 0) 0 else previous.text.lastIndexOf('\n', caret - 1) + 1
        val end = previous.text.indexOf('\n', caret).let { if (it < 0) previous.text.length else it }
        if (end - start > MAX_LINE_CHARACTERS) return incoming
        if (!canHandle(previous.text.substring(start, end), caret - start, isBackspace)) return incoming
        val fenced = MarkdownFenceIndex().inside(caret, { at, count -> previous.text.substring(at, at + count) })
        val edit = if (isEnter) enter(previous.text.substring(start, end), caret - start, start, fenced)
            else backspace(previous.text.substring(start, end), caret - start, start, fenced)
        return if (edit == null) incoming else TextFieldValue(previous.text.replaceRange(edit.start, edit.end, edit.text), TextRange(edit.caret))
    }
}

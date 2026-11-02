package com.example.notes

object NoteEditorWindows {
    const val MAX_CHARACTERS = 512
    const val MAX_LINES = 24
    const val SECTION_INPUT_CHARACTERS = 2048
    const val SECTION_INPUT_LINES = 48
    fun sectionNeedsStreaming(text: String): Boolean = text.length > SECTION_INPUT_CHARACTERS ||
        text.asSequence().filter { it == '\n' }.take(SECTION_INPUT_LINES).count() >= SECTION_INPUT_LINES
    data class Window(val start: Int, val end: Int, val text: String)

    fun requiresWindow(text: String): Boolean =
        text.length > MAX_CHARACTERS || text.asSequence().filter { it == '\n' }.take(MAX_LINES).count() >= MAX_LINES

    fun at(text: String, requestedStart: Int): Window {
        var start = requestedStart.coerceIn(0, text.length)
        if (start > 0 && start < text.length && Character.isLowSurrogate(text[start]) && Character.isHighSurrogate(text[start - 1])) start--
        var end = minOf(text.length, start + MAX_CHARACTERS)
        if (end < text.length && end > start && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        var lines = 0
        for (i in start until end) if (text[i] == '\n' && ++lines >= MAX_LINES) { end = i + 1; break }
        return Window(start, end, text.substring(start, end))
    }

    fun utf8Bytes(text: CharSequence, checkCancelled: () -> Unit = {}): Long {
        var index = 0
        var bytes = 0L
        var nextCancellationCheck = 0
        while (index < text.length) {
            if (index >= nextCancellationCheck) { checkCancelled(); nextCancellationCheck = index + 4096 }
            val char = text[index]
            when {
                char.code < 0x80 -> bytes++
                char.code < 0x800 -> bytes += 2
                Character.isHighSurrogate(char) && index + 1 < text.length && Character.isLowSurrogate(text[index + 1]) -> { bytes += 4; index++ }
                Character.isSurrogate(char) -> bytes++ // JVM UTF-8 encoder replacement for an unpaired surrogate.
                else -> bytes += 3
            }
            index++
        }
        return bytes
    }
}

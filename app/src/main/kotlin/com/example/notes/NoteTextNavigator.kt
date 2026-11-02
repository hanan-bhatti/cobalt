package com.example.notes

import java.io.InputStream

/** Bounded-memory, case-sensitive UTF-8 search and heading extraction. */
object NoteTextNavigator {
    data class Heading(val offset: Long, val level: Int, val title: String)
    const val MAX_HEADINGS = 500

    fun find(input: InputStream, query: String, start: Long = 0, checkCancelled: () -> Unit = {}): Long? {
        require(start >= 0 && query.isNotEmpty() && query.length <= 256)
        val pattern = query.toByteArray(Charsets.UTF_8)
        val prefix = IntArray(pattern.size)
        var matched = 0
        for (i in 1 until pattern.size) {
            while (matched > 0 && pattern[i] != pattern[matched]) matched = prefix[matched - 1]
            if (pattern[i] == pattern[matched]) matched++
            prefix[i] = matched
        }
        matched = 0
        var offset = 0L
        val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) return null
            for (i in 0 until count) {
                val byte = buffer[i]
                if (offset >= start) {
                    while (matched > 0 && byte != pattern[matched]) matched = prefix[matched - 1]
                    if (byte == pattern[matched]) matched++
                    if (matched == pattern.size) return offset - pattern.size + 1
                }
                offset++
            }
        }
    }

    fun headings(input: InputStream, checkCancelled: () -> Unit = {}): List<Heading> {
        val result = mutableListOf<Heading>()
        val line = java.io.ByteArrayOutputStream(512)
        var offset = 0L
        var lineStart = 0L
        var fence: Char? = null
        var fenceLength = 0
        fun consumeLine() {
            val text = line.toByteArray().toString(Charsets.UTF_8).removeSuffix("\r")
            val indent = text.takeWhile { it == ' ' }.length
            if (indent <= 3) {
                val body = text.drop(indent)
                val first = body.firstOrNull()
                val run = if (first == '`' || first == '~') body.takeWhile { it == first }.length else 0
                if (run >= 3) {
                    if (fence == null) { fence = first; fenceLength = run }
                    else if (first == fence && run >= fenceLength && body.drop(run).isBlank()) fence = null
                } else if (fence == null) {
                    val level = body.takeWhile { it == '#' }.length
                    if (level in 1..6 && body.getOrNull(level)?.isWhitespace() == true) {
                        result.add(Heading(lineStart, level, body.drop(level).trim().trimEnd('#').trim().take(160)))
                    }
                }
            }
            line.reset()
        }
        val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
        while (result.size < MAX_HEADINGS) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) { if (line.size() > 0) consumeLine(); break }
            for (i in 0 until count) {
                val byte = buffer[i]
                if (byte == 10.toByte()) {
                    consumeLine(); lineStart = offset + 1
                    if (result.size >= MAX_HEADINGS) break
                } else if (line.size() < 1024) line.write(byte.toInt())
                offset++
            }
        }
        return result
    }
}

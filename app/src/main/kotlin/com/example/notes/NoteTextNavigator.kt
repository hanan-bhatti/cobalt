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

    /**
     * Bounded-memory case-insensitive UTF-8 search that collects all matching byte offsets.
     */
    fun findAll(input: InputStream, query: String, limit: Int = 1000, checkCancelled: () -> Unit = {}): List<Long> {
        if (query.isEmpty() || query.length > 256) return emptyList()
        val results = ArrayList<Long>()
        val queryLower = query.lowercase(java.util.Locale.ROOT)
        val queryChars = queryLower.toCharArray()
        val queryLen = queryChars.size

        val prefix = IntArray(queryLen)
        var matched = 0
        for (i in 1 until queryLen) {
            while (matched > 0 && queryChars[i] != queryChars[matched]) matched = prefix[matched - 1]
            if (queryChars[i] == queryChars[matched]) matched++
            prefix[i] = matched
        }

        matched = 0
        var currentByteOffset = 0L
        val matchByteOffsets = LongArray(queryLen)

        val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
        var bufPos = 0
        var bufCount = 0

        fun nextCharAndByte(): Pair<Char, Long>? {
            if (bufPos >= bufCount) {
                checkCancelled()
                bufCount = input.read(buffer)
                bufPos = 0
                if (bufCount < 0) return null
            }
            val firstByte = buffer[bufPos++].toInt() and 0xFF
            val charStartByte = currentByteOffset

            val char: Char
            when {
                firstByte < 0x80 -> {
                    currentByteOffset += 1
                    char = firstByte.toChar()
                }
                firstByte and 0xE0 == 0xC0 -> {
                    if (bufPos >= bufCount) {
                        checkCancelled()
                        bufCount = input.read(buffer)
                        bufPos = 0
                        if (bufCount < 0) { currentByteOffset += 1; return '?' to charStartByte }
                    }
                    val b2 = buffer[bufPos++].toInt() and 0x3F
                    currentByteOffset += 2
                    char = (((firstByte and 0x1F) shl 6) or b2).toChar()
                }
                firstByte and 0xF0 == 0xE0 -> {
                    val b2 = if (bufPos < bufCount) buffer[bufPos++].toInt() and 0x3F else {
                        checkCancelled()
                        bufCount = input.read(buffer); bufPos = 0
                        if (bufCount < 0) 0 else buffer[bufPos++].toInt() and 0x3F
                    }
                    val b3 = if (bufPos < bufCount) buffer[bufPos++].toInt() and 0x3F else {
                        checkCancelled()
                        bufCount = input.read(buffer); bufPos = 0
                        if (bufCount < 0) 0 else buffer[bufPos++].toInt() and 0x3F
                    }
                    currentByteOffset += 3
                    char = (((firstByte and 0x0F) shl 12) or (b2 shl 6) or b3).toChar()
                }
                else -> {
                    currentByteOffset += 1
                    char = '?'
                }
            }
            return char to charStartByte
        }

        while (results.size < limit) {
            val next = nextCharAndByte() ?: break
            val charLower = next.first.lowercaseChar()
            val charByte = next.second

            while (matched > 0 && charLower != queryChars[matched]) {
                matched = prefix[matched - 1]
            }
            if (charLower == queryChars[matched]) {
                matchByteOffsets[matched] = charByte
                matched++
                if (matched == queryLen) {
                    results.add(matchByteOffsets[0])
                    matched = 0 // non-overlapping
                }
            }
        }
        return results
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

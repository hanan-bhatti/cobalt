package com.example.notes

/** Literal UTF-16 search with O(query length) working memory; never builds a document String. */
object ContinuousTextSearch {
    const val MAX_QUERY = 256
    const val MAX_REPLACEMENTS = 10_000
    /** Translate a reader's UTF-8 block boundaries into the editor's global UTF-16 positions. */
    fun utf16Range(text: CharSequence, byteStart: Long, byteEnd: Long, checkCancelled: () -> Unit = {}): Pair<Int, Int> {
        require(byteStart >= 0 && byteEnd >= byteStart)
        var index = 0
        var bytes = 0L
        var start = -1
        while (true) {
            if (index % 4096 == 0) checkCancelled()
            if (bytes == byteStart) start = index
            if (bytes == byteEnd) { require(start >= 0); return start to index }
            require(index < text.length && bytes < byteEnd) { "Range is not on UTF-8 boundaries" }
            val char = text[index++]
            bytes += when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                Character.isHighSurrogate(char) && index < text.length && Character.isLowSurrogate(text[index]) -> { index++; 4 }
                Character.isSurrogate(char) -> 1
                else -> 3
            }
        }
    }
    /** Streaming equivalent for byte ranges from a disk-backed formatted preview. */
    fun utf16Range(source: java.io.InputStream, byteStart: Long, byteEnd: Long, checkCancelled: () -> Unit = {}): Pair<Int, Int> {
        require(byteStart >= 0 && byteEnd >= byteStart)
        val input = source.buffered(NoteSizePolicy.IO_CHUNK_BYTES)
        var bytes = 0L; var units = 0; var start = -1
        while (true) {
            checkCancelled()
            if (bytes == byteStart) start = units
            if (bytes == byteEnd) { require(start >= 0); return start to units }
            require(bytes < byteEnd)
            val first = input.read(); require(first >= 0)
            val width = when { first < 0x80 -> 1; first in 0xC2..0xDF -> 2; first in 0xE0..0xEF -> 3; first in 0xF0..0xF4 -> 4; else -> error("Invalid UTF-8 boundary") }
            repeat(width - 1) { require(input.read() in 0x80..0xBF) }
            bytes += width; units += if (width == 4) 2 else 1
        }
    }
    fun matches(text: CharSequence, query: String, start: Int = 0,
        limit: Int = MAX_REPLACEMENTS + 1, checkCancelled: () -> Unit = {}): List<Int> {
        require(query.isNotEmpty() && query.length <= MAX_QUERY && start in 0..text.length && limit > 0)
        val prefix = IntArray(query.length)
        var matched = 0
        for (index in 1 until query.length) {
            while (matched > 0 && query[index] != query[matched]) matched = prefix[matched - 1]
            if (query[index] == query[matched]) matched++
            prefix[index] = matched
        }
        matched = 0
        val result = ArrayList<Int>()
        for (index in start until text.length) {
            if (index % 4096 == 0) checkCancelled()
            val char = text[index]
            while (matched > 0 && char != query[matched]) matched = prefix[matched - 1]
            if (char == query[matched]) matched++
            if (matched == query.length) {
                result.add(index - query.length + 1)
                if (result.size == limit) break
                matched = 0 // Non-overlapping matches agree with replacement semantics.
            }
        }
        return result
    }
}

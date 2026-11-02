package com.example.notes

import java.io.InputStream

/** Sparse byte offsets, built incrementally without decoding or retaining whole lines. */
class Utf8LineIndex private constructor(private val offsets: LongArray, val lineCount: Long, val byteLength: Long) {
    fun byteOffset(line: Long, open: (Long) -> InputStream, checkCancelled: () -> Unit = {}): Long {
        require(line in 0 until lineCount)
        val sample = (line / LINES_PER_SAMPLE).toInt()
        val baseLine = sample.toLong() * LINES_PER_SAMPLE
        val base = offsets[sample]
        if (line == baseLine) return base
        var remaining = line - baseLine
        var position = base
        var previousCr = false
        val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
        open(base).use { input ->
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) return byteLength
                for (i in 0 until count) {
                    val value = buffer[i].toInt() and 0xff
                    // A CR-ended target line starts after a following LF, if present.
                    if (previousCr && remaining == 0L) return if (value == 10) position + 1 else position
                    if (value == 13 || (value == 10 && !previousCr)) {
                        remaining--
                        if (remaining == 0L && value == 10) return position + 1
                    }
                    previousCr = value == 13
                    position++
                }
            }
        }
    }

    class Builder {
        private var samples = LongArray(16)
        private var used = 1
        private var position = 0L
        private var lines = 1L
        private var previousCr = false
        fun append(bytes: ByteArray, count: Int = bytes.size) {
            require(count in 0..bytes.size)
            for (i in 0 until count) {
                val value = bytes[i].toInt() and 0xff
                if (value == 10 && previousCr) {
                    if ((lines - 1) % LINES_PER_SAMPLE == 0L) samples[used - 1] = position + 1
                } else if (value == 10 || value == 13) {
                    lines++
                    if ((lines - 1) % LINES_PER_SAMPLE == 0L) {
                        if (used == samples.size) samples = samples.copyOf(samples.size * 2)
                        samples[used++] = position + 1
                    }
                }
                previousCr = value == 13
                position++
            }
        }
        fun snapshot() = Utf8LineIndex(samples.copyOf(used), lines, position)
    }

    companion object {
        const val LINES_PER_SAMPLE = 256
        fun build(input: InputStream, checkCancelled: () -> Unit = {}): Utf8LineIndex {
            val builder = Builder()
            val bytes = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
            while (true) {
                checkCancelled()
                val count = input.read(bytes)
                if (count < 0) return builder.snapshot()
                builder.append(bytes, count)
            }
        }
    }
}

package com.example.notes

import java.io.InputStream
import java.io.IOException

class EditorCapacityException : IOException("This note needs more memory than the continuous editor can safely use on this device. Read it as plain text or export it; the complete note is retained.")

/** Admission guard for the resident native document, including pathological millions-of-empty-lines files. */
object ContinuousEditorCapacity {
    const val MAX_MODEL_BUDGET = 128L * 1024 * 1024
    const val BYTES_PER_LINE_ESTIMATE = 160L // ContentLine, line list, wrap/index metadata and spare capacity.
    fun budget(maxHeap: Long, usedHeap: Long): Long =
        minOf(MAX_MODEL_BUDGET, ((maxHeap - usedHeap).coerceAtLeast(0) * 3 / 4))
    fun estimate(bytes: Long, lines: Long): Long = bytes * 2 + lines * BYTES_PER_LINE_ESTIMATE
    fun replacementBudget(existingEstimate: Long, maxHeap: Long, usedHeap: Long): Long =
        minOf(MAX_MODEL_BUDGET, existingEstimate + budget(maxHeap, usedHeap))
    fun lineBreaks(text: CharSequence): Long {
        var lines = 0L
        var previousCr = false
        for (index in 0 until text.length) {
            val char = text[index]
            if (char == '\r' || (char == '\n' && !previousCr)) lines++
            previousCr = char == '\r'
        }
        return lines
    }

    fun check(input: InputStream, budget: Long, checkCancelled: () -> Unit = {}) {
        if (budget < BYTES_PER_LINE_ESTIMATE) throw EditorCapacityException()
        var bytes = 0L
        var lines = 1L
        var previousCr = false
        val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) return
            bytes += count
            for (index in 0 until count) {
                val value = buffer[index].toInt() and 0xff
                if (value == 13 || (value == 10 && !previousCr)) lines++
                previousCr = value == 13
            }
            // Deliberately conservative: budget for UTF-16 plus resident row/index objects.
            if (estimate(bytes, lines) > budget) throw EditorCapacityException()
        }
    }
}

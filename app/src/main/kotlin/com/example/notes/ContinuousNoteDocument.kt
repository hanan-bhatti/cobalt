package com.example.notes

import io.github.rosemoe.sora.text.Content
import java.io.InputStream
import kotlinx.coroutines.Job

/**
 * One logical UTF-16 document, retained across Activity recreation. No Android View is retained.
 * The native editor owns selection/IME and operation-based undo. Disk snapshots share immutable
 * lines using Sora's copy-on-write API, rather than copying the note into a String.
 */
class ContinuousNoteDocument(val content: Content, initialBytes: Long? = null) {
    @Volatile var byteLength: Long? = initialBytes
    val permittedBytes: Long = maxOf(NoteSizePolicy.IMPORT_MAX_BYTES, initialBytes ?: 0)
    @Volatile var revision: Long = 0
        private set
    var savedRevision: Long = 0
    var selectionStart: Int = 0
    var selectionEnd: Int = 0
    var scrollX: Int = 0
    var scrollY: Int = 0
    var onFreeze: ((Boolean) -> Unit)? = null
    var onFormat: ((String) -> Unit)? = null
    var pendingEdit: Job? = null
    var undoPressure: Long = 0
    var undoTrimNotified: Boolean = false
    var frozen: Boolean = false
        private set

    init { content.setMaxUndoStackSize(PieceTable.MAX_UNDO_OPERATIONS) }

    /** Called on Main, after a native content modification, never for selection-only events. */
    fun changed() { revision++ }
    fun freeze(value: Boolean) { frozen = value; onFreeze?.invoke(value) }

    /** IO thread only. Caller must close/release the snapshot after streaming it. */
    fun snapshot(): Content = content.copyTextShallow()

    companion object {
        /** A bounded UTF-8 encoder over a stable line snapshot, including original LF/CRLF/CR. */
        fun stream(snapshot: Content): InputStream = object : InputStream() {
            private var line = 0
            private var column = 0
            private var separator = false
            private var buffer = ByteArray(0)
            private var position = 0
            private fun fill(): Boolean {
                if (position < buffer.size) return true
                while (line < snapshot.lineCount) {
                    val text = snapshot.getLine(line)
                    if (!separator && column < text.length) {
                        var end = minOf(column + 2048, text.length)
                        if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
                        buffer = text.subSequence(column, end).toString().toByteArray(Charsets.UTF_8)
                        column = end
                    } else if (!separator) {
                        separator = true
                        buffer = text.lineSeparator.content.toByteArray(Charsets.UTF_8)
                    } else {
                        line++; column = 0; separator = false
                        continue
                    }
                    position = 0
                    if (buffer.isNotEmpty()) return true
                }
                return false
            }
            override fun read(): Int = if (fill()) buffer[position++].toInt() and 0xff else -1
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
                if (len == 0) return 0
                if (!fill()) return -1
                val count = minOf(len, buffer.size - position)
                buffer.copyInto(bytes, off, position, position + count); position += count
                return count
            }
        }
    }
}

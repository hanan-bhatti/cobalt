package com.example.notes

import java.io.EOFException
import java.io.InputStream

/** Random-access immutable bytes. Implementations do IO only when read is called. */
interface NoteByteSource {
    val size: Long
    fun read(offset: Long, count: Int): ByteArray
}

class FileNoteByteSource(private val store: NoteBodyStore, private val id: String,
    private val hash: String, override val size: Long) : NoteByteSource {
    override fun read(offset: Long, count: Int) = store.readRange(id, hash, offset, count)
}

/**
 * Byte-addressed piece table. Original and appended fragments are immutable sources;
 * operations retain descriptors, not document copies. Callers supply UTF-8 boundaries.
 * All methods are confined to the owning background editing coroutine.
 */
class PieceTable(original: NoteByteSource) {
    private data class Piece(val source: NoteByteSource, val offset: Long, val length: Long)
    private data class Edit(val start: Long, val removed: List<Piece>, val inserted: List<Piece>)
    private var pieces = if (original.size == 0L) emptyList() else listOf(Piece(original, 0, original.size))
    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()
    var size: Long = original.size
        private set
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    init { require(original.size >= 0) }

    private fun length(parts: List<Piece>) = parts.fold(0L) { sum, part -> Math.addExact(sum, part.length) }

    private fun slice(start: Long, end: Long): List<Piece> {
        require(start in 0..end && end <= size)
        val result = mutableListOf<Piece>()
        var cursor = 0L
        for (piece in pieces) {
            val from = maxOf(start, cursor)
            val until = minOf(end, cursor + piece.length)
            if (from < until) result.add(Piece(piece.source, piece.offset + from - cursor, until - from))
            cursor += piece.length
            if (cursor >= end) break
        }
        return result
    }

    private fun splice(start: Long, removedBytes: Long, inserted: List<Piece>) {
        val end = Math.addExact(start, removedBytes)
        val newSize = Math.addExact(size - removedBytes, length(inserted))
        val next = slice(0, start) + inserted + slice(end, size)
        val merged = mutableListOf<Piece>()
        for (piece in next) {
            val previous = merged.lastOrNull()
            if (previous != null && previous.source === piece.source && previous.offset + previous.length == piece.offset) {
                merged[merged.lastIndex] = previous.copy(length = previous.length + piece.length)
            } else if (piece.length > 0) merged.add(piece)
        }
        pieces = merged
        size = newSize
    }

    fun replace(start: Long, end: Long, appended: NoteByteSource) {
        require(appended.size >= 0)
        val removed = slice(start, end)
        val inserted = if (appended.size == 0L) emptyList() else listOf(Piece(appended, 0, appended.size))
        splice(start, end - start, inserted)
        undo.addLast(Edit(start, removed, inserted))
        if (undo.size > MAX_UNDO_OPERATIONS) undo.removeFirst()
        redo.clear()
    }

    fun undo(): Boolean {
        val edit = undo.removeLastOrNull() ?: return false
        splice(edit.start, length(edit.inserted), edit.removed)
        redo.addLast(edit)
        return true
    }

    fun redo(): Boolean {
        val edit = redo.removeLastOrNull() ?: return false
        splice(edit.start, length(edit.removed), edit.inserted)
        undo.addLast(edit)
        return true
    }

    fun openStream(start: Long = 0, end: Long = size): InputStream {
        val snapshot = slice(start, end)
        return object : InputStream() {
            var index = 0
            var offset = 0L
            override fun read(): Int {
                val single = ByteArray(1)
                return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xff
            }
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
                if (len == 0) return 0
                if (index >= snapshot.size) return -1
                val piece = snapshot[index]
                val count = minOf(len.toLong(), NoteSizePolicy.IO_CHUNK_BYTES.toLong(), piece.length - offset).toInt()
                val data = piece.source.read(piece.offset + offset, count)
                if (data.size != count) throw EOFException("Piece source is incomplete; saved note retained")
                data.copyInto(bytes, off)
                offset += count
                if (offset == piece.length) { index++; offset = 0 }
                return count
            }
        }
    }

    companion object { const val MAX_UNDO_OPERATIONS = 100 }
}

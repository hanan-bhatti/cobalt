package com.example.notes

import java.io.EOFException
import java.io.InputStream

/** Random-access immutable bytes. Implementations do IO only when read is called. */
interface NoteByteSource {
    val size: Long
    fun read(offset: Long, count: Int): ByteArray
    fun openRange(offset: Long, length: Long): InputStream {
        require(offset >= 0 && offset <= size && length >= 0 && length <= size - offset)
        val source = this
        return object : InputStream() {
            var position = offset
            val end = offset + length
            override fun read(): Int {
                val single = ByteArray(1)
                return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xff
            }
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
                if (len == 0) return 0
                if (position == end) return -1
                val count = minOf(len.toLong(), NoteSizePolicy.IO_CHUNK_BYTES.toLong(), end - position).toInt()
                val data = source.read(position, count)
                if (data.size != count) throw EOFException("Piece source is incomplete; saved note retained")
                data.copyInto(bytes, off); position += count
                return count
            }
        }
    }
}

/** Immutable range of a shared source, allowing adjacent append ranges to coalesce. */
class NoteByteSlice(val source: NoteByteSource, val offset: Long, override val size: Long) : NoteByteSource {
    init { require(offset in 0..source.size && size in 0..(source.size - offset)) }
    override fun read(offset: Long, count: Int): ByteArray {
        require(offset in 0..size && count >= 0)
        return source.read(this.offset + offset, minOf(count.toLong(), size - offset).toInt())
    }
    override fun openRange(offset: Long, length: Long): InputStream {
        require(offset in 0..size && length in 0..(size - offset))
        return source.openRange(this.offset + offset, length)
    }
}

class FileNoteByteSource(private val store: NoteBodyStore, private val id: String,
    private val hash: String, override val size: Long) : NoteByteSource {
    override fun read(offset: Long, count: Int) = store.readRange(id, hash, offset, count)
    override fun openRange(offset: Long, length: Long): InputStream {
        require(offset >= 0 && offset <= size && length >= 0 && length <= size - offset)
        val input = store.open(id, hash)
        try {
            var remaining = offset
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped > 0) remaining -= skipped
                else { if (input.read() < 0) throw EOFException("Piece source is incomplete"); remaining-- }
            }
        } catch (error: Throwable) { input.close(); throw error }
        return object : InputStream() {
            var remaining = length
            override fun read(): Int {
                if (remaining == 0L) return -1
                val value = input.read()
                if (value < 0) throw EOFException("Piece source is incomplete; saved note retained")
                remaining--; return value
            }
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
                if (len == 0) return 0
                if (remaining == 0L) return -1
                val count = input.read(bytes, off, minOf(len.toLong(), remaining).toInt())
                if (count < 0) throw EOFException("Piece source is incomplete; saved note retained")
                remaining -= count; return count
            }
            override fun close() = input.close()
        }
    }
}

/**
 * Byte-addressed piece table. Original and appended fragments are immutable sources;
 * operations retain descriptors, not document copies. Callers supply UTF-8 boundaries.
 * All methods are confined to the owning background editing coroutine.
 */
class PieceTable(original: NoteByteSource, private val keepUndo: Boolean = true) {
    private data class Piece(val source: NoteByteSource, val offset: Long, val length: Long)
    private data class Edit(val start: Long, val removed: List<Piece>, val inserted: List<Piece>)
    private var pieces = if (original.size == 0L) emptyList() else listOf(Piece(original, 0, original.size))
    private var grouping = false
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
        val inserted = when {
            appended.size == 0L -> emptyList()
            appended is NoteByteSlice -> listOf(Piece(appended.source, appended.offset, appended.size))
            else -> listOf(Piece(appended, 0, appended.size))
        }
        splice(start, end - start, inserted)
        if (keepUndo && !grouping) {
            undo.addLast(Edit(start, removed, inserted))
            if (undo.size > MAX_UNDO_OPERATIONS) undo.removeFirst()
        }
        if (!grouping) redo.clear()
    }

    /** One logical user transaction, even when native IME emits delete then insert. */
    internal fun editGroup(action: () -> Unit) {
        check(!grouping)
        val before = pieces
        val beforeSize = size
        grouping = true
        try { action() }
        catch (error: Throwable) { pieces = before; size = beforeSize; throw error }
        finally { grouping = false }
        if (keepUndo) {
            undo.addLast(Edit(0, before, pieces))
            if (undo.size > MAX_UNDO_OPERATIONS) undo.removeFirst()
        }
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
            var active: InputStream? = null
            var closed = false
            override fun read(): Int {
                val single = ByteArray(1)
                return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xff
            }
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
                if (len == 0) return 0
                check(!closed) { "Stream is closed" }
                while (index < snapshot.size) {
                    val piece = snapshot[index]
                    val input = active ?: piece.source.openRange(piece.offset, piece.length).also { active = it }
                    val count = input.read(bytes, off, minOf(len, NoteSizePolicy.IO_CHUNK_BYTES))
                    if (count >= 0) return count
                    input.close(); active = null; index++
                }
                return -1
            }
            override fun close() { closed = true; active?.close(); active = null }
        }
    }

    /** Immutable range descriptors are shared; subsequent splices create a new list. */
    internal fun forkForRead(): PieceTable = PieceTable(object : NoteByteSource {
        override val size = 0L
        override fun read(offset: Long, count: Int) = ByteArray(0)
    }, keepUndo = false).also { it.pieces = pieces; it.size = size }

    /** Serialize descriptors only. Sources own the immutable bytes and must outlive this state. */
    internal fun writeState(output: java.io.DataOutputStream, sourceId: (NoteByteSource) -> Int) {
        val descriptors = pieces.size.toLong() + (undo + redo).sumOf { it.removed.size.toLong() + it.inserted.size }
        require(descriptors <= MAX_STATE_DESCRIPTORS) { "Editor history metadata is full; export unsaved edits before closing" }
        fun write(parts: List<Piece>) {
            output.writeInt(parts.size)
            parts.forEach { output.writeInt(sourceId(it.source)); output.writeLong(it.offset); output.writeLong(it.length) }
        }
        output.writeLong(size); write(pieces)
        for (stack in listOf(undo, redo)) {
            output.writeInt(stack.size)
            stack.forEach { output.writeLong(it.start); write(it.removed); write(it.inserted) }
        }
    }

    companion object {
        const val MAX_UNDO_OPERATIONS = 100
        private const val MAX_STATE_DESCRIPTORS = 1_000_000
        internal fun readState(input: java.io.DataInputStream, source: (Int) -> NoteByteSource): PieceTable {
            var descriptors = 0
            fun read(): List<Piece> {
                val count = input.readInt()
                require(count >= 0 && count <= MAX_STATE_DESCRIPTORS - descriptors) { "Invalid piece count" }
                descriptors += count
                return List(count) {
                    val body = source(input.readInt()); val offset = input.readLong(); val length = input.readLong()
                    require(offset in 0..body.size && length > 0 && length <= body.size - offset) { "Missing piece bytes" }
                    Piece(body, offset, length)
                }
            }
            val expected = input.readLong(); require(expected >= 0)
            val parts = read()
            val table = PieceTable(object : NoteByteSource {
                override val size = 0L
                override fun read(offset: Long, count: Int) = ByteArray(0)
            })
            table.pieces = parts; table.size = table.length(parts)
            require(table.size == expected) { "Invalid document size" }
            for (stack in listOf(table.undo, table.redo)) {
                val count = input.readInt(); require(count in 0..MAX_UNDO_OPERATIONS)
                repeat(count) {
                    val start = input.readLong(); require(start >= 0)
                    stack.addLast(Edit(start, read(), read()))
                }
            }
            return table
        }
    }
}

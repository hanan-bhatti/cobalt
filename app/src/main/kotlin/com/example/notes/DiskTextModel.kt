package com.example.notes

import java.io.*
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/** IO-confined live text and Undo/Redo. Only descriptors and a sparse UTF-8 index occupy RAM. */
class DiskTextModel private constructor(
    private val original: IndexedUtf8Units,
    private val appendFile: File,
    private val stateFile: File,
    private val fileOperations: NoteFileOperations,
    val base: NoteBodyRef,
    private val ownerId: String,
    private val checkAlive: () -> Unit,
    private val release: () -> Unit
) : Closeable {
    data class Position(val anchor: Int, val caret: Int)
    data class Status(val revision: Long, val characters: Int, val bytes: Long, val selection: Position,
        val canUndo: Boolean, val canRedo: Boolean)
    private data class Change(val before: Position, val after: Position, val beforeBytes: Long, val afterBytes: Long)
    private val added = AppendSource(appendFile)
    private var table = PieceTable(original)
    private val undo = ArrayDeque<Change>()
    private val redo = ArrayDeque<Change>()
    private var revision = 0L
    private var byteLength = base.bytes
    private var selection = Position(0, 0)
    private var closed = false
    private var failed = false
    private var checkpointRanges: List<PieceTable.Range>? = null
    private var checkpointHash: String? = null
    private var checkpointBytes = 0L
    internal val needsRecovery: Boolean get() = failed
    val status: Status get() = Status(revision, (table.size / 2).toInt(), byteLength, selection, table.canUndo, table.canRedo)
    private fun ready() { check(!closed && !failed) { "Editor is closed or needs recovery" }; checkAlive() }
    fun select(anchor: Int, caret: Int) {
        ready(); require(anchor in 0..status.characters && caret in 0..status.characters)
        selection = Position(anchor, caret)
    }
    /** Reads a bounded viewport; callers never request or construct the complete document String. */
    fun text(start: Int, count: Int): String {
        ready(); require(start in 0..status.characters && count in 0..MAX_VIEW_CHARACTERS)
        val length = minOf(count, status.characters - start)
        return table.openStream(start.toLong() * 2, (start + length).toLong() * 2).use { input ->
            val data = DataInputStream(BufferedInputStream(input, NoteSizePolicy.IO_CHUNK_BYTES))
            String(CharArray(length) { data.readChar() })
        }
    }
    fun openUtf8(start: Int = 0, end: Int = status.characters, checkCancelled: () -> Unit = {}): InputStream {
        ready(); require(start in 0..end && end <= status.characters)
        return DiskJournalRecovery.ReaderUtf8Stream(DiskJournalRecovery.Utf16UnitReader(
            table.openStream(start.toLong() * 2, end.toLong() * 2)), checkCancelled)
    }
    internal fun sharedPieces(): List<SharedNotePiece>? {
        ready()
        val previous = checkpointRanges
        if (previous != null && checkpointHash != null) {
            val current = table.ranges()
            fun common(a: List<PieceTable.Range>, b: List<PieceTable.Range>, reverse: Boolean): Long {
                var i=0;var j=0;var x=0L;var y=0L;var total=0L
                while(i<a.size && j<b.size) {
                    val p=if(reverse)a[a.lastIndex-i] else a[i]
                    val q=if(reverse)b[b.lastIndex-j] else b[j]
                    val po=if(reverse)p.offset+p.length-x else p.offset+x
                    val qo=if(reverse)q.offset+q.length-y else q.offset+y
                    if(p.source !== q.source || po != qo)break
                    val n=minOf(p.length-x,q.length-y);total+=n;x+=n;y+=n
                    if(x==p.length){i++;x=0};if(y==q.length){j++;y=0}
                }
                return total
            }
            val prefix=common(previous,current,false)
            val oldUnits=previous.sumOf { it.length }
            val suffix=minOf(common(previous,current,true),oldUnits-prefix,table.size-prefix)
            val first=sharedParts(table.ranges(0,prefix)) ?: return null
            val last=sharedParts(table.ranges(table.size-suffix,table.size)) ?: return null
            val middle=sharedParts(table.ranges(prefix,table.size-suffix)) ?: return null
            val prefixBytes=first.sumOf { it.bytes };val suffixBytes=last.sumOf { it.bytes }
            return buildList {
                if(prefixBytes>0)add(SharedNotePiece(checkpointHash,0,prefixBytes))
                addAll(middle)
                if(suffixBytes>0)add(SharedNotePiece(checkpointHash,checkpointBytes-suffixBytes,suffixBytes))
            }
        }
        return sharedParts(table.ranges())
    }
    internal fun acceptCheckpoint(ref: NoteBodyRef) {
        ready();check(ref.bytes==byteLength)
        checkpointRanges=table.ranges();checkpointHash=ref.hash;checkpointBytes=ref.bytes
    }
    private fun sharedParts(parts: List<PieceTable.Range>): List<SharedNotePiece>? {
        val result = ArrayList<SharedNotePiece>()
        for (part in parts) {
            if (part.source === original) {
                val start = original.utf8Offset(part.offset / 2) ?: return null
                val end = original.utf8Offset((part.offset + part.length) / 2) ?: return null
                result.add(SharedNotePiece(base.hash, start, end - start))
            } else {
                // Encoding each addition separately is safe only at scalar boundaries.
                if (part.length > 0) {
                    val first = part.source.read(part.offset, 2)
                    val last = part.source.read(part.offset + part.length - 2, 2)
                    fun char(bytes: ByteArray) = (((bytes[0].toInt() and 255) shl 8) or (bytes[1].toInt() and 255)).toChar()
                    if (Character.isLowSurrogate(char(first)) || Character.isHighSurrogate(char(last))) return null
                }
                val open = { DiskJournalRecovery.ReaderUtf8Stream(DiskJournalRecovery.Utf16UnitReader(
                    part.source.openRange(part.offset, part.length))) {} }
                var size = 0L
                open().use { input -> val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                    while (true) { val n = input.read(buffer); if (n < 0) break; size += n } }
                result.add(SharedNotePiece(null, 0, size, open))
            }
        }
        return result
    }
    /** Export remains possible when an internal state write failed. Never mutates or fsyncs. */
    fun openRecoveryUtf8(pending: List<List<Replacement>> = emptyList()): InputStream {
        check(!closed); checkAlive()
        val snapshot = table.forkForRead()
        for (batch in pending) for (edit in batch) {
            require(edit.start in 0..edit.end && edit.end.toLong() * 2 <= snapshot.size)
            val bytes = ByteArray(edit.text.length * 2)
            edit.text.forEachIndexed { index, char -> bytes[index * 2] = (char.code ushr 8).toByte(); bytes[index * 2 + 1] = char.code.toByte() }
            val source = object : NoteByteSource {
                override val size = bytes.size.toLong()
                override fun read(offset: Long, count: Int) = bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
            }
            snapshot.replace(edit.start.toLong() * 2, edit.end.toLong() * 2, source)
        }
        return DiskJournalRecovery.ReaderUtf8Stream(DiskJournalRecovery.Utf16UnitReader(snapshot.openStream())) {}
    }
    private fun utf8Size(start: Int, end: Int, checkCancelled: () -> Unit): Long {
        var bytes = 0L
        openUtf8(start, end, checkCancelled).use { input ->
            val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
            while (true) { val count = input.read(buffer); if (count < 0) break; bytes += count }
        }
        return bytes
    }
    data class Replacement(val start: Int, val end: Int, val text: String)
    /** Native IME replacement and format wrappers publish as one durable Undo transaction. */
    fun replaceBatch(edits: List<Replacement>, before: Position = selection, after: Position? = null,
        checkCancelled: () -> Unit = {}): Status {
        ready(); require(edits.isNotEmpty() && edits.size <= 10_000)
        return editGroup(before, after, checkCancelled) {
            for (chunk in edits.chunked(500)) {
                checkCancelled()
                for (edit in chunk) StringReader(edit.text).use { replaceRange(edit.start, edit.end, it, checkCancelled) }
            }
        }
    }
    fun replace(start: Int, end: Int, reader: Reader, after: Position? = null, checkCancelled: () -> Unit = {}): Status {
        ready()
        return editGroup(selection, after, checkCancelled) { replaceRange(start, end, reader, checkCancelled) }
    }
    fun replace(start: Int, end: Int, text: String, after: Position? = null) =
        StringReader(text).use { replace(start, end, it, after) }
    private fun editGroup(before: Position, after: Position?, checkCancelled: () -> Unit, action: () -> Unit): Status {
        val previousBytes = byteLength
        val previousSelection = selection
        val previousAppendLength = appendFile.length()
        try {
            table.editGroup {
                action(); checkCancelled()
                RandomAccessFile(appendFile, "rw").use { it.fd.sync() }
                require(NoteSizePolicy.acceptsEdit(byteLength, base.bytes)) { "Note exceeds 55 MB. Split the insertion; original and Undo are retained." }
                if (after != null) selection = after
                require(selection.anchor in 0..status.characters && selection.caret in 0..status.characters)
            }
        } catch (error: Throwable) {
            byteLength = previousBytes; selection = previousSelection
            // Rolled-back insertions are not referenced by the table or its Undo history.
            // Reclaim cancelled/over-limit paste bytes immediately, without touching old edits.
            try { RandomAccessFile(appendFile, "rw").use { it.setLength(previousAppendLength) } }
            catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
        undo.addLast(Change(before, selection, previousBytes, byteLength))
        if (undo.size > PieceTable.MAX_UNDO_OPERATIONS) undo.removeFirst()
        redo.clear(); revision++
        try { publish() } catch (error: Throwable) { failed = true; throw error }
        return status
    }
    private fun replaceRange(start: Int, end: Int, reader: Reader, checkCancelled: () -> Unit) {
        require(start in 0..end && end <= status.characters)
        // Include neighbouring code units so split-surrogate IME updates count exact UTF-8 bytes.
        val contextStart = (start - 1).coerceAtLeast(0)
        val contextEnd = minOf(status.characters, end + 1)
        val oldContextBytes = if (contextStart == 0 && contextEnd == status.characters) byteLength else utf8Size(contextStart, contextEnd, checkCancelled)
        val offset = appendFile.length()
        var units = 0L
        RandomAccessFile(appendFile, "rw").use { output ->
            output.seek(offset)
            val chars = CharArray(4096); val bytes = ByteArray(chars.size * 2)
            while (true) {
                checkCancelled(); val count = reader.read(chars)
                if (count < 0) break
                if (count == 0) continue
                units += count
                require(status.characters.toLong() - (end - start) + units <= Int.MAX_VALUE) { "Text-position limit exceeded" }
                // UTF-8 requires at least one byte per UTF-16 unit. This lower-bound
                // check stops an enormous stream before writing its entire rejected body.
                if (byteLength - oldContextBytes + units > maxOf(NoteSizePolicy.IMPORT_MAX_BYTES, base.bytes) + 2) {
                    throw NoteSizeExceededException()
                }
                repeat(count) { bytes[it * 2] = (chars[it].code ushr 8).toByte(); bytes[it * 2 + 1] = chars[it].code.toByte() }
                output.write(bytes, 0, count * 2)
            }
        }
        table.replace(start.toLong() * 2, end.toLong() * 2, NoteByteSlice(added, offset, units * 2))
        byteLength += utf8Size(contextStart, contextEnd - (end - start) + units.toInt(), checkCancelled) - oldContextBytes
        selection = Position(start + units.toInt(), start + units.toInt())
    }
    fun undo(): Boolean {
        ready(); val change = undo.lastOrNull() ?: return false
        try {
            check(table.undo()); undo.removeLast(); redo.addLast(change)
            selection = change.before; byteLength = change.beforeBytes; revision++; publish(); return true
        } catch (error: Throwable) { failed = true; throw error }
    }
    fun redo(): Boolean {
        ready(); val change = redo.lastOrNull() ?: return false
        try {
            check(table.redo()); redo.removeLast(); undo.addLast(change)
            selection = change.after; byteLength = change.afterBytes; revision++; publish(); return true
        } catch (error: Throwable) { failed = true; throw error }
    }
    fun find(query: String, from: Int, backwards: Boolean = false, checkCancelled: () -> Unit = {}): Int? {
        require(query.isNotEmpty() && query.length <= ContinuousTextSearch.MAX_QUERY)
        val pivot = from.coerceIn(0, status.characters)
        var found: Int? = null
        if (backwards) {
            scanMatches(query, 0, pivot, checkCancelled) { found = it; true }
            if (found == null) scanMatches(query, pivot, status.characters, checkCancelled) { found = it; true }
        } else {
            scanMatches(query, pivot, status.characters, checkCancelled) { found = it; false }
            if (found == null) scanMatches(query, 0, pivot, checkCancelled) { found = it; false }
        }
        return found
    }
    fun replaceAll(query: String, replacement: String, batchSize: Int = 10_000, checkCancelled: () -> Unit = {}): Int {
        ready(); require(query.isNotEmpty() && query.length <= ContinuousTextSearch.MAX_QUERY)
        if (query == replacement) return 0
        if (find(query, 0, false, checkCancelled) == null) return 0
        var totalReplaced = 0
        val before = selection
        editGroup(before, null, checkCancelled) {
            var searchFrom = 0
            val delta = replacement.length - query.length
            while (searchFrom < status.characters) {
                checkCancelled()
                val positions = ArrayList<Int>()
                scanMatches(query, searchFrom, status.characters, checkCancelled) { pos ->
                    positions.add(pos)
                    positions.size < batchSize
                }
                if (positions.isEmpty()) break
                val edits = positions.asReversed().map { Replacement(it, it + query.length, replacement) }
                for (chunk in edits.chunked(500)) {
                    checkCancelled()
                    for (edit in chunk) StringReader(edit.text).use { replaceRange(edit.start, edit.end, it, checkCancelled) }
                }
                val count = positions.size
                totalReplaced += count
                val lastPos = positions.last()
                val nextStart = lastPos + (count - 1) * delta + replacement.length
                searchFrom = maxOf(searchFrom + 1, nextStart)
            }
        }
        return totalReplaced
    }
    private fun scanMatches(query: String, start: Int, end: Int, checkCancelled: () -> Unit, match: (Int) -> Boolean) {
        ready(); require(query.isNotEmpty() && query.length <= ContinuousTextSearch.MAX_QUERY)
        val prefix = IntArray(query.length)
        var matched = 0
        for (index in 1 until query.length) {
            while (matched > 0 && query[index] != query[matched]) matched = prefix[matched - 1]
            if (query[index] == query[matched]) matched++
            prefix[index] = matched
        }
        matched = 0
        var position = start
        DiskJournalRecovery.Utf16UnitReader(table.openStream(start.toLong() * 2, end.toLong() * 2)).use { reader ->
            val buffer = CharArray(4096)
            while (true) {
                checkCancelled(); val count = reader.read(buffer); if (count < 0) break
                repeat(count) { index ->
                    val char = buffer[index]
                    while (matched > 0 && char != query[matched]) matched = prefix[matched - 1]
                    if (char == query[matched]) matched++
                    if (matched == query.length) {
                        if (!match(position - query.length + 1)) return
                        matched = 0
                    }
                    position++
                }
            }
        }
    }
    fun outline(checkCancelled: () -> Unit = {}): List<Pair<Int, String>> {
        val headings = ArrayList<Pair<Int, String>>()
        val prefix = StringBuilder(1024)
        var position = 0; var lineStart = 0; var cr = false
        var fence: Char? = null; var fenceLength = 0
        fun line() {
            val raw = prefix.toString(); prefix.setLength(0)
            val indent = raw.takeWhile { it == ' ' }.length
            if (indent > 3) return
            val body = raw.drop(indent)
            val marker = body.firstOrNull()
            val run = if (marker == '`' || marker == '~') body.takeWhile { it == marker }.length else 0
            if (run >= 3) {
                if (fence == null) { fence = marker; fenceLength = run }
                else if (fence == marker && run >= fenceLength && body.drop(run).isBlank()) fence = null
            } else if (fence == null) {
                val level = body.takeWhile { it == '#' }.length
                if (level in 1..6 && body.getOrNull(level)?.isWhitespace() == true) headings.add(lineStart to body.drop(level).trim())
            }
        }
        DiskJournalRecovery.Utf16UnitReader(table.openStream()).use { reader ->
            val buffer = CharArray(4096)
            while (true) {
                checkCancelled(); val count = reader.read(buffer); if (count < 0) break
                repeat(count) { index ->
                    val char = buffer[index]
                    if (char == '\n' && cr) { lineStart = position + 1; cr = false }
                    else if (char == '\n' || char == '\r') {
                        line(); lineStart = position + 1; cr = char == '\r'
                        if (headings.size == 500) return headings
                    } else { if (prefix.length < 1024) prefix.append(char); cr = false }
                    position++
                }
            }
            if (prefix.isNotEmpty()) line()
        }
        return headings
    }
    fun flush() { ready(); publish() }
    private fun publish() {
        checkAlive()
        val temp = File(stateFile.parentFile, stateFile.name + ".tmp")
        try {
            FileOutputStream(temp).use { raw ->
                val hash = MessageDigest.getInstance("SHA-256")
                val digest = DigestOutputStream(raw, hash)
                val out = DataOutputStream(BufferedOutputStream(digest, NoteSizePolicy.IO_CHUNK_BYTES))
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeUTF(base.hash); out.writeLong(base.bytes); out.writeUTF(ownerId)
                out.writeLong(revision); out.writeLong(byteLength)
                fun position(value: Position) { out.writeInt(value.anchor); out.writeInt(value.caret) }
                position(selection)
                table.writeState(out) { source -> when (source) { original -> 0; added -> 1; else -> error("Unknown piece source") } }
                for (stack in listOf(undo, redo)) {
                    out.writeInt(stack.size)
                    stack.forEach { position(it.before); position(it.after); out.writeLong(it.beforeBytes); out.writeLong(it.afterBytes) }
                }
                out.flush(); digest.on(false); raw.write(hash.digest()); raw.fd.sync()
            }
            NoteBodyLeases.guarded {
                checkAlive(); fileOperations.renameAtomic(temp, stateFile); fileOperations.syncDirectory(stateFile.parentFile!!)
            }
        } finally { temp.delete() }
    }
    fun recover() {
        check(!closed); checkAlive()
        // The in-memory descriptors own edits whose append bytes were already fsynced.
        // Loading the old state here would discard a failed paste/Undo publication.
        publish()
        failed = false
    }
    private fun restore() {
        val metadata = inspect(stateFile)
        check(metadata.hash == base.hash && metadata.bytes == base.bytes) { "Editor state belongs to another original" }
        DataInputStream(BufferedInputStream(FileInputStream(stateFile), NoteSizePolicy.IO_CHUNK_BYTES)).use { input ->
            check(input.readInt() == MAGIC)
            val version = input.readInt(); check(version in 1..VERSION)
            input.readUTF(); input.readLong()
            if (version >= 2) input.readUTF()
            revision = input.readLong(); byteLength = input.readLong()
            require(revision >= 0 && byteLength >= 0)
            fun position(): Position = Position(input.readInt(), input.readInt())
            selection = position()
            table = PieceTable.readState(input) { when (it) { 0 -> original; 1 -> added; else -> error("Invalid source") } }
            require(table.size % 2 == 0L && table.size / 2 <= Int.MAX_VALUE)
            require(selection.anchor in 0..status.characters && selection.caret in 0..status.characters)
            for (stack in listOf(undo, redo)) {
                val count = input.readInt(); require(count in 0..PieceTable.MAX_UNDO_OPERATIONS)
                repeat(count) { stack.addLast(Change(position(), position(), input.readLong(), input.readLong())) }
            }
            require(table.canUndo == undo.isNotEmpty() && table.canRedo == redo.isNotEmpty())
            check(input.available() == 32) { "Unexpected editor-state payload" }
        }
    }
    override fun close() { if (!closed) { closed = true; release() } }
    private class AppendSource(private val file: File) : NoteByteSource {
        override val size get() = file.length()
        override fun read(offset: Long, count: Int) = openRange(offset, minOf(count.toLong(), size - offset)).use { it.readBytes() }
        override fun openRange(offset: Long, length: Long): InputStream {
            require(offset in 0..size && length in 0..(size - offset))
            val input = RandomAccessFile(file, "r"); input.seek(offset)
            return object : InputStream() {
                var remaining = length
                override fun read(): Int { if (remaining == 0L) return -1; val value = input.read(); if (value < 0) throw EOFException(); remaining--; return value }
                override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                    if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
                    if (len == 0) return 0
                    if (remaining == 0L) return -1
                    val count = input.read(bytes, off, minOf(len.toLong(), remaining, NoteSizePolicy.IO_CHUNK_BYTES.toLong()).toInt())
                    if (count < 0) throw EOFException(); remaining -= count; return count
                }
                override fun close() = input.close()
            }
        }
    }
    companion object {
        const val MAX_VIEW_CHARACTERS = 32_768
        private const val MAGIC = 0x434C4956
        private const val VERSION = 2
        data class Metadata(val base: NoteBodyRef, val ownerId: String, val revision: Long, val bytes: Long)
        fun inspect(state: File): NoteBodyRef = metadata(state).base
        fun metadata(state: File): Metadata = RandomAccessFile(state, "r").use { input ->
            val length = input.length()
            require(length in 100..64L * 1024 * 1024) { "Invalid live editor state" }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
            var remaining = length - 32
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) throw EOFException()
                digest.update(buffer, 0, count); remaining -= count
            }
            val expected = ByteArray(32); input.readFully(expected)
            check(MessageDigest.isEqual(digest.digest(), expected)) { "Live editor checksum failed; files retained" }
            input.seek(0)
            check(input.readInt() == MAGIC) { "Unknown live editor format" }
            val version = input.readInt(); check(version in 1..VERSION) { "Unknown live editor format" }
            val hash = input.readUTF(); val bytes = input.readLong()
            val owner = if (version >= 2) input.readUTF() else ""
            val revision = input.readLong(); val currentBytes = input.readLong()
            require(hash.matches(Regex("[0-9a-f]{64}")) && bytes >= 0 && revision >= 0 && currentBytes >= 0 && owner.length <= 1024)
            Metadata(NoteBodyRef(hash, bytes, ""), owner, revision, currentBytes)
        }
        fun open(source: NoteByteSource, base: NoteBodyRef, append: File, state: File,
            operations: NoteFileOperations, checkAlive: () -> Unit = {}, release: () -> Unit = {}, checkCancelled: () -> Unit = {}, ownerId: String = ""): DiskTextModel {
            val original = IndexedUtf8Units.build(source, checkCancelled)
            check(append.isFile || append.createNewFile())
            return DiskTextModel(original, append, state, operations, base, ownerId, checkAlive, release).also {
                try { if (state.exists()) it.restore() else it.publish() }
                catch (error: Throwable) { it.close(); throw error }
            }
        }
    }
}

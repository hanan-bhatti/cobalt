package com.example.notes

import java.io.*
import java.nio.charset.CodingErrorAction

/**
 * Recovery adapter, not the live editor. Replays UTF-16 coordinates over a disk piece table,
 * so exporting/recovering a journal never requires allocating Sora's full resident document.
 * Temporary UTF-16 scratch files are disposable; the original Markdown and journal stay durable.
 */
internal object DiskJournalRecovery {
    fun <T> replay(base: InputStream, journal: NoteEditJournal, hash: String, bytes: Long,
        workDirectory: File, checkCancelled: () -> Unit = {}, consume: (InputStream) -> T): T {
        check(workDirectory.exists() || workDirectory.mkdirs()) { "Recovery workspace unavailable" }
        val original = File.createTempFile("original-", ".scratch", workDirectory)
        val added = File.createTempFile("added-", ".scratch", workDirectory)
        return try {
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            InputStreamReader(base, decoder).use { reader ->
                DataOutputStream(BufferedOutputStream(FileOutputStream(original), 16 * 1024)).use { output ->
                    val buffer = CharArray(2048)
                    while (true) {
                        checkCancelled()
                        val count = reader.read(buffer)
                        if (count < 0) break
                        for (index in 0 until count) output.writeChar(buffer[index].code)
                    }
                }
            }
            val table = PieceTable(ScratchSource(original), keepUndo = false)
            val appendedSource = ScratchSource(added)
            RandomAccessFile(added, "rw").use { append ->
                var start = 0L; var removed = 0L; var appendStart = 0L
                journal.replay(hash, bytes,
                    begin = { position, length ->
                        checkCancelled()
                        start = position.toLong() * 2; removed = length.toLong() * 2
                        check(start <= table.size && removed <= table.size - start) { "Journal coordinates exceed the body" }
                        appendStart = append.filePointer
                    }, insert = { part ->
                        checkCancelled()
                        val encoded = ByteArray(part.length * 2)
                        part.forEachIndexed { index, char ->
                            encoded[index * 2] = (char.code ushr 8).toByte()
                            encoded[index * 2 + 1] = char.code.toByte()
                        }
                        append.write(encoded)
                    }, finish = {
                        val length = append.filePointer - appendStart
                        table.replace(start, start + removed, NoteByteSlice(appendedSource, appendStart, length))
                    })
                table.openStream().use { utf16 ->
                    Utf16UnitReader(utf16).use { reader ->
                        ReaderUtf8Stream(reader, checkCancelled).use(consume)
                    }
                }
            }
        } finally { original.delete(); added.delete() }
    }

    private class ScratchSource(private val file: File) : NoteByteSource {
        override val size get() = file.length()
        override fun read(offset: Long, count: Int): ByteArray {
            require(offset in 0..size && count >= 0)
            return RandomAccessFile(file, "r").use {
                it.seek(offset)
                ByteArray(minOf(count.toLong(), size - offset).toInt()).also(it::readFully)
            }
        }
        override fun openRange(offset: Long, length: Long): InputStream {
            require(offset in 0..size && length in 0..(size - offset))
            val input = RandomAccessFile(file, "r")
            input.seek(offset)
            return object : InputStream() {
                var remaining = length
                override fun read(): Int {
                    if (remaining == 0L) return -1
                    val value = input.read(); if (value < 0) throw EOFException("Recovery scratch file is incomplete")
                    remaining--; return value
                }
                override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                    if (len == 0) return 0
                    if (remaining == 0L) return -1
                    val count = input.read(bytes, off, minOf(len.toLong(), remaining).toInt())
                    if (count < 0) throw EOFException("Recovery scratch file is incomplete")
                    remaining -= count; return count
                }
                override fun close() = input.close()
            }
        }
    }

    /** Read code units exactly, including an unpaired surrogate pending an IME replacement. */
    internal class Utf16UnitReader(input: InputStream) : Reader() {
        private val data = DataInputStream(BufferedInputStream(input, 16 * 1024))
        override fun read(chars: CharArray, offset: Int, length: Int): Int {
            if (offset < 0 || length < 0 || offset > chars.size - length) throw IndexOutOfBoundsException()
            if (length == 0) return 0
            var count = 0
            while (count < length) {
                val high = data.read()
                if (high < 0) return if (count == 0) -1 else count
                val low = data.read()
                if (low < 0) throw EOFException("Incomplete UTF-16 scratch unit")
                chars[offset + count++] = ((high shl 8) or low).toChar()
            }
            return count
        }
        override fun close() = data.close()
    }

    /** Chunk encoder keeps a high surrogate with its following low surrogate. */
    internal class ReaderUtf8Stream(private val reader: Reader, private val checkCancelled: () -> Unit) : InputStream() {
        private var buffer = ByteArray(0)
        private var position = 0
        private var carried: Char? = null
        private fun fill(): Boolean {
            if (position < buffer.size) return true
            checkCancelled()
            val chars = CharArray(2049)
            var count = 0
            carried?.let { chars[count++] = it; carried = null }
            val read = reader.read(chars, count, 2048 - count)
            if (read < 0 && count == 0) return false
            if (read > 0) count += read
            // If the final input is a high surrogate, resolve it now instead of dropping it.
            if (count > 0 && Character.isHighSurrogate(chars[count - 1])) {
                val next = reader.read()
                if (next >= 0) {
                    val char = next.toChar()
                    if (Character.isLowSurrogate(char)) chars[count++] = char else carried = char
                }
            }
            buffer = String(chars, 0, count).toByteArray(Charsets.UTF_8); position = 0
            return buffer.isNotEmpty()
        }
        override fun read(): Int = if (fill()) buffer[position++].toInt() and 255 else -1
        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
            if (len == 0) return 0
            if (!fill()) return -1
            val count = minOf(len, buffer.size - position)
            buffer.copyInto(bytes, off, position, position + count); position += count; return count
        }
    }
}

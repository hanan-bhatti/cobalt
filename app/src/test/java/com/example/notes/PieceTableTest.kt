package com.example.notes

import java.io.ByteArrayInputStream
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class PieceTableTest {
    private fun source(text: String): NoteByteSource {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return object : NoteByteSource {
            override val size = bytes.size.toLong()
            override fun read(offset: Long, count: Int) = bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
        }
    }
    private fun text(table: PieceTable) = table.openStream().bufferedReader().use { it.readText() }

    @Test fun deterministicFuzzMatchesStringBuilderAndUndoRedo() {
        val random = Random(4123)
        val reference = StringBuilder("Original")
        val table = PieceTable(source(reference.toString()))
        repeat(2_000) {
            val start = random.nextInt(reference.length + 1)
            val end = start + random.nextInt(reference.length - start + 1)
            val inserted = (0 until random.nextInt(20)).joinToString("") { ('a' + random.nextInt(26)).toString() }
            val previous = reference.toString()
            reference.replace(start, end, inserted)
            table.replace(start.toLong(), end.toLong(), source(inserted))
            assertEquals(reference.toString(), text(table))
            assertTrue(table.undo()); assertEquals(previous, text(table))
            assertTrue(table.redo()); assertEquals(reference.toString(), text(table))
        }
    }

    @Test fun streamsAndHistoryDoNotCopyTheOriginalFile() {
        var largestRead = 0
        val original = object : NoteByteSource {
            override val size = 50_000_000L
            override fun read(offset: Long, count: Int): ByteArray {
                largestRead = maxOf(largestRead, count)
                return ByteArray(count) { 'x'.code.toByte() }
            }
        }
        val table = PieceTable(original)
        val snapshot = table.openStream(0, 10)
        table.replace(1, 2, source("😀"))
        assertEquals(50_000_003L, table.size)
        assertEquals("xxxxxxxxxx", snapshot.bufferedReader().use { it.readText() })
        assertEquals("x😀xx", table.openStream(0, 7).bufferedReader().use { it.readText() })
        assertTrue(largestRead <= NoteSizePolicy.IO_CHUNK_BYTES)
        assertTrue(table.undo()); assertEquals(50_000_000L, table.size)
    }

    @Test fun windowHistoryRestoresUnicodeAndRefusesStaleText() {
        val history = WindowEditHistory()
        history.record("a😀b", "a漢字b")
        assertNull(history.undo("stale"))
        assertEquals("a😀b", history.undo("a漢字b"))
        assertEquals("a漢字b", history.redo("a😀b"))
    }

    @Test fun lineIndexHandlesCrLfMixedEmojiAndChunkBoundaries() {
        val text = (0..900).joinToString("") { "😀漢字$it" + when (it % 3) { 0 -> "\r\n"; 1 -> "\n"; else -> "\r" } }
        val bytes = text.toByteArray(Charsets.UTF_8)
        val builder = Utf8LineIndex.Builder()
        bytes.forEach { builder.append(byteArrayOf(it)) }
        val index = builder.snapshot()
        val expected = mutableListOf(0L)
        var i = 0
        while (i < bytes.size) {
            if (bytes[i] == 13.toByte()) {
                if (i + 1 < bytes.size && bytes[i + 1] == 10.toByte()) i++
                expected.add(i + 1L)
            } else if (bytes[i] == 10.toByte()) expected.add(i + 1L)
            i++
        }
        assertEquals(expected.size.toLong(), index.lineCount)
        expected.forEachIndexed { line, position ->
            assertEquals(position, index.byteOffset(line.toLong(), { offset ->
                ByteArrayInputStream(bytes, offset.toInt(), bytes.size - offset.toInt())
            }))
        }
    }
}

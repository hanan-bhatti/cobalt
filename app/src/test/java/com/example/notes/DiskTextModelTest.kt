package com.example.notes

import java.io.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DiskTextModelTest {
    private fun source(text: String): NoteByteSource {
        val bytes = text.toByteArray()
        return object : NoteByteSource {
            override val size = bytes.size.toLong()
            override fun read(offset: Long, count: Int) = bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
        }
    }
    private val base = NoteBodyRef("a".repeat(64), 0, "")
    @Test fun unicodeBookmarksAndOddByteReadsMatchUtf16Exactly() {
        val text = "a😀漢字\r\n".repeat(1_000) + "end"
        val index = IndexedUtf8Units.build(source(text))
        val expected = text.toByteArray(Charsets.UTF_16BE)
        assertEquals(text.length, index.characters)
        val random = Random(9)
        repeat(300) {
            val start = random.nextInt(expected.size)
            val size = random.nextInt(minOf(128, expected.size - start) + 1)
            assertArrayEquals(expected.copyOfRange(start, start + size), index.read(start.toLong(), size))
        }
    }
    @Test fun durableDescriptorUndoRedoAndSelectionSurviveReopen() {
        val directory = Files.createTempDirectory("cobalt-live-test-").toFile()
        val original = "start😀\r\n" + "middle\n".repeat(2_000) + "end"
        val originalSource = source(original)
        val ref = base.copy(bytes = originalSource.size)
        fun open() = DiskTextModel.open(originalSource, ref, File(directory, "append"), File(directory, "state"), HostTestNoteFileOperations)
        var model = open()
        try {
            model.select(2, 5); model.replace(3, 9, "CHANGED😀")
            val changed = original.replaceRange(3, 9, "CHANGED😀")
            assertEquals(changed, model.openUtf8().bufferedReader().use { it.readText() })
            model.close(); model = open()
            assertTrue(model.undo()); assertEquals(DiskTextModel.Position(2, 5), model.status.selection)
            assertEquals(original, model.openUtf8().bufferedReader().use { it.readText() })
            model.close(); model = open(); assertTrue(model.redo())
            assertEquals(changed, model.openUtf8().bufferedReader().use { it.readText() })
            model.undo(); model.replace(0, 0, "new"); assertFalse(model.redo())
        } finally { model.close(); directory.deleteRecursively() }
    }
    @Test fun nativeDeleteAndInsertAreOneDurableUndoGroupAndCountSplitSurrogatesExactly() {
        val directory = Files.createTempDirectory("cobalt-live-group-").toFile()
        val original = source("abc")
        val model = DiskTextModel.open(original, base.copy(bytes = original.size), File(directory, "append"), File(directory, "state"), HostTestNoteFileOperations)
        try {
            model.replaceBatch(listOf(DiskTextModel.Replacement(1, 2, ""), DiskTextModel.Replacement(1, 1, "new")))
            assertEquals("anewc", model.text(0, 100))
            assertTrue(model.undo()); assertEquals("abc", model.text(0, 100)); assertFalse(model.undo())
            model.replace(0, 3, "\uD83D")
            model.replace(1, 1, "\uDE00")
            assertEquals("😀", model.text(0, 100)); assertEquals(4L, model.status.bytes)
        } finally { model.close(); directory.deleteRecursively() }
    }
    @Test fun cancellationRollsBackTheGroupWithoutDiscardingUndo() {
        val directory = Files.createTempDirectory("cobalt-live-cancel-").toFile()
        val original = source("abc")
        val model = DiskTextModel.open(original, base.copy(bytes = original.size), File(directory, "append"), File(directory, "state"), HostTestNoteFileOperations)
        try {
            model.replace(0, 0, "kept")
            try { model.replace(0, 3, StringReader("cancelled"), checkCancelled = { throw kotlinx.coroutines.CancellationException() }); fail("Expected cancellation") }
            catch (_: kotlinx.coroutines.CancellationException) {}
            assertEquals("keptabc", model.text(0, 100))
            assertTrue(model.undo()); assertEquals("abc", model.text(0, 100))
        } finally { model.close(); directory.deleteRecursively() }
    }
    @Test fun deleteFiftyMegabytesRetainsOnlyDescriptorsAndStreamsRestoredBytes() {
        val directory = Files.createTempDirectory("cobalt-live-large-").toFile()
        var maximumRead = 0
        val original = object : NoteByteSource {
            override val size = 50_000_000L
            override fun read(offset: Long, count: Int): ByteArray { maximumRead = maxOf(maximumRead, count); return ByteArray(count) { 'x'.code.toByte() } }
        }
        val model = DiskTextModel.open(original, base.copy(bytes = original.size), File(directory, "append"), File(directory, "state"), HostTestNoteFileOperations)
        try {
            model.replace(0, 50_000_000, "")
            assertEquals(0, model.status.characters); assertEquals(0L, File(directory, "append").length())
            assertTrue(File(directory, "state").length() < 2_000)
            repeat(12) { assertTrue(model.undo()); assertEquals(50_000_000, model.status.characters); assertTrue(model.redo()) }
            assertTrue(model.undo())
            var bytes = 0L
            model.openUtf8().use { input ->
                val buffer = ByteArray(16_384)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    bytes += count
                    repeat(count) { assertEquals('x'.code.toByte(), buffer[it]) }
                }
            }
            assertEquals(original.size, bytes); assertTrue(maximumRead <= NoteSizePolicy.IO_CHUNK_BYTES)
            println("HOST_LIVE_DESCRIPTOR_FIXTURE: original=${original.size}, append=${File(directory, "append").length()}, state=${File(directory, "state").length()}, maxSourceRead=$maximumRead")
        } finally { model.close(); directory.deleteRecursively() }
    }
    @Test fun failedStatePublicationKeepsLastDurableEditAndRejectsCorruptMetadata() {
        val directory = Files.createTempDirectory("cobalt-live-fail-").toFile()
        val original = source("original"); val ref = base.copy(bytes = original.size)
        var failWrites = false
        val operations = object : NoteFileOperations by HostTestNoteFileOperations {
            override fun renameAtomic(source: File, destination: File) { if (failWrites) throw IOException("Simulated disk full"); HostTestNoteFileOperations.renameAtomic(source, destination) }
        }
        val append = File(directory, "append"); val state = File(directory, "state")
        var model = DiskTextModel.open(original, ref, append, state, operations)
        try {
            model.replace(0, 0, "kept "); failWrites = true
            try { model.replace(0, 4, "lost"); fail("Expected failure") } catch (_: IOException) {}
            model.close(); failWrites = false; model = DiskTextModel.open(original, ref, append, state, operations)
            assertEquals("kept original", model.text(0, 100)); model.close()
            RandomAccessFile(state, "rw").use { it.seek(12); it.writeByte(99) }
            try { DiskTextModel.open(original, ref, append, state, operations); fail("Expected corruption rejection") } catch (_: IllegalStateException) {}
        } finally { model.close(); directory.deleteRecursively() }
    }
}

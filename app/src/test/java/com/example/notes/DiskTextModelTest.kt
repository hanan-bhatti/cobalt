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
            val retainedBytes = File(directory, "append").length()
            try { model.replace(0, 3, StringReader("cancelled"), checkCancelled = { throw kotlinx.coroutines.CancellationException() }); fail("Expected cancellation") }
            catch (_: kotlinx.coroutines.CancellationException) {}
            var reads = 0
            val interrupted = object : Reader() {
                override fun read(chars: CharArray, offset: Int, length: Int): Int {
                    if (reads++ > 0) throw kotlinx.coroutines.CancellationException()
                    repeat(length) { chars[offset + it] = 'x' }
                    return length
                }
                override fun close() = Unit
            }
            try { model.replace(0, 3, interrupted); fail("Expected mid-stream cancellation") }
            catch (_: kotlinx.coroutines.CancellationException) {}
            assertEquals("Cancelled insertion left unused bytes", retainedBytes, File(directory, "append").length())
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
    @Test fun emergencyExportIncludesUnpublishedAndQueuedEditsWithoutAnotherInternalWrite() {
        val directory = Files.createTempDirectory("cobalt-live-export-").toFile()
        val original = source("original"); val ref = base.copy(bytes = original.size)
        var failWrites = false
        val operations = object : NoteFileOperations by HostTestNoteFileOperations {
            override fun renameAtomic(source: File, destination: File) {
                if (failWrites) throw IOException("Internal storage unavailable")
                HostTestNoteFileOperations.renameAtomic(source, destination)
            }
        }
        val append = File(directory, "append"); val state = File(directory, "state")
        val model = DiskTextModel.open(original, ref, append, state, operations)
        try {
            model.replace(0, 0, "kept "); val durable = state.readBytes()
            failWrites = true
            try { model.replace(0, 4, "unsaved"); fail("Expected failure") } catch (_: IOException) {}
            val pending = listOf(listOf(DiskTextModel.Replacement(0, 0, "queued ")))
            assertEquals("queued unsaved original", model.openRecoveryUtf8(pending).bufferedReader().use { it.readText() })
            assertArrayEquals(durable, state.readBytes())
            failWrites = false
            model.recover()
            assertEquals("unsaved original", model.text(0, 100))
            assertTrue(model.undo())
            assertEquals("kept original", model.text(0, 100))
        } finally { model.close(); directory.deleteRecursively() }
    }
    @Test fun streamedToolsFindFarContentAndReplaceAllAsOneUndoOperation() {
        val directory = Files.createTempDirectory("cobalt-live-tools-").toFile()
        val text = "# Start\r\n" + "paragraph\n".repeat(5_000) + "# Far heading\nTARGET😀 TARGET😀\n"
        val original = source(text)
        val model = DiskTextModel.open(original, base.copy(bytes = original.size), File(directory, "append"), File(directory, "state"), HostTestNoteFileOperations)
        try {
            assertEquals(text.indexOf("TARGET"), model.find("TARGET", 0))
            assertEquals(text.lastIndexOf("TARGET"), model.find("TARGET", text.length, true))
            assertEquals(listOf(0 to "Start", text.indexOf("# Far") to "Far heading"), model.outline())
            assertEquals(2, model.replaceAll("TARGET😀", "replaced"))
            assertEquals(text.replace("TARGET😀", "replaced"), model.openUtf8().bufferedReader().use { it.readText() })
            assertTrue(model.undo()); assertEquals(text, model.openUtf8().bufferedReader().use { it.readText() })
        } finally { model.close(); directory.deleteRecursively() }
    }
    @Test fun replaceAllAcrossBatchesHandlesExpandingContractingAndSingleUndo() {
        val directory = Files.createTempDirectory("cobalt-live-batch-").toFile()
        val text = "foo bar foo bar foo bar foo"
        val original = source(text)
        val model = DiskTextModel.open(original, base.copy(bytes = original.size), File(directory, "append"), File(directory, "state"), HostTestNoteFileOperations)
        try {
            // Test expanding replacement with batch size = 2 (forces 2 batches for 4 occurrences)
            assertEquals(4, model.replaceAll("foo", "foobar", batchSize = 2))
            assertEquals("foobar bar foobar bar foobar bar foobar", model.openUtf8().bufferedReader().use { it.readText() })
            assertTrue(model.undo())
            assertEquals(text, model.openUtf8().bufferedReader().use { it.readText() })

            // Test contracting replacement with batch size = 2
            assertEquals(4, model.replaceAll("foo", "f", batchSize = 2))
            assertEquals("f bar f bar f bar f", model.openUtf8().bufferedReader().use { it.readText() })
            assertTrue(model.undo())
            assertEquals(text, model.openUtf8().bufferedReader().use { it.readText() })

            // Test deletion replacement with batch size = 2
            assertEquals(4, model.replaceAll("foo", "", batchSize = 2))
            assertEquals(" bar  bar  bar ", model.openUtf8().bufferedReader().use { it.readText() })
            assertTrue(model.undo())
            assertEquals(text, model.openUtf8().bufferedReader().use { it.readText() })
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

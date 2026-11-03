package com.example.notes

import java.io.File
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class DiskJournalRecoveryTest {
    @get:Rule val folder = TemporaryFolder()
    private val hash = "b".repeat(64)
    @Test fun deterministicEditsMatchStringBuilderIncludingSplitSurrogateBoundaries() {
        val original = "# Heading\r\n😀 | value\r\n".repeat(50)
        val base = original.toByteArray(Charsets.UTF_8)
        val reference = StringBuilder(original)
        val log = NoteEditJournal.create(File(folder.root, "fuzz.edits"), hash, base.size.toLong())
        val random = Random(55)
        repeat(500) {
            val start = random.nextInt(reference.length + 1)
            val end = random.nextInt(start, reference.length + 1)
            val text = listOf("😀", "\r\n", "\u0000", "x", "", "**bold**", "\uD800")[random.nextInt(7)]
            log.append(start, end - start, text)
            reference.replace(start, end, text)
        }
        val scratch = File(folder.root, "scratch")
        val result = DiskJournalRecovery.replay(base.inputStream(), log, hash, base.size.toLong(), scratch) { it.readBytes() }
        assertArrayEquals(reference.toString().toByteArray(Charsets.UTF_8), result)
        assertEquals(0, scratch.listFiles().orEmpty().size)
    }
    @Test fun fiftyMegabyteStreamRecoversWithoutAResidentDocument() {
        val bytes = 50_000_000
        val log = NoteEditJournal.create(File(folder.root, "large.edits"), hash, bytes.toLong())
        log.append(1, 1, "X")
        val source = object : InputStream() {
            var remaining = bytes
            override fun read(): Int = if (remaining-- > 0) 97 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                if (remaining <= 0) return -1
                val count = minOf(length, remaining)
                java.util.Arrays.fill(buffer, offset, offset + count, 97.toByte()); remaining -= count; return count
            }
        }
        var total = 0L
        DiskJournalRecovery.replay(source, log, hash, bytes.toLong(), File(folder.root, "scratch")) { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                for (index in 0 until count) {
                    val expected = if (total + index == 1L) 88.toByte() else 97.toByte()
                    if (expected != buffer[index]) fail("Recovered byte differs at ${total + index}")
                }
                total += count
            }
        }
        assertEquals(bytes.toLong(), total)
    }
    @Test fun cancellationRemovesScratchButRetainsTheJournal() {
        val file = File(folder.root, "cancel.edits")
        val log = NoteEditJournal.create(file, hash, 4)
        log.append(0, 0, "new")
        val scratch = File(folder.root, "scratch")
        try {
            DiskJournalRecovery.replay("base".byteInputStream(), log, hash, 4, scratch,
                checkCancelled = { throw java.util.concurrent.CancellationException() }) { fail("Must cancel before output") }
            fail("Expected cancellation")
        } catch (_: java.util.concurrent.CancellationException) { }
        assertTrue(file.exists()); assertEquals(0, scratch.listFiles().orEmpty().size)
    }
}

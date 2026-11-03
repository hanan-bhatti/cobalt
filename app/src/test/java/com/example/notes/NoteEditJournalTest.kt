package com.example.notes

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteEditJournalTest {
    @get:Rule val folder = TemporaryFolder()
    private val hash = "a".repeat(64)
    private fun create(name: String = "operations.edits"): Pair<File, NoteEditJournal> {
        val file = File(folder.root, name)
        return file to NoteEditJournal.create(file, hash, 10)
    }
    private fun replay(journal: NoteEditJournal, value: StringBuilder): NoteEditJournal.Replay {
        var cursor = 0
        return journal.replay(hash, 10, { start, removed -> value.delete(start, start + removed); cursor = start },
            { part -> value.insert(cursor, part); cursor += part.length })
    }
    @Test fun roundTripIncludesSurrogatesCrLfNulAndLongInsertions() {
        val (_, journal) = create()
        val insert = "😀\r\nline\rnext\n\u0000".repeat(2000) + "\uD800"
        journal.appendBatch(listOf(NoteEditJournal.Edit(1, 3, "replacement"), NoteEditJournal.Edit(0, 0, insert)))
        val expected = StringBuilder("original").replace(1, 4, "replacement").insert(0, insert).toString()
        val actual = StringBuilder("original")
        val result = replay(journal, actual)
        assertEquals(expected, actual.toString()); assertEquals(2, result.operations); assertFalse(result.tornTail)
    }
    @Test fun everyTornByteOfReplacementLeavesTheWholeTransactionUnapplied() {
        val (source, journal) = create()
        journal.appendBatch(listOf(NoteEditJournal.Edit(0, 8, ""), NoteEditJournal.Edit(0, 0, "new😀body")))
        val bytes = source.readBytes()
        // Header is 80 bytes. Simulate a kill at every byte of a delete+insert frame.
        for (length in 81 until bytes.size) {
            val target = File(folder.root, "torn.edits").also { it.writeBytes(bytes.copyOf(length)) }
            val value = StringBuilder("original")
            val result = replay(NoteEditJournal(target), value)
            assertTrue("length=$length", result.tornTail)
            assertEquals("original", value.toString())
        }
    }
    @Test fun invalidCompleteFrameDoesNotApplyAnyOfItsOperations() {
        val (file, journal) = create()
        journal.append(0, 8, "changed")
        RandomAccessFile(file, "rw").use { it.seek(file.length() - 1); val old = it.read(); it.seek(file.length() - 1); it.writeByte(old xor 1) }
        val value = StringBuilder("original")
        try { replay(journal, value); fail("Checksum corruption must fail closed") }
        catch (_: IllegalStateException) { assertEquals("original", value.toString()) }
    }
    @Test fun alreadySyncedFramesSurviveATornLaterTransaction() {
        val (file, journal) = create()
        journal.append(8, 0, " first")
        val synced = file.length()
        journal.append(0, 8, "second")
        RandomAccessFile(file, "rw").use { it.setLength(file.length() - 3) }
        val value = StringBuilder("original")
        val result = replay(journal, value)
        assertEquals("original first", value.toString()); assertEquals(synced, result.validBytes)
        assertEquals(1, result.operations); assertTrue(result.tornTail)
    }
    @Test fun fiveSingleCharacterChangesStoreOperationsRatherThanFiveFullBodies() {
        val (file, journal) = create()
        repeat(5) { journal.append(0, 0, "x") }
        assertTrue(file.length() < 1024)
    }
    @Test fun journalCannotBeReplayedAgainstAnotherBody() {
        val (_, journal) = create()
        try { journal.replay("b".repeat(64), 10, { _, _ -> fail() }, { fail() }); fail() }
        catch (_: IllegalStateException) { }
    }
}

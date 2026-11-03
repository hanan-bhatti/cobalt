package com.example.notes

import io.github.rosemoe.sora.text.Content
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ContinuousEditWriterTest {
    @get:Rule val folder = TemporaryFolder()
    private val hash = "c".repeat(64)
    private fun replay(file: File): Pair<String, NoteEditJournal.Replay> {
        val text = StringBuilder("original"); var cursor = 0
        val result = NoteEditJournal(file).replay(hash, 8,
            { start, removed -> text.delete(start, start + removed); cursor = start },
            { part -> text.insert(cursor, part); cursor += part.length })
        return text.toString() to result
    }
    @Test fun replacementInOneUiTurnIsOneRecoverableTransaction() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val file = File(folder.root, "replace.edits")
        val log = NoteEditJournal.create(file, hash, 8)
        val content = Content("original")
        val writer = ContinuousEditWriter(this, log) { fail("Recovery should not fail") }
        try {
            content.delete(0, 8); writer.record(content, 0, 8, "")
            content.insert(0, 0, "new"); writer.record(content, 0, 0, "new")
            writer.flush()
            assertEquals("new", replay(file).first)
            val torn = File(folder.root, "torn.edits").also { it.writeBytes(file.readBytes()) }
            RandomAccessFile(torn, "rw").use { it.setLength(torn.length() - 1) }
            val (text, result) = replay(torn)
            assertEquals("original", text); assertTrue(result.tornTail)
        } finally { writer.close(); content.release(); Dispatchers.resetMain() }
    }
    @Test fun aBoundedQueueFailureIsVisibleAndFlushDoesNotReportSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val log = NoteEditJournal.create(File(folder.root, "pressure.edits"), hash, 8)
        val content = Content("original")
        var failures = 0
        val writer = ContinuousEditWriter(this, log) { failures++ }
        try {
            repeat(65) { writer.record(content, 0, 0, "x") }
            assertEquals(1, failures); assertFalse(writer.healthy)
            try { writer.flush(); fail("Overflow must require a full checkpoint") }
            catch (_: IllegalStateException) { }
        } finally { writer.close(); content.release(); Dispatchers.resetMain() }
    }
    @Test fun anIoFailureCannotAcknowledgeAnUnsyncedTransaction() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val file = File(folder.root, "failure.edits")
        val log = NoteEditJournal.create(file, hash, 8)
        assertTrue(file.delete()); assertTrue(file.mkdir()) // Deterministically refuse append, even as root.
        val content = Content("original")
        val failures = java.util.concurrent.atomic.AtomicInteger()
        val writer = ContinuousEditWriter(this, log) { failures.incrementAndGet() }
        try {
            writer.record(content, 0, 0, "x")
            var refused = false
            try { writer.flush() } catch (_: Exception) { refused = true }
            assertTrue(refused); assertEquals(1, failures.get()); assertFalse(writer.healthy)
        } finally { writer.close(); content.release(); Dispatchers.resetMain() }
    }
}

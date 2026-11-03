package com.example.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.io.File
import java.util.UUID
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NoteVersionArchiveTest {
    @Test fun tenSmallSavedEditsKeepExactHistoryWithoutTenFullCopies() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        val id = UUID.randomUUID().toString()
        try {
            var body = Random(48).nextBytes(1_000_000)
            var previous = body.inputStream().use { store.writeAtomic(id, it) }
            val versions = arrayListOf(previous to body)
            repeat(10) { step ->
                body = body.copyOf().also { it[10000 + step * 80000] = (it[10000 + step * 80000].toInt() xor 7).toByte() }
                val current = body.inputStream().use { store.writeAtomic(id, it) }
                store.committed(id, current.hash)
                store.archivePrevious(id, previous.hash, current.hash)
                versions.add(current to body); previous = current
            }
            for ((ref, bytes) in versions) {
                assertArrayEquals(bytes, store.open(id, ref.hash).use { it.readBytes() })
            }
            val entries = store.maintenanceFiles().filter { it.directoryKey == PrivateMarkdownStore.storageKey(id) && it.hash != null }
            val stored = entries.sumOf { it.bytes }
            assertEquals(2, entries.count { it.name.endsWith(".md") })
            assertEquals(9, entries.count { it.name.endsWith(".delta") })
            assertTrue("Small changes retained too many full copies: $stored", stored < 3_000_000)
            println("HOST_HISTORY_FIXTURE: 11 versions, 1000000 bytes each, physical body/delta bytes=$stored")
        } finally { store.deleteAll(id) }
    }
    @Test fun publicationFailureKeepsTheFullVersionAndRetryRemainsSafe() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        var failPublishSync = false
        val operations = object : NoteFileOperations by HostTestNoteFileOperations {
            override fun syncDirectory(directory: File) {
                if (failPublishSync && directory.listFiles().orEmpty().any { it.extension == "delta" }) {
                    failPublishSync = false
                    throw IOException("Simulated interruption after delta rename")
                }
                HostTestNoteFileOperations.syncDirectory(directory)
            }
        }
        val store = PrivateMarkdownStore(context, fileOperations = operations)
        try {
            val base = Random(5).nextBytes(100_000)
            val anchor = base.inputStream().use { store.writeAtomic(id, it) }
            val changed = base + "a".toByteArray()
            val previous = changed.inputStream().use { store.writeAtomic(id, it) }
            val current = (changed + "b".toByteArray()).inputStream().use { store.writeAtomic(id, it) }
            failPublishSync = true
            try { store.archivePrevious(id, previous.hash, current.hash); fail("Expected directory sync failure") }
            catch (_: IOException) { }
            assertTrue(store.maintenanceFiles().any { it.name == "${previous.hash}.md" })
            assertTrue(store.verify(id, previous.hash, previous.bytes))
            store.archivePrevious(id, previous.hash, current.hash)
            assertFalse(store.maintenanceFiles().any { it.name == "${previous.hash}.md" })
            assertTrue(store.verify(id, previous.hash, previous.bytes))
            assertTrue(store.verify(id, anchor.hash, anchor.bytes))
            assertTrue(store.verify(id, current.hash, current.bytes))
        } finally { failPublishSync = false; store.deleteAll(id) }
    }
    @Test fun compactedHistoryRestoresExactBytesAndProtectsItsAnchor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        val id = UUID.randomUUID().toString()
        try {
            val bytes = Random(17).nextBytes(1_000_000)
            val anchor = bytes.inputStream().use { store.writeAtomic(id, it) }
            val changed = bytes.copyOf().also { it[12345] = (it[12345].toInt() xor 1).toByte() }
            val previous = changed.inputStream().use { store.writeAtomic(id, it) }
            val current = (changed + "next".toByteArray()).inputStream().use { store.writeAtomic(id, it) }
            for (ref in listOf(anchor, previous, current)) store.committed(id, ref.hash)
            // Already-open range handles remain valid when the old .md is replaced by a delta.
            store.openRangeReader(id, previous.hash).use { existing ->
                store.archivePrevious(id, previous.hash, current.hash)
                assertArrayEquals(changed.copyOfRange(12000, 13000), existing.read(12000, 1000))
            }
            val entries = store.maintenanceFiles().filter { it.directoryKey == PrivateMarkdownStore.storageKey(id) }
            val history = entries.single { it.name == "${previous.hash}.delta" }
            assertTrue(history.bytes < 100_000)
            assertFalse(entries.any { it.name == "${previous.hash}.md" })
            assertEquals(previous.bytes, store.size(id, previous.hash))
            assertArrayEquals(changed, store.open(id, previous.hash).use { it.readBytes() })
            assertTrue(store.verify(id, previous.hash, previous.bytes))
            assertFalse(store.removeMaintenanceFile(entries.single { it.hash == anchor.hash }))
            assertArrayEquals(changed.copyOfRange(80000, 81000), store.readRange(id, previous.hash, 80000, 1000))
            store.materialize(id, previous.hash); store.committed(id, previous.hash)
            assertTrue(store.maintenanceFiles().any { it.name == "${previous.hash}.md" })
            assertFalse(store.maintenanceFiles().any { it.name == "${previous.hash}.delta" })
            assertTrue(store.verify(id, previous.hash, previous.bytes))
            assertTrue(store.verify(id, current.hash, current.bytes))
        } finally { store.deleteAll(id) }
    }
    @Test fun cancelledCompactionRetainsTheCompleteVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        val id = UUID.randomUUID().toString()
        try {
            val anchor = "a".repeat(100_000).byteInputStream().use { store.writeAtomic(id, it) }
            val previous = ("a".repeat(99999) + "b").byteInputStream().use { store.writeAtomic(id, it) }
            val current = "c".repeat(100_000).byteInputStream().use { store.writeAtomic(id, it) }
            try { store.archivePrevious(id, previous.hash, current.hash) { throw IOException("Interrupted") }; fail("Expected interruption") }
            catch (_: IOException) { }
            assertTrue(store.verify(id, previous.hash, previous.bytes))
            assertTrue(store.verify(id, anchor.hash, anchor.bytes))
            assertFalse(store.maintenanceFiles().any { it.name.endsWith(".delta") || it.name.endsWith(".tmp") })
        } finally { store.deleteAll(id) }
    }
}

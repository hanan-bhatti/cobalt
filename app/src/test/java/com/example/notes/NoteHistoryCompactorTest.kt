package com.example.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NoteHistoryCompactorTest {
    @Test fun latestSaveCompactsIntermediateCheckpointsAndKeepsCurrentMarkdown() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        val id = UUID.randomUUID().toString()
        try {
            val bytes = Random(83).nextBytes(200_000)
            val refs = (0..3).map { step ->
                val body = bytes + "version $step".toByteArray()
                body.inputStream().use { store.writeAtomic(id, it) }.also { store.committed(id, it.hash) }
            }
            // A final Save can have several preview checkpoints preceding its current body.
            NoteHistoryCompactor.enqueue(store, id, refs.first().hash, refs.last().hash) { true }
            var entries = emptyList<NoteStorageFile>()
            withTimeout(10_000) {
                do {
                    entries = store.maintenanceFiles().filter { it.directoryKey == PrivateMarkdownStore.storageKey(id) }
                    if (entries.count { it.name.endsWith(".delta") } == 2 && entries.count { it.name.endsWith(".md") } == 2) break
                    delay(10)
                } while (true)
            }
            assertEquals(2, entries.count { it.name.endsWith(".md") })
            assertTrue(entries.any { it.name == "${refs.last().hash}.md" })
            for ((index, ref) in refs.withIndex()) {
                assertArrayEquals(bytes + "version $index".toByteArray(), store.open(id, ref.hash).use { it.readBytes() })
            }
        } finally { store.deleteAll(id) }
    }
    @Test fun supersededSaveDoesNotPublishHistoryConversions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations)
        val id = UUID.randomUUID().toString()
        val checked = CountDownLatch(1)
        try {
            val bytes = Random(84).nextBytes(100_000)
            val refs = (0..2).map { step -> (bytes + byteArrayOf(step.toByte())).inputStream().use { store.writeAtomic(id, it) } }
            NoteHistoryCompactor.enqueue(store, id, refs.first().hash, refs.last().hash) {
                checked.countDown(); false
            }
            assertTrue(withContext(Dispatchers.IO) { checked.await(5, TimeUnit.SECONDS) })
            assertFalse(store.maintenanceFiles().any { it.directoryKey == PrivateMarkdownStore.storageKey(id) && it.name.endsWith(".delta") })
            for (ref in refs) assertTrue(store.verify(id, ref.hash, ref.bytes))
        } finally { store.deleteAll(id) }
    }
}

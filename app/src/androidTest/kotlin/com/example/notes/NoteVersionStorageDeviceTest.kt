package com.example.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android rename/fsync and range handles; only UUID-owned files in the debug sandbox. */
@RunWith(AndroidJUnit4::class)
class NoteVersionStorageDeviceTest {
    @Test fun verifiedHistoryRestoresWithoutChangingTheCurrentBody() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".debug"))
        val store = PrivateMarkdownStore(context)
        val id = "delta-device-${UUID.randomUUID()}"
        try {
            val bytes = Random(73).nextBytes(1_000_000)
            val anchor = bytes.inputStream().use { store.writeAtomic(id, it) }
            val changed = bytes + "history".toByteArray()
            val previous = changed.inputStream().use { store.writeAtomic(id, it) }
            val current = (changed + "current".toByteArray()).inputStream().use { store.writeAtomic(id, it) }
            store.openRangeReader(id, previous.hash).use { existing ->
                store.archivePrevious(id, previous.hash, current.hash)
                assertArrayEquals(changed.copyOfRange(100, 1100), existing.read(100, 1000))
            }
            assertTrue(store.maintenanceFiles().any { it.name == "${previous.hash}.delta" })
            assertArrayEquals(changed, store.open(id, previous.hash).use { it.readBytes() })
            assertTrue(store.verify(id, previous.hash, previous.bytes))
            store.materialize(id, previous.hash)
            store.committed(id, previous.hash)
            assertTrue(store.maintenanceFiles().any { it.name == "${previous.hash}.md" })
            assertTrue(store.verify(id, previous.hash, previous.bytes))
            assertTrue(store.verify(id, current.hash, current.bytes))
            assertTrue(store.verify(id, anchor.hash, anchor.bytes))
        } finally { store.deleteAll(id) }
    }
    @Test fun discardedPreparedConversionRetainsTheCompleteOldVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".debug"))
        val store = PrivateMarkdownStore(context)
        val id = "delta-device-${UUID.randomUUID()}"
        try {
            val base = Random(74).nextBytes(100_000)
            base.inputStream().use { store.writeAtomic(id, it) }
            val previous = (base + "one".toByteArray()).inputStream().use { store.writeAtomic(id, it) }
            val current = (base + "two".toByteArray()).inputStream().use { store.writeAtomic(id, it) }
            val prepared = requireNotNull(store.prepareArchivePrevious(id, previous.hash, current.hash))
            prepared.use { /* A closed/cancelled request never publishes its prepared delta. */ }
            assertTrue(store.maintenanceFiles().any { it.name == "${previous.hash}.md" })
            assertFalse(store.maintenanceFiles().any { it.name.endsWith(".delta") || it.name.endsWith(".tmp") })
            assertTrue(store.verify(id, previous.hash, previous.bytes))
        } finally { store.deleteAll(id) }
    }
}

package com.example.notes

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android storage, streamed 50 MB fixtures, isolated metadata and preferences. */
@RunWith(AndroidJUnit4::class)
class NoteLargeStorageDeepTest {
    @Test fun cacheCleanupAndDatabaseVacuumPreserveNotesAndProtectedWork() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        check(app.packageName.endsWith(".debug"))
        val prefix = "maintenance-test-${UUID.randomUUID()}-"
        val root = File(app.cacheDir, prefix).apply { check(mkdirs()) }
        val preferences = mutableSetOf<String>()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = if (File(name).isAbsolute) File(name) else app.getDatabasePath(prefix + name)
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                preferences.add(prefix + name)
                return app.getSharedPreferences(prefix + name, mode)
            }
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, context.getDatabasePath("cobalt_db").absolutePath).build()
        val store = PrivateMarkdownStore(context, database = database)
        val persistence = NotePersistence(context, database, store)
        val manager = NoteStorageManager(context, database, store)
        try {
            val note = persistence.save(SavedItem(type = SavedItemType.TEXT, title = "Maintenance fixture",
                content = "Keep this note 😀\r\n".repeat(100)))
            val work = File(context.cacheDir, "note-body-work").apply { mkdirs() }
            val scratch = File(work, "original-test.scratch").apply { writeText("active scratch") }
            val history = File(work, "test.history.tmp").apply { writeText("history scratch") }
            val unknown = File(work, "pending-recording.mp4").apply { writeText("pending recording") }
            val shares = File(context.cacheDir, "shares").apply { mkdirs() }
            val old = File(shares, "old.md").apply { writeText("old export"); setLastModified(System.currentTimeMillis() - 172_800_000) }
            val recent = File(shares, "recent.md").apply { writeText("recent share") }
            val owned = File(shares, "owned.png").apply { writeText("saved original"); setLastModified(System.currentTimeMillis() - 172_800_000) }
            persistence.save(SavedItem(type = SavedItemType.IMAGE, title = "Protected original", content = owned.absolutePath))
            NoteCacheWork.acquire(work)
            try {
                manager.clearCache()
                assertTrue(scratch.exists() && history.exists())
                assertFalse(old.exists())
                assertTrue(recent.exists() && owned.exists() && unknown.exists())
            } finally { NoteCacheWork.release(work) }
            manager.clearCache()
            assertFalse(scratch.exists() || history.exists())
            assertTrue(recent.exists() && owned.exists() && unknown.exists())
            val sqlite = database.openHelper.writableDatabase
            // Android may auto-vacuum new databases; explicitly create reclaimable free pages.
            sqlite.execSQL("PRAGMA auto_vacuum=NONE")
            sqlite.execSQL("VACUUM")
            sqlite.execSQL("CREATE TABLE deep_test_free_pages (body BLOB)")
            repeat(20) { sqlite.execSQL("INSERT INTO deep_test_free_pages VALUES (zeroblob(65536))") }
            sqlite.execSQL("DROP TABLE deep_test_free_pages")
            sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            fun freePages(): Long = sqlite.query("PRAGMA freelist_count").use { it.moveToFirst(); it.getLong(0) }
            val before = context.getDatabasePath("cobalt_db").length()
            assertTrue(freePages() > 0)
            manager.optimizeDatabase()
            val after = context.getDatabasePath("cobalt_db").length()
            assertEquals(0L, freePages())
            assertTrue("VACUUM did not reclaim the test's free pages", after < before)
            assertEquals(note.contentHash, database.savedItemDao().getItemById(note.id)!!.contentHash)
            assertTrue(store.verify(note.id, note.contentHash!!, note.byteLength))
            assertTrue(recent.exists() && owned.exists() && unknown.exists())
            val evidence = JSONObject().put("databaseBefore", before).put("databaseAfter", after)
                .put("noteVerified", true).put("activeScratchProtected", true)
                .put("ownedCacheOriginalProtected", true).put("recentShareProtected", true)
                .put("unknownRecordingProtected", true).put("oldExportRemoved", true)
            File(app.filesDir, "notes-deep-test-maintenance.json").writeText(evidence.toString(2))
            println("STORAGE_MAINTENANCE_MEASUREMENT: $evidence")
        } finally {
            database.close()
            app.deleteDatabase(prefix + "cobalt_db")
            root.deleteRecursively()
            preferences.forEach { app.deleteSharedPreferences(it) }
        }
    }

    @Test fun ten50MbEditsMeasureHistoryAndRestorationWithoutLosingBytes() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        check(app.packageName.endsWith(".debug"))
        val prefix = "large-storage-test-${UUID.randomUUID()}-"
        val root = File(app.cacheDir, prefix).apply { check(mkdirs()) }
        val preferences = mutableSetOf<String>()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                preferences.add(prefix + name)
                return app.getSharedPreferences(prefix + name, mode)
            }
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = PrivateMarkdownStore(context, sharedStorage = false, database = database)
        val persistence = NotePersistence(context, database, store)
        val manager = NoteStorageManager(context, database, store)
        val evidence = JSONArray()
        val size = 50_000_000L
        val fixture = File(root, "fixture.txt")
        try {
            // Printable pseudo-random text avoids claiming success from compressible repeated text.
            fixture.outputStream().use { output ->
                val random = Random(913)
                val buffer = ByteArray(16_384)
                var remaining = size
                while (remaining > 0) {
                    random.nextBytes(buffer)
                    for (i in buffer.indices) buffer[i] = (32 + ((buffer[i].toInt() and 255) % 95)).toByte()
                    val count = minOf(remaining, buffer.size.toLong()).toInt()
                    output.write(buffer, 0, count); remaining -= count
                }
            }
            fun stream(step: Int): InputStream = object : InputStream() {
                val source = fixture.inputStream()
                var position = 0L
                override fun read(): Int {
                    val b = ByteArray(1)
                    return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 255
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = source.read(b, off, len)
                    if (n > 0) {
                        // Cumulative changes at beginning, middle, and end of the document.
                        for (j in 1..step) {
                            val at = when (j % 3) { 0 -> size - 100 - j; 1 -> 100L + j; else -> size / 2 + j }
                            if (at >= position && at < position + n) {
                                val target = off + (at - position).toInt()
                                val replacement = ('A'.code + j).toByte()
                                b[target] = if (b[target] == replacement) ('a'.code + j).toByte() else replacement
                            }
                        }
                        position += n
                    }
                    return n
                }
                override fun close() = source.close()
            }
            suspend fun measure(label: String) {
                val report = manager.scan()
                val files = store.maintenanceFiles()
                val bodies = files.filter { it.hash != null }
                assertEquals(bodies.sumOf { it.bytes }, report.current + report.older)
                val entry = JSONObject().put("label", label).put("current", report.current)
                    .put("history", report.older).put("recovery", report.recovery)
                    .put("bodyBytes", bodies.sumOf { it.bytes })
                    .put("fullCopies", bodies.count { it.name.endsWith(".md") })
                    .put("deltas", bodies.count { it.name.endsWith(".delta") })
                evidence.put(entry)
                println("LARGE_STORAGE_MEASUREMENT: $entry")
            }
            var note = stream(0).use { persistence.importMarkdown(SavedItem(type = SavedItemType.TEXT,
                title = "Deep test 50 MB", content = ""), it) }
            val generations = mutableListOf(NoteGeneration(note.contentHash!!, size, note.preview))
            measure("import")
            repeat(10) { index ->
                val step = index + 1
                val started = System.nanoTime()
                note = stream(step).use { persistence.saveStream(note, it) }
                generations.add(NoteGeneration(note.contentHash!!, size, note.preview))
                if (step > 1) {
                    val deadline = System.nanoTime() + 90_000_000_000L
                    while (store.maintenanceFiles().count { it.name.endsWith(".delta") } < step - 1 && System.nanoTime() < deadline) delay(100)
                    assertEquals("Background compaction did not finish", step - 1, store.maintenanceFiles().count { it.name.endsWith(".delta") })
                }
                measure("edit-$step")
                evidence.getJSONObject(evidence.length() - 1).put("saveAndCompactionMs", (System.nanoTime() - started) / 1_000_000)
            }
            for ((step, generation) in generations.withIndex()) {
                val expected = stream(step).use { PrivateMarkdownStore.digest(it) }
                assertEquals("Generation $step hash changed", expected, generation.hash)
                assertTrue("Generation $step cannot be reconstructed", store.verify(note.id, generation.hash, size))
            }
            val entries = store.maintenanceFiles().filter { it.hash != null }
            assertEquals(2, entries.count { it.name.endsWith(".md") })
            assertEquals(9, entries.count { it.name.endsWith(".delta") })
            assertTrue("Repeated edits retained full duplicates", entries.sumOf { it.bytes } < size * 3)
            // Metadata-only changes must preserve the hash and physical body count.
            val beforeMetadata = entries.sumOf { it.bytes }
            note = persistence.save(note.copy(title = "Renamed deep test", bodyLoaded = false))
            assertEquals(generations.last().hash, note.contentHash)
            assertEquals(beforeMetadata, store.maintenanceFiles().filter { it.hash != null }.sumOf { it.bytes })
            measure("metadata-only")
            // Restore a delta through the actual storage-screen manager, then restore the latest.
            val copy = manager.olderCopies().single { it.hash == generations[5].hash }
            assertTrue(copy.delta)
            manager.restore(copy)
            assertEquals(copy.hash, database.savedItemDao().getItemById(note.id)!!.contentHash)
            assertTrue(store.verify(note.id, copy.hash, size))
            measure("restore-delta")
            val latest = manager.olderCopies().single { it.hash == generations.last().hash }
            manager.restore(latest)
            assertEquals(latest.hash, database.savedItemDao().getItemById(note.id)!!.contentHash)
            assertTrue(store.verify(note.id, latest.hash, size))
            measure("restore-latest")
        } finally {
            File(app.filesDir, "notes-deep-test-measurements.json").writeText(evidence.toString(2))
            database.close()
            root.deleteRecursively()
            preferences.forEach { app.deleteSharedPreferences(it) }
        }
    }
}

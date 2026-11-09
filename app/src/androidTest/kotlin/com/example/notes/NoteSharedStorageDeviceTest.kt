package com.example.notes

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class NoteSharedStorageDeviceTest {
    private class Sandbox(val app: Context, val token: String) : ContextWrapper(app) {
        val root = File(app.filesDir, token).apply { mkdirs() }
        val preferences = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getApplicationInfo() = ApplicationInfo(app.applicationInfo).apply { dataDir = root.path }
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String) = if (File(name).isAbsolute) File(name) else app.getDatabasePath("$token-$name")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            preferences.add("$token-$name"); return app.getSharedPreferences("$token-$name", mode)
        }
        fun database() = Room.databaseBuilder(this, AppDatabase::class.java, getDatabasePath("cobalt_db").absolutePath)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) { db.execSQL("PRAGMA synchronous=EXTRA") }
            }).build()
        fun remove() { app.deleteDatabase("$token-cobalt_db"); root.deleteRecursively(); preferences.forEach { app.deleteSharedPreferences(it) } }
    }
    private fun app(): Context = ApplicationProvider.getApplicationContext<Context>().also { check(it.packageName.endsWith(".debug")) }
    private fun textSource(text: String): NoteByteSource {
        val data = text.toByteArray()
        return object : NoteByteSource {
            override val size = data.size.toLong()
            override fun read(offset: Long, count: Int) = data.copyOfRange(offset.toInt(), minOf(data.size, offset.toInt() + count))
        }
    }
    private fun scalar(db: AppDatabase, sql: String) = db.openHelper.writableDatabase.query(sql).use { check(it.moveToFirst()); it.getLong(0) }

    @Test fun hundred50MbEditsShareBytesRestoreExactlyAndSurviveDatabaseReopen() = runBlocking {
        val app = app(); val context = Sandbox(app, "shared-device-${UUID.randomUUID()}")
        var db = context.database()
        val evidence = JSONObject()
        try {
            var store = PrivateMarkdownStore(context, database = db)
            var persistence = NotePersistence(context, db, store)
            val fixture = File(context.root, "fixture.txt")
            val size = 50_000_000L
            fixture.outputStream().use { out ->
                val random = Random(967); val buffer = ByteArray(16_384); var remaining = size
                while (remaining > 0) {
                    random.nextBytes(buffer)
                    for (i in buffer.indices) buffer[i] = (32 + ((buffer[i].toInt() and 255) % 95)).toByte()
                    val n = minOf(remaining, buffer.size.toLong()).toInt(); out.write(buffer, 0, n); remaining -= n
                }
            }
            var note = fixture.inputStream().use { persistence.importMarkdown(SavedItem(type = SavedItemType.TEXT, title = "Shared 50 MB fixture", content = ""), it) }
            val basePhysical = context.getDatabasePath("cobalt_db").length()
            val basePayload = scalar(db, "SELECT SUM(length(data)) FROM note_chunks")
            val versions = mutableListOf(NoteGeneration(note.contentHash!!, note.byteLength, note.preview))
            val timings = mutableListOf<Long>()
            repeat(100) { i ->
                // A shifted insertion tests sharing beyond fixed chunk boundaries.
                val at = when (i % 3) { 0 -> 0L; 1 -> size / 2; else -> note.byteLength }
                val insert = "edit-$i 🙂 اردو\n"
                val started = android.os.SystemClock.elapsedRealtime()
                note = persistence.replaceWindow(note.id, note.contentHash!!, EditableNoteWindow(at, at, ""), textSource(insert))
                timings.add(android.os.SystemClock.elapsedRealtime() - started)
                RandomAccessFile(fixture, "rw").use { file ->
                    // Independent expected file with bounded backwards shifting.
                    val added = insert.toByteArray(); val old = file.length(); file.setLength(old + added.size)
                    val buffer = ByteArray(16_384); var end = old
                    while (end > at) {
                        val start = maxOf(at, end - buffer.size); val count = (end - start).toInt()
                        file.seek(start); file.readFully(buffer, 0, count); file.seek(start + added.size); file.write(buffer, 0, count); end = start
                    }
                    file.seek(at); file.write(added)
                }
                assertEquals("Wrong independent content at edit $i", fixture.inputStream().use { PrivateMarkdownStore.digest(it) }, note.contentHash)
                versions.add(NoteGeneration(note.contentHash!!, note.byteLength, note.preview))
            }
            val payloadAfter = scalar(db, "SELECT SUM(length(data)) FROM note_chunks")
            val physicalAfter = context.getDatabasePath("cobalt_db").length()
            assertTrue("Body payload duplicated: $payloadAfter", payloadAfter - basePayload < 10_000)
            assertTrue("100 edits should not cost document copies: $physicalAfter", physicalAfter - basePhysical < 4_000_000)
            versions.forEachIndexed { i, version -> assertTrue("Version $i cannot reconstruct", store.verify(note.id, version.hash, version.bytes)) }
            val beforeRestore = context.getDatabasePath("cobalt_db").length()
            val manager = NoteStorageManager(context, db, store)
            manager.restore(manager.olderCopies().single { it.hash == versions[20].hash })
            note = persistence.load(note.id, false)!!
            assertEquals(versions[20].hash, note.contentHash)
            manager.restore(manager.olderCopies().single { it.hash == versions.last().hash })
            assertEquals(payloadAfter, scalar(db, "SELECT SUM(length(data)) FROM note_chunks"))
            assertTrue(context.getDatabasePath("cobalt_db").length() - beforeRestore < 100_000)
            db.close(); db = context.database(); store = PrivateMarkdownStore(context, database = db); persistence = NotePersistence(context, db, store)
            note = persistence.load(note.id, false)!!
            assertEquals(versions.last().hash, note.contentHash)
            versions.forEach { assertTrue(store.verify(note.id, it.hash, it.bytes)) }
            val connection = db.openHelper.writableDatabase
            assertEquals("ok", connection.query("PRAGMA integrity_check").use { it.moveToFirst(); it.getString(0) })
            val report = NoteStorageManager(context, db, store).scan()
            assertTrue(report.current > size && report.older < 4_000_000)
            evidence.put("baseDatabaseBytes", basePhysical).put("after100EditsDatabaseBytes", physicalAfter)
                .put("incrementalDatabaseBytes", physicalAfter - basePhysical).put("incrementalPayloadBytes", payloadAfter - basePayload)
                .put("verifiedVersions", versions.size).put("saveMedianMs", timings.sorted()[50]).put("saveMaxMs", timings.max())
                .put("storageCurrent", report.current).put("storageHistory", report.older).put("storageDatabaseOverhead", report.database)
                .put("reopenVerified", true).put("restorePayloadGrowth", 0)
                .put("sqliteVersion", connection.query("SELECT sqlite_version()").use { it.moveToFirst(); it.getString(0) })
                .put("journalMode", connection.query("PRAGMA journal_mode").use { it.moveToFirst(); it.getString(0) })
                .put("synchronous", scalar(db, "PRAGMA synchronous")).put("pageSize", scalar(db, "PRAGMA page_size"))
            println("SHARED_STORAGE_DEVICE: $evidence")
            File(app.filesDir, "notes-shared-device-measurements.json").writeText(evidence.toString(2))
        } finally { db.close(); context.remove() }
    }

    @Test fun publicationFaultsAndStaleSavePreserveOriginalOnRealAndroid() = runBlocking {
        val context = Sandbox(app(), "shared-fault-${UUID.randomUUID()}"); val db = context.database()
        val store = PrivateMarkdownStore(context, database = db); val persistence = NotePersistence(context, db, store)
        try {
            val original = persistence.save(SavedItem(type = SavedItemType.TEXT, title = "Fault fixture", content = "Original 🙂 اردو"))
            for (stage in PrivateMarkdownStore.WriteStage.entries) {
                val broken = PrivateMarkdownStore(context, database = db, checkpoint = { if (it == stage) throw IOException("Injected $stage") })
                assertTrue(runCatching { NotePersistence(context, db, broken).save(original.copy(content = "replacement $stage", bodyLoaded = true)) }.isFailure)
                assertEquals(original.contentHash, persistence.load(original.id, false)!!.contentHash)
                assertTrue(store.verify(original.id, original.contentHash!!, original.byteLength))
            }
            val next = persistence.save(original.copy(content = "new saved text", bodyLoaded = true))
            assertTrue(runCatching { persistence.save(original.copy(content = "stale edit", bodyLoaded = true)) }.isFailure)
            assertEquals(next.contentHash, persistence.load(original.id, false)!!.contentHash)
        } finally { db.close(); context.remove() }
    }

    @Test fun disposableCleanupPreservesRealDraftsTrashMediaAndReaders() = runBlocking {
        val context = Sandbox(app(), "shared-cleanup-${UUID.randomUUID()}"); val db = context.database()
        val store = PrivateMarkdownStore(context, database = db); val persistence = NotePersistence(context, db, store)
        try {
            val first = persistence.save(SavedItem(type = SavedItemType.TEXT, title = "Cleanup versions", content = "original version"))
            val current = persistence.save(first.copy(content = "current version 🙂", bodyLoaded = true))
            val protected = persistence.save(SavedItem(type = SavedItemType.TEXT, title = "Active writing", content = "protected original"))
            val quick = QuickNoteDraftStore(context) { persistence }
            quick.writeTitle("Unsaved title"); val draft = quick.write("Unsaved quick writing 🙂 اردو")!!
            val sections = SectionDraftStore(context) { persistence }
            val section = sections.save(protected.id, protected.contentHash!!, 0, 0, "Unsaved section\n")
            val trash = TrashManager(context, db, store, scheduleWork = false)
            val trashed = persistence.save(SavedItem(type = SavedItemType.TEXT, title = "Trash protected", content = "keep in Trash"))
            assertTrue(trash.delete(trashed.id))
            val export = File(context.cacheDir, "note_exports/old.md").apply { parentFile!!.mkdirs(); writeText("temporary export"); setLastModified(1) }
            val recording = File(context.cacheDir, "unknown-recording.mp4").apply { writeText("unfinished recording") }
            val image = File(context.cacheDir, "shares/owned.png").apply { parentFile!!.mkdirs(); writeText("owned original"); setLastModified(1) }
            persistence.save(SavedItem(type = SavedItemType.IMAGE, title = "Saved media", content = image.path))
            context.getSharedPreferences("fixture-setting", 0).edit().putBoolean("keep", true).commit()
            val manager = NoteStorageManager(context, db, store)
            store.openRangeReader(protected.id, protected.contentHash!!).use { reader ->
                val review = manager.reviewDisposable()
                assertTrue(review.entries.any { it.hash == first.contentHash })
                assertTrue(manager.clearDisposable(review).files > 0)
                assertEquals("protected original", reader.read(0, 100).toString(Charsets.UTF_8))
            }
            assertNull(store.size(first.id, first.contentHash!!))
            assertTrue(store.verify(current.id, current.contentHash!!, current.byteLength))
            assertEquals(draft.hash, quick.load().snapshot!!.hash)
            assertEquals("Unsaved title", quick.load().title)
            assertEquals(section, sections.load(protected.id))
            assertTrue(store.verify(trashed.id, trashed.contentHash!!, trashed.byteLength))
            assertTrue(image.exists() && recording.exists()); assertFalse(export.exists())
            assertTrue(context.getSharedPreferences("fixture-setting", 0).getBoolean("keep", false))
            // Applying a protected external draft also tests source-owner conversion.
            sections.apply(protected.id, section)
            assertEquals("Unsaved section\nprotected original", persistence.load(protected.id)!!.content)
            trash.restore(trashed.id)
            assertEquals(trashed.contentHash, persistence.load(trashed.id, false)!!.contentHash)
            assertEquals("ok", db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { it.moveToFirst(); it.getString(0) })
            println("DISPOSABLE_CLEANUP_DEVICE: saved versions, drafts, readers, Trash, media, settings and unknown recording verified")
        } finally { db.close(); context.remove() }
    }
    @Test fun sqliteFullDuringCheckpointRetainsSavedHeadAndDurableLiveWriting() = runBlocking {
        val context = Sandbox(app(), "shared-full-${UUID.randomUUID()}"); val db = context.database()
        val store = PrivateMarkdownStore(context, database = db); val persistence = NotePersistence(context, db, store)
        try {
            val saved = persistence.save(SavedItem(type = SavedItemType.TEXT, title = "Full database fixture", content = "Saved original 🙂\n"))
            val base = NoteBodyRef(saved.contentHash!!, saved.byteLength, saved.preview)
            val token = UUID.randomUUID().toString()
            var expected = ""
            store.openLiveEditor(saved.id, token, base).use { model ->
                model.replace(model.status.characters, model.status.characters, "unsaved writing 🙂 اردو\n".repeat(20_000))
                expected = model.openUtf8().use { PrivateMarkdownStore.digest(it) }
                val connection = db.openHelper.writableDatabase
                val pages = scalar(db, "PRAGMA page_count")
                val cappedPages = connection.query("PRAGMA max_page_count=$pages").use {
                    assertTrue(it.moveToFirst()); it.getLong(0)
                }
                assertEquals(pages, cappedPages)
                assertEquals(pages, scalar(db, "PRAGMA max_page_count"))
                val failure = try {
                    runCatching { model.openUtf8().use { store.writePieces(saved.id, it, model.sharedPieces()) } }.exceptionOrNull()
                } finally {
                    connection.query("PRAGMA max_page_count=2147483646").use {
                        assertTrue(it.moveToFirst()); assertTrue(it.getLong(0) > pages)
                    }
                }
                assertTrue("Expected real SQLite full error, got $failure", failure is android.database.sqlite.SQLiteFullException)
                assertEquals(expected, model.openRecoveryUtf8().use { PrivateMarkdownStore.digest(it) })
            }
            assertEquals(base.hash, persistence.load(saved.id, false)!!.contentHash)
            assertTrue(store.verify(saved.id, base.hash, base.bytes))
            store.openLiveEditor(saved.id, token, base, reopen = true).use { model ->
                assertEquals(expected, model.openUtf8().use { PrivateMarkdownStore.digest(it) })
                val prepared = model.openUtf8().use { store.writePieces(saved.id, it, model.sharedPieces()) }
                persistence.commitPrepared(saved, prepared)
                assertTrue(store.verify(saved.id, prepared.hash, prepared.bytes))
            }
            assertEquals("ok", db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { it.moveToFirst(); it.getString(0) })
            println("SQLITE_FULL_DEVICE: failed checkpoint preserved saved head and recoverable writing; retry verified")
        } finally { db.close(); context.remove() }
    }

    /** Host kills this process at the marker. Ordinary suite execution skips this phase. */
    @Test fun stageRealProcessDeath() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("crashPhase") ?: return@runBlocking
        val context = Sandbox(app(), "notes-shared-process-death")
        val db = context.database(); val store = PrivateMarkdownStore(context, database = db); val persistence = NotePersistence(context, db, store)
        val id = "process-death-fixture"; val token = "11111111-1111-4111-8111-111111111111"
        val saved = persistence.save(SavedItem(id = id, type = SavedItemType.TEXT, title = "Process death fixture", content = "Saved original 🙂 اردو\n"))
        val base = NoteBodyRef(saved.contentHash!!, saved.byteLength, saved.preview)
        val model = store.openLiveEditor(id, token, base)
        model.replace(model.status.characters, model.status.characters, "Durable unsaved writing 🙂 اردو\n")
        context.getSharedPreferences("fixture", 0).edit().putString("savedHash", base.hash).putLong("savedBytes", base.bytes)
            .putString("draftHash", model.openUtf8().use { PrivateMarkdownStore.digest(it) }).commit()
        fun awaitKill() {
            File(context.root, "ready-to-kill").writeText(phase)
            while (true) Thread.sleep(1000)
        }
        if (phase == "live") {
            var checks = 0
            model.replace(model.status.characters, model.status.characters, StringReader("interrupted insertion\n".repeat(30_000)), checkCancelled = {
                if (++checks == 4) awaitKill()
            })
        } else {
            val broken = PrivateMarkdownStore(context, database = db, checkpoint = { if (it == PrivateMarkdownStore.WriteStage.DATA_SYNCED) awaitKill() })
            broken.writeReplacement(id, base.hash, "Unpublished shared root\n".repeat(50_000).byteInputStream())
        }
        fail("Host did not kill process")
    }
    @Test fun recoverAfterRealProcessDeath() = runBlocking {
        if (InstrumentationRegistry.getArguments().getString("crashPhase") == null) return@runBlocking
        val context = Sandbox(app(), "notes-shared-process-death"); val db = context.database(); val store = PrivateMarkdownStore(context, database = db)
        try {
            val prefs = context.getSharedPreferences("fixture", 0)
            val id = "process-death-fixture"; val base = NoteBodyRef(prefs.getString("savedHash", null)!!, prefs.getLong("savedBytes", -1), "")
            assertEquals(base.hash, NotePersistence(context, db, store).load(id, false)!!.contentHash)
            assertTrue(store.verify(id, base.hash, base.bytes))
            store.openLiveEditor(id, "11111111-1111-4111-8111-111111111111", base, reopen = true).use { recovered ->
                assertEquals(prefs.getString("draftHash", null), recovered.openUtf8().use { PrivateMarkdownStore.digest(it) })
                assertTrue(recovered.undo()); assertEquals(base.hash, recovered.openUtf8().use { PrivateMarkdownStore.digest(it) })
                assertTrue(recovered.redo()); assertEquals(prefs.getString("draftHash", null), recovered.openUtf8().use { PrivateMarkdownStore.digest(it) })
            }
            store.collectSharedStorage()
            assertEquals("ok", db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { it.moveToFirst(); it.getString(0) })
            println("REAL_PROCESS_DEATH_RECOVERY: original, unsaved draft, undo, redo and integrity verified")
        } finally { db.close(); context.remove() }
    }
}

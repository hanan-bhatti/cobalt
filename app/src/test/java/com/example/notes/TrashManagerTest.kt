package com.example.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import java.io.IOException
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TrashManagerTest {
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val files = PrivateMarkdownStore(context, fileOperations = HostTestNoteFileOperations, sharedStorage = false)
        var failAuxiliaryDelete = false
        val store = object : NoteBodyStore by files {
            override fun deleteAll(id: String) {
                if (failAuxiliaryDelete && id.startsWith("capture-window:")) throw IOException("Simulated cleanup failure")
                files.deleteAll(id)
            }
        }
        var time = 1_000_000L
        val manager = TrashManager(context, database, store, now = { time }, scheduleWork = false, fileOperations = HostTestNoteFileOperations)
        val persistence = NotePersistence(context, database, store)
        val ids = ArrayList<String>()
        suspend fun note(text: String = "Original body😀\r\n"): SavedItem {
            manager.setEnabled(true)
            val item = SavedItem(type = SavedItemType.TEXT, title = "Trash test", content = "", timestamp = time, updatedAt = time)
            ids.add(item.id)
            return text.byteInputStream().use { persistence.saveStream(item, it) }
        }
        override fun close() {
            failAuxiliaryDelete = false
            runBlocking {
                database.savedItemDao().mediaStorageItems().filter { it.id in ids }.forEach { row ->
                    listOfNotNull(row.path, row.thumbnailPath).filter { it.startsWith('/') }.forEach { path ->
                        val file = File(path).canonicalFile
                        if (file.path.startsWith(context.cacheDir.canonicalPath + "/") || file.path.startsWith(File(context.filesDir, "media").canonicalPath + "/")) file.delete()
                    }
                }
            }
            for (id in ids) {
                for (identity in listOf(id, "capture-window:$id", "section-draft:$id")) files.deleteAll(identity)
                val editor = context.getSharedPreferences("permanently_deleted_notes", Context.MODE_PRIVATE).edit()
                for (identity in listOf(id, "capture-window:$id", "section-draft:$id")) editor.remove(PrivateMarkdownStore.storageKey(identity))
                editor.commit()
                for (name in listOf("capture_file_sessions", "note_section_drafts")) context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().remove(id).commit()
            }
            context.getSharedPreferences("note_storage_management", Context.MODE_PRIVATE).edit().remove("trash_enabled").commit()
            database.close()
        }
    }
    @Test fun cachedMediaIsMadeDurableBeforeTrashAndDeletedOnlyAfterPermanentRemoval() = runBlocking {
        Fixture().use { f ->
            val original = File(f.context.cacheDir, "trash-test-${java.util.UUID.randomUUID()}.jpg").also { it.writeText("private captured image") }
            val item = SavedItem(type = SavedItemType.IMAGE, title = "Image", content = original.path)
            f.ids.add(item.id)
            f.database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item))
            f.manager.delete(item.id)
            val stored = f.database.savedItemDao().getItemById(item.id)!!
            val durable = File(stored.content)
            assertTrue(durable.path.startsWith(f.context.filesDir.path))
            assertEquals("private captured image", durable.readText())
            assertFalse(original.exists())
            f.manager.restore(item.id)
            assertTrue(durable.isFile)
            f.manager.delete(item.id); f.manager.deleteForever(item.id)
            assertFalse(durable.exists())
        }
    }
    @Test fun megabyteMediaAccountingFollowsTrashRestoreAndPurge() = runBlocking {
        Fixture().use { f ->
            f.manager.setEnabled(true)
            val manager = NoteStorageManager(f.context, f.database, f.store)
            val cases = listOf(Triple(SavedItemType.IMAGE, "images", "png"),
                Triple(SavedItemType.VIDEO, "videos", "mp4"), Triple(SavedItemType.AUDIO, "audio", "wav"))
            for ((type, directory, extension) in cases) {
                val id = java.util.UUID.randomUUID().toString()
                val file = File(f.context.filesDir, "media/$directory/$id.$extension")
                file.parentFile!!.mkdirs()
                // Accounting works on bytes; decoding/playback is tested separately on device.
                file.outputStream().use { output -> repeat(32) { output.write(ByteArray(65_536) { 42 }) } }
                val item = SavedItem(id = id, type = type, title = "Media accounting", content = if (type == SavedItemType.AUDIO) "" else file.path,
                    thumbnailPath = if (type == SavedItemType.AUDIO) file.path else null)
                f.ids.add(id); f.database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item))
                val before = manager.scan()
                assertTrue(before.media >= file.length())
                val typeBytes = when (type) { SavedItemType.IMAGE -> before.images; SavedItemType.VIDEO -> before.video; else -> before.audio }
                assertTrue(typeBytes >= file.length())
                f.manager.delete(id)
                val trashed = manager.scan()
                assertEquals(before.media - 2_097_152, trashed.media)
                assertEquals(before.trash + 2_097_152, trashed.trash)
                f.manager.restore(id)
                assertEquals(before.media, manager.scan().media)
                assertTrue(file.exists())
                f.manager.delete(id); f.manager.deleteForever(id)
                assertFalse(file.exists())
                val purged = manager.scan()
                assertEquals(before.media - 2_097_152, purged.media)
                assertEquals(before.trash, purged.trash)
            }
        }
    }

    @Test fun permanentDeletionKeepsSharedMediaUntilItsLastOwnerIsRemoved() = runBlocking {
        Fixture().use { f ->
            val directory = File(f.context.filesDir, "media/images").also { it.mkdirs() }
            val file = File(directory, "shared-${java.util.UUID.randomUUID()}.jpg").also { it.writeText("shared capture") }
            val first = SavedItem(type = SavedItemType.IMAGE, title = "First", content = file.path)
            val second = first.copy(id = java.util.UUID.randomUUID().toString(), title = "Second")
            for (item in listOf(first, second)) { f.ids.add(item.id); f.database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item)) }
            f.manager.delete(first.id); f.manager.deleteForever(first.id)
            assertTrue(file.exists())
            f.manager.delete(second.id); f.manager.deleteForever(second.id)
            assertFalse(file.exists())
        }
    }
    @Test fun deleteHidesTheNoteAndRestoreKeepsExactBodyAndIdentity() = runBlocking {
        Fixture().use { f ->
            val note = f.note()
            assertTrue(f.manager.delete(note.id))
            val entry = f.database.trashDao().entry(note.id)!!
            assertEquals(f.time + TrashPolicy.RETENTION_MS, entry.expiresAt)
            assertTrue(f.database.savedItemDao().getAllItems().isEmpty())
            assertTrue(f.files.verify(note.id, note.contentHash!!, note.byteLength))
            f.manager.restore(note.id)
            assertNull(f.database.trashDao().entry(note.id))
            assertEquals(note.id, f.database.savedItemDao().getAllItems().single().id)
            assertEquals("Original body😀\r\n", f.files.open(note.id, note.contentHash).bufferedReader().use { it.readText() })
        }
    }
    @Test fun restoringLegacyInlineProjectionNeverOverwritesItsLargeBody() = runBlocking {
        Fixture().use { f ->
            val body = "legacy😀\r\n".repeat(250_000)
            val note = SavedItem(type = SavedItemType.TEXT, title = "Legacy", content = body, bodyLocation = "INLINE")
            f.ids.add(note.id)
            f.database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(note))
            f.manager.delete(note.id)
            f.manager.restore(note.id)
            assertEquals(body, LegacyBodyInputStream(f.database.savedItemDao(), note.id).bufferedReader().use { it.readText() })
        }
    }
    @Test fun permanentDeletionRemovesEveryGenerationDraftAndPreventsLateResurrection() = runBlocking {
        Fixture().use { f ->
            val note = f.note()
            for (id in listOf(note.id, "capture-window:${note.id}", "section-draft:${note.id}")) {
                "another retained copy".byteInputStream().use { f.files.writeAtomic(id, it) }
            }
            for (name in listOf("capture_file_sessions", "note_section_drafts")) f.context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString(note.id, "{}").commit()
            f.manager.delete(note.id); f.manager.deleteForever(note.id)
            assertNull(f.database.savedItemDao().getItemById(note.id))
            assertNull(f.database.trashDao().entry(note.id))
            assertFalse(f.files.maintenanceFiles().any { it.directoryKey == PrivateMarkdownStore.storageKey(note.id) })
            for (id in listOf("capture-window:${note.id}", "section-draft:${note.id}")) {
                assertFalse(f.files.maintenanceFiles().any { it.directoryKey == PrivateMarkdownStore.storageKey(id) })
            }
            try { "late autosave".byteInputStream().use { f.files.writeAtomic(note.id, it) }; fail("Late save recreated deleted files") }
            catch (_: IllegalStateException) { }
            try { f.persistence.save(note.copy(title = "Late metadata")); fail("Late metadata recreated the note") }
            catch (_: IllegalStateException) { }
        }
    }
    @Test fun trashExpiresAtThirtyDaysAndOffUsesOnlyTheUndoGrace() = runBlocking {
        Fixture().use { f ->
            val note = f.note(); f.manager.delete(note.id)
            f.time += TrashPolicy.RETENTION_MS - 1
            assertEquals(0, f.manager.expire())
            f.time++
            assertEquals(1, f.manager.expire())
            val second = f.note()
            f.manager.setEnabled(false); f.manager.delete(second.id)
            f.time += TrashPolicy.UNDO_GRACE_MS - 1
            f.manager.restore(second.id)
            f.time++; f.manager.delete(second.id)
            f.time += TrashPolicy.UNDO_GRACE_MS
            assertEquals(1, f.manager.expire())
            assertNull(f.database.savedItemDao().getItemById(second.id))
        }
    }
    @Test fun disablingTrashDoesNotPurgeItsExistingThirtyDayItems() = runBlocking {
        Fixture().use { f ->
            val note = f.note(); f.manager.delete(note.id)
            val deadline = f.database.trashDao().entry(note.id)!!.expiresAt
            f.manager.setEnabled(false); f.time += TrashPolicy.UNDO_GRACE_MS + 1
            assertEquals(0, f.manager.expire())
            assertEquals(deadline, f.database.trashDao().entry(note.id)!!.expiresAt)
        }
    }
    @Test fun interruptedPhysicalDeletionCannotBeRestoredAndResumesWithoutLosingTheTombstone() = runBlocking {
        Fixture().use { f ->
            val note = f.note(); f.manager.delete(note.id)
            f.failAuxiliaryDelete = true
            try { f.manager.deleteForever(note.id); fail("Expected interrupted cleanup") } catch (_: IOException) { }
            assertTrue(f.database.trashDao().entry(note.id)!!.purgeStarted)
            assertTrue(f.database.savedItemDao().isItemDeleted(note.id) == true)
            try { f.manager.restore(note.id); fail("Restored a partly deleted note") } catch (_: IllegalStateException) { }
            f.failAuxiliaryDelete = false
            assertEquals(1, f.manager.expire())
            assertNull(f.database.trashDao().entry(note.id))
        }
    }
    @Test fun staleEmptyTrashConfirmationCannotRemoveNewlyTrashedItems() = runBlocking {
        Fixture().use { f ->
            val first = f.note(); f.manager.delete(first.id)
            val review = mapOf(first.id to f.database.trashDao().entry(first.id)!!.deletedAt)
            f.manager.restore(first.id); f.time++
            f.manager.delete(first.id)
            val second = f.note(); f.manager.delete(second.id)
            assertEquals(0, f.manager.empty(review))
            assertEquals(2, f.database.trashDao().ids().size)
        }
    }
}

package com.example.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SharedNoteStorageTest {
    private fun fixture(test: (Context, AppDatabase, PrivateMarkdownStore, String) -> Unit) {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val store=PrivateMarkdownStore(context,fileOperations=HostTestNoteFileOperations,database=db)
        val id="shared-${UUID.randomUUID()}"
        try { test(context,db,store,id) } finally { store.deleteAll(id);db.close() }
    }
    @Test fun thousandTinyRevisionsSharePayloadAndEveryVersionRemainsExact() = fixture { _,db,store,id ->
        val body=Random(501).nextBytes(512_000)
        var ref=store.writeAtomic(id,body.inputStream());store.committed(id,ref.hash)
        fun hash(data: ByteArray)=java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it.toInt() and 255)}
        val versions=mutableListOf(ref to hash(body))
        repeat(1000) { i ->
            val offset=(i*7919L % body.size).toInt();body[offset]=(body[offset]+1).toByte()
            val previous=ref
            val plan=listOf(SharedNotePiece(previous.hash,0,offset.toLong()),
                SharedNotePiece(null,0,1){byteArrayOf(body[offset]).inputStream()},
                SharedNotePiece(previous.hash,offset+1L,body.size-offset-1L))
            ref=store.writePieces(id,body.inputStream(),plan);store.committed(id,ref.hash)
            versions.add(ref to hash(body))
        }
        val payload=db.openHelper.writableDatabase.query("SELECT SUM(length(data)) FROM note_chunks").use { it.moveToFirst();it.getLong(0) }
        assertTrue("Payload duplicated: $payload",payload<=512_000+256)
        versions.forEach { (generation,expected) -> assertEquals(expected,store.open(id,generation.hash).use { PrivateMarkdownStore.digest(it) }) }
        val before=payload
        store.materialize(id,versions[101].first.hash)
        assertEquals(before,db.openHelper.writableDatabase.query("SELECT SUM(length(data)) FROM note_chunks").use { it.moveToFirst();it.getLong(0) })
        assertFalse(store.maintenanceFiles().any { it.name.endsWith(".md") || it.name.endsWith(".delta") })
    }
    @Test fun failureAtEveryPublicationStagePreservesTheSavedHead() = fixture { context,db,store,id ->
        runBlocking {
            val persistence=NotePersistence(context,db,store)
            val saved=persistence.save(SavedItem(type=com.example.data.model.SavedItemType.TEXT,title="Shared fixture",id=id,content="Original 🙂 اردو",bodyLoaded=true))
            for(stage in PrivateMarkdownStore.WriteStage.entries) {
                val broken=PrivateMarkdownStore(context,fileOperations=HostTestNoteFileOperations,
                    checkpoint={if(it==stage)throw IOException("Injected $stage")},database=db)
                try { NotePersistence(context,db,broken).save(saved.copy(content="Changed $stage",bodyLoaded=true));fail("Failure was ignored") }
                catch(_:IOException) { }
                assertEquals(saved.contentHash,db.savedItemDao().getItemById(id)!!.contentHash)
                assertTrue(store.verify(id,saved.contentHash!!,saved.byteLength))
            }
        }
    }
    @Test fun residentAndStreamedInsertionsShareShiftedText() = fixture { context,db,store,id ->
        runBlocking {
            val persistence=NotePersistence(context,db,store)
            var text="🙂 اردو original\n"+Random(912).nextBytes(180_000).map { ('a'.code+(it.toInt() and 255)%26).toChar() }.joinToString("")
            var note=persistence.save(SavedItem(id=id,type=com.example.data.model.SavedItemType.TEXT,title="Shifted fixture",content=text))
            val base=note.byteLength
            repeat(200) { i ->
                val at=if(i%2==0)0 else text.length/2
                text=text.substring(0,at)+"addition-$i 🙂 اردو\n"+text.substring(at)
                note=if(i%2==0)persistence.save(note.copy(content=text,bodyLoaded=true))
                    else text.byteInputStream().use { persistence.saveStream(note,it) }
                assertEquals(text.byteInputStream().use { PrivateMarkdownStore.digest(it) },note.contentHash)
            }
            val payload=db.openHelper.writableDatabase.query("SELECT SUM(length(data)) FROM note_chunks").use { it.moveToFirst();it.getLong(0) }
            assertTrue("Shifted insertions duplicated body: $payload",payload-base<10_000)
            assertTrue(store.verify(id,note.contentHash!!,note.byteLength))
        }
    }
    @Test fun previewBoundaryNeverSplitsEmojiOrDamagesDurableHistoryJson() = fixture { context,db,store,id ->
        runBlocking {
            val persistence=NotePersistence(context,db,store)
            val text="a".repeat(199)+"🙂 اردو"
            val original=persistence.save(SavedItem(id=id,type=com.example.data.model.SavedItemType.TEXT,title="Unicode preview",content=text))
            assertEquals("a".repeat(199),original.preview)
            val next=persistence.save(original.copy(content=text+" new",bodyLoaded=true))
            val json=NoteHistoryPersistence.read(db,id)!!
            assertEquals(1,org.json.JSONObject(json).getInt("version"))
            val change=NoteGenerationHistory.forNote(id).undoChange!!
            persistence.restoreGeneration(id,next.contentHash!!,change.before,historyDirection=-1)
            assertTrue(store.verify(id,original.contentHash!!,original.byteLength))
        }
    }
    @Test fun staleMetadataAndAConcurrentTitleUpdateCannotBeOverwrittenByBodySave() = fixture { context,db,store,id ->
        runBlocking {
            val persistence=NotePersistence(context,db,store)
            val original=persistence.save(SavedItem(id=id,type=com.example.data.model.SavedItemType.TEXT,title="Original title",content="saved writing"))
            val renamed=persistence.save(original.copy(title="New title",folders=listOf("Moved"),bodyLoaded=false))
            assertTrue(renamed.updatedAt>original.updatedAt)
            assertTrue(runCatching { persistence.save(original.copy(content="stale writing",bodyLoaded=true)) }.isFailure)
            assertEquals("New title",persistence.load(id,false)!!.title)
            assertEquals(listOf("Moved"),persistence.load(id,false)!!.folders)
            var changed=false
            val racing=PrivateMarkdownStore(context,fileOperations=HostTestNoteFileOperations,database=db,checkpoint={ stage ->
                if(stage==PrivateMarkdownStore.WriteStage.DATA_SYNCED && !changed) {
                    changed=true
                    runBlocking { db.savedItemDao().updateTitleAndDescription(id,"Concurrent title",null,renamed.updatedAt+10) }
                }
            })
            assertTrue(runCatching { NotePersistence(context,db,racing).save(renamed.copy(content="draft body",bodyLoaded=true)) }.isFailure)
            val retained=persistence.load(id,false)!!
            assertEquals("Concurrent title",retained.title)
            assertEquals(original.contentHash,retained.contentHash)
            assertTrue(store.verify(id,retained.contentHash!!,retained.byteLength))
            assertTrue(store.maintenanceFiles().any { it.name.endsWith(".stage") })
        }
    }
    @Test fun nativeCheckpointReusesPreviouslyTypedPrefixesAndCompactUndoDescriptors() = fixture { _,db,store,id ->
        val original="original 🙂 اردو\n"+"x".repeat(100_000)
        val base=store.writeAtomic(id,original.byteInputStream());store.committed(id,base.hash)
        val token=UUID.randomUUID().toString()
        store.openLiveEditor(id,token,base).use { model ->
            repeat(200) { i ->
                model.replace(model.status.characters,model.status.characters,"batch-$i 🙂\n")
                val ref=model.openUtf8().use { store.writePieces(id,it,model.sharedPieces()) }
                store.committed(id,ref.hash);model.acceptCheckpoint(ref)
            }
            val payload=db.openHelper.writableDatabase.query("SELECT SUM(length(data)) FROM note_chunks").use { it.moveToFirst();it.getLong(0) }
            assertTrue("Typed prefixes duplicated: $payload",payload<110_000)
            assertTrue(model.undo());assertTrue(model.redo())
        }
    }
    @Test fun garbageCollectionKeepsSharedReadersAndUncommittedRecoveryRoots() = fixture { _,db,store,id ->
        val original=store.writeAtomic(id,"old body".byteInputStream());store.committed(id,original.hash)
        val staged=store.writeAtomic(id,"unsaved body".byteInputStream())
        store.openRangeReader(id,original.hash).use { reader ->
            val entry=store.maintenanceFiles().first { it.hash==original.hash }
            assertFalse(store.removeMaintenanceFile(entry))
            store.collectSharedStorage();assertArrayEquals("old body".toByteArray(),reader.read(0,8))
        }
        assertTrue(store.maintenanceFiles().any { it.name.endsWith(".stage") })
        store.collectSharedStorage();assertTrue(store.verify(id,staged.hash,staged.bytes))
        assertEquals("ok",db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { it.moveToFirst();it.getString(0) })
    }
    @Test fun shortSavedUndoRedoIsDurableAndPartOfTheHeadTransaction() = fixture { context,db,store,id ->
        runBlocking {
            val persistence=NotePersistence(context,db,store)
            val first=persistence.save(SavedItem(type=com.example.data.model.SavedItemType.TEXT,title="Shared fixture",id=id,content="one",bodyLoaded=true))
            val second=persistence.save(first.copy(content="two",bodyLoaded=true))
            val history=NoteGenerationHistory.forNote(id)
            val change=requireNotNull(history.undoChange)
            history.clear();NoteHistoryPersistence.publish(db,id)
            assertEquals(change,history.undoChange)
            val undone=persistence.restoreGeneration(id,second.contentHash!!,change.before,historyDirection=-1)
            assertEquals(first.contentHash,undone.contentHash)
            history.clear();NoteHistoryPersistence.publish(db,id);assertEquals(change,history.redoChange)
            val redone=persistence.restoreGeneration(id,undone.contentHash!!,change.after,historyDirection=1)
            assertEquals(second.contentHash,redone.contentHash)
        }
    }
    @Test fun corruptSharedPayloadIsDetectedAndNeverPromoted() = fixture { context,db,store,id ->
        runBlocking {
            val persistence=NotePersistence(context,db,store)
            val first=persistence.save(SavedItem(type=com.example.data.model.SavedItemType.TEXT,title="Shared fixture",id=id,content="original",bodyLoaded=true))
            val second=persistence.save(first.copy(content="second",bodyLoaded=true))
            val change=requireNotNull(NoteGenerationHistory.forNote(id).undoChange)
            db.openHelper.writableDatabase.execSQL("UPDATE note_chunks SET data=X'636F7272757074' WHERE data=X'6F726967696E616C'")
            try { persistence.restoreGeneration(id,second.contentHash!!,change.before,historyDirection=-1);fail("Corruption ignored") }
            catch(_:Exception) { }
            assertEquals(second.contentHash,db.savedItemDao().getItemById(id)!!.contentHash)
            assertTrue(store.verify(id,second.contentHash!!,second.byteLength))
        }
    }
}

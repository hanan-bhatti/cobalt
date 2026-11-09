package com.example.notes

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DisposableStorageCleanupTest {
    @Test fun confirmedCleanupKeepsSavedNotesAndUnsavedWritingWhileRemovingReviewedHistory() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val token=UUID.randomUUID().toString()
        val root=File(app.cacheDir,"cleanup-$token").apply { mkdirs() }
        val preferences=mutableSetOf<String>()
        val context=object: ContextWrapper(app) {
            override fun getApplicationContext(): Context=this
            override fun getApplicationInfo()=ApplicationInfo(app.applicationInfo).apply { dataDir=root.path }
            override fun getFilesDir()=File(root,"files").apply { mkdirs() }
            override fun getCacheDir()=File(root,"cache").apply { mkdirs() }
            override fun getDatabasePath(name: String)=File(root,"databases/$name").apply { parentFile!!.mkdirs() }
            override fun getSharedPreferences(name: String,mode: Int): SharedPreferences {
                preferences.add("$token-$name");return app.getSharedPreferences("$token-$name",mode)
            }
        }
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val store=PrivateMarkdownStore(context,fileOperations=HostTestNoteFileOperations,database=db)
        val persistence=NotePersistence(context,db,store)
        val manager=NoteStorageManager(context,db,store)
        val ids=mutableListOf<String>()
        try {
            val original=persistence.save(SavedItem(type=com.example.data.model.SavedItemType.TEXT,title="Cleanup fixture",content="original",bodyLoaded=true)).also { ids.add(it.id) }
            var current=persistence.save(original.copy(content="current 🙂",bodyLoaded=true))
            val draftNote=persistence.save(SavedItem(type=com.example.data.model.SavedItemType.TEXT,title="Cleanup fixture",content="draft base",bodyLoaded=true)).also { ids.add(it.id) }
            val draftId="capture-window:${draftNote.id}";ids.add(draftId)
            val draft=store.writeAtomic(draftId,"unsaved writing اردو".byteInputStream())
            context.getSharedPreferences("capture_file_sessions",0).edit().putString(draftNote.id,
                JSONObject().put("hash",draft.hash).put("bytes",draft.bytes).toString()).commit()
            context.getSharedPreferences("user-setting",0).edit().putBoolean("preserve",true).commit()
            val exports=File(context.cacheDir,"note_exports").apply { mkdirs() }
            val old=File(exports,"old.md").apply { writeText("temporary export");setLastModified(1) }
            val unknown=File(context.cacheDir,"recording.mp4").apply { writeText("unfinished recording") }
            val trash=TrashManager(context,db,store,scheduleWork=false,fileOperations=HostTestNoteFileOperations)
            val trashOriginal=persistence.save(SavedItem(type=com.example.data.model.SavedItemType.TEXT,title="Trash fixture",content="trash original",bodyLoaded=true)).also { ids.add(it.id) }
            val trashed=persistence.save(trashOriginal.copy(content="trash current",bodyLoaded=true))
            assertTrue(trash.delete(trashed.id))
            val staleReview=manager.reviewDisposable()
            current=persistence.save(current.copy(content="new current 🙂",bodyLoaded=true))
            assertTrue(runCatching { manager.clearDisposable(staleReview) }.isFailure)
            assertTrue(old.exists())
            assertNotNull(store.size(original.id,original.contentHash!!))
            val review=manager.reviewDisposable()
            assertTrue(review.entries.any { it.hash==original.contentHash })
            val duplicate=File(context.filesDir,"notes/${PrivateMarkdownStore.storageKey(current.id)}/${current.contentHash}.md")
            duplicate.writeBytes(ByteArray(current.byteLength.toInt()) { '!'.code.toByte() })
            val duplicateReview=manager.reviewDisposable()
            assertFalse(duplicateReview.entries.any { it.hash==current.contentHash })
            // Corrupt a current payload, confirm cleanup refuses before removing anything, then repair the fixture.
            val sqlite=db.openHelper.writableDatabase
            val chunk=sqlite.query("SELECT id,data FROM note_chunks ORDER BY id DESC LIMIT 1").use { it.moveToFirst();it.getLong(0) to it.getBlob(1) }
            sqlite.execSQL("UPDATE note_chunks SET data=X'00' WHERE id=?",arrayOf(chunk.first))
            assertTrue(runCatching { manager.clearDisposable(review) }.isFailure)
            assertTrue(old.exists());assertNotNull(store.size(original.id,original.contentHash!!))
            sqlite.execSQL("UPDATE note_chunks SET data=? WHERE id=?",arrayOf(chunk.second,chunk.first))
            val result=manager.clearDisposable(duplicateReview)
            assertTrue(result.files>0)
            assertNull(store.size(original.id,original.contentHash!!))
            assertTrue(store.verify(current.id,current.contentHash!!,current.byteLength))
            assertTrue(store.verify(draftId,draft.hash,draft.bytes))
            assertTrue(store.verify(trashed.id,trashed.contentHash!!,trashed.byteLength))
            assertTrue(store.verify(trashOriginal.id,trashOriginal.contentHash!!,trashOriginal.byteLength))
            trash.restore(trashed.id)
            assertEquals(trashed.contentHash,db.savedItemDao().getItemById(trashed.id)!!.contentHash)
            assertTrue(context.getSharedPreferences("capture_file_sessions",0).contains(draftNote.id))
            assertTrue(context.getSharedPreferences("user-setting",0).getBoolean("preserve",false))
            assertFalse(old.exists());assertTrue(unknown.exists())
            assertEquals(current.contentHash,db.savedItemDao().getItemById(current.id)!!.contentHash)
        } finally { ids.forEach(store::deleteAll);db.close();preferences.forEach { app.getSharedPreferences(it,0).edit().clear().commit() };root.deleteRecursively() }
    }
}

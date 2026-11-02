package com.example.notes

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Durable replacement operations; stale operations never overwrite a newer saved generation. */
class SectionDraftStore(context: Context, private val persistenceFactory: (Context) -> NotePersistence = { NotePersistence(it) }) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("note_section_drafts", Context.MODE_PRIVATE)
    private val store = PrivateMarkdownStore(this.context)
    data class Entry(val expectedHash: String, val start: Long, val end: Long, val hash: String, val bytes: Long)
    private fun key(id: String) = "section-draft:$id"

    private fun reference(id: String): Entry? = prefs.getString(id, null)?.let {
        val json = JSONObject(it)
        Entry(json.getString("expected"), json.getLong("start"), json.getLong("end"), json.getString("hash"), json.getLong("bytes"))
    }

    suspend fun load(id: String): Entry? = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            reference(id)?.also { check(store.size(key(id), it.hash) == it.bytes) { "Draft body missing; reference retained" } }
        }
    }

    suspend fun save(id: String, expectedHash: String, start: Long, end: Long, text: String): Entry =
        withContext(Dispatchers.IO) {
            val cancellation = currentCoroutineContext()
            NoteWriteLock.mutex.withLock {
                require(start >= 0 && end >= start)
                val old = reference(id)
                val ref = TextUtf8InputStream(text).use { input ->
                    store.writeAtomic(key(id), input) { cancellation.ensureActive() }
                }
                cancellation.ensureActive()
                val json = JSONObject().put("expected", expectedHash).put("start", start).put("end", end)
                    .put("hash", ref.hash).put("bytes", ref.bytes)
                check(prefs.edit().putString(id, json.toString()).commit()) { "Cannot commit section draft; original retained" }
                runCatching { store.committed(key(id), ref.hash) }
                old?.takeIf { it.hash != ref.hash }?.let { runCatching { store.delete(key(id), it.hash) } }
                Entry(expectedHash, start, end, ref.hash, ref.bytes)
            }
        }

    suspend fun apply(id: String, entry: Entry): com.example.data.model.SavedItem = withContext(Dispatchers.IO) {
        check(store.verify(key(id), entry.hash, entry.bytes)) { "Section draft verification failed" }
        val saved = persistenceFactory(context).replaceWindow(id, entry.expectedHash,
            EditableNoteWindow(entry.start, entry.end, ""), FileNoteByteSource(store, key(id), entry.hash, entry.bytes))
        // A cleanup failure must not misreport an already committed save as failed.
        runCatching { clear(id, entry) }
        saved
    }

    suspend fun clear(id: String, expected: Entry? = null) = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val current = reference(id)
            if (expected == null || current == expected) {
                check(prefs.edit().remove(id).commit()) { "Cannot clear draft acknowledgement" }
            }
            // Keep immutable bytes available for recovery; no automatic orphan deletion.
        }
    }

    /** Explicitly recover a conflicting patch as a new note instead of overwriting saved content. */
    suspend fun recoverAsNote(id: String, entry: Entry): com.example.data.model.SavedItem = withContext(Dispatchers.IO) {
        check(store.verify(key(id), entry.hash, entry.bytes)) { "Section draft unavailable" }
        val recoveryId = java.util.UUID.nameUUIDFromBytes((key(id) + entry.hash).toByteArray(Charsets.UTF_8)).toString()
        val saved = NoteWriteLock.mutex.withLock {
            store.open(key(id), entry.hash).use { input ->
                persistenceFactory(context).importLocked(com.example.data.model.SavedItem(id = recoveryId,
                    title = "Recovered section draft", type = com.example.data.model.SavedItemType.TEXT, content = ""), input,
                    allowOversize = true)
            }
        }
        clear(id, entry)
        saved
    }
}

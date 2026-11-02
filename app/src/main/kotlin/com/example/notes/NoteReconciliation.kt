package com.example.notes

import android.content.Context
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Metadata-only startup check. Interrupted copies are retained, never promoted speculatively. */
object NoteReconciliation {
    data class Result(val missing: Int, val interrupted: Int, val checked: Int)
    suspend fun run(context: Context): Result = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val dao = AppDatabase.getDatabase(context).savedItemDao()
            val store = PrivateMarkdownStore(context)
            var missing = 0
            var checked = 0
            for (note in dao.allNoteBodyMetadata()) {
                kotlin.coroutines.coroutineContext.ensureActive()
                if (note.bodyLocation != "FILE") continue
                checked++
                val available = runCatching { note.contentHash?.let { store.size(note.id, it) } == note.byteLength }.getOrDefault(false)
                dao.markBodyMissing(note.id, !available)
                if (!available) missing++
                else runCatching { store.committed(note.id, note.contentHash!!) }
            }
            Result(missing, store.retainedInterruptedWrites(), checked).also {
                check(context.getSharedPreferences("note_reconciliation", Context.MODE_PRIVATE).edit()
                    .putInt("missing", it.missing).putInt("interrupted", it.interrupted).putInt("checked", it.checked)
                    .putLong("last_run", System.currentTimeMillis()).commit())
            }
        }
    }
}

package com.example.notes

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.TrashEntryEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Trash is metadata plus intact private files. Purge is resumable; restore never saves a preview. */
// KTX edit returns Unit; these crash-safety steps must check the synchronous commit result.
@android.annotation.SuppressLint("UseKtx")
class TrashManager(context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context.applicationContext),
    private val store: NoteBodyStore = PrivateMarkdownStore(context.applicationContext),
    private val now: () -> Long = System::currentTimeMillis,
    private val scheduleWork: Boolean = true,
    private val fileOperations: NoteFileOperations = AndroidNoteFileOperations) {
    private val context = context.applicationContext
    private val dao = database.savedItemDao()
    private val trash = database.trashDao()
    private val prefs = this.context.getSharedPreferences("note_storage_management", Context.MODE_PRIVATE)
    private val fence = NoteDeletionFence(this.context)
    fun enabled(): Boolean = prefs.getBoolean("trash_enabled", true)
    fun observe() = trash.observe()
    suspend fun setEnabled(value: Boolean) = withContext(Dispatchers.IO) {
        // Existing Trash keeps its original deadline. Changing the setting never empties it.
        check(prefs.edit().putBoolean("trash_enabled", value).commit())
    }
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val changed = NoteWriteLock.mutex.withLock {
            val item = dao.getItemById(id) ?: return@withLock false
            if (dao.isItemDeleted(id) == true) return@withLock false
            val time = now()
            val useTrash = enabled()
            val promoted = if (NotePersistence.isNote(item.toDomainModel())) emptyMap() else promoteCacheMedia(item)
            try {
                database.withTransaction {
                    if (promoted.isNotEmpty()) {
                        fun path(value: String?): String? = value?.let { if (it.startsWith('/')) promoted[File(it).canonicalPath]?.path ?: it else it }
                        check(dao.updateMediaPaths(id, path(item.content)!!, path(item.thumbnailPath)) == 1)
                    }
                    trash.insert(TrashEntryEntity(id, time, TrashPolicy.expiresAt(time, useTrash), !useTrash))
                    check(dao.setDeleted(id, true, time) == 1)
                }
            } catch (error: Throwable) {
                // If commit status is uncertain, leave verified copies rather than deleting live data.
                runCatching {
                    val used = dao.mediaStorageItems().flatMap { listOfNotNull(it.path, it.thumbnailPath) }.toSet()
                    promoted.values.filter { it.path !in used }.forEach { it.delete() }
                }
                throw error
            }
            if (promoted.isNotEmpty()) {
                val otherPaths = dao.otherMediaPaths(id).flatMap { listOfNotNull(it.path, it.thumbnailPath) }
                    .filter { it.startsWith('/') }.map { File(it).canonicalPath }.toSet()
                promoted.keys.filter { it !in otherPaths }.forEach { File(it).delete() }
            }
            true
        }
        if (changed) { scheduleNext(); updateWidgets() }
        changed
    }
    suspend fun restore(id: String) = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val entry = trash.entry(id) ?: error("Undo has expired or this item was permanently deleted.")
            check(!entry.purgeStarted) { "Permanent deletion has started; this item cannot be restored." }
            check(!entry.undoOnly || now() < entry.expiresAt) { "Undo has expired." }
            val item = dao.getItemById(id) ?: error("Item metadata is missing; retained files were not removed.")
            val prefix = if (NotePersistence.isNote(item.toDomainModel()) && dao.noteSearchCount(id) == 0) runCatching {
                val source = if (item.bodyLocation == "FILE") store.open(id, requireNotNull(item.contentHash)) else LegacyBodyInputStream(dao, id)
                source.use { input ->
                    val output = java.io.ByteArrayOutputStream(NoteSizePolicy.SEARCH_PREFIX_BYTES)
                    val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                    while (output.size() < NoteSizePolicy.SEARCH_PREFIX_BYTES) {
                        val count = input.read(buffer, 0, minOf(buffer.size, NoteSizePolicy.SEARCH_PREFIX_BYTES - output.size()))
                        if (count < 0) break
                        if (count > 0) output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8").replace("\u0000", " ")
                }
            }.getOrNull() else null
            database.withTransaction {
                check(dao.setDeleted(id, false, now()) == 1)
                trash.remove(id)
                if (prefix != null) dao.insertNoteSearch(com.example.data.local.NoteSearchEntity(id, item.title, prefix))
            }
            // Search entries stayed intact; active-query filters hide them while in Trash.
        }
        updateWidgets()
    }
    suspend fun deleteForever(id: String, expectedDeletedAt: Long? = null) = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            if (expectedDeletedAt != null) check(trash.entry(id)?.deletedAt == expectedDeletedAt) { "Trash changed; refresh before deleting" }
            purgeLocked(id)
        }
        updateWidgets()
    }
    suspend fun empty(reviewed: Map<String, Long>? = null): Int = withContext(Dispatchers.IO) {
        // Snapshot IDs: items trashed after confirmation are not included in this action.
        val ids = NoteWriteLock.mutex.withLock { reviewed?.keys?.toList() ?: trash.ids() }
        var removed = 0
        for (id in ids) {
            ensureActive()
            NoteWriteLock.mutex.withLock {
                val entry = trash.entry(id)
                if (entry != null && (reviewed == null || reviewed[id] == entry.deletedAt)) { purgeLocked(id); removed++ }
            }
        }
        updateWidgets(); removed
    }
    suspend fun expire(): Int = withContext(Dispatchers.IO) {
        var removed = 0
        while (true) {
            val batch = trash.due(now())
            if (batch.isEmpty()) break
            for (entry in batch) {
                ensureActive()
                NoteWriteLock.mutex.withLock {
                    val current = trash.entry(entry.itemId)
                    if (current != null && (current.purgeStarted || current.expiresAt <= now())) {
                        purgeLocked(entry.itemId); removed++
                    }
                }
            }
        }
        if (removed > 0) updateWidgets()
        removed
    }
    suspend fun ownedSizes(): Map<String, Long> = withContext(Dispatchers.IO) {
        val files = store.maintenanceFiles().groupBy { it.directoryKey }
        val media = dao.mediaStorageItems().associateBy { it.id }
        val mediaRoot = File(context.filesDir, "media").canonicalPath + File.separator
        val activePaths = media.values.filter { !it.isDeleted }.flatMap { listOfNotNull(it.path, it.thumbnailPath) }
            .filter { it.startsWith('/') }.map { File(it).canonicalPath }.toSet()
        val root = File(mediaRoot)
        val privateFiles = if (root.isDirectory) root.walkTopDown().filter { it.isFile }.toList() else emptyList()
        trash.ids().associateWith { id ->
            val noteBytes = listOf(id, "capture-window:$id", "section-draft:$id").sumOf { identity ->
                files[PrivateMarkdownStore.storageKey(identity)].orEmpty().sumOf { it.bytes }
            }
            val row = media[id]
            val owned = listOfNotNull(row?.path, row?.thumbnailPath).filter { it.startsWith('/') }
                .map { File(it).canonicalFile }.filter { it.path.startsWith(mediaRoot) && it.isFile }.toMutableSet()
            privateFiles.forEach { file ->
                val name = file.name
                if (name.startsWith("$id.") || name.startsWith("${id}_") || name.startsWith("thumb_$id.") || name.startsWith("thumb_${id}_")) {
                    val canonical = file.canonicalFile
                    if (canonical.path.startsWith(mediaRoot) && canonical.path !in activePaths) owned.add(canonical)
                }
            }
            noteBytes + owned.sumOf { it.length() }
        }
    }
    private suspend fun purgeLocked(id: String) {
        val entry = trash.entry(id) ?: return
        val item = dao.getItemById(id)
        check(item == null || item.isDeleted) { "Item has been restored; refresh Trash before deleting." }
        // Commit intent first. A crash or disk error leaves a retryable record, never a restorable half-body.
        if (!entry.purgeStarted) trash.beginPurge(id)
        val identities = listOf(id, "capture-window:$id", "section-draft:$id")
        fence.mark(identities)
        identities.forEach { store.deleteAll(it) }
        for (name in listOf("capture_file_sessions", "note_section_drafts")) {
            check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().remove(id).commit())
        }
        val quick = context.getSharedPreferences("quick_note_reference", Context.MODE_PRIVATE)
        if (quick.getString("note_id", null) == id) {
            check(quick.edit().clear().commit())
            store.deleteAll("quick-note-draft")
        }
        if (item != null && !NotePersistence.isNote(item.toDomainModel())) {
            val root = File(context.filesDir, "media").canonicalFile
            val cacheRoot = context.cacheDir.canonicalPath + File.separator
            val others = dao.otherMediaPaths(id).flatMap { listOfNotNull(it.path, it.thumbnailPath) }
                .filter { it.startsWith('/') }.map { File(it).canonicalPath }.toSet()
            for (path in listOfNotNull(item.content, item.thumbnailPath).distinct()) {
                if (!path.startsWith('/')) continue
                val file = File(path).canonicalFile
                if (file.path in others) continue
                // Shared/SAF originals and arbitrary paths are never deleted.
                if ((file.path.startsWith(root.path + File.separator) || file.path.startsWith(cacheRoot)) && file.isFile) {
                    check(file.delete()) { "A private media file could not be removed; deletion will retry." }
                }
            }
            if (root.isDirectory) root.walkTopDown().filter { it.isFile }.forEach { file ->
                val namedOwner = file.name.startsWith("$id.") || file.name.startsWith("${id}_") ||
                    file.name.startsWith("thumb_$id.") || file.name.startsWith("thumb_${id}_")
                val canonical = file.canonicalFile
                if (namedOwner && canonical.path.startsWith(root.path + File.separator) && canonical.path !in others) {
                    check(file.delete()) { "An owned media copy could not be removed; deletion will retry." }
                }
            }
        }
        database.withTransaction {
            dao.deleteNoteSearch(id)
            dao.deleteById(id)
            trash.remove(id)
        }
    }
    private fun promoteCacheMedia(item: com.example.data.local.SavedItemEntity): Map<String, File> {
        val root = context.cacheDir.canonicalPath + File.separator
        val sources = listOfNotNull(item.content, item.thumbnailPath).filter { it.startsWith('/') }
            .map { File(it).canonicalFile }.filter { it.isFile && it.path.startsWith(root) }.distinctBy { it.path }
        if (sources.isEmpty()) return emptyMap()
        val directory = File(context.filesDir, "media/recovered")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot preserve cached media for Trash" }
        fileOperations.syncDirectory(context.filesDir)
        fileOperations.syncDirectory(directory.parentFile!!)
        val copies = LinkedHashMap<String, File>()
        try {
            for (source in sources) {
                val extension = source.extension.filter(Char::isLetterOrDigit).take(8).ifBlank { "bin" }
                val target = File(directory, "${java.util.UUID.randomUUID()}.$extension")
                val temp = File(directory, target.name + ".tmp")
                try {
                    val hash = source.inputStream().use { PrivateMarkdownStore.digest(it) }
                    java.io.FileOutputStream(temp).use { output ->
                        source.inputStream().use { it.copyTo(output, NoteSizePolicy.IO_CHUNK_BYTES) }
                        output.fd.sync()
                    }
                    check(temp.length() == source.length() && temp.inputStream().use { PrivateMarkdownStore.digest(it) } == hash) { "Cached media changed; original retained" }
                    fileOperations.renameAtomic(temp, target)
                    copies[source.path] = target
                    fileOperations.syncDirectory(directory)
                } finally { temp.delete() }
            }
            return copies
        } catch (error: Throwable) { copies.values.forEach { it.delete() }; throw error }
    }
    private suspend fun scheduleNext() {
        if (scheduleWork) trash.nextExpiry()?.let { expiry ->
            runCatching { TrashCleanupWorker.scheduleSoon(context, (expiry - now()).coerceAtLeast(0)) }
        }
    }
    private fun updateWidgets() { runCatching { com.example.widget.WidgetUpdater.update(context) } }
}

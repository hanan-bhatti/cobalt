package com.example.notes

import android.content.Context
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Explicit maintenance only. No startup pruning and no deletion of unknown or interrupted writes. */
class NoteStorageManager(context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context.applicationContext),
    private val store: NoteBodyStore = PrivateMarkdownStore(context.applicationContext)) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("note_storage_management", Context.MODE_PRIVATE)
    data class Report(val current: Long, val older: Long, val recovery: Long, val database: Long,
        val cache: Long, val media: Long, val removable: Long, val removableFiles: Int, val copiesToKeep: Int)
    data class CleanupResult(val bytes: Long, val files: Int)
    data class OlderCopy(val note: com.example.data.model.SavedItem, val hash: String, val bytes: Long, val created: Long)

    suspend fun olderCopies(): List<OlderCopy> = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val metadata = database.savedItemDao().allNoteBodyMetadata()
                .filter { it.bodyLocation == "FILE" && it.contentHash != null }
                .associateBy { PrivateMarkdownStore.storageKey(it.id) }
            val eligible = store.maintenanceFiles().filter { entry ->
                val note = metadata[entry.directoryKey]
                note != null && entry.hash != null && entry.hash != note.contentHash
            }.sortedByDescending { it.modified }.take(200)
            val summaries = HashMap<String, com.example.data.model.SavedItem>()
            eligible.mapNotNull { entry ->
                val id = metadata.getValue(entry.directoryKey).id
                val note = summaries[id] ?: database.savedItemDao().getItemById(id)?.toDomainModel()?.also { summaries[id] = it }
                note?.takeUnless { it.isDeleted }?.let { OlderCopy(it, entry.hash!!, entry.bytes, entry.modified) }
            }
        }
    }

    suspend fun restore(copy: OlderCopy) = withContext(Dispatchers.IO) {
        val preview = store.readRange(copy.note.id, copy.hash, 0, 4096).toString(Charsets.UTF_8)
            .replace(Regex("[\\r\\n\\t]+"), " ").take(NoteSizePolicy.PREVIEW_CHARACTERS)
        NotePersistence(context, database, store).restoreGeneration(copy.note.id,
            copy.note.contentHash ?: error("The current body is unavailable"),
            NoteGeneration(copy.hash, copy.bytes, preview), requireNoDraft = true)
    }

    suspend fun export(copy: OlderCopy, destination: android.net.Uri) = withContext(Dispatchers.IO) {
        val cancellation = currentCoroutineContext()
        context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
            NoteExport.write(copy.note.copy(content = "", bodyLoaded = false, bodyLocation = "FILE",
                contentHash = copy.hash, byteLength = copy.bytes), store, output) { cancellation.ensureActive() }
        } ?: error("Export destination unavailable")
    }
    private data class Plan(val report: Report, val candidates: List<NoteStorageFile>)

    fun retention(): Int = prefs.getInt("copies_to_keep", 5).takeIf { it in RETENTIONS } ?: 5
    suspend fun setRetention(value: Int) = withContext(Dispatchers.IO) {
        require(value in RETENTIONS)
        check(prefs.edit().putInt("copies_to_keep", value).commit()) { "Could not save retention preference" }
    }
    suspend fun scan(): Report = withContext(Dispatchers.IO) { NoteWriteLock.mutex.withLock { plan().report } }

    /** Recalculate under the write lock. Never trust a stale UI estimate or a guessed directory. */
    suspend fun cleanup(reviewedMaximum: Long): CleanupResult = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val plan = plan(verifyCurrent = true)
            check(plan.report.removable <= reviewedMaximum) { "Storage changed. Review the updated cleanup estimate." }
            var bytes = 0L; var files = 0
            for (entry in plan.candidates) {
                currentCoroutineContext().ensureActive()
                if (store.removeMaintenanceFile(entry)) { bytes += entry.bytes; files++ }
            }
            CleanupResult(bytes, files)
        }
    }

    private suspend fun plan(verifyCurrent: Boolean = false): Plan {
        val notes = database.savedItemDao().allNoteBodyMetadata()
            .filter { it.bodyLocation == "FILE" && it.contentHash != null }
        val byDirectory = notes.associateBy { PrivateMarkdownStore.storageKey(it.id) }
        val protectedDirectories = HashSet<String>()
        // A closed editor's reference is still a recoverable draft, not abandoned data.
        context.getSharedPreferences("capture_file_sessions", Context.MODE_PRIVATE).all.forEach { (id, value) ->
            check(value is String) { "Unreadable editor recovery metadata; cleanup disabled" }
            JSONObject(value) // Malformed metadata fails closed, rather than authorizing deletion.
            protectedDirectories.add(PrivateMarkdownStore.storageKey(id))
            protectedDirectories.add(PrivateMarkdownStore.storageKey("capture-window:$id"))
        }
        context.getSharedPreferences("note_section_drafts", Context.MODE_PRIVATE).all.forEach { (id, value) ->
            check(value is String); JSONObject(value)
            protectedDirectories.add(PrivateMarkdownStore.storageKey(id))
            protectedDirectories.add(PrivateMarkdownStore.storageKey("section-draft:$id"))
        }
        protectedDirectories.add(PrivateMarkdownStore.storageKey("quick-note-draft"))
        val files = store.maintenanceFiles()
        // An unretired operation log or incomplete atomic write may depend on any generation here.
        files.filter { it.name.endsWith(".edits") || it.name.endsWith(".journal") || it.name.endsWith(".tmp") }
            .forEach { protectedDirectories.add(it.directoryKey) }
        val pinned = NoteGenerationHistory.protectedHashes()
        val keep = retention()
        val candidates = ArrayList<NoteStorageFile>()
        var current = 0L; var older = 0L; var recovery = 0L
        for ((key, entries) in files.groupBy { it.directoryKey }) {
            currentCoroutineContext().ensureActive()
            val note = byDirectory[key]
            val bodies = entries.filter { it.hash != null }
            val previous = bodies.filter { it.hash != note?.contentHash }
            if (note != null) {
                current += bodies.filter { it.hash == note.contentHash }.sumOf { it.bytes }
                older += previous.sumOf { it.bytes }
            } else recovery += bodies.sumOf { it.bytes }
            recovery += entries.filter { it.hash == null }.sumOf { it.bytes }
            if (keep < 0 || note == null || key in protectedDirectories) continue
            // Do not discard recovery options for a missing/corrupt current body.
            if (store.size(note.id, note.contentHash!!) != note.byteLength) continue
            if (verifyCurrent && !store.verify(note.id, note.contentHash, note.byteLength)) continue
            candidates += previous.sortedWith(compareByDescending<NoteStorageFile> { it.modified }.thenBy { it.name })
                .drop(keep).filter { it.hash !in pinned }
            candidates += entries.filter { it.isRetiredJournal }
        }
        fun size(root: File): Long {
            if (!root.exists()) return 0
            return root.walkTopDown().filter { it.isFile }.fold(0L) { total, file -> total + file.length() }
        }
        val dbBytes = size(File(context.applicationInfo.dataDir, "databases"))
        val mediaBytes = context.filesDir.listFiles().orEmpty().filter { it.name != "notes" }.sumOf { size(it) }
        return Plan(Report(current, older, recovery, dbBytes, size(context.cacheDir), mediaBytes,
            candidates.sumOf { it.bytes }, candidates.size, keep), candidates)
    }
    companion object { val RETENTIONS = listOf(0, 1, 5, 10, 20, -1) }
}

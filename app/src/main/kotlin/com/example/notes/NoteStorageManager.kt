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
import coil.imageLoader

/** Explicit maintenance only. No startup pruning and no deletion of unknown or interrupted writes. */
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class NoteStorageManager(context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context.applicationContext),
    private val store: NoteBodyStore = PrivateMarkdownStore(context.applicationContext)) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("note_storage_management", Context.MODE_PRIVATE)
    data class Report(val current: Long, val older: Long, val recovery: Long, val database: Long,
        val cache: Long, val media: Long, val removable: Long, val removableFiles: Int, val copiesToKeep: Int,
        val trash: Long = 0, val trashCount: Int = 0, val unattached: Long = 0, val unattachedFiles: Int = 0,
        val unlinkedRemovable: Long = 0, val clearableCache: Long = 0, val freeDatabase: Long = 0, val other: Long = 0) {
        val total: Long get() = current + older + recovery + database + cache + media + trash + unattached + other
    }
    data class CleanupResult(val bytes: Long, val files: Int)
    data class OlderCopy(val note: com.example.data.model.SavedItem, val hash: String, val bytes: Long, val created: Long)
    data class RetainedMedia(val relativePath: String, val bytes: Long, val modified: Long)
    data class UnlinkedReview(val notes: List<NoteStorageFile>, val media: List<RetainedMedia>) {
        val bytes get() = notes.sumOf { it.bytes } + media.sumOf { it.bytes }
        val count get() = notes.size + media.size
    }
    data class UnlinkedCopy(val entry: NoteStorageFile?, val title: String, val media: RetainedMedia? = null) {
        val bytes get() = entry?.bytes ?: media!!.bytes
        val key get() = entry?.let { it.directoryKey + it.name } ?: "media:" + media!!.relativePath
        val compressed get() = entry?.name?.endsWith(".delta") == true
        val extension get() = if (media == null) "md" else File(media.relativePath).extension.take(8).ifBlank { "bin" }
        val recoverable get() = media == null || extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "mkv", "webm", "mp3", "m4a", "wav", "ogg", "aac")
    }

    suspend fun olderCopies(): List<OlderCopy> = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val deleted = database.savedItemDao().deletedIds().toSet()
            val metadata = database.savedItemDao().allNoteBodyMetadata()
                .filter { it.id !in deleted && it.bodyLocation == "FILE" && it.contentHash != null }
                .associateBy { PrivateMarkdownStore.storageKey(it.id) }
            val eligible = store.maintenanceFiles().filter { entry ->
                val note = metadata[entry.directoryKey]
                note != null && entry.hash != null && entry.hash != note.contentHash
            }.sortedByDescending { it.modified }.take(200)
            val summaries = HashMap<String, com.example.data.model.SavedItem>()
            eligible.mapNotNull { entry ->
                val id = metadata.getValue(entry.directoryKey).id
                val note = summaries[id] ?: database.savedItemDao().getItemById(id)?.toDomainModel()?.also { summaries[id] = it }
                note?.takeUnless { it.isDeleted }?.let {
                    OlderCopy(it, entry.hash!!, store.size(id, entry.hash) ?: return@mapNotNull null, entry.modified)
                }
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
    private data class Plan(val report: Report, val candidates: List<NoteStorageFile>, val unlinked: List<NoteStorageFile>,
        val media: List<RetainedMedia>)
    private fun mediaFile(entry: RetainedMedia): File {
        val root = File(context.filesDir, "media").canonicalFile
        val file = File(root, entry.relativePath).canonicalFile
        check(file.path.startsWith(root.path + File.separator) && file.isFile && file.length() == entry.bytes && file.lastModified() == entry.modified) { "Media file changed; refresh Storage" }
        return file
    }

    suspend fun unlinkedCopies(): List<UnlinkedCopy> = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val plan = plan()
            val notes = plan.unlinked.filter { it.hash != null }.sortedByDescending { it.bytes }.take(200).map { entry ->
                val title = runCatching {
                    store.openMaintenance(entry).use { source ->
                        val buffer = ByteArray(512)
                        val count = source.read(buffer)
                        if (count <= 0) "" else String(buffer, 0, count, Charsets.UTF_8)
                            .lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().trimStart('#', ' ').take(100)
                    }
                }.getOrDefault("")
                UnlinkedCopy(entry, title.ifBlank { "Unlinked Markdown note" })
            }
            val media = plan.media.sortedByDescending { it.bytes }.take(200).map { entry ->
                val stem = File(entry.relativePath).nameWithoutExtension.replace(Regex("^(?:thumb_)?[0-9a-f-]{36}[_-]?"), "").trim('_', '-', ' ')
                UnlinkedCopy(null, stem.take(100).ifBlank { "Retained media file" }, entry)
            }
            (notes + media).sortedByDescending { it.bytes }.take(200)
        }
    }
    suspend fun exportUnlinked(copy: UnlinkedCopy, destination: android.net.Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
            (copy.entry?.let(store::openMaintenance) ?: mediaFile(copy.media!!).inputStream()).use { input ->
                val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("Export destination unavailable")
    }
    suspend fun removeUnlinked(copy: UnlinkedCopy): Boolean = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val current = plan()
            if (copy.media != null) {
                check(copy.media in current.media) { "Storage changed; refresh this page" }
                check(mediaFile(copy.media).delete()) { "Media file could not be removed" }
                true
            } else {
                check(copy.entry in current.unlinked) { "Storage changed; refresh this page" }
                store.removeMaintenanceFile(copy.entry!!)
            }
        }
    }
    suspend fun recoverUnlinked(copy: UnlinkedCopy): Boolean = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val current = plan()
            if (copy.media != null) {
                check(copy.media in current.media && copy.recoverable) { "Media is not ready for recovery" }
                val file = mediaFile(copy.media)
                val type = if (copy.media.relativePath.startsWith("audio/")) com.example.data.model.SavedItemType.AUDIO else when (copy.extension.lowercase()) {
                    "jpg", "jpeg", "png", "webp", "gif" -> com.example.data.model.SavedItemType.IMAGE
                    "mp4", "mkv", "webm" -> com.example.data.model.SavedItemType.VIDEO
                    else -> com.example.data.model.SavedItemType.AUDIO
                }
                val audio = type == com.example.data.model.SavedItemType.AUDIO
                NotePersistence(context, database, store).saveNonNoteLocked(com.example.data.model.SavedItem(
                    type = type, title = copy.title, content = if (audio) "" else file.path,
                    thumbnailPath = if (audio) file.path else null, sizeBytes = file.length(), timestamp = file.lastModified()))
                return@withLock true
            }
            val entry = requireNotNull(copy.entry)
            check(entry in current.unlinked) { "Storage changed; refresh before recovery" }
            val stableId = java.util.UUID.nameUUIDFromBytes(("retained:" + entry.directoryKey + entry.name).toByteArray(Charsets.UTF_8)).toString()
            val id = if (NoteDeletionFence(context).deleted(stableId)) java.util.UUID.randomUUID().toString() else stableId
            val previous = database.savedItemDao().getItemById(id)
            check(previous?.isDeleted != true) { "This note is already recovered in Trash. Restore it there." }
            val saved = store.openMaintenance(entry).use { input ->
                NotePersistence(context, database, store).importLocked(
                    com.example.data.model.SavedItem(id = id, title = copy.title, content = "", type = com.example.data.model.SavedItemType.TEXT,
                        timestamp = entry.modified.coerceAtLeast(0)), input, allowOversize = true)
            }
            check(store.verify(id, saved.contentHash!!, saved.byteLength)) { "Recovered note verification failed; source retained" }
            // The recovered, committed copy owns the bytes before the redundant source may be unlinked.
            store.removeMaintenanceFile(entry)
        }
    }
    suspend fun reviewUnlinked(): UnlinkedReview = withContext(Dispatchers.IO) { NoteWriteLock.mutex.withLock { plan().let { UnlinkedReview(it.unlinked, it.media) } } }
    suspend fun reviewHistory(): List<NoteStorageFile> = withContext(Dispatchers.IO) { NoteWriteLock.mutex.withLock { plan().candidates } }
    suspend fun cleanupUnlinked(reviewedMaximum: Long, reviewedEntries: UnlinkedReview? = null): CleanupResult = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val plan = plan()
            val candidates = plan.unlinked.filter { reviewedEntries == null || it in reviewedEntries.notes }
            val media = plan.media.filter { reviewedEntries == null || it in reviewedEntries.media }
            check(candidates.sumOf { it.bytes } + media.sumOf { it.bytes } <= reviewedMaximum) { "Storage changed; review cleanup again" }
            var bytes = 0L; var count = 0
            for (entry in candidates.sortedBy { if (it.name.endsWith(".delta")) 0 else 1 }) {
                currentCoroutineContext().ensureActive()
                if (store.removeMaintenanceFile(entry)) { bytes += entry.bytes; count++ }
            }
            for (entry in media) {
                currentCoroutineContext().ensureActive()
                if (mediaFile(entry).delete()) { bytes += entry.bytes; count++ }
            }
            CleanupResult(bytes, count)
        }
    }
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        // Clear only known rebuildable previews and idle scratch. Pending captures/recordings/shares stay intact.
        val protected = ownedCachePaths()
        val cache = context.imageLoader.diskCache
        val previewRoot = cache?.directory?.toString()?.let { File(it).canonicalPath + File.separator }
        if (previewRoot == null || protected.none { it.startsWith(previewRoot) }) cache?.clear()
        context.imageLoader.memoryCache?.clear()
        val work = File(context.cacheDir, "note-body-work")
        if (protected.none { it.startsWith(work.canonicalPath + File.separator) }) NoteCacheWork.clear(work)
        oldExports(protected).forEach { check(it.delete()) { "An old temporary export could not be removed" } }
    }
    private suspend fun ownedCachePaths(): Set<String> {
        val root = context.cacheDir.canonicalPath + File.separator
        return database.savedItemDao().mediaStorageItems().flatMap { listOfNotNull(it.path, it.thumbnailPath) }
            .filter { it.startsWith('/') }.map { File(it).canonicalPath }.filter { it.startsWith(root) }.toSet()
    }
    private fun oldExports(protected: Set<String>): List<File> {
        val before = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return listOf("note_exports", "shares", "feedback_export", "crash_reports").flatMap { name ->
            val root = File(context.cacheDir, name)
            if (!root.isDirectory) emptyList() else root.walkTopDown().filter {
                it.isFile && it.lastModified() < before && it.canonicalPath !in protected
            }.toList()
        }
    }
    suspend fun optimizeDatabase() = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val db = database.openHelper.writableDatabase
            val main = context.getDatabasePath("cobalt_db")
            check(context.filesDir.usableSpace >= main.length() * 2) { "More free space is needed to optimize the database" }
            db.execSQL("VACUUM")
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }
    }

    fun retention(): Int = prefs.getInt("copies_to_keep", 5).takeIf { it in RETENTIONS } ?: 5
    suspend fun setRetention(value: Int) = withContext(Dispatchers.IO) {
        require(value in RETENTIONS)
        check(prefs.edit().putInt("copies_to_keep", value).commit()) { "Could not save retention preference" }
    }
    suspend fun scan(): Report = withContext(Dispatchers.IO) { NoteWriteLock.mutex.withLock { plan().report } }

    /** Recalculate under the write lock. Never trust a stale UI estimate or a guessed directory. */
    suspend fun cleanup(reviewedMaximum: Long, includeUnlinked: Boolean = false, reviewedEntries: List<NoteStorageFile>? = null): CleanupResult = withContext(Dispatchers.IO) {
        NoteWriteLock.mutex.withLock {
            val plan = plan(verifyCurrent = true)
            val selected = (plan.candidates + if (includeUnlinked) plan.unlinked else emptyList())
                .filter { reviewedEntries == null || it in reviewedEntries }
            check(selected.sumOf { it.bytes } <= reviewedMaximum) { "Storage changed. Review the updated cleanup estimate." }
            var bytes = 0L; var files = 0
            for (entry in selected.sortedBy { if (it.name.endsWith(".delta")) 0 else 1 }) {
                currentCoroutineContext().ensureActive()
                if (store.removeMaintenanceFile(entry)) { bytes += entry.bytes; files++ }
            }
            CleanupResult(bytes, files)
        }
    }

    private suspend fun plan(verifyCurrent: Boolean = false): Plan {
        val cancellation = currentCoroutineContext()
        val dao = database.savedItemDao()
        val deletedIds = (dao.deletedIds() + database.trashDao().ids()).toSet()
        val trashedDirectories = deletedIds.flatMap { id -> listOf(id, "capture-window:$id", "section-draft:$id") }
            .map(PrivateMarkdownStore::storageKey).toSet()
        val notes = dao.allNoteBodyMetadata().filter { it.id !in deletedIds && it.bodyLocation == "FILE" && it.contentHash != null }
        val byDirectory = notes.associateBy { PrivateMarkdownStore.storageKey(it.id) }
        val protectedDirectories = HashSet<String>()
        for ((preference, prefix) in listOf("capture_file_sessions" to "capture-window:", "note_section_drafts" to "section-draft:")) {
            context.getSharedPreferences(preference, Context.MODE_PRIVATE).all.forEach { (id, value) ->
                check(value is String) { "Unreadable editor recovery metadata; cleanup disabled" }
                JSONObject(value)
                protectedDirectories.add(PrivateMarkdownStore.storageKey(id))
                protectedDirectories.add(PrivateMarkdownStore.storageKey(prefix + id))
            }
        }
        if (context.getSharedPreferences("quick_note_reference", Context.MODE_PRIVATE).contains("hash") ||
            context.getSharedPreferences("draft_prefs", Context.MODE_PRIVATE).contains("quick_note")) {
            protectedDirectories.add(PrivateMarkdownStore.storageKey("quick-note-draft"))
        }
        val files = store.maintenanceFiles()
        files.filter { it.name.endsWith(".edits") || it.name.endsWith(".journal") || it.name.endsWith(".tmp") }
            .forEach { protectedDirectories.add(it.directoryKey) }
        val pinned = NoteGenerationHistory.protectedHashes()
        val links = store.historyLinks()
        val keep = retention()
        val candidates = ArrayList<NoteStorageFile>()
        val unlinked = ArrayList<NoteStorageFile>()
        var current = 0L; var older = 0L; var recovery = 0L; var trashed = 0L; var unattached = 0L; var unattachedCount = 0
        fun eligible(entries: List<NoteStorageFile>): List<NoteStorageFile> {
            val possible = entries.filter { it.hash !in pinned }
            val selected = possible.mapNotNull { entry -> entry.hash?.let { entry.directoryKey to it } }.toSet()
            // A base is removable in the same reviewed batch only when no retained delta still needs it.
            val retainedBases = links.filterKeys { it !in selected }.map { (target, base) -> target.first to base }.toSet()
            return possible.filter { (it.directoryKey to it.hash) !in retainedBases }
        }
        for ((key, entries) in files.groupBy { it.directoryKey }) {
            currentCoroutineContext().ensureActive()
            if (key in trashedDirectories) { trashed += entries.sumOf { it.bytes }; continue }
            val note = byDirectory[key]
            val bodies = entries.filter { it.hash != null }
            val preferredCurrent = bodies.firstOrNull { it.name == "${note?.contentHash}.md" }
                ?: bodies.firstOrNull { it.hash == note?.contentHash }
            val previous = bodies.filter { it != preferredCurrent }
            if (note != null) {
                current += preferredCurrent?.bytes ?: 0
                older += previous.sumOf { it.bytes }
                recovery += entries.filter { it.hash == null }.sumOf { it.bytes }
                if (keep < 0 || key in protectedDirectories) continue
                if (store.size(note.id, note.contentHash!!) != note.byteLength) continue
                if (verifyCurrent && !store.verify(note.id, note.contentHash, note.byteLength)) continue
                val potential = previous.sortedWith(compareByDescending<NoteStorageFile> { it.modified }.thenBy { it.name }).drop(keep)
                candidates += eligible(potential)
                candidates += entries.filter { it.isRetiredJournal }
            } else if (key in protectedDirectories) {
                recovery += entries.sumOf { it.bytes }
            } else {
                val detached = entries.filter { it.hash != null || it.isRetiredJournal }
                unattached += detached.sumOf { it.bytes }
                unattachedCount += detached.count { it.hash != null }
                recovery += entries.filter { it !in detached }.sumOf { it.bytes }
                unlinked += eligible(detached)
            }
        }
        val mediaRoot = File(context.filesDir, "media").canonicalFile
        val mediaRows = dao.mediaStorageItems()
        fun paths(rows: List<com.example.data.local.MediaStorageItem>): Set<String> = rows
            .flatMap { listOfNotNull(it.path, it.thumbnailPath) }.filter { it.startsWith('/') }
            .map { File(it).canonicalPath }.toSet()
        val activePaths = paths(mediaRows.filter { !it.isDeleted })
        val trashedPaths = paths(mediaRows.filter { it.isDeleted }) - activePaths
        var media = 0L
        val unlinkedMedia = ArrayList<RetainedMedia>()
        val time = System.currentTimeMillis()
        if (mediaRoot.exists()) mediaRoot.walkTopDown().filter { it.isFile }.forEach {
            cancellation.ensureActive()
            val path = it.canonicalPath
            if (!path.startsWith(mediaRoot.path + File.separator)) return@forEach
            val namedOwner = mediaRows.firstOrNull { row ->
                it.name.startsWith(row.id + ".") || it.name.startsWith(row.id + "_") ||
                    it.name.startsWith("thumb_" + row.id + ".") || it.name.startsWith("thumb_" + row.id + "_")
            }
            if (path in trashedPaths || namedOwner?.isDeleted == true && path !in activePaths) trashed += it.length()
            else if (path in activePaths || namedOwner != null) media += it.length()
            else {
                unattached += it.length(); unattachedCount++
                val grace = if (it.name.endsWith(".tmp")) 24 * 60 * 60 * 1000L else 60_000L
                if (it.lastModified() <= time - grace) unlinkedMedia.add(RetainedMedia(it.relativeTo(mediaRoot).path, it.length(), it.lastModified()))
            }
        }
        fun size(root: File): Long = if (!root.exists()) 0 else root.walkTopDown().filter { it.isFile }.sumOf { cancellation.ensureActive(); it.length() }
        val dbBytes = size(File(context.applicationInfo.dataDir, "databases"))
        val other = context.filesDir.listFiles().orEmpty().filter { it.name != "notes" && it.name != "media" }.sumOf(::size) +
            size(File(context.applicationInfo.dataDir, "shared_prefs"))
        val databaseConnection = database.openHelper.writableDatabase
        fun pragma(name: String): Long = databaseConnection.query("PRAGMA $name").use { if (it.moveToFirst()) it.getLong(0) else 0 }
        val freeDatabase = pragma("freelist_count") * pragma("page_size")
        val ownedCache = ownedCachePaths()
        val diskCache = context.imageLoader.diskCache
        val previewRoot = diskCache?.directory?.toString()?.let { File(it).canonicalPath + File.separator }
        val previewCache = if (previewRoot != null && ownedCache.any { it.startsWith(previewRoot) }) 0 else diskCache?.size ?: 0
        val cache = size(context.cacheDir)
        val work = File(context.cacheDir, "note-body-work")
        val workBytes = if (ownedCache.any { it.startsWith(work.canonicalPath + File.separator) }) 0 else NoteCacheWork.clearableBytes(work)
        val safeCache = (previewCache + workBytes + oldExports(ownedCache).sumOf { it.length() }).coerceAtMost(cache)
        return Plan(Report(current, older, recovery, dbBytes, cache, media, candidates.sumOf { it.bytes }, candidates.size, keep,
            trashed, deletedIds.size, unattached, unattachedCount, unlinked.sumOf { it.bytes } + unlinkedMedia.sumOf { it.bytes }, safeCache, freeDatabase, other), candidates, unlinked, unlinkedMedia)
    }
    companion object { val RETENTIONS = listOf(0, 1, 5, 10, 20, -1) }
}

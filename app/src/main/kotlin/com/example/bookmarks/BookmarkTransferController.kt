package com.example.bookmarks

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.CustomFolderEntity
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.notes.NoteWriteLock
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock

data class BookmarkIdentity(val id: String, val content: String, val isDeleted: Boolean)

/** Activity-scoped transfer state survives configuration changes; completed batches survive process death. */
class BookmarkTransferController(private val context: Context, private val scope: CoroutineScope) {
    enum class Phase { IDLE, CHOOSER, IMPORT_PICKER, IMPORT_WAITING, READING, REVIEW, IMPORTING, EXPORT_LOADING, EXPORT_REVIEW, EXPORT_PICKER, EXPORT_WAITING, WRITING, RESULT }
    data class Counts(val added: Int = 0, val existing: Int = 0, val updated: Int = 0, val trash: Int = 0, val skipped: Int = 0, val folders: Int = 0, val previews: Int = 0)
    data class State(
        val phase: Phase = Phase.IDLE,
        val fileName: String = "",
        val parsed: BookmarkHtml.Parsed? = null,
        val mapping: Map<List<String>, String> = emptyMap(),
        val existingFolders: List<String> = emptyList(),
        val singleFolder: Boolean = false,
        val destination: String = "Imported bookmarks",
        val existingLinks: Int = 0,
        val trashLinks: Int = 0,
        val duplicates: Int = 0,
        val processed: Int = 0,
        val counts: Counts = Counts(),
        val exportFolders: List<String> = emptyList(),
        val selectedFolders: Set<String> = emptySet(),
        val allFolders: Boolean = true,
        val includeUngrouped: Boolean = true,
        val includeArchived: Boolean = false,
        val exportCount: Int = 0,
        val unsupportedExport: Int = 0,
        val message: String = "",
        val failed: Boolean = false,
        val cancelled: Boolean = false,
        val exporting: Boolean = false,
        val cancelRequested: Boolean = false
    ) {
        val busy get() = phase in setOf(Phase.READING, Phase.IMPORTING, Phase.EXPORT_LOADING, Phase.WRITING)
    }
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val db = AppDatabase.getDatabase(context)
    val folders = db.customFolderDao().getAllFoldersFlow()

    suspend fun createDestinationFolder(name: String, icon: String): CustomFolderEntity = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 200) { "Use a folder name between 1 and 200 characters." }
        val folder = NoteWriteLock.mutex.withLock {
            db.withTransaction {
                db.customFolderDao().insertBookmarkFolder(CustomFolderEntity(name = trimmed, iconName = icon))
                db.customFolderDao().getAllFolders().first { it.name == trimmed }
            }
        }
        mutable.update { it.copy(existingFolders = (it.existingFolders + folder.name).distinct()) }
        runCatching { WidgetUpdater.update(context) }
        folder
    }

    private var job: Job? = null
    private var exportSnapshot = emptyList<SavedItem>()
    private var importUri: Uri? = null

    fun chooseImport() { if (!state.value.busy) mutable.value = State(phase = Phase.CHOOSER) }
    fun chooseBookmarks() { if (!state.value.busy) mutable.value = State(phase = Phase.IMPORT_PICKER) }
    fun dismiss() { if (!state.value.busy) { mutable.value = State(); exportSnapshot = emptyList() } }
    fun cancel() { mutable.update { it.copy(cancelRequested = true) }; job?.cancel() }
    fun pickerLaunched() {
        mutable.update { it.copy(phase = if (it.phase == Phase.EXPORT_PICKER) Phase.EXPORT_WAITING else Phase.IMPORT_WAITING) }
    }
    fun retryImport() {
        if (state.value.parsed != null) {
            mutable.update { it.copy(phase = Phase.REVIEW, counts = Counts(), processed = 0, failed = false, cancelled = false, cancelRequested = false) }
        } else importUri?.let(::read)
    }
    fun singleFolder(value: Boolean) { mutable.update { it.copy(singleFolder = value) } }
    fun destination(value: String) { mutable.update { it.copy(destination = value.take(200)) } }
    fun folderName(path: List<String>, value: String) { mutable.update { it.copy(mapping = it.mapping + (path to value.take(200))) } }
    fun pickerCancelled() {
        if (state.value.phase in setOf(Phase.EXPORT_PICKER, Phase.EXPORT_WAITING)) mutable.update { it.copy(phase = Phase.EXPORT_REVIEW) }
        else dismiss()
    }

    fun read(uri: Uri) {
        if (state.value.busy) return
        importUri = uri
        mutable.value = State(phase = Phase.READING)
        job = scope.launch(Dispatchers.IO) {
            try {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "Bookmark file"
                mutable.update { it.copy(fileName = name) }
                val cancellation = currentCoroutineContext()
                val parsed = (context.contentResolver.openInputStream(uri) ?: error("The file couldn't be opened. Choose it again.")).use {
                    BookmarkHtml.read(it) { cancellation.ensureActive() }
                }
                val links = db.savedItemDao().getBookmarkIdentities()
                val active = links.filterNot { it.isDeleted }.mapNotNull { BookmarkHtml.key(it.content) }.toSet()
                val trash = links.filter { it.isDeleted }.mapNotNull { BookmarkHtml.key(it.content) }.toSet() - active
                val unique = parsed.entries.mapNotNull { BookmarkHtml.key(it.url) }.toSet()
                val existing = db.customFolderDao().getAllFolders().map { it.name }
                val used = existing.toMutableSet()
                val mapping = parsed.folders.associateWith { path -> uniqueName(path.joinToString(" / ").take(190), used).also { used.add(it) } }
                mutable.update { it.copy(phase = Phase.REVIEW, parsed = parsed, mapping = mapping, existingFolders = existing,
                    destination = uniqueName("Imported bookmarks", existing.toSet()),
                    existingLinks = unique.count { key -> key in active }, trashLinks = unique.count { key -> key in trash },
                    duplicates = parsed.entries.size - unique.size) }
            } catch (_: CancellationException) {
                mutable.update { it.copy(phase = Phase.RESULT, cancelled = true, message = "Import cancelled. No bookmarks were saved.") }
            } catch (e: Exception) { failure(e, "The bookmark file couldn't be read. Choose it again.") }
        }
    }

    fun startImport() {
        val review = state.value
        if (review.phase != Phase.REVIEW) return
        val parsed = review.parsed ?: return
        val destinations = if (review.singleFolder) listOf(review.destination.trim()) else review.mapping.values.map { it.trim() }
        if (destinations.any { it.isBlank() }) return
        mutable.value = review.copy(phase = Phase.IMPORTING, cancelRequested = false, counts = Counts(skipped = parsed.skipped))
        job = scope.launch(Dispatchers.IO) {
            var counts = Counts(skipped = parsed.skipped)
            var processed = 0
            var previewWorkerStarted = false
            try {
                // Group equal URLs so one record gets all imported folder assignments.
                val deletionFence = com.example.notes.NoteDeletionFence(context)
                val grouped = parsed.entries.groupBy { BookmarkHtml.key(it.url)!! }.values.toList()
                val knownIds = db.savedItemDao().getBookmarkIdentities().groupBy { BookmarkHtml.key(it.content) }
                    .mapValues { (_, rows) -> (rows.firstOrNull { !it.isDeleted } ?: rows.first()).id }
                // Preserve empty folders too. Retry keeps this reviewed mapping, avoiding new suffixes.
                destinations.distinct().chunked(100).forEach { names ->
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        val created = NoteWriteLock.mutex.withLock {
                            db.withTransaction {
                                names.count { name -> db.customFolderDao().insertBookmarkFolder(CustomFolderEntity(name = name)) != -1L }
                            }
                        }
                        counts = counts.copy(folders = counts.folders + created)
                        mutable.update { it.copy(counts = counts, existingFolders = (it.existingFolders + names).distinct()) }
                    }
                }
                grouped.chunked(100).forEach { batch ->
                    currentCoroutineContext().ensureActive()
                    // Each bounded batch commits completely before cancellation is acknowledged.
                    withContext(NonCancellable) {
                        NoteWriteLock.mutex.withLock {
                            val committed = db.withTransaction {
                                var next = counts
                                val previewJobs = mutableListOf<com.example.data.local.LinkPreviewEntity>()
                                val urls = batch.flatMap { entries -> listOf(entries.first().url, BookmarkHtml.key(entries.first().url)!!) }.distinct()
                                val ids = batch.mapNotNull { entries -> knownIds[BookmarkHtml.key(entries.first().url)] }
                                // Resolve each batch while holding the same lock used by normal link saves.
                                val existingByKey = db.savedItemDao().getBookmarkCandidates(urls, ids).groupBy { BookmarkHtml.key(it.content) }
                                    .mapValues { (_, rows) -> rows.firstOrNull { !it.isDeleted } ?: rows.first() }
                                batch.forEach entry@ { entries ->
                                    val first = entries.first()
                                    val key = BookmarkHtml.key(first.url)!!
                                    val folders = if (review.singleFolder) listOf(review.destination.trim()) else entries.mapNotNull {
                                        review.mapping[it.path]?.trim()
                                    }.distinct()
                                    val cached = existingByKey[key]
                                    // Re-read inside the lock: don't overwrite a concurrent edit or restore Trash.
                                    val old = cached?.let { db.savedItemDao().getItemById(it.id) }
                                    if (old?.isDeleted == true) { next = next.copy(trash = next.trash + 1); return@entry }
                                    val item = old?.toDomainModel()
                                    val importedDescription = entries.firstNotNullOfOrNull { it.description?.takeIf(String::isNotBlank) }
                                    val saved = if (item != null) item.copy(folders = (item.folders + folders).distinct(),
                                        linkDescription = item.linkDescription?.takeIf(String::isNotBlank) ?: importedDescription ?: item.linkDescription,
                                        updatedAt = System.currentTimeMillis())
                                        else SavedItem(type = SavedItemType.LINK, title = first.title, content = first.url,
                                            folders = folders, timestamp = first.added ?: System.currentTimeMillis(), linkDescription = importedDescription)
                                    if (item == null || saved.folders != item.folders || saved.linkDescription != item.linkDescription) {
                                        deletionFence.requireAlive(saved.id)
                                        db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(saved))
                                    }
                                    if (item == null || saved.linkImage.isNullOrBlank() || saved.linkDescription.isNullOrBlank() || saved.linkTitle.isNullOrBlank()) {
                                        previewJobs.add(com.example.data.local.LinkPreviewEntity(saved.id, saved.content,
                                            replaceFallback = false, preserveTitle = true))
                                    }
                                    next = if (item == null) next.copy(added = next.added + 1) else next.copy(
                                        existing = next.existing + 1, updated = next.updated + if (saved.folders != item.folders) 1 else 0)
                                }
                                db.linkPreviewDao().putAll(previewJobs)
                                next.copy(previews = next.previews + previewJobs.size)
                            }
                            counts = committed
                        }
                        if (!previewWorkerStarted && counts.previews > 0) {
                            runCatching { com.example.links.LinkPreviewWorker.schedule(context) }
                            previewWorkerStarted = true
                        }
                        processed += batch.sumOf { it.size }
                        mutable.update { it.copy(counts = counts, processed = processed) }
                    }
                }
                mutable.update { it.copy(phase = Phase.RESULT, counts = counts, message = "Your bookmarks are saved. Previews and missing descriptions load in the background when online. Saved titles and descriptions are kept.") }
            } catch (_: CancellationException) {
                mutable.update { it.copy(phase = Phase.RESULT, counts = counts, cancelled = true,
                    message = "Import stopped. Completed bookmarks are kept. Import the file again to add the rest.") }
            } catch (_: Exception) {
                mutable.update { it.copy(phase = Phase.RESULT, counts = counts, failed = true,
                    message = "Import stopped before finishing. Completed bookmarks are kept. Check available storage and try again.") }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (counts.previews > 0) runCatching {
                        com.example.links.LinkPreviewWorker.schedule(context)
                        com.example.links.LinkPreviewWorker.continueQueue(context)
                    }
                    runCatching { WidgetUpdater.update(context) }
                }
            }
        }
    }

    fun prepareExport() {
        if (state.value.busy) return
        mutable.value = State(phase = Phase.EXPORT_LOADING, exporting = true)
        job = scope.launch(Dispatchers.IO) {
            try {
                val all = db.savedItemDao().getBookmarkLinks().filterNot { it.isDeleted }.map { it.toDomainModel() }
                val supported = all.filter { BookmarkHtml.key(it.content) != null }
                exportSnapshot = supported
                mutable.update { it.copy(phase = Phase.EXPORT_REVIEW, exportFolders = supported.flatMap { row -> row.folders }.distinct().sorted(),
                    unsupportedExport = all.size - supported.size) }
                updateExportOptions()
            } catch (_: CancellationException) { mutable.update { it.copy(phase = Phase.RESULT, cancelled = true, message = "Export cancelled. Your saved links are unchanged.") } }
            catch (_: Exception) { failure(null, "Your links couldn't be loaded. Try exporting again.") }
        }
    }
    fun updateExportOptions(all: Boolean = state.value.allFolders, folders: Set<String> = state.value.selectedFolders,
        ungrouped: Boolean = state.value.includeUngrouped, archived: Boolean = state.value.includeArchived) {
        val next = state.value.copy(allFolders = all, selectedFolders = folders, includeUngrouped = ungrouped, includeArchived = archived)
        mutable.value = next.copy(exportCount = exportItems(next).size)
    }
    private fun exportItems(options: State) = exportSnapshot.filter {
        (options.includeArchived || !it.isArchived) &&
            (options.allFolders || it.folders.any { folder -> folder in options.selectedFolders } || (options.includeUngrouped && it.folders.isEmpty()))
    }
    fun chooseExportFile() { if (state.value.exportCount > 0) mutable.update { it.copy(phase = Phase.EXPORT_PICKER) } }
    fun write(uri: Uri) {
        if (state.value.phase != Phase.EXPORT_WAITING) return
        val options = state.value
        mutable.value = options.copy(phase = Phase.WRITING)
        job = scope.launch(Dispatchers.IO) {
            try {
                val items = exportItems(options)
                val entries = items.flatMap { item ->
                    val folders = if (options.allFolders) item.folders.distinct() else item.folders.filter { it in options.selectedFolders }.distinct()
                    if (folders.isEmpty()) listOf(BookmarkHtml.Entry(item.content, item.title.ifBlank { item.content }, emptyList(), item.timestamp, item.linkDescription))
                    else folders.map(BookmarkHtml::exportPath).distinct().map { path ->
                        BookmarkHtml.Entry(item.content, item.title.ifBlank { item.content }, path, item.timestamp, item.linkDescription)
                    }
                }
                val cancellation = currentCoroutineContext()
                (context.contentResolver.openOutputStream(uri, "wt") ?: error("The destination couldn't be opened.")).use {
                    BookmarkHtml.write(it, entries) { cancellation.ensureActive() }
                }
                mutable.update { it.copy(phase = Phase.RESULT, message = "${items.size} links exported. Links in several folders appear in each folder.") }
            } catch (_: CancellationException) { mutable.update { it.copy(phase = Phase.RESULT, cancelled = true,
                message = "Export cancelled. The destination file may be incomplete. Your saved links are unchanged.") } }
            catch (_: Exception) { failure(null, "Export couldn't finish. Choose a writable destination and try again. The destination file may be incomplete.") }
        }
    }
    private fun failure(error: Exception?, fallback: String) {
        mutable.update { it.copy(phase = Phase.RESULT, failed = true,
            message = if (error is IllegalArgumentException) error.message ?: fallback else fallback) }
    }
    private fun uniqueName(base: String, used: Set<String>): String {
        if (base !in used) return base
        var count = 2
        while ("$base ($count)" in used) count++
        return "$base ($count)"
    }
}

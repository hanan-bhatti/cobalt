package com.example.notes

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.*
import java.security.MessageDigest
import java.util.UUID

private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** The only component allowed to access private Markdown body files. All calls are IO work. */
interface NoteBodyStore {
    fun open(id: String, hash: String): InputStream
    fun readRange(id: String, hash: String, offset: Long, count: Int): ByteArray
    fun openRangeReader(id: String, hash: String): NoteRangeReader {
        val store = this
        return object : NoteRangeReader {
            override fun read(offset: Long, count: Int) = store.readRange(id, hash, offset, count)
            override fun close() = Unit
        }
    }
    fun writeAtomic(id: String, input: InputStream, allowOversize: Boolean = false, progress: (Long) -> Unit = {}): NoteBodyRef
    fun writeReplacement(id: String, baseHash: String, input: InputStream, allowOversize: Boolean = false,
        progress: (Long) -> Unit = {}): NoteBodyRef = writeAtomic(id, input, allowOversize, progress)
    fun writePieces(id: String, input: InputStream, pieces: List<SharedNotePiece>?, allowOversize: Boolean = false,
        progress: (Long) -> Unit = {}): NoteBodyRef = writeAtomic(id, input, allowOversize, progress)
    fun verify(id: String, hash: String, bytes: Long): Boolean
    fun delete(id: String, hash: String)
    fun size(id: String, hash: String): Long?
    fun committed(id: String, hash: String) {}
    fun deleteAll(id: String)
    fun createEditJournal(id: String, token: String, base: NoteBodyRef): NoteEditJournal =
        throw UnsupportedOperationException("Edit journals unavailable")
    fun editJournal(id: String, token: String): NoteEditJournal =
        throw UnsupportedOperationException("Edit journals unavailable")
    fun retireEditJournal(id: String, token: String, verified: NoteBodyRef) {}
    fun recoverEditJournal(id: String, token: String, base: NoteBodyRef, checkCancelled: () -> Unit = {}): NoteBodyRef =
        throw UnsupportedOperationException("Journal recovery unavailable")
    fun openLiveEditor(id: String, token: String, base: NoteBodyRef, reopen: Boolean = false, checkCancelled: () -> Unit = {}): DiskTextModel =
        throw UnsupportedOperationException("Live disk editing unavailable")
    fun retireLiveEditor(id: String, token: String) {}
    fun liveEditors(): List<LiveEditorDescriptor> = emptyList()
    fun maintenanceFiles(): List<NoteStorageFile> = emptyList()
    fun accountingFiles(current: Set<Pair<String, String>>): List<NoteStorageFile> = maintenanceFiles()
    fun collectSharedStorage() {}
    fun removeMaintenanceFile(entry: NoteStorageFile): Boolean = false
    fun prepareArchivePrevious(id: String, previousHash: String, currentHash: String,
        checkCancelled: () -> Unit = {}): NoteHistoryWrite? = null
    fun archivePrevious(id: String, previousHash: String, currentHash: String, checkCancelled: () -> Unit = {}) {
        prepareArchivePrevious(id, previousHash, currentHash, checkCancelled)?.use { it.publish() }
    }
    fun historyAnchors(): Set<Pair<String, String>> = emptySet()
    fun materialize(id: String, hash: String) {}
    fun openMaintenance(entry: NoteStorageFile): InputStream = throw UnsupportedOperationException("Storage export unavailable")
    fun historyLinks(): Map<Pair<String, String>, String> = emptyMap()
}

/** A prepared, verified history conversion. Caller serializes publish with metadata writes. */
interface NoteHistoryWrite : Closeable { fun publish() }

/** A confined random reader; opening/reading/closing all happen off Main. */
interface NoteRangeReader : Closeable { fun read(offset: Long, count: Int): ByteArray }

data class NoteBodyRef(val hash: String, val bytes: Long, val preview: String)

/** Platform filesystem seam. Production always uses Android's rename/fsync syscalls. */
interface NoteFileOperations {
    fun syncDirectory(directory: File)
    fun renameAtomic(source: File, destination: File)
}

internal object AndroidNoteFileOperations : NoteFileOperations {
    override fun renameAtomic(source: File, destination: File) = Os.rename(source.absolutePath, destination.absolutePath)
    override fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
}

class PrivateMarkdownStore(context: Context,
    private val fileOperations: NoteFileOperations = AndroidNoteFileOperations,
    private val checkpoint: (WriteStage) -> Unit = {},
    private val sharedStorage: Boolean = true,
    private val database: com.example.data.local.AppDatabase? = null) : NoteBodyStore {
    enum class WriteStage { JOURNAL_SYNCED, CHUNK_WRITTEN, DATA_SYNCED, RENAMED, VERIFIED }
    private val appContext = context.applicationContext
    private val fence by lazy { NoteDeletionFence(appContext) }
    private val root = File(context.applicationContext.filesDir, "notes")
    private val work = File(context.applicationContext.cacheDir, "note-body-work")
    private val shared by lazy { SharedNoteTree(database ?: com.example.data.local.AppDatabase.getDatabase(appContext)) }
    private fun sharedRoot(id: String, hash: String) = if (sharedStorage) shared.root(key(id), hash) else null
    private fun key(id: String) = sha256(id.toByteArray(Charsets.UTF_8))
    private fun directory(id: String) = File(root, key(id))
    private fun file(id: String, hash: String): File {
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid body reference" }
        return File(directory(id), "$hash.md")
    }
    private fun delta(id: String, hash: String) = File(file(id, hash).parentFile, "$hash.delta")

    override fun materialize(id: String, hash: String) {
        if (sharedRoot(id, hash) != null) return
        if (file(id, hash).isFile) return
        val restored = open(id, hash).use { writeAtomic(id, it, allowOversize = true) }
        check(restored.hash == hash) { "History restoration mismatch; versions retained" }
    }

    override fun historyAnchors(): Set<Pair<String, String>> = historyLinks().map { (target, base) -> target.first to base }.toSet()

    override fun historyLinks(): Map<Pair<String, String>, String> = root.listFiles().orEmpty().flatMap { dir ->
        dir.listFiles().orEmpty().filter { it.extension == "delta" || it.name.endsWith(".live.state") }.map { entry ->
            if (entry.name.endsWith(".live.state")) {
                (dir.name to "live:${entry.name}") to DiskTextModel.inspect(entry).hash
            } else {
                val header = entry.inputStream().use { NoteVersionDelta.inspect(it) }
                check(header.targetHash == entry.nameWithoutExtension)
                (dir.name to header.targetHash) to header.baseHash
            }
        }
    }.toMap()

    override fun openMaintenance(entry: NoteStorageFile): InputStream = NoteBodyLeases.guarded {
        require(entry.directoryKey.matches(Regex("[0-9a-f]{64}")) && entry.hash != null)
        val dir = File(root, entry.directoryKey)
        val source = File(dir, entry.name)
        val hash = entry.hash!!
        if (entry.name.endsWith(".tree")) {
            val ref = requireNotNull(shared.root(entry.directoryKey, hash)) { "Shared version unavailable" }
            check(ref.created == entry.modified) { "Version changed; refresh Storage" }
            return@guarded leased(entry.directoryKey, hash, shared.open(ref))
        }
        check(source.isFile && source.length() == entry.bytes && source.lastModified() == entry.modified) { "File changed; refresh Storage" }
        val input = if (entry.name.endsWith(".md")) source.inputStream() else {
            NoteVersionDelta.open(source.inputStream(), hash) { header ->
                val base = File(dir, "${header.baseHash}.md")
                val sharedBase = if (sharedStorage) shared.root(entry.directoryKey, header.baseHash) else null
                check(sharedBase?.bytes == header.baseBytes || base.isFile && base.length() == header.baseBytes) { "History anchor missing" }
                val reader = if (sharedBase != null) shared.reader(sharedBase) else null
                val fileReader = if (reader == null) RandomAccessFile(base, "r") else null
                NoteBodyLeases.acquire(entry.directoryKey, header.baseHash)
                object : NoteRangeReader {
                    override fun read(offset: Long, count: Int): ByteArray {
                        require(offset >= 0 && count in 0..NoteSizePolicy.IO_CHUNK_BYTES)
                        if (reader != null) return reader.read(offset, count)
                        fileReader!!.seek(offset)
                        return ByteArray(minOf(count.toLong(), (fileReader.length() - offset).coerceAtLeast(0)).toInt()).also(fileReader::readFully)
                    }
                    override fun close() { try { reader?.close(); fileReader?.close() } finally { NoteBodyLeases.release(entry.directoryKey, header.baseHash) } }
                }
            }
        }
        NoteBodyLeases.acquire(entry.directoryKey, hash)
        object : FilterInputStream(input) {
            private var closed = false
            override fun close() {
                if (closed) return
                closed = true
                try { super.close() } finally { NoteBodyLeases.release(entry.directoryKey, hash) }
            }
        }
    }

    /** Older versions use one-level deltas. The current body and every anchor stay ordinary .md. */
    override fun prepareArchivePrevious(id: String, previousHash: String, currentHash: String,
        checkCancelled: () -> Unit): NoteHistoryWrite? {
        if (sharedRoot(id, previousHash) != null || sharedRoot(id, currentHash) != null) return null
        if (previousHash == currentHash) return null
        val runtime = Runtime.getRuntime()
        if (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) < 32L * 1024 * 1024) return null
        val old = file(id, previousHash)
        if (!old.isFile || !file(id, currentHash).isFile || old.length() < 32 * 1024) return null
        val identity = key(id)
        if ((identity to previousHash) in historyAnchors()) return null
        val anchor = directory(id).listFiles().orEmpty().filter {
            it.extension == "md" && it.nameWithoutExtension != previousHash && it.nameWithoutExtension != currentHash
        }.minByOrNull { it.lastModified() } ?: return null
        val baseHash = anchor.nameWithoutExtension
        val oldBytes = old.length()
        val baseBytes = anchor.length()
        val temp = File(directory(id), "${UUID.randomUUID()}.delta.tmp")
        // Keep source leases across preparation/publication, without holding the database mutex
        // during hashing, chunk indexing, compression or complete reconstruction verification.
        val target = open(id, previousHash)
        val base = try { open(id, baseHash) } catch (error: Throwable) { target.close(); throw error }
        var transferred = false
        fun dispose() { try { base.close() } finally { try { target.close() } finally { temp.delete() } } }
        try {
            check(verify(id, baseHash, baseBytes) && verify(id, previousHash, oldBytes)) { "History source verification failed" }
            FileOutputStream(temp).use { output ->
                NoteVersionDelta.write(base, target,
                    NoteVersionDelta.Header(baseHash, previousHash, baseBytes, oldBytes), output, checkCancelled)
                output.fd.sync()
            }
            if (temp.length() >= oldBytes - oldBytes / 10) return null
            temp.setLastModified(old.lastModified())
            val verified = NoteVersionDelta.open(temp.inputStream(), previousHash) { header ->
                check(header.baseHash == baseHash && header.baseBytes == baseBytes)
                openRangeReader(id, baseHash)
            }.use { input -> digest(input) == previousHash }
            check(verified) { "History reconstruction failed; full version retained" }
            checkCancelled()
            return object : NoteHistoryWrite {
                private var closed = false
                override fun publish() {
                    check(!closed)
                    checkCancelled()
                    NoteBodyLeases.guarded {
                        // Existing .md descriptors stay readable after unlink on Android.
                        if (!old.isFile || old.length() != oldBytes || (identity to previousHash) in historyAnchors()) return@guarded
                        fileOperations.renameAtomic(temp, delta(id, previousHash))
                        syncDirectory(directory(id))
                        check(old.delete()) { "Verified delta published; complete duplicate retained" }
                        syncDirectory(directory(id))
                    }
                }
                override fun close() { if (!closed) { closed = true; dispose() } }
            }.also { transferred = true }
        } finally { if (!transferred) dispose() }
    }

    private fun journalFile(id: String, token: String): File {
        require(token.matches(Regex("[0-9a-f-]{36}"))) { "Invalid journal identity" }
        return File(directory(id), "$token.edits")
    }

    override fun createEditJournal(id: String, token: String, base: NoteBodyRef): NoteEditJournal = NoteBodyLeases.guarded {
        fence.requireAlive(id)
        val dir = directory(id)
        check(dir.isDirectory) { "Journal base directory missing" }
        NoteEditJournal.create(journalFile(id, token), base.hash, base.bytes).also { syncDirectory(dir) }
    }
    override fun editJournal(id: String, token: String): NoteEditJournal = NoteEditJournal(journalFile(id, token))

    override fun recoverEditJournal(id: String, token: String, base: NoteBodyRef, checkCancelled: () -> Unit): NoteBodyRef {
        check(verify(id, base.hash, base.bytes)) { "Recovery base verification failed; originals retained" }
        val log = editJournal(id, token)
        if (log.bytes() == 80L) {
            log.replay(base.hash, base.bytes, { _, _ -> error("Unexpected journal operation") }, {})
            return base
        }
        return NoteCacheWork.using(work) { open(id, base.hash).use { input ->
            DiskJournalRecovery.replay(input, log, base.hash, base.bytes, work, checkCancelled) { recovered ->
                writeAtomic(id, recovered, allowOversize = base.bytes > NoteSizePolicy.IMPORT_MAX_BYTES) { checkCancelled() }
            }
        } }
    }

    override fun retireEditJournal(id: String, token: String, verified: NoteBodyRef) {
        check(verify(id, verified.hash, verified.bytes)) { "Cannot retire recovery before verification" }
        val source = journalFile(id, token)
        if (!source.isFile) return
        // Rename only after a durable preference/DB reference owns the verified full replacement.
        // A crash before this step merely retains extra recovery bytes.
        fileOperations.renameAtomic(source, File(source.parentFile, source.name + ".retired"))
        syncDirectory(source.parentFile!!)
    }

    override fun openLiveEditor(id: String, token: String, base: NoteBodyRef, reopen: Boolean, checkCancelled: () -> Unit): DiskTextModel {
        require(token.matches(Regex("[0-9a-f-]{36}")))
        val dir = directory(id)
        val state = File(dir, "$token.live.state")
        val original = NoteBodyLeases.guarded {
            fence.requireAlive(id)
            LiveEditorLeases.acquire(key(id))
            try {
                check(dir.isDirectory || dir.mkdirs())
                check(!reopen || state.isFile) { "Live editor state is missing; the saved checkpoint is retained" }
                val metadata = if (state.isFile) DiskTextModel.metadata(state) else null
                check(metadata == null || metadata.ownerId.isEmpty() || metadata.ownerId == id) { "Editor ownership mismatch; retained" }
                val ref = metadata?.base ?: base
                check(size(id, ref.hash) == ref.bytes) { "Live editor original is missing; files retained" }
                NoteBodyLeases.acquire(key(id), ref.hash)
                ref
            } catch (error: Throwable) { LiveEditorLeases.release(key(id)); throw error }
        }
        val released = java.util.concurrent.atomic.AtomicBoolean(false)
        val release = { if (released.compareAndSet(false, true)) {
            NoteBodyLeases.release(key(id), original.hash); LiveEditorLeases.release(key(id))
        } }
        try {
            return DiskTextModel.open(FileNoteByteSource(this, id, original.hash, original.bytes), original,
                File(dir, "$token.live.buffer"), state, fileOperations, { fence.requireAlive(id) },
                release, checkCancelled, ownerId = id)
        } catch (error: Throwable) { release(); throw error }
    }
    override fun liveEditors(): List<LiveEditorDescriptor> = maintenanceFiles().filter { it.name.endsWith(".live.state") }.mapNotNull { entry ->
        runCatching {
            val metadata = DiskTextModel.metadata(File(File(root, entry.directoryKey), entry.name))
            if (metadata.ownerId.isBlank() || key(metadata.ownerId) != entry.directoryKey) return@runCatching null
            LiveEditorDescriptor(metadata.ownerId, entry.name.removeSuffix(".live.state"), metadata.base, metadata.bytes, metadata.revision)
        }.getOrNull()
    }
    override fun retireLiveEditor(id: String, token: String) = NoteBodyLeases.guarded {
        check(!LiveEditorLeases.active(key(id))) { "Editor history is still in use" }
        require(token.matches(Regex("[0-9a-f-]{36}")))
        val dir = directory(id)
        for (suffix in listOf("live.state", "live.buffer", "live.state.tmp")) {
            val path = File(dir, "$token.$suffix")
            if (path.exists()) check(path.delete()) { "Live editor history could not be removed" }
        }
        if (dir.isDirectory) syncDirectory(dir)
    }

    private fun filesystemFiles(): List<NoteStorageFile> = root.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.matches(Regex("[0-9a-f]{64}")) }
        .flatMap { dir -> dir.listFiles().orEmpty().filter { it.isFile }.map {
            NoteStorageFile(dir.name, it.name, it.length(), it.lastModified())
        } }

    override fun maintenanceFiles(): List<NoteStorageFile> = filesystemFiles() + if (sharedStorage) shared.accounting(emptySet()) else emptyList()
    override fun accountingFiles(current: Set<Pair<String, String>>) = filesystemFiles() + if (sharedStorage) shared.accounting(current) else emptyList()
    override fun collectSharedStorage() { if (sharedStorage) shared.collect() }

    override fun removeMaintenanceFile(entry: NoteStorageFile): Boolean {
        require(entry.directoryKey.matches(Regex("[0-9a-f]{64}")))
        require(entry.hash != null || (entry.isRetiredJournal && entry.name.matches(Regex("[0-9a-f-]{36}\\.edits\\.retired"))))
        val dir = File(root, entry.directoryKey)
        val target = File(dir, entry.name)
        if (entry.name.endsWith(".tree")) return NoteBodyLeases.guarded {
            if (entry.directoryKey to entry.hash!! in historyAnchors() ||
                NoteBodyLeases.active(entry.directoryKey, entry.hash) || entry.hash in NoteGenerationHistory.protectedHashes()) return@guarded false
            shared.remove(entry.directoryKey, entry.hash, entry.modified)
        }
        // A reviewed cleanup plan cannot remove a file that changed after the scan.
        val removed = NoteBodyLeases.guarded {
            if (entry.hash != null && entry.directoryKey to entry.hash!! in historyAnchors()) return@guarded false
            if (entry.hash != null && (NoteBodyLeases.active(entry.directoryKey, entry.hash!!) ||
                entry.hash in NoteGenerationHistory.protectedHashes())) return@guarded false
            if (!target.isFile || target.length() != entry.bytes || target.lastModified() != entry.modified) return@guarded false
            if (!target.delete()) throw IOException("Cannot remove the reviewed storage copy")
            true
        }
        if (!removed) return false
        syncDirectory(dir)
        if (dir.listFiles()?.isEmpty() == true && dir.delete()) syncDirectory(root)
        return true
    }

    override fun size(id: String, hash: String): Long? = sharedRoot(id, hash)?.bytes ?: file(id, hash).takeIf { it.isFile }?.length()
        ?: delta(id, hash).takeIf { it.isFile }?.inputStream()?.use { input ->
            NoteVersionDelta.inspect(input).also { check(it.targetHash == hash) }.targetBytes
        }

    override fun open(id: String, hash: String): InputStream = NoteBodyLeases.guarded {
        sharedRoot(id, hash)?.let { return@guarded leased(key(id), hash, shared.open(it)) }
        val body = file(id, hash)
        val input = if (body.isFile) FileInputStream(body) else {
            NoteVersionDelta.open(FileInputStream(delta(id, hash)), hash) { header ->
                check(size(id, header.baseHash) == header.baseBytes) {
                    "History anchor missing; version retained"
                }
                openRangeReader(id, header.baseHash)
            }
        }
        leased(key(id), hash, input)
    }

    private fun leased(identity: String, hash: String, input: InputStream): InputStream {
        NoteBodyLeases.acquire(identity, hash)
        return object : FilterInputStream(input) {
            private var closed = false
            override fun close() {
                if (closed) return
                closed = true
                try { super.close() } finally { NoteBodyLeases.release(identity, hash) }
            }
        }
    }

    override fun openRangeReader(id: String, hash: String): NoteRangeReader {
        sharedRoot(id, hash)?.let { ref ->
            return NoteBodyLeases.guarded {
                val reader = shared.reader(ref)
                NoteBodyLeases.acquire(key(id), hash)
                object : NoteRangeReader {
                    private var closed = false
                    override fun read(offset: Long, count: Int) = reader.read(offset, count)
                    override fun close() {
                        if (!closed) { closed = true; try { reader.close() } finally { NoteBodyLeases.release(key(id), hash) } }
                    }
                }
            }
        }
        if (!file(id, hash).isFile) {
            // History browsing is infrequent; bounded streaming avoids a resident reconstructed String.
            // The current body is always materialized before Room switches to it.
            NoteCacheWork.acquire(work)
            if (!work.exists() && !work.mkdirs() && !work.isDirectory) {
                NoteCacheWork.release(work); error("History workspace unavailable")
            }
            val scratch = File(work, "${UUID.randomUUID()}.history.tmp")
            try {
                open(id, hash).use { source -> scratch.outputStream().use { source.copyTo(it, NoteSizePolicy.IO_CHUNK_BYTES) } }
                val reader = RandomAccessFile(scratch, "r")
                return object : NoteRangeReader {
                    override fun read(offset: Long, count: Int): ByteArray {
                        require(offset >= 0 && count in 0..NoteSizePolicy.IO_CHUNK_BYTES)
                        reader.seek(offset)
                        return ByteArray(minOf(count.toLong(), (reader.length() - offset).coerceAtLeast(0)).toInt()).also { reader.readFully(it) }
                    }
                    private var closed = false
                    override fun close() {
                        if (closed) return
                        closed = true
                        try { reader.close() } finally { scratch.delete(); NoteCacheWork.release(work) }
                    }
                }
            } catch (error: Throwable) { scratch.delete(); NoteCacheWork.release(work); throw error }
        }
        val identity = key(id)
        val input = NoteBodyLeases.guarded {
            RandomAccessFile(file(id, hash), "r").also { NoteBodyLeases.acquire(identity, hash) }
        }
        return object : NoteRangeReader {
            override fun read(offset: Long, count: Int): ByteArray {
                require(offset >= 0 && count in 0..NoteSizePolicy.IO_CHUNK_BYTES)
                input.seek(offset)
                return ByteArray(minOf(count.toLong(), (input.length() - offset).coerceAtLeast(0)).toInt()).also { input.readFully(it) }
            }
            private var closed = false
            override fun close() {
                if (closed) return
                closed = true
                try { input.close() } finally { NoteBodyLeases.release(identity, hash) }
            }
        }
    }

    override fun readRange(id: String, hash: String, offset: Long, count: Int): ByteArray {
        require(offset >= 0 && count in 0..NoteSizePolicy.IO_CHUNK_BYTES)
        if (sharedRoot(id, hash) != null) return openRangeReader(id, hash).use { it.read(offset, count) }
        if (!file(id, hash).isFile) return openRangeReader(id, hash).use { it.read(offset, count) }
        return RandomAccessFile(file(id, hash), "r").use { input ->
            input.seek(offset)
            val result = ByteArray(minOf(count.toLong(), (input.length() - offset).coerceAtLeast(0)).toInt())
            input.readFully(result)
            result
        }
    }

    override fun writeAtomic(id: String, input: InputStream, allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef {
        if (sharedStorage) return writePieces(id, input, null, allowOversize, progress)
        fence.requireAlive(id)
        val dir = directory(id)
        val token = UUID.randomUUID().toString()
        val temp = File(dir, "$token.tmp")
        val journal = File(dir, "$token.journal")
        NoteBodyLeases.guarded {
            fence.requireAlive(id)
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create private note directory")
            syncDirectory(root.parentFile!!)
            syncDirectory(root)
            FileOutputStream(journal).use { it.write("WRITING\n$token.tmp\n".toByteArray()); it.fd.sync() }
            syncDirectory(dir)
            checkpoint(WriteStage.JOURNAL_SYNCED)
        }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val prefix = ByteArrayOutputStream(4096)
            var bytes = 0L
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    bytes += count
                    if (!allowOversize && bytes > NoteSizePolicy.IMPORT_MAX_BYTES) {
                        throw NoteSizeExceededException()
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    if (prefix.size() < 4096) prefix.write(buffer, 0, minOf(count, 4096 - prefix.size()))
                    progress(bytes)
                    checkpoint(WriteStage.CHUNK_WRITTEN)
                }
                output.fd.sync()
                checkpoint(WriteStage.DATA_SYNCED)
            }
            val hash = digest.digest().hex()
            val destination = file(id, hash)
            NoteBodyLeases.guarded {
                fence.requireAlive(id)
                if (destination.exists()) {
                    if (!verify(id, hash, bytes)) throw IOException("Existing body generation failed verification")
                    temp.delete()
                } else {
                    fileOperations.renameAtomic(temp, destination)
                    syncDirectory(dir)
                    checkpoint(WriteStage.RENAMED)
                }
            }
            if (!verify(id, hash, bytes)) throw IOException("Markdown verification failed; original retained")
            // This durable journal is removed only after Room commits the new reference.
            FileOutputStream(journal).use { it.write("VERIFIED\n$hash\n$bytes\n".toByteArray()); it.fd.sync() }
            syncDirectory(dir)
            checkpoint(WriteStage.VERIFIED)
            val preview = NoteTextMetadata.preview(prefix.toByteArray().toString(Charsets.UTF_8)
                .replace(Regex("[\\r\\n\\t]+"), " ").replace("\u0000", "")
                .replace(Regex("^\\s*[#>*-]+\\s*"), ""))
            return NoteBodyRef(hash, bytes, preview)
        } catch (error: Throwable) {
            temp.delete() // Never delete any pre-existing body generation on failure.
            if (fence.deleted(id)) NoteBodyLeases.guarded {
                journal.delete()
                if (dir.listFiles()?.isEmpty() == true) dir.delete()
            }
            throw error
        }
    }

    override fun writePieces(id: String, input: InputStream, pieces: List<SharedNotePiece>?, allowOversize: Boolean,
        progress: (Long) -> Unit): NoteBodyRef {
        if (!sharedStorage) return writeAtomic(id, input, allowOversize, progress)
        fence.requireAlive(id)
        val dir = directory(id)
        NoteBodyLeases.guarded {
            check(dir.isDirectory || dir.mkdirs()) { "Private note directory unavailable" }
            syncDirectory(root.parentFile!!); syncDirectory(root); syncDirectory(dir)
        }
        val bases = pieces.orEmpty().mapNotNull { it.hash }.toSet()
        NoteBodyLeases.guarded { bases.forEach { NoteBodyLeases.acquire(key(id), it) } }
        try {
            for (hash in bases) if (sharedRoot(id, hash) == null) open(id, hash).use { original ->
                    val saved = shared.write(key(id), original, true, { progress(0) })
                    check(saved.hash == hash) { "Legacy migration checksum mismatch" }
                    shared.committed(key(id), hash)
                    // Deltas can read a shared anchor through the range interface. Existing .md handles survive unlink.
                    NoteBodyLeases.guarded {
                        val source = file(id, hash)
                        if (source.isFile) { check(source.delete()) { "Verified legacy duplicate could not be removed" }; syncDirectory(dir) }
                    }
            }
            return shared.write(key(id), input, allowOversize, { bytes -> fence.requireAlive(id); progress(bytes) }, pieces,
                ensureBase = { hash -> requireNotNull(sharedRoot(id, hash)) { "Shared editor base unavailable" } }, checkpoint = checkpoint)
        } finally { NoteBodyLeases.guarded { bases.forEach { NoteBodyLeases.release(key(id), it) } } }
    }

    override fun writeReplacement(id: String, baseHash: String, input: InputStream, allowOversize: Boolean,
        progress: (Long) -> Unit): NoteBodyRef {
        if (!sharedStorage) return writeAtomic(id, input, allowOversize, progress)
        return NoteReplacementStorage.write(this, work, id, baseHash, input, allowOversize, progress)
    }

    override fun verify(id: String, hash: String, bytes: Long): Boolean {
        if (size(id, hash) != bytes) return false
        return open(id, hash).use { digest(it) } == hash
    }

    /** Called after the DB commit; journals never authorize changing a DB reference. */
    override fun committed(id: String, hash: String) {
        if (sharedRoot(id, hash) != null) shared.committed(key(id), hash)
        var changed = false
        if (sharedRoot(id, hash) != null) {
            for (copy in listOf(file(id, hash), delta(id, hash))) if (copy.isFile) {
                check(copy.delete()) { "Verified shared body retained; legacy duplicate cleanup needs retry" }; changed = true
            }
        }
        // A restored version has become a complete current Markdown body again.
        if (file(id, hash).isFile && delta(id, hash).isFile && delta(id, hash).delete()) changed = true
        directory(id).listFiles()?.filter { it.extension == "journal" }?.forEach { journal ->
            val lines = journal.readLines()
            if (lines.getOrNull(0) == "VERIFIED" && lines.getOrNull(1) == hash && journal.delete()) changed = true
        }
        if (changed) syncDirectory(directory(id))
    }

    override fun delete(id: String, hash: String) {
        check(key(id) to hash !in historyAnchors()) { "History anchor is still referenced" }
        val body = file(id, hash)
        if (sharedRoot(id, hash) != null) NoteBodyLeases.guarded {
            check(!NoteBodyLeases.active(key(id), hash)) { "Note generation still in use" }
            shared.remove(key(id), hash)
        }
        if (body.exists() && !body.delete()) throw IOException("Cannot remove superseded body")
        val history = delta(id, hash)
        if (history.exists() && !history.delete()) throw IOException("Cannot remove superseded history")
        syncDirectory(directory(id))
    }

    override fun deleteAll(id: String) = NoteBodyLeases.guarded {
        if (sharedStorage) {
            check(!NoteBodyLeases.activeDirectory(key(id))) { "Note generation still in use" }
            shared.removeAll(key(id))
        }
        val dir = directory(id)
        if (!dir.exists()) return@guarded
        dir.listFiles()?.forEach { if (!it.delete()) throw IOException("Could not remove a private body generation") }
        if (!dir.delete()) throw IOException("Could not remove private note directory")
        syncDirectory(root)
    }

    /** Interrupted writes remain recoverable; never promote or discard them automatically. */
    fun retainedInterruptedWrites(): Int = root.listFiles()?.filter { it.isDirectory }?.sumOf { dir ->
        dir.listFiles()?.count { it.extension in listOf("tmp", "journal") } ?: 0
    } ?: 0

    private fun syncDirectory(dir: File) = fileOperations.syncDirectory(dir)

    companion object {
        fun digest(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
            return digest.digest().hex()
        }
        fun storageKey(id: String): String = sha256(id.toByteArray(Charsets.UTF_8))
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    }
}

/** Encode an existing editor value without allocating a second, whole-body byte array. */
class TextUtf8InputStream(private val text: CharSequence) : InputStream() {
    private var offset = 0
    private var buffer = ByteArray(0)
    private var position = 0
    private fun availableChunk(): Boolean {
        if (position < buffer.size) return true
        if (offset >= text.length) return false
        var end = minOf(offset + 1024, text.length)
        if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        buffer = text.subSequence(offset, end).toString().toByteArray(Charsets.UTF_8)
        offset = end
        position = 0
        return true
    }
    override fun read(): Int = if (availableChunk()) buffer[position++].toInt() and 0xff else -1
    override fun read(bytes: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
        if (len == 0) return 0
        if (!availableChunk()) return -1
        val count = minOf(len, buffer.size - position)
        buffer.copyInto(bytes, off, position, position + count)
        position += count
        return count
    }
}

/** A non-copying UTF-16 slice; the encoder materializes only its 1024-character chunks. */
class NoteTextSlice(private val text: CharSequence, private val start: Int, private val end: Int) : CharSequence {
    init { require(start in 0..end && end <= text.length) }
    override val length get() = end - start
    override fun get(index: Int): Char { require(index in 0 until length); return text[start + index] }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex in 0..endIndex && endIndex <= length)
        return NoteTextSlice(text, start + startIndex, start + endIndex)
    }
    override fun toString() = buildString(length) { for (index in start until end) append(text[index]) }
}


data class LiveEditorDescriptor(val id: String, val token: String, val base: NoteBodyRef, val bytes: Long, val revision: Long)

/** A second Activity must not overwrite the first Activity's live draft reference for the same note. */
private object LiveEditorLeases {
    private val active = HashSet<String>()
    @Synchronized fun acquire(key: String) { check(active.add(key)) { "This note is already being edited. Close its other editor before reopening." } }
    @Synchronized fun release(key: String) { active.remove(key) }
    @Synchronized fun active(key: String) = key in active
}

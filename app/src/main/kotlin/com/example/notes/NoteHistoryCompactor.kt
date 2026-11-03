package com.example.notes

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.withLock

/** One bounded consumer, independent of editor lifetime. Failure retains the complete versions. */
internal object NoteHistoryCompactor {
    private data class Request(val store: NoteBodyStore, val id: String, val previous: String, val current: String,
        val stillCurrent: () -> Boolean)
    private val queue = Channel<Request>(16)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val worker = scope.launch {
        for (request in queue) {
            try {
                val cancellation = currentCoroutineContext()
                val candidates = NoteWriteLock.mutex.withLock {
                    if (!request.stillCurrent()) emptyList() else request.store.maintenanceFiles()
                        .filter { it.directoryKey == PrivateMarkdownStore.storageKey(request.id) &&
                            it.name.endsWith(".md") && it.hash != request.current }
                        .mapNotNull { it.hash }.ifEmpty { listOf(request.previous) }
                }
                for (previous in candidates) {
                    if (!NoteWriteLock.mutex.withLock { request.stillCurrent() }) break
                    val prepared = request.store.prepareArchivePrevious(request.id, previous, request.current) {
                        cancellation.ensureActive()
                    }
                    val proceed = prepared?.use {
                        NoteWriteLock.mutex.withLock {
                            if (!request.stillCurrent()) false else { it.publish(); true }
                        }
                    } ?: true
                    if (!proceed) break
                    yield()
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: OutOfMemoryError) { return@launch } // Optional maintenance must not crash the app.
            catch (_: Exception) { /* Verified complete originals remain authoritative. */ }
        }
    }
    fun enqueue(store: NoteBodyStore, id: String, previous: String?, current: String, stillCurrent: () -> Boolean) {
        if (previous == null || previous == current || !worker.isActive) return
        // Backpressure means retaining full versions, never dropping user edits or blocking Save.
        queue.trySend(Request(store, id, previous, current, stillCurrent))
    }
}

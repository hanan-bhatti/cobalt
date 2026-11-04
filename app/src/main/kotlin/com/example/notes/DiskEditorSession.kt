package com.example.notes

import java.io.FilterInputStream
import java.io.InputStream
import java.io.Reader
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One bounded consumer serializes live edits and IO; the native View keeps only a viewport. */
class DiskEditorSession internal constructor(private val scope: CoroutineScope, private val model: DiskTextModel) {
    data class State(val revision: Long, val characters: Int, val bytes: Long, val anchor: Int, val caret: Int,
        val canUndo: Boolean, val canRedo: Boolean, val error: String? = null)
    private class Command(val editRevision: Long? = null, val action: (DiskTextModel) -> Any?,
        val completion: CompletableDeferred<Any?>? = null, val retry: Boolean = false,
        val edits: List<DiskTextModel.Replacement>? = null, val allowFailure: Boolean = false)
    private val lifetime = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Command>(64)
    private val held = ArrayDeque<Command>()
    private val outbox = ArrayDeque<Command>() // Main-owned; survives native View disposal.
    private var delivery: Job? = null
    var onBackpressure: ((Boolean) -> Unit)? = null
    private val mutable = MutableStateFlow(state())
    val state = mutable.asStateFlow()
    var onChanged: (() -> Unit)? = null
    var onFailure: ((String) -> Unit)? = null
    var captureForExport: (() -> Unit)? = null
    var deliverPending: (suspend () -> Unit)? = null // Main: finish the current native IME transaction before a barrier.
    private var nextEditRevision = model.status.revision
    @Volatile private var closing = false
    private var workerFinished = false
    private var readers = 0
    private var disposed = false
    private var retirement: (() -> Unit)? = null
    @Volatile private var failure: Throwable? = null
    private fun state(error: String? = null) = model.status.let {
        State(it.revision, it.characters, it.bytes, it.selection.anchor, it.selection.caret, it.canUndo, it.canRedo, error)
    }
    private val worker = lifetime.launch {
        try {
            for (command in queue) {
                if (command.retry) {
                    try {
                        model.recover(); failure = null
                        while (held.isNotEmpty()) apply(held.first()).also { held.removeFirst() }
                        mutable.value = state(); command.completion?.complete(Unit)
                    } catch (error: Exception) { failed(error); command.completion?.completeExceptionally(error) }
                    continue
                }
                if (failure != null && !command.allowFailure) {
                    if (command.completion != null) command.completion.completeExceptionally(failure!!)
                    else held.addLast(command)
                    continue
                }
                try { apply(command) }
                catch (cancel: CancellationException) { command.completion?.completeExceptionally(cancel); currentCoroutineContext().ensureActive() }
                catch (error: Exception) {
                    if (command.completion == null) held.addLast(command)
                    val isFatal = error is java.io.IOException || command.completion == null
                    if (isFatal) failed(error)
                    command.completion?.completeExceptionally(error)
                }
            }
        } finally {
            queue.close()
            while (true) (queue.tryReceive().getOrNull() ?: break).completion?.completeExceptionally(java.io.IOException("Editor closed"))
            synchronized(this@DiskEditorSession) { closing = true; workerFinished = true; releaseIfUnused() }
            lifetime.cancel()
        }
    }
    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            lifetime.launch(Dispatchers.Main) {
                try { deliverPending?.invoke() } finally { close() }
            }
        }
    }
    private suspend fun apply(command: Command) {
        val expected = command.editRevision
        val result = if (expected != null && model.status.revision == expected + 1) Unit else {
            if (expected != null) check(model.status.revision == expected) { "Editor operation order changed; recovery retained" }
            command.action(model)
        }
        val previousRevision = mutable.value.revision
        mutable.value = state(if (failure != null) mutable.value.error else null)
        withContext(Dispatchers.Main.immediate) {
            nextEditRevision = maxOf(nextEditRevision, model.status.revision)
            if (previousRevision != model.status.revision) onChanged?.invoke()
        }
        command.completion?.complete(result)
    }
    private suspend fun failed(error: Throwable) {
        failure = error
        val message = "Edits could not be stored. Editing is paused; retry Save or export before closing. Original and recovery files are retained."
        mutable.value = state(message)
        withContext(Dispatchers.Main.immediate) { onFailure?.invoke(message) }
    }
    /** Main only. Ownership transfers before returning, so closing the View cannot lose this edit. */
    internal fun record(edits: List<DiskTextModel.Replacement>, before: DiskTextModel.Position,
        after: DiskTextModel.Position, final: Boolean = false): Boolean {
        if (closing || (!final && (failure != null || outbox.size >= 16))) return false
        outbox.addLast(Command(nextEditRevision++, { it.replaceBatch(edits, before, after) }, edits = edits))
        if (outbox.size >= 8) onBackpressure?.invoke(true)
        if (delivery?.isActive != true) delivery = lifetime.launch(Dispatchers.Main) {
            try {
                while (outbox.isNotEmpty()) {
                    queue.send(outbox.first()); outbox.removeFirst()
                }
            } finally { delivery = null; onBackpressure?.invoke(outbox.isNotEmpty()) }
        }
        return true
    }
    private suspend fun drainNative() {
        withContext(Dispatchers.Main.immediate) { deliverPending?.invoke(); delivery?.join() }
    }
    @Suppress("UNCHECKED_CAST")
    internal suspend fun <T> execute(action: (DiskTextModel) -> T): T {
        drainNative()
        check(!closing && worker.isActive) { "Editor closed" }
        val done = CompletableDeferred<Any?>()
        val caller = currentCoroutineContext()
        queue.send(Command(action = { caller.ensureActive(); action(it) }, completion = done))
        return done.await() as T
    }
    suspend fun flush() {
        withContext(Dispatchers.Main.immediate) { delivery?.join() }
        if (failure != null) {
            val done = CompletableDeferred<Any?>()
            queue.send(Command(action = { Unit }, completion = done, retry = true)); done.await()
        }
        drainNative()
        execute { it.flush() }
    }
    internal suspend fun window(start: Int, count: Int = DiskTextModel.MAX_VIEW_CHARACTERS): String = execute {
        it.text(start.coerceIn(0, it.status.characters), count)
    }
    internal suspend fun select(anchor: Int, caret: Int) = execute { it.select(anchor, caret) }
    internal suspend fun undo() = execute { it.undo() }
    internal suspend fun redo() = execute { it.redo() }
    internal suspend fun replace(start: Int, end: Int, reader: Reader): DiskTextModel.Status {
        val cancellation = currentCoroutineContext()
        return execute { it.replace(start, end, reader, checkCancelled = { cancellation.ensureActive() }) }
    }
    suspend fun openUtf8(): InputStream = execute { retain(it.openUtf8()) }
    suspend fun openRecoveryUtf8(): InputStream {
        withContext(Dispatchers.Main.immediate) { captureForExport?.invoke(); delivery?.join() }
        check(!closing && worker.isActive)
        val done = CompletableDeferred<Any?>()
        queue.send(Command(action = { model ->
            val pending = held.filter { (it.editRevision ?: -1) >= model.status.revision }.mapNotNull { it.edits }
            retain(model.openRecoveryUtf8(pending))
        }, completion = done, allowFailure = true))
        return done.await() as InputStream
    }
    private fun retain(input: InputStream): InputStream {
        synchronized(this) { readers++ }
        return object : FilterInputStream(input) {
            private var ended = false
            override fun close() {
                if (ended) return
                ended = true
                try { super.close() } finally { synchronized(this@DiskEditorSession) { readers--; releaseIfUnused() } }
            }
        }
    }
    internal suspend fun awaitClosed() { worker.join() }
    fun retireAfterClose(action: () -> Unit) { retirement = action }
    fun close() {
        if (closing) return
        closing = true
        lifetime.launch(Dispatchers.Main) { try { delivery?.join() } finally { queue.close() } }
    }
    private fun releaseIfUnused() { if (workerFinished && readers == 0 && !disposed) { disposed = true; model.close(); retirement?.let { action -> scope.launch(NonCancellable + Dispatchers.IO) { runCatching(action) } } } }
}

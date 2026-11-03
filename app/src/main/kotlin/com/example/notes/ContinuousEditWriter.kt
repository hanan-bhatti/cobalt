package com.example.notes

import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One bounded IO consumer. Native mutations in the same UI turn form one durable transaction. */
internal class ContinuousEditWriter(private val scope: CoroutineScope, private val journal: NoteEditJournal,
    private val onFailure: () -> Unit) {
    private sealed interface Message
    private class Change(val edit: NoteEditJournal.Edit, val snapshot: Content? = null) {
        fun release() { snapshot?.release() }
    }
    private class Batch(val changes: List<Change>) : Message
    private class Barrier(val done: CompletableDeferred<Unit>) : Message
    private val queue = Channel<Message>(16)
    private val pending = ArrayList<Change>() // Main only
    private var delivery: Job? = null
    @Volatile private var failed = false
    val healthy get() = !failed && worker.isActive
    private val worker = scope.launch(Dispatchers.IO) {
        try {
            for (message in queue) when (message) {
                is Barrier -> if (failed) message.done.completeExceptionally(java.io.IOException("Recovery journal failed")) else message.done.complete(Unit)
                is Batch -> {
                    val batches = arrayListOf(message)
                    var barrier: Barrier? = null
                    try {
                        delay(8) // Coalesce nearby producer turns into one flush/fsync off Main.
                        while (batches.size < 16) {
                            when (val next = queue.tryReceive().getOrNull() ?: break) {
                                is Batch -> batches.add(next)
                                is Barrier -> { barrier = next; break }
                            }
                        }
                        if (!failed) journal.appendTransactions(batches.map { batch -> batch.changes.map { it.edit } })
                        if (failed) barrier?.done?.completeExceptionally(java.io.IOException("Recovery journal failed"))
                        else barrier?.done?.complete(Unit)
                    } catch (cancel: CancellationException) {
                        barrier?.done?.completeExceptionally(cancel); throw cancel
                    } catch (_: Exception) {
                        fail(); barrier?.done?.completeExceptionally(java.io.IOException("Recovery journal failed"))
                    } finally { batches.forEach { batch -> batch.changes.forEach { it.release() } } }
                }
            }
        } finally {
            queue.close()
            while (true) when (val pending = queue.tryReceive().getOrNull() ?: break) {
                is Batch -> pending.changes.forEach { it.release() }
                is Barrier -> pending.done.completeExceptionally(java.io.IOException("Recovery writer stopped"))
            }
        }
    }

    /** Large insertions retain a COW view; deletions carry coordinates and need no body copy. */
    fun record(content: Content, start: Int, removed: Int, inserted: CharSequence) {
        if (failed) return
        if (pending.size >= 64) { fail(); return }
        var snapshot: Content? = null
        val stable = if (inserted.length <= 8192) inserted.toString() else {
            val copy = content.copyTextShallow().also { snapshot = it }
            NoteTextSlice(copy, start, start + inserted.length)
        }
        pending.add(Change(NoteEditJournal.Edit(start, removed, stable), snapshot))
        if (delivery?.isActive != true) delivery = scope.launch(Dispatchers.Main) { deliver() }
    }
    private fun deliver() {
        if (pending.isEmpty()) return
        val batch = Batch(pending.toList()); pending.clear()
        if (failed || !queue.trySend(batch).isSuccess) { batch.changes.forEach { it.release() }; fail() }
    }
    private fun fail() { if (!failed) { failed = true; onFailure() } }
    suspend fun flush() {
        withContext(Dispatchers.Main.immediate) { delivery?.cancel(); deliver() }
        check(!failed) { "Recovery journal failed; save a full checkpoint" }
        val barrier = Barrier(CompletableDeferred())
        queue.send(barrier); barrier.done.await()
        check(!failed) { "Recovery journal failed; save a full checkpoint" }
    }
    suspend fun close() {
        withContext(Dispatchers.Main.immediate) { delivery?.cancel(); deliver() }
        queue.close(); worker.join()
    }
}

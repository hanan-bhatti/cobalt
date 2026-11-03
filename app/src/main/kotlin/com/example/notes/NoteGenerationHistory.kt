package com.example.notes

data class NoteGeneration(val hash: String, val bytes: Long, val preview: String)

/** Undo references verified immutable files, never full-body Strings. */
class NoteGenerationHistory {
    @Synchronized private fun hashes(): Set<String> = (undo + redo).flatMap { listOf(it.before.hash, it.after.hash) }.toSet()
    companion object {
        private val instances = java.util.WeakHashMap<NoteGenerationHistory, Unit>()
        private val recent = HashMap<String, Long>()
        /** Bridge the DB-commit / Main-thread history-registration handoff. */
        fun protectReplacement(hash: String?) {
            if (hash != null) synchronized(instances) { recent[hash] = (System.nanoTime() / 1_000_000) }
        }
        fun protectedHashes(): Set<String> {
            val live = synchronized(instances) { instances.keys.toList() }
            val handoffs = synchronized(instances) {
                val now = (System.nanoTime() / 1_000_000)
                recent.entries.removeAll { now - it.value > 60_000 }
                recent.keys.toSet()
            }
            return live.flatMap { it.hashes() }.toSet() + handoffs
        }
    }
    data class Change(val before: NoteGeneration, val after: NoteGeneration)
    private val undo = ArrayDeque<Change>()
    private val redo = ArrayDeque<Change>()
    init { synchronized(instances) { instances[this] = Unit } }
    val undoChange get() = undo.lastOrNull()
    val redoChange get() = redo.lastOrNull()
    @Synchronized fun record(before: NoteGeneration, after: NoteGeneration) {
        if (before.hash == after.hash) return
        undo.addLast(Change(before, after)); redo.clear()
        if (undo.size > 10) undo.removeFirst()
    }
    @Synchronized fun didUndo(change: Change) { check(undo.lastOrNull() == change); undo.removeLast(); redo.addLast(change) }
    @Synchronized fun didRedo(change: Change) { check(redo.lastOrNull() == change); redo.removeLast(); undo.addLast(change) }
}

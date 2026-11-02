package com.example.notes

data class NoteGeneration(val hash: String, val bytes: Long, val preview: String)

/** Undo references verified immutable files, never full-body Strings. */
class NoteGenerationHistory {
    data class Change(val before: NoteGeneration, val after: NoteGeneration)
    private val undo = ArrayDeque<Change>()
    private val redo = ArrayDeque<Change>()
    val undoChange get() = undo.lastOrNull()
    val redoChange get() = redo.lastOrNull()
    fun record(before: NoteGeneration, after: NoteGeneration) {
        if (before.hash == after.hash) return
        undo.addLast(Change(before, after)); redo.clear()
        if (undo.size > 10) undo.removeFirst()
    }
    fun didUndo(change: Change) { check(undo.lastOrNull() == change); undo.removeLast(); redo.addLast(change) }
    fun didRedo(change: Change) { check(redo.lastOrNull() == change); redo.removeLast(); undo.addLast(change) }
}

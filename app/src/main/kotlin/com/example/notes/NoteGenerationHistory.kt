package com.example.notes

data class NoteGeneration(val hash: String, val bytes: Long, val preview: String)

/** Undo references verified immutable files, never full-body Strings. */
class NoteGenerationHistory {
    val changes = kotlinx.coroutines.flow.MutableStateFlow(0L)
    private fun changed() { changes.value = changes.value + 1 }
    @Synchronized private fun hashes(): Set<String> = (undo + redo).flatMap { listOf(it.before.hash, it.after.hash) }.toSet()
    companion object {
        private val notes = HashMap<String, NoteGenerationHistory>()
        @Synchronized fun forNote(id: String) = notes.getOrPut(id) { NoteGenerationHistory() }
        @Synchronized fun clearNote(id: String) { notes.remove(id)?.clear() }
        fun clearSavedNavigation() {
            synchronized(instances) { instances.keys.toList().forEach { it.clear() }; recent.clear() }
        }
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
    val undoChange @Synchronized get() = undo.lastOrNull()
    val redoChange @Synchronized get() = redo.lastOrNull()
    @Synchronized fun record(before: NoteGeneration, after: NoteGeneration) {
        if (before.hash == after.hash) return
        if (undo.lastOrNull() == Change(before, after) && redo.isEmpty()) return
        undo.addLast(Change(before, after)); redo.clear()
        if (undo.size > 10) undo.removeFirst()
        changed()
    }
    @Synchronized fun didUndo(change: Change) { check(undo.lastOrNull() == change); undo.removeLast(); redo.addLast(change); changed() }
    @Synchronized fun didRedo(change: Change) { check(redo.lastOrNull() == change); redo.removeLast(); undo.addLast(change); changed() }
    @Synchronized fun clear() { undo.clear(); redo.clear(); changed() }
    @Synchronized internal fun encode(): String {
        fun generation(g: NoteGeneration) = org.json.JSONObject().put("hash", g.hash).put("bytes", g.bytes).put("preview", NoteTextMetadata.preview(g.preview))
        fun changes(stack: ArrayDeque<Change>) = org.json.JSONArray().also { array -> stack.forEach {
            array.put(org.json.JSONObject().put("before", generation(it.before)).put("after", generation(it.after))) } }
        return org.json.JSONObject().put("version", 1).put("undo", changes(undo)).put("redo", changes(redo)).toString()
    }
    @Synchronized internal fun decode(state: String) {
        val json = org.json.JSONObject(state); check(json.getInt("version") == 1)
        fun generation(g: org.json.JSONObject): NoteGeneration {
            val hash = g.getString("hash"); val bytes = g.getLong("bytes")
            check(hash.matches(Regex("[0-9a-f]{64}")) && bytes >= 0)
            return NoteGeneration(hash, bytes, NoteTextMetadata.preview(g.getString("preview")))
        }
        fun changes(key: String): ArrayDeque<Change> {
            val array = json.getJSONArray(key); check(array.length() <= 10)
            return ArrayDeque<Change>().also { stack -> repeat(array.length()) { i -> val c = array.getJSONObject(i)
                stack.addLast(Change(generation(c.getJSONObject("before")), generation(c.getJSONObject("after")))) } }
        }
        val previous = changes("undo"); val next = changes("redo")
        undo.clear(); undo.addAll(previous); redo.clear(); redo.addAll(next)
        changed()
    }
}

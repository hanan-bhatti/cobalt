package com.example.notes

import com.example.data.local.AppDatabase

/** Called inside the SAME metadata transaction; Undo survives a crash after head commit. */
internal object NoteHistoryPersistence {
    fun read(database: AppDatabase, id: String): String? = database.openHelper.writableDatabase.query(
        "SELECT state FROM note_history_state WHERE noteId=?", arrayOf(id)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun change(database: AppDatabase, id: String, before: NoteGeneration?, after: NoteGeneration, direction: Int = 0): String {
        val history = NoteGenerationHistory()
        read(database, id)?.let(history::decode)
        if (before != null && before.hash != after.hash) {
            if (direction == 0) history.record(before, after)
            else {
                val change = requireNotNull(if (direction < 0) history.undoChange else history.redoChange) { "Saved Undo changed; reopen the note" }
                check((if (direction < 0) change.after.hash else change.before.hash) == before.hash &&
                    (if (direction < 0) change.before.hash else change.after.hash) == after.hash) { "Saved Undo changed; original retained" }
                if (direction < 0) history.didUndo(change) else history.didRedo(change)
            }
        }
        return history.encode().also { state -> database.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO note_history_state(noteId,state) VALUES(?,?)", arrayOf(id,state)) }
    }
    fun publish(database: AppDatabase, id: String) {
        val history = NoteGenerationHistory.forNote(id)
        val state = read(database,id)
        if (state == null) history.clear()
        else try { history.decode(state) }
        catch (_: Exception) {
            // Damaged navigation metadata must not prevent opening verified user text.
            // Preserve the durable row and its versions for explicit recovery/cleanup.
            history.clear()
            android.util.Log.e("NoteHistory", "Saved navigation unavailable; note content and history retained")
        }
    }
    fun protectedHashes(database: AppDatabase): Set<String> = database.openHelper.writableDatabase.query("SELECT state FROM note_history_state").use { cursor ->
        buildSet {
            while(cursor.moveToNext()) {
                val json=org.json.JSONObject(cursor.getString(0));check(json.getInt("version")==1)
                for(key in listOf("undo","redo")) {
                    val entries=json.getJSONArray(key);check(entries.length()<=10)
                    repeat(entries.length()) { i -> for(side in listOf("before","after")) {
                        val hash=entries.getJSONObject(i).getJSONObject(side).getString("hash")
                        check(hash.matches(Regex("[0-9a-f]{64}")));add(hash)
                    } }
                }
            }
        }
    }
}

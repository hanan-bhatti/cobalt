package com.example.notes

/** Prevent explicit maintenance from unlinking a generation while a reader owns its handle. */
internal object NoteBodyLeases {
    private val readers = HashMap<Pair<String, String>, Int>()
    @Synchronized fun acquire(key: String, hash: String) { val id = key to hash; readers[id] = (readers[id] ?: 0) + 1 }
    @Synchronized fun release(key: String, hash: String) {
        val id = key to hash; val count = readers[id] ?: return
        if (count <= 1) readers.remove(id) else readers[id] = count - 1
    }
    @Synchronized fun <T> guarded(action: () -> T): T = action()
    @Synchronized fun active(key: String, hash: String) = (readers[key to hash] ?: 0) > 0
}

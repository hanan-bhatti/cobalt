package com.example.notes

/** Opaque private-storage identity, never an arbitrary filesystem path. */
data class NoteStorageFile(val directoryKey: String, val name: String, val bytes: Long, val modified: Long) {
    val hash: String? = name.removeSuffix(".md").takeIf { name.endsWith(".md") && it.matches(Regex("[0-9a-f]{64}")) }
    val isRetiredJournal = name.endsWith(".edits.retired")
}

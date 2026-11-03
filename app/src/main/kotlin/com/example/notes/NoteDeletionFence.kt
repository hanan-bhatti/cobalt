package com.example.notes

import android.content.Context

/** Tiny durable tombstones reject late autosaves after a permanent deletion. IO only. */
internal class NoteDeletionFence(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("permanently_deleted_notes", Context.MODE_PRIVATE)
    fun deleted(id: String): Boolean = prefs.getBoolean(PrivateMarkdownStore.storageKey(id), false)
    fun requireAlive(id: String) { check(!deleted(id)) { "This item was permanently deleted; a late save cannot recreate it." } }
    fun mark(ids: List<String>) = NoteBodyLeases.guarded {
        val update = prefs.edit()
        ids.forEach { update.putBoolean(PrivateMarkdownStore.storageKey(it), true) }
        check(update.commit()) { "Could not record permanent deletion; retained for retry" }
    }
}

package com.example.notes

object TrashPolicy {
    const val RETENTION_DAYS = 30
    const val RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000L
    const val UNDO_GRACE_MS = 30_000L
    fun deletionMessage(enabled: Boolean, plural: Boolean = false): String {
        val subject = if (plural) "these items" else "this item"
        return if (enabled) "Move $subject to Trash? You can restore them for $RETENTION_DAYS days, or delete them forever from Storage → Trash."
        else "Delete $subject? Trash is off. Undo is available briefly; after that the items and their saved versions and recovery files are permanently removed."
    }
    fun expiresAt(now: Long, enabled: Boolean): Long = Math.addExact(now, if (enabled) RETENTION_MS else UNDO_GRACE_MS)
    fun daysRemaining(now: Long, expiresAt: Long): Int =
        ((expiresAt - now).coerceIn(0, RETENTION_MS).let { (it + 86_399_999) / 86_400_000 }).toInt()
}

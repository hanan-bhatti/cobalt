package com.example.notes

class NoteSizeExceededException : java.io.IOException("Note exceeds 55 MB. Split it into multiple notes.")

/** Byte limits use decimal MB. INLINE exists only to identify pre-migration rows. */
object NoteSizePolicy {
    const val TIER_A_MAX_BYTES = 200_000L
    const val TIER_B_MAX_BYTES = 5_000_000L
    const val SOFT_MAX_BYTES = 50_000_000L
    const val IMPORT_MAX_BYTES = 55_000_000L
    const val SPLIT_TARGET_BYTES = 50_000_000L
    const val PREVIEW_CHARACTERS = 200
    const val DECORATED_LINK_LIMIT = 300
    const val IO_CHUNK_BYTES = 16 * 1024
    const val SEARCH_PREFIX_BYTES = 32 * 1024
    const val MARKDOWN_BLOCK_CHARACTERS = 32 * 1024
    const val MARKDOWN_BLOCK_LINES = 500
    const val MARKDOWN_MAX_DEPTH = 32
    const val URL_CHARACTERS = 8192
    const val EDITOR_UNDO_PRESSURE_BYTES = 8 * 1024 * 1024L
    const val MARKDOWN_CACHE_BYTES = 16 * 1024 * 1024L
    const val MARKDOWN_CACHE_BLOCKS = 16_384
    const val MARKDOWN_PREFETCH_BLOCKS = 256
    const val DOCK_RECENT_ITEMS = 15

    enum class Tier { A, B, C }
    enum class Location { INLINE, FILE }

    fun tier(byteLength: Long): Tier {
        require(byteLength >= 0)
        return when {
            byteLength < TIER_A_MAX_BYTES -> Tier.A
            byteLength < TIER_B_MAX_BYTES -> Tier.B
            else -> Tier.C
        }
    }

    /** Existing content is never refused or truncated, regardless of its size. */
    fun accepts(byteLength: Long, existing: Boolean = false): Boolean {
        require(byteLength >= 0)
        return existing || byteLength <= IMPORT_MAX_BYTES
    }

    fun needsWarning(byteLength: Long): Boolean {
        require(byteLength >= 0)
        return byteLength > SOFT_MAX_BYTES
    }

    fun acceptsEdit(byteLength: Long, storedByteLength: Long): Boolean {
        require(byteLength >= 0 && storedByteLength >= 0)
        return byteLength <= maxOf(IMPORT_MAX_BYTES, storedByteLength)
    }

    /** Every note, including an empty or one-line note, uses a Markdown file. */
    fun location(byteLength: Long): Location {
        require(byteLength >= 0)
        return Location.FILE
    }

    /** Formats raw byte counts into human-readable B, KB, MB, or GB strings. */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return if (digitGroups == 0) "$bytes B"
        else String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

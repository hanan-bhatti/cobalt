package com.example.notes

import java.io.File

/** Cache cleanup cannot unlink active history/recovery scratch files. Process death clears leases. */
internal object NoteCacheWork {
    private val active = HashMap<String, Int>()
    @Synchronized fun acquire(directory: File) { val key = directory.absolutePath; active[key] = (active[key] ?: 0) + 1 }
    @Synchronized fun release(directory: File) {
        val key = directory.absolutePath
        val count = active[key] ?: return
        if (count <= 1) active.remove(key) else active[key] = count - 1
    }
    fun <T> using(directory: File, block: () -> T): T {
        acquire(directory)
        return try { block() } finally { release(directory) }
    }
    private fun candidates(directory: File): List<File> = directory.listFiles().orEmpty().filter {
        it.isFile && (it.name.endsWith(".history.tmp") ||
            (it.name.startsWith("original-") || it.name.startsWith("added-")) && it.name.endsWith(".scratch"))
    }
    @Synchronized fun clearableBytes(directory: File): Long =
        if ((active[directory.absolutePath] ?: 0) > 0) 0 else candidates(directory).sumOf { it.length() }
    @Synchronized fun clear(directory: File) {
        if ((active[directory.absolutePath] ?: 0) > 0) return
        candidates(directory).forEach { check(it.delete()) { "A temporary file could not be removed" } }
    }
}

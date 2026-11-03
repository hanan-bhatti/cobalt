package com.example.notes

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NoteCacheWorkTest {
    @Test fun clearingCacheProtectsActiveWorkAndUnknownFiles() {
        val dir = Files.createTempDirectory("cobalt-cache-test-").toFile()
        try {
            val scratch = java.io.File(dir, "original-one.scratch").also { it.writeText("recoverable scratch") }
            val history = java.io.File(dir, "one.history.tmp").also { it.writeText("temporary history") }
            val unknown = java.io.File(dir, "pending-recording.mp4").also { it.writeText("recording") }
            assertEquals(scratch.length() + history.length(), NoteCacheWork.clearableBytes(dir))
            NoteCacheWork.using(dir) {
                assertEquals(0L, NoteCacheWork.clearableBytes(dir))
                NoteCacheWork.clear(dir)
                assertTrue(scratch.exists() && history.exists())
            }
            NoteCacheWork.clear(dir)
            assertFalse(scratch.exists() || history.exists())
            assertTrue(unknown.exists())
        } finally { dir.deleteRecursively() }
    }
}

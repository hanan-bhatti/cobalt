package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class NoteSizePolicyTest {
    @Test fun importToleranceAndExistingData() {
        for (size in listOf(49_900_000L, 50_000_000L, 54_900_000L, 55_000_000L)) {
            assertTrue(NoteSizePolicy.accepts(size))
        }
        assertFalse(NoteSizePolicy.accepts(55_100_000L))
        assertTrue(NoteSizePolicy.accepts(100_000_000L, existing = true))
        assertFalse(NoteSizePolicy.needsWarning(50_000_000L))
        assertTrue(NoteSizePolicy.needsWarning(50_000_001L))
    }

    @Test fun tierBoundaries() {
        assertEquals(NoteSizePolicy.Tier.A, NoteSizePolicy.tier(199_999))
        assertEquals(NoteSizePolicy.Tier.B, NoteSizePolicy.tier(200_000))
        assertEquals(NoteSizePolicy.Tier.B, NoteSizePolicy.tier(4_999_999))
        assertEquals(NoteSizePolicy.Tier.C, NoteSizePolicy.tier(5_000_000))
        assertEquals(NoteSizePolicy.Tier.C, NoteSizePolicy.tier(Long.MAX_VALUE))
    }

    @Test fun everyNoteUsesAFile() {
        for (size in listOf(0L, 1L, 128_000L, 256_000L, 55_000_000L, Long.MAX_VALUE)) {
            assertEquals(NoteSizePolicy.Location.FILE, NoteSizePolicy.location(size))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeSizeIsRejected() { NoteSizePolicy.tier(-1) }
}

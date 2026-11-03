package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class TrashPolicyTest {
    @Test fun retentionAndRoundedDaysHaveExactBoundaries() {
        val start = 1_000_000L
        val end = TrashPolicy.expiresAt(start, true)
        assertEquals(30, TrashPolicy.daysRemaining(start, end))
        assertEquals(1, TrashPolicy.daysRemaining(end - 1, end))
        assertEquals(0, TrashPolicy.daysRemaining(end, end))
        assertEquals(0, TrashPolicy.daysRemaining(end + 1, end))
        assertEquals(start + 30_000, TrashPolicy.expiresAt(start, false))
        assertEquals(30, TrashPolicy.daysRemaining(0, Long.MAX_VALUE))
    }
}

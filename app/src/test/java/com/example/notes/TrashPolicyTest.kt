package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class TrashPolicyTest {
    @Test fun warningMatchesRetentionAndUndoOnlyDeletion() {
        assertTrue(TrashPolicy.deletionMessage(true).contains("30 days"))
        assertFalse(TrashPolicy.deletionMessage(true).contains("cannot be undone"))
        assertTrue(TrashPolicy.deletionMessage(false).contains("Trash is off"))
        assertTrue(TrashPolicy.deletionMessage(false).contains("Undo is available briefly"))
        assertTrue(TrashPolicy.deletionMessage(true, true).contains("these items"))
    }
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

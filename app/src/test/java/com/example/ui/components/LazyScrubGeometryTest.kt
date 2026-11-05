package com.example.ui.components

import org.junit.Assert.*
import org.junit.Test

class LazyScrubGeometryTest {
    @Test fun thousandNotesHaveStableMonotonicTargetsAndReachBothEnds() {
        for (lanes in listOf(1, 2)) {
            val geometry = LazyScrubGeometry(1_000, 12, lanes, 180)
            assertEquals(0 to 0, geometry.target(0f))
            assertEquals(999 to 0, geometry.target(1f))
            var previous = -1L
            repeat(1_001) { sample ->
                val (index, offset) = geometry.target(sample / 1_000f)
                val position = index.toLong() / lanes * 180 + offset
                assertTrue(position >= previous)
                assertTrue(index in 0..999)
                assertTrue(offset in 0..180)
                previous = position
            }
        }
    }
    @Test fun emptyShortAndInvalidInputsStayBounded() {
        assertEquals(0 to 0, LazyScrubGeometry(0, 0, 2, 100).target(1f))
        val geometry = LazyScrubGeometry(3, 8, 2, 100)
        assertEquals(0 to 0, geometry.target(0.5f))
        assertEquals(0 to 0, geometry.target(Float.NaN))
        assertEquals(0 to 0, geometry.target(-2f))
        assertEquals(2 to 0, geometry.target(2f))
    }
}

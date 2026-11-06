package com.example.ui.components

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class RadialFabGeometryTest {
    @Test fun sixStableActionCentersSelectTheirOwnAction() {
        repeat(6) { index ->
            val angle = Math.toRadians(180.0 + 18 * index)
            assertEquals(index, radialFabIndex((cos(angle) * 192).toFloat(), (sin(angle) * 192).toFloat(), 192f, 6))
        }
    }
    @Test fun draggingIntoUnrelatedDirectionsOrBackToCenterCancels() {
        for ((x,y) in listOf(192f to 0f, 0f to 192f, 120f to 120f, -20f to -20f, -400f to -400f)) {
            assertEquals(-1, radialFabIndex(x,y,192f,6))
        }
        assertEquals(-1, radialFabIndex(Float.NaN,-192f,192f,6))
        assertEquals(-1, radialFabIndex(-192f,0f,192f,0))
    }
    @Test fun neighboring48DpTargetsDoNotOverlap() {
        val centerSpacing = 2 * 192 * sin(Math.toRadians(18.0 / 2))
        assertTrue(centerSpacing >= 48)
    }
    @Test fun halfTargetToleranceAtEdgesDoesNotWrapAroundToAnUnrelatedAction() {
        assertEquals(0, radialFabIndex(-192f,10f,192f,6))
        assertEquals(5, radialFabIndex(10f,-192f,192f,6))
        assertEquals(-1, radialFabIndex(-130f,130f,192f,6))
    }
}

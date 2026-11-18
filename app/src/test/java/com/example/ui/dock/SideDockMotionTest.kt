package com.example.ui.dock

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class SideDockMotionTest {
    private data class SpringCase(val preset: String, val strength: Float, val damping: Float, val closeStrength: Float, val closeDamping: Float)

    /** Independent unit-mass damped-spring solution, rather than an animation approximation. */
    private fun response(milliseconds: Long, strength: Float, damping: Float): Double {
        val t = milliseconds / 1000.0
        val omega = sqrt(strength.toDouble())
        val zeta = damping.toDouble()
        if (zeta == 1.0) return 1.0 - (1.0 + omega * t) * exp(-omega * t)
        val root = sqrt(1.0 - zeta * zeta)
        val phase = omega * root * t
        return 1.0 - exp(-zeta * omega * t) * (cos(phase) + zeta / root * sin(phase))
    }

    @Test
    fun bothDirectionsFollowThePhysicalSpringResponse() {
        val originals = listOf(
            SpringCase("Smooth", 430f, 1f, 650f, 1f),
            SpringCase("Fluid", 170f, 0.88f, 420f, 1f),
            SpringCase("Bouncy", 380f, 0.52f, 700f, 0.85f),
            SpringCase("Snappy", 900f, 1f, 1000f, 1f),
            SpringCase("Custom", 210f, 0.53f, 270f, 0.63f)
        )
        for (original in originals) {
            val physics = sideDockPhysics(original.preset, original.strength, original.damping)
            val opening = TargetBasedAnimation(physics.openingSpec(), Float.VectorConverter, 0f, 1f)
            val closing = TargetBasedAnimation(physics.closingSpec(), Float.VectorConverter, 1f, 0f)
            for (ms in listOf(0L, 32L, 64L, 120L, 200L, 400L)) {
                val playTime = ms * 1_000_000
                // Compose snaps to the target once its spring meets the visibility threshold.
                val expectedOpening = if (playTime >= opening.durationNanos) 1.0
                    else response(ms, original.strength, original.damping)
                val expectedClosing = if (playTime >= closing.durationNanos) 0.0
                    else 1.0 - response(ms, original.closeStrength, original.closeDamping)
                assertEquals("${original.preset} opening at $ms ms", expectedOpening, opening.getValueFromNanos(playTime).toDouble(), 0.0005)
                assertEquals("${original.preset} closing at $ms ms", expectedClosing, closing.getValueFromNanos(playTime).toDouble(), 0.0005)
            }
        }
    }

    @Test
    fun presetsHavePerceptiblyDifferentRiseAndRebound() {
        fun samples(preset: String) = TargetBasedAnimation(sideDockPhysics(preset).openingSpec(), Float.VectorConverter, 0f, 1f)
            .let { animation -> (0L..1200L).map { animation.getValueFromNanos(it * 1_000_000) } }
        val smooth = samples("Smooth")
        val fluid = samples("Fluid")
        val bouncy = samples("Bouncy")
        val snappy = samples("Snappy")
        fun rise90(values: List<Float>) = values.indexOfFirst { it >= 0.9f }
        assertTrue("Fluid must visibly linger longer than Smooth", rise90(fluid) - rise90(smooth) >= 50)
        assertTrue("Snappy must arrive sooner than Smooth", rise90(smooth) - rise90(snappy) >= 40)
        assertTrue("Fluid and Smooth must separate during the visible entrance", smooth[150] - fluid[150] >= 0.15f)
        assertTrue("Bouncy must have a controlled visible rebound", bouncy.max() in 1.12f..1.18f)
        assertTrue("Fluid must finish gently", fluid.max() <= 1.01f)
        assertTrue("Smooth must never reverse direction", smooth.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("Snappy must never reverse direction", snappy.zipWithNext().all { (a, b) -> b >= a })
        for (preset in listOf("Smooth", "Fluid", "Bouncy", "Snappy")) {
            val physics = sideDockPhysics(preset)
            val opening = TargetBasedAnimation(physics.openingSpec(), Float.VectorConverter, 0f, 1f)
            val closing = TargetBasedAnimation(physics.closingSpec(), Float.VectorConverter, 1f, 0f)
            assertTrue("$preset exit should settle before its entrance", closing.durationNanos < opening.durationNanos)
        }
    }

    @Test
    fun instantBypassesBothSprings() {
        val physics = sideDockPhysics("Instant")
        val opening = TargetBasedAnimation(physics.openingSpec(), Float.VectorConverter, 0f, 1f)
        val closing = TargetBasedAnimation(physics.closingSpec(), Float.VectorConverter, 1f, 0f)
        assertEquals(0L, opening.durationNanos)
        assertEquals(0L, closing.durationNanos)
        assertEquals(1f, opening.getValueFromNanos(0L), 0f)
        assertEquals(0f, closing.getValueFromNanos(0L), 0f)
    }

    @Test
    fun criticalDampingHasNoOvershootAndBouncyHasVisibleOvershoot() {
        val critical = TargetBasedAnimation(sideDockPhysics("Custom", 340f, 1f).openingSpec(), Float.VectorConverter, 0f, 1f)
        val bouncy = TargetBasedAnimation(sideDockPhysics("Bouncy").openingSpec(), Float.VectorConverter, 0f, 1f)
        val samples = (0L..1200L step 8L).map { it * 1_000_000 }
        assertTrue(samples.all { critical.getValueFromNanos(it) <= 1f })
        assertTrue(samples.maxOf { bouncy.getValueFromNanos(it) } > 1.10f)
    }

    @Test
    fun morphKeepsTheSpringOvershootAndReturnsToThePhysicalHandle() {
        val card = sideDockCardGeometry(997, false)
        val closed = sideDockMorph(0f, card, 100, 6, "Right")
        assertEquals(6f, card.widthDp * closed.scaleX, 0.001f)
        assertEquals(100f, card.heightDp * closed.scaleY, 0.001f)
        assertEquals(12f, closed.translationXDp, 0f)
        val overshoot = sideDockMorph(1.10f, card, 100, 6, "Right")
        assertTrue(overshoot.scaleX > 1f && overshoot.scaleY > 1f)
        assertEquals(24f, overshoot.innerCornerDp, 0f)
        assertEquals(1f, overshoot.contentAlpha, 0f)
        assertEquals(0f, sideDockMorph(0f, card, 100, 6, "Right", isDismissing = true).containerAlpha, 0f)
    }

    @Test
    fun previewHandleCenterMatchesTheMeasuredAndroidWindowFrame() {
        // Emulator: 2992 px screen, density 3, 72 px navigation bar, 159 px status bar.
        val screenHeight = 2992 / 3f
        assertEquals(1209 / 3f, sideDockHandleCenterYDp(screenHeight, -251 / 3f, 100,
            navigationBottomDp = 24f, statusTopDp = 53f), 0.001f)
        assertEquals(351 / 3f, sideDockHandleCenterYDp(screenHeight, -1109 / 3f, 100,
            navigationBottomDp = 24f, statusTopDp = 53f), 0.001f)
        assertEquals(1277 / 3f, sideDockHandleCenterYDp(screenHeight, -183 / 3f, 100,
            navigationBottomDp = 24f, statusTopDp = 53f), 0.001f)
        // WindowManager keeps tall handles inside the visible frame at both extremes.
        assertEquals(153f, sideDockHandleCenterYDp(screenHeight, -1256 / 3f, 200,
            navigationBottomDp = 24f, statusTopDp = 53f), 0.001f)
        assertEquals(873.3333f, sideDockHandleCenterYDp(screenHeight, 1256 / 3f, 200,
            navigationBottomDp = 24f, statusTopDp = 53f), 0.001f)
    }
}

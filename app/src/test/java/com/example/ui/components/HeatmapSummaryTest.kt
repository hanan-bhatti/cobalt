package com.example.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class HeatmapSummaryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun showSummary(width: Dp, fontScale: Float = 1f) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    CompactHeatmapSummary(507, 8, modifier = Modifier.width(width).testTag("summary"))
                }
            }
        }
    }

    @Test
    fun wideSummaryPlacesStatsAndLegendOnOneRow() {
        showSummary(380.dp)
        val stats = composeTestRule.onNodeWithTag("heatmap_summary_stats").fetchSemanticsNode().boundsInRoot
        val legend = composeTestRule.onNodeWithTag("heatmap_summary_legend").fetchSemanticsNode().boundsInRoot
        assertEquals(stats.top, legend.top, 1f)
        assertTrue(legend.left >= stats.right)
    }

    @Test
    fun narrowSummaryWrapsTheWholeLegendBelowStats() {
        showSummary(230.dp)
        assertLegendWrapped()
    }

    @Test
    fun largerTextWrapsWithoutClipping() {
        showSummary(300.dp, fontScale = 1.3f)
        assertLegendWrapped()
    }

    private fun assertLegendWrapped() {
        val summary = composeTestRule.onNodeWithTag("summary").fetchSemanticsNode().boundsInRoot
        val stats = composeTestRule.onNodeWithTag("heatmap_summary_stats").fetchSemanticsNode().boundsInRoot
        val legend = composeTestRule.onNodeWithTag("heatmap_summary_legend").fetchSemanticsNode().boundsInRoot
        assertTrue(legend.top >= stats.bottom)
        assertEquals(stats.left, legend.left, 1f)
        assertTrue(stats.right <= summary.right + 1f)
        assertTrue(legend.right <= summary.right + 1f)
    }
}

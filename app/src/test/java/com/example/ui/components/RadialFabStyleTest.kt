package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class RadialFabStyleTest {
    @Test fun foregroundMaintainsContrastAcrossLightDarkAndCategoryFills() {
        val fills = listOf(Color.Black, Color.White, Color(0xFFFF9800), Color(0xFF42A5F5),
            Color(0xFFAB47BC), Color(0xFF26A69A), Color(0xFF303136), Color(0xFFE4E1E8))
        for (fill in fills) {
            val foreground = radialFabIconColor(fill)
            val light = maxOf(fill.luminance(), foreground.luminance())
            val dark = minOf(fill.luminance(), foreground.luminance())
            assertTrue("Insufficient contrast on $fill", (light + 0.05f) / (dark + 0.05f) >= 4.5f)
        }
    }
}

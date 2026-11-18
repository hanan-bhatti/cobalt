package com.example.ui.screens

import android.view.View
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class AppearancePreviewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var rootView: View

    private fun showPreview(fontScale: Float = 1f) {
        composeTestRule.setContent {
            rootView = LocalView.current.rootView
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    StaticHomeAndDockPreview(
                        useBlur = false,
                        blurRadius = 25,
                        blurOpacity = 0.3f,
                        modifier = Modifier.width(380.dp).testTag("static_preview")
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertRatios(dockRatio: Float) {
        val home = composeTestRule.onNodeWithTag("appearance_home_preview").fetchSemanticsNode().boundsInRoot
        val dock = composeTestRule.onNodeWithTag("appearance_dock_preview").fetchSemanticsNode().boundsInRoot
        val homeRatio = rootView.width.toFloat() / rootView.height
        assertTrue("Home keeps the full device aspect ratio", abs(home.width - home.height * homeRatio) <= 1f)
        assertTrue("Dock keeps its resting card aspect ratio", abs(dock.width - dock.height * dockRatio) <= 1f)
        assertTrue("Preview is scaled down", home.height < rootView.height / 2f)
    }

    @Test
    fun portraitPreviewsKeepRealProportionsAndRemainStatic() {
        showPreview()
        assertRatios(280f / 460f)
        val first = pixels()
        composeTestRule.mainClock.advanceTimeBy(5_000)
        composeTestRule.waitForIdle()
        assertArrayEquals("Preview does not animate", first, pixels())
    }

    @Test
    @Config(qualifiers = "w997dp-h448dp-land", sdk = [34])
    fun landscapePreviewsKeepRealProportionsWithLargerText() {
        showPreview(fontScale = 1.3f)
        assertRatios(1f)
    }

    private fun pixels(): IntArray {
        val bitmap = composeTestRule.onNodeWithTag("static_preview").captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }
}

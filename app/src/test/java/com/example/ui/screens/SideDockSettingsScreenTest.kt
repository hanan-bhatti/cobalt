package com.example.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.ui.dock.sideDockPhysics
import com.example.ui.dock.sideDockMorph
import com.example.ui.dock.sideDockCardGeometry
import com.example.ui.dock.SideDockMorphSurface
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class SideDockSettingsScreenTest {
    @get:Rule val composeTestRule = createComposeRule()
    private var settings by mutableStateOf(SideDockPreviewSettings())
    private var enabled by mutableStateOf(true)
    private var overlayAllowed by mutableStateOf(true)
    private var animationOpened = false
    private var permissionRequests = 0

    @Before
    fun settleMotionForLayoutAndAccessibilityChecks() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun show(animation: Boolean = false, fontScale: Float = 1f) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    if (animation) SideDockAnimationSettingsContent(
                        settings, enabled, overlayAllowed,
                        onEnabledChanged = { enabled = it }, onRequestPermission = { permissionRequests++ },
                        onPresetChanged = {
                            val physics = sideDockPhysics(it)
                            settings = settings.copy(preset = it, stiffness = physics.openingStiffness, damping = physics.openingDamping)
                        },
                        onCustomChanged = { strength, damping -> settings = settings.copy(preset = "Custom", stiffness = strength, damping = damping) },
                        onOpenDock = {}, onNavigateBack = {}
                    ) else SideDockSettingsContent(
                        settings, enabled, overlayAllowed, onEnabledChanged = { enabled = it }, onRequestPermission = { permissionRequests++ },
                        onSideChanged = { settings = settings.copy(side = it) },
                        onPositionChanged = { settings = settings.copy(position = it) },
                        onHeightChanged = { settings = settings.copy(heightDp = it) },
                        onThicknessChanged = { settings = settings.copy(thicknessDp = it) },
                        onOpacityChanged = { settings = settings.copy(opacity = it) },
                        onNavigateToAnimation = { animationOpened = true }, onNavigateBack = {}
                    )
                }
            }
        }
    }

    @Test
    fun disablingTheDockLeavesOnlyTheEnableSwitch() {
        show()
        composeTestRule.onNodeWithTag("side_dock_enable").performScrollTo().performClick()
        assertFalse(enabled)
        composeTestRule.onNodeWithTag("side_dock_enable").assertIsDisplayed()
        assertControlsHidden()
        composeTestRule.onNodeWithTag("side_dock_enable").performClick()
        composeTestRule.onNodeWithTag("side_dock_animation_link").performScrollTo().performClick()
        assertEquals(true, animationOpened)
    }

    @Test
    fun missingPermissionTakesPriorityOverTheEnableSwitch() {
        overlayAllowed = false
        enabled = false
        show()
        composeTestRule.onNodeWithTag("side_dock_permission").assertIsDisplayed().performClick()
        assertEquals(1, permissionRequests)
        composeTestRule.onNodeWithTag("side_dock_enable").assertDoesNotExist()
        assertControlsHidden()
        composeTestRule.runOnIdle { overlayAllowed = true }
        composeTestRule.onNodeWithTag("side_dock_enable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("side_dock_permission").assertDoesNotExist()
        assertControlsHidden()
    }

    @Test
    fun animationControlsDisappearIfPermissionIsRevokedWhileThePageIsOpen() {
        show(animation = true)
        composeTestRule.onNodeWithTag("side_dock_animation_preview").assertIsDisplayed()
        composeTestRule.runOnIdle { overlayAllowed = false }
        composeTestRule.onNodeWithTag("side_dock_permission").assertIsDisplayed()
        composeTestRule.onNodeWithTag("side_dock_enable").assertDoesNotExist()
        assertControlsHidden()
        composeTestRule.runOnIdle { overlayAllowed = true; enabled = false }
        composeTestRule.onNodeWithTag("side_dock_enable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("side_dock_permission").assertDoesNotExist()
        assertControlsHidden()
    }

    private fun assertControlsHidden() {
        composeTestRule.onNodeWithTag("side_dock_animation_link").assertDoesNotExist()
        composeTestRule.onNodeWithTag("side_dock_setting_side").assertDoesNotExist()
        composeTestRule.onNodeWithTag("side_dock_animation_preview").assertDoesNotExist()
        composeTestRule.onNodeWithTag("side_dock_preset_smooth").assertDoesNotExist()
        for (key in listOf("position", "height", "width", "opacity")) {
            composeTestRule.onNodeWithTag("side_dock_slider_$key").assertDoesNotExist()
        }
        composeTestRule.onNodeWithContentDescription("Spring strength").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Bounce damping").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w340dp-h760dp-port", sdk = [34])
    fun edgesCanBeChangedWithOneTapAndLargerText() {
        show(fontScale = 1.3f)
        composeTestRule.onNodeWithTag("side_dock_settings_list").performScrollToNode(hasTestTag("side_dock_edge_right"))
        composeTestRule.onNodeWithTag("side_dock_edge_right").assertIsDisplayed().assertIsSelected().assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("side_dock_edge_left").assertIsDisplayed().assertIsNotSelected().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("Left", settings.side)
        composeTestRule.onNodeWithTag("side_dock_edge_left").assertIsSelected()
        composeTestRule.onNodeWithTag("side_dock_edge_right").assertIsNotSelected().performClick()
        assertEquals("Right", settings.side)
        composeTestRule.onNodeWithTag("side_dock_edge_right").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Close settings sheet").assertDoesNotExist()
    }

    @Test
    fun allFourHandleSlidersWorkDirectlyWithoutASheetOrPreview() {
        show()
        composeTestRule.onNodeWithTag("side_dock_preview_card").assertDoesNotExist()
        for ((key, value) in listOf("position" to 0.65f, "height" to 180f, "width" to 12f, "opacity" to 0.4f)) {
            composeTestRule.onNodeWithTag("side_dock_setting_$key").performScrollTo()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("side_dock_slider_$key").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
            composeTestRule.onNodeWithContentDescription("Close settings sheet").assertDoesNotExist()
        }
        assertEquals(0.65f, settings.position, 0f)
        assertEquals(180, settings.heightDp)
        assertEquals(12, settings.thicknessDp)
        assertEquals(0.4f, settings.opacity, 0f)
        assertEquals("Right", settings.side)
    }

    @Test
    fun springEditsSelectCustomAndKeepTheOtherSliderValue() {
        show(animation = true)
        composeTestRule.onNodeWithContentDescription("Spring strength").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(510f) }
        assertEquals("Custom", settings.preset)
        assertEquals(510f, settings.stiffness, 0f)
        assertEquals(1f, settings.damping, 0f)
        composeTestRule.onNodeWithContentDescription("Bounce damping").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        assertEquals(1f, settings.damping, 0f)
        composeTestRule.onNodeWithText("No spring overshoot.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w340dp-h760dp-port", sdk = [34])
    fun instantAndAllPresetChoicesRemainReachableWithLargerText() {
        show(animation = true, fontScale = 1.3f)
        composeTestRule.onNodeWithTag("side_dock_animation_list").performScrollToNode(hasTestTag("side_dock_preset_instant"))
        composeTestRule.onNodeWithTag("side_dock_preset_instant").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("Instant", settings.preset)
        composeTestRule.onNodeWithTag("side_dock_animation_list").performScrollToNode(hasContentDescription("Spring strength"))
        composeTestRule.onNodeWithContentDescription("Spring strength").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("side_dock_animation_list").performScrollToNode(hasContentDescription("Bounce damping"))
        composeTestRule.onNodeWithContentDescription("Bounce damping").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("side_dock_preset_bouncy").performScrollTo().performClick()
        assertEquals("Bouncy", settings.preset)
        assertEquals(0.52f, settings.damping, 0f)
        composeTestRule.onNodeWithContentDescription("Spring strength").performScrollTo().assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Close settings sheet").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w340dp-h760dp-port", sdk = [34])
    fun presetPillsWrapWithoutClippingOrMovingWhenSelected() {
        show(animation = true, fontScale = 1.3f)
        composeTestRule.onNodeWithTag("side_dock_animation_list").performScrollToNode(hasTestTag("side_dock_preset_instant"))
        val viewport = composeTestRule.onNodeWithTag("side_dock_animation_list").fetchSemanticsNode().boundsInRoot
        val labels = listOf("Smooth", "Fluid", "Bouncy", "Snappy", "Instant")
        val initialBounds = labels.associateWith {
            composeTestRule.onNodeWithTag("side_dock_preset_${it.lowercase()}").assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        }
        org.junit.Assert.assertTrue("Preset choices do not wrap", initialBounds["Instant"]!!.top > initialBounds["Smooth"]!!.top)
        for (label in labels) {
            val pill = initialBounds[label]!!
            org.junit.Assert.assertTrue("$label clips outside the viewport", pill.left >= viewport.left && pill.right <= viewport.right)
            composeTestRule.onNodeWithTag("side_dock_preset_${label.lowercase()}").performClick().assertIsSelected()
            for (other in labels) {
                assertEquals("Selecting $label moved $other", initialBounds[other],
                    composeTestRule.onNodeWithTag("side_dock_preset_${other.lowercase()}").fetchSemanticsNode().boundsInRoot)
            }
        }
    }

    @Test
    @Config(qualifiers = "w997dp-h448dp-land", sdk = [34])
    fun wideHeaderKeepsTheTitleBelowTheBackButton() {
        show()
        val title = composeTestRule.onNodeWithText("Side dock").fetchSemanticsNode().boundsInRoot
        val back = composeTestRule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Expanded title overlaps Back", title.top >= back.bottom)
        composeTestRule.onNodeWithTag("side_dock_preview_card").assertDoesNotExist()
    }

    @Test
    fun changingMotionProgressDoesNotRecomposeTheDockContent() {
        var progress by mutableFloatStateOf(0f)
        var contentCompositions = 0
        composeTestRule.setContent {
            MaterialTheme {
                val card = sideDockCardGeometry(997, false)
                SideDockMorphSurface("Right", card, morphState = { sideDockMorph(progress, card, 100, 6, "Right") }) {
                    SideEffect { contentCompositions++ }
                    androidx.compose.material3.Text("Saved captures")
                }
            }
        }
        composeTestRule.waitForIdle()
        val initialCompositions = contentCompositions
        for (frame in listOf(0.25f, 0.65f, 1.1f, 1f)) {
            composeTestRule.runOnIdle { progress = frame }
            composeTestRule.waitForIdle()
            assertEquals("The capture UI recomposed at motion progress $frame", initialCompositions, contentCompositions)
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RadialFabInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val labels = listOf("Note", "Link", "Image", "Voice", "Import", "More")
    private val clicks = mutableListOf<Int>()
    private var ticks = 0
    private val expanded = mutableStateOf(false)
    private var backDispatcher: androidx.activity.OnBackPressedDispatcher? = null

    private fun show() {
        compose.setContent {
            MaterialTheme {
                backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks++ }
                }) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
                        GestureRadialFab(labels.mapIndexed { i, name -> RadialMenuItem(R.drawable.ic_custom_plus, name, Color.Blue) { clicks += i } },
                            expanded.value, { expanded.value = it }, fabContainerColor = Color.White,
                            fabContentColor = Color.Black, mainIconResId = R.drawable.ic_custom_plus)
                    }
                }
            }
        }
    }
    @Test fun tapOpensSixActionsAndSelectingOneClosesMenu() {
        show()
        compose.onNodeWithTag("fab_expand").performTouchInput { click() }
        labels.forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onNodeWithContentDescription("Image").performClick()
        compose.runOnIdle { assertEquals(listOf(2), clicks); assertFalse(expanded.value) }
    }
    @Test fun accessibleBaseClickCanToggleTheMenu() {
        show()
        compose.onNodeWithTag("fab_expand").performClick()
        compose.runOnIdle { assertTrue(expanded.value) }
        compose.onNodeWithTag("fab_expand").performClick()
        compose.runOnIdle { assertFalse(expanded.value); assertTrue(clicks.isEmpty()) }
    }
    @Test fun dragOutsideTheActionArcDoesNotFireAnAction() {
        show()
        compose.onNodeWithTag("fab_expand").performTouchInput {
            down(center); moveTo(center + Offset(100f, -10f)); up()
        }
        compose.runOnIdle { assertTrue(clicks.isEmpty()); assertFalse(expanded.value) }
    }
    @Test fun dragAcrossActionsSelectsOnlyTheReleasedActionAndProvidesFeedback() {
        show()
        compose.onNodeWithTag("fab_expand").performTouchInput {
            down(center)
            moveTo(center + Offset(-192.dp.toPx(), 0f))
            moveTo(center + Offset(0f, -192.dp.toPx()))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(5), clicks); assertFalse(expanded.value); assertTrue(ticks >= 2) }
    }
    @Test fun backDismissesExpandedMenuWithoutSelecting() {
        show()
        compose.onNodeWithTag("fab_expand").performClick()
        compose.runOnIdle { requireNotNull(backDispatcher).onBackPressed() }
        compose.runOnIdle { assertFalse(expanded.value); assertTrue(clicks.isEmpty()) }
    }
    @Test fun longPressOpensAndDraggingDisplaysTheSelectedLabel() {
        show()
        compose.onNodeWithTag("fab_expand").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.onNodeWithText("Choose an action").assertIsDisplayed()
        compose.onNodeWithTag("fab_expand").performTouchInput { moveTo(center + Offset(-192.dp.toPx(), 0f)) }
        compose.onNodeWithText("Note").assertIsDisplayed()
        compose.onNodeWithTag("fab_expand").performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(0), clicks) }
    }
    @Test fun cancellingADragDoesNotExecuteHighlightedAction() {
        show()
        compose.onNodeWithTag("fab_expand").performTouchInput {
            down(center); moveTo(center + Offset(-192.dp.toPx(), 0f)); cancel()
        }
        compose.runOnIdle { assertTrue(clicks.isEmpty()); assertFalse(expanded.value) }
    }

}

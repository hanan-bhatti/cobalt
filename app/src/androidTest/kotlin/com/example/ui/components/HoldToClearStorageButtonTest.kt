package com.example.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HoldToClearStorageButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun button() = compose.onNodeWithTag("clear_disposable_hold")
    private fun content(calls: AtomicInteger) {
        compose.setContent { MaterialTheme { HoldToClearStorageButton(true) { calls.incrementAndGet() } } }
    }
    @Test fun shortTapAndLeavingBoundsNeverConfirm() {
        val calls = AtomicInteger(); content(calls)
        button().performTouchInput { down(center); advanceEventTime(400); up() }
        // A cancelled gesture must not leave a delayed confirmation job behind.
        Thread.sleep(5200); compose.mainClock.advanceTimeBy(5500); compose.waitForIdle()
        assertEquals(0, calls.get())
        button().performTouchInput { down(center); moveTo(Offset(-1000f, -1000f)) }
        Thread.sleep(5200); compose.mainClock.advanceTimeBy(5500); compose.waitForIdle()
        button().performTouchInput { up() }
        assertEquals(0, calls.get())
    }
    @Test fun continuousFiveSecondHoldConfirmsExactlyOnce() {
        val calls = AtomicInteger(); content(calls)
        button().performTouchInput { down(center) }
        compose.waitUntil(7000) { calls.get() == 1 }
        Thread.sleep(300); compose.mainClock.advanceTimeBy(500)
        button().performTouchInput { up() }
        assertEquals(1, calls.get())
    }
    @Test fun backgroundingCancelsAndRequiresNewHold() {
        val calls = AtomicInteger(); content(calls)
        button().performTouchInput { down(center) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        Thread.sleep(5200); compose.mainClock.advanceTimeBy(5500)
        assertEquals(0, calls.get())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        button().performTouchInput { up() }
        assertEquals(0, calls.get())
        button().performTouchInput { down(center) }
        compose.waitUntil(7000) { calls.get() == 1 }
        button().performTouchInput { up() }
        assertEquals(1, calls.get())
    }
    @Test fun keyboardNeedsContinuousHoldAndShortPressCancels() {
        val calls = AtomicInteger(); content(calls)
        button().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        button().performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        Thread.sleep(5200); compose.mainClock.advanceTimeBy(5500)
        assertEquals(0, calls.get())
        button().performKeyInput { keyDown(Key.Spacebar) }
        compose.waitUntil(7000) { calls.get() == 1 }
        button().performKeyInput { keyUp(Key.Spacebar) }
        assertEquals(1, calls.get())
    }
}

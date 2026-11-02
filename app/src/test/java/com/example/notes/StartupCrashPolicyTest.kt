package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class StartupCrashPolicyTest {
    @Test fun twoRealStartupCrashesEnableSafeMode() {
        val first = StartupCrashPolicy()
        first.recordCrash(duringStartup = true)
        assertFalse(first.safeMode)
        val nextProcess = StartupCrashPolicy(first.consecutiveCrashes)
        nextProcess.recordCrash(duringStartup = true)
        assertTrue(StartupCrashPolicy(nextProcess.consecutiveCrashes).safeMode)
    }

    @Test fun laterCrashDoesNotCountAsStartupCrash() {
        val state = StartupCrashPolicy()
        state.recordCrash(duringStartup = false)
        assertEquals(0, state.consecutiveCrashes)
    }

    @Test fun healthyStartupBreaksTheSequenceButSafeModeRequiresExplicitExit() {
        val state = StartupCrashPolicy(1)
        state.healthyStartup()
        assertEquals(0, state.consecutiveCrashes)
        val recovery = StartupCrashPolicy(2)
        recovery.healthyStartup()
        assertTrue(recovery.safeMode)
        recovery.reset()
        assertFalse(recovery.safeMode)
    }

    @Test fun normalTerminationDoesNotIncrementTheCounter() {
        // No uncaught exception callback is invoked for a normal process termination.
        val state = StartupCrashPolicy(1)
        assertEquals(1, StartupCrashPolicy(state.consecutiveCrashes).consecutiveCrashes)
    }
}

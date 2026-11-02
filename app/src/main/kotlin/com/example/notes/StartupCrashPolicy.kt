package com.example.notes

/** Only a genuine uncaught exception during startup increments this counter. */
class StartupCrashPolicy(initialCount: Int = 0) {
    var consecutiveCrashes: Int = initialCount.coerceAtLeast(0)
        private set
    val safeMode: Boolean get() = consecutiveCrashes >= CRASH_THRESHOLD

    fun recordCrash(duringStartup: Boolean) {
        if (duringStartup) consecutiveCrashes = (consecutiveCrashes.toLong() + 1)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun healthyStartup() {
        if (!safeMode) consecutiveCrashes = 0
    }

    fun reset() { consecutiveCrashes = 0 }

    companion object {
        const val CRASH_THRESHOLD = 2
        const val STARTUP_WINDOW_MS = 30_000L
    }
}

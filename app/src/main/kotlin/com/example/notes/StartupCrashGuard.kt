package com.example.notes

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

object StartupCrashGuard {
    @Volatile var isSafeMode: Boolean = false
        private set
    private lateinit var preferences: SharedPreferences
    private lateinit var policy: StartupCrashPolicy
    @Volatile private var startupHealthy = false
    @Volatile private var installed = false

    /** WorkManager's provider can initialize before Application.onCreate(). */
    fun shouldPause(context: Context): Boolean {
        if (installed) return isSafeMode
        val count = try {
            context.getSharedPreferences("startup_recovery", Context.MODE_PRIVATE).getInt("startup_crashes", 0)
        } catch (_: ClassCastException) { 0 }
        return count >= StartupCrashPolicy.CRASH_THRESHOLD
    }

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        preferences = context.getSharedPreferences("startup_recovery", Context.MODE_PRIVATE)
        val count = try { preferences.getInt("startup_crashes", 0) } catch (_: ClassCastException) { 0 }
        policy = StartupCrashPolicy(count)
        isSafeMode = policy.safeMode
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                synchronized(this) {
                    policy.recordCrash(duringStartup = !startupHealthy)
                    // apply() can lose the update when Android terminates a crashed process.
                    preferences.edit().putInt("startup_crashes", policy.consecutiveCrashes).commit()
                }
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            synchronized(this) {
                // A blocked main thread has not completed startup even if 30 seconds elapsed.
                startupHealthy = true
                policy.healthyStartup()
                preferences.edit().putInt("startup_crashes", policy.consecutiveCrashes).apply()
            }
        }, StartupCrashPolicy.STARTUP_WINDOW_MS)
        installed = true
    }

    @Synchronized
    fun leaveSafeMode() {
        policy.reset()
        preferences.edit().putInt("startup_crashes", 0).apply()
        isSafeMode = false
    }
}

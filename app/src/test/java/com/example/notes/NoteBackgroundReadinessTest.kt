package com.example.notes

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NoteBackgroundReadinessTest {
    @Test fun eagerReaderStartsOnlyAfterMigrationAndInstalledRecoveryHaveBothCleared() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val migration = context.getSharedPreferences("note_migration", 0)
        val recovery = context.getSharedPreferences("startup_recovery", 0)
        migration.edit().putBoolean("migration_required", true).commit()
        recovery.edit().putInt("startup_crashes", 3).commit()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        StartupCrashGuard.install(context)
        val calls = AtomicInteger()
        val pausedAtRecoveryNotification = AtomicReference<Boolean?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "startup_crashes") pausedAtRecoveryNotification.set(StartupCrashGuard.shouldPause(context))
        }
        recovery.registerOnSharedPreferenceChangeListener(listener)
        val read = async(Dispatchers.IO) {
            backgroundNoteDatabaseReads(context) { calls.incrementAndGet(); flowOf(7) }.first()
        }
        try {
            assertTrue(StartupCrashGuard.shouldPause(context))
            delay(100); assertEquals(0, calls.get()); assertFalse(read.isCompleted)
            migration.edit().putBoolean("migration_required", false).putInt("completed_format", 1).commit()
            shadowOf(Looper.getMainLooper()).idle()
            delay(100); assertEquals(0, calls.get()); assertFalse(read.isCompleted)
            StartupCrashGuard.leaveSafeMode()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(false, pausedAtRecoveryNotification.get())
            assertEquals(7, withTimeout(5_000) { read.await() })
            assertEquals(1, calls.get())
        } finally {
            read.cancel()
            recovery.unregisterOnSharedPreferenceChangeListener(listener)
            if (StartupCrashGuard.isSafeMode) StartupCrashGuard.leaveSafeMode()
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }
}

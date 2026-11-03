package com.example.notes

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Persisted WorkManager name: kept deliberately for worker reflection across R8 updates. */
@androidx.annotation.Keep
class TrashCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (StartupCrashGuard.shouldPause(applicationContext)) return Result.success()
        return try { TrashManager(applicationContext, scheduleWork = false).expire(); Result.success() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun schedule(context: Context) {
            if (StartupCrashGuard.shouldPause(context)) return
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("cobalt-trash-expiry",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TrashCleanupWorker>(24, TimeUnit.HOURS).build())
            WorkManager.getInstance(context).enqueueUniqueWork("cobalt-trash-startup",
                ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<TrashCleanupWorker>().build())
        }
        fun scheduleSoon(context: Context, delayMs: Long) {
            if (StartupCrashGuard.shouldPause(context)) return
            WorkManager.getInstance(context).enqueueUniqueWork("cobalt-trash-undo-expiry", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TrashCleanupWorker>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build())
        }
    }
}

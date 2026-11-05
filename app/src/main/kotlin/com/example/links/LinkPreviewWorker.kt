package com.example.links

import android.content.Context
import androidx.work.*
import com.example.data.repository.CobaltRepository
import com.example.notes.StartupCrashGuard
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One serial consumer for the durable Room queue, independent of Capture/Dock lifetimes. */
class LinkPreviewWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = consumer.withLock {
        if (paused()) return@withLock Result.retry()
        val repository = CobaltRepository(applicationContext)
        repeat(32) {
            if (isStopped || paused()) return@withLock Result.retry()
            if (!repository.processNextLinkPreview()) return@withLock Result.success()
        }
        return@withLock Result.retry() // bounded run; pending rows survive process death and cancellation
    }

    private fun paused() = StartupCrashGuard.shouldPause(applicationContext) ||
        com.example.notes.NoteMigrationManager.shouldPauseBackground(applicationContext)

    companion object {
        private val consumer = Mutex()
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniqueWork("link-preview-queue", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LinkPreviewWorker>().setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
            // Covers a save racing the last empty-queue check of an already running KEEP worker.
            manager.enqueueUniquePeriodicWork("link-preview-queue-safety", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LinkPreviewWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints).build())
        }
    }
}

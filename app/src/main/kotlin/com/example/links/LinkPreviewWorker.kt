package com.example.links

import android.content.Context
import android.os.SystemClock
import androidx.work.*
import com.example.data.local.AppDatabase
import com.example.data.repository.CobaltRepository
import com.example.notes.StartupCrashGuard
import com.example.widget.WidgetUpdater
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Durable queue with bounded parallel fetches; importing never waits for website responses. */
class LinkPreviewWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = consumer.withLock {
        if (paused()) return@withLock Result.retry()
        try {
            val repository = CobaltRepository(applicationContext)
            val queue = AppDatabase.getDatabase(applicationContext).linkPreviewDao()
            val started = SystemClock.elapsedRealtime()
            var processed = 0
            while (processed < 128 && SystemClock.elapsedRealtime() - started < TimeUnit.MINUTES.toMillis(4)) {
                if (isStopped || paused()) return@withLock Result.retry()
                val batch = queue.nextBatch(4)
                if (batch.isEmpty()) {
                    if (processed > 0) WidgetUpdater.update(applicationContext)
                    return@withLock Result.success()
                }
                coroutineScope {
                    batch.map { request -> async { repository.processLinkPreview(request, updateWidgets = false) } }.awaitAll()
                }
                processed += batch.size
                // Refresh widgets once per group, not once for every network response.
                if (processed % 16 == 0) WidgetUpdater.update(applicationContext)
            }
            WidgetUpdater.update(applicationContext)
            // Continue real queued work without escalating exponential retry delays between successful batches.
            if (queue.next() != null) continueQueue(applicationContext)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry() // Keep durable rows pending when storage or scheduling is temporarily unavailable.
        }
    }

    private fun paused() = StartupCrashGuard.shouldPause(applicationContext) ||
        com.example.notes.NoteMigrationManager.shouldPauseBackground(applicationContext)

    companion object {
        private val consumer = Mutex()
        private const val QUEUE = "link-preview-queue"
        private fun request() = OneTimeWorkRequestBuilder<LinkPreviewWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()

        /** Append after a running worker so a late import batch cannot get lost in a KEEP race. */
        fun continueQueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request())
        }

        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniqueWork(QUEUE, ExistingWorkPolicy.KEEP, request())
            // Recover pending rows after process death or an interrupted save/scheduling handoff.
            manager.enqueueUniquePeriodicWork("link-preview-queue-safety", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LinkPreviewWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}

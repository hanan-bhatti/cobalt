/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.util

import android.content.Context
import com.example.util.SafeLogger as Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.remote.trakt.TraktAuthManager
import com.example.data.remote.trakt.TraktSyncEngine
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker executing Trakt two-way sync quietly with notifications.
 */
class TraktSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if ((com.example.notes.StartupCrashGuard.shouldPause(applicationContext) || com.example.notes.NoteMigrationManager.shouldPauseBackground(applicationContext))) return Result.success()
        val authManager = TraktAuthManager.getInstance(applicationContext)
        if (!authManager.hasValidToken()) {
            return Result.success()
        }

        return try {
            if (inputData.getBoolean("upload_only", false)) {
                authManager.refreshAccessTokenIfNeeded()
                com.example.data.remote.trakt.TraktOutbox.drain(applicationContext, TraktSyncEngine.getApiService())
                return Result.success()
            }
            val result = TraktSyncEngine.performSync(
                context = applicationContext,
                isBackground = true
            )
            if (result.isSuccess) {
                Result.success()
            } else {
                Log.w(TAG, "Sync finished with failure: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Worker execution failed: ${e.message}", e)
            Result.retry()
        } finally {
            if (authManager.hasValidToken()) runCatching { scheduleDeferredRetry(applicationContext) }
        }
    }

    companion object {
        private const val TAG = "TraktSyncWorker"
        const val ONE_TIME_WORK_NAME = "cobalt_trakt_sync_immediate"
        const val PERIODIC_WORK_NAME = "cobalt_trakt_sync_periodic"
        const val DEFERRED_WORK_NAME = "cobalt_trakt_sync_deferred"

        private val uploadScheduleLock = Any()

        fun enqueueOneTimeSync(context: Context, uploadOnly: Boolean = false) {
            if ((com.example.notes.StartupCrashGuard.shouldPause(context) || com.example.notes.NoteMigrationManager.shouldPauseBackground(context))) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<TraktSyncWorker>()
                .setInputData(androidx.work.workDataOf("upload_only" to uploadOnly))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            val manager = WorkManager.getInstance(context)
            if (uploadOnly) {
                // Called from the IO outbox writer. Keep at most one waiting upload behind the active worker.
                synchronized(uploadScheduleLock) {
                    val query = androidx.work.WorkQuery.Builder.fromUniqueWorkNames(listOf(ONE_TIME_WORK_NAME))
                        .addStates(listOf(androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.BLOCKED)).build()
                    val waiting = manager.getWorkInfos(query).get().isNotEmpty()
                    if (!waiting) manager.enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request).result.get()
                }
            } else manager.enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)

        }

        suspend fun scheduleDeferredRetry(context: Context) {
            val auth = TraktAuthManager.getInstance(context)
            if (!auth.hasValidToken() || com.example.notes.StartupCrashGuard.shouldPause(context) ||
                com.example.notes.NoteMigrationManager.shouldPauseBackground(context)) return
            val next = com.example.data.local.AppDatabase.getDatabase(context).traktOutboxDao()
                .nextRetryAt(auth.syncSessionId(), System.currentTimeMillis()) ?: return
            val manager = WorkManager.getInstance(context)
            synchronized(uploadScheduleLock) {
                val query = androidx.work.WorkQuery.Builder.fromUniqueWorkNames(listOf(DEFERRED_WORK_NAME))
                    .addStates(listOf(androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.BLOCKED)).build()
                if (manager.getWorkInfos(query).get().isNotEmpty()) return
                val request = OneTimeWorkRequestBuilder<TraktSyncWorker>()
                    .setInputData(androidx.work.workDataOf("upload_only" to true))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay((next - System.currentTimeMillis()).coerceAtLeast(1000L), TimeUnit.MILLISECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
                manager.enqueueUniqueWork(DEFERRED_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request).result.get()
            }
        }

        fun schedulePeriodicSync(context: Context, repeatHours: Long = 24) {
            if ((com.example.notes.StartupCrashGuard.shouldPause(context) || com.example.notes.NoteMigrationManager.shouldPauseBackground(context))) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<TraktSyncWorker>(repeatHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelAll(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(ONE_TIME_WORK_NAME)
            wm.cancelUniqueWork(PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(DEFERRED_WORK_NAME)
        }
    }
}

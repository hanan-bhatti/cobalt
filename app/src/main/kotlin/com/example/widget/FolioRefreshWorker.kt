package com.example.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class FolioRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (runCatching { FolioWidgetProvider.updateAll(applicationContext) }.getOrDefault(false)) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()

    companion object {
        fun enqueue(context: Context) {
            if (FolioWidgetProvider.ids(context).isEmpty()) return
            WorkManager.getInstance(context).enqueueUniqueWork("cobalt-folio-refresh", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<FolioRefreshWorker>().setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build())
        }
    }
}

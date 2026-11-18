package com.example.widget

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class ScreeningWidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val id = inputData.getInt("advance", -1).takeIf { it > 0 }
        val generation = inputData.getLong("generation", -1).takeIf { it >= 0 }
        if (ScreeningWidgetProvider.updateAll(applicationContext, id, generation)) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }

    companion object {
        fun enqueue(context: Context, advanceId: Int? = null, generation: Long? = null): Operation? {
            if (ScreeningWidgetProvider.ids(context).isEmpty()) return null
            val request = OneTimeWorkRequestBuilder<ScreeningWidgetRefreshWorker>()
                .setInputData(workDataOf("advance" to (advanceId ?: -1), "generation" to (generation ?: -1L)))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
            return WorkManager.getInstance(context).enqueueUniqueWork("cobalt-screening-widget-refresh", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

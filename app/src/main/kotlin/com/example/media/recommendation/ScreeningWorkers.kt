package com.example.media.recommendation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScreeningMorningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = ScreeningStore(applicationContext)
        val generation = inputData.getInt("generation", -1)
        if (!store.enabled || generation != ScreeningWork.generation(applicationContext)) return@withContext Result.success()
        try {
            if (com.example.notes.StartupCrashGuard.shouldPause(applicationContext) || com.example.notes.NoteMigrationManager.shouldPauseBackground(applicationContext)) return@withContext Result.success()
            val now = System.currentTimeMillis()
            if (!ScreeningSchedule.inWindow(now, store.hour, store.minute, store.weekends) || !ScreeningNotifications.available(applicationContext)) return@withContext Result.success()
            val pending = store.pendingMorning(ScreeningSchedule.day(now), now)
            val ranked = ScreeningService.ranked(applicationContext, cooldowns = pending == null)
            val pick = if (pending != null) ranked.find { it.key == pending.key }
                else ScreeningRecommendationEngine.choose(ranked, "morning:${ScreeningSchedule.day(now)}")
            if (pick == null) return@withContext Result.success()
            val delivery = pending ?: store.createDelivery(pick, now, ScreeningSchedule.day(now), true) ?: return@withContext Result.success()
            if (ScreeningNotifications.show(applicationContext, pick, delivery)) store.markPosted(delivery.token,
                System.currentTimeMillis(), pick, mode = if (pending != null) "Retained reservation" else "Selected")
            Result.success()
        } catch (cancel: CancellationException) { throw cancel }
        catch (e: Exception) {
            com.example.util.SafeLogger.e("Screening", "Morning question failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            // A distinct work name for each local date avoids canceling the currently running worker.
            if (store.enabled && generation == ScreeningWork.generation(applicationContext)) runCatching { ScreeningWork.schedule(applicationContext, allowToday = false) }
        }
    }
}
object ScreeningWork {
    private const val TAG = "cobalt-screening-morning"
    fun generation(context: Context) = context.getSharedPreferences("cobalt_screening", Context.MODE_PRIVATE).getInt("generation", 0)
    @Synchronized
    fun schedule(context: Context, replace: Boolean = false, allowToday: Boolean = true) {
        val store = ScreeningStore(context)
        val wm = WorkManager.getInstance(context)
        if (replace) {
            val prefs = context.getSharedPreferences("cobalt_screening", Context.MODE_PRIVATE)
            prefs.edit().putInt("generation", generation(context) + 1).commit()
            wm.cancelAllWorkByTag(TAG)
        }
        if (!store.enabled) { wm.cancelAllWorkByTag(TAG); return }
        val now = System.currentTimeMillis()
        val due = ScreeningSchedule.next(now, store.hour, store.minute, store.weekends, allowToday = allowToday)
        val gen = generation(context)
        val request = OneTimeWorkRequestBuilder<ScreeningMorningWorker>().addTag(TAG)
            .setInitialDelay((due - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("generation" to gen))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        wm.enqueueUniqueWork("$TAG:$gen:${ScreeningSchedule.day(due)}", ExistingWorkPolicy.KEEP, request)
    }
}
class ScreeningFeedbackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (com.example.notes.StartupCrashGuard.shouldPause(applicationContext) || com.example.notes.NoteMigrationManager.shouldPauseBackground(applicationContext)) return if (runAttemptCount < 3) Result.retry() else Result.failure()
        val token = inputData.getString("token") ?: return Result.failure()
        // Destructive watch-state changes always require confirmation in the app, never a broadcast.
        val kind = runCatching { ScreeningFeedback.valueOf(inputData.getString("feedback").orEmpty()) }.getOrNull()
            ?.takeIf { it in setOf(ScreeningFeedback.USEFUL, ScreeningFeedback.NOT_INTERESTED) } ?: return Result.failure()
        return try { ScreeningService.answer(applicationContext, token, kind); Result.success() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
class ScreeningFeedbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(ScreeningNotifications.EXTRA_TOKEN) ?: return
        val feedback = intent.getStringExtra("FEEDBACK") ?: return
        val pending = goAsync()
        executor.execute {
            try {
                val request = OneTimeWorkRequestBuilder<ScreeningFeedbackWorker>()
                    .setInputData(workDataOf("token" to token, "feedback" to feedback)).build()
                WorkManager.getInstance(context).enqueueUniqueWork("screening-answer:$token", ExistingWorkPolicy.KEEP, request)
                    .result.get(8, TimeUnit.SECONDS)
            } catch (e: Exception) { com.example.util.SafeLogger.e("Screening", "Could not queue feedback", e) } finally { pending.finish() }
        }
    }
    companion object { private val executor = Executors.newSingleThreadExecutor() }
}
class ScreeningTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED)) return
        val pending = goAsync()
        executor.execute { try { ScreeningWork.schedule(context, replace = true) } catch (e: Exception) { com.example.util.SafeLogger.e("Screening", "Could not reschedule", e) } finally { pending.finish() } }
    }
    companion object { private val executor = Executors.newSingleThreadExecutor() }
}

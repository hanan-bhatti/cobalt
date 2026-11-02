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
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AdaptiveReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if ((com.example.notes.StartupCrashGuard.shouldPause(applicationContext) || com.example.notes.NoteMigrationManager.shouldPauseBackground(applicationContext))) return Result.success()
        val store = AdaptiveReminderStore(applicationContext)
        if (!store.isRemindersEnabled) {
            return Result.success()
        }

        // Notification permission check (Android 13+)
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        // Weekend check
        if (!store.includeWeekends) {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                return Result.success()
            }
        }

        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val entities = db.savedItemDao().getAllItems()
            val items = entities.map { it.toDomainModel() }

            val candidate = AdaptiveReminderEngine.selectBestCandidate(applicationContext, items)
            if (candidate != null) {
                AdaptiveReminderNotificationHelper.showReminderNotification(
                    context = applicationContext,
                    item = candidate.item,
                    reason = candidate.reason
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("AdaptiveReminderWorker", "Error selecting or showing reminder: ${e.message}")
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "cobalt_adaptive_reminder_worker"

        fun schedule(context: Context) {
            if ((com.example.notes.StartupCrashGuard.shouldPause(context) || com.example.notes.NoteMigrationManager.shouldPauseBackground(context))) return
            val store = AdaptiveReminderStore(context)
            if (!store.isRemindersEnabled) return

            val targetHour = store.deliveryHour
            val targetMinute = store.deliveryMinute
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val initialDelayMillis = (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)

            val periodicRequest = PeriodicWorkRequestBuilder<AdaptiveReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

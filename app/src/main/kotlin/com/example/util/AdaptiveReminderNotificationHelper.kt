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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.SavedItem

object AdaptiveReminderNotificationHelper {

    const val CHANNEL_ID = "cobalt_adaptive_reminders"
    const val NOTIFICATION_ID = 2026
    const val EXTRA_REMINDER_ITEM_ID = "EXTRA_REMINDER_ITEM_ID"
    const val EXTRA_FROM_REMINDER = "EXTRA_FROM_REMINDER"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Archive Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Self-learning resurfacing notifications for forgotten notes and captures"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#F59E0B")
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(
        context: Context,
        item: SavedItem,
        reason: String? = null
    ): Boolean {
        ensureChannel(context)

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return false
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REMINDER_ITEM_ID, item.id)
            putExtra(EXTRA_FROM_REMINDER, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val subtext = item.folders.firstOrNull() ?: reason ?: "From your archive"
        val displayTitle = item.title.ifBlank { item.linkTitle ?: "Saved Note" }
        val previewBody = item.linkDescription?.takeIf { it.isNotBlank() }
            ?: item.extractedText?.takeIf { it.isNotBlank() }
            ?: item.content.lines().firstOrNull { it.isNotBlank() }
            ?: "Tap to review this item from your Cobalt archive."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_custom_reminder)
            .setColor(android.graphics.Color.parseColor("#F59E0B"))
            .setContentTitle(displayTitle)
            .setSubText(subtext)
            .setContentText(previewBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(previewBody).setSummaryText(subtext))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
            AdaptiveReminderStore(context).recordReminderDispatched(item.id, item.folders)
            return true
        } catch (e: SecurityException) {
            com.example.util.SafeLogger.w("CobaltReminder", "Missing POST_NOTIFICATIONS permission: ${e.message}")
            return false
        } catch (e: Exception) {
            com.example.util.SafeLogger.e("CobaltReminder", "Failed to dispatch reminder notification: ${e.message}")
            return false
        }
    }
}

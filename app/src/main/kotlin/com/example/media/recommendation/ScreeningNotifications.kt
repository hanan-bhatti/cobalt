package com.example.media.recommendation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object ScreeningNotifications {
    const val CHANNEL = "cobalt_screening_questions"
    const val EXTRA_TOKEN = "SCREENING_FEEDBACK_TOKEN"
    private const val ID = 2027
    fun available(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Screening questions", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Optional morning questions about your saved movies and shows"
        })
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    }
    fun show(context: Context, pick: ScreeningRecommendation, delivery: ScreeningStore.Delivery): Boolean {
        if (!available(context)) return false
        val feedback = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("cobalt-screening://question/${delivery.token}")
            putExtra(EXTRA_TOKEN, delivery.token)
        }
        val tap = PendingIntent.getActivity(context, 0, feedback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val progress = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("cobalt-screening://progress/${delivery.token}")
            putExtra("OPEN_ITEM_ID", pick.item.id)
        }
        val progressTap = PendingIntent.getActivity(context, 0, progress, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(kind: ScreeningFeedback) = PendingIntent.getBroadcast(context, 0,
            Intent(context, ScreeningFeedbackReceiver::class.java).apply {
                action = "com.example.SCREENING_FEEDBACK"
                data = Uri.parse("cobalt-screening://answer/${delivery.token}/${kind.name}")
                putExtra(EXTRA_TOKEN, delivery.token); putExtra("FEEDBACK", kind.name)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_custom_movie)
            .setContentTitle(pick.question)
            .setContentText(pick.reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${pick.reason}\nTap for more responses, including Finished or Paused."))
            .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(R.drawable.ic_custom_helpful, "Useful", action(ScreeningFeedback.USEFUL))
            .addAction(R.drawable.ic_custom_unhelpful, "Not interested", action(ScreeningFeedback.NOT_INTERESTED))
            .addAction(R.drawable.ic_custom_edit, "Update progress", progressTap).build()
        return try {
            // Keep one visible morning question; an old action can only cancel its own tagged notification.
            context.getSystemService(NotificationManager::class.java).activeNotifications.filter { it.id == ID && it.tag != delivery.token }
                .forEach { NotificationManagerCompat.from(context).cancel(it.tag, it.id) }
            NotificationManagerCompat.from(context).notify(delivery.token, ID, notification); true
        } catch (_: SecurityException) { false }
    }
    fun cancel(context: Context, token: String) = NotificationManagerCompat.from(context).cancel(token, ID)
    fun cancelAll(context: Context) {
        context.getSystemService(NotificationManager::class.java).activeNotifications.filter { it.id == ID }.forEach {
            NotificationManagerCompat.from(context).cancel(it.tag, ID)
        }
    }
}

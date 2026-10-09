package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.repository.CobaltRepository
import com.example.sync.BackupSyncManager
import kotlinx.coroutines.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class DataUploadService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: CobaltRepository
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val CHANNEL_ID = "data_upload_channel"
        private const val NOTIFICATION_ID = 1003
        const val ACTION_START_SYNC = "com.example.service.action.START_SYNC"
        const val ACTION_START_BACKUP = "com.example.service.action.START_BACKUP"
        const val EXTRA_ITEM_IDS = "extra_item_ids"
    }

    override fun onCreate() {
        super.onCreate()
        repository = CobaltRepository(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        
        val notification = createNotification("Starting upload...", 0, 100)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val action = intent.action
        val itemIds = intent.getStringArrayListExtra(EXTRA_ITEM_IDS)

        serviceScope.launch {
            try {
                if (!isNetworkAvailable()) {
                    updateNotificationError("No network connection.")
                    stopSelf()
                    return@launch
                }

                if (action == ACTION_START_BACKUP && itemIds != null) {
                    repository.backupSelectedItems(itemIds) { progress ->
                        updateProgress(progress)
                    }
                } else {
                    repository.syncUnsyncedItems { progress ->
                        updateProgress(progress)
                    }
                }
                
                showSuccessNotification()
            } catch (e: Exception) {
                Log.e("DataUploadService", "Upload failed", e)
                updateNotificationError("Upload failed: ${e.message}")
            } finally {
                BackupSyncManager.updateProgress(BackupSyncManager.SyncProgress(isSyncing = false))
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun updateProgress(progress: BackupSyncManager.SyncProgress) {
        BackupSyncManager.updateProgress(progress)
        val percentage = if (progress.totalBytes > 0) {
            ((progress.progressBytes.toFloat() / progress.totalBytes) * 100).toInt()
        } else {
            0
        }
        val text = "Uploading ${progress.currentItem} / ${progress.totalItems}"
        notificationManager.notify(NOTIFICATION_ID, createNotification(text, percentage, 100))
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun createNotification(contentText: String, progress: Int, maxProgress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cloud Sync & Backup")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(maxProgress, progress, maxProgress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotificationError(message: String) {
        val errorNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Upload Failed")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, errorNotification)
    }

    private fun showSuccessNotification() {
        val successNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sync Complete")
            .setContentText("All items have been backed up.")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, successNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Data Upload & Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for uploads and backups."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}

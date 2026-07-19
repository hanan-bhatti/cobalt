/*
 * Second Brain - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 */

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
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.repository.SecondBrainRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class DataDownloadService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: SecondBrainRepository
    private lateinit var notificationManager: NotificationManager
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val CHANNEL_ID = "data_download_channel"
        private const val NOTIFICATION_ID = 1002
        private const val TAG = "DataDownloadService"
    }

    override fun onCreate() {
        super.onCreate()
        repository = SecondBrainRepository(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Preparing download...", 0, 100)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                performDownload(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                DataDownloadManager.updateProgress(
                    DataDownloadManager.progress.value.copy(
                        isDownloading = false,
                        error = e.localizedMessage ?: "Unknown download error"
                    )
                )
                updateNotificationError(e.localizedMessage ?: "Download failed")
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun performDownload(intent: Intent?) = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("second_brain_prefs", Context.MODE_PRIVATE)
        val resumeInterrupted = intent?.getBooleanExtra("resume_interrupted", false) ?: false
        val retryFailed = intent?.getBooleanExtra("retry_failed", false) ?: false

        DataDownloadManager.updateProgress(
            DataDownloadManager.DownloadProgress(
                isDownloading = true,
                totalFiles = 0,
                downloadedFiles = 0,
                currentFileName = "Checking pre-requisites..."
            )
        )

        // Pre-flight check: Network connectivity
        if (!isNetworkAvailable()) {
            val errorMsg = "No internet connection. Please check your network and try again."
            Log.e(TAG, errorMsg)
            DataDownloadManager.updateProgress(
                DataDownloadManager.progress.value.copy(
                    isDownloading = false,
                    error = errorMsg
                )
            )
            updateNotificationError("No internet connection")
            return@withContext
        }

        // Fetch all items from repository
        val allItems = repository.getAllItems()
        val mediaItems: List<SavedItem>

        if (resumeInterrupted || retryFailed) {
            val allIds = prefs.getStringSet("interrupted_backup_all_ids", emptySet()) ?: emptySet()
            val completedIds = prefs.getStringSet("interrupted_backup_completed_ids", emptySet()) ?: emptySet()
            val remainingIds = allIds - completedIds
            mediaItems = allItems.filter { it.id in remainingIds }
        } else {
            // Fresh run
            mediaItems = allItems.filter { item ->
                val mediaUrl = if (item.type == SavedItemType.AUDIO) item.thumbnailPath ?: "" else item.content
                val hasWebUrl = mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")
                val isMedia = item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO || item.type == SavedItemType.AUDIO
                isMedia && hasWebUrl
            }
            
            // Persist initial state
            val allIds = mediaItems.map { it.id }.toSet()
            prefs.edit().apply {
                putBoolean("interrupted_backup_in_progress", true)
                putStringSet("interrupted_backup_all_ids", allIds)
                putStringSet("interrupted_backup_completed_ids", emptySet())
                putStringSet("interrupted_backup_failed_ids", emptySet())
                apply()
            }
        }

        if (mediaItems.isEmpty()) {
            Log.d(TAG, "No media files to download from cloud.")
            val completedIds = prefs.getStringSet("interrupted_backup_completed_ids", emptySet()) ?: emptySet()
            val allIds = prefs.getStringSet("interrupted_backup_all_ids", emptySet()) ?: emptySet()
            val totalCount = if (allIds.isNotEmpty()) allIds.size else 0
            
            val failedIds = prefs.getStringSet("interrupted_backup_failed_ids", emptySet()) ?: emptySet()
            if (failedIds.isNotEmpty() && (resumeInterrupted || retryFailed)) {
                val failedItems = allItems.filter { it.id in failedIds }.map { 
                    DataDownloadManager.FailedItem(it.id, it.title, "Interrupted download")
                }
                DataDownloadManager.updateProgress(
                    DataDownloadManager.DownloadProgress(
                        isDownloading = false,
                        isCompleted = false,
                        downloadedFiles = totalCount - failedIds.size,
                        totalFiles = totalCount,
                        failedItems = failedItems
                    )
                )
                updateNotificationError("${failedIds.size} items failed to back up.")
            } else {
                finalizeSignOutAndComplete(totalCount)
            }
            return@withContext
        }

        val totalFilesCount = mediaItems.size
        
        // Determine total size of all items
        var totalBytesExpected = 0L
        val sizes = LongArray(totalFilesCount) { -1L }
        
        DataDownloadManager.updateProgress(
            DataDownloadManager.progress.value.copy(
                totalFiles = totalFilesCount,
                currentFileName = "Calculating archive size..."
            )
        )

        for (i in mediaItems.indices) {
            val item = mediaItems[i]
            val mediaUrl = if (item.type == SavedItemType.AUDIO) item.thumbnailPath ?: "" else item.content
            var size = getRemoteFileSize(mediaUrl)
            if (size <= 0L) {
                size = if (item.sizeBytes > 0L) {
                    item.sizeBytes
                } else {
                    // Conservative non-zero estimate based on type
                    when (item.type) {
                        SavedItemType.VIDEO -> 15L * 1024L * 1024L
                        SavedItemType.AUDIO -> 5L * 1024L * 1024L
                        else -> 2L * 1024L * 1024L
                    }
                }
            }
            sizes[i] = size
            totalBytesExpected += size
        }

        DataDownloadManager.updateProgress(
            DataDownloadManager.progress.value.copy(
                totalBytes = totalBytesExpected
            )
        )

        // Pre-flight check: Storage space check
        val freeSpaceBytes = applicationContext.filesDir.freeSpace
        val requiredWithMargin = (totalBytesExpected * 1.1).toLong() + (10 * 1024 * 1024)
        if (freeSpaceBytes < requiredWithMargin) {
            val errorMsg = "Not enough storage: need ${requiredWithMargin / (1024 * 1024)} MB, have ${freeSpaceBytes / (1024 * 1024)} MB available."
            Log.e(TAG, errorMsg)
            DataDownloadManager.updateProgress(
                DataDownloadManager.progress.value.copy(
                    isDownloading = false,
                    error = errorMsg
                )
            )
            updateNotificationError("Insufficient storage space")
            return@withContext
        }

        var totalDownloadedBytes = 0L
        val startDownloadTimeMs = System.currentTimeMillis()
        val failedList = mutableListOf<DataDownloadManager.FailedItem>()

        for (index in mediaItems.indices) {
            val item = mediaItems[index]
            val mediaUrl = if (item.type == SavedItemType.AUDIO) item.thumbnailPath ?: "" else item.content
            val extension = when (item.type) {
                SavedItemType.VIDEO -> "mp4"
                SavedItemType.AUDIO -> "mp4"
                else -> "jpg"
            }
            val fileName = "${item.id}.$extension"

            var downloadSuccess = false
            var retries = 3
            var lastError: Exception? = null

            while (!downloadSuccess && retries > 0) {
                try {
                    val request = Request.Builder().url(mediaUrl).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Server returned HTTP code ${response.code}")
                        }

                        val body = response.body ?: throw Exception("Response body is empty")
                        val contentLength = body.contentLength()
                        val inputStream = body.byteStream()
                        
                        val targetDir = repository.getPermanentMediaDir(item.type)
                        val destFile = File(targetDir, fileName)

                        FileOutputStream(destFile).use { outputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalDownloadedBytes += bytesRead
                                
                                // Realtime stats calculation
                                val timeElapsedMs = System.currentTimeMillis() - startDownloadTimeMs
                                val speedBytesPerSec = if (timeElapsedMs > 0) (totalDownloadedBytes * 1000L) / timeElapsedMs else 0L
                                val estRemainingSeconds = if (speedBytesPerSec > 0 && totalBytesExpected > totalDownloadedBytes) {
                                    (totalBytesExpected - totalDownloadedBytes) / speedBytesPerSec
                                } else {
                                    -1L
                                }

                                val progressPercent = if (totalBytesExpected > 0) {
                                    ((totalDownloadedBytes * 100) / totalBytesExpected).toInt().coerceIn(0, 100)
                                } else {
                                    ((index * 100) / totalFilesCount)
                                }

                                DataDownloadManager.updateProgress(
                                    DataDownloadManager.progress.value.copy(
                                        downloadedFiles = index,
                                        currentFileName = fileName,
                                        downloadedBytes = totalDownloadedBytes,
                                        speedBytesPerSec = speedBytesPerSec,
                                        estRemainingSeconds = estRemainingSeconds
                                    )
                                )

                                // Update notification progress
                                val infoText = String.format(
                                    Locale.US,
                                    "File %d/%d | %.1f MB/s",
                                    index + 1,
                                    totalFilesCount,
                                    speedBytesPerSec / (1024f * 1024f)
                                )
                                val notification = createNotification(
                                    contentText = infoText,
                                    progress = progressPercent,
                                    maxProgress = 100
                                )
                                notificationManager.notify(NOTIFICATION_ID, notification)
                            }
                        }

                        // Verify downloaded file integrity
                        val localFileSize = destFile.length()
                        val expectedSize = if (item.sizeBytes > 0) item.sizeBytes else contentLength
                        if (expectedSize > 0 && localFileSize != expectedSize) {
                            if (destFile.exists()) {
                                destFile.delete()
                            }
                            throw Exception("File size mismatch. Expected $expectedSize bytes, got $localFileSize bytes.")
                        }

                        // Update Room Entity
                        val updatedItem = if (item.type == SavedItemType.AUDIO) {
                            item.copy(
                                thumbnailPath = destFile.absolutePath,
                                isSynced = false,
                                isPendingBackup = false,
                                isBackedUp = false,
                                isUnavailable = false
                            )
                        } else {
                            item.copy(
                                content = destFile.absolutePath,
                                thumbnailPath = destFile.absolutePath,
                                isSynced = false,
                                isPendingBackup = false,
                                isBackedUp = false,
                                isUnavailable = false
                            )
                        }
                        repository.saveItemLocallyOnly(updatedItem)

                        // Update persisted completed list
                        synchronized(prefs) {
                            val completed = prefs.getStringSet("interrupted_backup_completed_ids", emptySet())!!.toMutableSet()
                            completed.add(item.id)
                            
                            val failed = prefs.getStringSet("interrupted_backup_failed_ids", emptySet())!!.toMutableSet()
                            failed.remove(item.id) // successfully completed, remove from failed if it was there
                            
                            prefs.edit().apply {
                                putStringSet("interrupted_backup_completed_ids", completed)
                                putStringSet("interrupted_backup_failed_ids", failed)
                                apply()
                            }
                        }
                        downloadSuccess = true
                    }
                } catch (e: Exception) {
                    retries--
                    lastError = e
                    Log.w(TAG, "Retry failed for $fileName. Retries left: $retries", e)
                    if (retries > 0) {
                        delay(2000)
                    }
                }
            }

            if (!downloadSuccess) {
                val reason = lastError?.localizedMessage ?: "Unknown download error"
                Log.e(TAG, "Failed to download ${item.title}: $reason")
                failedList.add(DataDownloadManager.FailedItem(item.id, item.title, reason))
                
                // Update persisted failed list
                synchronized(prefs) {
                    val failed = prefs.getStringSet("interrupted_backup_failed_ids", emptySet())!!.toMutableSet()
                    failed.add(item.id)
                    prefs.edit().putStringSet("interrupted_backup_failed_ids", failed).apply()
                }
            }
        }

        // Final completion check
        val allIds = prefs.getStringSet("interrupted_backup_all_ids", emptySet()) ?: emptySet()
        val finalFailedIds = prefs.getStringSet("interrupted_backup_failed_ids", emptySet()) ?: emptySet()
        val totalCount = allIds.size

        if (finalFailedIds.isEmpty()) {
            finalizeSignOutAndComplete(totalCount)
        } else {
            val failedItems = allItems.filter { it.id in finalFailedIds }.map { 
                DataDownloadManager.FailedItem(it.id, it.title, "Download failed")
            }
            DataDownloadManager.updateProgress(
                DataDownloadManager.DownloadProgress(
                    isDownloading = false,
                    isCompleted = false,
                    downloadedFiles = totalCount - finalFailedIds.size,
                    totalFiles = totalCount,
                    failedItems = failedItems
                )
            )
            val errorNotificationText = "${finalFailedIds.size} items failed to back up."
            updateNotificationError(errorNotificationText)
        }
    }

    private fun finalizeSignOutAndComplete(downloadedCount: Int) {
        Log.i(TAG, "Download finished. Signing out user...")
        
        // Remove simulated preference
        val prefs = getSharedPreferences("second_brain_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("simulated_email")
            putBoolean("interrupted_backup_in_progress", false)
            putStringSet("interrupted_backup_all_ids", emptySet())
            putStringSet("interrupted_backup_completed_ids", emptySet())
            putStringSet("interrupted_backup_failed_ids", emptySet())
            apply()
        }
        
        // Firebase Auth sign-out
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Auth sign out error: ${e.message}")
        }

        DataDownloadManager.updateProgress(
            DataDownloadManager.DownloadProgress(
                isDownloading = false,
                isCompleted = true,
                downloadedFiles = downloadedCount,
                totalFiles = downloadedCount
            )
        )

        // Show final completion notification
        val doneNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Archive Download Complete")
            .setContentText("Successfully backed up $downloadedCount media files and signed out.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, doneNotification)
    }

    private fun getRemoteFileSize(urlStr: String): Long {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch content length for $urlStr: ${e.message}")
            -1L
        }
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
            .setContentTitle("Downloading Cloud Media Archive")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(maxProgress, progress, maxProgress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotificationError(message: String) {
        val errorNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Archive Download Failed")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, errorNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Data Sync & Archive",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress of media archives when signing out."
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

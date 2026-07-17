package com.example.service

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
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
import java.io.InputStream
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

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
                performDownload()
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

    private suspend fun performDownload() = withContext(Dispatchers.IO) {
        DataDownloadManager.updateProgress(
            DataDownloadManager.DownloadProgress(
                isDownloading = true,
                totalFiles = 0,
                downloadedFiles = 0,
                currentFileName = "Fetching items list..."
            )
        )

        // Fetch all items from repository
        val allItems = repository.getAllItems()
        
        // Filter cloud media items (images, videos, audios with web URLs)
        val mediaItems = allItems.filter { item ->
            val hasWebUrl = item.content.startsWith("http://") || item.content.startsWith("https://")
            val isMedia = item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO || item.type == SavedItemType.AUDIO
            isMedia && hasWebUrl
        }

        if (mediaItems.isEmpty()) {
            Log.d(TAG, "No media files to download from cloud.")
            finalizeSignOutAndComplete(0)
            return@withContext
        }

        val totalFilesCount = mediaItems.size
        
        // Determine total size of all items (by requesting headers or content lengths)
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
            val size = getRemoteFileSize(item.content)
            sizes[i] = size
            if (size > 0L) {
                totalBytesExpected += size
            }
        }

        DataDownloadManager.updateProgress(
            DataDownloadManager.progress.value.copy(
                totalBytes = totalBytesExpected
            )
        )

        var totalDownloadedBytes = 0L
        val startDownloadTimeMs = System.currentTimeMillis()

        for (index in mediaItems.indices) {
            val item = mediaItems[index]
            val fileName = getFileNameFromUrl(item.content, item.id, item.type)
            val mimeType = getMimeTypeFromType(item.type)

            var downloadSuccess = false
            var retries = 3
            var lastError: Exception? = null

            while (!downloadSuccess && retries > 0) {
                try {
                    val request = Request.Builder().url(item.content).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Server returned HTTP code ${response.code}")
                        }

                        val body = response.body ?: throw Exception("Response body is empty")
                        val inputStream = body.byteStream()
                        
                        saveFileToDownloads(
                            context = this@DataDownloadService,
                            fileName = fileName,
                            mimeType = mimeType,
                            byteStream = inputStream
                        ) { bytesWritten ->
                            totalDownloadedBytes += bytesWritten
                            
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
                        downloadSuccess = true
                    }
                } catch (e: Exception) {
                    retries--
                    lastError = e
                    Log.w(TAG, "Retry failed for $fileName. Retries left: $retries", e)
                    delay(2000) // Delay before retry
                }
            }

            if (!downloadSuccess) {
                // We propagate the exception if all retries failed
                throw lastError ?: Exception("Failed to download $fileName")
            }
        }

        finalizeSignOutAndComplete(totalFilesCount)
    }

    private fun finalizeSignOutAndComplete(downloadedCount: Int) {
        Log.i(TAG, "Download finished. Signing out user...")
        
        // Remove simulated preference
        val prefs = getSharedPreferences("second_brain_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("simulated_email").apply()
        
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

    private fun saveFileToDownloads(
        context: Context,
        fileName: String,
        mimeType: String?,
        byteStream: InputStream,
        onBytesWritten: (Int) -> Unit
    ) {
        val contentResolver = context.contentResolver
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                if (mimeType != null) {
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SecondBrainArchive")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (byteStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            onBytesWritten(bytesRead)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                } catch (e: Exception) {
                    contentResolver.delete(uri, null, null)
                    throw e
                }
            } else {
                throw Exception("Failed to insert MediaStore download record")
            }
        } else {
            // Fallback for pre-Q devices
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val archiveDir = File(downloadsDir, "SecondBrainArchive")
            if (!archiveDir.exists()) {
                archiveDir.mkdirs()
            }
            val targetFile = File(archiveDir, fileName)
            FileOutputStream(targetFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (byteStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    onBytesWritten(bytesRead)
                }
            }
        }
    }

    private fun getFileNameFromUrl(urlString: String, itemId: String, type: SavedItemType): String {
        return try {
            val path = URL(urlString).path
            val name = path.substring(path.lastIndexOf('/') + 1)
            if (name.contains(".") && name.length > 5) {
                name
            } else {
                getDefaultName(itemId, type)
            }
        } catch (e: Exception) {
            getDefaultName(itemId, type)
        }
    }

    private fun getDefaultName(itemId: String, type: SavedItemType): String {
        val ext = when (type) {
            SavedItemType.IMAGE -> "jpg"
            SavedItemType.VIDEO -> "mp4"
            SavedItemType.AUDIO -> "mp3"
            else -> "bin"
        }
        return "archive_$itemId.$ext"
    }

    private fun getMimeTypeFromType(type: SavedItemType): String? {
        return when (type) {
            SavedItemType.IMAGE -> "image/jpeg"
            SavedItemType.VIDEO -> "video/mp4"
            SavedItemType.AUDIO -> "audio/mpeg"
            else -> null
        }
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

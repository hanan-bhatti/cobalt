/*
 * Second Brain - A universal capture and personal knowledge archive
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

package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.utils.MediaProjectionCache

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val timeoutHandler = Handler(android.os.Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        backgroundHandler = Handler(handlerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var resultCode = intent?.getIntExtra("result_code", 0) ?: 0
        var resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("result_data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("result_data")
        }

        if (resultCode == 0 || resultData == null) {
            // Retrieve from persistent token cache
            resultCode = MediaProjectionCache.resultCode
            resultData = MediaProjectionCache.resultData
        } else {
            // Cache the newly granted token
            MediaProjectionCache.resultCode = resultCode
            MediaProjectionCache.resultData = resultData
        }

        if (resultCode != 0 && resultData != null) {
            // Promote to foreground service (required for media projection in Android 10+)
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            captureScreen(resultCode, resultData)
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun captureScreen(resultCode: Int, resultData: Intent) {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        try {
            // Use active MediaProjection if alive, otherwise create a new one from cached token
            var activeProj = MediaProjectionCache.activeMediaProjection
            if (activeProj == null) {
                activeProj = mediaProjectionManager.getMediaProjection(resultCode, resultData)
                MediaProjectionCache.activeMediaProjection = activeProj
            }
            mediaProjection = activeProj

            // Register callback required by Android 14+ (SDK 34+) to avoid IllegalStateException
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            super.onStop()
                            MediaProjectionCache.activeMediaProjection = null
                            cleanup()
                        }
                    }, Handler(android.os.Looper.getMainLooper()))
                } catch (e: Exception) {
                    // Ignore registration if already registered or failed
                }
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCaptureVirtualDisplay",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                backgroundHandler
            )

            var frameCaptured = false
            timeoutRunnable = Runnable {
                if (!frameCaptured) {
                    frameCaptured = true
                    callback?.onError("Screen capture timed out.")
                    cleanup()
                    stopSelf()
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable!!, CAPTURE_TIMEOUT_MS)

            imageReader!!.setOnImageAvailableListener({ reader ->
                if (frameCaptured) return@setOnImageAvailableListener
                val image = reader.acquireLatestImage()
                if (image != null) {
                    frameCaptured = true
                    timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    
                    capturedBitmap = cleanBitmap
                    callback?.onBitmapCaptured(cleanBitmap)

                    cleanup()
                    stopSelf()
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
            callback?.onError(e.message ?: "Capture failed")
            cleanup()
            stopSelf()
        }
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            
            // Cleanly stop mediaProjection to prevent leaks and release system resources on Android 14
            mediaProjection?.stop()
            mediaProjection = null
            MediaProjectionCache.activeMediaProjection = null
            MediaProjectionCache.resultData = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
        cleanup()
        handlerThread?.quitSafely()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture Active")
            .setContentText("Capturing background screen for link extraction...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    interface CaptureCallback {
        fun onBitmapCaptured(bitmap: Bitmap)
        fun onError(error: String)
    }

    companion object {
        private const val NOTIFICATION_ID = 9284
        private const val CHANNEL_ID = "screen_capture_service_channel"
        private const val CAPTURE_TIMEOUT_MS = 4000L

        var capturedBitmap: Bitmap? = null
        var callback: CaptureCallback? = null
    }
}

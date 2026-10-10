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

package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.repository.CobaltRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.overlay.OverlayState
import com.example.ui.overlay.UnifiedOverlay
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class CobaltOcrOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    private var isExpanded = false
    private val overlayState = mutableStateOf(OverlayState.COLLAPSED)

    private val EXTRA_TOUCH_WIDTH_DP = 16
    private val EXPANDED_MARGIN_DP = 12
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: CobaltRepository
    private lateinit var settingsRepo: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    private fun getEdgePanelSide(): String = prefs.getString("edge_panel_side", "Right") ?: "Right"
    private fun getEdgePanelYPercent(): Float = prefs.getFloat("edge_panel_y_percent", 0.4f)
    private fun getEdgePanelThickness(): Int = prefs.getInt("edge_panel_thickness", 6)
    private fun getEdgePanelHeight(): Int = prefs.getInt("edge_panel_height", 100)
    private fun getEdgePanelOpacity(): Float = prefs.getFloat("edge_panel_opacity", 0.7f)
    private fun getAnimPreset(): String = prefs.getString("edge_panel_anim_preset", "Smooth") ?: "Smooth"

    private fun isDarkTheme(): Boolean {
        val theme = prefs.getString("theme_mode", "Light") ?: "Light"
        return when (theme) {
            "Dark" -> true
            "Light" -> false
            else -> {
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "edge_panel_height" || key == "edge_panel_thickness" ||
            key == "edge_panel_opacity" || key == "edge_panel_side" ||
            key == "edge_panel_y_percent" || key == "floating_ocr_enabled" ||
            key == "dynamic_color" || key == "theme_mode" ||
            key == "edge_panel_anim_preset"
        ) {
            mainHandler.post {
                updateViewLayoutAndStyle()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post {
            updateViewLayoutAndStyle()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = CobaltRepository(applicationContext)
        settingsRepo = SettingsRepository(applicationContext)

        prefs = applicationContext.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        overlayLifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!com.example.utils.PermissionUtils.hasOverlayPermission(this)) {
            Toast.makeText(this, "Enable Draw Over Other Apps permission to use Floating OCR", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == "com.example.ACTION_TOGGLE_PANEL") {
            mainHandler.post {
                toggleExpand()
            }
            return START_STICKY
        }

        if (composeView == null) {
            createOverlayViews()
        } else {
            updateViewLayoutAndStyle()
        }

        return START_STICKY
    }

    private fun updateSystemGestureExclusions() {
        val root = composeView ?: return
        val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
        val w = lp.width
        val h = lp.height
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isExpanded) {
                root.systemGestureExclusionRects = emptyList()
                return
            }
            if (w > 0 && h > 0) {
                val rect = android.graphics.Rect(0, 0, w, h)
                root.systemGestureExclusionRects = listOf(rect)
            }
        }
    }

    private fun createOverlayViews() {
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val thickness = getEdgePanelThickness()
        val height = getEdgePanelHeight()

        val params = WindowManager.LayoutParams(
            dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP),
            dpToPx(height),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or (if (side == "Right") Gravity.END else Gravity.START)
            x = 0
            y = calculateYPosition(yPercent, height)
        }

        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            overlayLifecycleOwner?.let { owner ->
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
            }
            setContent {
                MyApplicationTheme(
                    darkTheme = isDarkTheme(),
                    dynamicColor = prefs.getBoolean("dynamic_color", true)
                ) {
                    UnifiedOverlay(
                        overlayState = overlayState.value,
                        side = getEdgePanelSide(),
                        yPercent = getEdgePanelYPercent(),
                        thickness = getEdgePanelThickness(),
                        handleHeight = getEdgePanelHeight(),
                        opacity = getEdgePanelOpacity(),
                        animPreset = getAnimPreset(),
                        repository = repository,
                        onExpand = { expandPanel() },
                        onDismiss = { collapsePanel() },
                        onCollapseFinished = { onPanelCollapsed() },
                        onLaunchOcr = { launchOcrCapture() },
                        onLaunchLinkCapture = { launchLinkCapture() },
                        onOpenMainApp = { itemId -> openMainApp(itemId) }
                    )
                }
            }
        }

        // Outside touch detection when expanded
        compose.setOnTouchListener { _, event ->
            if (isExpanded && event.action == MotionEvent.ACTION_OUTSIDE) {
                collapsePanel()
                true
            } else {
                false
            }
        }

        composeView = compose

        compose.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateSystemGestureExclusions()
        }

        windowManager.addView(compose, params)
        updateSystemGestureExclusions()
    }

    private fun expandPanel() {
        val root = composeView ?: return
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val expandedWidthDp = if (isLandscape) 300 else 260
        val expandedHeightDp = if (isLandscape) 280 else 400

        isExpanded = true
        overlayState.value = OverlayState.EXPANDED

        val params = root.layoutParams as WindowManager.LayoutParams
        params.gravity = Gravity.CENTER_VERTICAL or (if (side == "Right") Gravity.END else Gravity.START)
        params.y = calculateYPosition(yPercent, expandedHeightDp)
        params.width = dpToPx(expandedWidthDp + EXPANDED_MARGIN_DP)
        params.height = dpToPx(expandedHeightDp)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        try {
            windowManager.updateViewLayout(root, params)
        } catch (_: Exception) {}

        updateSystemGestureExclusions()
    }

    private fun collapsePanel() {
        if (!isExpanded) return
        isExpanded = false
        // Trigger Compose spring morph back to COLLAPSED state
        overlayState.value = OverlayState.COLLAPSED
    }

    private fun onPanelCollapsed() {
        val root = composeView ?: return
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val thickness = getEdgePanelThickness()
        val height = getEdgePanelHeight()

        val params = root.layoutParams as WindowManager.LayoutParams
        params.gravity = Gravity.CENTER_VERTICAL or (if (side == "Right") Gravity.END else Gravity.START)
        params.y = calculateYPosition(yPercent, height)
        params.width = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
        params.height = dpToPx(height)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        try {
            windowManager.updateViewLayout(root, params)
        } catch (_: Exception) {}

        updateViewLayoutAndStyle()
        updateSystemGestureExclusions()
    }

    private fun toggleExpand() {
        if (isExpanded) {
            collapsePanel()
        } else {
            expandPanel()
        }
    }

    private fun updateViewLayoutAndStyle() {
        val root = composeView ?: return
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val thickness = getEdgePanelThickness()
        val height = getEdgePanelHeight()

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val expandedWidthDp = if (isLandscape) 300 else 260
        val expandedHeightDp = if (isLandscape) 280 else 400

        val params = root.layoutParams as WindowManager.LayoutParams
        params.gravity = Gravity.CENTER_VERTICAL or (if (side == "Right") Gravity.END else Gravity.START)
        params.y = calculateYPosition(yPercent, if (isExpanded) expandedHeightDp else height)

        if (!isExpanded) {
            params.width = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
            params.height = dpToPx(height)
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            params.width = dpToPx(expandedWidthDp + EXPANDED_MARGIN_DP)
            params.height = dpToPx(expandedHeightDp)
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        try {
            windowManager.updateViewLayout(root, params)
        } catch (_: Exception) {}
        updateSystemGestureExclusions()
    }

    private fun launchOcrCapture() {
        val intent = Intent(this, OcrCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun launchLinkCapture() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("NAVIGATE_TO", "capture")
            putExtra("CAPTURE_TYPE", "link")
        }
        startActivity(intent)
    }

    private fun openMainApp(itemId: String? = null) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (itemId != null) {
                putExtra("OPEN_ITEM_ID", itemId)
            }
        }
        startActivity(intent)
    }

    private fun removeOverlayViews() {
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            composeView = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(applicationContext, CobaltOcrOverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("CobaltOcrOverlay", "Failed to restart after task removal: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        serviceScope.cancel()
        overlayLifecycleOwner?.onDestroy()
        overlayLifecycleOwner = null
        removeOverlayViews()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun calculateYPosition(yPercent: Float, heightDp: Int = 100): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        val targetCenterY = screenHeight * yPercent
        val halfHeight = dpToPx(heightDp) / 2
        val minCenterY = halfHeight.toFloat() + dpToPx(24)
        val maxCenterY = (screenHeight.toFloat() - halfHeight - dpToPx(24)).coerceAtLeast(minCenterY)
        val clampedCenterY = targetCenterY.coerceIn(minCenterY, maxCenterY)
        return (clampedCenterY - (screenHeight / 2f)).toInt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating OCR Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Floating OCR Assistant active in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val appIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cobalt Assistant Active")
            .setContentText("Tap the side handle on your screen or swipe from the edge to open options.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun onCreate() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 9283
        private const val CHANNEL_ID = "floating_ocr_service_channel"
    }
}

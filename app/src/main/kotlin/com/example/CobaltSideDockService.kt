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
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.repository.CobaltRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.dock.SideDockContent
import com.example.ui.theme.MyApplicationTheme
import com.example.util.OverlayLifecycleOwner
import com.example.utils.DevicePerformance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * FrameLayout wrapper providing key event interception for 3-button navigation
 * and hardware back key handling on WindowManager system overlays.
 */
private class DockRootLayout(
    context: Context,
    private val dispatcher: androidx.activity.OnBackPressedDispatcher
) : FrameLayout(context) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                if (dispatcher.hasEnabledCallbacks()) {
                    dispatcher.onBackPressed()
                    return true
                }
            } else if (event.action == KeyEvent.ACTION_DOWN) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * CobaltSideDockService: Ultra-lightweight, 60fps/120fps system overlay service.
 * Employs a decoupled two-window architecture:
 * 1. A featherweight, idle handle overlay consuming zero CPU/GPU at rest.
 * 2. An on-demand MATCH_PARENT Compose window for fluid GPU RenderThread animations.
 */
class CobaltSideDockService : Service() {

    private lateinit var windowManager: WindowManager
    private var handleContainerView: FrameLayout? = null
    private var handlePillView: View? = null

    private var dockRootView: View? = null
    private var dockComposeView: ComposeView? = null
    private var scrimView: View? = null
    private var dismissDockAction: (() -> Unit)? = null
    private var lastBlurRadiusPx = -1
    private var dockLifecycleOwner: OverlayLifecycleOwner? = null
    private var isDockExpanded = false

    private val EXTRA_TOUCH_WIDTH_DP = 16
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: CobaltRepository
    private lateinit var settingsRepo: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    private fun getEdgePanelSide(): String = prefs.getString("edge_panel_side", "Right") ?: "Right"
    private fun getEdgePanelYPercent(): Float = prefs.getFloat("edge_panel_y_percent", 0.4f)
    private fun getEdgePanelThickness(): Int = prefs.getInt("edge_panel_thickness", 6)
    private fun getEdgePanelHeight(): Int = prefs.getInt("edge_panel_height", 100)
    private fun getEffectiveHandleHeightDp(): Int {
        val base = getEdgePanelHeight()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) {
            val screenHDp = resources.configuration.screenHeightDp
            (screenHDp * 0.14f).toInt().coerceIn(44, 68)
        } else {
            base
        }
    }
    private fun getEdgePanelOpacity(): Float = prefs.getFloat("edge_panel_opacity", 0.7f)
    private fun getAnimPreset(): String = prefs.getString("edge_panel_anim_preset", "Smooth") ?: "Smooth"
    private fun getAnimStiffness(): Float = prefs.getFloat("edge_panel_anim_stiffness", 340f)
    private fun getAnimDamping(): Float = prefs.getFloat("edge_panel_anim_damping", 0.72f)
    private fun getBlurRadius(): Int = prefs.getInt("blur_radius", 25)
    private fun getBlurOpacity(): Float = prefs.getFloat("blur_opacity", 0.30f)
    private fun canUseBlur(): Boolean {
        if (!DevicePerformance.shouldUseBlur(this)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                windowManager.isCrossWindowBlurEnabled
            } catch (_: Throwable) {
                false
            }
        }
        return false
    }

    private fun isDarkTheme(): Boolean {
        val theme = prefs.getString("theme_mode", "Light") ?: "Light"
        return when (theme) {
            "Dark" -> true
            "Light" -> false
            else -> {
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in listOf(
                "edge_panel_height", "edge_panel_thickness", "edge_panel_opacity",
                "edge_panel_side", "edge_panel_y_percent", "floating_ocr_enabled",
                "dynamic_color", "theme_mode", "edge_panel_anim_preset",
                "edge_panel_anim_stiffness", "edge_panel_anim_damping",
                "force_disable_blur", "blur_radius", "blur_opacity"
            )
        ) {
            mainHandler.post {
                updateHandleLayoutAndStyle()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post {
            updateHandleLayoutAndStyle()
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
            Toast.makeText(this, "Enable Draw Over Other Apps permission to use Cobalt SideDock", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == "com.example.ACTION_TOGGLE_PANEL") {
            mainHandler.post {
                toggleDock()
            }
            return START_STICKY
        }

        if (handleContainerView == null) {
            createHandleOverlay()
        } else {
            updateHandleLayoutAndStyle()
        }

        return START_STICKY
    }

    // ══════════════════════════════════════════════════════════
    // WINDOW 1: FEATHERWEIGHT EDGE HANDLE
    // ══════════════════════════════════════════════════════════

    private fun createHandleOverlay() {
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val thickness = getEdgePanelThickness()
        val height = getEffectiveHandleHeightDp()
        val opacity = getEdgePanelOpacity()

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

        val root = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        val pill = View(this).apply {
            background = createHandleDrawable(side)
            alpha = opacity
        }

        val pillParams = FrameLayout.LayoutParams(
            dpToPx(thickness),
            dpToPx(height)
        ).apply {
            gravity = (if (side == "Right") Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        }
        root.addView(pill, pillParams)

        // Gesture handling on the handle
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDraggingY = false
        var isSwipingX = false
        var isLongPressDetected = false
        var touchActive = false
        var downTime = 0L

        val longPressRunnable = Runnable {
            if (touchActive && !isSwipingX) {
                isLongPressDetected = true
                try {
                    root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                } catch (_: Exception) {}
            }
        }

        root.setOnTouchListener { _, event ->
            val layoutParams = root.layoutParams as WindowManager.LayoutParams
            val currentSide = getEdgePanelSide()
            val touchSlopPx = ViewConfiguration.get(this@CobaltSideDockService).scaledTouchSlop
            val swipeThresholdPx = dpToPx(14)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchActive = true
                    downTime = System.currentTimeMillis()
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDraggingY = false
                    isSwipingX = false
                    isLongPressDetected = false

                    mainHandler.postDelayed(longPressRunnable, 450)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!touchActive) return@setOnTouchListener false
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (isDraggingY) {
                        layoutParams.y = (initialY + deltaY).toInt()
                        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        val insetDp = if (isLandscape) 80 else 160
                        val usableHeight = (resources.displayMetrics.heightPixels - dpToPx(insetDp)).coerceAtLeast(dpToPx(60))
                        val halfHeight = usableHeight / 2
                        if (layoutParams.y < -halfHeight) layoutParams.y = -halfHeight
                        if (layoutParams.y > halfHeight) layoutParams.y = halfHeight
                        windowManager.updateViewLayout(root, layoutParams)
                        return@setOnTouchListener true
                    }

                    if (isSwipingX) return@setOnTouchListener true

                    val absDeltaX = Math.abs(deltaX)
                    val absDeltaY = Math.abs(deltaY)

                    if (absDeltaX > touchSlopPx || absDeltaY > touchSlopPx) {
                        if (absDeltaX > absDeltaY * 1.4f) {
                            val isInward = if (currentSide == "Right") deltaX < 0 else deltaX > 0
                            if (isInward) {
                                mainHandler.removeCallbacks(longPressRunnable)
                                isSwipingX = true
                            }
                        } else if (absDeltaY > touchSlopPx) {
                            if (isLongPressDetected) {
                                isDraggingY = true
                            } else {
                                mainHandler.removeCallbacks(longPressRunnable)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchActive = false
                    mainHandler.removeCallbacks(longPressRunnable)

                    if (event.action == MotionEvent.ACTION_UP) {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        val absDeltaX = Math.abs(deltaX)
                        val absDeltaY = Math.abs(deltaY)

                        if (isDraggingY) {
                            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            val insetDp = if (isLandscape) 80 else 160
                            val usableHeight = (resources.displayMetrics.heightPixels - dpToPx(insetDp)).coerceAtLeast(dpToPx(60))
                            val halfHeight = usableHeight / 2
                            val finalY = layoutParams.y
                            val newPercent = (finalY + halfHeight).toFloat() / usableHeight.toFloat()
                            settingsRepo.setEdgePanelYPercent(newPercent.coerceIn(0.0f, 1.0f))
                        } else if (isSwipingX || (absDeltaX > swipeThresholdPx && absDeltaX > absDeltaY * 1.4f)) {
                            val isInward = if (currentSide == "Right") deltaX < 0 else deltaX > 0
                            if (isInward) {
                                openDock()
                            }
                        } else {
                            val duration = System.currentTimeMillis() - downTime
                            if (absDeltaX < touchSlopPx && absDeltaY < touchSlopPx && duration < 450) {
                                openDock()
                            }
                        }
                    }

                    isDraggingY = false
                    isSwipingX = false
                    isLongPressDetected = false
                    true
                }

                else -> false
            }
        }

        handleContainerView = root
        handlePillView = pill

        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateSystemGestureExclusions()
        }

        windowManager.addView(root, params)
        updateSystemGestureExclusions()
    }

    private fun createHandleDrawable(side: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#3B82F6")) // Accent blue or theme accent
            val radiusPx = dpToPx(8).toFloat()
            cornerRadii = if (side == "Right") {
                floatArrayOf(radiusPx, radiusPx, 0f, 0f, 0f, 0f, radiusPx, radiusPx)
            } else {
                floatArrayOf(0f, 0f, radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f)
            }
        }
    }

    private fun updateHandleLayoutAndStyle() {
        val root = handleContainerView ?: return
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val thickness = getEdgePanelThickness()
        val height = getEffectiveHandleHeightDp()
        val opacity = getEdgePanelOpacity()

        handlePillView?.let { pill ->
            pill.background = createHandleDrawable(side)
            pill.alpha = opacity
            pill.layoutParams = FrameLayout.LayoutParams(
                dpToPx(thickness),
                dpToPx(height)
            ).apply {
                gravity = (if (side == "Right") Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            }
        }

        val params = root.layoutParams as WindowManager.LayoutParams
        params.gravity = Gravity.CENTER_VERTICAL or (if (side == "Right") Gravity.END else Gravity.START)
        params.y = calculateYPosition(yPercent, height)
        params.width = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
        params.height = dpToPx(height)

        try {
            windowManager.updateViewLayout(root, params)
        } catch (_: Exception) {}

        updateSystemGestureExclusions()
    }

    private fun updateSystemGestureExclusions() {
        val root = handleContainerView ?: return
        val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
        val w = lp.width
        val h = lp.height
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isDockExpanded || handlePillView?.visibility != View.VISIBLE) {
                root.systemGestureExclusionRects = emptyList()
                return
            }
            if (w > 0 && h > 0) {
                val rect = android.graphics.Rect(0, 0, w, h)
                root.systemGestureExclusionRects = listOf(rect)
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // WINDOW 2: FULL-SCREEN COMPOSE DOCK OVERLAY
    // ══════════════════════════════════════════════════════════

    private fun openDock() {
        if (isDockExpanded) return
        isDockExpanded = true

        val location = IntArray(2)
        handlePillView?.getLocationOnScreen(location)
        val density = resources.displayMetrics.density
        val pillHeightPx = handlePillView?.height ?: dpToPx(getEffectiveHandleHeightDp())
        val pillWidthPx = handlePillView?.width ?: dpToPx(getEdgePanelThickness())
        val handleCenterYPx = location[1] + (pillHeightPx / 2f)
        val exactHandleCenterYDp = if (location[1] > 0) handleCenterYPx / density else null

        // Hide handle while dock is open
        handlePillView?.visibility = View.INVISIBLE
        updateSystemGestureExclusions()

        val isBlurSupported = canUseBlur()
        val currentBlurRadius = getBlurRadius()
        val currentBlurOpacity = getBlurOpacity()

        val isRightSide = getEdgePanelSide() == "Right"
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val cardWidthDp = if (isLandscape) 330 else 280
        val screenHDp = resources.configuration.screenHeightDp
        val cardHeightDp = if (isLandscape) (screenHDp - 48).coerceAtMost(330) else 460
        val edgeMarginDp = 12

        val rawCenterYDp = exactHandleCenterYDp ?: (screenHDp * getEdgePanelYPercent())
        val minTopDp = if (isLandscape) 12f else 36f
        val maxTopDp = (screenHDp - cardHeightDp - (if (isLandscape) 12f else 36f)).coerceAtLeast(minTopDp)
        val baseTopDp = rawCenterYDp - (cardHeightDp / 2f)
        val effectiveTopDp = baseTopDp.coerceIn(minTopDp, maxTopDp)

        val fullScreenParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            gravity = Gravity.TOP or Gravity.START
        }

        val lifecycleOwner = OverlayLifecycleOwner(
            fallbackOnBackPressed = Runnable {
                dismissDockAction?.invoke() ?: closeDock()
            }
        ).apply { onStart() }
        dockLifecycleOwner = lifecycleOwner

        val rootLayout = DockRootLayout(this, lifecycleOwner.onBackPressedDispatcher).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeOnBackPressedDispatcherOwner(lifecycleOwner)
        }
        dockRootView = rootLayout

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeOnBackPressedDispatcherOwner(lifecycleOwner)

            setContent {
                val isDark = isDarkTheme()
                val customTabsUriHandler = androidx.compose.runtime.remember(this@CobaltSideDockService, isDark) {
                    object : androidx.compose.ui.platform.UriHandler {
                        override fun openUri(uri: String) {
                            dismissDockAction?.invoke() ?: closeDock()
                            com.example.util.CobaltCustomTabsLauncher.launch(
                                context = this@CobaltSideDockService,
                                url = uri,
                                isDarkTheme = isDark
                            )
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalOnBackPressedDispatcherOwner provides lifecycleOwner,
                    androidx.compose.ui.platform.LocalUriHandler provides customTabsUriHandler
                ) {
                    MyApplicationTheme(
                        darkTheme = isDark,
                        dynamicColor = prefs.getBoolean("dynamic_color", true)
                    ) {
                        SideDockContent(
                            side = getEdgePanelSide(),
                            yPercent = getEdgePanelYPercent(),
                            handleCenterYDp = exactHandleCenterYDp,
                            handleHeightDp = getEffectiveHandleHeightDp(),
                            handleThicknessDp = getEdgePanelThickness(),
                            handleOpacity = getEdgePanelOpacity(),
                            animPreset = getAnimPreset(),
                            animStiffness = getAnimStiffness(),
                            animDamping = getAnimDamping(),
                            useBlur = false,
                            blurRadius = currentBlurRadius,
                            blurOpacity = currentBlurOpacity,
                            repository = repository,
                            onRegisterDismiss = { action ->
                                dismissDockAction = action
                            },
                            onClose = {
                                // Handled via SideDockContent's dismiss animation
                            },
                            onCloseFinished = {
                                closeDock()
                            },
                            onLaunchOcr = {
                                dismissDockAction?.invoke() ?: closeDock()
                                launchOcrCapture()
                            },
                            onLaunchLink = {
                                dismissDockAction?.invoke() ?: closeDock()
                                launchLinkCapture()
                            },
                            onOpenMainApp = { itemId, destination ->
                                dismissDockAction?.invoke() ?: closeDock()
                                openMainApp(itemId, destination)
                            }
                        )
                    }
                }
            }
        }
        dockComposeView = composeView

        rootLayout.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        rootLayout.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val dispatcher = v.findOnBackInvokedDispatcher()
                    if (dispatcher != null) {
                        lifecycleOwner.onBackPressedDispatcher.setOnBackInvokedDispatcher(dispatcher)
                    }
                }
                v.requestFocus()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })

        try {
            windowManager.addView(rootLayout, fullScreenParams)
        } catch (e: Exception) {
            android.util.Log.e("CobaltSideDock", "Failed to add dock window: ${e.message}", e)
            closeDock()
        }
    }

    private fun closeDock() {
        isDockExpanded = false
        dockRootView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
            dockRootView = null
        }
        dockComposeView = null
        scrimView = null
        dismissDockAction = null
        lastBlurRadiusPx = -1
        dockLifecycleOwner?.onDestroy()
        dockLifecycleOwner = null

        // Restore handle view visibility
        handlePillView?.visibility = View.VISIBLE
        updateSystemGestureExclusions()
    }

    private fun toggleDock() {
        if (isDockExpanded) {
            closeDock()
        } else {
            openDock()
        }
    }

    // ══════════════════════════════════════════════════════════
    // ACTION HANDLERS
    // ══════════════════════════════════════════════════════════

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

    private fun openMainApp(itemId: String?, destination: String? = null) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (itemId != null) {
                putExtra("OPEN_ITEM_ID", itemId)
            }
            if (destination != null) {
                putExtra("NAVIGATE_TO", destination)
            }
        }
        startActivity(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (settingsRepo.isFloatingOcrEnabled.value) {
            val restartIntent = Intent(applicationContext, CobaltSideDockService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restartIntent)
                } else {
                    startService(restartIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("CobaltSideDock", "Failed to restart service: ${e.message}", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        serviceScope.cancel()

        closeDock()

        handleContainerView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            handleContainerView = null
            handlePillView = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun calculateYPosition(yPercent: Float, heightDp: Int = 100): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val insetDp = if (isLandscape) 80 else 160
        val usableHeight = (resources.displayMetrics.heightPixels - dpToPx(insetDp)).coerceAtLeast(dpToPx(60))
        val halfHeight = usableHeight / 2
        return (yPercent.coerceIn(0f, 1f) * usableHeight - halfHeight).toInt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cobalt SideDock Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Cobalt SideDock active in the background"
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
            .setContentTitle("Cobalt SideDock Active")
            .setContentText("Swipe or tap the side dock handle to access quick OCR, notes, and recents.")
            .setSmallIcon(R.drawable.ic_custom_school)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 9283
        private const val CHANNEL_ID = "cobalt_sidedock_service_channel"
    }
}

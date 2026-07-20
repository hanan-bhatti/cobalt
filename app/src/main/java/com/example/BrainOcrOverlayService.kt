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
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import androidx.core.app.NotificationCompat
import androidx.compose.ui.graphics.toArgb
import com.example.ui.theme.*
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.repository.SecondBrainRepository
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrainOcrOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var containerView: FrameLayout? = null

    private var handleView: View? = null
    private var panelView: View? = null
    private var noteInputRef: EditText? = null
    private var isExpanded = false
    private var panelAnimator: ValueAnimator? = null
    private val EXTRA_TOUCH_WIDTH_DP = 16
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: SecondBrainRepository
    private lateinit var settingsRepo: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    private fun getEdgePanelSide(): String = prefs.getString("edge_panel_side", "Right") ?: "Right"
    private fun getEdgePanelYPercent(): Float = prefs.getFloat("edge_panel_y_percent", 0.4f)
    private fun getEdgePanelThickness(): Int = prefs.getInt("edge_panel_thickness", 6)
    private fun getEdgePanelHeight(): Int = prefs.getInt("edge_panel_height", 100)
    private fun getEdgePanelOpacity(): Float = prefs.getFloat("edge_panel_opacity", 0.7f)

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

    private fun getThemedColor(
        fallbackLightColor: Int,
        fallbackDarkColor: Int,
        dynamicLightColorRes: Int?,
        dynamicDarkColorRes: Int?
    ): Int {
        val isDark = isDarkTheme()
        val useDynamic = prefs.getBoolean("dynamic_color", true)
        if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicLightColorRes != null && dynamicDarkColorRes != null) {
            val resId = if (isDark) dynamicDarkColorRes else dynamicLightColorRes
            try {
                return resources.getColor(resId, theme)
            } catch (e: Exception) {
                // Fallback
            }
        }
        return if (isDark) fallbackDarkColor else fallbackLightColor
    }

    private fun getAccentColor(): Int {
        return getThemedColor(
            Primary.toArgb(),
            PrimaryDark.toArgb(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_accent1_600 else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_accent1_600 else null
        )
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "edge_panel_height" || key == "edge_panel_thickness" ||
            key == "edge_panel_opacity" || key == "edge_panel_side" ||
            key == "edge_panel_y_percent" || key == "floating_ocr_enabled" ||
            key == "dynamic_color" || key == "theme_mode"
        ) {
            Handler(Looper.getMainLooper()).post {
                updateViewLayoutAndStyle()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = SecondBrainRepository(applicationContext)
        settingsRepo = SettingsRepository(applicationContext)

        prefs = applicationContext.getSharedPreferences("second_brain_settings", Context.MODE_PRIVATE)
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
            Toast.makeText(this, "Enable Draw Over Other Apps permission to use Floating OCR", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        if (containerView == null) {
            createOverlayViews()
        } else {
            updateViewLayoutAndStyle()
        }

        return START_STICKY
    }

    private fun updateSystemGestureExclusions() {
        val root = containerView ?: return
        val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
        val w = lp.width
        val h = lp.height
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isExpanded || handleView?.visibility != View.VISIBLE) {
                root.systemGestureExclusionRects = emptyList()
                return
            }
            if (w > 0 && h > 0) {
                // Exclude the entire touch window of the handle from system back gestures.
                // This ensures swiping directly from the extreme screen edge over the handle height
                // always triggers the panel expand gesture, while system back gestures remain
                // fully functional above and below the handle.
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
        val opacity = getEdgePanelOpacity()

        // Gesture edge quirk workaround: Make window 16dp wider than the handle thickness.
        // This ensures the window extends slightly inset from the edge so android's native gesture
        // zone does not eat our swipe. The handle is aligned to the extreme edge, but swipe-detection
        // covers the whole window, capturing touches started slightly inset (e.g. ~8dp to 28dp).
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

        val rootContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        // 1. Collapsed state: Draggable thin vertical handle (like Samsung edge)
        val handle = FrameLayout(this).apply {
            val bgShape = GradientDrawable().apply {
                setColor(getAccentColor())
                val radiusPx = dpToPx(8).toFloat()
                cornerRadii = if (side == "Right") {
                    floatArrayOf(radiusPx, radiusPx, 0f, 0f, 0f, 0f, radiusPx, radiusPx)
                } else {
                    floatArrayOf(0f, 0f, radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f)
                }
            }
            background = bgShape
            alpha = opacity
            // elevation = dpToPx(4).toFloat()
        }

        // Gesture state variables
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
                    rootContainer.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                } catch (e: Exception) {
                    // Ignore haptic feedback errors
                }
            }
        }

        rootContainer.setOnTouchListener { _, event ->
            if (isExpanded) {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    collapsePanel()
                    true
                } else {
                    false
                }
            } else {
                val layoutParams = rootContainer.layoutParams as WindowManager.LayoutParams
                val currentSide = getEdgePanelSide()
                val touchSlopPx = ViewConfiguration.get(this@BrainOcrOverlayService).scaledTouchSlop
                val swipeThresholdPx = dpToPx(16) // Deliberate swipe distance threshold

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

                        mainHandler.postDelayed(longPressRunnable, 500)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!touchActive) return@setOnTouchListener false
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY

                        // 1. If actively dragging to vertically reposition:
                        if (isDraggingY) {
                            layoutParams.y = (initialY + deltaY).toInt()

                            val usableHeight = resources.displayMetrics.heightPixels - dpToPx(160)
                            val halfHeight = usableHeight / 2
                            if (layoutParams.y < -halfHeight) layoutParams.y = -halfHeight
                            if (layoutParams.y > halfHeight) layoutParams.y = halfHeight

                            windowManager.updateViewLayout(rootContainer, layoutParams)
                            return@setOnTouchListener true
                        }

                        // 2. If actively swiping horizontally:
                        if (isSwipingX) {
                            return@setOnTouchListener true
                        }

                        // 3. Disambiguate gestures
                        val absDeltaX = Math.abs(deltaX)
                        val absDeltaY = Math.abs(deltaY)

                        if (absDeltaX > touchSlopPx || absDeltaY > touchSlopPx) {
                            if (absDeltaX > absDeltaY * 1.5f) {
                                // Horizontal movement - determine if swiping correct direction (inward)
                                val isSwipeDirectionCorrect = if (currentSide == "Right") deltaX < 0 else deltaX > 0
                                if (isSwipeDirectionCorrect) {
                                    mainHandler.removeCallbacks(longPressRunnable)
                                    isSwipingX = true
                                }
                            } else if (absDeltaY > touchSlopPx) {
                                // Vertical movement - only drag if long press has fired first!
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
                                val usableHeight = resources.displayMetrics.heightPixels - dpToPx(160)
                                val halfHeight = usableHeight / 2
                                val finalY = layoutParams.y
                                val newPercent = (finalY + halfHeight).toFloat() / usableHeight.toFloat()
                                val clampedPercent = newPercent.coerceIn(0.0f, 1.0f)
                                settingsRepo.setEdgePanelYPercent(clampedPercent)
                            } else if (isSwipingX || (absDeltaX > swipeThresholdPx && absDeltaX > absDeltaY * 1.5f)) {
                                val isSwipeDirectionCorrect = if (currentSide == "Right") deltaX < 0 else deltaX > 0
                                if (isSwipeDirectionCorrect) {
                                    expandPanel()
                                }
                            } else {
                                val duration = System.currentTimeMillis() - downTime
                                if (absDeltaX < touchSlopPx && absDeltaY < touchSlopPx && duration < 500) {
                                    toggleExpand()
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
        }

        // Align the visible handle perfectly to the edge within the wider touchable window container
        val handleParams = FrameLayout.LayoutParams(
            dpToPx(thickness),
            dpToPx(height)
        ).apply {
            gravity = (if (side == "Right") Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        }
        rootContainer.addView(handle, handleParams)

        containerView = rootContainer
        handleView = handle

        // Dynamic system gesture exclusion tracking
        rootContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateSystemGestureExclusions()
        }

        windowManager.addView(rootContainer, params)
        updateSystemGestureExclusions()
    }

    private fun expandPanel() {
        val root = containerView ?: return
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val isDark = isDarkTheme()
        val thickness = getEdgePanelThickness()
        val height = getEdgePanelHeight()
        val opacity = getEdgePanelOpacity()

        isExpanded = true
        handleView?.visibility = View.VISIBLE

        // ── Color palette ──
        val surfaceColor = getThemedColor(
            Background.toArgb(),
            BackgroundDark.toArgb(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral1_10 else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral1_900 else null
        )
        val cardColor = getThemedColor(
            SurfaceVariant.toArgb(),
            SurfaceVariantDark.toArgb(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral1_100 else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral1_800 else null
        )
        val textPrimary = getThemedColor(
            OnBackground.toArgb(),
            OnBackgroundDark.toArgb(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral1_900 else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral1_50 else null
        )
        val textSecondary = getThemedColor(
            OnSurfaceVariant.toArgb(),
            OnSurfaceVariantDark.toArgb(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral2_500 else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral2_300 else null
        )
        val accent = getAccentColor()
        val accentSoft = getThemedColor(
            PrimaryContainer.toArgb(),
            PrimaryContainerDark.toArgb(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_accent1_100 else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_accent1_900 else null
        )
        val borderColor = getThemedColor(
            OutlineVariant.toArgb(),
            OutlineVariantDark.toArgb(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral2_200 else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral2_800 else null
        )
        val accentBorder = (accent and 0x00FFFFFF) or 0x33000000 // 20% alpha

        // ── Root panel ──
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(12))
            background = null
            elevation = 0f
            alpha = 0f
            scaleX = 0.96f
            scaleY = 0.96f
        }

        // ═══════════════════════════════════════════════════
        // 1. HEADER — brand pill + close button
        // ═══════════════════════════════════════════════════
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(10))
        }

        // Brand pill
        val brandPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10), dpToPx(5), dpToPx(12), dpToPx(5))
            val pillBg = GradientDrawable().apply {
                setColor(accentSoft)
                cornerRadius = dpToPx(20).toFloat()
            }
            background = pillBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val brandIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_custom_school)
            imageTintList = ColorStateList.valueOf(accent)
            layoutParams = LinearLayout.LayoutParams(dpToPx(16), dpToPx(16))
        }
        brandPill.addView(brandIcon)

        val brandLabel = TextView(this).apply {
            text = "Second Brain"
            textSize = 12f
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dpToPx(6), 0, 0, 0)
        }
        brandPill.addView(brandLabel)

        header.addView(brandPill)

        // Close button
        val closeBtn = FrameLayout(this).apply {
            val closeBg = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dpToPx(14).toFloat()
            }
            background = closeBg
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                setMargins(dpToPx(8), 0, 0, 0)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { collapsePanel() }
        }
        val closeIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_custom_close)
            imageTintList = ColorStateList.valueOf(textSecondary)
            layoutParams = FrameLayout.LayoutParams(dpToPx(14), dpToPx(14), Gravity.CENTER)
        }
        closeBtn.addView(closeIcon)
        header.addView(closeBtn)
        panel.addView(header)

        // ═══════════════════════════════════════════════════
        // 2. QUICK ACTIONS — 4-column icon grid
        // ═══════════════════════════════════════════════════
        val actionsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(10))
        }

        data class QuickAction(val iconRes: Int, val label: String, val onClick: () -> Unit)

        val actions = listOf(
            QuickAction(R.drawable.ic_custom_ocr, "OCR") {
                collapsePanel()
                launchOcrCapture()
            },
            QuickAction(R.drawable.ic_custom_text, "Note") {
                // Focus note input below
                noteInputRef?.requestFocus()
            },
            QuickAction(R.drawable.ic_custom_link, "Link") {
                collapsePanel()
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("NAVIGATE_TO", "capture")
                    putExtra("CAPTURE_TYPE", "link")
                }
                startActivity(intent)
            },
            QuickAction(R.drawable.ic_custom_home, "Open") {
                collapsePanel()
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
        )

        actions.forEach { action ->
            val actionCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true
                isFocusable = true
                setPadding(dpToPx(2), dpToPx(6), dpToPx(2), dpToPx(6))
                setOnClickListener { action.onClick() }
            }

            val iconContainer = FrameLayout(this).apply {
                val iconBg = GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = dpToPx(14).toFloat()
                }
                background = iconBg
                layoutParams = LinearLayout.LayoutParams(dpToPx(42), dpToPx(42))
            }
            val icon = ImageView(this).apply {
                setImageResource(action.iconRes)
                imageTintList = ColorStateList.valueOf(accent)
                layoutParams = FrameLayout.LayoutParams(dpToPx(20), dpToPx(20), Gravity.CENTER)
            }
            iconContainer.addView(icon)
            actionCol.addView(iconContainer)

            val label = TextView(this).apply {
                text = action.label
                textSize = 10f
                setTextColor(textSecondary)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dpToPx(4), 0, 0)
            }
            actionCol.addView(label)
            actionsGrid.addView(actionCol)
        }

        panel.addView(actionsGrid)

        // ═══════════════════════════════════════════════════
        // 3. QUICK NOTE — inline compact input bar
        // ═══════════════════════════════════════════════════
        val noteBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10), dpToPx(2), dpToPx(4), dpToPx(2))
            val noteBg = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), borderColor)
            }
            background = noteBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(42)
            ).apply {
                setMargins(0, 0, 0, dpToPx(10))
            }
        }

        val noteInput = EditText(this).apply {
            hint = "Quick thought..."
            setHintTextColor(Color.parseColor(if (isDark) "#5A5A5D" else "#AEAEB2"))
            setTextColor(textPrimary)
            textSize = 12.5f
            background = null
            setPadding(0, 0, 0, 0)
            maxLines = 1
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        noteInputRef = noteInput
        noteBar.addView(noteInput)

        val sendBtn = FrameLayout(this).apply {
            val sendBg = GradientDrawable().apply {
                setColor(accent)
                cornerRadius = dpToPx(12).toFloat()
            }
            background = sendBg
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                setMargins(dpToPx(6), 0, 0, 0)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val textContent = noteInput.text.toString().trim()
                if (textContent.isNotBlank()) {
                    serviceScope.launch {
                        val newItem = SavedItem(
                            title = "Quick Edge Note",
                            content = textContent,
                            type = SavedItemType.TEXT
                        )
                        repository.saveItem(newItem)
                        withContext(Dispatchers.Main) {
                            noteInput.setText("")
                            Toast.makeText(applicationContext, "✓ Saved to Second Brain", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        val sendIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_custom_send)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dpToPx(16), dpToPx(16), Gravity.CENTER)
        }
        sendBtn.addView(sendIcon)
        noteBar.addView(sendBtn)
        panel.addView(noteBar)

        // ═══════════════════════════════════════════════════
        // 4. RECENTS — section label + slim list
        // ═══════════════════════════════════════════════════
        val recentsLabel = TextView(this).apply {
            text = "RECENTS"
            textSize = 9.5f
            setTextColor(textSecondary)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(dpToPx(2), 0, 0, dpToPx(6))
        }
        panel.addView(recentsLabel)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(170)
            )
            isVerticalScrollBarEnabled = false
        }
        val recentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(recentContainer)
        panel.addView(scrollView)

        // Populate recent items
        serviceScope.launch {
            repository.getAllItemsFlow().collect { list ->
                val recent = list.sortedByDescending { it.timestamp }.take(5)
                withContext(Dispatchers.Main) {
                    recentContainer.removeAllViews()
                    if (recent.isEmpty()) {
                        val emptyTv = TextView(applicationContext).apply {
                            text = "No recent items yet"
                            textSize = 11f
                            setTextColor(textSecondary)
                            gravity = Gravity.CENTER
                            setPadding(0, dpToPx(20), 0, dpToPx(20))
                        }
                        recentContainer.addView(emptyTv)
                    } else {
                        recent.forEach { item ->
                            val row = LinearLayout(applicationContext).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(dpToPx(10), dpToPx(9), dpToPx(10), dpToPx(9))
                                val rowBg = GradientDrawable().apply {
                                    setColor(cardColor)
                                    cornerRadius = dpToPx(12).toFloat()
                                }
                                background = rowBg
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 0, 0, dpToPx(5))
                                }
                                isClickable = true
                                isFocusable = true
                                setOnClickListener {
                                    if (item.type == SavedItemType.LINK) {
                                        val intent =
                                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.content)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        try {
                                            startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(applicationContext, "Cannot open link", Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    } else {
                                        val intent = Intent(applicationContext, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            putExtra("OPEN_ITEM_ID", item.id)
                                        }
                                        startActivity(intent)
                                    }
                                    collapsePanel()
                                }
                            }

                            // Type icon — use app's custom icons
                            val iconRes = when (item.type) {
                                SavedItemType.LINK -> R.drawable.ic_custom_link
                                SavedItemType.IMAGE -> R.drawable.ic_custom_image
                                SavedItemType.VIDEO -> R.drawable.ic_custom_video
                                SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                                else -> R.drawable.ic_custom_text
                            }
                            val iconTint = when (item.type) {
                                SavedItemType.LINK -> Color.parseColor("#42A5F5")
                                SavedItemType.IMAGE -> Color.parseColor("#66BB6A")
                                SavedItemType.VIDEO -> Color.parseColor("#AB47BC")
                                SavedItemType.AUDIO -> accent
                                else -> accent
                            }

                            val iconWrap = FrameLayout(applicationContext).apply {
                                val iconBg = GradientDrawable().apply {
                                    setColor(if (isDark) Color.parseColor("#2A2A2E") else Color.parseColor("#F0F0F4"))
                                    cornerRadius = dpToPx(10).toFloat()
                                }
                                background = iconBg
                                layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
                            }
                            val iv = ImageView(applicationContext).apply {
                                setImageResource(iconRes)
                                imageTintList = ColorStateList.valueOf(iconTint)
                                layoutParams = FrameLayout.LayoutParams(dpToPx(16), dpToPx(16), Gravity.CENTER)
                            }
                            iconWrap.addView(iv)
                            row.addView(iconWrap)

                            // Text column
                            val col = LinearLayout(applicationContext).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(dpToPx(10), 0, 0, 0)
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }

                            val title = TextView(applicationContext).apply {
                                text = if (item.title.isNotBlank()) item.title else "Untitled"
                                textSize = 12f
                                setTextColor(textPrimary)
                                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                            }
                            col.addView(title)

                            val snippet = TextView(applicationContext).apply {
                                text = item.content.take(60)
                                textSize = 10f
                                setTextColor(textSecondary)
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                            }
                            col.addView(snippet)

                            row.addView(col)

                            // Arrow chevron
                            val arrow = ImageView(applicationContext).apply {
                                setImageResource(R.drawable.ic_custom_chevron_right)
                                imageTintList = ColorStateList.valueOf(
                                    if (isDark) Color.parseColor("#4A4A4E") else Color.parseColor("#C8C8CC")
                                )
                                layoutParams = LinearLayout.LayoutParams(dpToPx(14), dpToPx(14))
                            }
                            row.addView(arrow)

                            recentContainer.addView(row)
                        }
                    }
                }
            }
        }

        panelView = panel
        val endWidth = dpToPx(260)
        val endHeight = dpToPx(400)

        panel.layoutParams = FrameLayout.LayoutParams(endWidth, endHeight).apply {
            gravity = (if (side == "Right") Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        }
        val handle = handleView as? FrameLayout
        handle?.addView(panel)

        // ── Expand animation ──
        val params = root.layoutParams as WindowManager.LayoutParams
        panel.pivotX = if (side == "Right") endWidth.toFloat() else 0f
        panel.pivotY = endHeight / 2f

        cancelPanelAnimation()

        val startY = params.y
        val targetY = calculateYPosition(yPercent, 400)
        val startWinWidth = params.width
        val startWinHeight = params.height

        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        try {
            windowManager.updateViewLayout(root, params)
        } catch (_: Exception) {}

        val startAlpha = panel.alpha
        val startScale = panel.scaleX

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float

                panel.alpha = startAlpha + (1f - startAlpha) * fraction
                panel.scaleX = startScale + (1f - startScale) * fraction
                panel.scaleY = startScale + (1f - startScale) * fraction

                // Handle morphing animation
                val handleParams = handleView?.layoutParams as? FrameLayout.LayoutParams
                if (handleParams != null) {
                    val startHandleWidth = dpToPx(thickness)
                    val endHandleWidth = endWidth
                    val startHandleHeight = dpToPx(height)
                    val endHandleHeight = endHeight

                    handleParams.width = (startHandleWidth + (endHandleWidth - startHandleWidth) * fraction).toInt()
                    handleParams.height = (startHandleHeight + (endHandleHeight - startHandleHeight) * fraction).toInt()
                    handleView?.layoutParams = handleParams
                }

                val handleBg = handleView?.background as? GradientDrawable
                if (handleBg != null) {
                    val startRadius = dpToPx(8).toFloat()
                    val endRadius = dpToPx(22).toFloat()

                    val rTopLeft = if (side == "Right") {
                        startRadius + (endRadius - startRadius) * fraction
                    } else {
                        0f + (endRadius - 0f) * fraction
                    }
                    val rTopRight = if (side == "Right") {
                        0f + (endRadius - 0f) * fraction
                    } else {
                        startRadius + (endRadius - startRadius) * fraction
                    }
                    val rBottomRight = if (side == "Right") {
                        0f + (endRadius - 0f) * fraction
                    } else {
                        startRadius + (endRadius - startRadius) * fraction
                    }
                    val rBottomLeft = if (side == "Right") {
                        startRadius + (endRadius - startRadius) * fraction
                    } else {
                        0f + (endRadius - 0f) * fraction
                    }

                    handleBg.cornerRadii = floatArrayOf(
                        rTopLeft, rTopLeft,
                        rTopRight, rTopRight,
                        rBottomRight, rBottomRight,
                        rBottomLeft, rBottomLeft
                    )

                    val evaluator = ArgbEvaluator()
                    val startColor = getAccentColor()
                    val currentColor = evaluator.evaluate(fraction, startColor, surfaceColor) as Int
                    handleBg.setColor(currentColor)

                    val strokeW = (dpToPx(1) * fraction).toInt()
                    handleBg.setStroke(strokeW, borderColor)
                }

                handleView?.alpha = opacity + (1f - opacity) * fraction

                // Animate window position AND size in lockstep per-frame
                params.y = (startY + (targetY - startY) * fraction).toInt()
                params.width = (startWinWidth + (endWidth - startWinWidth) * fraction).toInt()
                params.height = (startWinHeight + (endHeight - startWinHeight) * fraction).toInt()
                try {
                    windowManager.updateViewLayout(root, params)
                } catch (_: Exception) {}
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    params.y = targetY
                    params.width = endWidth
                    params.height = endHeight
                    if (containerView != null) {
                        try {
                            windowManager.updateViewLayout(root, params)
                        } catch (_: Exception) {}
                    }
                    panelAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    panelAnimator = null
                }
            })
        }
        panelAnimator = animator
        animator.start()
        updateSystemGestureExclusions()
    }

    private fun collapsePanel() {
        val root = containerView ?: return
        val panel = panelView ?: return
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val thickness = getEdgePanelThickness()
        val height = getEdgePanelHeight()
        val surfaceColor = getThemedColor(
            Background.toArgb(),
            BackgroundDark.toArgb(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral1_10 else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral1_900 else null
        )
        val borderColor = getThemedColor(
            OutlineVariant.toArgb(),
            OutlineVariantDark.toArgb(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral2_200 else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_neutral2_800 else null
        )
        val opacity = getEdgePanelOpacity()

        isExpanded = false
        noteInputRef = null

        val params = root.layoutParams as WindowManager.LayoutParams
        val startWidth = params.width
        val startHeight = params.height
        val endWidth = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
        val endHeight = dpToPx(height)
        val startAlpha = panel.alpha
        val startScale = panel.scaleX

        val startY = params.y
        val targetY = calculateYPosition(yPercent, height)

        cancelPanelAnimation()

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 300
            interpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float

                panel.alpha = startAlpha * fraction
                panel.scaleX = 0.96f + (startScale - 0.96f) * fraction
                panel.scaleY = 0.96f + (startScale - 0.96f) * fraction

                // Handle morphing animation
                val handleParams = handleView?.layoutParams as? FrameLayout.LayoutParams
                if (handleParams != null) {
                    val startHandleWidth = dpToPx(thickness)
                    val endHandleWidth = startWidth
                    val startHandleHeight = dpToPx(height)
                    val endHandleHeight = startHeight

                    handleParams.width = (startHandleWidth + (endHandleWidth - startHandleWidth) * fraction).toInt()
                    handleParams.height = (startHandleHeight + (endHandleHeight - startHandleHeight) * fraction).toInt()
                    handleView?.layoutParams = handleParams
                }

                val handleBg = handleView?.background as? GradientDrawable
                if (handleBg != null) {
                    val startRadius = dpToPx(8).toFloat()
                    val endRadius = dpToPx(22).toFloat()

                    val rTopLeft = if (side == "Right") {
                        startRadius + (endRadius - startRadius) * fraction
                    } else {
                        0f + (endRadius - 0f) * fraction
                    }
                    val rTopRight = if (side == "Right") {
                        startRadius + (endRadius - startRadius) * fraction
                    } else {
                        startRadius + (endRadius - startRadius) * fraction
                    }
                    val rBottomRight = if (side == "Right") {
                        startRadius + (endRadius - startRadius) * fraction
                    } else {
                        startRadius + (endRadius - startRadius) * fraction
                    }
                    val rBottomLeft = if (side == "Right") {
                        startRadius + (endRadius - startRadius) * fraction
                    } else {
                        0f + (endRadius - 0f) * fraction
                    }

                    handleBg.cornerRadii = floatArrayOf(
                        rTopLeft, rTopLeft,
                        rTopRight, rTopRight,
                        rBottomRight, rBottomRight,
                        rBottomLeft, rBottomLeft
                    )

                    val evaluator = ArgbEvaluator()
                    val startColor = getAccentColor()
                    val currentColor = evaluator.evaluate(fraction, startColor, surfaceColor) as Int
                    handleBg.setColor(currentColor)

                    val strokeW = (dpToPx(1) * fraction).toInt()
                    handleBg.setStroke(strokeW, borderColor)
                }

                handleView?.alpha = opacity + (1f - opacity) * fraction

                // Animate actual window position and size per-frame in lockstep
                params.y = (targetY + (startY - targetY) * fraction).toInt()
                params.width = (endWidth + (startWidth - endWidth) * fraction).toInt()
                params.height = (endHeight + (startHeight - endHeight) * fraction).toInt()
                try {
                    windowManager.updateViewLayout(root, params)
                } catch (_: Exception) {}
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (containerView != null) {
                        (handleView as? ViewGroup)?.removeView(panel)
                        panelView = null
                        handleView?.visibility = View.VISIBLE
                        params.y = targetY
                        params.width = endWidth
                        params.height = endHeight
                        try {
                            windowManager.updateViewLayout(root, params)
                        } catch (_: Exception) {}
                        updateViewLayoutAndStyle()
                    }
                    panelAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    panelAnimator = null
                }
            })
        }
        panelAnimator = animator
        animator.start()
    }

    private fun cancelPanelAnimation() {
        panelAnimator?.cancel()
        panelAnimator = null
    }

    private fun toggleExpand() {
        if (isExpanded) {
            collapsePanel()
        } else {
            expandPanel()
        }
    }

    private fun updateViewLayoutAndStyle() {
        val root = containerView ?: return
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val thickness = getEdgePanelThickness()
        val height = getEdgePanelHeight()
        val opacity = getEdgePanelOpacity()

        // Reapply background shape style to handle
        handleView?.let { h ->
            val bgShape = GradientDrawable().apply {
                setColor(getAccentColor())
                val radiusPx = dpToPx(8).toFloat()
                cornerRadii = if (side == "Right") {
                    floatArrayOf(radiusPx, radiusPx, 0f, 0f, 0f, 0f, radiusPx, radiusPx)
                } else {
                    floatArrayOf(0f, 0f, radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f)
                }
            }
            h.background = bgShape
            h.alpha = opacity

            h.layoutParams = FrameLayout.LayoutParams(
                if (isExpanded) dpToPx(260) else dpToPx(thickness),
                if (isExpanded) dpToPx(400) else dpToPx(height)
            ).apply {
                gravity = (if (side == "Right") Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
                val m = if (isExpanded) dpToPx(10) else 0
                if (side == "Right") {
                    marginEnd = m
                    marginStart = 0
                } else {
                    marginStart = m
                    marginEnd = 0
                }
            }
        }

        val params = root.layoutParams as WindowManager.LayoutParams
        params.gravity = Gravity.CENTER_VERTICAL or (if (side == "Right") Gravity.END else Gravity.START)
        params.y = calculateYPosition(yPercent, if (isExpanded) 400 else height)

        if (!isExpanded) {
            params.width = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
            params.height = dpToPx(height)
        } else {
            params.width = dpToPx(260 + 10)
            params.height = dpToPx(400)
        }

        windowManager.updateViewLayout(root, params)
        updateSystemGestureExclusions()
    }

    private fun launchOcrCapture() {
        val intent = Intent(this, OcrCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun removeOverlayViews() {
        containerView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
            containerView = null
            handleView = null
            panelView = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Re-deliver a start command so the service survives
        // when the user swipes the app away from recents.
        val restartIntent = Intent(applicationContext, BrainOcrOverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("BrainOcrOverlay", "Failed to restart after task removal: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        serviceScope.cancel()
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
        val maxCenterY = screenHeight.toFloat() - halfHeight - dpToPx(24)
        val clampedCenterY = targetCenterY.coerceIn(minCenterY, maxCenterY)
        return (clampedCenterY - (screenHeight / 2f)).toInt()
    }

    private fun createDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
                setMargins(0, dpToPx(12), 0, dpToPx(12))
            }
            backgroundColor = Color.parseColor("#262629")
        }
    }

    // Helper extensions for programmatically styling views
    private var View.backgroundColor: Int
        get() = 0
        set(value) {
            setBackgroundColor(value)
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
            .setContentTitle("Second Brain Assistant Active")
            .setContentText("Tap the side handle on your screen or swipe from the edge to open options.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 9283
        private const val CHANNEL_ID = "floating_ocr_service_channel"
    }
}

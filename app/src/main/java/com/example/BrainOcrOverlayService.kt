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
import android.animation.ValueAnimator
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.app.NotificationCompat
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
    private var isExpanded = false
    private val EXTRA_TOUCH_WIDTH_DP = 16
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: SecondBrainRepository
    private lateinit var settingsRepo: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    private fun getEdgePanelSide(): String = prefs.getString("edge_panel_side", "Right") ?: "Right"
    private fun getEdgePanelYPercent(): Float = prefs.getFloat("edge_panel_y_percent", 0.4f)
    private fun getEdgePanelThickness(): Int = prefs.getInt("edge_panel_thickness", 12)
    private fun getEdgePanelHeight(): Int = prefs.getInt("edge_panel_height", 100)
    private fun getEdgePanelOpacity(): Float = prefs.getFloat("edge_panel_opacity", 0.7f)
    
    private fun isDarkTheme(): Boolean {
        val theme = prefs.getString("theme_mode", "System Default") ?: "System Default"
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
            key == "edge_panel_y_percent" || key == "floating_ocr_enabled") {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val root = containerView ?: return
            if (isExpanded || handleView?.visibility != View.VISIBLE) {
                root.systemGestureExclusionRects = emptyList()
                return
            }
            val w = root.width
            val h = root.height
            if (w > 0 && h > 0) {
                // OPPO / ColorOS edge workaround: Declare the exclusion rect starting from x=0
                // at the screen edge covering the entire declared width and height of the container.
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

        // ColorOS edge quirk workaround: Make window 16dp wider than the handle thickness.
        // This ensures the window extends slightly inset from the edge so ColorOS's native gesture
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
            y = calculateYPosition(yPercent)
        }

        val rootContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        // 1. Collapsed state: Draggable thin vertical handle (like Samsung edge)
        val handle = FrameLayout(this).apply {
            val bgShape = GradientDrawable().apply {
                setColor(Color.parseColor("#FF7043")) // Second Brain primary accent orange
                val radiusPx = dpToPx(8).toFloat()
                cornerRadii = if (side == "Right") {
                    floatArrayOf(radiusPx, radiusPx, 0f, 0f, 0f, 0f, radiusPx, radiusPx)
                } else {
                    floatArrayOf(0f, 0f, radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f)
                }
            }
            background = bgShape
            alpha = opacity
            elevation = dpToPx(4).toFloat()
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
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = if (side == "Right") Gravity.END else Gravity.START
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

        isExpanded = true
        handleView?.visibility = View.GONE

        // Build elegant, compact vertical panel
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            val bg = GradientDrawable().apply {
                if (isDark) {
                    setColors(intArrayOf(Color.parseColor("#1C1B1F"), Color.parseColor("#121113")))
                } else {
                    setColors(intArrayOf(Color.parseColor("#FCFCFF"), Color.parseColor("#F4F4F9")))
                }
                orientation = GradientDrawable.Orientation.TL_BR
                cornerRadius = dpToPx(20).toFloat()
                // Glowing vibrant orange accent rays border
                setStroke(dpToPx(2), Color.parseColor("#FF7043"))
            }
            background = bg
            elevation = dpToPx(16).toFloat()
            alpha = 0f
            scaleX = 0.85f
            scaleY = 0.85f
        }

        // Header Section
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(this).apply {
            text = "SECOND BRAIN"
            textSize = 13f
            setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#1C1B1F"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        titleContainer.addView(titleTv)

        val subtitleTv = TextView(this).apply {
            text = "Edge Assistant"
            textSize = 9.5f
            setTextColor(Color.parseColor("#FF7043"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        titleContainer.addView(subtitleTv)

        header.addView(titleContainer)

        val closeIv = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = ColorStateList.valueOf(if (isDark) Color.parseColor("#9E9E9E") else Color.parseColor("#5A5A5E"))
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            isClickable = true
            setOnClickListener { collapsePanel() }
        }
        header.addView(closeIv)

        panel.addView(header)

        // Divider
        panel.addView(createDivider())

        // 1. Smart Screen OCR button (Gradient background based on theme)
        val ocrButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            val shape = GradientDrawable().apply {
                if (isDark) {
                    setColors(intArrayOf(Color.parseColor("#2C221F"), Color.parseColor("#221C1A")))
                } else {
                    setColors(intArrayOf(Color.parseColor("#FFEBE5"), Color.parseColor("#FFDFD5")))
                }
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#4DFF7043")) // 30% alpha of FF7043
            }
            background = shape
            isClickable = true
            setOnClickListener {
                collapsePanel()
                launchOcrCapture()
            }
        }

        val ocrIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#FF7043"))
        }
        ocrButton.addView(ocrIcon)

        val ocrText = TextView(this).apply {
            text = "Smart Screen OCR / Lens"
            textSize = 11.5f
            setTextColor(if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#1C1B1F"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(10), 0, 0, 0)
        }
        ocrButton.addView(ocrText)

        panel.addView(ocrButton)

        // 2. Quick Note Section
        val noteTitle = TextView(this).apply {
            text = "QUICK NOTE"
            textSize = 9f
            setTextColor(if (isDark) Color.parseColor("#9E9E9E") else Color.parseColor("#505054"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(14), 0, dpToPx(6))
        }
        panel.addView(noteTitle)

        val noteInput = EditText(this).apply {
            hint = "Capture a quick thought..."
            setHintTextColor(if (isDark) Color.parseColor("#5A5A5D") else Color.parseColor("#8E8E93"))
            setTextColor(if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#1C1B1F"))
            textSize = 12f
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            val shape = GradientDrawable().apply {
                setColor(if (isDark) Color.parseColor("#161618") else Color.parseColor("#FFFFFF"))
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), if (isDark) Color.parseColor("#303033") else Color.parseColor("#DCDCD1"))
            }
            background = shape
            maxLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        panel.addView(noteInput)

        val saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(6), 0, 0)
        }

        val saveButton = TextView(this).apply {
            text = "Save Note"
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
            val btnShape = GradientDrawable().apply {
                setColors(intArrayOf(Color.parseColor("#FF8A65"), Color.parseColor("#FF7043")))
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                cornerRadius = dpToPx(8).toFloat()
            }
            background = btnShape
            isClickable = true
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
                            Toast.makeText(applicationContext, "Saved to Second Brain!", Toast.LENGTH_SHORT).show()
                            collapsePanel()
                        }
                    }
                } else {
                    Toast.makeText(applicationContext, "Please enter some text", Toast.LENGTH_SHORT).show()
                }
            }
        }
        saveRow.addView(saveButton)
        panel.addView(saveRow)

        // 3. Recent Notes Section
        val recentTitle = TextView(this).apply {
            text = "RECENT ARCHIVES"
            textSize = 9f
            setTextColor(if (isDark) Color.parseColor("#9E9E9E") else Color.parseColor("#505054"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(14), 0, dpToPx(6))
        }
        panel.addView(recentTitle)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(200) // Limit height
            )
        }
        val recentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(recentContainer)
        panel.addView(scrollView)

        // Populate recent notes
        serviceScope.launch {
            repository.getAllItemsFlow().collect { list ->
                val recent = list.sortedByDescending { it.timestamp }.take(5)
                withContext(Dispatchers.Main) {
                    recentContainer.removeAllViews()
                    if (recent.isEmpty()) {
                        val emptyTv = TextView(applicationContext).apply {
                            text = "No recent archives"
                            textSize = 10.5f
                            setTextColor(if (isDark) Color.parseColor("#505054") else Color.parseColor("#8E8E93"))
                            gravity = Gravity.CENTER
                            setPadding(0, dpToPx(8), 0, dpToPx(8))
                        }
                        recentContainer.addView(emptyTv)
                    } else {
                        recent.forEach { item ->
                            val itemRow = LinearLayout(applicationContext).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                                val itemShape = GradientDrawable().apply {
                                    setColor(if (isDark) Color.parseColor("#1C1B1F") else Color.parseColor("#FFFFFF"))
                                    cornerRadius = dpToPx(8).toFloat()
                                    setStroke(dpToPx(1), if (isDark) Color.parseColor("#2E2E31") else Color.parseColor("#E2E2E6"))
                                }
                                background = itemShape
                                val lp = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 0, 0, dpToPx(6))
                                }
                                layoutParams = lp
                                isClickable = true
                                setOnClickListener {
                                    if (item.type == SavedItemType.LINK) {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.content)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(applicationContext, "Cannot open link", Toast.LENGTH_SHORT).show()
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

                            val iv = ImageView(applicationContext).apply {
                                val drawable = when (item.type) {
                                    SavedItemType.LINK -> android.R.drawable.ic_menu_view
                                    SavedItemType.IMAGE -> android.R.drawable.ic_menu_gallery
                                    else -> android.R.drawable.ic_menu_edit
                                }
                                setImageResource(drawable)
                                imageTintList = ColorStateList.valueOf(Color.parseColor("#FF7043"))
                            }
                            itemRow.addView(iv)

                            val col = LinearLayout(applicationContext).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(dpToPx(8), 0, 0, 0)
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }

                            val title = TextView(applicationContext).apply {
                                text = if (item.title.isNotBlank()) item.title else "Untitled Note"
                                textSize = 11f
                                setTextColor(if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#1C1B1F"))
                                typeface = Typeface.DEFAULT_BOLD
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                            }
                            col.addView(title)

                            val snippet = TextView(applicationContext).apply {
                                text = item.content
                                textSize = 9f
                                setTextColor(if (isDark) Color.parseColor("#9E9E9E") else Color.parseColor("#5A5A5E"))
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                            }
                            col.addView(snippet)

                            itemRow.addView(col)
                            recentContainer.addView(itemRow)
                        }
                    }
                }
            }
        }

        panelView = panel
        root.addView(panel)

        // Expand layout params
        val params = root.layoutParams as WindowManager.LayoutParams
        val startWidth = dpToPx(getEdgePanelThickness() + EXTRA_TOUCH_WIDTH_DP)
        val startHeight = dpToPx(getEdgePanelHeight())
        val endWidth = dpToPx(285)

        // Measure panel height dynamically
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(endWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val endHeight = panel.measuredHeight

        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                       WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or 
                       WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        params.y = calculateYPosition(yPercent)

        // ValueAnimator for beautiful smooth spring expansion animation
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = OvershootInterpolator(0.9f)
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                params.width = (startWidth + (endWidth - startWidth) * fraction).toInt()
                params.height = (startHeight + (endHeight - startHeight) * fraction).toInt()
                
                panel.alpha = fraction
                panel.scaleX = 0.85f + 0.15f * fraction
                panel.scaleY = 0.85f + 0.15f * fraction
                
                if (containerView != null) {
                    windowManager.updateViewLayout(root, params)
                }
            }
        }
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

        isExpanded = false

        val params = root.layoutParams as WindowManager.LayoutParams
        val startWidth = params.width
        val startHeight = params.height
        val endWidth = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
        val endHeight = dpToPx(height)

        // ValueAnimator for beautiful smooth shrink collapse animation
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                params.width = (endWidth + (startWidth - endWidth) * fraction).toInt()
                params.height = (endHeight + (startHeight - endHeight) * fraction).toInt()
                
                panel.alpha = fraction
                panel.scaleX = 0.85f + 0.15f * fraction
                panel.scaleY = 0.85f + 0.15f * fraction

                if (containerView != null) {
                    windowManager.updateViewLayout(root, params)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (containerView != null) {
                        root.removeView(panel)
                        panelView = null
                        handleView?.visibility = View.VISIBLE
                        
                        // Restore handle parameters inside layout
                        handleView?.layoutParams = FrameLayout.LayoutParams(
                            dpToPx(thickness),
                            FrameLayout.LayoutParams.MATCH_PARENT
                        ).apply {
                            gravity = if (side == "Right") Gravity.END else Gravity.START
                        }
                        
                        params.width = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
                        params.height = dpToPx(height)
                        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        params.y = calculateYPosition(yPercent)
                        windowManager.updateViewLayout(root, params)
                        updateSystemGestureExclusions()
                    }
                }
            })
        }
        animator.start()
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
                setColor(Color.parseColor("#FF7043"))
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
                dpToPx(thickness),
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = if (side == "Right") Gravity.END else Gravity.START
            }
        }

        val params = root.layoutParams as WindowManager.LayoutParams
        params.gravity = Gravity.CENTER_VERTICAL or (if (side == "Right") Gravity.END else Gravity.START)
        params.y = calculateYPosition(yPercent)
        
        if (!isExpanded) {
            params.width = dpToPx(thickness + EXTRA_TOUCH_WIDTH_DP)
            params.height = dpToPx(height)
        }
        
        windowManager.updateViewLayout(root, params)
        updateSystemGestureExclusions()
    }

    private fun launchOcrCapture() {
        val intent = Intent(this, OcrCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        serviceScope.cancel()
        removeOverlayViews()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun calculateYPosition(yPercent: Float): Int {
        val usableHeight = resources.displayMetrics.heightPixels - dpToPx(160)
        return ((usableHeight * yPercent) - (usableHeight / 2)).toInt()
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
            .setContentText("Tap the side handle on your screen to open options.")
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

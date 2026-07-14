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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

    private lateinit var repository: SecondBrainRepository
    private lateinit var settingsRepo: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    private fun getEdgePanelSide(): String = prefs.getString("edge_panel_side", "Right") ?: "Right"
    private fun getEdgePanelYPercent(): Float = prefs.getFloat("edge_panel_y_percent", 0.4f)
    private fun getEdgePanelThickness(): Int = prefs.getInt("edge_panel_thickness", 12)
    private fun getEdgePanelHeight(): Int = prefs.getInt("edge_panel_height", 100)
    private fun getEdgePanelOpacity(): Float = prefs.getFloat("edge_panel_opacity", 0.7f)
    
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    private fun createOverlayViews() {
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val thickness = getEdgePanelThickness()
        val height = getEdgePanelHeight()
        val opacity = getEdgePanelOpacity()

        val params = WindowManager.LayoutParams(
            dpToPx(thickness),
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
        
        // Touch outside observer
        rootContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (isExpanded) {
                    collapsePanel()
                }
                true
            } else {
                false
            }
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

        // Drag & click listener for handle
        var initialY = 0
        var initialTouchY = 0f
        var isDragging = false

        handle.setOnTouchListener { _, event ->
            val layoutParams = rootContainer.layoutParams as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = layoutParams.y
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaY) > 8) {
                        isDragging = true
                        layoutParams.y = initialY + deltaY
                        
                        // Clamp vertical position
                        val usableHeight = resources.displayMetrics.heightPixels - dpToPx(160)
                        val halfHeight = usableHeight / 2
                        if (layoutParams.y < -halfHeight) layoutParams.y = -halfHeight
                        if (layoutParams.y > halfHeight) layoutParams.y = halfHeight
                        
                        windowManager.updateViewLayout(rootContainer, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        val usableHeight = resources.displayMetrics.heightPixels - dpToPx(160)
                        val halfHeight = usableHeight / 2
                        val finalY = layoutParams.y
                        val newPercent = (finalY + halfHeight).toFloat() / usableHeight.toFloat()
                        val clampedPercent = newPercent.coerceIn(0.0f, 1.0f)
                        settingsRepo.setEdgePanelYPercent(clampedPercent)
                    } else {
                        toggleExpand()
                    }
                    true
                }
                else -> false
            }
        }

        rootContainer.addView(handle, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        containerView = rootContainer
        handleView = handle
        
        windowManager.addView(rootContainer, params)
    }

    private fun expandPanel() {
        val root = containerView ?: return
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()

        isExpanded = true
        handleView?.visibility = View.GONE

        // Build elegant, compact vertical panel
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#18181A")) // Pure high contrast charcoal
                cornerRadius = dpToPx(20).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#303033")) // M3 card outline
            }
            background = bg
            elevation = dpToPx(16).toFloat()
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
            setTextColor(Color.parseColor("#FFFFFF"))
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
            imageTintList = ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            isClickable = true
            setOnClickListener { collapsePanel() }
        }
        header.addView(closeIv)

        panel.addView(header)

        // Divider
        panel.addView(createDivider())

        // 1. Smart Screen OCR button
        val ocrButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#232326"))
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
            setTextColor(Color.parseColor("#FFFFFF"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(10), 0, 0, 0)
        }
        ocrButton.addView(ocrText)

        panel.addView(ocrButton)

        // 2. Quick Note Section
        val noteTitle = TextView(this).apply {
            text = "QUICK NOTE"
            textSize = 9f
            setTextColor(Color.parseColor("#757575"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(14), 0, dpToPx(6))
        }
        panel.addView(noteTitle)

        val noteInput = EditText(this).apply {
            hint = "Capture a quick thought..."
            setHintTextColor(Color.parseColor("#4C4C4F"))
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#1D1D20"))
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#2E2E31"))
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
                setColor(Color.parseColor("#FF7043"))
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
            setTextColor(Color.parseColor("#757575"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(14), 0, dpToPx(6))
        }
        panel.addView(recentTitle)

        val recentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        panel.addView(recentContainer)

        // Populate recent notes
        serviceScope.launch {
            repository.getAllItemsFlow().collect { list ->
                val recent = list.sortedByDescending { it.timestamp }.take(3)
                withContext(Dispatchers.Main) {
                    recentContainer.removeAllViews()
                    if (recent.isEmpty()) {
                        val emptyTv = TextView(applicationContext).apply {
                            text = "No recent archives"
                            textSize = 10.5f
                            setTextColor(Color.parseColor("#505054"))
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
                                    setColor(Color.parseColor("#1D1D20"))
                                    cornerRadius = dpToPx(8).toFloat()
                                    setStroke(dpToPx(1), Color.parseColor("#262629"))
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
                                    val intent = Intent(applicationContext, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        putExtra("OPEN_ITEM_ID", item.id)
                                    }
                                    startActivity(intent)
                                    collapsePanel()
                                }
                            }

                            val iv = ImageView(applicationContext).apply {
                                val drawable = when (item.type) {
                                    SavedItemType.LINK -> android.R.drawable.ic_menu_share
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
                                setTextColor(Color.parseColor("#E0E0E0"))
                                typeface = Typeface.DEFAULT_BOLD
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                            }
                            col.addView(title)

                            val snippet = TextView(applicationContext).apply {
                                text = item.content
                                textSize = 9f
                                setTextColor(Color.parseColor("#757575"))
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
        params.width = dpToPx(285)
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        // Clear flags so we can capture focus (needed for EditText input)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                       WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or 
                       WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        params.y = calculateYPosition(yPercent)
        windowManager.updateViewLayout(root, params)
    }

    private fun collapsePanel() {
        val root = containerView ?: return
        val side = getEdgePanelSide()
        val yPercent = getEdgePanelYPercent()
        val thickness = getEdgePanelThickness()
        val height = getEdgePanelHeight()

        isExpanded = false
        
        panelView?.let {
            root.removeView(it)
            panelView = null
        }

        handleView?.visibility = View.VISIBLE

        val params = root.layoutParams as WindowManager.LayoutParams
        params.width = dpToPx(thickness)
        params.height = dpToPx(height)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        params.y = calculateYPosition(yPercent)
        windowManager.updateViewLayout(root, params)
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
        }

        val params = root.layoutParams as WindowManager.LayoutParams
        params.gravity = Gravity.CENTER_VERTICAL or (if (side == "Right") Gravity.END else Gravity.START)
        params.y = calculateYPosition(yPercent)
        
        if (!isExpanded) {
            params.width = dpToPx(thickness)
            params.height = dpToPx(height)
        }
        
        windowManager.updateViewLayout(root, params)
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

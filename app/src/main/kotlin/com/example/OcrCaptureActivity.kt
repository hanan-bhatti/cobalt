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
import com.example.R
import androidx.compose.ui.res.painterResource

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SavedItemType
import com.example.ui.screens.ImageMarkingCanvas
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.GoogleApiErrorMapper
import com.example.util.OcrLoadingMessages
import com.example.utils.DevicePerformance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

@android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
class OcrCaptureActivity : ComponentActivity(), ScreenCaptureService.CaptureCallback {

    private val viewModel: CobaltViewModel by viewModels()
    private lateinit var mediaProjectionManager: MediaProjectionManager

    // Activity state
    private var capturedBitmapState = mutableStateOf<Bitmap?>(null)
    private var errorMessageState = mutableStateOf<String?>(null)
    private var isCapturingState = mutableStateOf(false)

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        if (isCapturingState.value) {
            isCapturingState.value = false
            Toast.makeText(this, "Screen capture timed out. Please try again.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            com.example.utils.MediaProjectionCache.resultCode = result.resultCode
            com.example.utils.MediaProjectionCache.resultData = result.data
            isCapturingState.value = true

            // Wait for the system consent dialog to fully dismiss before capturing,
            // otherwise the screenshot includes the dialog/transition itself.
            handler.postDelayed({
                startCaptureService(result.resultCode, result.data!!)
            }, 400)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Set ScreenCaptureService callback
        ScreenCaptureService.callback = this

        // Safety timeout: 5 seconds
        handler.postDelayed(timeoutRunnable, 5000)

        // Always trigger a fresh projection system dialog on launch on Android 14+ to prevent SecurityException (no token reuse)
        com.example.utils.MediaProjectionCache.clear()
        isCapturingState.value = false
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
            captureIntent.putExtra("android.media.projection.extra.EXTRA_MEDIA_PROJECTION_CONFIG", config)
        }
        projectionLauncher.launch(captureIntent)

        setContent {
            val themeMode by viewModel.settingsRepository.themeMode.collectAsState()
            val dynamicColorEnabled by viewModel.settingsRepository.dynamicColor.collectAsState()
            val isDarkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColorEnabled) {
                OcrOverlayUI()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Restart safety timeout
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, 5000)

        // Trigger a fresh projection screen capture prompt
        com.example.utils.MediaProjectionCache.clear()
        isCapturingState.value = false
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
            captureIntent.putExtra("android.media.projection.extra.EXTRA_MEDIA_PROJECTION_CONFIG", config)
        }
        projectionLauncher.launch(captureIntent)
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("result_code", resultCode)
            putExtra("result_data", data)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("OcrCaptureActivity", "Failed to start capture service: ${e.message}", e)
        }
    }

    override fun onBitmapCaptured(bitmap: Bitmap) {
        runOnUiThread {
            handler.removeCallbacks(timeoutRunnable)
            isCapturingState.value = false
            capturedBitmapState.value = bitmap
            viewModel.startFloatingOcrCapture(bitmap)
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            handler.removeCallbacks(timeoutRunnable)
            isCapturingState.value = false
            errorMessageState.value = error
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        ScreenCaptureService.callback = null
        super.onDestroy()
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun OcrOverlayUI() {
        val capturedBitmap by capturedBitmapState
        val isCapturing by isCapturingState
        val errorMessage by errorMessageState

        val activeItem by viewModel.activeCaptureItem.collectAsState()
        val isOcrLoading by viewModel.isOcrLoading.collectAsState()
        val extractedLinks by viewModel.extractedLinksToReview.collectAsState()
        val ocrError by viewModel.ocrError.collectAsState()

        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        var shownToast by remember { mutableStateOf(false) }

        val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
        val blurRadius by viewModel.blurRadius.collectAsState()
        val blurOpacity by viewModel.blurOpacity.collectAsState()
        val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur
        val hazeState = remember { HazeState() }

        LaunchedEffect(capturedBitmap) {
            if (capturedBitmap != null && !shownToast) {
                Toast.makeText(
                    context,
                    "Smart Screen Selection: Draw a box over any text or links to extract.",
                    Toast.LENGTH_LONG
                ).show()
                shownToast = true
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            if (capturedBitmap != null) {
                // Show visual screenshot selection interface
                Box(modifier = Modifier.fillMaxSize()) {
                    // Selection Canvas wrapped in hazeSource for background blur
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(state = hazeState)
                    ) {
                        ImageMarkingCanvas(
                            bitmap = capturedBitmap!!,
                            onRegionSelected = { x, y, w, h ->
                                viewModel.performRegionOcr(x, y, w, h)
                            },
                            enabled = !isOcrLoading,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Floating Exit / Close Button (Always visible at top-right)
                    Surface(
                        shape = CircleShape,
                        color = if (useBlur) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 16.dp, end = 16.dp)
                            .size(38.dp)
                            .then(
                                if (useBlur) {
                                    Modifier
                                        .clip(CircleShape)
                                        .hazeEffect(
                                            state = hazeState,
                                            style = HazeStyle(
                                                backgroundColor = MaterialTheme.colorScheme.surface,
                                                tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = blurOpacity)),
                                                blurRadius = blurRadius.dp,
                                                noiseFactor = 0.02f
                                            )
                                        )
                                } else Modifier
                            )
                            .clickable {
                                viewModel.clearOcrError()
                                viewModel.cancelCapture()
                                finish()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_close),
                                contentDescription = "Exit OCR Capture",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // OCR Progress Overlay (Redesigned with frosted glass & funny rotating messages)
                    if (isOcrLoading) {
                        OcrProgressOverlay(
                            hazeState = hazeState,
                            useBlur = useBlur,
                            blurRadius = blurRadius,
                            blurOpacity = blurOpacity,
                            onCancel = {
                                viewModel.clearOcrError()
                                viewModel.cancelCapture()
                                finish()
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Extracted Results / Error bottom review panel
                    var userSelectedTab by remember { mutableStateOf<String?>(null) }
                    val activeTab = userSelectedTab ?: if (extractedLinks.isNotEmpty()) "Links" else "Note"
                    var noteTitle by remember { mutableStateOf("Extracted Note") }
                    var isPreviewMode by remember { mutableStateOf(true) }
                    val customFolders by viewModel.customFolders.collectAsState()
                    var selectedFolders by remember { mutableStateOf(setOf("AI Extracted")) }
                    var showNewFolderDialog by remember { mutableStateOf(false) }
                    var newFolderName by remember { mutableStateOf("") }

                    LaunchedEffect(activeItem?.extractedText) {
                        val text = activeItem?.extractedText
                        if (!text.isNullOrBlank()) {
                            val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: "Extracted Note"
                            noteTitle = if (firstLine.length > 30) firstLine.take(27) + "..." else firstLine
                        }
                    }

                    AnimatedVisibility(
                        visible = !isOcrLoading && (activeItem?.extractedText != null || extractedLinks.isNotEmpty() || ocrError != null),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    ) {
                        val sheetModifier = if (useBlur) {
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .hazeEffect(
                                    state = hazeState,
                                    style = HazeStyle(
                                        backgroundColor = MaterialTheme.colorScheme.surface,
                                        tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = blurOpacity)),
                                        blurRadius = blurRadius.dp,
                                        noiseFactor = 0.02f
                                    )
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), RoundedCornerShape(28.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                        }

                        Surface(
                            modifier = sheetModifier,
                            shape = RoundedCornerShape(28.dp),
                            color = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (ocrError != null) "Extraction Notice" else "OCR Intelligence Hub",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (ocrError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = {
                                            viewModel.clearOcrError()
                                            viewModel.cancelCapture()
                                            finish()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(painter = painterResource(id = R.drawable.ic_custom_close), contentDescription = "Close", modifier = Modifier.size(18.dp))
                                    }
                                }

                                if (ocrError != null) {
                                    // Dedicated friendly error presentation
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        val cleanErrorMessage = GoogleApiErrorMapper.formatUserFriendlyError(ocrError)

                                        Text(
                                            text = cleanErrorMessage,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.5.sp,
                                                lineHeight = 17.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            ),
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.clearOcrError()
                                                    viewModel.cancelCapture()
                                                    finish()
                                                },
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.weight(1f).height(44.dp)
                                            ) {
                                                Text("Close", fontWeight = FontWeight.SemiBold)
                                            }

                                            Button(
                                                onClick = {
                                                    if (capturedBitmap != null) {
                                                        viewModel.startFloatingOcrCapture(capturedBitmap!!)
                                                    } else {
                                                        viewModel.cancelCapture()
                                                        finish()
                                                    }
                                                },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f).height(44.dp)
                                            ) {
                                                Text("Select Again", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    // Gorgeous Pill-shaped Tab Selector (Switch between Links and Note)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(50))
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val tabs = listOf("Links", "Note")
                                        tabs.forEach { tab ->
                                            val isSelected = activeTab == tab
                                            val hasContent = if (tab == "Links") extractedLinks.isNotEmpty() else activeItem?.extractedText != null

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else Color.Transparent
                                                    )
                                                    .clickable(enabled = hasContent) { userSelectedTab = tab }
                                                    .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = if (tab == "Links") R.drawable.ic_custom_link else R.drawable.ic_custom_text),
                                                        contentDescription = null,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                               else if (hasContent) MaterialTheme.colorScheme.onSurface
                                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = if (tab == "Links") "$tab (${extractedLinks.size})" else tab,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                               else if (hasContent) MaterialTheme.colorScheme.onSurface
                                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Content rendering based on selected Tab
                                    if (activeTab == "Links" && extractedLinks.isNotEmpty()) {
                                        // Editable link listing
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            extractedLinks.forEach { reviewItem ->
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                    ),
                                                    shape = RoundedCornerShape(16.dp),
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Checkbox(
                                                            checked = reviewItem.isSelected,
                                                            onCheckedChange = { checked ->
                                                                viewModel.toggleExtractedLinkSelection(reviewItem.id, checked)
                                                            }
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            OutlinedTextField(
                                                                value = reviewItem.url,
                                                                onValueChange = { viewModel.updateExtractedLink(reviewItem.id, it) },
                                                                label = { Text("Hyperlink URL", fontSize = 10.sp) },
                                                                singleLine = true,
                                                                textStyle = MaterialTheme.typography.bodySmall,
                                                                shape = RoundedCornerShape(12.dp),
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                            if (reviewItem.description.isNotBlank()) {
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = reviewItem.description,
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (activeTab == "Note" && activeItem?.extractedText != null) {
                                        // Beautifully styled note view with optional raw markdown editing and rich text formatting toggle
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            // Title field
                                            OutlinedTextField(
                                                value = noteTitle,
                                                onValueChange = { noteTitle = it },
                                                label = { Text("Note Title") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            // Edit vs. View toggle
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { isPreviewMode = !isPreviewMode }
                                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = if (isPreviewMode) R.drawable.ic_custom_edit else R.drawable.ic_custom_text),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = if (isPreviewMode) "Edit Raw Text" else "Styled Preview",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            if (isPreviewMode) {
                                                // Styled formatted preview card with rounded edges, beautiful spacing and colors
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                    ),
                                                    shape = RoundedCornerShape(16.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Text(
                                                            text = com.example.ui.screens.parseMarkdown(
                                                                activeItem?.extractedText ?: "",
                                                                MaterialTheme.colorScheme.primary
                                                            ),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            } else {
                                                // Raw markdown editing field
                                                OutlinedTextField(
                                                    value = activeItem?.extractedText ?: "",
                                                    onValueChange = { newValue ->
                                                        viewModel.updateActiveCaptureItem { it.copy(extractedText = newValue) }
                                                    },
                                                    minLines = 3,
                                                    maxLines = 8,
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    // REDESIGNED FOLDER ASSIGNMENT ZONE (Immediately above Save action buttons)
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(painter = painterResource(id = R.drawable.ic_custom_folder), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Text(
                                                    text = "Select Folders / Tags",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { showNewFolderDialog = true }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_custom_plus),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "New Folder",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        if (showNewFolderDialog) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .padding(6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                BasicTextField(
                                                    value = newFolderName,
                                                    onValueChange = { newFolderName = it },
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    decorationBox = { innerTextField ->
                                                        if (newFolderName.isEmpty()) {
                                                            Text(
                                                                text = "Folder name...",
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                )
                                                Button(
                                                    onClick = {
                                                        if (newFolderName.isNotBlank()) {
                                                            viewModel.createFolder(newFolderName.trim())
                                                            selectedFolders = selectedFolders + newFolderName.trim()
                                                            newFolderName = ""
                                                            showNewFolderDialog = false
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Text("Add", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                                }
                                                TextButton(
                                                    onClick = { showNewFolderDialog = false },
                                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Text("Cancel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        }

                                        // Horizontal scroll list of visual folder choice pills
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val allFolders = listOf("AI Extracted") + customFolders.filter { it != "All" && it != "Archive" }
                                            allFolders.distinct().forEach { folder ->
                                                val isFolderSelected = selectedFolders.contains(folder)
                                                Surface(
                                                    onClick = {
                                                        selectedFolders = if (isFolderSelected) {
                                                            selectedFolders - folder
                                                        } else {
                                                            selectedFolders + folder
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(50),
                                                    color = if (isFolderSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    border = BorderStroke(
                                                        width = 1.dp,
                                                        color = if (isFolderSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                                    ),
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(id = if (isFolderSelected) R.drawable.ic_custom_check else R.drawable.ic_custom_folder),
                                                            contentDescription = null,
                                                            tint = if (isFolderSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = folder,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = if (isFolderSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isFolderSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Action buttons depending on active tab selection
                                    if (activeTab == "Links") {
                                        val selectedCount = extractedLinks.count { it.isSelected && it.url.isNotBlank() }
                                        Button(
                                            onClick = {
                                                viewModel.confirmAndSaveExtractedLinks(selectedFolders.toList())
                                                Toast.makeText(context.applicationContext, "Saved selected links to Cobalt!", Toast.LENGTH_SHORT).show()
                                                finish()
                                            },
                                            enabled = selectedCount > 0,
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        ) {
                                            Icon(painter = painterResource(id = R.drawable.ic_custom_link), contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (selectedCount > 0) "Save Selected Links ($selectedCount) to Cobalt" else "No Links Selected",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                viewModel.updateActiveCaptureItem { item ->
                                                    item.copy(
                                                        title = noteTitle.ifBlank { "Extracted Note" },
                                                        content = item.extractedText ?: "",
                                                        type = SavedItemType.TEXT,
                                                        folders = selectedFolders.toList()
                                                    )
                                                }
                                                viewModel.saveActiveItem()
                                                Toast.makeText(context.applicationContext, "Saved formatted note to Cobalt!", Toast.LENGTH_SHORT).show()
                                                finish()
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        ) {
                                            Icon(painter = painterResource(id = R.drawable.ic_custom_text), contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Save Formatted Note to Cobalt",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OcrProgressOverlay(
    hazeState: HazeState,
    useBlur: Boolean,
    blurRadius: Int,
    blurOpacity: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentMessage by remember { mutableStateOf(OcrLoadingMessages.getRandomMessage()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(8000L) // Rotates every 8 seconds (in the 7-10s window)
            currentMessage = OcrLoadingMessages.getRandomMessage(exclude = currentMessage)
        }
    }

    val cardModifier = if (useBlur) {
        modifier
            .padding(horizontal = 28.dp)
            .widthIn(max = 310.dp)
            .clip(RoundedCornerShape(32.dp))
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = blurOpacity)),
                    blurRadius = blurRadius.dp,
                    noiseFactor = 0.02f
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            )
    } else {
        modifier
            .padding(horizontal = 28.dp)
            .widthIn(max = 310.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(32.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(32.dp)
            )
    }

    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Modern Expressive Progress Indicator
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(62.dp)
                ) {}
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
            }

            // Headline (Clean, no mention of Gemini!)
            Text(
                text = "Reading Selection...",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            // Animated witty / sarcastic polite messages
            AnimatedContent(
                targetState = currentMessage,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(500)) + slideInVertically { it / 3 })
                        .togetherWith(fadeOut(animationSpec = tween(350)) + slideOutVertically { -it / 3 })
                },
                label = "ocr_loading_message"
            ) { msg ->
                Text(
                    text = msg,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                )
            }

            // Subtle cancel button so the user can abort anytime
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

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
import com.example.ui.viewmodel.SecondBrainViewModel
import kotlinx.coroutines.delay
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

    private val viewModel: SecondBrainViewModel by viewModels()
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
            val isDarkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
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
        val snackbarHostState = remember { SnackbarHostState() }
        val context = LocalContext.current
        var shownToast by remember { mutableStateOf(false) }

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

        LaunchedEffect(ocrError) {
            ocrError?.let { error ->
                snackbarHostState.showSnackbar(error)
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
                    // Selection Canvas
                    ImageMarkingCanvas(
                        bitmap = capturedBitmap!!,
                        onRegionSelected = { x, y, w, h ->
                            viewModel.performRegionOcr(x, y, w, h)
                        },
                        enabled = !isOcrLoading,
                        modifier = Modifier.fillMaxSize()
                    )

                    // OCR Progress Overlay
                    if (isOcrLoading) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp)
                                .widthIn(max = 280.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            tonalElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = "Reading Region\nwith Gemini...",
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Analyzing visual layout\nto locate text and hyperlinks.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Extracted Results bottom review panel
                    var activeTab by remember { mutableStateOf("Note") }
                    var noteTitle by remember { mutableStateOf("Extracted Note") }
                    var isPreviewMode by remember { mutableStateOf(false) }
                    val customFolders by viewModel.customFolders.collectAsState()
                    var selectedFolders by remember { mutableStateOf(setOf("AI Extracted")) }
                    var showNewFolderDialog by remember { mutableStateOf(false) }
                    var newFolderName by remember { mutableStateOf("") }

                    LaunchedEffect(extractedLinks) {
                        if (extractedLinks.isNotEmpty()) {
                            activeTab = "Links"
                        } else {
                            activeTab = "Note"
                        }
                    }

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
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            tonalElevation = 8.dp
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
                                        text = "OCR Intelligence Hub",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = {
                                            // Reset the capture state so they can draw a box again on the current capturedBitmap!
                                            if (capturedBitmap != null) {
                                                viewModel.startFloatingOcrCapture(capturedBitmap!!)
                                            } else {
                                                viewModel.cancelCapture()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(painter = painterResource(id = R.drawable.ic_custom_close), contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }

                                if (ocrError == null) {
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
                                                    .clickable(enabled = hasContent) { activeTab = tab }
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
                                                        painter = painterResource(id = if (isPreviewMode) R.drawable.ic_custom_text else R.drawable.ic_custom_edit),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = if (isPreviewMode) "Styled Preview" else "Edit Markdown",
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
                                                            text = parseMarkdownToAnnotatedString(
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

                                    // Action buttons depending on tab selection
                                    if (activeTab == "Links") {
                                        Button(
                                            onClick = {
                                                viewModel.confirmAndSaveExtractedLinks(selectedFolders.toList())
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Saved links to Second Brain!")
                                                    delay(1200)
                                                    finish()
                                                }
                                            },
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
                                                text = "Save Selected Links to Brain",
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
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Saved formatted note to Second Brain!")
                                                    delay(1200)
                                                    finish()
                                                }
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
                                            Text("Save Formatted Note to Brain", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            com.example.ui.components.AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )
        }
    }
}

fun parseMarkdownToAnnotatedString(text: String, primaryColor: Color): androidx.compose.ui.text.AnnotatedString {
    val lines = text.split("\n")
    return androidx.compose.ui.text.buildAnnotatedString {
        lines.forEachIndexed { i, line ->
            var processedLine = line
            if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
                val firstCharIndex = line.indexOf(line.trimStart().first())
                val indent = if (firstCharIndex >= 0) line.substring(0, firstCharIndex) else ""
                processedLine = indent + "•  " + line.trimStart().substring(2)
            }

            var index = 0
            while (index < processedLine.length) {
                val boldStart = processedLine.indexOf("**", index)
                if (boldStart != -1) {
                    append(processedLine.substring(index, boldStart))
                    val boldEnd = processedLine.indexOf("**", boldStart + 2)
                    if (boldEnd != -1) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                        append(processedLine.substring(boldStart + 2, boldEnd))
                        pop()
                        index = boldEnd + 2
                    } else {
                        append("**")
                        index = boldStart + 2
                    }
                } else {
                    val remaining = processedLine.substring(index)
                    val urlMatch = Regex("https?://[\\w\\d\\-_\\?\\.\\/\\=\\+&%#]+").find(remaining)
                    if (urlMatch != null) {
                        append(remaining.substring(0, urlMatch.range.first))
                        pushStyle(androidx.compose.ui.text.SpanStyle(
                            color = primaryColor,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        ))
                        append(urlMatch.value)
                        pop()
                        index += urlMatch.range.last + 1
                    } else {
                        append(remaining)
                        break
                    }
                }
            }
            if (i < lines.size - 1) {
                append("\n")
            }
        }
    }
}

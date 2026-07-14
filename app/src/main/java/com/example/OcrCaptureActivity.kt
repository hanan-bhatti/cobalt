package com.example

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
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

@android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
class OcrCaptureActivity : ComponentActivity(), ScreenCaptureService.CaptureCallback {

    private val viewModel: SecondBrainViewModel by viewModels()
    private lateinit var mediaProjectionManager: MediaProjectionManager

    // Activity state
    private var capturedBitmapState = mutableStateOf<Bitmap?>(null)
    private var errorMessageState = mutableStateOf<String?>(null)
    private var isCapturingState = mutableStateOf(true)

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
            startCaptureService(result.resultCode, result.data!!)
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

        if (com.example.utils.MediaProjectionCache.resultData != null) {
            isCapturingState.value = true
            startCaptureService(
                com.example.utils.MediaProjectionCache.resultCode,
                com.example.utils.MediaProjectionCache.resultData!!
            )
        } else {
            // Trigger projection system dialog
            isCapturingState.value = true
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
                captureIntent.putExtra("android.media.projection.extra.EXTRA_MEDIA_PROJECTION_CONFIG", config)
            }
            projectionLauncher.launch(captureIntent)
        }

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

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("result_code", resultCode)
            putExtra("result_data", data)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
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
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            if (isCapturing) {
                // Elegant, ultra-subtle centered progress indicator so the background remains clean and transparent
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (capturedBitmap != null) {
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

                    // Subtle, unobtrusive close button in top right
                    IconButton(
                        onClick = { finish() },
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
                            .align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close overlay", tint = Color.White)
                    }

                    // OCR Progress Overlay
                    if (isOcrLoading) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                                Text(
                                    text = "Reading Region with Gemini...",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Analyzing visual layout to locate text and hyperlinks.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Extracted Results bottom review panel
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
                                        text = if (extractedLinks.isNotEmpty()) "Extracted Links" else "Extracted Text",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { viewModel.cancelCapture() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }

                                if (ocrError == null) {
                                    if (extractedLinks.isNotEmpty()) {
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
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = {
                                                    viewModel.confirmAndSaveExtractedLinks()
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Saved to Second Brain!")
                                                        delay(1200)
                                                        finish()
                                                    }
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF2E7D32), // Direct Success Green M3
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(48.dp)
                                            ) {
                                                Icon(Icons.Outlined.Link, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Quick Save Links to Brain", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else if (activeItem?.extractedText != null) {
                                        // Raw note view
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
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

                                            Button(
                                                onClick = {
                                                    viewModel.updateActiveCaptureItem { item ->
                                                        item.copy(
                                                            title = "Floating OCR Note",
                                                            content = item.extractedText ?: "",
                                                            type = SavedItemType.TEXT
                                                        )
                                                    }
                                                    viewModel.saveActiveItem()
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Saved to Second Brain!")
                                                        delay(1200)
                                                        finish()
                                                    }
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.fillMaxWidth().height(48.dp)
                                            ) {
                                                Icon(Icons.Outlined.Description, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Save as Text Note", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )
        }
    }
}

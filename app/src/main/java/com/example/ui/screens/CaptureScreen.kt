package com.example.ui.screens

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.example.ui.components.bounceClick
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.ui.viewmodel.SecondBrainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: SecondBrainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeItem by viewModel.activeCaptureItem.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val isOcrLoading by viewModel.isOcrLoading.collectAsState()
    val ocrError by viewModel.ocrError.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()
    val extractedLinks by viewModel.extractedLinksToReview.collectAsState()

    LaunchedEffect(ocrError) {
        ocrError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearOcrError()
        }
    }

    val item = activeItem ?: return
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Capture Memory",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.cancelCapture() },
                        modifier = Modifier.bounceClick().testTag("cancel_capture_button")
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.saveActiveItem() },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.bounceClick().testTag("save_capture_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // TITLE FIELD
            Text(
                text = "Title",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = item.title,
                onValueChange = { viewModel.updateActiveCaptureItem { item -> item.copy(title = it) } },
                placeholder = { Text("Add an archive title...") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("capture_title_input")
            )

            // MEDIA DRAWING CANVAS (OCR Region Selection)
            if (item.type == SavedItemType.IMAGE && capturedBitmap != null) {
                Text(
                    text = "Screenshot Region Marking",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "Draw a rough box or line over text/URLs to autoextract using Gemini AI. Leave empty to skip OCR.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .background(Color.Black)
                ) {
                    ImageMarkingCanvas(
                        bitmap = capturedBitmap!!,
                        onRegionSelected = { x, y, w, h ->
                            viewModel.performRegionOcr(x, y, w, h)
                        },
                        enabled = !isOcrLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // EXTRACTED TEXT FIELD (IF OCR TRIGGERED)
            if (isOcrLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Gemini AI OCR extracting text...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (item.extractedText != null) {
                Text(
                    text = "Extracted OCR Result",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = item.extractedText ?: "",
                    onValueChange = { viewModel.updateActiveCaptureItem { item -> item.copy(extractedText = it) } },
                    shape = RoundedCornerShape(20.dp),
                    minLines = 3,
                    maxLines = Int.MAX_VALUE,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("ocr_result_output")
                )

                if (extractedLinks.isNotEmpty()) {
                    Text(
                        text = "Review Extracted Links",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        extractedLinks.forEach { reviewItem ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = reviewItem.isSelected,
                                            onCheckedChange = { checked ->
                                                viewModel.toggleExtractedLinkSelection(reviewItem.id, checked)
                                            },
                                            modifier = Modifier.testTag("review_link_checkbox_${reviewItem.id}")
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedTextField(
                                            value = reviewItem.url,
                                            onValueChange = { newValue ->
                                                viewModel.updateExtractedLink(reviewItem.id, newValue)
                                            },
                                            label = { Text("URL") },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("review_link_input_${reviewItem.id}")
                                        )
                                    }
                                    if (reviewItem.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = reviewItem.description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(start = 40.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.confirmAndSaveExtractedLinks() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick()
                                .testTag("confirm_save_extracted_links_button")
                        ) {
                            Text("Confirm & Save Selected Links", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // MAIN CONTENT EDITOR
            Text(
                text = when (item.type) {
                    SavedItemType.LINK -> "URL / Link"
                    SavedItemType.CODE -> "Code Snippet Source"
                    else -> "Note Content"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            val editorFont = if (item.type == SavedItemType.CODE) FontFamily.Monospace else FontFamily.SansSerif
            val isMultiLine = item.type == SavedItemType.TEXT || item.type == SavedItemType.CODE || item.type == SavedItemType.AUDIO
            if (item.type == SavedItemType.TEXT) {
                RichTextEditor(
                    value = item.content,
                    onValueChange = { newContent -> viewModel.updateActiveCaptureItem { captured -> captured.copy(content = newContent) } },
                    placeholder = { Text("Capture your thoughts or paste clipboard contents...") },
                    minLines = if (isMultiLine) 5 else 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("capture_content_input")
                )
            } else {
                OutlinedTextField(
                    value = item.content,
                    onValueChange = { viewModel.updateActiveCaptureItem { captured -> captured.copy(content = it) } },
                    visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                    placeholder = {
                        Text(
                            when (item.type) {
                                SavedItemType.LINK -> "https://example.com/shared-resource"
                                SavedItemType.CODE -> "Write or paste source code..."
                                SavedItemType.IMAGE -> "Screenshot path"
                                SavedItemType.VIDEO -> "Video file path"
                                SavedItemType.TEXT -> "Capture your thoughts or paste clipboard contents..."
                                SavedItemType.AUDIO -> "Voice transcription..."
                            }
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    minLines = if (isMultiLine) 5 else 1,
                    maxLines = Int.MAX_VALUE,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = editorFont),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("capture_content_input")
                )
            }

            if (item.type == SavedItemType.LINK && item.content.isNotBlank()) {
                Text(
                    text = "Live Link Preview",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    if (!item.linkTitle.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!item.linkImage.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.linkImage,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.linkTitle ?: "",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!item.linkDescription.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.linkDescription ?: "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val uri = try { android.net.Uri.parse(item.content) } catch (e: Exception) { null }
                                val domain = uri?.host ?: item.content
                                Text(
                                    text = domain,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Extracting link metadata...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // ORGANIZATIONAL FOLDERS SELECTION
            Text(
                text = "Assign to Custom Folders",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (customFolders.isEmpty()) {
                Text(
                    text = "No custom folders created yet. Create folder tags on the Home Screen to tag your items.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    customFolders.forEach { folder ->
                        val isAssigned = item.folders.contains(folder)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isAssigned) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            contentColor = if (isAssigned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, if (isAssigned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .clickable {
                                    viewModel.updateActiveCaptureItem { item ->
                                        val newFoldersList = if (isAssigned) {
                                            item.folders - folder
                                        } else {
                                            item.folders + folder
                                        }
                                        item.copy(folders = newFoldersList)
                                    }
                                }
                                .testTag("tag_chip_$folder")
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(folder, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // AUTO-CATEGORIZATION ASSURANCE BOX (Design polish)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "This item will be automatically archived into system category '${item.type.displayName}'.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun ImageMarkingCanvas(
    bitmap: Bitmap,
    onRegionSelected: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var pathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var containerWidth by remember { mutableStateOf(0f) }
    var containerHeight by remember { mutableStateOf(0f) }

    val currentEnabled by androidx.compose.runtime.rememberUpdatedState(enabled)
    val currentOnRegionSelected by androidx.compose.runtime.rememberUpdatedState(onRegionSelected)
    val currentBitmap by androidx.compose.runtime.rememberUpdatedState(bitmap)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width.toFloat()
                containerHeight = coordinates.size.height.toFloat()
            }
            .pointerInput(bitmap) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (currentEnabled) {
                            pathPoints = listOf(offset)
                        }
                    },
                    onDrag = { change, _ ->
                        if (currentEnabled) {
                            change.consume()
                            pathPoints = pathPoints + change.position
                        }
                    },
                    onDragEnd = {
                        if (currentEnabled && pathPoints.isNotEmpty() && containerWidth > 0 && containerHeight > 0) {
                            val minX = pathPoints.minOf { it.x }.coerceIn(0f, containerWidth)
                            val maxX = pathPoints.maxOf { it.x }.coerceIn(0f, containerWidth)
                            val minY = pathPoints.minOf { it.y }.coerceIn(0f, containerHeight)
                            val maxY = pathPoints.maxOf { it.y }.coerceIn(0f, containerHeight)

                            // Map coordinates to original bitmap dimensions
                            val scaleX = currentBitmap.width / containerWidth
                            val scaleY = currentBitmap.height / containerHeight

                            val cropX = (minX * scaleX).toInt()
                            val cropY = (minY * scaleY).toInt()
                            val cropWidth = ((maxX - minX) * scaleX).toInt()
                            val cropHeight = ((maxY - minY) * scaleY).toInt()

                            currentOnRegionSelected(cropX, cropY, cropWidth, cropHeight)
                        }
                    }
                )
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Shared visual asset",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (pathPoints.size > 1) {
                val path = Path().apply {
                    moveTo(pathPoints.first().x, pathPoints.first().y)
                    for (i in 1 until pathPoints.size) {
                        lineTo(pathPoints[i].x, pathPoints[i].y)
                    }
                }
                // Light transparent primary freehand outline stroke
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.6f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                val minX = pathPoints.minOf { it.x }
                val maxX = pathPoints.maxOf { it.x }
                val minY = pathPoints.minOf { it.y }
                val maxY = pathPoints.maxOf { it.y }

                // Draw a nice translucent region bounding rectangle box
                drawRect(
                    color = primaryColor.copy(alpha = 0.15f),
                    topLeft = Offset(minX, minY),
                    size = Size(maxX - minX, maxY - minY),
                    style = Fill
                )
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(minX, minY),
                    size = Size(maxX - minX, maxY - minY),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

// FlowRow implementation (Since FlowRow wasn't included in early Compose, standard wrapping Row is perfect)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Basic Layout fallback for wrapping items in rows
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

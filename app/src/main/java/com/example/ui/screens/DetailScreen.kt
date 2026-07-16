package com.example.ui.screens

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.components.bounceClick
import com.example.ui.components.MarkdownText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.getBestImagePath
import com.example.ui.viewmodel.SecondBrainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.painterResource
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: SecondBrainViewModel,
    onClose: () -> Unit,
    onEdit: (SavedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeItem by viewModel.activeDetailItem.collectAsState()
    val item = activeItem ?: return

    val customFolders by viewModel.customFolderEntities.collectAsState()
    val folderColor = remember(item, customFolders) {
        val firstFolder = item.folders.firstOrNull()
        val customFolder = if (firstFolder != null) {
            customFolders.find { it.name.equals(firstFolder, ignoreCase = true) }
        } else {
            null
        }
        if (customFolder != null && !customFolder.colorHex.isNullOrBlank()) {
            try {
                Color(android.graphics.Color.parseColor(customFolder.colorHex))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        } ?: when (item.type) {
            SavedItemType.LINK -> Color(0xFF42A5F5)
            SavedItemType.IMAGE -> Color(0xFFAB47BC)
            SavedItemType.VIDEO -> Color(0xFFEF5350)
            SavedItemType.TEXT -> Color(0xFFFFA726)
            SavedItemType.CODE -> Color(0xFF66BB6A)
            SavedItemType.AUDIO -> Color(0xFF26A69A)
        }
    }

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    BackHandler {
        onClose()
    }

    var showLeaveAppDialog by remember { mutableStateOf(false) }
    var urlToOpen by remember { mutableStateOf("") }

    val formatter = remember { SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()) }
    val formattedDate = formatter.format(Date(item.timestamp))

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.id) {
        scale = 1f
        offset = Offset.Zero
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.bounceClick().testTag("detail_back_button")) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_close),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                val shareText = buildString {
                                    appendLine("Title: ${item.title}")
                                    appendLine("Type: ${item.type.displayName}")
                                    appendLine("Content: ${item.content}")
                                    if (item.extractedText != null) {
                                        appendLine("Extracted Text: ${item.extractedText}")
                                    }
                                }
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Memory"))
                        },
                        modifier = Modifier.bounceClick().testTag("detail_share_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_share),
                            contentDescription = "Share memory",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                val shareText = buildString {
                                    appendLine("Title: ${item.title}")
                                    appendLine("Type: ${item.type.displayName}")
                                    appendLine("Content: ${item.content}")
                                    if (item.extractedText != null) {
                                        appendLine("Extracted Text: ${item.extractedText}")
                                    }
                                }
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Memory"))
                        },
                        modifier = Modifier.bounceClick().testTag("detail_bottom_share_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_share),
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Button(
                        onClick = { onEdit(item) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 24.dp)
                            .height(48.dp)
                            .bounceClick()
                            .testTag("detail_bottom_edit_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = folderColor,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_edit),
                            contentDescription = "Edit",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Memory", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // HEADER SECTION
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Title
                val displayTitle = item.title.ifBlank { item.linkTitle ?: "Untitled Memory" }
                Text(
                    text = displayTitle,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 38.sp
                )

                // Meta row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val iconResId = when (item.type) {
                            SavedItemType.LINK -> R.drawable.ic_custom_link
                            SavedItemType.IMAGE -> R.drawable.ic_custom_image
                            SavedItemType.VIDEO -> R.drawable.ic_custom_video
                            SavedItemType.CODE -> R.drawable.ic_custom_code
                            SavedItemType.TEXT -> R.drawable.ic_custom_text
                            SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                        }
                        Icon(
                            painter = painterResource(id = iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.type.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!item.isSynced) {
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_cloud_queue),
                            contentDescription = "Local Only",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Folders
                if (item.folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.folders.forEach { folder ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = folder,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // VISUAL MEDIA
            if (item.type == SavedItemType.IMAGE || item.type == SavedItemType.VIDEO) {
                if (item.type == SavedItemType.VIDEO) {
                    com.example.ui.components.VideoPlayer(
                        videoUri = item.content,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.05f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = item.getBestImagePath(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .pointerInput(item.id) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                                        if (newScale > 1f) {
                                            val maxOffset = (newScale - 1f) * 200f
                                            val newOffset = offset + pan
                                            offset = Offset(
                                                x = newOffset.x.coerceIn(-maxOffset, maxOffset),
                                                y = newOffset.y.coerceIn(-maxOffset, maxOffset)
                                            )
                                        } else {
                                            offset = Offset.Zero
                                        }
                                        scale = newScale
                                    }
                                }
                                .pointerInput(item.id) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                scale = 2.5f
                                            }
                                        }
                                    )
                                }
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }
                }
            }

            // LINK PREVIEW IMAGE
            if (item.type == SavedItemType.LINK && !item.linkImage.isNullOrBlank()) {
                AsyncImage(
                    model = item.linkImage,
                    contentDescription = item.linkTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                )
            }

            if (item.type == SavedItemType.AUDIO && !item.thumbnailPath.isNullOrBlank()) {
                com.example.ui.components.AudioPlayerComponent(
                    audioUri = item.thumbnailPath
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // DESCRIPTION / LINK META
            val descriptionToShow = if (item.type == SavedItemType.LINK) {
                item.linkDescription
            } else if (item.type != SavedItemType.CODE && item.content.isNotBlank() && item.type != SavedItemType.IMAGE && item.type != SavedItemType.VIDEO) {
                item.content
            } else null

            if (!descriptionToShow.isNullOrBlank()) {
                if (item.type == SavedItemType.TEXT || item.type == SavedItemType.AUDIO) {
                    MarkdownText(
                        markdown = descriptionToShow,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                    )
                } else {
                    Text(
                        text = parseMarkdown(descriptionToShow),
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                    )
                }
            }

            // RAW URL / CONTENT SNIPPET
            if (item.type == SavedItemType.LINK || item.type == SavedItemType.CODE) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (item.type == SavedItemType.LINK) "SOURCE LINK" else "CODE SNIPPET",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (item.type == SavedItemType.LINK) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_link_external),
                                        contentDescription = "Open Link",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                urlToOpen = item.content
                                                showLeaveAppDialog = true
                                            },
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_copy),
                                    contentDescription = "Copy",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(item.content))
                                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Text(
                            text = parseMarkdown(item.content),
                            style = if (item.type == SavedItemType.CODE) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // OCR EXTRACTED TEXT
            if (!item.extractedText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_star),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "AI EXTRACTED TEXT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_copy),
                                contentDescription = "Copy",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(item.extractedText))
                                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                    },
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Text(
                            text = item.extractedText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            if (showLeaveAppDialog) {
                AlertDialog(
                    onDismissRequest = { showLeaveAppDialog = false },
                    title = { Text("Leaving App") },
                    text = { Text("You are about to visit an external link in your browser. Do you want to leave the application?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showLeaveAppDialog = false
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Leave App")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showLeaveAppDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

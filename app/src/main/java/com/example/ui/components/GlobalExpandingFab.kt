package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import com.example.R
import com.example.data.model.SavedItemType
import com.example.ui.viewmodel.SecondBrainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.example.utils.DevicePerformance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip

@Composable
fun GlobalExpandingFab(viewModel: SecondBrainViewModel, hazeState: HazeState) {
    var isFabExpanded by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showQuickNoteOverlay by remember { mutableStateOf(false) }
    var captureTitle by remember { mutableStateOf("") }
    var captureContent by remember { mutableStateOf("") }

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    if (isSelectionMode) return

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isFabExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isFabExpanded = false
                    }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        // Quick Note
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quick Note", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    captureTitle = ""
                                    captureContent = ""
                                    showQuickNoteOverlay = true
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_custom_edit), contentDescription = "Quick Note")
                            }
                        }

                        // Voice Memo
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Voice Memo", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    viewModel.startManualCapture(SavedItemType.AUDIO)
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_custom_voice), contentDescription = "Voice Memo")
                            }
                        }

                        // New Folder
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("New Folder", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    showAddFolderDialog = true
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_custom_add_folder), contentDescription = "New Folder")
                            }
                        }

                        // New Item
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("New Item", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    viewModel.startManualCapture(SavedItemType.TEXT)
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_custom_plus), contentDescription = "New Item")
                            }
                        }
                    }
                }

                val context = LocalContext.current
                val fabModifier = if (DevicePerformance.shouldUseBlur(context)) {
                    Modifier
                        .testTag("fab_expand")
                        .size(56.dp)
                        .clip(CircleShape)
                        .hazeChild(state = hazeState, style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            tint = HazeTint(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            blurRadius = 20.dp,
                            noiseFactor = 0.05f
                        ))
                } else {
                    Modifier
                        .testTag("fab_expand")
                        .size(56.dp)
                        .clip(CircleShape)
                }

                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    ),
                    containerColor = if (DevicePerformance.shouldUseBlur(context)) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = fabModifier
                        .clip(CircleShape)
                ) {
                    val rotation by animateFloatAsState(targetValue = if (isFabExpanded) 45f else 0f)
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_plus),
                        contentDescription = "Add options",
                        modifier = Modifier.graphicsLayer(rotationZ = rotation)
                    )
                }
            }
        }
    }

    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddFolderDialog = false
                newFolderName = ""
            },
            title = { Text("New Custom Folder", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createFolder(newFolderName)
                        showAddFolderDialog = false
                        newFolderName = ""
                    }
                ) {
                    Text("Create", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddFolderDialog = false
                    newFolderName = ""
                }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                }
            }
        )
    }

    if (showQuickNoteOverlay) {
        AlertDialog(
            onDismissRequest = {
                showQuickNoteOverlay = false
                captureTitle = ""
                captureContent = ""
            },
            title = { Text("Quick Note", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = captureTitle,
                        onValueChange = { captureTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = captureContent,
                        onValueChange = { captureContent = it },
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startManualCapture(SavedItemType.TEXT)
                        viewModel.updateActiveCaptureItem { item ->
                            item.copy(title = captureTitle, content = captureContent)
                        }
                        viewModel.saveActiveItem()
                        showQuickNoteOverlay = false
                        captureTitle = ""
                        captureContent = ""
                    },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save", color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showQuickNoteOverlay = false
                    captureTitle = ""
                    captureContent = ""
                }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                }
            }
        )
    }
}

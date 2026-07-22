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

package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.SavedItemType
import com.example.ui.components.bounceClick
import com.example.ui.viewmodel.SecondBrainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.example.utils.DevicePerformance

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
    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val blurOpacity by viewModel.blurOpacity.collectAsState()

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
                        // Movie / Anime
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Movie / Anime", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    viewModel.openMediaSearchSheet()
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_custom_movie), contentDescription = "Movie / Anime")
                            }
                        }

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
                val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur
                val fabModifier = if (useBlur) {
                    Modifier
                        .testTag("fab_expand")
                        .size(56.dp)
                        .clip(CircleShape)
                        .hazeEffect(state = hazeState, style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            tint = HazeTint(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = blurOpacity)),
                            blurRadius = blurRadius.dp,
                            noiseFactor = 0.02f
                        ))
                } else {
                    Modifier
                        .testTag("fab_expand")
                        .size(56.dp)
                        .clip(CircleShape)
                }

                val fabInteractionSource = remember { MutableInteractionSource() }
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    shape = CircleShape,
                    interactionSource = fabInteractionSource,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    ),
                    containerColor = if (useBlur) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = fabModifier
                        .bounceClick(fabInteractionSource)
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
        val focusManager = LocalFocusManager.current
        Dialog(onDismissRequest = {
            showAddFolderDialog = false
            newFolderName = ""
        }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {

                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_folder),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "New Folder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Organise your captures",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_folder),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showAddFolderDialog = false
                                newFolderName = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    viewModel.createFolder(newFolderName)
                                    showAddFolderDialog = false
                                    newFolderName = ""
                                }
                            },
                            enabled = newFolderName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }

    if (showQuickNoteOverlay) {
        val focusManager = LocalFocusManager.current
        Dialog(onDismissRequest = {
            showQuickNoteOverlay = false
            captureTitle = ""
            captureContent = ""
        }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {

                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_edit),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Quick Note",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Capture a thought instantly",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )

                    OutlinedTextField(
                        value = captureTitle,
                        onValueChange = { captureTitle = it },
                        label = { Text("Title (optional)") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_text),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = captureContent,
                        onValueChange = { captureContent = it },
                        label = { Text("Note") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_edit),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showQuickNoteOverlay = false
                                captureTitle = ""
                                captureContent = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
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
                            enabled = !isSaving && captureContent.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSaving) {
                                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Save Note")
                            }
                        }
                    }
                }
            }
        }
    }
}

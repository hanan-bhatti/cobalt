package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.CustomFolderEntity
import com.example.ui.screens.FolderDirectoryItem
import com.example.ui.screens.FolderIcon
import com.example.ui.screens.PinnedFolderCard
import com.example.ui.screens.folderPresetColors
import com.example.ui.screens.folderPresetIcons
import com.example.ui.screens.parseHexColor
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    initialFolder: CustomFolderEntity? = null,
    initialItemCount: Int = 0,
    onSave: (CustomFolderEntity, String?) -> Unit,
    onDelete: ((CustomFolderEntity) -> Unit)? = null,
    useBlur: Boolean = false,
    hazeState: HazeState? = null
) {
    val focusManager = LocalFocusManager.current
    val titleFocusRequester = remember { FocusRequester() }
    val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    // State that reacts to initialFolder changes perfectly
    var folderNameInput by remember(initialFolder) { mutableStateOf(initialFolder?.name ?: "") }
    var selectedColorHex by remember(initialFolder) { mutableStateOf(initialFolder?.colorHex ?: folderPresetColors.first().first) }
    var selectedIconName by remember(initialFolder) { mutableStateOf(initialFolder?.iconName ?: folderPresetIcons.first()) }
    var isPinned by remember(initialFolder) { mutableStateOf(initialFolder?.isPinned ?: false) }
    val originalName = initialFolder?.name

    LaunchedEffect(isVisible) {
        if (isVisible && initialFolder == null) {
            // Auto-focus only if creating a new folder
            try {
                titleFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    val previewFolder = CustomFolderEntity(
        name = if (folderNameInput.isBlank()) "New Folder" else folderNameInput,
        colorHex = selectedColorHex,
        iconName = selectedIconName,
        isPinned = isPinned,
        isSynced = initialFolder?.isSynced ?: false
    )

    val transitionState = remember { MutableTransitionState(isVisible) }
    transitionState.targetState = isVisible

    if (transitionState.currentState || transitionState.targetState || !transitionState.isIdle) {
        Dialog(
            onDismissRequest = { onDismiss() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val scrimModifier = if (useBlur && hazeState != null) {
                        Modifier
                            .fillMaxSize()
                            .hazeEffect(state = hazeState, style = HazeStyle(
                                backgroundColor = Color.Black,
                                tint = HazeTint(Color.Black.copy(alpha = 0.3f)),
                                blurRadius = 12.dp,
                                noiseFactor = 0.03f
                            ))
                    } else {
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                    }

                    // Blurred Scrim
                    Box(
                        modifier = scrimModifier.clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onDismiss()
                                focusManager.clearFocus()
                            }
                        )
                    )

                    // Bottom Sheet Content
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = imePadding + 8.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            // Folder Preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPinned) {
                                    PinnedFolderCard(
                                        folder = previewFolder,
                                        count = initialItemCount,
                                        modifier = Modifier.width(160.dp),
                                        onClick = {},
                                        onCustomize = {}
                                    )
                                } else {
                                    FolderDirectoryItem(
                                        folder = previewFolder,
                                        count = initialItemCount,
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        onClick = {},
                                        onCustomize = {}
                                    )
                                }
                            }

                            // Simple Input Field
                            BasicTextField(
                                value = folderNameInput,
                                onValueChange = { if (it.length <= 40) folderNameInput = it },
                                textStyle = MaterialTheme.typography.headlineMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (folderNameInput.isNotBlank()) {
                                            onSave(previewFolder, originalName)
                                            onDismiss()
                                            focusManager.clearFocus()
                                        }
                                    }
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (folderNameInput.isEmpty()) {
                                        Text(
                                            text = "Folder Name",
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(titleFocusRequester)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Pin Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Pin to Home",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = isPinned,
                                    onCheckedChange = { isPinned = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Color Picker
                            Text("Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(folderPresetColors) { (hex, _) ->
                                    val isSelected = hex == selectedColorHex
                                    val color = parseHexColor(hex)
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable { selectedColorHex = hex }
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_custom_check),
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Icon Picker
                            Text("Icon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(folderPresetIcons) { iconName ->
                                    val isSelected = iconName == selectedIconName
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clickable { selectedIconName = iconName }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            FolderIcon(
                                                iconName = iconName,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (initialFolder != null && onDelete != null) {
                                    TextButton(onClick = { onDelete(initialFolder) }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        onDismiss()
                                        focusManager.clearFocus()
                                    }) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (folderNameInput.isNotBlank()) {
                                                onSave(previewFolder, originalName)
                                                onDismiss()
                                                focusManager.clearFocus()
                                            }
                                        },
                                        enabled = folderNameInput.isNotBlank(),
                                        shape = CircleShape
                                    ) {
                                        Text(if (initialFolder == null) "Create" else "Save")
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

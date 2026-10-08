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

package com.example.ui.components
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState


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

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import kotlin.math.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.SavedItemType
import com.example.ui.components.bounceClick
import com.example.ui.viewmodel.CobaltViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.example.utils.DevicePerformance
import com.example.ui.theme.*
import androidx.compose.foundation.isSystemInDarkTheme


data class RadialMenuItem(
    val iconResId: Int,
    val label: String,
    val color: Color,
    val action: () -> Unit
)

@Composable
fun GlobalExpandingFab(viewModel: CobaltViewModel, hazeState: HazeState) {
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

    val context = LocalContext.current
    val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur

    val isDark = isSystemInDarkTheme()
    val items = listOf(
        RadialMenuItem(R.drawable.ic_custom_movie, "Movie / Anime", CategoryMedia.toThemeColor(isDark)) {
            viewModel.openMediaSearchSheet()
        },
        RadialMenuItem(R.drawable.ic_custom_edit, "Quick Note", CategoryText.toThemeColor(isDark)) {
            captureTitle = ""
            captureContent = ""
            showQuickNoteOverlay = true
        },
        RadialMenuItem(R.drawable.ic_custom_voice, "Voice Memo", CategoryAudio.toThemeColor(isDark)) {
            viewModel.startManualCapture(SavedItemType.AUDIO)
        },
        RadialMenuItem(R.drawable.ic_custom_add_folder, "New Folder", DefaultFolderColor.toThemeColor(isDark)) {
            showAddFolderDialog = true
        },
        RadialMenuItem(R.drawable.ic_custom_plus, "New Item", SuccessGreen.toThemeColor(isDark)) {
            viewModel.startManualCapture(SavedItemType.TEXT)
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isFabExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val bgModifier = if (useBlur) {
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
            
            Box(
                modifier = bgModifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isFabExpanded = false
                    }
            )
        }

        val navInsets = androidx.compose.foundation.layout.WindowInsets.navigationBars
        val bottomPadding = navInsets.asPaddingValues().calculateBottomPadding()
        
        val baseFabModifier = if (useBlur) {
            Modifier
                .testTag("fab_expand")
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
                .clip(CircleShape)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            GestureRadialFab(
                items = items,
                isExpanded = isFabExpanded,
                onToggleExpand = { isFabExpanded = it },
                fabBaseModifier = baseFabModifier,
                fabContainerColor = if (useBlur) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                fabContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                mainIconResId = R.drawable.ic_custom_plus
            )
        }
    }

    FolderBottomSheet(
        isVisible = showAddFolderDialog,
        onDismiss = { showAddFolderDialog = false },
        initialFolder = null,
        onSave = { folder, _ ->
            viewModel.createFolder(folder.name, folder.colorHex, folder.iconName, folder.isPinned)
            showAddFolderDialog = false
        },
        useBlur = useBlur,
        hazeState = hazeState
    )

    QuickNoteBottomSheet(
        isVisible = showQuickNoteOverlay,
        onDismiss = {
            showQuickNoteOverlay = false
            captureTitle = ""
            captureContent = ""
        },
        captureTitle = captureTitle,
        onTitleChange = { captureTitle = it },
        captureContent = captureContent,
        onContentChange = { captureContent = it },
        isSaving = isSaving,
        onSave = {
            viewModel.saveQuickNote(captureTitle, captureContent) {
                showQuickNoteOverlay = false
                captureTitle = ""
                captureContent = ""
            }
        },
        useBlur = useBlur,
        hazeState = hazeState
    )
}

@Composable
fun GestureRadialFab(
    items: List<RadialMenuItem>,
    isExpanded: Boolean,
    onToggleExpand: (Boolean) -> Unit,
    fabBaseModifier: Modifier = Modifier,
    fabContainerColor: Color,
    fabContentColor: Color,
    mainIconResId: Int
) {
    var highlightedIndex by remember { mutableStateOf(-1) }
    
    val radiusDp = 160.dp
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.toPx() }
    
    val startAngle = 180.0
    val endAngle = 270.0
    val step = if (items.size > 1) (endAngle - startAngle) / (items.size - 1) else 0.0

    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
    )

    Box(modifier = Modifier, contentAlignment = Alignment.BottomEnd) {
        if (expandProgress > 0.01f) {
            items.forEachIndexed { index, item ->
                val angle = startAngle + step * index
                val angleRad = Math.toRadians(angle)
                
                val targetX = (cos(angleRad) * radiusPx).toFloat()
                val targetY = (sin(angleRad) * radiusPx).toFloat()
                
                val itemProgress by animateFloatAsState(
                    targetValue = if (isExpanded) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                
                val centerOffsetPx = with(density) { -4.dp.toPx() }
                val currentX = targetX * itemProgress + centerOffsetPx
                val currentY = targetY * itemProgress + centerOffsetPx
                val alpha = (itemProgress * 1.5f).coerceIn(0f, 1f)
                val isHighlighted = index == highlightedIndex
                val scale by animateFloatAsState(
                    targetValue = if (isHighlighted) 1.25f else if (isExpanded) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
                )

                Box(
                    modifier = Modifier
                        .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                        .graphicsLayer {
                            this.alpha = alpha
                            this.scaleX = scale
                            this.scaleY = scale
                        }
                        .size(48.dp)
                        .then(fabBaseModifier)
                        .clip(CircleShape)
                        .background(fabContainerColor)
                        .clickable {
                            item.action()
                            onToggleExpand(false)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = item.iconResId),
                        contentDescription = item.label,
                        tint = if (isHighlighted) item.color else item.color.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // The custom FAB base without a competing `clickable` modifier
        Box(
            modifier = Modifier
                .size(56.dp)
                .then(fabBaseModifier)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // CONSUME the down event so parent/child modifiers don't steal it
                        down.consume()
                        
                        onToggleExpand(true)
                        var currentDrag = Offset.Zero
                        var localHighlighted = -1
                        
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            
                            // CONSUME every movement so the UI knows we are actively handling it
                            if (change.position != change.previousPosition) {
                                change.consume()
                            }
                            
                            if (change.pressed) {
                                val posChange = change.position - down.position
                                currentDrag = posChange
                                
                                val dx = currentDrag.x
                                val dy = currentDrag.y 
                                var adjustedAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))
                                if (adjustedAngle < 0) adjustedAngle += 360.0
                                
                                var minDiff = Double.MAX_VALUE
                                var closestIndex = -1
                                for (i in items.indices) {
                                    val itemAngle = startAngle + step * i
                                    val diff = kotlin.math.abs(itemAngle - adjustedAngle)
                                    if (diff < minDiff) {
                                        minDiff = diff
                                        closestIndex = i
                                    }
                                }
                                
                                val distance = kotlin.math.sqrt(dx*dx + dy*dy)
                                if (distance > 30.dp.toPx()) {
                                    localHighlighted = closestIndex
                                    highlightedIndex = closestIndex
                                } else {
                                    localHighlighted = -1
                                    highlightedIndex = -1
                                }
                            } else {
                                // Pointer lifted
                                if (localHighlighted in items.indices) {
                                    items[localHighlighted].action()
                                }
                                onToggleExpand(false)
                                highlightedIndex = -1
                                break
                            }
                        }
                    }
                }
                .background(fabContainerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val rotation by animateFloatAsState(targetValue = if (isExpanded) 45f else 0f)
            Icon(
                painter = painterResource(id = mainIconResId),
                contentDescription = "Add options",
                tint = fabContentColor,
                modifier = Modifier.graphicsLayer(rotationZ = rotation)
            )
        }
    }
}


@Composable
fun ClassicExpandingFab(viewModel: CobaltViewModel, hazeState: HazeState) {
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

    val context = LocalContext.current
    val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isFabExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val bgModifier = if (useBlur) {
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
            
            Box(
                modifier = bgModifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isFabExpanded = false
                    }
            )
        }

        val navInsets = androidx.compose.foundation.layout.WindowInsets.navigationBars
        val bottomPadding = navInsets.asPaddingValues().calculateBottomPadding()
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding)
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

    FolderBottomSheet(
        isVisible = showAddFolderDialog,
        onDismiss = { showAddFolderDialog = false },
        initialFolder = null,
        onSave = { folder, _ ->
            viewModel.createFolder(folder.name, folder.colorHex, folder.iconName, folder.isPinned)
            showAddFolderDialog = false
        },
        useBlur = useBlur,
        hazeState = hazeState
    )

    QuickNoteBottomSheet(
        isVisible = showQuickNoteOverlay,
        onDismiss = {
            showQuickNoteOverlay = false
            captureTitle = ""
            captureContent = ""
        },
        captureTitle = captureTitle,
        onTitleChange = { captureTitle = it },
        captureContent = captureContent,
        onContentChange = { captureContent = it },
        isSaving = isSaving,
        onSave = {
            viewModel.saveQuickNote(captureTitle, captureContent) {
                showQuickNoteOverlay = false
                captureTitle = ""
                captureContent = ""
            }
        },
        useBlur = useBlur,
        hazeState = hazeState
    )
}


@Composable
fun QuickNoteBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    captureTitle: String,
    onTitleChange: (String) -> Unit,
    captureContent: String,
    onContentChange: (String) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit,
    useBlur: Boolean,
    hazeState: HazeState
) {
    val focusManager = LocalFocusManager.current
    val titleFocusRequester = remember { FocusRequester() }

    // Auto-focus title when opened
    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(100) // Wait for bottom sheet animation
            try {
                titleFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus error
            }
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val scrimModifier = if (useBlur) {
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
                modifier = scrimModifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismiss()
                        focusManager.clearFocus()
                    }
            )

            // Bottom Sheet Canvas
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        // Make sure it doesn't take maximum height when empty, wrapping content nicely
                        .wrapContentHeight()
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            // Allow scroll if content gets too long
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Drag handle pill
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Title Input (Borderless)
                        BasicTextField(
                            value = captureTitle,
                            onValueChange = onTitleChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (captureTitle.isEmpty()) {
                                    Text(
                                        text = "Title",
                                        style = MaterialTheme.typography.headlineSmall.copy(
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // Body Input (Borderless, Auto-expanding)
                        BasicTextField(
                            value = captureContent,
                            onValueChange = onContentChange,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (captureContent.isEmpty()) {
                                    Text(
                                        text = "Start typing...",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp) // Removed weight(1f) to fix layout gap, set minHeight instead
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    onDismiss()
                                    focusManager.clearFocus()
                                }
                            ) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onSave()
                                    focusManager.clearFocus()
                                },
                                enabled = !isSaving && captureContent.isNotBlank(),
                                shape = CircleShape
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
}

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
import androidx.activity.compose.BackHandler
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import com.example.ui.screens.MarkdownAccessoryToolbar
import com.example.ui.screens.MarkdownVisualTransformation


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
    var showQuickNoteOverlay by remember { mutableStateOf(false) }
    var userHasEditedInSession by remember(showQuickNoteOverlay) { mutableStateOf(false) }
    val importText = rememberTextImportAction(viewModel)
    val quickDraft by viewModel.quickDraft.collectAsState()
    val captureTitle = quickDraft.title
    val captureContent = quickDraft.text
    LaunchedEffect(showQuickNoteOverlay) { if (showQuickNoteOverlay) viewModel.loadQuickDraft() }

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val forceDisableBlur by viewModel.forceDisableBlur.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val blurOpacity by viewModel.blurOpacity.collectAsState()

    if (isSelectionMode) return

    val context = LocalContext.current
    val useBlur = DevicePerformance.isDeviceCapableOfBlur(context) && !forceDisableBlur

    val isDark = isSystemInDarkTheme()
    var showMoreActions by remember { mutableStateOf(false) }
    val imageAction = rememberFabImageAction(viewModel)
    val compactMenu = LocalConfiguration.current.screenHeightDp < 340 || LocalConfiguration.current.screenWidthDp < 300
    BackHandler(enabled = isFabExpanded) { isFabExpanded = false }
    val items = listOf(
        RadialMenuItem(R.drawable.ic_custom_edit, "Note", CategoryText.toThemeColor(isDark)) { showQuickNoteOverlay = true },
        RadialMenuItem(R.drawable.ic_custom_link, "Link", CategoryLink.toThemeColor(isDark)) { viewModel.startManualCapture(SavedItemType.LINK) },
        RadialMenuItem(R.drawable.ic_custom_image, "Image", CategoryImage.toThemeColor(isDark), imageAction),
        RadialMenuItem(R.drawable.ic_custom_voice, "Voice", CategoryAudio.toThemeColor(isDark)) { viewModel.startManualCapture(SavedItemType.AUDIO) },
        RadialMenuItem(R.drawable.ic_custom_import, "Import", CategoryText.toThemeColor(isDark), importText),
        RadialMenuItem(R.drawable.ic_custom_more_vert, "More", MaterialTheme.colorScheme.onSurface) { showMoreActions = true }
    )
    if (showMoreActions) FabActionSheet("More actions", listOf(
        RadialMenuItem(R.drawable.ic_custom_movie, "Movie / Anime", CategoryMedia.toThemeColor(isDark)) { viewModel.openMediaSearchSheet() },
        RadialMenuItem(R.drawable.ic_custom_add_folder, "New Folder", DefaultFolderColor.toThemeColor(isDark)) { showAddFolderDialog = true },
        RadialMenuItem(R.drawable.ic_custom_video, "Video", CategoryVideo.toThemeColor(isDark)) { viewModel.startManualCapture(SavedItemType.VIDEO) },
        RadialMenuItem(R.drawable.ic_custom_code, "Code", CategoryCode.toThemeColor(isDark)) { viewModel.startManualCapture(SavedItemType.CODE) }
    ), onDismiss = { showMoreActions = false })
    if (compactMenu && isFabExpanded) FabActionSheet("Add to Cobalt", items, onDismiss = { isFabExpanded = false })

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isFabExpanded && !compactMenu,
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
                .clip(CircleShape)
                .hazeEffect(state = hazeState, style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    tint = HazeTint(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = blurOpacity)),
                    blurRadius = blurRadius.dp,
                    noiseFactor = 0.02f
                ))
        } else {
            Modifier
                .clip(CircleShape)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            if (compactMenu) FloatingActionButton(onClick = { isFabExpanded = true }, modifier = baseFabModifier.testTag("fab_expand")) {
                Icon(painterResource(R.drawable.ic_custom_plus), "Add options")
            } else GestureRadialFab(
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
        },
        captureTitle = captureTitle,
        onTitleChange = {
            userHasEditedInSession = true
            viewModel.updateQuickDraftTitle(it)
        },
        captureContent = captureContent,
        onContentChange = {
            userHasEditedInSession = true
            viewModel.updateQuickDraft(it)
        },
        isSaving = isSaving || !quickDraft.ready || quickDraft.busy,
        savedDraftPreview = if (userHasEditedInSession) null else quickDraft.snapshot?.takeIf { captureContent.isEmpty() && it.bytes > 0 }?.preview,
        draftError = quickDraft.error,
        onRetryDraft = viewModel::loadQuickDraft,
        hasPendingDraft = quickDraft.pending || quickDraft.snapshot != null,
        onContinueInEditor = {
            if (captureContent.isBlank() && !quickDraft.pending && quickDraft.snapshot == null) {
                showQuickNoteOverlay = false
                viewModel.startManualCapture(SavedItemType.TEXT)
            } else viewModel.saveQuickDraft(emptyList(), edit = true, onSaved = { showQuickNoteOverlay = false })
        },
        onRestoreDraft = {
            viewModel.saveQuickDraft(emptyList(), open = true, onSaved = { showQuickNoteOverlay = false })
        },
        onSave = {
            viewModel.saveQuickDraft(emptyList()) {
                showQuickNoteOverlay = false
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
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    val currentItems by rememberUpdatedState(items)
    val currentExpanded by rememberUpdatedState(isExpanded)
    val toggle by rememberUpdatedState(onToggleExpand)
    val haptics = LocalHapticFeedback.current
    val radius = if (items.size > 5) 192.dp else 160.dp
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val progress by animateFloatAsState(if (isExpanded) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    LaunchedEffect(isExpanded) { if (!isExpanded) highlightedIndex = -1 }
    BackHandler(enabled = isExpanded) { onToggleExpand(false) }

    fun highlight(index: Int) {
        if (index != highlightedIndex) {
            highlightedIndex = index
            if (index >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(contentAlignment = Alignment.BottomEnd) {
        if (progress > 0.01f) {
            items.forEachIndexed { index, item ->
                val angle = Math.toRadians(180.0 + if (items.size > 1) 90.0 * index / (items.size - 1) else 0.0)
                val active = index == highlightedIndex
                val scale by animateFloatAsState(if (active) 1.08f else 1f, spring())
                Box(Modifier.offset {
                    IntOffset((cos(angle) * radiusPx * progress - with(density) { 4.dp.toPx() }).roundToInt(),
                        (sin(angle) * radiusPx * progress - with(density) { 4.dp.toPx() }).roundToInt())
                }.size(48.dp).graphicsLayer { alpha = progress; scaleX = scale; scaleY = scale }
                    .then(fabBaseModifier).clip(CircleShape)
                    .background(if (active) item.color.copy(alpha = 1f) else fabContainerColor)
                    .semantics { selected = active }
                    .clickable(enabled = isExpanded, onClickLabel = item.label) {
                        highlight(index)
                        onToggleExpand(false)
                        item.action()
                    }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(item.iconResId), item.label, tint = if (active) radialFabIconColor(item.color.copy(alpha = 1f)) else item.color,
                        modifier = Modifier.size(22.dp))
                }
            }
            val label = items.getOrNull(highlightedIndex)?.label ?: "Choose an action"
            Surface(Modifier.align(Alignment.BottomEnd).offset(y = -(radius + 56.dp)),
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Box(Modifier.size(56.dp).testTag("fab_expand").then(fabBaseModifier)
            .semantics {
                contentDescription = "Add options"
                stateDescription = if (isExpanded) "Expanded" else "Collapsed"
                role = androidx.compose.ui.semantics.Role.Button
                onClick { toggle(!currentExpanded); true }
            }
            .pointerInput(items.size, radiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val initiallyExpanded = currentExpanded
                    var dragged = false
                    var held = false
                    var cancelled = false
                    try {
                        while (true) {
                            val event = if (!held && !dragged) {
                                withTimeoutOrNull((viewConfiguration.longPressTimeoutMillis -
                                    (android.os.SystemClock.uptimeMillis() - down.uptimeMillis)).coerceAtLeast(1)) { awaitPointerEvent() }
                            } else awaitPointerEvent()
                            if (event == null) {
                                held = true
                                toggle(true)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || event.changes.size != 1 || change.isConsumed) { cancelled = true; break }
                            if (!change.pressed) {
                                change.consume()
                                if (dragged) {
                                    val index = highlightedIndex
                                    toggle(false)
                                    if (index in currentItems.indices) currentItems[index].action()
                                } else toggle(if (held) true else !initiallyExpanded)
                                break
                            }
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                            if (dragged) {
                                toggle(true)
                                val delta = change.position - Offset(size.width / 2f, size.height / 2f)
                                highlight(radialFabIndex(delta.x / density.density, delta.y / density.density, radius.value, currentItems.size))
                            }
                            change.consume()
                        }
                    } finally {
                        if (cancelled || !currentEvent.changes.all { !it.pressed }) {
                            if (dragged || held) toggle(false)
                        }
                        highlight(-1)
                    }
                }
            }.background(fabContainerColor, CircleShape), contentAlignment = Alignment.Center) {
            val rotation by animateFloatAsState(if (isExpanded) 45f else 0f)
            Icon(painterResource(mainIconResId), null, tint = fabContentColor,
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation })
        }
    }
}


@Composable
fun ClassicExpandingFab(viewModel: CobaltViewModel, hazeState: HazeState) {
    var isFabExpanded by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showQuickNoteOverlay by remember { mutableStateOf(false) }
    var userHasEditedInSession by remember(showQuickNoteOverlay) { mutableStateOf(false) }
    val importText = rememberTextImportAction(viewModel)
    val quickDraft by viewModel.quickDraft.collectAsState()
    val captureTitle = quickDraft.title
    val captureContent = quickDraft.text
    LaunchedEffect(showQuickNoteOverlay) { if (showQuickNoteOverlay) viewModel.loadQuickDraft() }

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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Import text", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
                            SmallFloatingActionButton(onClick = { isFabExpanded = false; importText() },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary) {
                                Icon(painterResource(R.drawable.ic_custom_import), contentDescription = "Import text document")
                            }
                        }

                        // Quick Note
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quick Note", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp))
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
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
        },
        captureTitle = captureTitle,
        onTitleChange = {
            userHasEditedInSession = true
            viewModel.updateQuickDraftTitle(it)
        },
        captureContent = captureContent,
        onContentChange = {
            userHasEditedInSession = true
            viewModel.updateQuickDraft(it)
        },
        isSaving = isSaving || !quickDraft.ready || quickDraft.busy,
        savedDraftPreview = if (userHasEditedInSession) null else quickDraft.snapshot?.takeIf { captureContent.isEmpty() && it.bytes > 0 }?.preview,
        draftError = quickDraft.error,
        onRetryDraft = viewModel::loadQuickDraft,
        hasPendingDraft = quickDraft.pending || quickDraft.snapshot != null,
        onRestoreDraft = {
            viewModel.saveQuickDraft(emptyList(), open = true, onSaved = { showQuickNoteOverlay = false })
        },
        onSave = {
            viewModel.saveQuickDraft(emptyList()) {
                showQuickNoteOverlay = false
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
    hazeState: HazeState,
    savedDraftPreview: String? = null,
    draftError: String? = null,
    hasPendingDraft: Boolean = false,
    onRetryDraft: () -> Unit = {},
    onRestoreDraft: () -> Unit = {},
    onContinueInEditor: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val titleFocusRequester = remember { FocusRequester() }

    var contentValue by remember {
        mutableStateOf(TextFieldValue(text = captureContent, selection = TextRange(captureContent.length)))
    }
    LaunchedEffect(captureContent) {
        if (contentValue.text != captureContent) {
            contentValue = contentValue.copy(text = captureContent, selection = TextRange(captureContent.length))
        }
    }
    var isPreviewMode by remember { mutableStateOf(false) }

    val transitionState = remember { MutableTransitionState(isVisible) }
    transitionState.targetState = isVisible

    // Request title focus immediately when sheet becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            try {
                titleFocusRequester.requestFocus()
            } catch (_: Exception) {
                // Ignore focus error
            }
        }
    }

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
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(100)),
                modifier = Modifier.fillMaxSize()
            ) {
                val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val navPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomInsetPadding = maxOf(imePadding, navPadding)

                Box(modifier = Modifier.fillMaxSize()) {
                    val scrimModifier = if (useBlur) {
                        Modifier
                            .fillMaxSize()
                            .hazeEffect(
                                state = hazeState,
                                style = HazeStyle(
                                    backgroundColor = Color.Black,
                                    tint = HazeTint(Color.Black.copy(alpha = 0.35f)),
                                    blurRadius = 14.dp,
                                    noiseFactor = 0.03f
                                )
                            )
                    } else {
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                    }

                    // Scrim
                    Box(
                        modifier = scrimModifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onDismiss()
                                focusManager.clearFocus()
                            }
                        )
                    )

                    // Stable Bottom Sheet Surface - completely free of jittery slide animations
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = bottomInsetPadding)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Header: Action row (Cancel & Save)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        onDismiss()
                                        focusManager.clearFocus()
                                    }
                                ) {
                                    Text(
                                        "Cancel",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Quick Note", style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    onContinueInEditor?.let { continueEditing ->
                                        IconButton(onClick = continueEditing, enabled = !isSaving, modifier = Modifier.size(36.dp)) {
                                            Icon(painterResource(R.drawable.ic_custom_link_external),
                                                "Save draft and continue in full editor", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        onSave()
                                        focusManager.clearFocus()
                                    },
                                    enabled = !isSaving && (captureContent.isNotBlank() || savedDraftPreview != null || hasPendingDraft),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    if (isSaving) {
                                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Content area: Title, Divider, and Markdown Body (Adaptive height, no void gaps)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                // 1. Clean Title Input (Borderless)
                                BasicTextField(
                                    value = captureTitle,
                                    onValueChange = onTitleChange,
                                    enabled = !isSaving,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 20.sp,
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
                                                    fontSize = 20.sp,
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

                                Spacer(modifier = Modifier.height(8.dp))

                                // 2. Clean Horizontal Line
                                HorizontalDivider(
                                    thickness = 0.75.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // 3. Body Input (Borderless or Markdown Preview)
                                draftError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = onRetryDraft) { Text("Retry loading draft") }
                                }
                                if (savedDraftPreview != null) {
                                    Text("A large draft is stored privately. Restore it to continue editing.")
                                    Text(savedDraftPreview, maxLines = 3)
                                    TextButton(onClick = onRestoreDraft, enabled = !isSaving) { Text("Restore draft and open") }
                                } else if (!isPreviewMode) {
                                    BasicTextField(
                                        value = contentValue,
                                        enabled = !isSaving,
                                        onValueChange = { updated ->
                                            if (!com.example.notes.NoteEditorWindows.requiresWindow(updated.text)) contentValue = updated
                                            onContentChange(updated.text)
                                        },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        visualTransformation = MarkdownVisualTransformation(),
                                        decorationBox = { innerTextField ->
                                            if (contentValue.text.isEmpty()) {
                                                Text(
                                                    text = "Start typing thoughts in markdown...",
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                        fontSize = 15.sp,
                                                        lineHeight = 22.sp
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 70.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 70.dp)
                                            .padding(vertical = 4.dp)
                                    ) {
                                        if (contentValue.text.isBlank()) {
                                            Text(
                                                text = "Nothing to preview yet. Start typing in Edit mode!",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        } else {
                                            MarkdownText(
                                                markdown = contentValue.text,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                onMarkdownChange = { updated ->
                                                    contentValue = TextFieldValue(text = updated, selection = TextRange(updated.length))
                                                    onContentChange(updated)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Markdown Accessory Toolbar Pinned Above Keyboard
                            if (!isSaving && savedDraftPreview == null && (imePadding > 0.dp || isPreviewMode)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        MarkdownAccessoryToolbar(
                                            textFieldValue = contentValue,
                                            onApplyFormat = { updated ->
                                                if (!com.example.notes.NoteEditorWindows.requiresWindow(updated.text)) contentValue = updated
                                                onContentChange(updated.text)
                                            },
                                            isPreviewMode = isPreviewMode,
                                            onTogglePreview = { isPreviewMode = !isPreviewMode },
                                            modifier = Modifier.fillMaxWidth()
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

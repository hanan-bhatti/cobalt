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

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.example.util.displayTitle
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SavedItem
import com.example.notes.NotePersistence
import com.example.notes.NoteSizePolicy
import com.example.notes.NoteTextNavigator
import com.example.utils.DevicePerformance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modern Bottom Sheet for Note Actions (replaces the standard top dropdown menu).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteActionsBottomSheet(
    item: SavedItem,
    plain: Boolean,
    onPlainChange: (Boolean) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFindInNote: () -> Unit,
    onShowOutline: () -> Unit,
    onExportMarkdown: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "NOTE ACTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.displayTitle(context),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Group 1: Navigation & Discovery
            NoteActionGroupCard {
                NoteActionRowItem(
                    icon = painterResource(id = R.drawable.ic_custom_search),
                    title = "Find in note",
                    subtitle = "Search keywords & text",
                    onClick = {
                        onDismissRequest()
                        onFindInNote()
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )
                NoteActionRowItem(
                    icon = painterResource(id = R.drawable.ic_custom_outline),
                    title = "Outline",
                    subtitle = "Jump to headings & sections",
                    onClick = {
                        onDismissRequest()
                        onShowOutline()
                    }
                )
            }

            // Group 2: View & Version History
            NoteActionGroupCard {
                if (item.byteLength < NoteSizePolicy.TIER_B_MAX_BYTES) {
                    NoteActionRowItem(
                        icon = painterResource(id = R.drawable.ic_custom_markdown),
                        title = if (plain) "Plain Text Mode" else "Markdown Mode",
                        subtitle = if (plain) "Viewing raw unformatted text" else "Formatted rich preview",
                        trailing = {
                            Switch(
                                checked = !plain,
                                onCheckedChange = { onPlainChange(!plain) },
                                modifier = Modifier.height(28.dp)
                            )
                        },
                        onClick = { onPlainChange(!plain) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )
                }

                NoteActionRowItem(
                    icon = painterResource(id = R.drawable.ic_custom_undo),
                    title = "Undo last edit",
                    subtitle = if (canUndo) "Revert to previous saved version" else "No earlier version",
                    enabled = canUndo,
                    onClick = {
                        onDismissRequest()
                        onUndo()
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )
                NoteActionRowItem(
                    icon = painterResource(id = R.drawable.ic_custom_redo),
                    title = "Redo saved edit",
                    subtitle = if (canRedo) "Re-apply reverted edit" else "No newer version",
                    enabled = canRedo,
                    onClick = {
                        onDismissRequest()
                        onRedo()
                    }
                )
            }

            // Group 3: File & Share
            NoteActionGroupCard {
                NoteActionRowItem(
                    icon = painterResource(id = R.drawable.ic_custom_export),
                    title = "Export Markdown",
                    subtitle = "Save .md file to device storage",
                    onClick = {
                        onDismissRequest()
                        onExportMarkdown()
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )
                NoteActionRowItem(
                    icon = painterResource(id = R.drawable.ic_custom_share),
                    title = "Share Note",
                    subtitle = "Send content to other apps",
                    onClick = {
                        onDismissRequest()
                        onShare()
                    }
                )
            }

            // Group 4: Destructive Action
            NoteActionGroupCard(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f),
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            ) {
                NoteActionRowItem(
                    icon = painterResource(id = R.drawable.ic_custom_delete),
                    title = "Delete Note",
                    subtitle = "Permanently remove or delete this note",
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = {
                        onDismissRequest()
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Outline bottom sheet showing heading hierarchy and allowing one-tap jumps.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteOutlineBottomSheet(
    outline: List<NoteTextNavigator.Heading>,
    onSelectHeading: (Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_outline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Note Outline",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${outline.size} headings found",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (outline.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No headings found",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Add markdown headings like '# Heading' or '## Subheading' to see an outline here.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(outline) { heading ->
                        val indent = ((heading.level - 1).coerceAtLeast(0) * 16).dp
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indent)
                                .clickable {
                                    onSelectHeading(heading.offset)
                                    onDismissRequest()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    Text(
                                        text = "H${heading.level}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = heading.title,
                                    fontSize = if (heading.level == 1) 15.sp else 14.sp,
                                    fontWeight = if (heading.level <= 2) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern floating blurred search bar docked above keyboard / navigation bar.
 */
@Composable
fun NoteFloatingSearchBar(
    item: SavedItem,
    localHazeState: HazeState,
    onJump: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    useBlur: Boolean = true,
    onSearchStateChanged: (query: String, activeOffset: Long?, totalMatches: Int, currentIndex: Int) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persistence = remember { NotePersistence(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Long>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(query) {
        val hash = item.contentHash
        if (hash == null || query.isBlank()) {
            searchJob?.cancel()
            matches = emptyList()
            currentIndex = -1
            isSearching = false
            onSearchStateChanged("", null, 0, -1)
            return@LaunchedEffect
        }

        isSearching = true
        delay(120)
        searchJob?.cancel()
        searchJob = scope.launch {
            try {
                val foundMatches = withContext(Dispatchers.IO) {
                    val cancellation = currentCoroutineContext()
                    persistence.store.open(item.id, hash).use { stream ->
                        NoteTextNavigator.findAll(stream, query) { cancellation.ensureActive() }
                    }
                }
                matches = foundMatches
                if (foundMatches.isNotEmpty()) {
                    currentIndex = 0
                    val active = foundMatches[0]
                    onJump(active)
                    onSearchStateChanged(query, active, foundMatches.size, 0)
                } else {
                    currentIndex = -1
                    onSearchStateChanged(query, null, 0, -1)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                matches = emptyList()
                currentIndex = -1
                onSearchStateChanged(query, null, 0, -1)
            } finally {
                isSearching = false
            }
        }
    }

    fun navigateMatch(forward: Boolean) {
        if (matches.isEmpty()) return
        val nextIndex = if (forward) {
            (currentIndex + 1) % matches.size
        } else {
            if (currentIndex - 1 < 0) matches.size - 1 else currentIndex - 1
        }
        currentIndex = nextIndex
        val targetOffset = matches[nextIndex]
        onJump(targetOffset)
        onSearchStateChanged(query, targetOffset, matches.size, nextIndex)
    }

    val containerModifier = if (useBlur) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .hazeEffect(
                state = localHazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.55f)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.02f
                )
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.5f),
                RoundedCornerShape(26.dp)
            )
    } else {
        Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(26.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.4f),
                RoundedCornerShape(26.dp)
            )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(containerModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Icon
            Icon(
                painter = painterResource(id = R.drawable.ic_custom_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            // Text Input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Find in note...",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = {
                        if (it.length <= 256) {
                            query = it
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { navigateMatch(forward = true) })
                )
            }

            // Status count or Spinner
            val statusText = when {
                isSearching -> null
                query.isBlank() -> null
                matches.isEmpty() -> "0/0"
                else -> "${currentIndex + 1}/${matches.size}"
            }
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (statusText != null) {
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (matches.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            // Up arrow (Previous match)
            IconButton(
                onClick = { navigateMatch(forward = false) },
                enabled = matches.isNotEmpty() && !isSearching,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_chevron_up),
                    contentDescription = "Previous match",
                    tint = if (matches.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Down arrow (Next match)
            IconButton(
                onClick = { navigateMatch(forward = true) },
                enabled = matches.isNotEmpty() && !isSearching,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_chevron_down),
                    contentDescription = "Next match",
                    tint = if (matches.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )

            // Close Search Button
            IconButton(
                onClick = {
                    searchJob?.cancel()
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onClose()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_close),
                    contentDescription = "Close search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Grouped rounded container for bottom sheet action items (Apple / Material 3 style).
 */
@Composable
private fun NoteActionGroupCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * Individual action row inside a group card.
 */
@Composable
private fun NoteActionRowItem(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBgColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconBgColor.copy(alpha = iconBgColor.alpha * alpha),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = iconTint.copy(alpha = iconTint.alpha * alpha),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor.copy(alpha = titleColor.alpha * alpha)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f * alpha)
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

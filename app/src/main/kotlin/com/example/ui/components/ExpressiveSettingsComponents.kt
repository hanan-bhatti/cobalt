package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.collectAsState
import com.example.ui.viewmodel.CobaltViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.util.HapticManager

/**
 * Returns an adaptive RoundedCornerShape for connected capsule items (Pill Group)
 * matching PixelPlayer's Material 3 Expressive connected list design.
 */
fun connectedPillShape(index: Int, totalItems: Int): RoundedCornerShape {
    return when {
        totalItems <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        index == totalItems - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(4.dp)
    }
}

/**
 * Clean PixelPlayer-style Subsection Header for settings sub-screens.
 * Renders a bold, primary-colored label without icon badges to keep the UI
 * clean, focused, and hierarchy-clear.
 */
@Composable
fun SettingsSubsectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    tooltipText: String? = null
) {
    var showTooltipDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            if (tooltipText != null) {
                IconButton(
                    onClick = { showTooltipDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_custom_info),
                        contentDescription = "More information",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showTooltipDialog && tooltipText != null) {
        AlertDialog(
            onDismissRequest = { showTooltipDialog = false },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tooltipText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showTooltipDialog = false }) {
                    Text("Got it")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

/**
 * Section Header for settings groups with icon, title, optional description, and info tooltip dialog.
 */
@Composable
fun ExpressiveSettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    iconRes: Int? = null,
    iconVector: ImageVector? = null,
    tooltipText: String? = null
) {
    var showTooltipDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (iconRes != null || iconVector != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (iconVector != null) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (tooltipText != null) {
                IconButton(
                    onClick = { showTooltipDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_custom_info),
                        contentDescription = "More information",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (description != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (iconRes != null || iconVector != null) 38.dp else 0.dp)
            )
        }
    }

    if (showTooltipDialog && tooltipText != null) {
        AlertDialog(
            onDismissRequest = { showTooltipDialog = false },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tooltipText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showTooltipDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

/**
 * Connected capsule container holding items with 2.dp spacing.
 */
@Composable
fun ExpressiveSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
    }
}

/**
 * Switch item for expressive capsule lists.
 */
@Composable
fun ExpressiveSettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    compact: Boolean = false
) {
    Surface(
        color = if (compact) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = modifier.fillMaxWidth().then(
            if (compact) Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ) else Modifier
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 16.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp)
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier.size(if (compact) 40.dp else 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    leadingIcon()
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = if (compact) FontWeight.Medium else FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = if (compact) Modifier.clearAndSetSemantics { } else Modifier
            )
        }
    }
}

/**
 * Clickable Navigation Item inside a capsule group with chevron indicator.
 */
@Composable
fun ExpressiveSettingsNavigationItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingBadge: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    compact: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (compact) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp)
        ) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier.size(if (compact) 40.dp else 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    leadingIcon()
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = if (compact) FontWeight.Medium else FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (trailingBadge != null) {
                if (compact) {
                    Text(
                        text = trailingBadge,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                ) {
                    Text(
                        text = trailingBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                }
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(if (compact) 24.dp else 18.dp)
            )
        }
    }
}

/**
 * Modern selector tile with pill value badge and full ModalBottomSheet picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSettingsSelectorItem(
    title: String,
    options: Map<String, String>,
    selectedKey: String,
    onSelectionChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    optionColor: ((String) -> Color)? = null,
    sheetContent: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    compact: Boolean = false,
    selectedValueInDescription: Boolean = false,
    enabled: Boolean = true
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedOption = options[selectedKey] ?: selectedKey

    if (compact) {
        ExpressiveSettingsNavigationItem(
            title = title,
            subtitle = if (selectedValueInDescription && description != null) {
                stringResource(R.string.settings_selected_detail, selectedOption, description)
            } else description,
            trailingBadge = if (selectedValueInDescription) null else selectedOption,
            leadingIcon = leadingIcon,
            onClick = { showSheet = true },
            modifier = modifier,
            shape = shape,
            enabled = enabled,
            compact = true
        )
    } else {
    Surface(
        onClick = { showSheet = true },
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (leadingIcon != null) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        leadingIcon()
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (description != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            optionColor?.invoke(selectedKey)?.let { color ->
                                Box(Modifier.size(16.dp).background(color, CircleShape))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = selectedOption,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    }

    if (showSheet && sheetContent != null) {
        sheetContent { showSheet = false }
    } else if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options.entries.toList()) { (key, optionLabel) ->
                        val isSelected = key == selectedKey
                        val swatchColor = optionColor?.invoke(key)
                        val containerColor = if (swatchColor != null) swatchColor.copy(alpha = if (isSelected) 0.24f else 0.12f) else if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                        val contentColor = if (swatchColor != null) MaterialTheme.colorScheme.onSurface else if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

                        Surface(
                            onClick = {
                                onSelectionChanged(key)
                                showSheet = false
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = containerColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (swatchColor != null) {
                                    Box(Modifier.size(28.dp).background(swatchColor, CircleShape))
                                    Spacer(Modifier.width(16.dp))
                                }
                                Text(
                                    text = optionLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_custom_check),
                                        contentDescription = "Selected",
                                        tint = contentColor
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

/**
 * Searchable AI model selector with bottom sheet picker and refresh capability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSearchableModelSelectorItem(
    title: String,
    models: List<String>,
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    onRefreshModels: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    var showSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Surface(
        onClick = { showSheet = true },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (leadingIcon != null) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        leadingIcon()
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (description != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                            text = selectedModel.ifBlank { "Default Model" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                searchQuery = ""
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (onRefreshModels != null) {
                        FilledTonalButton(
                            onClick = onRefreshModels,
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_sync),
                                    contentDescription = "Refresh models",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Gemini models...") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_custom_search),
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_custom_close),
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                val filteredModels = remember(models, searchQuery) {
                    if (searchQuery.isBlank()) models
                    else models.filter { it.contains(searchQuery, ignoreCase = true) }
                }

                Text(
                    text = "${filteredModels.size} model${if (filteredModels.size != 1) "s" else ""} available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredModels.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (models.isEmpty()) "No models loaded yet. Tap Refresh above." else "No matching models found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredModels, key = { it }) { model ->
                            val isSelected = model == selectedModel
                            val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

                            Surface(
                                onClick = {
                                    onModelSelected(model)
                                    showSheet = false
                                    searchQuery = ""
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = containerColor,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = contentColor,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isSelected) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_custom_success_circle),
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
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

/**
 * Expressive slider row with live value indicator badge and smooth slider control.
 */
@Composable
fun ExpressiveSettingsSliderItem(
    title: String,
    value: Float,
    valueDisplay: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (leadingIcon != null) {
                        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            leadingIcon()
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = CircleShape
                ) {
                    Text(
                        text = valueDisplay,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Segmented selection options inside a single capsule card (e.g. Low, Medium, High).
 */
@Composable
fun ExpressiveSegmentedChoiceItem(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        leadingIcon()
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    Button(
                        onClick = { onOptionSelected(option) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = option,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Expressive Typography Slider Item featuring:
 * - Live value badge and unit
 * - Endpoint hints at both ends reflecting the font family
 * - Step snapping and light haptic tick via HapticManager on each step
 * - TalkBack accessibility semantics (label, value with unit, step actions)
 * - 48dp touch target support
 * - Explanatory hint for disabled/static font constraints
 */
@Composable
fun ExpressiveTypographySliderItem(
    title: String,
    value: Float,
    valueDisplay: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    startHint: @Composable (() -> Unit)? = null,
    endHint: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    disabledHint: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    compact: Boolean = false
) {
    val context = LocalContext.current
    var lastHapticValue by remember { mutableStateOf(value) }

    Surface(
        color = if (compact) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 14.dp)
        ) {
            // Header Row: Title, Subtitle, and Value Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (leadingIcon != null) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = if (compact) FontWeight.Medium else FontWeight.SemiBold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    if (!enabled && disabledHint != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = disabledHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (compact) {
                    Text(
                        text = valueDisplay,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = CircleShape
                ) {
                    Text(
                        text = valueDisplay,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Slider with Endpoint Visual Hints
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (startHint != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        startHint()
                    }
                }

                Slider(
                    value = value,
                    onValueChange = { newVal ->
                        if (newVal != lastHapticValue) {
                            HapticManager.performSwipeTick(context)
                            lastHapticValue = newVal
                        }
                        onValueChange(newVal)
                    },
                    valueRange = valueRange,
                    steps = steps,
                    enabled = enabled,
                    onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = title
                            stateDescription = valueDisplay
                        }
                )

                if (endHint != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        endHint()
                    }
                }
            }
        }
    }
}

/**
 * Modern selector tile with pill status badge and full ModalBottomSheet picker for Floating Scrollbar destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingScrollbarSettingsItem(
    viewModel: CobaltViewModel,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    compact: Boolean = false
) {
    var showSheet by remember { mutableStateOf(false) }
    val enabled by viewModel.floatingScrollbarEnabled.collectAsState()
    val notes by viewModel.floatingScrollbarNotes.collectAsState()
    val home by viewModel.floatingScrollbarHome.collectAsState()
    val mediaHub by viewModel.floatingScrollbarMediaHub.collectAsState()

    val activeCount = listOf(notes, home, mediaHub).count { it }
    val summaryBadge = when {
        !enabled -> "Disabled"
        activeCount == 3 -> "All screens (3)"
        activeCount == 0 -> "Disabled"
        notes && activeCount == 1 -> "Notes only"
        home && activeCount == 1 -> "Home only"
        mediaHub && activeCount == 1 -> "Media Hub only"
        else -> "$activeCount screens"
    }

    if (compact) {
        val summary = if (enabled && activeCount > 0) {
            pluralStringResource(R.plurals.settings_scrollbar_screen_count, activeCount, activeCount)
        } else stringResource(R.string.settings_off)
        ExpressiveSettingsNavigationItem(
            title = stringResource(R.string.settings_scrollbar),
            subtitle = stringResource(R.string.settings_scrollbar_summary),
            trailingBadge = summary,
            leadingIcon = { SettingsIconBadge(R.drawable.ic_settings_scroll) },
            onClick = { showSheet = true },
            modifier = modifier,
            shape = shape,
            compact = true
        )
        if (showSheet) {
            SettingsBottomSheet(
                stringResource(R.string.settings_scrollbar),
                stringResource(R.string.settings_scrollbar_sheet_description),
                { showSheet = false }
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExpressiveSettingsSwitchItem(
                        title = stringResource(R.string.settings_scrollbar_enabled),
                        subtitle = stringResource(R.string.settings_scrollbar_enabled_description),
                        checked = enabled,
                        onCheckedChange = viewModel::setFloatingScrollbarEnabled,
                        compact = true
                    )
                    Column {
                        SettingsGroupLabel(stringResource(R.string.settings_scrollbar_destinations))
                        ExpressiveSettingsGroup {
                            ExpressiveSettingsSwitchItem(
                                title = stringResource(R.string.settings_scrollbar_notes),
                                subtitle = stringResource(R.string.settings_scrollbar_notes_description),
                                checked = notes, enabled = enabled,
                                onCheckedChange = viewModel::setFloatingScrollbarNotes,
                                shape = connectedPillShape(0, 3), compact = true
                            )
                            ExpressiveSettingsSwitchItem(
                                title = stringResource(R.string.settings_home),
                                subtitle = stringResource(R.string.settings_scrollbar_home_description),
                                checked = home, enabled = enabled,
                                onCheckedChange = viewModel::setFloatingScrollbarHome,
                                shape = connectedPillShape(1, 3), compact = true
                            )
                            ExpressiveSettingsSwitchItem(
                                title = stringResource(R.string.settings_scrollbar_media),
                                subtitle = stringResource(R.string.settings_scrollbar_media_description),
                                checked = mediaHub, enabled = enabled,
                                onCheckedChange = viewModel::setFloatingScrollbarMediaHub,
                                shape = connectedPillShape(2, 3), compact = true
                            )
                        }
                    }
                    Text(stringResource(R.string.settings_scrollbar_settings_excluded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    Surface(
        onClick = { showSheet = true },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_list),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Floating Scrollbar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tactile glassmorphic pill for rapid drag-scrubbing and jumps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                            text = summaryBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (enabled && activeCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_custom_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Floating Scrollbar",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Text(
                    text = "Configure fast-scroll pill appearance and destinations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveSettingsSwitchItem(
                        title = "Enable Floating Scrollbar",
                        subtitle = "Master switch for floating drag pill",
                        checked = enabled,
                        onCheckedChange = { viewModel.setFloatingScrollbarEnabled(it) },
                        shape = RoundedCornerShape(20.dp)
                    )

                    AnimatedVisibility(
                        visible = enabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ACTIVE DESTINATIONS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                            )

                            // 1. Notes & Detail Screen
                            ExpressiveSettingsSwitchItem(
                                title = "Notes & Detail Screen",
                                subtitle = "Fast scroll through long notes, articles, and markdown",
                                checked = notes,
                                onCheckedChange = { viewModel.setFloatingScrollbarNotes(it) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_text),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                shape = connectedPillShape(0, 3)
                            )

                            // 2. Home Feed
                            ExpressiveSettingsSwitchItem(
                                title = "Home Feed",
                                subtitle = "Scrub through saved cards and bookmarks",
                                checked = home,
                                onCheckedChange = { viewModel.setFloatingScrollbarHome(it) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_nav_home_outline),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                shape = connectedPillShape(1, 3)
                            )

                            // 3. Media Hub
                            ExpressiveSettingsSwitchItem(
                                title = "Media Hub",
                                subtitle = "Navigate video, audio, image galleries, and links",
                                checked = mediaHub,
                                onCheckedChange = { viewModel.setFloatingScrollbarMediaHub(it) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_movie),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                shape = connectedPillShape(2, 3)
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_custom_info),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Floating scrollbar is never displayed in Settings to keep preferences clean and distraction-free.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

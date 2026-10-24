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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.screens.FolderIcon
import com.example.ui.screens.folderPresetIcons

/**
 * A sleek, modern inline folder creation row matching Material 3 and Cobalt aesthetic.
 * Features an integrated folder icon picker dropdown, clean text field, "+ Add" button,
 * and a compact cancel button.
 */
@Composable
fun InlineNewFolderCreator(
    onAddFolder: (name: String, iconName: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var folderName by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf(folderPresetIcons.first()) }
    var isIconDropdownOpen by remember { mutableStateOf(false) }

    fun submitFolder() {
        val trimmed = folderName.trim()
        if (trimmed.isNotEmpty()) {
            onAddFolder(trimmed, selectedIconName)
            folderName = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Input Box with Integrated Leading Icon Picker (Translucent matching card blur)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leading Icon with Popover Trigger
                    Box {
                        Surface(
                            onClick = { isIconDropdownOpen = !isIconDropdownOpen },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(32.dp)
                                .bounceClick()
                                .testTag("folder_icon_picker_trigger")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                FolderIcon(
                                    iconName = selectedIconName,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Icon Selector Dropdown: 1 single column showing exactly 3 scrollable rows with translucent background
                        DropdownMenu(
                            expanded = isIconDropdownOpen,
                            onDismissRequest = { isIconDropdownOpen = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "Icon",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            // 1 column with exactly 3 visible rows (~136dp height), scrollable
                            Column(
                                modifier = Modifier
                                    .width(54.dp)
                                    .height(136.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                folderPresetIcons.forEach { iconName ->
                                    val isSelected = iconName == selectedIconName
                                    Surface(
                                        onClick = {
                                            selectedIconName = iconName
                                            isIconDropdownOpen = false
                                        },
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .bounceClick()
                                            .testTag("folder_icon_option_$iconName")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            FolderIcon(
                                                iconName = iconName,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Clean Text Field
                    BasicTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            textDirection = TextDirection.Content
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitFolder() }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inline_folder_name_input"),
                        decorationBox = { innerTextField ->
                            if (folderName.isEmpty()) {
                                Text(
                                    text = "Folder name...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }

            // Connected Button Group (M3 Expressive) matching reference prompt: 3dp gap, inner corners 6dp, outer pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Button 1: + Add (outer left rounded pill, inner right 6dp)
                Surface(
                    onClick = { submitFolder() },
                    enabled = folderName.isNotBlank(),
                    shape = RoundedCornerShape(
                        topStart = 22.dp,
                        bottomStart = 22.dp,
                        topEnd = 6.dp,
                        bottomEnd = 6.dp
                    ),
                    color = if (folderName.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .height(44.dp)
                        .bounceClick()
                        .testTag("inline_folder_add_button")
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Button 2: ✕ Cancel (inner left 6dp, outer right rounded pill)
                Surface(
                    onClick = onCancel,
                    shape = RoundedCornerShape(
                        topStart = 6.dp,
                        bottomStart = 6.dp,
                        topEnd = 22.dp,
                        bottomEnd = 22.dp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(44.dp)
                        .bounceClick()
                        .testTag("inline_folder_cancel_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_close),
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

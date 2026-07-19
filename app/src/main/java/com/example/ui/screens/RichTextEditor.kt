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

package com.example.ui.screens
import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.bounceClick
import com.example.ui.components.MarkdownText

@Composable
fun RichTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    minLines: Int = 3,
    maxLines: Int = Int.MAX_VALUE
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    // Sync external changes
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    var isPreviewMode by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Mode selector Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Mode toggle pill
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val editBtnColor = if (!isPreviewMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    val editTextColor = if (!isPreviewMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    val previewBtnColor = if (isPreviewMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    val previewTextColor = if (isPreviewMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(editBtnColor)
                            .clickable { isPreviewMode = false }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_edit),
                            contentDescription = "Edit Mode",
                            tint = editTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Edit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = editTextColor
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewBtnColor)
                            .clickable { isPreviewMode = true }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_eye),
                            contentDescription = "Preview Mode",
                            tint = previewTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Preview",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = previewTextColor
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            )

            if (!isPreviewMode) {
                // Formatting Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val toolbarActions = listOf(
                        MarkdownAction(Icons.Default.FormatBold, "Bold", FormatType.Bold),
                        MarkdownAction(Icons.Default.FormatItalic, "Italic", FormatType.Italic),
                        MarkdownAction(Icons.Default.FormatStrikethrough, "Strikethrough", FormatType.Strikethrough),
                        MarkdownAction(Icons.Default.Title, "Heading", FormatType.Heading),
                        MarkdownAction(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet List", FormatType.BulletList),
                        MarkdownAction(Icons.Default.FormatListNumbered, "Numbered List", FormatType.NumberedList),
                        MarkdownAction(Icons.Default.Code, "Code Inline", FormatType.CodeInline),
                        MarkdownAction(Icons.Default.Terminal, "Code Block", FormatType.CodeBlock),
                        MarkdownAction(Icons.Default.Link, "Link", FormatType.Link)
                    )

                    toolbarActions.forEach { action ->
                        IconButton(
                            onClick = {
                                applyMarkdownFormat(textFieldValue, action.type) { updated ->
                                    textFieldValue = updated
                                    onValueChange(updated.text)
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .bounceClick()
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.description,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                )

                // Input field (borderless standard TextField)
                TextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        onValueChange(it.text)
                    },
                    placeholder = placeholder,
                    minLines = minLines,
                    maxLines = maxLines,
                    visualTransformation = MarkdownVisualTransformation(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                )
            } else {
                // Live Preview Mode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 400.dp)
                        .background(Color.Transparent)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (value.isBlank()) {
                        Text(
                            "Nothing to preview yet. Start typing in Edit mode!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else {
                        MarkdownText(
                            markdown = value,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private data class MarkdownAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val type: FormatType
)

private enum class FormatType {
    Bold, Italic, Strikethrough, Heading, BulletList, NumberedList, CodeInline, CodeBlock, Link
}

private fun applyMarkdownFormat(
    value: TextFieldValue,
    formatType: FormatType,
    onValueChange: (TextFieldValue) -> Unit
) {
    val text = value.text
    val selection = value.selection
    val selectedText = text.substring(selection.start, selection.end)

    val (newText, newSelection) = when (formatType) {
        FormatType.Bold -> {
            val wrapper = "**"
            val updated = text.substring(0, selection.start) + wrapper + selectedText + wrapper + text.substring(selection.end)
            val start = selection.start + wrapper.length
            val end = start + selectedText.length
            updated to TextRange(start, end)
        }
        FormatType.Italic -> {
            val wrapper = "_"
            val updated = text.substring(0, selection.start) + wrapper + selectedText + wrapper + text.substring(selection.end)
            val start = selection.start + wrapper.length
            val end = start + selectedText.length
            updated to TextRange(start, end)
        }
        FormatType.Strikethrough -> {
            val wrapper = "~~"
            val updated = text.substring(0, selection.start) + wrapper + selectedText + wrapper + text.substring(selection.end)
            val start = selection.start + wrapper.length
            val end = start + selectedText.length
            updated to TextRange(start, end)
        }
        FormatType.Heading -> {
            val isLineStart = selection.start == 0 || text.getOrNull(selection.start - 1) == '\n'
            val prefix = if (isLineStart) "### " else "\n### "
            val updated = text.substring(0, selection.start) + prefix + selectedText + text.substring(selection.end)
            val pos = selection.start + prefix.length + selectedText.length
            updated to TextRange(pos, pos)
        }
        FormatType.BulletList -> {
            val isLineStart = selection.start == 0 || text.getOrNull(selection.start - 1) == '\n'
            val prefix = if (isLineStart) "- " else "\n- "
            val updated = text.substring(0, selection.start) + prefix + selectedText + text.substring(selection.end)
            val pos = selection.start + prefix.length + selectedText.length
            updated to TextRange(pos, pos)
        }
        FormatType.NumberedList -> {
            val isLineStart = selection.start == 0 || text.getOrNull(selection.start - 1) == '\n'
            val prefix = if (isLineStart) "1. " else "\n1. "
            val updated = text.substring(0, selection.start) + prefix + selectedText + text.substring(selection.end)
            val pos = selection.start + prefix.length + selectedText.length
            updated to TextRange(pos, pos)
        }
        FormatType.CodeInline -> {
            val wrapper = "`"
            val updated = text.substring(0, selection.start) + wrapper + selectedText + wrapper + text.substring(selection.end)
            val start = selection.start + wrapper.length
            val end = start + selectedText.length
            updated to TextRange(start, end)
        }
        FormatType.CodeBlock -> {
            val wrapperBefore = "```\n"
            val wrapperAfter = "\n```"
            val updated = text.substring(0, selection.start) + wrapperBefore + selectedText + wrapperAfter + text.substring(selection.end)
            val start = selection.start + wrapperBefore.length
            val end = start + selectedText.length
            updated to TextRange(start, end)
        }
        FormatType.Link -> {
            val linkText = if (selectedText.isEmpty()) "link text" else selectedText
            val updated = text.substring(0, selection.start) + "[" + linkText + "](https://example.com)" + text.substring(selection.end)
            val start = selection.start + 1
            val end = start + linkText.length
            updated to TextRange(start, end)
        }
    }
    onValueChange(TextFieldValue(newText, newSelection))
}

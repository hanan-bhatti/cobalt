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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
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
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Mode toggle segmented pill
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val editBtnColor = if (!isPreviewMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    val editTextColor = if (!isPreviewMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    val previewBtnColor = if (isPreviewMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    val previewTextColor = if (isPreviewMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(editBtnColor)
                            .clickable { isPreviewMode = false }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .bounceClick()
                            .testTag("markdown_edit_tab"),
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
                            .clip(RoundedCornerShape(9.dp))
                            .background(previewBtnColor)
                            .clickable { isPreviewMode = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .bounceClick()
                            .testTag("markdown_preview_tab"),
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

                Text(
                    text = if (isPreviewMode) "Markdown Preview" else "Supports English & اردو",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            )

            if (!isPreviewMode) {
                // Formatting Toolbar - Bear / Obsidian inspired ergonomic mobile accessory bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val toolbarActions = listOf(
                        MarkdownToolbarAction("H1", "Header 1", FormatType.Heading1),
                        MarkdownToolbarAction("H2", "Header 2", FormatType.Heading2),
                        MarkdownToolbarAction("B", "Bold", FormatType.Bold, isBoldLabel = true),
                        MarkdownToolbarAction("I", "Italic", FormatType.Italic, isItalicLabel = true),
                        MarkdownToolbarAction("S", "Strikethrough", FormatType.Strikethrough),
                        MarkdownToolbarAction("”", "Quote", FormatType.Quote),
                        MarkdownToolbarAction("•", "Bullet List", FormatType.BulletList),
                        MarkdownToolbarAction("1.", "Numbered List", FormatType.NumberedList),
                        MarkdownToolbarAction("☑", "Checklist", FormatType.Checklist),
                        MarkdownToolbarAction("<>", "Code Inline", FormatType.CodeInline),
                        MarkdownToolbarAction("```", "Code Block", FormatType.CodeBlock),
                        MarkdownToolbarAction("🔗", "Link", FormatType.Link)
                    )

                    toolbarActions.forEach { action ->
                        Surface(
                            onClick = {
                                applyMarkdownFormat(textFieldValue, action.type) { updated ->
                                    textFieldValue = updated
                                    onValueChange(updated.text)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .height(34.dp)
                                .defaultMinSize(minWidth = 36.dp)
                                .bounceClick()
                                .testTag("markdown_action_${action.label}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = action.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (action.isBoldLabel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontStyle = if (action.isItalicLabel) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                )

                // Input field with natural Bidirectional / Urdu + English support
                TextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        onValueChange(it.text)
                    },
                    placeholder = placeholder,
                    minLines = minLines,
                    maxLines = maxLines,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.Content,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
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
                        .padding(horizontal = 4.dp, vertical = 4.dp)
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
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = TextDirection.Content
                            ),
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

private data class MarkdownToolbarAction(
    val label: String,
    val description: String,
    val type: FormatType,
    val isBoldLabel: Boolean = false,
    val isItalicLabel: Boolean = false
)

private enum class FormatType {
    Bold, Italic, Strikethrough, Heading1, Heading2, Quote, BulletList, NumberedList, Checklist, CodeInline, CodeBlock, Link
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
        FormatType.Heading1 -> {
            val isLineStart = selection.start == 0 || text.getOrNull(selection.start - 1) == '\n'
            val prefix = if (isLineStart) "# " else "\n# "
            val updated = text.substring(0, selection.start) + prefix + selectedText + text.substring(selection.end)
            val pos = selection.start + prefix.length + selectedText.length
            updated to TextRange(pos, pos)
        }
        FormatType.Heading2 -> {
            val isLineStart = selection.start == 0 || text.getOrNull(selection.start - 1) == '\n'
            val prefix = if (isLineStart) "## " else "\n## "
            val updated = text.substring(0, selection.start) + prefix + selectedText + text.substring(selection.end)
            val pos = selection.start + prefix.length + selectedText.length
            updated to TextRange(pos, pos)
        }
        FormatType.Quote -> {
            val isLineStart = selection.start == 0 || text.getOrNull(selection.start - 1) == '\n'
            val prefix = if (isLineStart) "> " else "\n> "
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
        FormatType.Checklist -> {
            val isLineStart = selection.start == 0 || text.getOrNull(selection.start - 1) == '\n'
            val prefix = if (isLineStart) "- [ ] " else "\n- [ ] "
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

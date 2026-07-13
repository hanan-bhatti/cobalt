package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

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

    Column(modifier = modifier) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = {
                val text = textFieldValue.text
                val selection = textFieldValue.selection
                val newText = text.substring(0, selection.start) + "**" + text.substring(selection.start, selection.end) + "**" + text.substring(selection.end)
                val newSelection = TextRange(selection.end + 4)
                textFieldValue = TextFieldValue(newText, newSelection)
                onValueChange(newText)
            }) {
                Icon(Icons.Filled.FormatBold, contentDescription = "Bold", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = {
                val text = textFieldValue.text
                val selection = textFieldValue.selection
                val newText = text.substring(0, selection.start) + "*" + text.substring(selection.start, selection.end) + "*" + text.substring(selection.end)
                val newSelection = TextRange(selection.end + 2)
                textFieldValue = TextFieldValue(newText, newSelection)
                onValueChange(newText)
            }) {
                Icon(Icons.Filled.FormatItalic, contentDescription = "Italic", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = {
                val text = textFieldValue.text
                val selection = textFieldValue.selection
                val prefix = if (selection.start == 0 || text.getOrNull(selection.start - 1) == '\n') "- " else "\n- "
                val newText = text.substring(0, selection.start) + prefix + text.substring(selection.start, selection.end) + text.substring(selection.end)
                val newSelection = TextRange(selection.start + prefix.length)
                textFieldValue = TextFieldValue(newText, newSelection)
                onValueChange(newText)
            }) {
                Icon(Icons.Filled.FormatListBulleted, contentDescription = "Bullet List", tint = MaterialTheme.colorScheme.primary)
            }
        }

        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                onValueChange(it.text)
            },
            placeholder = placeholder,
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = MarkdownVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

/** The same rounded search field used by Cobalt's folder-move picker. */
@Composable
fun FolderSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("folder_search_input"),
        placeholder = { Text("Search folders...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_custom_search), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = if (value.isNotEmpty()) ({ IconButton(onClick = { onValueChange("") }) { Icon(painterResource(R.drawable.ic_custom_close), "Clear search", Modifier.size(14.dp)) } }) else null,
        singleLine = true, shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        textStyle = MaterialTheme.typography.bodyMedium)
}

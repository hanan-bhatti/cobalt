package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.SettingsBottomSheet

private data class ExampleFolder(val name: String, val iconName: String, val colorHex: String)

/** Illustrative folders only; the preview never reads, selects or moves saved items. */
@Composable
internal fun FolderOrderPreview(suggestionsEnabled: Boolean, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val work = stringResource(R.string.settings_example_folder_work)
    val folders = listOf(
        ExampleFolder(work, "work", folderPresetColors.first { it.second == "Royal Indigo" }.first),
        ExampleFolder(stringResource(R.string.settings_example_folder_ideas), "idea", folderPresetColors.first { it.second == "Soft Gold" }.first),
        ExampleFolder(stringResource(R.string.settings_example_folder_reading), "book", folderPresetColors.first { it.second == "Emerald Sage" }.first)
    ).sortedBy { it.name.lowercase() }
    val ordered = if (suggestionsEnabled) folders.filter { it.name == work } + folders.filter { it.name != work } else folders
    val description = stringResource(
        if (suggestionsEnabled) R.string.settings_folder_preview_suggested_description
        else R.string.settings_folder_preview_alphabetical_description,
        ordered[0].name, ordered[1].name, ordered[2].name
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().testTag("notes_folder_order_preview")
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.settings_folder_picker),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.settings_example),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ordered.forEachIndexed { index, folder ->
                val suggested = suggestionsEnabled && index == 0
                val folderColor = parseHexColor(folder.colorHex, isDark = isDark)
                Surface(
                    color = if (suggested) folderColor.copy(alpha = 0.12f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    border = if (suggested) BorderStroke(1.dp, folderColor.copy(alpha = 0.65f)) else null
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(folderColor.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            FolderIcon(folder.iconName, tint = folderColor, modifier = Modifier.size(16.dp))
                        }
                        Text(folder.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (suggested) {
                            Surface(color = folderColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    stringResource(R.string.settings_folder_suggested),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FolderSuggestionsHelpSheet(onDismiss: () -> Unit) {
    SettingsBottomSheet(
        title = stringResource(R.string.settings_folder_suggestions_how),
        description = stringResource(R.string.settings_folder_suggestions_sheet_description),
        onDismiss = onDismiss
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            FolderSuggestionExplanation(
                R.drawable.ic_custom_folder,
                stringResource(R.string.settings_folder_usage_title),
                stringResource(R.string.settings_folder_usage_description)
            )
            FolderSuggestionExplanation(
                R.drawable.ic_custom_history,
                stringResource(R.string.settings_folder_recency_title),
                stringResource(R.string.settings_folder_recency_description)
            )
            FolderSuggestionExplanation(
                R.drawable.ic_custom_text,
                stringResource(R.string.settings_folder_content_title),
                stringResource(R.string.settings_folder_content_description)
            )
            FolderSuggestionExplanation(
                R.drawable.ic_custom_pin,
                stringResource(R.string.settings_folder_pinned_title),
                stringResource(R.string.settings_folder_pinned_description)
            )
        }
    }
}

@Composable
private fun FolderSuggestionExplanation(icon: Int, title: String, description: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(8.dp).size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

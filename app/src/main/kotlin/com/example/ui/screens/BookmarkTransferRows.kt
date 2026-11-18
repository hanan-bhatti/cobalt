package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.bookmarks.BookmarkHtml
import com.example.data.local.CustomFolderEntity
import com.example.ui.components.FolderSearchField
import com.example.ui.components.InlineNewFolderCreator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** No website fetches: this row previews only the data present in the selected HTML file. */
@Composable
internal fun BookmarkPreviewRow(entry: BookmarkHtml.Entry) {
    var expanded by rememberSaveable(entry.url, entry.title, entry.path) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val host = remember(entry.url) { runCatching { java.net.URI(entry.url).host }.getOrNull() ?: entry.url }
    Surface(
        onClick = { expanded = !expanded }, shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Details expanded" else "Details collapsed" }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(14.dp), color = colors.primaryContainer) {
                    Icon(painterResource(R.drawable.ic_custom_link), null, Modifier.padding(10.dp).size(20.dp), tint = colors.onPrimaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (entry.title == entry.url) host else entry.title, style = MaterialTheme.typography.titleSmall,
                        maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                    if (!expanded) Text(entry.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (entry.path.isNotEmpty() && !expanded) Text(entry.path.last(), style = MaterialTheme.typography.labelSmall,
                        color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(if (expanded) R.drawable.ic_custom_chevron_up else R.drawable.ic_custom_chevron_down),
                    null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            }
            if (expanded) {
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
                SelectionContainer { Text(entry.url, style = MaterialTheme.typography.bodyMedium, color = colors.primary) }
                entry.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(painterResource(R.drawable.ic_custom_folder_open), null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    Text(entry.path.joinToString(" / ").ifBlank { "Outside folders" }, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Folder identity uses the same glyph and theme-adjusted accent as the main folder screens. */
@Composable
internal fun BookmarkFolderRow(
    folder: CustomFolderEntity,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    selectionControl: Boolean = true,
    multiple: Boolean = false,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val accent = parseHexColor(folder.colorHex, colors.primary, colors.background.luminance() < 0.5f)
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(24.dp),
        color = if (selected) colors.primaryContainer.copy(alpha = 0.35f) else colors.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth().semantics {
            if (selectionControl) {
                role = if (multiple) Role.Checkbox else Role.RadioButton
                if (multiple) toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                else this.selected = selected
            }
        }
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = accent.copy(alpha = 0.12f)) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    FolderIcon(folder.iconName, Modifier.size(24.dp), tint = accent)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            if (!selectionControl) Icon(painterResource(R.drawable.ic_custom_chevron_right), null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            else if (multiple) Checkbox(selected, onCheckedChange = null)
            else RadioButton(selected, onClick = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkDestinationPicker(
    folders: List<CustomFolderEntity>,
    selected: String,
    onSelect: (CustomFolderEntity) -> Unit,
    onCreate: suspend (String, String) -> CustomFolderEntity,
    onDismiss: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val currentSaving by rememberUpdatedState(saving)
    val filtered = remember(folders, search) {
        folders.filter { it.name.contains(search.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase() }
    }
    val sheet = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded), confirmValueChange = { !currentSaving || it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp * 0.85f)
            .imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choose a folder", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onDismiss, enabled = !saving) { Icon(painterResource(R.drawable.ic_custom_close), "Close") }
            }
            Text("Choose where to add these links, or create a folder below.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FolderSearchField(search, { search = it })
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)) {
                items(filtered, key = { it.name }) { folder ->
                    BookmarkFolderRow(folder, folder.name == selected.trim(), { onSelect(folder) }, enabled = !saving)
                }
                if (filtered.isEmpty()) item {
                    Text(if (folders.isEmpty()) "No folders yet. Create your first folder below." else "No matching folders. Try another name or create a folder.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            if (saving) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Creating folder…", style = MaterialTheme.typography.bodySmall)
            } else if (creating) InlineNewFolderCreator(
                onAddFolder = { name, icon ->
                    saving = true
                    error = null
                    scope.launch {
                        try { onSelect(onCreate(name, icon)) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = if (e is IllegalArgumentException) e.message else "Couldn't create this folder. Check available storage and try again." }
                        finally { saving = false }
                    }
                },
                onCancel = { creating = false; error = null }, existingFolders = folders.map { it.name }
            ) else TextButton(onClick = { creating = true; error = null }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_custom_add_folder), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("New folder")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

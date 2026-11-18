package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.recommendation.ScreeningFeedback
import com.example.widget.ScreeningSource

@Composable
fun ScreeningSourceSelectorItem(selected: ScreeningSource, onSelected: (ScreeningSource) -> Unit) {
    ExpressiveSettingsSelectorItem(
        title = "Recommend",
        options = ScreeningSource.entries.associate { it.name to it.label },
        selectedKey = selected.name,
        onSelectionChanged = { onSelected(ScreeningSource.valueOf(it)) },
        description = "Choose the titles this card can suggest",
        sheetContent = { dismiss -> ScreeningSourceSheet(selected, { onSelected(it); dismiss() }, dismiss) }
    )
}

/** Appearance's Floating Scrollbar sheet geometry, with exclusive choices and no category icons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreeningSourceSheet(selected: ScreeningSource, onSelected: (ScreeningSource) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text("Recommend", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text("Choose the titles this card can suggest.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceRow(ScreeningSource.ALL, selected == ScreeningSource.ALL, { onSelected(ScreeningSource.ALL) }, RoundedCornerShape(20.dp))
                Text("TITLE TYPES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sources = ScreeningSource.entries.filter { it != ScreeningSource.ALL }
                    sources.forEachIndexed { index, source ->
                        SourceRow(source, selected == source, { onSelected(source) }, connectedPillShape(index, sources.size))
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(R.drawable.ic_custom_info), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("One choice per card. Only your saved titles are included.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: ScreeningSource, selected: Boolean, onSelected: () -> Unit, shape: Shape) {
    Surface(shape = shape, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().selectable(selected, onClick = onSelected, role = Role.RadioButton).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(source.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(source.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreeningFeedbackSheet(title: String, busy: Boolean, onAnswer: (ScreeningFeedback) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text("What do you think?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text("About $title", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
            Spacer(Modifier.height(16.dp))
            FeedbackRow("Good pick", "Show me more picks like this.", R.drawable.ic_custom_helpful, !busy) { onAnswer(ScreeningFeedback.USEFUL) }
            FeedbackRow("Not interested", "Hide this title from future picks.", R.drawable.ic_custom_unhelpful, !busy) { onAnswer(ScreeningFeedback.NOT_INTERESTED) }
            FeedbackRow("Maybe later", "Skip this title for 7 days.", R.drawable.ic_custom_history, !busy) { onAnswer(ScreeningFeedback.NOT_NOW) }
        }
    }
}

@Composable
private fun FeedbackRow(title: String, subtitle: String, icon: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

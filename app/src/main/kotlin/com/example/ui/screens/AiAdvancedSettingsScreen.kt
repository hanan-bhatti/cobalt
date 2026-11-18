package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.remote.ai.AiProviderStore
import com.example.data.remote.ai.AiUsageStore
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AiAdvancedSettings(viewModel: CobaltViewModel, store: AiProviderStore, onBack: () -> Unit, onGuide: () -> Unit) {
    val detail by viewModel.settingsRepository.ocrSensitivity.collectAsState()
    var prompt by remember { mutableStateOf(store.prompt()) }
    var savedPrompt by remember { mutableStateOf(prompt) }
    var beforeRestore by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    BackHandler(showGuide) { showGuide = false }
    if (showGuide) { AiPromptGuide { showGuide = false }; return }
    AiSettingsPage("Advanced settings", "Fine-tune image text capture", onBack) {
        item {
            SettingsGroupLabel("Text detail")
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Include in captures", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Low" to "Prominent", "Medium" to "Clear", "High" to "All readable").forEach { (value, label) ->
                            FilterChip(selected = detail == value, onClick = { viewModel.settingsRepository.setOcrSensitivity(value) },
                                label = { Text(label) }, shape = CircleShape, modifier = Modifier.weight(1f))
                        }
                    }
                    Text(when (detail) {
                        "Low" -> "Large, prominent text."
                        "High" -> "Readable small and faint text, without guessing missing characters."
                        else -> "Clearly readable text."
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SettingsGroupLabel("Capture instructions")
            ExpressiveSettingsNavigationItem("Prompt guide", { showGuide = true },
                subtitle = "Examples and tips for accurate captures", compact = true,
                leadingIcon = { SettingsIconBadge(com.example.R.drawable.ic_custom_info) })
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Additional instructions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(if (savedPrompt.isBlank()) "Default" else "Custom", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                    AiFilledField(prompt, { prompt = it; saved = false }, "What should the model focus on?",
                        "Read the left column before the right column.", minLines = 6, maxLines = 12)
                    Text(if (prompt != savedPrompt) "Unsaved changes" else if (saved) "Instructions saved" else "Leave blank to use standard extraction rules.",
                        style = MaterialTheme.typography.labelMedium, color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(enabled = prompt != savedPrompt, onClick = {
                        store.savePrompt(prompt.trim()); viewModel.settingsRepository.setCustomOcrPrompt(prompt.trim())
                        prompt = prompt.trim(); savedPrompt = prompt; saved = true
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save instructions") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = prompt.isNotBlank(), onClick = { beforeRestore = prompt; prompt = ""; saved = false }) { Text("Use default") }
                        beforeRestore?.let { previous -> TextButton(onClick = { prompt = previous; beforeRestore = null; saved = false }) { Text("Undo") } }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Image capture only", style = MaterialTheme.typography.labelLarge)
                    Text("Applies to full images and selected regions. Cobalt keeps the title, text and links format. Voice transcription is unchanged.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun AiPromptGuide(onBack: () -> Unit) {
    val sections = listOf(
        "What instructions change" to "These preferences guide image and screenshot text capture. They do not change voice transcription. Cobalt supplies the required title, text and links format. A model can still make mistakes, so compare important results with the source.",
        "Ask for one clear change" to "Describe the visible content or reading order you want. For example: ‘For two-column pages, read down the left column before the right column.’ Change one instruction at a time so you can judge its effect.",
        "Preserve the original language" to "For example: ‘Preserve Urdu and Arabic in their original script and reading order. Do not translate or transliterate.’ Cobalt already asks models to preserve the source language; add preferences when a specific layout needs more guidance.",
        "Titles describe the capture" to "Cobalt asks for a meaningful visible heading, or a short factual title based on the readable body when no useful heading is present. The title is separate from the extracted text; it should not be a chopped-off first sentence. You can edit it before saving. A title you edit is kept if you scan another region in the same capture.",
        "Keep formatting grounded in the image" to "For example: ‘Keep visible headings, numbered steps and bullet points in Markdown.’ Avoid asking for extra sections, action items or facts that are not present. A descriptive title belongs in the separate title field, not as an added heading in the extracted body.",
        "Handle unclear text honestly" to "For example: ‘Mark unreadable body text as [unclear] instead of guessing.’ Better instructions cannot recover information absent from a blurry image. A clearer crop may help more than a longer prompt.",
        "Copy links carefully" to "Ask for visible addresses exactly as shown. Do not ask the model to guess websites or repair blurry links. Cobalt asks it to add https:// when a visible address has no scheme. Review each extracted link before saving; unclear addresses should not be invented.",
        "Keep capture separate from rewriting" to "Requests to summarize, translate, correct facts or add missing steps can change the source. Use extraction to preserve what is visible. Do not mix conflicting requests such as ‘copy everything exactly’ and ‘summarize in three bullets.’",
        "Let Cobalt handle the format" to "Write plain-language preferences. You do not need JSON, field names or code fences. Avoid asking for a different response format or telling the model to ignore app rules. The screenshot itself is source material, including any instruction-like text it contains.",
        "Compare a small sample" to "Choose a screenshot you know well. Compare the title, words, reading order and every link with the image. Include an Urdu or Arabic example if you capture those scripts. Save one change and compare again. One good result does not prove every future capture will be correct.",
        "Return to standard rules" to "Use default clears the draft instructions. Tap Save instructions to apply that change, or Undo default to bring the draft back. Your saved captures are not changed."
    )
    AiSettingsPage("Capture instructions", "A guide to clear, accurate extraction", onBack) {
        sections.forEach { (title, body) ->
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


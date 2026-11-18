package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.ai.AiUsageStore
import com.example.ui.components.SettingsGroupLabel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class UsageDay(val time: Long, val label: String, val requests: Int, val tokens: Long, val unknown: Int)

@Composable
internal fun AiUsageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val records = remember { AiUsageStore(context).records() }
    val numbers = remember { NumberFormat.getIntegerInstance() }
    var tokenMode by remember { mutableStateOf(true) }
    var selectedDay by remember { mutableIntStateOf(6) }
    val days = remember(records) {
        val label = SimpleDateFormat("EEE", Locale.getDefault())
        (6 downTo 0).map { ago ->
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -ago)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val bucket = records.filter { it.optLong("time") in start.timeInMillis until end.timeInMillis }
            UsageDay(start.timeInMillis, label.format(start.time), bucket.size,
                bucket.filterNot { it.isNull("tokens") }.sumOf { it.optLong("tokens") }, bucket.count { it.isNull("tokens") })
        }
    }
    val cutoff = days.first().time
    val week = records.filter { it.optLong("time") >= cutoff }
    val reported = week.filterNot { it.isNull("tokens") }
    val tokens = reported.sumOf { it.optLong("tokens") }
    val unknown = week.size - reported.size
    val taskLabels = mapOf("IMAGE" to "Image text extraction", "AUDIO" to "Audio transcription", "TEXT" to "Voice text formatting")
    AiSettingsPage("AI usage", "Last 7 days · on this device", onBack) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UsageStat("Reported tokens", numbers.format(tokens), Modifier.weight(1f))
                UsageStat("Responses", numbers.format(week.size), Modifier.weight(1f))
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(tokenMode, { tokenMode = true }, label = { Text("Tokens") }, shape = CircleShape)
                        FilterChip(!tokenMode, { tokenMode = false }, label = { Text("Responses") }, shape = CircleShape)
                    }
                    val chosen = days[selectedDay]
                    val value = if (tokenMode) chosen.tokens else chosen.requests.toLong()
                    Text(numbers.format(value), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text(SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date(chosen.time)) + if (tokenMode) " · reported tokens" else " · recorded responses",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val maxValue = days.maxOf { if (tokenMode) it.tokens else it.requests.toLong() }.coerceAtLeast(1L)
                    Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        days.forEachIndexed { index, day ->
                            val amount = if (tokenMode) day.tokens else day.requests.toLong()
                            Column(Modifier.weight(1f).fillMaxHeight().clickable { selectedDay = index }
                                .semantics { contentDescription = "${day.label}, ${numbers.format(amount)} ${if (tokenMode) "reported tokens" else "responses"}, ${day.unknown} responses without token counts" },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                    if (amount > 0) Box(Modifier.fillMaxWidth().fillMaxHeight((amount.toFloat() / maxValue).coerceIn(0.025f, 1f))
                                        .background(if (index == selectedDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)))
                                    else Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                }
                                Text(day.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                    Text(if (week.isEmpty()) "No activity yet. Captures and voice notes will appear here." else
                        if (unknown > 0) "$unknown responses have no reported token count and are excluded from the token chart." else "Token counts come from provider responses. Tap a day to inspect it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SettingsGroupLabel("By task · responses")
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    taskLabels.forEach { (key, label) ->
                        UsageBreakdown(label, week.count { it.optString("task") == key }.toLong(), week.size.toLong(), when (key) {
                            "IMAGE" -> "AI responses that extract a title, text and links from an image or screenshot."
                            "AUDIO" -> "AI responses that transcribe speech from an audio recording."
                            else -> "AI responses that organize already-transcribed speech into Markdown. Ordinary note editing is not counted here."
                        })
                    }
                }
            }
        }
        if (week.isNotEmpty()) item {
            SettingsGroupLabel("By provider · reported tokens")
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    week.groupBy { it.optString("provider") }.forEach { (provider, entries) ->
                        val known = entries.filterNot { it.isNull("tokens") }
                        if (known.isEmpty()) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(provider, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text("Unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else UsageBreakdown(provider, known.sumOf { it.optLong("tokens") }, tokens)
                    }
                }
            }
        }
        item {
            Text("Shows retained activity from up to 200 recent provider responses. Missing counts are never estimated. A response can use tokens even when capture validation fails. This is not a billing total.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        }
    }
}

@Composable
private fun UsageStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UsageBreakdown(label: String, value: Long, total: Long, explanation: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (explanation != null) AiInfoLabel(label, explanation, Modifier.weight(1f))
            else Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(NumberFormat.getIntegerInstance().format(value), style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
            if (value > 0 && total > 0) Box(Modifier.fillMaxWidth((value.toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

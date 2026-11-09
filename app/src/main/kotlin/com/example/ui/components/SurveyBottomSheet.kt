package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.testTag as sliderTestTag
import com.example.util.surveyReactionLabel
import com.example.util.surveyReactionEmoji
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.screens.collectEnvironmentReport
import com.example.ui.viewmodel.CobaltViewModel
import com.example.util.FeedbackSubmissionManager
import com.example.util.SurveyResponse
import org.json.JSONObject

private data class SurveyChoice(val answer: String, val label: String, @DrawableRes val icon: Int)
private val surveyAreas = listOf(
    SurveyChoice("Notes / Markdown", "Notes", R.drawable.ic_custom_text),
    SurveyChoice("Links", "Links", R.drawable.ic_custom_link),
    SurveyChoice("Images / video / audio", "Images & media", R.drawable.ic_custom_image),
    SurveyChoice("Folders / search", "Folders & search", R.drawable.ic_custom_folder),
    SurveyChoice("Media tracking", "Movies & TV", R.drawable.ic_custom_movie),
    SurveyChoice("Sharing", "Sharing", R.drawable.ic_custom_share),
    SurveyChoice("Saved history / storage", "Saved history", R.drawable.ic_custom_history),
    SurveyChoice("Dock / OCR", "Dock & OCR", R.drawable.ic_custom_sidedock),
    SurveyChoice("Widgets", "Widgets", R.drawable.ic_settings_widgets),
    SurveyChoice("Appearance / fonts", "Appearance", R.drawable.ic_custom_settings),
    SurveyChoice("Reminders", "Reminders", R.drawable.ic_custom_reminder)
)
private val surveyImprovements = listOf(
    SurveyChoice("Editing / saving", "Editing & saving", R.drawable.ic_custom_text),
    SurveyChoice("Finding / organizing items", "Finding things", R.drawable.ic_custom_search),
    SurveyChoice("Media tracking", "Movies & TV", R.drawable.ic_custom_movie),
    SurveyChoice("Sharing", "Sharing", R.drawable.ic_custom_share),
    SurveyChoice("Storage / migration", "Storage & updates", R.drawable.ic_custom_storage),
    SurveyChoice("Appearance / readability", "Look & readability", R.drawable.ic_custom_settings),
    SurveyChoice("Dock / OCR", "Dock & OCR", R.drawable.ic_custom_sidedock),
    SurveyChoice("Speed / stability", "Speed & stability", R.drawable.ic_custom_sparkle)
)
private val surveyUpdateChoices = listOf(
    SurveyChoice("No problems noticed", "No problems noticed", R.drawable.ic_custom_check),
    SurveyChoice("Something missing or changed unexpectedly", "Something changed", R.drawable.ic_custom_feedback),
    SurveyChoice("Update / migration trouble", "Migration trouble", R.drawable.ic_custom_sync),
    SurveyChoice("Not sure", "Not sure", R.drawable.ic_custom_help),
    SurveyChoice("I haven’t upgraded", "I haven’t upgraded", R.drawable.ic_custom_history)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun SurveyBottomSheet(
    viewModel: CobaltViewModel,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    ),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val environment = remember(context) { collectEnvironmentReport(context) }
    var responseJson by rememberSaveable { mutableStateOf(SurveyResponse().toJson().toString()) }
    val response = remember(responseJson) { SurveyResponse.fromJson(JSONObject(responseJson)) }
    var status by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf(false) }
    val emailBody = remember(response, environment) {
        FeedbackSubmissionManager.formatSurveyEmail(response, environment.deviceModel,
            environment.osVersion, environment.appVersion)
    }
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp, modifier = modifier) {
        SurveySheetContent(response, onResponseChange = {
            responseJson = it.toJson().toString()
            status = null
        }, version = environment.appVersion, emailBody = emailBody,
            onDismissRequest = onDismissRequest, status = status, emailError = emailError,
            onSendEmail = {
                val opened = FeedbackSubmissionManager.launchSurveyEmail(context, environment.appVersion, emailBody)
                emailError = !opened
                status = if (opened) "Your email app is ready. Tap Send there to finish."
                    else "Couldn’t open an email app. Your answers are still here; you can copy the response below."
            }, onCopy = { FeedbackSubmissionManager.copyToClipboard(context, "Cobalt survey", emailBody) })
    }
}

/** The same content is rendered in UI tests; the email action is injected to avoid sending test feedback. */
@Composable
internal fun SurveySheetContent(
    response: SurveyResponse,
    onResponseChange: (SurveyResponse) -> Unit,
    version: String,
    emailBody: String,
    onDismissRequest: () -> Unit,
    onSendEmail: () -> Unit,
    onCopy: () -> Boolean,
    modifier: Modifier = Modifier,
    initialStep: Int = 1,
    status: String? = null,
    emailError: Boolean = false
) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    LaunchedEffect(step) { list.scrollToItem(0) }
    Column(modifier.fillMaxWidth().heightIn(max = height)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(
                MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_custom_survey), null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Make Cobalt better", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("A few taps. Your honest take.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismissRequest) {
                Icon(painterResource(R.drawable.ic_custom_close), "Close survey", Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SurveyStepPill("1", "Your experience", active = step == 1, done = step > 1, modifier = Modifier.weight(1f))
            SurveyStepPill("2", "Your ideas", active = step == 2, done = false, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.weight(1f).testTag("survey_questions"), state = list,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step == 1) {
                item(key = "experience") {
                    SurveyQuestionCard("How’s Cobalt been for you?", "Slide from 1 to 10, or leave it blank.", R.drawable.ic_custom_heart) {
                        response.reactionScore?.let { score ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(surveyReactionEmoji(score), style = MaterialTheme.typography.headlineMedium)
                                Text(surveyReactionLabel(score), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        OptionalSurveySlider(response.reactionScore, "Very poorly", "Very well", "survey_reaction_slider") { score ->
                            onResponseChange(response.copy(reactionScore = score, overall = score?.let(::surveyReactionLabel)))
                        }
                    }
                }
                item(key = "used") {
                    SurveyQuestionCard("What have you been using?", "Pick as many as you like.", R.drawable.ic_custom_checklist) {
                        SurveyChoices(surveyAreas, selected = { it in response.areasUsed }) {
                            onResponseChange(response.copy(areasUsed = if (it in response.areasUsed) response.areasUsed - it
                                else response.areasUsed + it))
                        }
                    }
                }
                item(key = "update") {
                    SurveyQuestionCard("How did the update go?", "New here? That’s an option too.", R.drawable.ic_custom_sync) {
                        SurveyChoices(surveyUpdateChoices, selected = { response.updateExperience == it }) {
                            onResponseChange(response.copy(updateExperience = it.takeUnless { it == response.updateExperience }))
                        }
                    }
                }
            } else {
                item(key = "improve") {
                    SurveyQuestionCard("What should we polish next?", "Choose one existing area.", R.drawable.ic_custom_sparkle) {
                        SurveyChoices(surveyImprovements, selected = { response.improvementArea == it }) {
                            onResponseChange(response.copy(improvementArea = it.takeUnless { it == response.improvementArea }))
                        }
                    }
                }
                item(key = "details") {
                    SurveyQuestionCard("Anything we should know?", "What happened, and what did you expect?", R.drawable.ic_custom_feedback) {
                        OutlinedTextField(response.details, onValueChange = { onResponseChange(response.copy(details = it)) },
                            placeholder = { Text("A small annoyance, a bug, or an idea…", style = MaterialTheme.typography.bodyMedium) },
                            minLines = 3, maxLines = 5, shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth().testTag("survey_details_input"))
                    }
                }
                item(key = "recommendation") {
                    SurveyQuestionCard("Would you recommend Cobalt?", "Optional · leave it blank if it’s too early.", R.drawable.ic_custom_star) {
                        OptionalSurveySlider(response.recommendation, "Not likely", "Very likely", "survey_recommendation_slider") { score ->
                            onResponseChange(response.copy(recommendation = score))
                        }
                    }
                }
                item(key = "email") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_custom_mail), null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        TextButton(onClick = { preview = !preview }) { Text(if (preview) "Hide email preview" else "Preview email") }
                        Spacer(Modifier.weight(1f))
                        Text("v$version", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Your email app handles sending. The response includes app, device and Android versions.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (preview || emailError) Surface(shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(top = 12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(emailBody, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { copied = onCopy() }) {
                                Icon(painterResource(R.drawable.ic_custom_copy), null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (copied) "Copied" else "Copy response")
                            }
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (emailError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step == 2) OutlinedIconButton(onClick = { step = 1 }, modifier = Modifier.size(52.dp)) {
                        Icon(painterResource(R.drawable.ic_custom_back), "Previous step", Modifier.size(20.dp))
                    }
                    Button(onClick = { if (step == 1) step = 2 else onSendEmail() },
                        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).bounceClick()
                            .testTag(if (step == 1) "survey_next_step_button" else "survey_send_email_button")) {
                        Icon(painterResource(if (step == 1) R.drawable.ic_custom_arrow_right else R.drawable.ic_custom_mail),
                            null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(if (step == 1) "Next · your ideas" else "Send via email", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (step == 1) Text("All questions are optional. Nothing is selected for you.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SurveyStepPill(number: String, title: String, active: Boolean, done: Boolean, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (done) Icon(painterResource(R.drawable.ic_custom_check), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            else Text(number, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun SurveyQuestionCard(title: String, subtitle: String, @DrawableRes icon: Int, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                    Icon(painterResource(icon), null, Modifier.padding(8.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurveyChoices(options: List<SurveyChoice>, selected: (String) -> Boolean, onSelect: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { option ->
            FilterChip(selected(option.answer), onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(option.answer)
            }, label = { Text(option.label, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(painterResource(option.icon), null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
        }
    }
}

@Composable
private fun OptionalSurveySlider(
    value: Int?, lowLabel: String, highLabel: String, tag: String, onChange: (Int?) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    // The hidden starting position is geometry only. It never becomes an answer until interaction.
    var gestureValue by remember { mutableFloatStateOf(value?.toFloat() ?: 1f) }
    LaunchedEffect(value) { gestureValue = value?.toFloat() ?: 1f }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(value?.let { "$it / 10" } ?: "Not answered", style = MaterialTheme.typography.titleSmall,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f))
        if (value != null) TextButton(onClick = { onChange(null) }, modifier = Modifier.testTag(tag + "_clear")) {
            Icon(painterResource(R.drawable.ic_custom_close), null, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clear")
        }
    }
    Slider(value = value?.toFloat() ?: 1f, onValueChange = {
        gestureValue = it
        onChange(it.roundToInt().coerceIn(1, 10))
    }, valueRange = 1f..10f, steps = 8, interactionSource = interaction,
        onValueChangeFinished = { onChange(gestureValue.roundToInt().coerceIn(1, 10)) },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            sliderTestTag = tag
            stateDescription = value?.let { "$it out of 10" } ?: "Not answered. Adjust to choose a rating."
            progressBarRangeInfo = ProgressBarRangeInfo(value?.toFloat() ?: 1f, 1f..10f, 8)
            setProgress { score -> onChange(score.roundToInt().coerceIn(1, 10)); true }
        },
        thumb = { SliderDefaults.Thumb(interactionSource = interaction,
            modifier = Modifier.alpha(if (value == null) 0f else 1f)) },
        track = { state ->
            if (value == null) Box(Modifier.fillMaxWidth().height(12.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(50)))
            else SliderDefaults.Track(sliderState = state)
        })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("1 · $lowLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("10 · $highLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

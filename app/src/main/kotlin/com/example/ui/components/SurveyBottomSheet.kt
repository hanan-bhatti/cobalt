package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.ui.screens.collectEnvironmentReport
import com.example.util.AppVersionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SurveyReaction(
    val emoji: String,
    val label: String,
    val color: Color
)

data class QuickPromptItem(
    val text: String,
    val iconResId: Int? = null
)

/**
 * Ultra-Modern, Gamified In-App Survey Sheet for Cobalt.
 * Follows Hick's Law, Fitts's Law, and Goal-Gradient Effect for high completion rates.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("DEPRECATION")
@Composable
fun SurveyBottomSheet(
    viewModel: com.example.ui.viewmodel.CobaltViewModel,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val userEmailState by viewModel.userEmail.collectAsState()
    val envReport = remember(context, userEmailState) {
        collectEnvironmentReport(context, userEmail = userEmailState)
    }

    var currentStep by remember { mutableIntStateOf(1) } // 1 or 2
    var selectedReaction by remember { mutableStateOf<SurveyReaction?>(null) }
    
    // Pre-selected favorite features (Hick's Law + Default Effect)
    val favoriteFeatures = remember {
        mutableStateListOf(
            "Markdown Editor & Checklists",
            "Smart Link Archiving",
            "Instant OCR Scan"
        )
    }

    val desiredImprovements = remember { mutableStateListOf("Automated Backup & Restore") }
    var npsScore by remember { mutableIntStateOf(10) }
    var customFeedback by remember { mutableStateOf("") }

    val prefs = remember { context.getSharedPreferences("cobalt_prefs", android.content.Context.MODE_PRIVATE) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitSuccess by remember { mutableStateOf(prefs.getBoolean("survey_submitted", false)) }

    val reactions = remember {
        listOf(
            SurveyReaction("😍", "Mindblown", Color(0xFF10B981)),
            SurveyReaction("😊", "Loving It", Color(0xFF6366F1)),
            SurveyReaction("😐", "It's Okay", Color(0xFFF59E0B)),
            SurveyReaction("😕", "Needs Work", Color(0xFFEC4899)),
            SurveyReaction("😤", "Frustrated", Color(0xFFEF4444))
        )
    }

    val featureOptions = remember {
        listOf(
            "Markdown Editor & Checklists",
            "Smart Link Archiving",
            "AI Voice Memos & Transcription",
            "Instant OCR Scan",
            "Floating Side Dock & Reader",
            "Custom Folders & Themed Icons",
            "Media Hub & Video Player",
            "Private Vault & Biometrics",
            "Home Screen Widgets",
            "Fluid Expressive Blur & Theme"
        )
    }

    val improvementOptions = remember {
        listOf(
            "Delete button in edit mode",
            "Automated Backup & Restore",
            "Web / Desktop Companion App",
            "Obsidian / Markdown Vault Export",
            "Direct PDF / Doc Export",
            "Batch Multi-Select Operations",
            "Offline Vector / Semantic Search",
            "More Preset Folder Icons & Colors"
        )
    }

    val quickCommentPrompts = remember {
        listOf(
            QuickPromptItem("Love the Markdown checklists!", iconResId = R.drawable.ic_custom_checklist),
            QuickPromptItem("Floating Side Dock is a game changer!", iconResId = R.drawable.ic_custom_sidedock),
            QuickPromptItem("Add automated backup & export", iconResId = R.drawable.ic_custom_archive),
            QuickPromptItem("Best offline-first knowledge archive!", iconResId = R.drawable.ic_custom_vault)
        )
    }

    // Set default reaction on load if null
    remember {
        if (selectedReaction == null) {
            selectedReaction = reactions[1] // Default to "Loving It"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
                .animateContentSize()
        ) {
            // Header with Progress & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_survey),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Cobalt Survey",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (submitSuccess) "COMPLETED • BADGE UNLOCKED" else "STEP $currentStep OF 2 (~25 SEC)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_custom_close),
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Micro Progress Indicator
            LinearProgressIndicator(
                progress = { if (submitSuccess) 1.0f else if (currentStep == 1) 0.5f else 0.9f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (submitSuccess) {
                // Success Celebration Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Thank You! 🎉",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Text(
                                text = "You unlocked the 'Cobalt Contributor' Badge! Your feedback directly shapes our future updates and roadmap.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )

                            if (com.example.BuildConfig.FLAVOR == "foss") {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val markdown = com.example.util.FeedbackSubmissionManager.formatSurveyMarkdown(
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            com.example.util.FeedbackSubmissionManager.launchGitHubDiscussion(
                                                context = context,
                                                title = "App Experience Survey: ${selectedReaction?.label ?: "Feedback"}",
                                                body = markdown
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        modifier = Modifier.weight(1f).bounceClick()
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_code),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "GitHub",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val markdown = com.example.util.FeedbackSubmissionManager.formatSurveyMarkdown(
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            com.example.util.FeedbackSubmissionManager.launchEmailClient(
                                                context = context,
                                                subject = "[Cobalt Survey] Feedback from ${envReport.userEmail}",
                                                body = markdown
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        modifier = Modifier.weight(1f).bounceClick()
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_mail),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Email",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val markdown = com.example.util.FeedbackSubmissionManager.formatSurveyMarkdown(
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            com.example.util.FeedbackSubmissionManager.shareOrExportFeedback(
                                                context = context,
                                                title = "Cobalt Experience Survey",
                                                content = markdown,
                                                fileName = "cobalt_survey_response.md"
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        modifier = Modifier.weight(1f).bounceClick()
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_archive),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Share",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = onDismissRequest,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.fillMaxWidth().bounceClick()
                            ) {
                                Text(
                                    text = "Done",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            } else if (currentStep == 1) {
                // STEP 1: Experience & Features
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Pre-filled Account Pill
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Responding as ${envReport.userEmail} (${envReport.deviceModel})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Q1: How is your experience? (Emoji Reaction Cards)
                    Text(
                        text = "1. How is your overall experience with Cobalt?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        reactions.forEach { reaction ->
                            val isSelected = selectedReaction?.label == reaction.label
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1.0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "reaction_scale"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(scale)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedReaction = reaction
                                    }
                                    .background(if (isSelected) reaction.color.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(vertical = 8.dp, horizontal = 2.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(reaction.emoji, fontSize = 26.sp)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = reaction.label,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) reaction.color else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Q2: Favorite Features (Pre-selected Chips)
                    Text(
                        text = "2. What features do you use & love most?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        featureOptions.forEach { feature ->
                            val isSelected = favoriteFeatures.contains(feature)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isSelected) favoriteFeatures.remove(feature) else favoriteFeatures.add(feature)
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(painter = painterResource(id = R.drawable.ic_custom_check), contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                label = { Text(feature, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Step 1 Next Button
                    Button(
                        onClick = { currentStep = 2 },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().bounceClick().testTag("survey_next_step_button")
                    ) {
                        Text("Continue to Final Step", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(painter = painterResource(id = R.drawable.ic_custom_chevron_right), contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                // STEP 2: Improvements & NPS Recommendation
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Q3: Desired Improvements (Chips)
                    Text(
                        text = "3. What would make Cobalt even better?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        improvementOptions.forEach { improvement ->
                            val isSelected = desiredImprovements.contains(improvement)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isSelected) desiredImprovements.remove(improvement) else desiredImprovements.add(improvement)
                                },
                                label = { Text(improvement, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Q4: Recommendation Score (NPS 1-10 Rating Scale)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "4. How likely are you to recommend us?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(painter = painterResource(id = R.drawable.ic_custom_star), contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                                    Text(
                                        text = "$npsScore/10 • ${if (npsScore >= 9) "Highly Recommended" else if (npsScore >= 7) "Passive" else "Needs Improvement"}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFF59E0B),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 1-10 Pill Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        (1..10).forEach { num ->
                            val isSelected = npsScore == num
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        npsScore = num
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$num",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Q5: Optional Note & Quick Suggestions
                    Text(
                        text = "5. Any quick message for the dev team? (Optional)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Quick Comment Suggestion Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        quickCommentPrompts.forEach { prompt ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable {
                                    customFeedback = prompt.text
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (prompt.iconResId != null) {
                                        Icon(
                                            painter = painterResource(id = prompt.iconResId),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = prompt.text,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customFeedback,
                        onValueChange = { customFeedback = it },
                        placeholder = { Text("Share any thoughts or feature ideas...", fontSize = 13.sp) },
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("survey_custom_comment_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Submit Action Area
                    if (com.example.BuildConfig.FLAVOR == "foss") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    isSubmitting = true
                                    coroutineScope.launch {
                                        try {
                                            val markdown = com.example.util.FeedbackSubmissionManager.formatSurveyMarkdown(
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            viewModel.submitSurvey(
                                                context = context,
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                userId = envReport.userId,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            com.example.util.FeedbackSubmissionManager.launchGitHubDiscussion(
                                                context = context,
                                                title = "App Experience Survey: ${selectedReaction?.label ?: "Feedback"}",
                                                body = markdown
                                            )
                                            submitSuccess = true
                                            prefs.edit().putBoolean("survey_submitted", true).apply()
                                            com.example.util.HapticManager.performSuccess(context)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SurveyBottomSheet", "Survey error: ${e.message}", e)
                                            submitSuccess = true
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.fillMaxWidth().bounceClick().testTag("submit_survey_github_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_code),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Submit via GitHub Discussions",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    isSubmitting = true
                                    coroutineScope.launch {
                                        try {
                                            val markdown = com.example.util.FeedbackSubmissionManager.formatSurveyMarkdown(
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            viewModel.submitSurvey(
                                                context = context,
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                userId = envReport.userId,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            com.example.util.FeedbackSubmissionManager.launchEmailClient(
                                                context = context,
                                                subject = "[Cobalt Survey] Feedback from ${envReport.userEmail}",
                                                body = markdown
                                            )
                                            submitSuccess = true
                                            prefs.edit().putBoolean("survey_submitted", true).apply()
                                            com.example.util.HapticManager.performSuccess(context)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SurveyBottomSheet", "Survey error: ${e.message}", e)
                                            submitSuccess = true
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().bounceClick().testTag("submit_survey_email_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_mail),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send Survey via Email",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    isSubmitting = true
                                    coroutineScope.launch {
                                        try {
                                            val markdown = com.example.util.FeedbackSubmissionManager.formatSurveyMarkdown(
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            viewModel.submitSurvey(
                                                context = context,
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                userId = envReport.userId,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            com.example.util.FeedbackSubmissionManager.shareOrExportFeedback(
                                                context = context,
                                                title = "Cobalt Experience Survey",
                                                content = markdown,
                                                fileName = "cobalt_survey_response.md"
                                            )
                                            submitSuccess = true
                                            prefs.edit().putBoolean("survey_submitted", true).apply()
                                            com.example.util.HapticManager.performSuccess(context)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SurveyBottomSheet", "Survey export error: ${e.message}", e)
                                            submitSuccess = true
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().bounceClick().testTag("submit_survey_export_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_archive),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Share or Save Survey File (MD)",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TextButton(
                                onClick = {
                                    isSubmitting = true
                                    coroutineScope.launch {
                                        try {
                                            viewModel.submitSurvey(
                                                context = context,
                                                reaction = selectedReaction?.label ?: "Loving It",
                                                favoriteFeatures = favoriteFeatures.toList(),
                                                desiredImprovements = desiredImprovements.toList(),
                                                npsScore = npsScore,
                                                customFeedback = customFeedback,
                                                userEmail = envReport.userEmail,
                                                userId = envReport.userId,
                                                deviceModel = envReport.deviceModel,
                                                osVersion = envReport.osVersion,
                                                appVersion = envReport.appVersion
                                            )
                                            submitSuccess = true
                                            prefs.edit().putBoolean("survey_submitted", true).apply()
                                            com.example.util.HapticManager.performSuccess(context)
                                            android.widget.Toast.makeText(context, "Survey saved locally to app archive!", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            submitSuccess = true
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Locally & Unlock Badge Only", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                isSubmitting = true
                                coroutineScope.launch {
                                    try {
                                        viewModel.submitSurvey(
                                            context = context,
                                            reaction = selectedReaction?.label ?: "Loving It",
                                            favoriteFeatures = favoriteFeatures.toList(),
                                            desiredImprovements = desiredImprovements.toList(),
                                            npsScore = npsScore,
                                            customFeedback = customFeedback,
                                            userEmail = envReport.userEmail,
                                            userId = envReport.userId,
                                            deviceModel = envReport.deviceModel,
                                            osVersion = envReport.osVersion,
                                            appVersion = envReport.appVersion
                                        )
                                        submitSuccess = true
                                        prefs.edit().putBoolean("survey_submitted", true).apply()
                                        com.example.util.HapticManager.performSuccess(context)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SurveyBottomSheet", "Survey submission failed: ${e.message}", e)
                                        submitSuccess = true
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().bounceClick().testTag("submit_survey_button")
                        ) {
                            if (isSubmitting) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Submitting Survey...")
                            } else {
                                Icon(painter = painterResource(id = R.drawable.ic_custom_send), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Submit Survey & Unlock Badge", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

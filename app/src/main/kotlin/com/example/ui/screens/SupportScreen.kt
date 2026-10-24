package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AppVersionBadge
import com.example.ui.components.bounceClick
import com.example.util.AppVersionManager
import kotlinx.coroutines.launch

data class FaqCategory(
    val id: String,
    val name: String,
    @DrawableRes val iconRes: Int,
    val accentColor: Color
)

data class ExpressiveFaqItem(
    val categoryId: String,
    val question: String,
    val answer: String,
    val tags: List<String> = emptyList()
)

/**
 * Custom Redesigned Expressive Support & Help Center Hub for Cobalt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    viewModel: com.example.ui.viewmodel.CobaltViewModel? = null,
    onNavigateBack: () -> Unit,
    onNavigateToFeedback: (tabIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_help),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Help & Support",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "KNOWLEDGE BASE & FAQ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button_support")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ExpressiveHelpCenterContent(onNavigateToFeedback = onNavigateToFeedback)
        }
    }
}

@Composable
private fun ExpressiveHelpCenterContent(
    onNavigateToFeedback: (tabIndex: Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val expandedItems = remember { mutableStateMapOf<Int, Boolean>() }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE) }
    
    val helpfulFeedback = remember {
        val map = mutableStateMapOf<String, Boolean>()
        // Load initial state
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("faq_")) {
                val questionKey = key.removePrefix("faq_")
                map[questionKey] = value as Boolean
            }
        }
        map
    }

    val categories = remember {
        listOf(
            FaqCategory("getting_started", "Getting Started", R.drawable.ic_custom_star, Color(0xFF6366F1)),
            FaqCategory("ai_ocr", "AI & Vision OCR", R.drawable.ic_custom_ocr, Color(0xFF8B5CF6)),
            FaqCategory("sync", "Privacy & Storage", R.drawable.ic_custom_cloud_queue, Color(0xFF0EA5E9)),
            FaqCategory("voice", "Voice & Audio", R.drawable.ic_custom_voice, Color(0xFFEC4899)),
            FaqCategory("widgets", "Widgets & Interface", R.drawable.ic_custom_tools, Color(0xFF10B981))
        )
    }

    val faqList = remember {
        listOf(
            ExpressiveFaqItem(
                "getting_started",
                "How do I capture content into Cobalt?",
                "You can save items in three ways: tap the floating action button (+), share text, links, or images from any app directly via the Android system Share sheet, or swipe inward on the screen edge to open the Cobalt Side Dock."
            ),
            ExpressiveFaqItem(
                "getting_started",
                "How does Cobalt organize my items?",
                "Items are automatically categorized into Notes, Links, Images, Videos, Audio, Code, and Media. You can also create custom color-coded Folders, assign items across multiple folders, and pin favorite folders to the top of your Folders tab."
            ),
            ExpressiveFaqItem(
                "getting_started",
                "What is the Floating Side Dock?",
                "The Side Dock is a screen-edge handle that stays accessible over any app. Swiping it open allows you to quickly jot notes, inspect recent items, launch screen OCR, or bookmark links without leaving your current application."
            ),
            ExpressiveFaqItem(
                "ai_ocr",
                "How does Gemini AI Vision OCR work?",
                "When you select a region using the OCR tool, the cropped visual is sent to the Google Gemini API to extract text, Markdown structure, and hyperlinks. This requires an active internet connection and a free API key from Google AI Studio."
            ),
            ExpressiveFaqItem(
                "ai_ocr",
                "How do I configure my Gemini API Key and models?",
                "Open Settings -> Intelligence & Gemini AI, and paste your API key from Google AI Studio (aistudio.google.com). You can independently choose your preferred model for Vision OCR and Voice Memo transcription (such as Gemini 2.5 Flash, 2.0 Flash, or 1.5 Flash), or define a custom OCR system prompt."
            ),
            ExpressiveFaqItem(
                "ai_ocr",
                "How does Media Hub search work?",
                "The Media tab searches TMDb and the Jikan Anime API to automatically populate posters, ratings, release dates, streaming providers, and overviews. You can use Cobalt's built-in search or enter a personal TMDb API key in Settings -> Media API Keys."
            ),
            ExpressiveFaqItem(
                "sync",
                "Is my data stored locally or on a remote server?",
                "Cobalt is offline-first. All your notes, folders, and extracted content are stored locally in an SQLite Room database on your device. Cobalt collects zero telemetry or analytics. In the Play flavor, optional cloud synchronization with Firebase is available when signed in."
            ),
            ExpressiveFaqItem(
                "sync",
                "Can I use Cobalt completely offline without an API key?",
                "Yes! You can create rich text notes, bookmark URLs, organize custom folders, attach images/videos, and listen to voice recordings entirely offline without any external accounts or API keys."
            ),
            ExpressiveFaqItem(
                "voice",
                "How do voice recordings and audio transcription work?",
                "You can record voice memos using the microphone action in the capture menu. Audio files are saved locally on your device. When a Gemini API key is configured, Cobalt can transcribe your speech into structured Markdown notes."
            ),
            ExpressiveFaqItem(
                "voice",
                "What audio player controls are available?",
                "The built-in media player supports playback, pausing, scrubbing through recordings, and one-tap sharing or copying of transcribed notes."
            ),
            ExpressiveFaqItem(
                "widgets",
                "How do I add home screen widgets?",
                "Long-press on your Android home screen, select Widgets, find Cobalt, and place either the Quick Capture widget (for instant 1-tap note entry) or the Recent Items widget."
            ),
            ExpressiveFaqItem(
                "widgets",
                "Can I customize animations, haptic feedback, and themes?",
                "Yes! In Settings, you can choose System Default, Light, Dark, or dynamic Material You themes. You can also tune high-precision haptic feedback, or customize Side Dock spring physics (stiffness and damping) with a live Canvas preview."
            )
        )
    }

    val filteredFaqs = remember(searchQuery, selectedCategoryId) {
        faqList.filter { item ->
            val matchesCategory = selectedCategoryId == null || item.categoryId == selectedCategoryId
            val matchesSearch = searchQuery.isBlank() ||
                item.question.contains(searchQuery, ignoreCase = true) ||
                item.answer.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Expressive Hero Search Banner
        item {
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
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "How can we help?",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Search FAQs or explore categories below",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            AppVersionBadge(tag = AppVersionManager.currentTag)
                        }

                        // Search Input Field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search help articles & FAQs...", fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_search),
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_custom_close),
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            maxLines = 1,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("support_search_input")
                        )
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CATEGORIES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.sp
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 12.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("All Topics (${faqList.size})") },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }

                    items(categories) { category ->
                        val count = faqList.count { it.categoryId == category.id }
                        val isSelected = selectedCategoryId == category.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryId = if (isSelected) null else category.id },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = category.iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else category.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("${category.name} ($count)") },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // FAQ Items List
        items(filteredFaqs.size) { index ->
            val item = filteredFaqs[index]
            val isExpanded = expandedItems[index] == true
            val category = categories.find { it.id == item.categoryId } ?: categories.first()

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick()
                    .animateContentSize()
                    .clickable { expandedItems[index] = !isExpanded }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = category.accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = category.iconRes),
                                        contentDescription = null,
                                        tint = category.accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = item.question,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            painter = painterResource(id = if (isExpanded) R.drawable.ic_custom_chevron_up else R.drawable.ic_custom_chevron_down),
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = item.answer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Interactive Answer Feedback Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Was this helpful?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                IconButton(
                                    onClick = {
                                        helpfulFeedback[item.question] = true
                                        prefs.edit().putBoolean("faq_${item.question}", true).apply()
                                        com.example.util.HapticManager.performClick(context)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbUp,
                                        contentDescription = "Helpful",
                                        tint = if (helpfulFeedback[item.question] == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        helpfulFeedback[item.question] = false
                                        prefs.edit().putBoolean("faq_${item.question}", false).apply()
                                        com.example.util.HapticManager.performClick(context)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbDown,
                                        contentDescription = "Not helpful",
                                        tint = if (helpfulFeedback[item.question] == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("FAQ Answer", "${item.question}\n\n${item.answer}")
                                    clipboard.setPrimaryClip(clip)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_custom_copy),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Couldn't find answer footer action card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Still need help?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Submit a bug report or suggest a new feature directly to our development team.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onNavigateToFeedback(0) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_feedback),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Report Bug", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onNavigateToFeedback(1) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_star),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Feature Request", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

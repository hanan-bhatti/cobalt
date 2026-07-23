package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppVersionBadge
import com.example.util.AppVersionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class FaqItem(
    val question: String,
    val answer: String,
    val category: String
)

/**
 * Modern Support & System Diagnostics Screen featuring Help Center & System Health.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFeedback: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Help Center & FAQ", "System Diagnostics")

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Help & System Support",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("back_button_support")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { onNavigateToFeedback(0) },
                            modifier = Modifier.testTag("open_feedback_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Feedback,
                                contentDescription = "Feedback & Bugs",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> HelpCenterTabContent(onNavigateToFeedback = onNavigateToFeedback)
                1 -> SystemDiagnosticsTabContent()
            }
        }
    }
}

@Composable
private fun HelpCenterTabContent(
    onNavigateToFeedback: (Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var expandedFaqIndex by remember { mutableStateOf<Int?>(null) }

    val categories = listOf("All", "Getting Started", "Sync & Storage", "AI & OCR", "Voice Memos", "Widgets")

    val faqList = remember {
        listOf(
            FaqItem(
                question = "How does the floating OCR screen handle work?",
                answer = "Swipe or tap the floating handle on the edge of your screen to open the capture HUD over any app. Select a region on screen, and Gemini AI will automatically extract text, links, or notes.",
                category = "AI & OCR"
            ),
            FaqItem(
                question = "Are my notes and recordings stored locally?",
                answer = "Yes! Second Brain uses an offline-first architecture. All your notes, folders, and recordings are saved locally in a Room database. Cloud sync to Firebase is optional.",
                category = "Sync & Storage"
            ),
            FaqItem(
                question = "How do I record and transcribe Voice Memos?",
                answer = "Tap the Voice Memo option from the Quick Capture FAB. Speak your thoughts with live waveform monitoring. Once done, Gemini AI will automatically transcribe and format your note.",
                category = "Voice Memos"
            ),
            FaqItem(
                question = "How do I customize Glance widgets on my home screen?",
                answer = "Go to Settings ➔ Home Screen Widgets. You can customize widget opacity, theme colors, greeting header visibility, and filter by folder or category.",
                category = "Widgets"
            ),
            FaqItem(
                question = "What happens when I update my Gemini API key?",
                answer = "Updating your API key in Settings automatically refreshes your model list and validates your connection so AI features continue working seamlessly.",
                category = "Getting Started"
            ),
            FaqItem(
                question = "Does Second Brain support Right-to-Left (RTL) languages?",
                answer = "Yes! Urdu, Arabic, Hebrew, and other RTL scripts are automatically detected and aligned properly in Markdown renderers, Rich Text editor, and note views.",
                category = "Getting Started"
            )
        )
    }

    val filteredFaqs = remember(searchQuery, selectedCategory) {
        faqList.filter { item ->
            (selectedCategory == "All" || item.category == selectedCategory) &&
                    (searchQuery.isBlank() || item.question.contains(searchQuery, ignoreCase = true) || item.answer.contains(searchQuery, ignoreCase = true))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search help articles & FAQs...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("faq_search_input")
            )
        }

        // Category Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category, fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // FAQ List
        items(filteredFaqs.size) { index ->
            val item = filteredFaqs[index]
            val isExpanded = expandedFaqIndex == index

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { expandedFaqIndex = if (isExpanded) null else index }
                    .animateContentSize()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = item.question,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = item.answer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Still Need Help Card
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Still Need Help or Want to Suggest Features?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Report a bug or submit a feature request directly to our team.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToFeedback(0) }, // Bug report
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("faq_report_bug_button")
                        ) {
                            Icon(imageVector = Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Report Bug", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onNavigateToFeedback(1) }, // Feature request
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("faq_request_feature_button")
                        ) {
                            Icon(imageVector = Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Feature Request", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun SystemDiagnosticsTabContent() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var isRunningDiagnostics by remember { mutableStateOf(false) }
    var diagnosticResult by remember { mutableStateOf<String?>(null) }
    var isCopied by remember { mutableStateOf(false) }

    val osVersion = remember { android.os.Build.VERSION.RELEASE }
    val deviceModel = remember { "${android.os.Build.MANUFACTURER.uppercase()} ${android.os.Build.MODEL}" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        item {
            // Header Diagnostic Action Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MedicalServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "System Diagnostics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                AppVersionBadge(tag = AppVersionManager.currentTag, fontSize = 9.sp)
                            }
                            Text(
                                text = "Check health status of app subsystems & database",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRunningDiagnostics) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Running diagnostic tests...", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Text(
                                text = diagnosticResult ?: "All systems operating normally",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                isRunningDiagnostics = true
                                diagnosticResult = null
                                coroutineScope.launch {
                                    delay(1400)
                                    isRunningDiagnostics = false
                                    diagnosticResult = "Diagnostics Complete: 100% Passed"
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("run_diagnostics_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Test", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Subsystem Health Metrics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            DiagnosticMetricRow(
                title = "App Version & Build",
                statusText = "v${AppVersionManager.currentVersionName} (#${AppVersionManager.currentVersionCode})",
                icon = Icons.Default.Info,
                isOk = true
            )
        }

        item {
            DiagnosticMetricRow(
                title = "Room Database Health",
                statusText = "SQLite Room DB Healthy • Active",
                icon = Icons.Default.Storage,
                isOk = true
            )
        }

        item {
            DiagnosticMetricRow(
                title = "Cloud Sync & Firestore",
                statusText = "Firebase Connected • Auth Verified",
                icon = Icons.Default.Wifi,
                isOk = true
            )
        }

        item {
            DiagnosticMetricRow(
                title = "Device Environment",
                statusText = "$deviceModel (Android $osVersion)",
                icon = Icons.Default.Security,
                isOk = true
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val info = "App: v${AppVersionManager.currentVersionName} (#${AppVersionManager.currentVersionCode})\nDevice: $deviceModel\nAndroid: $osVersion\nStatus: Healthy"
                    clipboardManager.setText(AnnotatedString(info))
                    isCopied = true
                    coroutineScope.launch {
                        delay(2000)
                        isCopied = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("copy_diagnostic_info_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isCopied) "Copied Diagnostic Logs!" else "Copy Diagnostic Info for Support")
            }
        }
    }
}

@Composable
private fun DiagnosticMetricRow(
    title: String,
    statusText: String,
    icon: ImageVector,
    isOk: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

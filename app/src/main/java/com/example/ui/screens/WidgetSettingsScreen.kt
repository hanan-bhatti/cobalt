/*
 * Second Brain - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodel.SecondBrainViewModel
import com.example.widget.QuickCaptureWidgetReceiver
import com.example.widget.RecentItemsWidgetReceiver
import com.example.widget.WidgetUpdater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    viewModel: SecondBrainViewModel,
    onNavigateBack: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }

    val recentWidgetIds by rememberUpdatedState(
        appWidgetManager.getAppWidgetIds(ComponentName(context, RecentItemsWidgetReceiver::class.java))
    )
    val quickWidgetIds by rememberUpdatedState(
        appWidgetManager.getAppWidgetIds(ComponentName(context, QuickCaptureWidgetReceiver::class.java))
    )

    val totalActiveWidgets = recentWidgetIds.size + quickWidgetIds.size

    val widgetOpacity by viewModel.settingsRepository.widgetOpacity.collectAsState()
    val widgetShowHeader by viewModel.settingsRepository.widgetShowHeader.collectAsState()
    val widgetCategoryFilter by viewModel.settingsRepository.widgetCategoryFilter.collectAsState()
    val widgetMaxItems by viewModel.settingsRepository.widgetMaxItems.collectAsState()
    val quickCaptureAction by viewModel.settingsRepository.quickCaptureAction.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget Customization", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            WidgetUpdater.update(context)
                            Toast.makeText(context, "Force refreshed all widgets!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_custom_sync),
                            contentDescription = "Force Refresh",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SUB-PAGES / TABS
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Quick Capture", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Recent Archive", fontWeight = FontWeight.SemiBold) }
                )
            }

            val allItems by viewModel.allItems.collectAsState()

            if (selectedTab == 0) {
                QuickCaptureCustomizationSection(
                    viewModel = viewModel
                )
            } else {
                RecentItemsCustomizationSection(
                    realItems = allItems,
                    opacity = widgetOpacity,
                    onOpacityChanged = { viewModel.settingsRepository.setWidgetOpacity(it) },
                    showHeader = widgetShowHeader,
                    onHeaderToggled = { viewModel.settingsRepository.setWidgetShowHeader(it) },
                    categoryFilter = widgetCategoryFilter,
                    onCategorySelected = { viewModel.settingsRepository.setWidgetCategoryFilter(it) },
                    maxItems = widgetMaxItems,
                    onMaxItemsSelected = { viewModel.settingsRepository.setWidgetMaxItems(it) }
                )
            }
        }
    }
}



@Composable
private fun AnimatedCapsuleDotIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isCurrent = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isCurrent) 22.dp else 7.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dotWidth"
            )
            val color by animateColorAsState(
                targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                animationSpec = tween(250),
                label = "dotColor"
            )

            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun QuickCaptureCustomizationSection(
    viewModel: SecondBrainViewModel
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val quickWidgetIds = remember {
        appWidgetManager.getAppWidgetIds(ComponentName(context, com.example.widget.QuickCaptureWidgetReceiver::class.java))
    }

    val slotCount = remember(quickWidgetIds) { maxOf(1, quickWidgetIds.size) }
    val pagerState = rememberPagerState { slotCount }

    var slotActions by remember {
        mutableStateOf(
            (0 until slotCount).map { viewModel.settingsRepository.getQuickCaptureActionForSlot(it) }
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val actions = remember(isDark, primaryColor) {
        listOf(
            QuickActionOption("TEXT", "Add Note", "Create quick text note", R.drawable.ic_custom_text, CategoryText),
            QuickActionOption("LINK", "Save Link", "Bookmark URL or web page", R.drawable.ic_custom_link, CategoryLink),
            QuickActionOption("IMAGE", "Photo Capture", "Snap photo or select image", R.drawable.ic_custom_image, CategoryImage),
            QuickActionOption("AUDIO", "Voice Memo", "Record audio memo instantly", R.drawable.ic_custom_voice, CategoryAudio),
            QuickActionOption("CODE", "Add Code", "Save code snippet", R.drawable.ic_custom_code, CategoryCode),
            QuickActionOption("MEDIA", "Add Movie / Show", "Search movies, TV & anime", R.drawable.ic_custom_movie, CategoryMedia),
            QuickActionOption("OCR", "Screen OCR", "Capture & extract text from screen", R.drawable.ic_custom_ocr, primaryColor)
        ).map { it.copy(accentColor = it.accentColor.toThemeColor(isDark)) }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // LIVE PREVIEW CARD WITH SNAPPY HORIZONTAL PAGER
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val pageAction = slotActions.getOrElse(page) { "TEXT" }
                    val selectedOption = actions.find { it.id == pageAction } ?: actions[0]

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "WIDGET LIVE PREVIEW — SLOT ${page + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        // Simulated 1x1 circular widget button
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(selectedOption.accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = selectedOption.iconRes),
                                contentDescription = selectedOption.title,
                                modifier = Modifier.size(32.dp),
                                tint = Color.White
                            )
                        }

                        Text(
                            text = selectedOption.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedOption.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ANIMATED CAPSULE DOT INDICATOR (_ ...)
                AnimatedCapsuleDotIndicator(
                    pageCount = slotCount,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        val currentSlotAction = slotActions.getOrElse(pagerState.currentPage) { "TEXT" }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select Action for Widget #${pagerState.currentPage + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { option ->
                    val isSelected = option.id == currentSlotAction
                    Surface(
                        onClick = {
                            viewModel.settingsRepository.setQuickCaptureActionForSlot(pagerState.currentPage, option.id)
                            val newList = slotActions.toMutableList()
                            newList[pagerState.currentPage] = option.id
                            slotActions = newList
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) option.accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) option.accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(option.accentColor.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = option.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = option.accentColor
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = option.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = option.accentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class QuickActionOption(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    val accentColor: Color
)

@Composable
private fun RecentItemsCustomizationSection(
    realItems: List<com.example.data.model.SavedItem>,
    opacity: Float,
    onOpacityChanged: (Float) -> Unit,
    showHeader: Boolean,
    onHeaderToggled: (Boolean) -> Unit,
    categoryFilter: String,
    onCategorySelected: (String) -> Unit,
    maxItems: Int,
    onMaxItemsSelected: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val cachedItems = remember {
        try { com.example.widget.WidgetCache.getCachedItems(context) } catch (e: Exception) { emptyList() }
    }
    val itemsToDisplay = remember(realItems, cachedItems, categoryFilter, maxItems) {
        val base = if (realItems.isNotEmpty()) realItems else cachedItems
        val filtered = if (categoryFilter == "All") base else base.filter { it.type.name.equals(categoryFilter, ignoreCase = true) }
        filtered.take(maxItems)
    }

    val userName = remember {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            auth.currentUser?.displayName?.trim()?.ifBlank { null }
                ?: auth.currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
        } catch (e: Exception) { null } ?: "User"
    }

    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // LIVE PREVIEW CARD FOR RECENT ITEMS WIDGET
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "REAL WIDGET LIVE PREVIEW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp, bottom = 2.dp)
                )

                // Simulated Widget Container (Edge-to-Edge)
                Surface(
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showHeader) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "$greeting, $userName",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Your Second Brain",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_custom_sync),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (itemsToDisplay.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (categoryFilter == "All") "Your archive is empty" else "No $categoryFilter items found",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsToDisplay.forEach { item ->
                                    val (iconRes, rawColor) = when (item.type) {
                                        com.example.data.model.SavedItemType.LINK -> Pair(R.drawable.ic_custom_link, CategoryLink)
                                        com.example.data.model.SavedItemType.IMAGE, com.example.data.model.SavedItemType.VIDEO -> Pair(R.drawable.ic_custom_image, CategoryImage)
                                        com.example.data.model.SavedItemType.CODE -> Pair(R.drawable.ic_custom_code, CategoryCode)
                                        com.example.data.model.SavedItemType.AUDIO -> Pair(R.drawable.ic_custom_voice, CategoryAudio)
                                        com.example.data.model.SavedItemType.MEDIA -> Pair(R.drawable.ic_custom_movie, CategoryMedia)
                                        else -> Pair(R.drawable.ic_custom_text, CategoryText)
                                    }
                                    val categoryColor = rawColor.toThemeColor(isDark)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(categoryColor.copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = categoryColor
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title.ifBlank { item.type.displayName },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val snippet = item.content.ifBlank { item.extractedText ?: "" }
                                            if (snippet.isNotBlank()) {
                                                Text(
                                                    text = snippet,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // CATEGORY FILTER
        Text("Filter by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val categories = listOf("All", "LINK", "TEXT", "IMAGE", "AUDIO", "CODE", "MEDIA")
            categories.forEach { cat ->
                val isSelected = categoryFilter.equals(cat, ignoreCase = true)
                val labelText = when (cat) {
                    "All" -> "All"
                    "LINK" -> "Links"
                    "TEXT" -> "Notes"
                    "IMAGE" -> "Images"
                    "AUDIO" -> "Audio"
                    "CODE" -> "Code"
                    "MEDIA" -> "Movies & Anime"
                    else -> cat
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelected(cat) },
                    label = { Text(labelText, fontSize = 12.sp) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // MAX ITEMS
        Text("Maximum Displayed Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5, 10, 15, 20).forEach { num ->
                val isSelected = maxItems == num
                FilterChip(
                    selected = isSelected,
                    onClick = { onMaxItemsSelected(num) },
                    label = { Text("$num items", fontSize = 12.sp) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))



        // OPACITY SLIDER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Background Opacity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${(opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = opacity,
            onValueChange = onOpacityChanged,
            valueRange = 0.4f..1.0f,
            steps = 5
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // SHOW HEADER TOGGLE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Greeting Header", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Displays 'Good morning, User' header on the widget.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = showHeader,
                onCheckedChange = onHeaderToggled
            )
        }
    }
}

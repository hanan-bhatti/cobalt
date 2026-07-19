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

package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.repository.SecondBrainRepository
import androidx.glance.appwidget.cornerRadius
import androidx.glance.unit.ColorProvider

import com.example.utils.AnalyticsHelper

class SecondBrainWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SecondBrainWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AnalyticsHelper.logWidgetAdded(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.update(context)
    }
}

class RefreshCallback : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        WidgetUpdater.update(context)
    }
}

class SecondBrainWidget : GlanceAppWidget() {
    
    override val sizeMode = SizeMode.Single
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = SecondBrainRepository(context)
        var isFromCache = false
        var isTimeout = false
        
        val items = try {
            val fetched = kotlinx.coroutines.withTimeoutOrNull(3000) {
                repository.getAllItems()
            }
            if (fetched != null) {
                WidgetCache.saveItemsToCache(context, fetched)
                fetched
            } else {
                isTimeout = true
                isFromCache = true
                WidgetCache.getCachedItems(context)
            }
        } catch (e: Exception) {
            isFromCache = true
            WidgetCache.getCachedItems(context)
        }

        provideContent {
            GlanceTheme {
                // Modernized card design with rounded corners and adaptive surface colors
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(24.dp)
                        .padding(12.dp)
                ) {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        WidgetHeader(isFromCache = isFromCache, isTimeout = isTimeout)
                        
                        Spacer(modifier = GlanceModifier.height(10.dp))
                        
                        if (items.isEmpty()) {
                            Box(
                                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                val emptyText = if (isTimeout) {
                                    "Connection timed out\nTap a quick action to capture"
                                } else {
                                    "Your second brain is empty\nTap a quick action to capture"
                                }
                                Text(
                                    text = emptyText,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        textAlign = androidx.glance.text.TextAlign.Center
                                    )
                                )
                            }
                        } else {
                            // Display up to 3 items inside a clean scrollable list
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                items.take(3).forEach { item ->
                                    ItemRow(item)
                                    Spacer(modifier = GlanceModifier.height(6.dp))
                                }
                            }
                        }
                        
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        
                        // Beautifully color-coded round quick action buttons
                        QuickActionRow()
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun WidgetHeader(isFromCache: Boolean = false, isTimeout: Boolean = false) {
    val context = LocalContext.current
    
    val userName = try {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        auth.currentUser?.displayName?.trim()?.ifBlank { null }
            ?: auth.currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
    } catch (e: Exception) {
        null
    } ?: try {
        val prefs = context.getSharedPreferences("second_brain_prefs", Context.MODE_PRIVATE)
        prefs.getString("simulated_name", null)?.trim()?.ifBlank { null }
            ?: prefs.getString("simulated_email", null)?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
    } catch (e: Exception) {
        null
    } ?: "User"

    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$greeting, $userName",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )
                if (isFromCache || isTimeout) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    val badgeColor = if (isTimeout) GlanceTheme.colors.error else GlanceTheme.colors.primary
                    val badgeText = if (isTimeout) "Offline" else "Cached"
                    Box(
                        modifier = GlanceModifier
                            .background(badgeColor)
                            .cornerRadius(4.dp)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                color = GlanceTheme.colors.onPrimary
                            )
                        )
                    }
                }
            }
            Spacer(modifier = GlanceModifier.height(1.dp))
            Text(
                text = "Your Second Brain",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
        
        // Refresh / Retry button with a subtle tint background and circular corner radius
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(14.dp)
                .clickable(androidx.glance.appwidget.action.actionRunCallback<RefreshCallback>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_custom_sync),
                contentDescription = "Refresh Widget",
                modifier = GlanceModifier.size(14.dp)
            )
        }
        
        Spacer(modifier = GlanceModifier.width(8.dp))
        
        val addIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_TEXT"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        // Plus action with a bright orange background glow and circular corner radius
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .background(GlanceTheme.colors.primary)
                .cornerRadius(14.dp)
                .clickable(actionStartActivity(addIntent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_custom_plus),
                contentDescription = "Add Text Notes",
                modifier = GlanceModifier.size(14.dp)
            )
        }
    }
}

@androidx.compose.runtime.Composable
fun ItemRow(item: SavedItem) {
    val context = LocalContext.current
    val openIntent = if (item.type == SavedItemType.LINK) {
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.content)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    } else {
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("secondbrain://item/${item.id}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
    
    // Choose beautiful color accents and icons based on type
    val (iconRes, iconTintBg) = when (item.type) {
        SavedItemType.LINK -> Pair(R.drawable.ic_custom_link, GlanceTheme.colors.primaryContainer)
        SavedItemType.IMAGE, SavedItemType.VIDEO -> Pair(R.drawable.ic_custom_image, GlanceTheme.colors.tertiaryContainer)
        SavedItemType.CODE -> Pair(R.drawable.ic_custom_code, GlanceTheme.colors.secondaryContainer)
        SavedItemType.AUDIO -> Pair(R.drawable.ic_custom_voice, GlanceTheme.colors.primaryContainer)
        else -> Pair(R.drawable.ic_custom_text, GlanceTheme.colors.primaryContainer)
    }
    
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surface) // Adaptive background
            .cornerRadius(12.dp)
            .clickable(actionStartActivity(openIntent))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High contrast colored round icon container (circle)
        Box(
            modifier = GlanceModifier
                .size(24.dp)
                .background(iconTintBg) // Adaptive tint
                .cornerRadius(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(12.dp)
            )
        }
        
        Spacer(modifier = GlanceModifier.width(10.dp))
        
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.title.ifBlank { item.type.displayName },
                maxLines = 1,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 11.sp
                )
            )
            val subText = item.content.ifBlank { item.extractedText ?: "" }
            if (subText.isNotBlank()) {
                Text(
                    text = subText,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                )
            }
        }
        
        Spacer(modifier = GlanceModifier.width(4.dp))
        
        // Arrow pointing in
        Text(
            text = "→",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@androidx.compose.runtime.Composable
fun QuickActionRow() {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Link Button (Blue)
        val linkIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_LINK"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        QuickActionButton(
            iconRes = R.drawable.ic_custom_link,
            intent = linkIntent,
            bgColor = GlanceTheme.colors.primaryContainer,
            iconTintGlow = GlanceTheme.colors.onPrimaryContainer,
            label = "Link"
        )
        
        Spacer(modifier = GlanceModifier.defaultWeight())
        
        // Image Button (Pink)
        val imageIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_IMAGE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        QuickActionButton(
            iconRes = R.drawable.ic_custom_image,
            intent = imageIntent,
            bgColor = GlanceTheme.colors.tertiaryContainer,
            iconTintGlow = GlanceTheme.colors.onTertiaryContainer,
            label = "Media"
        )
        
        Spacer(modifier = GlanceModifier.defaultWeight())
        
        // Code Button (Green)
        val codeIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_CODE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        QuickActionButton(
            iconRes = R.drawable.ic_custom_code,
            intent = codeIntent,
            bgColor = GlanceTheme.colors.secondaryContainer,
            iconTintGlow = GlanceTheme.colors.onSecondaryContainer,
            label = "Code"
        )
    }
}

@androidx.compose.runtime.Composable
fun QuickActionButton(
    iconRes: Int,
    intent: Intent,
    bgColor: androidx.glance.unit.ColorProvider,
    iconTintGlow: androidx.glance.unit.ColorProvider,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(40.dp)
                .background(bgColor)
                .cornerRadius(20.dp) // Circular button
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(16.dp)
            )
        }
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = label,
            style = TextStyle(
                color = iconTintGlow,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}


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
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.repository.SecondBrainRepository
import com.example.ui.theme.*
import com.example.utils.AnalyticsHelper

class RecentItemsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentItemsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AnalyticsHelper.logWidgetAdded(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.update(context)
    }
}

class RecentItemsRefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdater.update(context)
    }
}

class RecentItemsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(150.dp, 110.dp),
            DpSize(250.dp, 200.dp),
            DpSize(300.dp, 300.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = SecondBrainRepository(context)
        var isFromCache = false
        var isTimeout = false

        val items = try {
            val fetched = kotlinx.coroutines.withTimeoutOrNull(3000) { repository.getAllItems() }
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
            GlanceTheme(colors = getWidgetColorProviders()) {
                RecentItemsContent(items = items, isFromCache = isFromCache, isTimeout = isTimeout)
            }
        }
    }
}

@Composable
fun RecentItemsContent(items: List<SavedItem>, isFromCache: Boolean, isTimeout: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(
                imageProvider = ImageProvider(R.drawable.widget_bg_rounded_24),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.background)
            )
            .appWidgetBackground()
            .padding(12.dp)
    ) {
        RecentItemsHeader(isFromCache = isFromCache, isTimeout = isTimeout)
        Spacer(modifier = GlanceModifier.height(10.dp))

        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Your archive is empty",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items.take(20)) { item ->
                    RecentItemRow(item = item)
                    Spacer(modifier = GlanceModifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun RecentItemsHeader(isFromCache: Boolean, isTimeout: Boolean) {
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
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GlanceTheme.colors.onBackground
                    )
                )
                if (isFromCache || isTimeout) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    val badgeBg = if (isTimeout) GlanceTheme.colors.error else GlanceTheme.colors.secondary
                    val badgeFg = if (isTimeout) GlanceTheme.colors.onError else GlanceTheme.colors.onSecondary
                    val badgeText = if (isTimeout) "Offline" else "Cached"
                    Box(
                        modifier = GlanceModifier
                            .background(
                                imageProvider = ImageProvider(R.drawable.widget_bg_rounded_12),
                                colorFilter = ColorFilter.tint(badgeBg)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp,
                                color = badgeFg
                            )
                        )
                    }
                }
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "Your Second Brain",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        // Circular Refresh Button
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(
                    imageProvider = ImageProvider(R.drawable.widget_bg_oval),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant)
                )
                .clickable(actionRunCallback<RecentItemsRefreshCallback>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_custom_sync),
                contentDescription = "Refresh",
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
            )
        }
    }
}

@Composable
fun RecentItemRow(item: SavedItem) {
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

    val (iconRes, categoryColor) = when (item.type) {
        SavedItemType.LINK -> Pair(R.drawable.ic_custom_link, CategoryLink)
        SavedItemType.IMAGE, SavedItemType.VIDEO -> Pair(R.drawable.ic_custom_image, CategoryImage)
        SavedItemType.CODE -> Pair(R.drawable.ic_custom_code, CategoryCode)
        SavedItemType.AUDIO -> Pair(R.drawable.ic_custom_voice, CategoryAudio)
        else -> Pair(R.drawable.ic_custom_text, CategoryText)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                imageProvider = ImageProvider(R.drawable.widget_bg_rounded_16),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.surface)
            )
            .clickable(actionStartActivity(openIntent))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular Category Chip
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(
                    imageProvider = ImageProvider(R.drawable.widget_bg_oval),
                    colorFilter = ColorFilter.tint(ColorProvider(categoryColor))
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.surface)
            )
        }

        Spacer(modifier = GlanceModifier.width(12.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.title.ifBlank { item.type.displayName },
                maxLines = 1,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp
                )
            )
            val subText = item.content.ifBlank { item.extractedText ?: "" }
            if (subText.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = subText,
                    maxLines = 1,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

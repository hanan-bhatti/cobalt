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

class SecondBrainWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SecondBrainWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
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
            val fetched = kotlinx.coroutines.withTimeoutOrNull(2000) {
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
                // Elegant premium dark obsidian card design with vibrant neon accent highlights
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(18.dp)
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
                    
                    // High-contrast, beautifully color-coded quick action panel
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
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Glowing brand badge in marker orange or warning colors
        val badgeColor = if (isTimeout) Color(0xFFD84315) else if (isFromCache) Color(0xFFEF6C00) else Color(0xFFFF6F1E)
        val badgeText = if (isTimeout) "TIMEOUT" else if (isFromCache) "CACHED" else "BRAIN"
        
        Box(
            modifier = GlanceModifier
                .background(badgeColor)
                .cornerRadius(6.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = badgeText,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
        }
        
        Spacer(modifier = GlanceModifier.width(8.dp))
        
        Text(
            text = "Second Brain",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = GlanceTheme.colors.onSurface
            )
        )
        
        Spacer(modifier = GlanceModifier.defaultWeight())
        
        val addIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_TEXT"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        // Plus action with a bright orange background glow
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .background(Color(0x22FF6F1E)) // 15% opacity orange
                .cornerRadius(14.dp)
                .clickable(actionStartActivity(addIntent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
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
        SavedItemType.LINK -> Pair(R.drawable.ic_widget_link, GlanceTheme.colors.primaryContainer)
        SavedItemType.IMAGE, SavedItemType.VIDEO -> Pair(R.drawable.ic_widget_image, GlanceTheme.colors.tertiaryContainer)
        SavedItemType.CODE -> Pair(R.drawable.ic_widget_code, GlanceTheme.colors.secondaryContainer)
        else -> Pair(R.drawable.ic_widget_text, GlanceTheme.colors.primaryContainer)
    }
    
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surface) // Adaptive background
            .cornerRadius(10.dp)
            .clickable(actionStartActivity(openIntent))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High contrast colored round icon container
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
            iconRes = R.drawable.ic_widget_link,
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
            iconRes = R.drawable.ic_widget_image,
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
            iconRes = R.drawable.ic_widget_code,
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
                .cornerRadius(12.dp)
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


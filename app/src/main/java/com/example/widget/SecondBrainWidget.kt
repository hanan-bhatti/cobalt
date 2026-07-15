package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
            // Elegant premium dark obsidian card design with vibrant neon accent highlights
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF121212)) // Dark sleek Obsidian base
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
                                    color = androidx.glance.color.ColorProvider(day = Color(0xFF9E9E9E), night = Color(0xFF9E9E9E)),
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
                    color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White)
                )
            )
        }
        
        Spacer(modifier = GlanceModifier.width(8.dp))
        
        Text(
            text = "Second Brain",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White)
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
    val openIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = android.net.Uri.parse("secondbrain://item/${item.id}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    
    // Choose beautiful color accents and icons based on type
    val (iconRes, iconTintBg) = when (item.type) {
        SavedItemType.LINK -> Pair(R.drawable.ic_widget_link, Color(0x1A3B82F6)) // Sky Sticker Blue
        SavedItemType.IMAGE, SavedItemType.VIDEO -> Pair(R.drawable.ic_widget_image, Color(0x1AFF66CF)) // Bubblegum Pink
        SavedItemType.CODE -> Pair(R.drawable.ic_widget_code, Color(0x1A22C55E)) // Sprout Green
        else -> Pair(R.drawable.ic_widget_text, Color(0x1AFF6F1E)) // Marker Orange
    }
    
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E)) // Obsidian card background
            .cornerRadius(10.dp)
            .clickable(actionStartActivity(openIntent))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High contrast colored round icon container
        Box(
            modifier = GlanceModifier
                .size(24.dp)
                .background(iconTintBg)
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
                    color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                    fontSize = 11.sp
                )
            )
            val subText = item.content.ifBlank { item.extractedText ?: "" }
            if (subText.isNotBlank()) {
                Text(
                    text = subText,
                    maxLines = 1,
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color(0xFF9A9A9A), night = Color(0xFF9A9A9A)),
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
                color = androidx.glance.color.ColorProvider(day = Color(0xFF6F6F6F), night = Color(0xFF6F6F6F)),
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
            bgColor = Color(0xFF102A43), // Deep blue surface
            iconTintGlow = Color(0xFF3B82F6), // Glowing neon blue
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
            bgColor = Color(0xFF3B102F), // Deep pink surface
            iconTintGlow = Color(0xFFFF66CF), // Glowing neon pink
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
            bgColor = Color(0xFF103A24), // Deep green surface
            iconTintGlow = Color(0xFF22C55E), // Glowing neon green
            label = "Code"
        )
    }
}

@androidx.compose.runtime.Composable
fun QuickActionButton(
    iconRes: Int, 
    intent: Intent, 
    bgColor: Color, 
    iconTintGlow: Color, 
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
                color = androidx.glance.color.ColorProvider(day = iconTintGlow, night = iconTintGlow),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}


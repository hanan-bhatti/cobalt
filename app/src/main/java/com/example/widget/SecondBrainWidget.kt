package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.TextDefaults
import com.example.MainActivity
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.repository.SecondBrainRepository
import kotlinx.coroutines.flow.first
import androidx.glance.appwidget.cornerRadius

class SecondBrainWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SecondBrainWidget()
}

class SecondBrainWidget : GlanceAppWidget() {
    
    override val sizeMode = SizeMode.Exact
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = SecondBrainRepository(context)
        val items = try {
            repository.getAllItemsFlow().first()
        } catch (e: Exception) {
            emptyList()
        }

        provideContent {
            val size = LocalSize.current
            val isSmall = size.height < 140.dp
            
            // Minimalist aesthetic: cream background, rounded corners (16dp), thin hairline border not possible easily in Glance root, but we can do background.
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFFFDFBF9))
                    .cornerRadius(16.dp)
                    .padding(12.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    WidgetHeader()
                    
                    Spacer(modifier = GlanceModifier.height(12.dp))
                    
                    if (items.isEmpty()) {
                        Text(
                            text = "Share something to get started",
                            style = TextStyle(
                                color = androidx.glance.color.ColorProvider(day = Color(0xFF171717).copy(alpha = 0.6f), night = Color(0xFF171717).copy(alpha = 0.6f)),
                                fontSize = 14.sp
                            ),
                            modifier = GlanceModifier.padding(top = 8.dp)
                        )
                    } else {
                        if (isSmall) {
                            // Small layout: 1 item + 3 quick actions
                            val lastItem = items.first()
                            ItemRow(lastItem)
                            
                            Spacer(modifier = GlanceModifier.defaultWeight())
                            
                            QuickActionRow()
                        } else {
                            // Large layout: up to 5 items, scrollable if needed
                            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                                items(items.take(5)) { item ->
                                    ItemRow(item)
                                    Spacer(modifier = GlanceModifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun WidgetHeader() {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Second Brain",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = androidx.glance.color.ColorProvider(day = Color(0xFF2B1A07), night = Color(0xFF2B1A07))
            )
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        
        val addIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_TEXT"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        Image(
            provider = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = "Add",
            modifier = GlanceModifier
                .size(24.dp)
                .clickable(actionStartActivity(addIntent))
        )
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
    
    val iconRes = when (item.type) {
        SavedItemType.LINK -> R.drawable.ic_widget_link
        SavedItemType.IMAGE, SavedItemType.VIDEO -> R.drawable.ic_widget_image
        SavedItemType.CODE -> R.drawable.ic_widget_code
        else -> R.drawable.ic_widget_text
    }
    
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(openIntent))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(16.dp)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = item.title.ifBlank { item.content },
            maxLines = 1,
            style = TextStyle(
                color = androidx.glance.color.ColorProvider(day = Color(0xFF171717), night = Color(0xFF171717)),
                fontSize = 14.sp
            ),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }
}

@androidx.compose.runtime.Composable
fun QuickActionRow() {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Link
        val linkIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_LINK"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        QuickActionButton(R.drawable.ic_widget_link, linkIntent)
        
        Spacer(modifier = GlanceModifier.width(16.dp))
        
        // Image
        val imageIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_IMAGE" // assuming this exists or handled similarly
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        QuickActionButton(R.drawable.ic_widget_image, imageIntent)
        
        Spacer(modifier = GlanceModifier.width(16.dp))
        
        // Code
        val codeIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_CODE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        QuickActionButton(R.drawable.ic_widget_code, codeIntent)
    }
}

@androidx.compose.runtime.Composable
fun QuickActionButton(iconRes: Int, intent: Intent) {
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .background(Color(0xFFF7EFE9)) // Dew Drop surface
            .cornerRadius(12.dp)
            .clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp)
        )
    }
}

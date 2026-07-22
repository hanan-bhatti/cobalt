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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import com.example.MainActivity
import com.example.R
import com.example.utils.AnalyticsHelper

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.example.ui.theme.CategoryAudio
import com.example.ui.theme.CategoryCode
import com.example.ui.theme.CategoryImage
import com.example.ui.theme.CategoryLink
import com.example.ui.theme.CategoryMedia
import com.example.ui.theme.CategoryText
import com.example.ui.theme.toThemeColor

class QuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCaptureWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AnalyticsHelper.logWidgetAdded(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

class QuickCaptureWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (e: Exception) {
            -1
        }

        val prefs = context.getSharedPreferences("second_brain_settings", Context.MODE_PRIVATE)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val quickIds = appWidgetManager.getAppWidgetIds(ComponentName(context, QuickCaptureWidgetReceiver::class.java))
        val slotIndex = quickIds.indexOf(appWidgetId).let { if (it >= 0) it else 0 }

        val actionType = prefs.getString("quick_capture_action_$slotIndex", null)
            ?: prefs.getString("quick_capture_action", "TEXT")
            ?: "TEXT"

        provideContent {
            GlanceTheme(colors = getWidgetColorProviders()) {
                QuickCaptureContent(actionType = actionType)
            }
        }
    }
}

@Composable
fun QuickCaptureContent(actionType: String = "TEXT") {
    val context = LocalContext.current
    val isNightMode = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    val (iconRes, intentAction, desc, colorProvider) = when (actionType) {
        "LINK" -> Quadruple(R.drawable.ic_custom_link, "com.example.ACTION_QUICK_LINK", "Quick Add Link", ColorProvider(CategoryLink.toThemeColor(isNightMode)))
        "IMAGE" -> Quadruple(R.drawable.ic_custom_image, "com.example.ACTION_QUICK_IMAGE", "Quick Capture Photo", ColorProvider(CategoryImage.toThemeColor(isNightMode)))
        "AUDIO" -> Quadruple(R.drawable.ic_custom_voice, "com.example.ACTION_QUICK_AUDIO", "Quick Voice Memo", ColorProvider(CategoryAudio.toThemeColor(isNightMode)))
        "CODE" -> Quadruple(R.drawable.ic_custom_code, "com.example.ACTION_QUICK_CODE", "Quick Add Code", ColorProvider(CategoryCode.toThemeColor(isNightMode)))
        "MEDIA" -> Quadruple(R.drawable.ic_custom_movie, "com.example.ACTION_QUICK_MEDIA", "Quick Add Movie / Show", ColorProvider(CategoryMedia.toThemeColor(isNightMode)))
        "OCR" -> Quadruple(R.drawable.ic_custom_ocr, "com.example.ACTION_QUICK_OCR", "Quick Screen OCR", GlanceTheme.colors.primary)
        else -> Quadruple(R.drawable.ic_custom_text, "com.example.ACTION_QUICK_TEXT", "Quick Add Note", ColorProvider(CategoryText.toThemeColor(isNightMode)))
    }

    val targetIntent = Intent(context, MainActivity::class.java).apply {
        action = intentAction
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(
                imageProvider = ImageProvider(R.drawable.widget_bg_oval),
                colorFilter = ColorFilter.tint(colorProvider)
            )
            .clickable(actionStartActivity(targetIntent)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = desc,
            modifier = GlanceModifier.size(32.dp),
            colorFilter = ColorFilter.tint(ColorProvider(Color.White))
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

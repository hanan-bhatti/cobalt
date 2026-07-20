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

class QuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCaptureWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AnalyticsHelper.logWidgetAdded(context)
    }
}

class QuickCaptureWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = getWidgetColorProviders()) {
                QuickCaptureContent()
            }
        }
    }
}

@Composable
fun QuickCaptureContent() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("second_brain_settings", Context.MODE_PRIVATE)
    val actionType = prefs.getString("quick_capture_action", "TEXT") ?: "TEXT"

    val (iconRes, intentAction, desc) = when (actionType) {
        "LINK" -> Triple(R.drawable.ic_custom_link, "com.example.ACTION_QUICK_LINK", "Quick Add Link")
        "IMAGE" -> Triple(R.drawable.ic_custom_image, "com.example.ACTION_QUICK_IMAGE", "Quick Capture Photo")
        "AUDIO" -> Triple(R.drawable.ic_custom_voice, "com.example.ACTION_QUICK_AUDIO", "Quick Voice Memo")
        "CODE" -> Triple(R.drawable.ic_custom_code, "com.example.ACTION_QUICK_CODE", "Quick Add Code")
        "OCR" -> Triple(R.drawable.ic_custom_ocr, "com.example.ACTION_QUICK_OCR", "Quick Screen OCR")
        else -> Triple(R.drawable.ic_custom_text, "com.example.ACTION_QUICK_TEXT", "Quick Add Note")
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
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
            )
            .clickable(actionStartActivity(targetIntent)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = desc,
            modifier = GlanceModifier.size(32.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary)
        )
    }
}

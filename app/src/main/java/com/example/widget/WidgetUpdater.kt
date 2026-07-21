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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUpdater {
    fun update(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val glanceManager = GlanceAppWidgetManager(context)

                val quickGlanceIds = glanceManager.getGlanceIds(QuickCaptureWidget::class.java)
                val quickWidget = QuickCaptureWidget()
                quickGlanceIds.forEach { glanceId ->
                    try { androidx.glance.appwidget.state.updateAppWidgetState(context, glanceId) { } } catch (e: Exception) {}
                    quickWidget.update(context, glanceId)
                }
                quickWidget.updateAll(context)

                val recentGlanceIds = glanceManager.getGlanceIds(RecentItemsWidget::class.java)
                val recentWidget = RecentItemsWidget()
                recentGlanceIds.forEach { glanceId ->
                    try { androidx.glance.appwidget.state.updateAppWidgetState(context, glanceId) { } } catch (e: Exception) {}
                    recentWidget.update(context, glanceId)
                }
                recentWidget.updateAll(context)

                val appWidgetManager = AppWidgetManager.getInstance(context)

                val recentComponent = ComponentName(context, RecentItemsWidgetReceiver::class.java)
                val recentIds = appWidgetManager.getAppWidgetIds(recentComponent)
                if (recentIds.isNotEmpty()) {
                    val updateIntent = Intent(context, RecentItemsWidgetReceiver::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, recentIds)
                    }
                    context.sendBroadcast(updateIntent)
                }

                val quickComponent = ComponentName(context, QuickCaptureWidgetReceiver::class.java)
                val quickIds = appWidgetManager.getAppWidgetIds(quickComponent)
                if (quickIds.isNotEmpty()) {
                    val updateIntent = Intent(context, QuickCaptureWidgetReceiver::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, quickIds)
                    }
                    context.sendBroadcast(updateIntent)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}

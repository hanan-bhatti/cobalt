package com.example.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUpdater {
    fun update(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SecondBrainWidget().updateAll(context)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}

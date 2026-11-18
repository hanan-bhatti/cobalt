package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.screens.ScreeningWidgetSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CobaltViewModel

class ScreeningConfigurationActivity : ComponentActivity() {
    private val viewModel: CobaltViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        if (getSharedPreferences("cobalt_settings", MODE_PRIVATE).getBoolean("hide_recents", false)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id <= 0 || AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider != ComponentName(this, ScreeningWidgetProvider::class.java)) { finish(); return }
        val paused = com.example.notes.StartupCrashGuard.shouldPause(this) || com.example.notes.NoteMigrationManager.shouldPauseBackground(this)
        setContent {
            val mode by viewModel.settingsRepository.themeMode.collectAsState()
            val dynamic by viewModel.settingsRepository.dynamicColor.collectAsState()
            val typography by viewModel.settingsRepository.typographyPrefs.collectAsState()
            val dark = when (mode) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
            MyApplicationTheme(darkTheme = dark, dynamicColor = dynamic, typographyPrefs = typography) {
                if (paused) AlertDialog(onDismissRequest = { finish() }, title = { Text("Open Cobalt first") },
                    text = { Text("Finish note recovery or migration in Cobalt before setting up your widget.") },
                    confirmButton = { TextButton(onClick = { finish() }) { Text("Close") } })
                else ScreeningWidgetSettingsScreen(viewModel, onNavigateBack = { finish() }, configurationId = id, onConfigured = {
                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                })
            }
        }
    }
}

package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.notes.TextFileIntents
import com.example.ui.screens.CaptureScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Review Android shares in the normal Capture UI. Only explicit Save inserts an archive item. */
class QuickShareActivity : ComponentActivity() {
    private val viewModel: CobaltViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.example.notes.StartupCrashGuard.isSafeMode || com.example.notes.NoteMigrationManager.shouldPauseBackground(this)) {
            startActivity(Intent(intent).setClass(this, MainActivity::class.java).putExtra(REVIEW_AFTER_GATE, true))
            finish()
            return
        }
        if (getSharedPreferences("cobalt_settings", MODE_PRIVATE).getBoolean("hide_recents", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        val received = intent
        if (received.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)) { finish(); return }
        val uris = TextFileIntents.uris(received).distinct()
        val count = uris.size.coerceAtLeast(1)
        setContent {
            val mode by viewModel.settingsRepository.themeMode.collectAsState()
            val dynamic by viewModel.settingsRepository.dynamicColor.collectAsState()
            val fonts by viewModel.typographyPrefs.collectAsState()
            val dark = when (mode) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
            MyApplicationTheme(darkTheme = dark, dynamicColor = dynamic, typographyPrefs = fonts) {
                val item by viewModel.activeCaptureItem.collectAsState()
                val preparing by viewModel.sharedCaptureState.collectAsState()
                val saving by viewModel.isSaving.collectAsState()
                val savedId by viewModel.lastCaptureSavedId.collectAsState()
                var index by rememberSaveable { mutableIntStateOf(0) }
                var reviewedId by rememberSaveable { mutableStateOf<String?>(null) }
                var retry by remember { mutableIntStateOf(0) }
                LaunchedEffect(index, retry) {
                    if (item != null || preparing.preparing || reviewedId != null) return@LaunchedEffect
                    if (count > 100) return@LaunchedEffect
                    val uri: Uri? = uris.getOrNull(index)
                    val type = withContext(Dispatchers.IO) {
                        uri?.let { contentResolver.getType(it) } ?: received.type ?: "text/plain"
                    }
                    val document = uri != null && withContext(Dispatchers.IO) { TextFileIntents.isTextFile(received, type) }
                    if (document) viewModel.prepareSharedDocument(requireNotNull(uri))
                    else viewModel.handleSharedIntent(type, received.getStringExtra(Intent.EXTRA_TEXT), uri,
                        received.getStringExtra(Intent.EXTRA_SUBJECT))
                }
                LaunchedEffect(item?.id, preparing.preparing, saving, savedId) {
                    if (item != null) reviewedId = item?.id
                    else if (reviewedId != null && !preparing.preparing && !saving) {
                        if (savedId == reviewedId && index + 1 < count) { reviewedId = null; index++ }
                        else finish()
                    }
                }
                BackHandler(enabled = !saving) { viewModel.cancelCapture(); finish() }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (item != null) {
                        Column {
                            if (count > 1) Text("Shared item ${index + 1} of $count", Modifier.statusBarsPadding().padding(16.dp),
                                style = MaterialTheme.typography.labelLarge)
                            CaptureScreen(viewModel = viewModel)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text("Review shared content", style = MaterialTheme.typography.headlineSmall)
                            if (count > 100) Text("Share up to 100 items at once. Nothing was saved.")
                            else if (preparing.error != null) {
                                Text(preparing.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                Button(onClick = { retry++ }) { Text("Try again") }
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Preparing a preview. Save it when you're ready.")
                            }
                            OutlinedButton(onClick = { viewModel.cancelCapture(); finish() }) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
    companion object { const val REVIEW_AFTER_GATE = "review_shared_content_after_gate" }
}

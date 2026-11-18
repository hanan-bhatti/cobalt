package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.viewmodel.CobaltViewModel

/** Existing navigation route retained for saved navigation state. */
@Composable
fun GeminiUsageScreen(viewModel: CobaltViewModel, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    AiUsageSettingsScreen(onNavigateBack)
}

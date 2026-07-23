package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class FeedbackSeverity {
    SUCCESS,
    INFO,
    WARNING,
    ERROR
}

data class UserFeedbackMessage(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val severity: FeedbackSeverity = FeedbackSeverity.INFO,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 4000L
) {
    val icon: ImageVector
        get() = when (severity) {
            FeedbackSeverity.SUCCESS -> Icons.Default.CheckCircle
            FeedbackSeverity.INFO -> Icons.Default.Info
            FeedbackSeverity.WARNING -> Icons.Default.Warning
            FeedbackSeverity.ERROR -> Icons.Default.Error
        }
}

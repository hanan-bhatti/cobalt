package com.example.util

import androidx.annotation.DrawableRes
import com.example.R

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
    @get:DrawableRes
    val iconRes: Int
        get() = when (severity) {
            FeedbackSeverity.SUCCESS -> R.drawable.ic_custom_success_circle
            FeedbackSeverity.INFO -> R.drawable.ic_custom_info
            FeedbackSeverity.WARNING -> R.drawable.ic_custom_warning_triangle
            FeedbackSeverity.ERROR -> R.drawable.ic_custom_error_circle
        }
}

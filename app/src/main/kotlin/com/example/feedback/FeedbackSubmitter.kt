package com.example.feedback

import android.content.Context

data class FeedbackPayload(
    val type: String, // "BUG_REPORT" or "FEATURE_REQUEST"
    val title: String,
    val description: String,
    val userEmail: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String
)

interface FeedbackSubmitter {
    suspend fun submitFeedback(context: Context, payload: FeedbackPayload): Result<Unit>
}

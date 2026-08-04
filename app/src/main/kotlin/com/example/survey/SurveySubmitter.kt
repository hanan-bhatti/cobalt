package com.example.survey

import android.content.Context

data class SurveyPayload(
    val userEmail: String,
    val userId: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val reaction: String,
    val favoriteFeatures: List<String>,
    val desiredImprovements: List<String>,
    val npsScore: Int,
    val customFeedback: String
)

interface SurveySubmitter {
    suspend fun submitSurvey(context: Context, payload: SurveyPayload): Result<Unit>
}

package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified Submission Manager for Bug Reports, Feature Requests, and In-App Surveys.
 */
object FeedbackSubmissionManager {

    suspend fun submitBugReport(
        context: Context,
        title: String,
        description: String,
        steps: List<String>,
        attachmentUriStr: String?,
        userEmail: String,
        userId: String,
        deviceModel: String,
        osVersion: String,
        appVersion: String,
        uploadCloudAttachment: suspend (Uri) -> String?,
        submitToCloud: suspend (String, Map<String, Any?>) -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        var finalAttachmentUrl = ""
        
        if (!attachmentUriStr.isNullOrBlank()) {
            val uri = Uri.parse(attachmentUriStr)
            try {
                // Production fix: Validate file size (Max 25MB)
                val fileSize = getFileSize(context, uri)
                if (fileSize > 25 * 1024 * 1024) {
                    throw Exception("Attachment exceeds 25MB limit.")
                }
                finalAttachmentUrl = uploadCloudAttachment(uri) ?: ""
            } catch (e: Exception) {
                throw Exception("Failed to upload attachment: ${e.message}")
            }
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val payload = mapOf(
            "type" to "BUG_REPORT",
            "title" to title,
            "description" to description,
            "steps" to steps,
            "attachmentUrl" to finalAttachmentUrl, // Uploaded remote URL, not local URI
            "userEmail" to userEmail,
            "userId" to userId,
            "deviceModel" to deviceModel,
            "osVersion" to osVersion,
            "appVersion" to appVersion,
            "timestamp" to timestamp
        )

        saveLocally(context, "bug_reports.json", payload)
        
        val cloudSuccess = submitToCloud("bug_reports", payload)
        if (!cloudSuccess) {
            throw Exception("Network error or permissions denied when submitting to cloud.")
        }
        return@withContext true
    }

    suspend fun submitFeatureRequest(
        context: Context,
        title: String,
        problemStatement: String,
        proposedSolution: String,
        priority: String,
        userConsent: Boolean,
        userEmail: String,
        userId: String,
        deviceModel: String,
        osVersion: String,
        appVersion: String,
        submitToCloud: suspend (String, Map<String, Any?>) -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val payload = mapOf(
            "type" to "FEATURE_REQUEST",
            "title" to title,
            "problemStatement" to problemStatement,
            "proposedSolution" to proposedSolution,
            "priority" to priority,
            "userConsent" to userConsent,
            "userEmail" to userEmail,
            "userId" to userId,
            "deviceModel" to deviceModel,
            "osVersion" to osVersion,
            "appVersion" to appVersion,
            "timestamp" to timestamp
        )

        saveLocally(context, "feature_requests.json", payload)
        val cloudSuccess = submitToCloud("feature_requests", payload)
        if (!cloudSuccess) {
            throw Exception("Network error or permissions denied when submitting to cloud.")
        }
        return@withContext true
    }

    suspend fun submitSurvey(
        context: Context,
        reaction: String,
        favoriteFeatures: List<String>,
        desiredImprovements: List<String>,
        npsScore: Int,
        customFeedback: String,
        userEmail: String,
        userId: String,
        deviceModel: String,
        osVersion: String,
        appVersion: String,
        submitToCloud: suspend (String, Map<String, Any?>) -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val payload = mapOf(
            "type" to "SURVEY",
            "reaction" to reaction,
            "favoriteFeatures" to favoriteFeatures,
            "desiredImprovements" to desiredImprovements,
            "npsScore" to npsScore,
            "customFeedback" to customFeedback,
            "userEmail" to userEmail,
            "userId" to userId,
            "deviceModel" to deviceModel,
            "osVersion" to osVersion,
            "appVersion" to appVersion,
            "timestamp" to timestamp
        )

        saveLocally(context, "surveys.json", payload)
        val cloudSuccess = submitToCloud("surveys", payload)
        if (!cloudSuccess) {
            throw Exception("Network error or permissions denied when submitting to cloud.")
        }
        return@withContext true
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FeedbackSubmissionManager", "Failed to get file size", e)
        }
        return size
    }

    private fun saveLocally(context: Context, filename: String, payload: Map<String, Any?>) {
        try {
            val dir = File(context.filesDir, "feedback")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)

            val jsonArray = if (file.exists()) {
                try {
                    JSONArray(file.readText())
                } catch (e: Exception) {
                    JSONArray()
                }
            } else {
                JSONArray()
            }

            val jsonObject = JSONObject()
            payload.forEach { (k, v) ->
                when (v) {
                    is List<*> -> {
                        val arr = JSONArray()
                        v.forEach { arr.put(it.toString()) }
                        jsonObject.put(k, arr)
                    }
                    else -> jsonObject.put(k, v)
                }
            }
            jsonArray.put(jsonObject)
            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            Log.e("FeedbackSubmissionManager", "Failed to save feedback locally: ${e.message}", e)
        }
    }
}

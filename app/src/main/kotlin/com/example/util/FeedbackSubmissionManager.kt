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
        
        val cloudSuccess = try {
            submitToCloud("bug_reports", payload)
        } catch (e: Exception) {
            Log.w("FeedbackManager", "Failed to submit bug report to cloud: ${e.message}")
            false
        }
        if (!cloudSuccess) {
            Log.w("FeedbackManager", "Bug report saved locally; cloud submission was unsuccessful.")
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
        val cloudSuccess = try {
            submitToCloud("feature_requests", payload)
        } catch (e: Exception) {
            Log.w("FeedbackManager", "Failed to submit feature request to cloud: ${e.message}")
            false
        }
        if (!cloudSuccess) {
            Log.w("FeedbackManager", "Feature request saved locally; cloud submission was unsuccessful.")
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
        val cloudSuccess = try {
            submitToCloud("surveys", payload)
        } catch (e: Exception) {
            Log.w("FeedbackManager", "Failed to submit survey to cloud: ${e.message}")
            false
        }
        if (!cloudSuccess) {
            Log.w("FeedbackManager", "Survey saved locally; cloud submission was unsuccessful.")
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

    /**
     * Formats a bug report into clean GitHub / Email markdown.
     */
    fun formatBugReportMarkdown(
        title: String,
        description: String,
        steps: List<String>,
        userEmail: String,
        deviceModel: String,
        osVersion: String,
        appVersion: String
    ): String {
        val nonBlankSteps = steps.filter { it.isNotBlank() }
        val stepsText = if (nonBlankSteps.isNotEmpty()) {
            nonBlankSteps.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n")
        } else {
            "_No specific steps provided_"
        }

        return """
            ### Bug Summary
            $title

            ### Description
            $description

            ### Steps to Reproduce
            $stepsText

            ### Device & Environment Details
            - **App Version:** v$appVersion (FOSS)
            - **Device:** $deviceModel
            - **Android OS:** $osVersion
            - **Reporter:** $userEmail
        """.trimIndent()
    }

    /**
     * Formats a feature request into clean GitHub / Email markdown.
     */
    fun formatFeatureRequestMarkdown(
        title: String,
        problemStatement: String,
        proposedSolution: String,
        priority: String,
        userEmail: String,
        deviceModel: String,
        osVersion: String,
        appVersion: String
    ): String {
        return """
            ### Feature Summary
            $title

            ### Problem Statement
            $problemStatement

            ### Proposed Solution
            $proposedSolution

            ### Priority
            $priority

            ### Device & Environment Details
            - **App Version:** v$appVersion (FOSS)
            - **Device:** $deviceModel
            - **Android OS:** $osVersion
            - **Requester:** $userEmail
        """.trimIndent()
    }

    /**
     * Formats a survey into clean GitHub / Email markdown.
     */
    fun formatSurveyMarkdown(
        reaction: String,
        favoriteFeatures: List<String>,
        desiredImprovements: List<String>,
        npsScore: Int,
        customFeedback: String,
        userEmail: String,
        deviceModel: String,
        osVersion: String,
        appVersion: String
    ): String {
        val favs = if (favoriteFeatures.isNotEmpty()) favoriteFeatures.joinToString(", ") else "None specified"
        val imps = if (desiredImprovements.isNotEmpty()) desiredImprovements.joinToString(", ") else "None specified"
        val comments = if (customFeedback.isNotBlank()) customFeedback else "_No additional feedback entered_"

        return """
            ### Cobalt In-App Survey Response
            - **Experience Sentiment:** $reaction
            - **Net Promoter Score (NPS):** $npsScore / 10
            - **Favorite Features:** $favs
            - **Desired Improvements:** $imps

            ### User Feedback & Ideas
            $comments

            ### Device & Version
            - **App Version:** v$appVersion (FOSS)
            - **Device:** $deviceModel
            - **Android OS:** $osVersion
            - **Respondent:** $userEmail
        """.trimIndent()
    }

    /**
     * Launches the user's browser with a pre-filled GitHub issue URL.
     */
    fun launchGitHubIssue(
        context: Context,
        title: String,
        body: String,
        labels: String = "bug"
    ) {
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedBody = java.net.URLEncoder.encode(body, "UTF-8")
            val encodedLabels = java.net.URLEncoder.encode(labels, "UTF-8")
            val url = "https://github.com/hanan-bhatti/cobalt/issues/new?title=$encodedTitle&body=$encodedBody&labels=$encodedLabels"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("FeedbackSubmissionManager", "Failed to launch GitHub issue: ${e.message}", e)
        }
    }

    /**
     * Launches the user's browser with a pre-filled GitHub discussion URL.
     */
    fun launchGitHubDiscussion(
        context: Context,
        title: String,
        body: String
    ) {
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedBody = java.net.URLEncoder.encode(body, "UTF-8")
            val url = "https://github.com/hanan-bhatti/cobalt/discussions/new?title=$encodedTitle&body=$encodedBody"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("FeedbackSubmissionManager", "Failed to launch GitHub discussion: ${e.message}", e)
        }
    }

    /**
     * Launches the user's email client with pre-filled subject, body, and recipient.
     */
    fun launchEmailClient(
        context: Context,
        subject: String,
        body: String,
        recipient: String = "hananbhatti.dev@gmail.com"
    ) {
        try {
            val mailtoUri = Uri.parse("mailto:$recipient")
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, mailtoUri).apply {
                putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                putExtra(android.content.Intent.EXTRA_TEXT, body)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(recipient))
                    putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                    putExtra(android.content.Intent.EXTRA_TEXT, body)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Send Feedback via Email"))
            } catch (e2: Exception) {
                Log.e("FeedbackSubmissionManager", "Failed to launch email client: ${e2.message}", e2)
            }
        }
    }
}

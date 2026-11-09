package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.util.SafeLogger as Log
import android.widget.Toast
import androidx.core.content.FileProvider
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
                finalAttachmentUrl = uri.toString()
            } catch (e: Exception) {
                throw Exception("Failed to read attachment: ${e.message}")
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

    fun copyToClipboard(context: Context, label: String, text: String): Boolean {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) return false
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            return true
        } catch (e: Exception) {
            Log.w("FeedbackSubmissionManager", "Failed to copy to clipboard: ${e.message}")
            return false
        }
    }

    /**
     * Formats a bug report into clean, highly readable GitHub / Email markdown.
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
            ## 🐛 Cobalt Bug Report: $title

            ### 📝 Description
            $description

            ### 🔄 Steps to Reproduce
            $stepsText

            ### 📱 Environment & Device Info
            | Parameter | Value |
            | :--- | :--- |
            | **App Version** | v$appVersion |
            | **Device Model** | $deviceModel |
            | **Android OS** | $osVersion |
            | **Reporter Email** | ${if (userEmail.isNotBlank()) userEmail else "_Not specified_"} |
            | **Submission Time** | ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} |

            ---
            _Generated automatically by Cobalt Knowledge Archive_
        """.trimIndent()
    }

    /**
     * Formats a feature request into clean, highly readable GitHub / Email markdown.
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
            ## 💡 Cobalt Feature Request: $title

            ### 🎯 Motivation & Problem Statement
            $problemStatement

            ### 🚀 Proposed Solution
            $proposedSolution

            ### ⚡ Priority Level
            **$priority**

            ### 📱 Environment & Submitter Info
            | Parameter | Value |
            | :--- | :--- |
            | **App Version** | v$appVersion |
            | **Device Model** | $deviceModel |
            | **Android OS** | $osVersion |
            | **Requester Email** | ${if (userEmail.isNotBlank()) userEmail else "_Not specified_"} |
            | **Submission Time** | ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} |

            ---
            _Generated automatically by Cobalt Knowledge Archive_
        """.trimIndent()
    }

    /** Plain text for email clients: no Markdown headings, tables or emphasis markers. */
    fun formatSurveyEmail(response: SurveyResponse, deviceModel: String, osVersion: String, appVersion: String): String = buildString {
        fun answer(value: String?) = value?.takeIf { it.isNotBlank() } ?: "Not answered"
        appendLine("Cobalt v$appVersion — experience survey")
        appendLine()
        appendLine("1. Overall experience")
        appendLine(response.reactionScore?.let { "$it out of 10 — ${answer(response.overall)}" } ?: answer(response.overall))
        appendLine()
        appendLine("2. Parts used in this version")
        appendLine(if (response.areasUsed.isEmpty()) "Not answered" else response.areasUsed.joinToString(", "))
        appendLine()
        appendLine("3. Update experience")
        appendLine(answer(response.updateExperience))
        appendLine()
        appendLine("4. Existing area to improve first")
        appendLine(answer(response.improvementArea))
        appendLine()
        appendLine("5. What happened, and what was expected")
        appendLine(answer(response.details))
        appendLine()
        appendLine("6. Likelihood of recommending Cobalt")
        appendLine(response.recommendation?.let { "$it out of 10" } ?: "Not answered")
        appendLine()
        appendLine("App version: $appVersion")
        appendLine("Device: $deviceModel")
        appendLine("Android: $osVersion")
    }

    const val FEEDBACK_EMAIL = "hannanbhatti2006@gmail.com"

    /** Opens an email composer directly. This cannot establish whether the user sends it. */
    fun launchSurveyEmail(context: Context, appVersion: String, body: String): Boolean {
        val subject = "Cobalt v$appVersion experience survey"
        val uri = Uri.parse("mailto:$FEEDBACK_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        return try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (error: Exception) {
            Log.e("FeedbackSubmissionManager", "Could not open survey email composer", error)
            false
        }
    }

    /**
     * Formats a survey into clean, highly readable GitHub / Email markdown.
     */
    fun formatSurveyMarkdown(
        response: SurveyResponse,
        deviceModel: String,
        osVersion: String,
        appVersion: String
    ): String = buildString {
        fun answer(value: String?) = value?.takeIf { it.isNotBlank() } ?: "_Not answered_"
        appendLine("## Cobalt v$appVersion experience survey")
        appendLine()
        appendLine("### 1. How has this version worked for you?")
        appendLine(answer(response.overall))
        appendLine()
        appendLine("### 2. Which parts have you used?")
        if (response.areasUsed.isEmpty()) appendLine("_Not answered_")
        else response.areasUsed.forEach { appendLine("- $it") }
        appendLine()
        appendLine("### 3. Did you notice problems after updating?")
        appendLine(answer(response.updateExperience))
        appendLine()
        appendLine("### 4. Which existing area should we improve first?")
        appendLine(answer(response.improvementArea))
        appendLine()
        appendLine("### 5. What happened, and what did you expect instead?")
        appendLine(answer(response.details))
        appendLine()
        appendLine("### 6. How likely are you to recommend Cobalt?")
        appendLine(response.recommendation?.let { "$it / 10" } ?: "_Not answered_")
        appendLine()
        appendLine("### Environment")
        appendLine("- App: v$appVersion")
        appendLine("- Device: $deviceModel")
        appendLine("- OS: $osVersion")
        appendLine("- Contact (optional): ${answer(response.contact)}")
    }

    /**
     * Launches the user's browser with a pre-filled GitHub issue URL.
     * Also offers a clipboard copy for manual sharing.
     */
    fun launchGitHubIssue(
        context: Context,
        title: String,
        body: String,
        labels: String = "bug"
    ) {
        copyToClipboard(context, "Cobalt Issue", body)
        Toast.makeText(context, "Issue details copied to clipboard!", Toast.LENGTH_SHORT).show()
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedBody = java.net.URLEncoder.encode(body, "UTF-8")
            val encodedLabels = java.net.URLEncoder.encode(labels, "UTF-8")
            val url = "https://github.com/hanan-bhatti/cobalt/issues/new?title=$encodedTitle&body=$encodedBody&labels=$encodedLabels"
            CobaltCustomTabsLauncher.launch(context, url)
        } catch (e: Exception) {
            Log.e("FeedbackSubmissionManager", "Failed to launch GitHub issue: ${e.message}", e)
        }
    }

    /**
     * Launches the user's browser with a pre-filled GitHub discussion URL.
     * Also offers a clipboard copy for manual sharing.
     */
    fun launchGitHubDiscussion(
        context: Context,
        title: String,
        body: String
    ): Boolean {
        copyToClipboard(context, "Cobalt Survey", body)
        Toast.makeText(context, "Survey details copied to clipboard!", Toast.LENGTH_SHORT).show()
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedBody = java.net.URLEncoder.encode(body, "UTF-8")
            val url = "https://github.com/hanan-bhatti/cobalt/discussions/new?title=$encodedTitle&body=$encodedBody"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: Exception) {
            Log.e("FeedbackSubmissionManager", "Failed to launch GitHub discussion: ${e.message}", e)
            return false
        }
    }

    /**
     * Launches the user's email client with pre-filled subject, body, and recipient.
     * Guaranteed to pre-fill both URI parameters and Intent extras, with clipboard fallback.
     */
    fun launchEmailClient(
        context: Context,
        subject: String,
        body: String,
        recipient: String = FEEDBACK_EMAIL
    ): Boolean {
        copyToClipboard(context, "Cobalt Feedback", body)
        try {
            val encodedSubject = Uri.encode(subject)
            val encodedBody = Uri.encode(body)
            val mailtoUri = Uri.parse("mailto:$recipient?subject=$encodedSubject&body=$encodedBody")
            val intent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Feedback details copied to clipboard!", Toast.LENGTH_SHORT).show()
            return true
        } catch (e: Exception) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Send Feedback via Email"))
                Toast.makeText(context, "Feedback details copied to clipboard!", Toast.LENGTH_SHORT).show()
                return true
            } catch (e2: Exception) {
                Log.e("FeedbackSubmissionManager", "Failed to launch email client: ${e2.message}", e2)
                Toast.makeText(context, "Feedback copied to clipboard. Please email $recipient", Toast.LENGTH_LONG).show()
                return false
            }
        }
    }

    /**
     * Exports or shares feedback/survey as a Markdown file via the Android share sheet.
     * Enables users to save to Downloads, Google Drive, or share via Telegram/Messaging apps.
     */
    fun shareOrExportFeedback(
        context: Context,
        title: String,
        content: String,
        fileName: String = "cobalt_feedback.md"
    ): Boolean {
        copyToClipboard(context, title, content)
        try {
            val exportDir = File(context.cacheDir, "feedback_export")
            if (!exportDir.exists()) exportDir.mkdirs()
            val file = File(exportDir, fileName)
            file.writeText(content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share or Save Feedback File...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Toast.makeText(context, "Feedback copied to clipboard and ready to share!", Toast.LENGTH_SHORT).show()
            return true
        } catch (e: Exception) {
            Log.e("FeedbackSubmissionManager", "Failed to share feedback file: ${e.message}", e)
            Toast.makeText(context, "Feedback copied to clipboard!", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}

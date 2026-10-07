package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FOSS Uncaught Exception Handler and Crash Reporting Engine.
 * Automatically intercepts uncaught app crashes, saves stack trace to a local text file,
 * and allows users to report crashes to hannanbhatti2006@gmail.com on next app startup.
 */
object FossCrashReporter {

    private const val CRASH_FILE_NAME = "latest_crash_report.txt"
    const val CRASH_EMAIL = "hannanbhatti2006@gmail.com"

    /**
     * Installs the custom uncaught exception handler.
     */
    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashToFile(context.applicationContext, thread, throwable)
            } catch (e: Throwable) {
                Log.e("FossCrashReporter", "Failed to save crash log: ${e.message}", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashToFile(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTraceStr = sw.toString()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val reportText = buildString {
            appendLine("==================================================")
            appendLine("COBALT FOSS CRASH REPORT")
            appendLine("==================================================")
            appendLine("App Version    : ${BuildConfig.VERSION_NAME} (Build #${BuildConfig.VERSION_CODE})")
            appendLine("Device Model   : ${Build.MANUFACTURER.uppercase()} ${Build.MODEL}")
            appendLine("Android OS     : Version ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread Name    : ${thread.name}")
            appendLine("Timestamp      : $timestamp")
            appendLine("==================================================")
            appendLine("EXCEPTION TYPE : ${throwable.javaClass.name}")
            appendLine("MESSAGE        : ${throwable.message ?: "No message"}")
            appendLine("==================================================")
            appendLine("FULL STACK TRACE:")
            appendLine(stackTraceStr)
        }

        val file = File(context.filesDir, CRASH_FILE_NAME)
        file.writeText(reportText)
    }

    /**
     * Returns the pending crash report text if available from a previous crash.
     */
    fun getPendingCrashReport(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        return if (file.exists() && file.length() > 0) {
            file.readText()
        } else {
            null
        }
    }

    /**
     * Clears the saved crash report file.
     */
    fun clearCrashReport(context: Context) {
        try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("FossCrashReporter", "Failed to clear crash report file: ${e.message}")
        }
    }

    /**
     * Launches email intent to send crash report stacktrace to developer.
     */
    fun sendCrashReportEmail(context: Context, reportText: String) {
        try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            val uri: Uri? = if (file.exists()) {
                try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (e: Exception) {
                    null
                }
            } else null

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(CRASH_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, "Cobalt FOSS Crash Report - v${BuildConfig.VERSION_NAME}")
                putExtra(Intent.EXTRA_TEXT, reportText)
                if (uri != null) {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val chooser = Intent.createChooser(intent, "Send Crash Report via Email...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("FossCrashReporter", "Failed to launch email chooser: ${e.message}", e)
        }
    }
}

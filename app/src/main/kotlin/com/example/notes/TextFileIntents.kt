package com.example.notes

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Some providers use EXTRA_STREAM, some ClipData, and document viewers use data. */
object TextFileIntents {
    fun uris(intent: Intent): List<Uri> {
        val streams = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        if (streams.isNotEmpty()) return streams.distinct()
        val clips = intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
        return (clips + listOfNotNull(intent.data?.takeIf { it.scheme == "content" })).distinct()
    }
    fun isTextFile(intent: Intent, mime: String?): Boolean = uris(intent).isNotEmpty() &&
        (TextFiles.accepts(null, mime) || mime == "application/octet-stream" || mime == "*/*" || mime == null)
}

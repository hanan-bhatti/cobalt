/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Zero-lag Share Sheet Receiver.
 * Receives shared URLs, text, images, and videos from the Android system share sheet,
 * persists an optimistic record immediately, finishes in < 80ms, and enriches
 * metadata / persists files in the background without user interruption.
 */
class QuickShareActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val action = intent?.action
        val type = intent?.type

        if ((Intent.ACTION_SEND == action || Intent.ACTION_SEND_MULTIPLE == action) && type != null) {
            handleQuickShare(intent, type)
        } else {
            finish()
        }
    }

    private fun handleQuickShare(intent: Intent, mimeType: String) {
        val appContext = applicationContext
        val textContent = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val mediaUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        when {
            // 1. Text or URL Shared
            mimeType.startsWith("text/") && textContent != null -> {
                val urlRegex = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))".toRegex()
                val match = urlRegex.find(textContent)
                val url = match?.value

                if (url != null) {
                    val initialTitle = subject?.takeIf { it.isNotBlank() } ?: "Shared Link"
                    val entity = SavedItemEntity(
                        id = id,
                        type = SavedItemType.LINK.name,
                        title = initialTitle,
                        content = url,
                        timestamp = now,
                        foldersJson = "[]",
                        extractedText = null,
                        thumbnailPath = null,
                        linkTitle = initialTitle
                    )

                    persistAndNotify("Saved link to Cobalt!", entity)

                    // Background enrichment for OpenGraph preview
                    activityScope.launch {
                        try {
                            val metadata = fetchLinkMetadata(url)
                            if (!metadata.title.isNullOrBlank() || !metadata.description.isNullOrBlank() || !metadata.imageUrl.isNullOrBlank()) {
                                val db = AppDatabase.getDatabase(appContext)
                                val current = db.savedItemDao().getItemById(id)
                                if (current != null) {
                                    val realDesc = metadata.description?.takeIf { it.isNotBlank() }
                                    val realTitle = metadata.title?.takeIf { it.isNotBlank() }
                                    val realImage = metadata.imageUrl?.takeIf { it.isNotBlank() }

                                    db.savedItemDao().insertItem(
                                        current.copy(
                                            title = realTitle ?: current.title,
                                            linkTitle = realTitle ?: current.linkTitle,
                                            linkDescription = realDesc ?: current.linkDescription,
                                            linkImage = realImage ?: current.linkImage,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("QuickShare", "Background link metadata enrichment failed: ${e.message}")
                        }
                    }
                } else {
                    val isCode = textContent.contains("class ") || textContent.contains("fun ") || textContent.contains("{") || textContent.contains("import ")
                    val title = subject?.takeIf { it.isNotBlank() } ?: if (isCode) "Captured Code" else "Captured Note"
                    val entity = SavedItemEntity(
                        id = id,
                        type = if (isCode) SavedItemType.CODE.name else SavedItemType.TEXT.name,
                        title = title,
                        content = textContent,
                        timestamp = now,
                        foldersJson = "[]",
                        extractedText = null,
                        thumbnailPath = null
                    )
                    persistAndNotify("Saved to Cobalt!", entity)
                }
            }

            // 2. Image Shared
            mimeType.startsWith("image/") && mediaUri != null -> {
                val entity = SavedItemEntity(
                    id = id,
                    type = SavedItemType.IMAGE.name,
                    title = subject?.takeIf { it.isNotBlank() } ?: "Shared Image",
                    content = mediaUri.toString(),
                    timestamp = now,
                    foldersJson = "[]",
                    extractedText = null,
                    thumbnailPath = mediaUri.toString()
                )

                persistAndNotify("Saved image to Cobalt!", entity)

                // Background cache persistence
                activityScope.launch {
                    try {
                        val bytes = appContext.contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val localFile = File(appContext.filesDir, "media/images/shared_${id}.jpg")
                            localFile.parentFile?.mkdirs()
                            FileOutputStream(localFile).use { fos -> fos.write(bytes) }

                            val db = AppDatabase.getDatabase(appContext)
                            val current = db.savedItemDao().getItemById(id)
                            if (current != null) {
                                db.savedItemDao().insertItem(
                                    current.copy(
                                        content = localFile.absolutePath,
                                        thumbnailPath = localFile.absolutePath,
                                        sizeBytes = bytes.size.toLong(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("QuickShare", "Background image file caching failed: ${e.message}")
                    }
                }
            }

            // 3. Video Shared
            mimeType.startsWith("video/") && mediaUri != null -> {
                val entity = SavedItemEntity(
                    id = id,
                    type = SavedItemType.VIDEO.name,
                    title = subject?.takeIf { it.isNotBlank() } ?: "Shared Video",
                    content = mediaUri.toString(),
                    timestamp = now,
                    foldersJson = "[]",
                    extractedText = null,
                    thumbnailPath = null
                )

                persistAndNotify("Saved video to Cobalt!", entity)

                // Background cache persistence
                activityScope.launch {
                    try {
                        val bytes = appContext.contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val localFile = File(appContext.filesDir, "media/videos/shared_${id}.mp4")
                            localFile.parentFile?.mkdirs()
                            FileOutputStream(localFile).use { fos -> fos.write(bytes) }

                            val db = AppDatabase.getDatabase(appContext)
                            val current = db.savedItemDao().getItemById(id)
                            if (current != null) {
                                db.savedItemDao().insertItem(
                                    current.copy(
                                        content = localFile.absolutePath,
                                        thumbnailPath = localFile.absolutePath,
                                        sizeBytes = bytes.size.toLong(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("QuickShare", "Background video caching failed: ${e.message}")
                    }
                }
            }

            else -> {
                Toast.makeText(appContext, "Unsupported content format", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun persistAndNotify(message: String, entity: SavedItemEntity) {
        val appContext = applicationContext
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()

        // Insert instantly on IO pool
        activityScope.launch {
            try {
                AppDatabase.getDatabase(appContext).savedItemDao().insertItem(entity)
            } catch (e: Exception) {
                Log.e("QuickShare", "Failed to insert shared item: ${e.message}")
            }
        }

        // Close share sheet immediately without lag
        finish()
    }

    private data class ParsedMetadata(val title: String?, val description: String?, val imageUrl: String?)

    private fun fetchLinkMetadata(urlString: String): ParsedMetadata {
        return try {
            val cleanUrl = if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                "https://$urlString"
            } else {
                urlString
            }

            val document = org.jsoup.Jsoup.connect(cleanUrl)
                .timeout(6000)
                .ignoreHttpErrors(true)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .get()

            val title = document.title().takeIf { it.isNotBlank() }
                ?: document.select("meta[property=og:title]").attr("content").takeIf { it.isNotBlank() }
                ?: document.select("meta[name=twitter:title]").attr("content").takeIf { it.isNotBlank() }

            val description = document.select("meta[property=og:description]").attr("content").takeIf { it.isNotBlank() }
                ?: document.select("meta[name=description]").attr("content").takeIf { it.isNotBlank() }
                ?: document.select("meta[name=twitter:description]").attr("content").takeIf { it.isNotBlank() }

            val imageUrl = document.select("meta[property=og:image]").firstOrNull()?.absUrl("content")?.takeIf { it.isNotBlank() }
                ?: document.select("meta[name=twitter:image]").firstOrNull()?.absUrl("content")?.takeIf { it.isNotBlank() }
                ?: document.select("meta[property=og:image]").attr("content").takeIf { it.isNotBlank() }

            ParsedMetadata(title?.trim(), description?.trim(), imageUrl)
        } catch (e: Exception) {
            ParsedMetadata(null, null, null)
        }
    }
}

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

package com.example.ui.components.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.SavedItem
import com.example.data.model.getBestImagePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Lightweight helper to export rendered bitmaps and text content via Android's native share mechanisms.
 */
object ShareImageExporter {

    suspend fun exportAndShareBitmap(
        context: Context,
        bitmap: Bitmap,
        linkUrl: String? = null,
        chooserTitle: String = "Share Link"
    ) = withContext(Dispatchers.IO) {
        // Automatically copy link to clipboard silently so the user has the tappable link ready
        if (!linkUrl.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                copyToClipboard(context, linkUrl, showToast = false)
            }
        }

        val directory = File(context.cacheDir, "shares").also {
            if (!it.exists()) it.mkdirs()
        }
        val file = File(directory, "link_share_${UUID.randomUUID()}.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Shared Image", uri)
                if (!linkUrl.isNullOrBlank()) {
                    // Share image with link only so recipients get visual + clickable URL
                    putExtra(Intent.EXTRA_TEXT, linkUrl)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (e: Throwable) {
            android.util.Log.e("CobaltShare", "Error sharing bitmap", e)
            file.delete()
        }
    }

    fun shareLinkAsText(context: Context, item: SavedItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val shareText = buildString {
                val title = item.linkTitle?.takeIf { it.isNotBlank() } ?: item.title
                if (title.isNotBlank() && !title.startsWith("http://") && !title.startsWith("https://")) {
                    appendLine(title)
                }
                append(item.content)
            }
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Link").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Cobalt Link", showToast: Boolean = false) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        if (showToast && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareOriginalImage(context: Context, item: SavedItem, chooserTitle: String = "Share Original Photo") {
        val path = item.getBestImagePath() ?: return
        try {
            val uri: android.net.Uri = when {
                path.startsWith("content://") -> android.net.Uri.parse(path)
                path.startsWith("http://") || path.startsWith("https://") -> android.net.Uri.parse(path)
                else -> {
                    val file = File(path)
                    if (file.exists()) {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } else {
                        android.net.Uri.parse(path)
                    }
                }
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Shared Image", uri)
                val caption = item.title.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: item.extractedText
                if (!caption.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Throwable) {
            android.util.Log.e("CobaltShare", "Error sharing original image", e)
        }
    }

    suspend fun copyImageToClipboard(context: Context, bitmap: Bitmap, label: String = "Cobalt Image"): Boolean = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shares").also {
            if (!it.exists()) it.mkdirs()
        }
        val file = File(directory, "clip_${UUID.randomUUID()}.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            withContext(Dispatchers.Main) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newUri(context.contentResolver, label, uri)
                clipboard.setPrimaryClip(clip)
            }
            true
        } catch (e: Throwable) {
            android.util.Log.e("CobaltShare", "Error copying image to clipboard", e)
            false
        }
    }
}

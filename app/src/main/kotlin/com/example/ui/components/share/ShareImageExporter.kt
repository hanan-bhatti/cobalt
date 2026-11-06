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
        chooserTitle: String = "Share Link Card"
    ) = withContext(Dispatchers.IO) {
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
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
            }
        } catch (e: Throwable) {
            file.delete()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Could not prepare image for sharing", Toast.LENGTH_SHORT).show()
            }
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
        context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Cobalt Link") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}

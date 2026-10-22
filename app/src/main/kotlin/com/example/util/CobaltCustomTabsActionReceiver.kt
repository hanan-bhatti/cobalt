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

package com.example.util

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles custom actions triggered from Chrome Custom Tabs toolbar buttons and menu items.
 */
class CobaltCustomTabsActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COPY_LINK = "com.example.action.CUSTOM_TABS_COPY_LINK"
        const val ACTION_SHARE_LINK = "com.example.action.CUSTOM_TABS_SHARE_LINK"
        const val ACTION_STAR_ITEM = "com.example.action.CUSTOM_TABS_STAR_ITEM"

        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_URL = "extra_url"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val url = intent.dataString ?: intent.getStringExtra(EXTRA_URL)

        when (action) {
            ACTION_COPY_LINK -> {
                if (!url.isNullOrBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("URL", url)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }

            ACTION_SHARE_LINK -> {
                if (!url.isNullOrBlank()) {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Share link via").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot share link", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            ACTION_STAR_ITEM -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
                if (!itemId.isNullOrBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val entity = db.savedItemDao().getItemById(itemId)
                            if (entity != null) {
                                val folders = try {
                                    entity.foldersJson.removeSurrounding("[", "]")
                                        .split(",")
                                        .map { it.trim().removeSurrounding("\"") }
                                        .filter { it.isNotEmpty() }
                                        .toMutableList()
                                } catch (e: Exception) {
                                    mutableListOf()
                                }

                                val wasFavorited = folders.any { it.equals("Favorites", ignoreCase = true) }
                                val updatedFolders = if (wasFavorited) {
                                    folders.filterNot { it.equals("Favorites", ignoreCase = true) }
                                } else {
                                    folders + "Favorites"
                                }

                                val newFoldersJson = "[" + updatedFolders.joinToString(",") { "\"$it\"" } + "]"
                                db.savedItemDao().insertItem(
                                    entity.copy(
                                        foldersJson = newFoldersJson,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        if (wasFavorited) "Removed from Favorites" else "Starred & added to Favorites",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CobaltCustomTabs", "Failed to update favorite item: ${e.message}", e)
                        }
                    }
                } else if (!url.isNullOrBlank()) {
                    Toast.makeText(context, "Link bookmarked", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

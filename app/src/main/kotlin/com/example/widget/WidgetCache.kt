/*
 * Second Brain - A universal capture and personal knowledge archive
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

package com.example.widget

import android.content.Context
import android.util.Log
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import org.json.JSONArray
import org.json.JSONObject

object WidgetCache {
    private const val PREFS_NAME = "second_brain_widget_cache"
    private const val KEY_ITEMS = "cached_items"
    private const val KEY_LAST_UPDATED = "last_updated_time"

    fun getCachedItems(context: Context): List<SavedItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val result = mutableListOf<SavedItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val foldersArray = obj.optJSONArray("folders")
                val foldersList = mutableListOf<String>()
                if (foldersArray != null) {
                    for (j in 0 until foldersArray.length()) {
                        foldersList.add(foldersArray.getString(j))
                    }
                }
                val rawType = obj.optString("type", "TEXT")
                val itemType = SavedItemType.entries.find { it.name.equals(rawType, ignoreCase = true) } ?: SavedItemType.TEXT

                result.add(
                    SavedItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        type = itemType,
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        folders = foldersList,
                        extractedText = obj.optString("extractedText").ifBlank { null },
                        thumbnailPath = obj.optString("thumbnailPath").ifBlank { null },
                        orderIndex = obj.optDouble("orderIndex", 0.0),
                        isSynced = obj.optBoolean("isSynced", false),
                        linkTitle = obj.optString("linkTitle").ifBlank { null },
                        linkDescription = obj.optString("linkDescription").ifBlank { null },
                        linkImage = obj.optString("linkImage").ifBlank { null },
                        isBackedUp = obj.optBoolean("isBackedUp", false),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        isPendingBackup = obj.optBoolean("isPendingBackup", false),
                        isUnavailable = obj.optBoolean("isUnavailable", false)
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e("WidgetCache", "Failed to deserialize cached items", e)
            emptyList()
        }
    }

    fun saveItemsToCache(context: Context, items: List<SavedItem>) {
        try {
            val jsonArray = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type.name)
                    put("title", item.title)
                    put("content", item.content)
                    put("timestamp", item.timestamp)
                    put("folders", JSONArray(item.folders))
                    item.extractedText?.let { put("extractedText", it) }
                    item.thumbnailPath?.let { put("thumbnailPath", it) }
                    put("orderIndex", item.orderIndex)
                    put("isSynced", item.isSynced)
                    item.linkTitle?.let { put("linkTitle", it) }
                    item.linkDescription?.let { put("linkDescription", it) }
                    item.linkImage?.let { put("linkImage", it) }
                    put("isBackedUp", item.isBackedUp)
                    put("sizeBytes", item.sizeBytes)
                    put("isPendingBackup", item.isPendingBackup)
                    put("isUnavailable", item.isUnavailable)
                }
                jsonArray.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ITEMS, jsonArray.toString())
                .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e("WidgetCache", "Failed to serialize items to cache", e)
        }
    }

    fun getLastUpdatedTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATED, 0L)
    }
}

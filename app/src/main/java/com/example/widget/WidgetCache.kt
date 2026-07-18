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
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object WidgetCache {
    private const val PREFS_NAME = "second_brain_widget_cache"
    private const val KEY_ITEMS = "cached_items"
    private const val KEY_LAST_UPDATED = "last_updated_time"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, SavedItem::class.java)
    private val adapter = moshi.adapter<List<SavedItem>>(listType)

    fun getCachedItems(context: Context): List<SavedItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e("WidgetCache", "Failed to deserialize cached items", e)
            emptyList()
        }
    }

    fun saveItemsToCache(context: Context, items: List<SavedItem>) {
        try {
            val json = adapter.toJson(items)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ITEMS, json)
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

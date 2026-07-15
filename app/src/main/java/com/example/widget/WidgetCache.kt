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

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

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.SavedItem
import org.json.JSONObject

/**
 * 100% on-device learning store for adaptive reminders.
 * Stores telemetry, folder attention history, item open metrics,
 * and user response rates without any cloud or external dependencies.
 */
class AdaptiveReminderStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cobalt_adaptive_reminders", Context.MODE_PRIVATE)

    var isRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var deliveryHour: Int
        get() = prefs.getInt(KEY_DELIVERY_HOUR, 9) // Default 9 AM
        set(value) = prefs.edit().putInt(KEY_DELIVERY_HOUR, value).apply()

    var deliveryMinute: Int
        get() = prefs.getInt(KEY_DELIVERY_MINUTE, 0) // Default 0 min
        set(value) = prefs.edit().putInt(KEY_DELIVERY_MINUTE, value).apply()

    var inactivityDaysThreshold: Int
        get() = prefs.getInt(KEY_INACTIVITY_THRESHOLD, 3) // Default 3 days
        set(value) = prefs.edit().putInt(KEY_INACTIVITY_THRESHOLD, value).apply()

    var prioritizeActiveFolders: Boolean
        get() = prefs.getBoolean(KEY_PRIORITIZE_FOLDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_PRIORITIZE_FOLDERS, value).apply()

    var includeWeekends: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_WEEKENDS, true)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_WEEKENDS, value).apply()

    val totalRemindersSent: Int
        get() = prefs.getInt(KEY_TOTAL_SENT, 0)

    val totalRemindersOpened: Int
        get() = prefs.getInt(KEY_TOTAL_OPENED, 0)

    val lastReminderTimestamp: Long
        get() = prefs.getLong(KEY_LAST_REMINDER_TIME, 0L)

    // ----------------------------------------------------
    // TELEMETRY RECORDING
    // ----------------------------------------------------

    /**
     * Record when a user opens an item in DetailScreen.
     * Updates item open count, last open timestamp, and folder attention.
     */
    fun recordItemOpened(item: SavedItem, fromNotification: Boolean = false) {
        val now = System.currentTimeMillis()
        val editor = prefs.edit()

        // 1. Update item open timestamp and count
        val openTimestamps = getJsonMapLong(KEY_ITEM_LAST_OPENED)
        openTimestamps[item.id] = now
        putJsonMapLong(editor, KEY_ITEM_LAST_OPENED, openTimestamps)

        val openCounts = getJsonMapInt(KEY_ITEM_OPEN_COUNTS)
        openCounts[item.id] = (openCounts[item.id] ?: 0) + 1
        putJsonMapInt(editor, KEY_ITEM_OPEN_COUNTS, openCounts)

        if (fromNotification) {
            val notifOpenCounts = getJsonMapInt(KEY_ITEM_NOTIF_OPEN_COUNTS)
            notifOpenCounts[item.id] = (notifOpenCounts[item.id] ?: 0) + 1
            putJsonMapInt(editor, KEY_ITEM_NOTIF_OPEN_COUNTS, notifOpenCounts)

            val currentOpened = prefs.getInt(KEY_TOTAL_OPENED, 0)
            editor.putInt(KEY_TOTAL_OPENED, currentOpened + 1)
        }

        // 2. Update folder attention
        if (item.folders.isNotEmpty()) {
            val folderAttention = getJsonMapLong(KEY_FOLDER_ATTENTION)
            val folderCounts = getJsonMapInt(KEY_FOLDER_OPEN_COUNTS)
            val folderSuccess = getJsonMapInt(KEY_FOLDER_REMINDER_SUCCESS)

            for (folder in item.folders) {
                folderAttention[folder] = now
                folderCounts[folder] = (folderCounts[folder] ?: 0) + 1
                if (fromNotification) {
                    folderSuccess[folder] = (folderSuccess[folder] ?: 0) + 1
                }
            }

            putJsonMapLong(editor, KEY_FOLDER_ATTENTION, folderAttention)
            putJsonMapInt(editor, KEY_FOLDER_OPEN_COUNTS, folderCounts)
            if (fromNotification) {
                putJsonMapInt(editor, KEY_FOLDER_REMINDER_SUCCESS, folderSuccess)
            }
        }

        editor.apply()
    }

    /**
     * Record when a reminder notification was opened via deep link.
     */
    fun recordNotificationOpened(itemId: String) {
        val editor = prefs.edit()
        val currentOpened = prefs.getInt(KEY_TOTAL_OPENED, 0)
        editor.putInt(KEY_TOTAL_OPENED, currentOpened + 1)

        val notifOpenCounts = getJsonMapInt(KEY_ITEM_NOTIF_OPEN_COUNTS)
        notifOpenCounts[itemId] = (notifOpenCounts[itemId] ?: 0) + 1
        putJsonMapInt(editor, KEY_ITEM_NOTIF_OPEN_COUNTS, notifOpenCounts)

        editor.apply()
    }

    /**
     * Record when a reminder was dispatched to the system tray.
     */
    fun recordReminderDispatched(itemId: String, folders: List<String>) {
        val now = System.currentTimeMillis()
        val editor = prefs.edit()

        editor.putLong(KEY_LAST_REMINDER_TIME, now)
        val currentSent = prefs.getInt(KEY_TOTAL_SENT, 0)
        editor.putInt(KEY_TOTAL_SENT, currentSent + 1)

        val remindedMap = getJsonMapLong(KEY_ITEM_LAST_REMINDED)
        remindedMap[itemId] = now
        putJsonMapLong(editor, KEY_ITEM_LAST_REMINDED, remindedMap)

        editor.apply()
    }

    // ----------------------------------------------------
    // READ ENGINE SIGNALS
    // ----------------------------------------------------

    fun getItemLastOpened(itemId: String): Long? {
        return getJsonMapLong(KEY_ITEM_LAST_OPENED)[itemId]
    }

    fun getItemOpenCount(itemId: String): Int {
        return getJsonMapInt(KEY_ITEM_OPEN_COUNTS)[itemId] ?: 0
    }

    fun getItemNotificationOpenCount(itemId: String): Int {
        return getJsonMapInt(KEY_ITEM_NOTIF_OPEN_COUNTS)[itemId] ?: 0
    }

    fun getItemLastReminded(itemId: String): Long? {
        return getJsonMapLong(KEY_ITEM_LAST_REMINDED)[itemId]
    }

    fun getFolderAttentionMap(): Map<String, Long> {
        return getJsonMapLong(KEY_FOLDER_ATTENTION)
    }

    fun getFolderOpenCounts(): Map<String, Int> {
        return getJsonMapInt(KEY_FOLDER_OPEN_COUNTS)
    }

    fun getFolderSuccessMap(): Map<String, Int> {
        return getJsonMapInt(KEY_FOLDER_REMINDER_SUCCESS)
    }

    fun getTopResurfacedFolders(): List<Pair<String, Int>> {
        val map = getFolderSuccessMap()
        return map.toList().sortedByDescending { it.second }.take(4)
    }

    fun resetLearningStats() {
        prefs.edit()
            .remove(KEY_TOTAL_SENT)
            .remove(KEY_TOTAL_OPENED)
            .remove(KEY_LAST_REMINDER_TIME)
            .remove(KEY_ITEM_LAST_OPENED)
            .remove(KEY_ITEM_OPEN_COUNTS)
            .remove(KEY_ITEM_NOTIF_OPEN_COUNTS)
            .remove(KEY_FOLDER_ATTENTION)
            .remove(KEY_FOLDER_OPEN_COUNTS)
            .remove(KEY_FOLDER_REMINDER_SUCCESS)
            .remove(KEY_ITEM_LAST_REMINDED)
            .apply()
    }

    // ----------------------------------------------------
    // INTERNAL JSON HELPERS
    // ----------------------------------------------------

    private fun getJsonMapLong(key: String): MutableMap<String, Long> {
        val raw = prefs.getString(key, null) ?: return mutableMapOf()
        return try {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, Long>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.optLong(k)
            }
            map
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun putJsonMapLong(editor: SharedPreferences.Editor, key: String, map: Map<String, Long>) {
        val json = JSONObject()
        // Cap to latest 2000 items to guarantee fast performance and small storage footprint
        val entries = if (map.size > 2000) {
            map.entries.sortedByDescending { it.value }.take(2000)
        } else map.entries

        for ((k, v) in entries) {
            json.put(k, v)
        }
        editor.putString(key, json.toString())
    }

    private fun getJsonMapInt(key: String): MutableMap<String, Int> {
        val raw = prefs.getString(key, null) ?: return mutableMapOf()
        return try {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.optInt(k)
            }
            map
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun putJsonMapInt(editor: SharedPreferences.Editor, key: String, map: Map<String, Int>) {
        val json = JSONObject()
        val entries = if (map.size > 2000) {
            map.entries.sortedByDescending { it.value }.take(2000)
        } else map.entries

        for ((k, v) in entries) {
            json.put(k, v)
        }
        editor.putString(key, json.toString())
    }

    companion object {
        private const val KEY_ENABLED = "reminders_enabled"
        private const val KEY_DELIVERY_HOUR = "reminders_delivery_hour"
        private const val KEY_DELIVERY_MINUTE = "reminders_delivery_minute"
        private const val KEY_INACTIVITY_THRESHOLD = "reminders_inactivity_threshold"
        private const val KEY_PRIORITIZE_FOLDERS = "reminders_prioritize_active_folders"
        private const val KEY_INCLUDE_WEEKENDS = "reminders_include_weekends"

        private const val KEY_TOTAL_SENT = "stat_reminders_total_sent"
        private const val KEY_TOTAL_OPENED = "stat_reminders_total_opened"
        private const val KEY_LAST_REMINDER_TIME = "stat_last_reminder_time"

        private const val KEY_ITEM_LAST_OPENED = "map_item_last_opened"
        private const val KEY_ITEM_OPEN_COUNTS = "map_item_open_counts"
        private const val KEY_ITEM_NOTIF_OPEN_COUNTS = "map_item_notif_open_counts"
        private const val KEY_FOLDER_ATTENTION = "map_folder_attention"
        private const val KEY_FOLDER_OPEN_COUNTS = "map_folder_open_counts"
        private const val KEY_FOLDER_REMINDER_SUCCESS = "map_folder_reminder_success"
        private const val KEY_ITEM_LAST_REMINDED = "map_item_last_reminded"
    }
}

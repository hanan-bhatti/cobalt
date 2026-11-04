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

package com.example.data.cleanup

import android.content.Context
import android.net.Uri
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.repository.CobaltRepository
import com.example.util.ItemTitleResolver
import com.example.util.SafeLogger
import com.example.util.loadTitleFallbacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time, idempotent, crash-safe data cleanup for stored items whose title
 * or link description looks like an internal cache path, URI, or generated cache name (Issue #21).
 */
object LegacyPathTitleCleanup {
    private const val TAG = "LegacyPathTitleCleanup"
    const val PREFS_NAME = "cobalt_data_fixes"
    const val PREF_KEY_CLEANUP_DONE = "title_cleanup_v1_done"

    /**
     * Checks whether the legacy path title cleanup has already executed.
     */
    fun isCleanupDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_KEY_CLEANUP_DONE, false)
    }

    /**
     * Resets the cleanup flag. Useful for testing.
     */
    fun resetCleanupFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    /**
     * Dry-run inspection that counts how many items in the repository have
     * titles or link descriptions that match internal paths or generated cache names.
     * Does NOT modify any data.
     */
    suspend fun countItemsNeedingCleanup(repository: CobaltRepository): Int = withContext(Dispatchers.IO) {
        try {
            val allItems = repository.getAllItems()
            allItems.count { item -> isItemDirty(item) }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Returns true if the item has an internal path or generated cache name in its title
     * or link description.
     */
    fun isItemDirty(item: SavedItem): Boolean {
        if (ItemTitleResolver.isInternalPathOrGeneratedName(item.title)) {
            return true
        }
        if (!item.linkDescription.isNullOrBlank() && ItemTitleResolver.isInternalPathOrGeneratedName(item.linkDescription)) {
            return true
        }
        return false
    }

    /**
     * Determines the clean replacement title for an item, preferring recoverable metadata
     * (such as linkTitle or original displayName from content:// URIs) before falling back
     * to localized type fallbacks.
     */
    fun determineCleanTitle(context: Context, item: SavedItem): String {
        // 1. Recover from linkTitle if valid
        if (item.type == SavedItemType.LINK && !item.linkTitle.isNullOrBlank() && !ItemTitleResolver.isInternalPathOrGeneratedName(item.linkTitle)) {
            val sanitized = ItemTitleResolver.sanitizeDisplayName(item.linkTitle)
            if (sanitized.isNotBlank()) return sanitized
        }

        // 2. Recover from content:// URI if still openable and provides DISPLAY_NAME
        if (item.content.startsWith("content://")) {
            try {
                val recovered = ItemTitleResolver.queryDisplayName(context, Uri.parse(item.content))
                if (!recovered.isNullOrBlank() && !ItemTitleResolver.isInternalPathOrGeneratedName(recovered)) {
                    val sanitized = ItemTitleResolver.sanitizeDisplayName(ItemTitleResolver.removeExtension(recovered))
                    if (sanitized.isNotBlank()) return sanitized
                }
            } catch (_: Exception) {
                // Ignore failure to query display name
            }
        }

        // 3. Localized type fallback
        val fallbacks = context.loadTitleFallbacks()
        return ItemTitleResolver.resolveItemTitle(
            userTitle = null,
            displayName = null,
            type = item.type,
            timestamp = item.timestamp,
            fallbacks = fallbacks
        )
    }

    /**
     * Executes the one-time idempotent cleanup off the main thread.
     * Crash-safe: catches any exceptions and leaves state consistent.
     * Logs counts ONLY (never titles, URIs or file paths).
     *
     * @return number of items cleaned, or 0 if already done or nothing needed cleanup.
     */
    suspend fun performCleanup(context: Context, repository: CobaltRepository): Int = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_KEY_CLEANUP_DONE, false)) {
            return@withContext 0
        }

        try {
            val allItems = repository.getAllItems()
            var modifiedCount = 0

            for (item in allItems) {
                val titleNeedsFix = ItemTitleResolver.isInternalPathOrGeneratedName(item.title)
                val descNeedsFix = !item.linkDescription.isNullOrBlank() && ItemTitleResolver.isInternalPathOrGeneratedName(item.linkDescription)

                if (titleNeedsFix || descNeedsFix) {
                    val newTitle = if (titleNeedsFix) determineCleanTitle(context, item) else item.title
                    val newDesc = if (descNeedsFix) null else item.linkDescription

                    repository.updateItemTitleAndDescription(item.id, newTitle, newDesc)
                    modifiedCount++
                }
            }

            prefs.edit().putBoolean(PREF_KEY_CLEANUP_DONE, true).commit()
            SafeLogger.i(TAG, "Legacy path title cleanup completed: $modifiedCount items updated")
            modifiedCount
        } catch (e: Exception) {
            SafeLogger.e(TAG, "Legacy path title cleanup encountered an error", e)
            0
        }
    }

    /**
     * Convenience entry point to be triggered on startup in a background coroutine.
     */
    suspend fun runCleanup(context: Context) {
        if (isRobolectric()) return
        val repository = CobaltRepository(context)
        performCleanup(context, repository)
    }

    internal fun isRobolectric(): Boolean {
        return android.os.Build.FINGERPRINT == "robolectric" ||
                try {
                    Class.forName("org.robolectric.Robolectric")
                    true
                } catch (_: Throwable) {
                    false
                }
    }
}

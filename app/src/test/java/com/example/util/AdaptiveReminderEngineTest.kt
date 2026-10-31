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

import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveReminderEngineTest {

    private val oneDayMillis = 24L * 60L * 60L * 1000L

    @Test
    fun testNeverOpenedItemRanksHigherThanRecentlyOpenedItem() {
        val now = 1000000000000L

        // Item 1: created 5 days ago, never opened
        val itemNeverOpened = SavedItem(
            id = "item_never_opened",
            type = SavedItemType.TEXT,
            title = "Crucial Architecture Decision",
            content = "Detailed note about architecture",
            timestamp = now - (5 * oneDayMillis)
        )

        // Item 2: created 5 days ago, opened yesterday
        val itemOpenedYesterday = SavedItem(
            id = "item_opened_yesterday",
            type = SavedItemType.TEXT,
            title = "Grocery list",
            content = "Milk, eggs, bread",
            timestamp = now - (5 * oneDayMillis)
        )

        val candidate = AdaptiveReminderEngine.selectBestCandidateFromSignals(
            allItems = listOf(itemNeverOpened, itemOpenedYesterday),
            inactivityDaysThreshold = 3,
            itemLastOpenedMap = { id ->
                if (id == "item_opened_yesterday") now - oneDayMillis else null
            },
            itemOpenCountMap = { id ->
                if (id == "item_opened_yesterday") 5 else 0
            },
            now = now
        )

        assertNotNull(candidate)
        assertEquals("item_never_opened", candidate!!.item.id)
        assertTrue(candidate.score > 30.0)
    }

    @Test
    fun testNeglectedItemInActiveFolderReceivesSynergyBoost() {
        val now = 1000000000000L

        // Two neglected items (both never opened):
        // Item A is in "StaleFolder" (not touched in 30 days)
        val itemA = SavedItem(
            id = "item_a",
            type = SavedItemType.TEXT,
            title = "Old archived thought",
            content = "Random note",
            folders = listOf("StaleFolder"),
            timestamp = now - (6 * oneDayMillis)
        )

        // Item B is in "ActiveProject" (user has been opening notes in ActiveProject today!)
        val itemB = SavedItem(
            id = "item_b",
            type = SavedItemType.TEXT,
            title = "Missing piece for active sprint",
            content = "Checklist of items for sprint",
            folders = listOf("ActiveProject"),
            timestamp = now - (6 * oneDayMillis)
        )

        val folderAttention = mapOf(
            "ActiveProject" to now - (1000L * 60 * 60), // Touched 1 hour ago
            "StaleFolder" to now - (30 * oneDayMillis)
        )
        val folderOpenCounts = mapOf(
            "ActiveProject" to 15,
            "StaleFolder" to 1
        )

        val candidate = AdaptiveReminderEngine.selectBestCandidateFromSignals(
            allItems = listOf(itemA, itemB),
            inactivityDaysThreshold = 3,
            prioritizeActiveFolders = true,
            folderAttention = folderAttention,
            folderOpenCounts = folderOpenCounts,
            now = now
        )

        assertNotNull(candidate)
        // Item B should win because it is an overlooked note in an active folder
        assertEquals("item_b", candidate!!.item.id)
        assertTrue(candidate.reason.contains("active folder") || candidate.reason.contains("Overlooked"))
    }

    @Test
    fun testCooldownSuppressesRecentReminder() {
        val now = 1000000000000L

        val item = SavedItem(
            id = "item_recent_reminded",
            type = SavedItemType.TEXT,
            title = "Already Reminded Note",
            content = "Note content",
            timestamp = now - (10 * oneDayMillis)
        )

        val candidate = AdaptiveReminderEngine.selectBestCandidateFromSignals(
            allItems = listOf(item),
            inactivityDaysThreshold = 3,
            itemLastRemindedMap = { now - (2 * oneDayMillis) }, // Reminded 2 days ago (within 4-day cooldown)
            now = now
        )

        assertNull("Item within cooldown should not be reminded", candidate)
    }

    @Test
    fun testLearnedFeedbackRewardsResponsiveFolders() {
        val now = 1000000000000L

        val itemFinance = SavedItem(
            id = "item_finance",
            type = SavedItemType.TEXT,
            title = "Tax Receipts",
            content = "Keep for tax declaration",
            folders = listOf("Finance"),
            timestamp = now - (7 * oneDayMillis)
        )

        val itemCooking = SavedItem(
            id = "item_cooking",
            type = SavedItemType.TEXT,
            title = "Pasta recipe",
            content = "Tomato and garlic sauce",
            folders = listOf("Cooking"),
            timestamp = now - (7 * oneDayMillis)
        )

        // User previously opened 3 reminder notifications from Finance, 0 from Cooking
        val folderSuccess = mapOf("Finance" to 3, "Cooking" to 0)

        val candidate = AdaptiveReminderEngine.selectBestCandidateFromSignals(
            allItems = listOf(itemFinance, itemCooking),
            inactivityDaysThreshold = 3,
            folderSuccess = folderSuccess,
            now = now
        )

        assertNotNull(candidate)
        assertEquals("item_finance", candidate!!.item.id)
    }
}

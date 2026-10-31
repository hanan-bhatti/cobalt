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
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType

data class ScoredReminderCandidate(
    val item: SavedItem,
    val score: Double,
    val reason: String
)

/**
 * On-Device Adaptive Knowledge Resurfacing Engine.
 *
 * Employs a multi-factor heuristics model:
 * 1. Neglect Factor: Items never opened or untouched for a long period.
 * 2. Folder Attention Synergy: If a folder is active (user frequently opens items in it)
 *    but a specific item in that folder was overlooked, it receives high synergy urgency.
 * 3. Learning Feedback: Learns from notification opens vs dismissals to favor categories the user appreciates.
 * 4. Content Quality: Prioritizes meaningful markdown notes, tasks, or extracted text over empty stubs.
 */
object AdaptiveReminderEngine {

    fun selectBestCandidate(
        context: Context,
        allItems: List<SavedItem>,
        now: Long = System.currentTimeMillis()
    ): ScoredReminderCandidate? {
        val store = AdaptiveReminderStore(context)
        if (!store.isRemindersEnabled) return null

        return selectBestCandidateFromSignals(
            allItems = allItems,
            inactivityDaysThreshold = store.inactivityDaysThreshold,
            prioritizeActiveFolders = store.prioritizeActiveFolders,
            folderAttention = store.getFolderAttentionMap(),
            folderOpenCounts = store.getFolderOpenCounts(),
            folderSuccess = store.getFolderSuccessMap(),
            itemLastOpenedMap = { store.getItemLastOpened(it) },
            itemOpenCountMap = { store.getItemOpenCount(it) },
            itemNotifOpenCountMap = { store.getItemNotificationOpenCount(it) },
            itemLastRemindedMap = { store.getItemLastReminded(it) },
            now = now
        )
    }

    fun selectBestCandidateFromSignals(
        allItems: List<SavedItem>,
        inactivityDaysThreshold: Int = 3,
        prioritizeActiveFolders: Boolean = true,
        folderAttention: Map<String, Long> = emptyMap(),
        folderOpenCounts: Map<String, Int> = emptyMap(),
        folderSuccess: Map<String, Int> = emptyMap(),
        itemLastOpenedMap: (String) -> Long? = { null },
        itemOpenCountMap: (String) -> Int = { 0 },
        itemNotifOpenCountMap: (String) -> Int = { 0 },
        itemLastRemindedMap: (String) -> Long? = { null },
        now: Long = System.currentTimeMillis()
    ): ScoredReminderCandidate? {
        val minUntouchedMillis = inactivityDaysThreshold * 24L * 60L * 60L * 1000L
        val cooldownMillis = 4L * 24L * 60L * 60L * 1000L // 4-day cooldown per item

        val eligible = allItems.filter { item ->
            !item.isArchived &&
            !item.isDeleted &&
            (item.type != SavedItemType.MEDIA || !item.isUnavailable) &&
            (now - item.timestamp >= 24L * 60L * 60L * 1000L) // At least 24 hours old
        }

        if (eligible.isEmpty()) return null

        val candidates = mutableListOf<ScoredReminderCandidate>()

        for (item in eligible) {
            val lastReminded = itemLastRemindedMap(item.id)
            if (lastReminded != null && (now - lastReminded) < cooldownMillis) {
                continue // Still in cooldown
            }

            val lastOpened = itemLastOpenedMap(item.id) ?: item.timestamp
            val timeSinceLastOpened = (now - lastOpened).coerceAtLeast(0L)

            // Must have been inactive for at least the user threshold
            if (timeSinceLastOpened < minUntouchedMillis) {
                continue
            }

            val openCount = itemOpenCountMap(item.id)
            val notifOpenCount = itemNotifOpenCountMap(item.id)

            var score = 10.0
            var primaryReason = "Unreviewed capture"

            // 1. Neglect Urgency (0 opens = high priority)
            if (openCount == 0) {
                score += 35.0
                primaryReason = "Never opened since saved"
            } else {
                val daysInactive = timeSinceLastOpened / (1000.0 * 60.0 * 60.0 * 24.0)
                // Curve capping at 30 days
                val inactivityBonus = (daysInactive.coerceAtMost(30.0) / 30.0) * 20.0
                score += inactivityBonus
                primaryReason = "Untouched for ${daysInactive.toInt()} days"
            }

            // 2. Folder Attention Synergy
            // "learns what user open which folder got attentions and weather other items in that folder got attention or not"
            if (prioritizeActiveFolders && item.folders.isNotEmpty()) {
                var bestFolderSynergy = 0.0
                var synergyFolder = ""

                for (folder in item.folders) {
                    val lastFolderTouch = folderAttention[folder] ?: 0L
                    val folderDaysSinceTouch = (now - lastFolderTouch) / (1000.0 * 60.0 * 60.0 * 24.0)

                    if (folderDaysSinceTouch <= 7.0) {
                        val folderHeat = (7.0 - folderDaysSinceTouch) / 7.0 // 1.0 (today) down to 0.0 (7 days ago)
                        val folderTotalOpens = folderOpenCounts[folder] ?: 0

                        val neglectRatio = if (folderTotalOpens > 1) {
                            (1.0 - (openCount.toDouble() / folderTotalOpens.toDouble())).coerceIn(0.2, 1.0)
                        } else 0.5

                        val synergyScore = folderHeat * neglectRatio * 28.0
                        if (synergyScore > bestFolderSynergy) {
                            bestFolderSynergy = synergyScore
                            synergyFolder = folder
                        }
                    }

                    // Learned reward: did the user previously respond to reminders from this folder?
                    val successCount = folderSuccess[folder] ?: 0
                    score += (successCount.coerceAtMost(5) * 4.0)
                }

                if (bestFolderSynergy > 0.0) {
                    score += bestFolderSynergy
                    primaryReason = "Overlooked item in active folder '$synergyFolder'"
                }
            }

            // 3. User feedback reinforcement: if this item was opened from notification before
            if (notifOpenCount > 0) {
                score += (notifOpenCount * 8.0)
            }

            // 4. Content substance bonus
            if (item.title.isNotBlank() && !item.title.startsWith("http")) {
                score += 6.0
            }
            if (item.content.length > 50 || !item.extractedText.isNullOrBlank() || !item.linkDescription.isNullOrBlank()) {
                score += 5.0
            }

            candidates.add(ScoredReminderCandidate(item, score, primaryReason))
        }

        return candidates.maxByOrNull { it.score }
    }
}

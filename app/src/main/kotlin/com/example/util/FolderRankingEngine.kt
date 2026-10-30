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

import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import kotlin.math.exp
import kotlin.math.ln

data class RankedFolder(
    val name: String,
    val score: Double,
    val isSuggested: Boolean = false,
    val usageCount: Int = 0,
    val entity: CustomFolderEntity? = null
)

/**
 * Lightweight, on-device adaptive scoring engine that learns from user organizational habits.
 * It ranks folders according to usage frequency, recency, content type affinity,
 * keyword relevance, and pinning status without any heavy ML runtime.
 */
object FolderRankingEngine {

    fun rankFolders(
        folders: List<String>,
        folderEntities: List<CustomFolderEntity> = emptyList(),
        allItems: List<SavedItem>,
        currentItemType: SavedItemType? = null,
        currentItemTitle: String? = null,
        currentItemContent: String? = null,
        rankingEnabled: Boolean = true
    ): List<RankedFolder> {
        val distinctFolders = folders.distinct().filter { it != "All" && it != "Archive" }
        if (distinctFolders.isEmpty()) return emptyList()

        val entityMap = folderEntities.associateBy { it.name }
        val now = System.currentTimeMillis()

        // Precompute stats per folder from allItems
        val folderItemsMap = mutableMapOf<String, MutableList<SavedItem>>()
        for (item in allItems) {
            for (folder in item.folders) {
                folderItemsMap.getOrPut(folder) { mutableListOf() }.add(item)
            }
        }

        val titleTokens = tokenize(currentItemTitle)
        val contentTokens = tokenize(currentItemContent?.take(500))

        val scored = distinctFolders.map { folderName ->
            val entity = entityMap[folderName]
            val itemsInFolder = folderItemsMap[folderName] ?: emptyList()
            val usageCount = itemsInFolder.size

            if (!rankingEnabled) {
                val baseScore = if (entity?.isPinned == true) 100.0 else 0.0
                return@map RankedFolder(
                    name = folderName,
                    score = baseScore,
                    isSuggested = false,
                    usageCount = usageCount,
                    entity = entity
                )
            }

            var score = 0.0

            // 1. Frequency Score (log-damped count)
            val freqScore = ln(1.0 + usageCount) * 2.2
            score += freqScore

            // 2. Recency Score (exponential decay based on hours)
            val mostRecent = itemsInFolder.maxOfOrNull { it.timestamp }
            if (mostRecent != null && mostRecent > 0) {
                val hoursAgo = ((now - mostRecent).coerceAtLeast(0L) / (1000.0 * 60.0 * 60.0))
                // Half-life ~ 48 hours
                val recencyDecay = exp(-hoursAgo / 48.0)
                score += recencyDecay * 4.0
            }

            // 3. Type Affinity
            if (currentItemType != null && itemsInFolder.isNotEmpty()) {
                val typeCount = itemsInFolder.count { it.type == currentItemType }
                val typeRatio = typeCount.toDouble() / itemsInFolder.size.toDouble()
                score += typeRatio * 3.5
            }

            // 4. Keyword & Content Affinity
            val folderTokens = tokenize(folderName)
            if (folderTokens.isNotEmpty()) {
                var keywordHits = 0
                for (ft in folderTokens) {
                    if (titleTokens.contains(ft)) keywordHits += 3
                    if (contentTokens.contains(ft)) keywordHits += 1
                }
                score += (keywordHits.coerceAtMost(6) * 1.5)
            }

            // 5. Pinned bonus
            if (entity?.isPinned == true) {
                score += 5.0
            }

            RankedFolder(
                name = folderName,
                score = score,
                isSuggested = false,
                usageCount = usageCount,
                entity = entity
            )
        }

        val sorted = scored.sortedWith(
            compareByDescending<RankedFolder> { it.entity?.isPinned == true }
                .thenByDescending { it.score }
                .thenBy { it.name.lowercase() }
        )

        // Mark top folder as suggested if score is meaningful and ranking is enabled
        if (rankingEnabled && sorted.isNotEmpty()) {
            val top = sorted.first()
            if (top.score >= 3.0 && top.usageCount > 0) {
                return sorted.mapIndexed { index, ranked ->
                    if (index == 0) ranked.copy(isSuggested = true) else ranked
                }
            }
        }

        return sorted
    }

    private fun tokenize(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]"))
            .filter { it.length >= 3 }
            .toSet()
    }
}

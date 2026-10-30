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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRankingEngineTest {

    @Test
    fun testRankFoldersDisabledReturnsAlphabeticOrder() {
        val folderNames = listOf("Zebra", "Apple", "Mango")
        val entities = folderNames.map { CustomFolderEntity(name = it) }

        val ranked = FolderRankingEngine.rankFolders(
            folders = folderNames,
            folderEntities = entities,
            allItems = emptyList(),
            rankingEnabled = false
        )

        assertEquals(listOf("Apple", "Mango", "Zebra"), ranked.map { it.name })
        assertFalse(ranked.any { it.isSuggested })
    }

    @Test
    fun testFrequentAndRecentFoldersRankHigher() {
        val now = System.currentTimeMillis()
        val folderNames = listOf("Work", "Archive", "Personal")
        val entities = folderNames.map { CustomFolderEntity(name = it) }

        // Work has 5 recent items
        val workItems = (1..5).map {
            SavedItem(
                id = "work_$it",
                type = SavedItemType.TEXT,
                title = "Meeting note $it",
                content = "Content $it",
                timestamp = now - (it * 60_000L),
                folders = listOf("Work")
            )
        }

        // Personal has 1 old item (20 days ago)
        val oldPersonal = listOf(
            SavedItem(
                id = "old_personal",
                type = SavedItemType.TEXT,
                title = "Shopping",
                content = "Milk",
                timestamp = now - (20 * 86_400_000L),
                folders = listOf("Personal")
            )
        )

        val ranked = FolderRankingEngine.rankFolders(
            folders = folderNames,
            folderEntities = entities,
            allItems = workItems + oldPersonal,
            rankingEnabled = true
        )

        assertEquals("Work", ranked.first().name)
        assertTrue(ranked.first().score > ranked.last().score)
    }

    @Test
    fun testContentTokenAffinitySuggestsMatchingFolder() {
        val now = System.currentTimeMillis()
        val folderNames = listOf("Android", "Recipes", "Finance")
        val entities = folderNames.map { CustomFolderEntity(name = it) }

        val archiveItems = listOf(
            SavedItem(
                id = "1",
                type = SavedItemType.TEXT,
                title = "Android Notes",
                content = "Android compose preview test",
                timestamp = now - 3600_000L,
                folders = listOf("Android")
            )
        )

        val ranked = FolderRankingEngine.rankFolders(
            folders = folderNames,
            folderEntities = entities,
            allItems = archiveItems,
            currentItemType = SavedItemType.TEXT,
            currentItemTitle = "Jetpack Compose",
            currentItemContent = "New Android development tools",
            rankingEnabled = true
        )

        assertEquals("Android", ranked.first().name)
        assertTrue(ranked.first().isSuggested)
    }

    @Test
    fun testTypeAffinityBoostsFolderSharingSameMediaType() {
        val now = System.currentTimeMillis()
        val folderNames = listOf("Voice Notes", "Articles")
        val entities = folderNames.map { CustomFolderEntity(name = it) }

        val archiveItems = listOf(
            SavedItem(
                id = "1",
                type = SavedItemType.AUDIO,
                title = "Voice memo",
                content = "path",
                timestamp = now - 1000L,
                folders = listOf("Voice Notes")
            ),
            SavedItem(
                id = "2",
                type = SavedItemType.LINK,
                title = "Web link",
                content = "https://example.com",
                timestamp = now - 1000L,
                folders = listOf("Articles")
            )
        )

        val ranked = FolderRankingEngine.rankFolders(
            folders = folderNames,
            folderEntities = entities,
            allItems = archiveItems,
            currentItemType = SavedItemType.AUDIO,
            currentItemTitle = "New Audio",
            currentItemContent = "audio path",
            rankingEnabled = true
        )

        assertEquals("Voice Notes", ranked.first().name)
    }
}

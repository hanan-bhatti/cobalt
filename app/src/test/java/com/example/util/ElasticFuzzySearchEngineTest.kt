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
import org.junit.Assert.assertTrue
import org.junit.Test

class ElasticFuzzySearchEngineTest {

    @Test
    fun testEmptyOrBlankQueryReturnsAllItemsUnchanged() {
        val items = listOf(
            SavedItem(id = "1", title = "Item 1", content = "Content 1", type = SavedItemType.TEXT),
            SavedItem(id = "2", title = "Item 2", content = "Content 2", type = SavedItemType.TEXT)
        )

        val resultsEmpty = ElasticFuzzySearchEngine.search(items, "")
        assertEquals(items, resultsEmpty)

        val resultsWhitespace = ElasticFuzzySearchEngine.search(items, "   ")
        assertEquals(items, resultsWhitespace)
    }

    @Test
    fun testExactTitleMatchRanksHigherThanBodyMatch() {
        val titleMatch = SavedItem(
            id = "1",
            title = "Mobile Architecture Guide",
            content = "General software development overview",
            type = SavedItemType.TEXT
        )
        val bodyMatch = SavedItem(
            id = "2",
            title = "Overview",
            content = "Detailed discussions about mobile design patterns",
            type = SavedItemType.TEXT
        )

        val results = ElasticFuzzySearchEngine.search(listOf(bodyMatch, titleMatch), "mobile")

        assertEquals(2, results.size)
        // Title match has higher field weight (3.5x vs 1.5x)
        assertEquals(titleMatch.id, results[0].id)
    }

    @Test
    fun testTypoToleranceTranspositionMatchesCorrectly() {
        // User searches for "moblie" (transposed l and i)
        val item = SavedItem(
            id = "1",
            title = "Mobile Computing in 2026",
            content = "Smartphones and handheld devices",
            type = SavedItemType.TEXT
        )
        val irrelevant = SavedItem(
            id = "2",
            title = "Cooking Pasta",
            content = "Boil water with salt",
            type = SavedItemType.TEXT
        )

        val results = ElasticFuzzySearchEngine.search(listOf(irrelevant, item), "moblie")

        assertEquals(1, results.size)
        assertEquals(item.id, results[0].id)
    }

    @Test
    fun testTypoToleranceSingleCharDeletionMatches() {
        // User searches for "androi" (missing d)
        val item = SavedItem(
            id = "10",
            title = "Android Operating System",
            content = "Linux kernel based mobile OS",
            type = SavedItemType.TEXT
        )

        val results = ElasticFuzzySearchEngine.search(listOf(item), "androi")

        assertEquals(1, results.size)
        assertEquals(item.id, results[0].id)
    }

    @Test
    fun testTypoToleranceSingleCharInsertionMatches() {
        // User searches for "kotllin" (extra l)
        val item = SavedItem(
            id = "20",
            title = "Kotlin Coroutines in Depth",
            content = "Asynchronous programming",
            type = SavedItemType.TEXT
        )

        val results = ElasticFuzzySearchEngine.search(listOf(item), "kotllin")

        assertEquals(1, results.size)
        assertEquals(item.id, results[0].id)
    }

    @Test
    fun testSearchesAcrossOcrAndLinkMetadata() {
        val ocrItem = SavedItem(
            id = "100",
            title = "Receipt",
            content = "Scanned bill",
            extractedText = "Total Amount: $42.50 at Grocery Store",
            type = SavedItemType.IMAGE
        )
        val linkItem = SavedItem(
            id = "101",
            title = "Article",
            content = "https://example.com/travel",
            linkTitle = "Passport Renewal Guide",
            linkDescription = "Remember to renew passport before December",
            type = SavedItemType.LINK
        )

        val ocrResults = ElasticFuzzySearchEngine.search(listOf(ocrItem, linkItem), "grocery")
        assertEquals(1, ocrResults.size)
        assertEquals(ocrItem.id, ocrResults[0].id)

        val linkResults = ElasticFuzzySearchEngine.search(listOf(ocrItem, linkItem), "passport")
        assertEquals(1, linkResults.size)
        assertEquals(linkItem.id, linkResults[0].id)
    }

    @Test
    fun testSearchesAcrossCustomFolders() {
        val item = SavedItem(
            id = "50",
            title = "Trip Planning",
            content = "Flight tickets and hotel bookings",
            folders = listOf("Vacation2026", "Travel"),
            type = SavedItemType.TEXT
        )

        val results = ElasticFuzzySearchEngine.search(listOf(item), "Vacation2026")
        assertEquals(1, results.size)
        assertEquals(item.id, results[0].id)
    }

    @Test
    fun testDamerauLevenshteinDistanceCalculation() {
        assertEquals(0, ElasticFuzzySearchEngine.damerauLevenshteinDistance("test", "test"))
        assertEquals(1, ElasticFuzzySearchEngine.damerauLevenshteinDistance("mobile", "moblie")) // transposition
        assertEquals(1, ElasticFuzzySearchEngine.damerauLevenshteinDistance("mobile", "mobiile")) // insertion
        assertEquals(1, ElasticFuzzySearchEngine.damerauLevenshteinDistance("mobile", "mobil")) // deletion
        assertEquals(1, ElasticFuzzySearchEngine.damerauLevenshteinDistance("mobile", "moxile")) // substitution
    }
}

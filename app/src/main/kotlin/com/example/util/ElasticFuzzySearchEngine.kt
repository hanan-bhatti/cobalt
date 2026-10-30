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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * An on-device algorithmic search engine inspired by Elasticsearch / Lucene.
 * Features:
 * - Dynamic BM25-style field scoring across title, content, OCR/transcription, metadata, and folders.
 * - Typo-tolerant fuzzy matching with Damerau-Levenshtein distance (e.g. "moblie" -> "mobile").
 * - Character bigram Dice-Sorensen similarity for morphological tolerance.
 * - Zero hardcoded synonym or dictionary tables; 100% algorithmic and adaptive.
 */
object ElasticFuzzySearchEngine {

    private const val TITLE_WEIGHT = 3.5
    private const val FOLDER_WEIGHT = 2.5
    private const val OCR_WEIGHT = 2.2
    private const val METADATA_WEIGHT = 2.0
    private const val CONTENT_WEIGHT = 1.5
    private const val TYPE_WEIGHT = 1.0

    private val WORD_SPLIT_REGEX = Regex("[^\\p{L}\\p{Nd}]+")

    /**
     * Searches and ranks a list of items against a query string.
     * Returns items with score > 0 sorted in descending order of relevance.
     */
    fun search(items: List<SavedItem>, query: String): List<SavedItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return items

        val queryTerms = tokenize(trimmed)
        if (queryTerms.isEmpty()) return items

        return items
            .map { item -> item to calculateRelevanceScore(item, queryTerms, trimmed) }
            .filter { it.second > 0.0 }
            .sortedWith(compareByDescending<Pair<SavedItem, Double>> { it.second }.thenByDescending { it.first.timestamp })
            .map { it.first }
    }

    /**
     * Computes the relevance score of an item for the given query terms.
     */
    fun calculateRelevanceScore(
        item: SavedItem,
        queryTerms: List<String>,
        rawQuery: String
    ): Double {
        if (queryTerms.isEmpty()) return 0.0

        var totalScore = 0.0
        var matchedTermCount = 0

        val titleTokens = tokenize(item.title)
        val contentTokens = tokenize(item.content)
        val ocrTokens = tokenize(item.extractedText)
        val metaTokens = tokenize("${item.linkTitle.orEmpty()} ${item.linkDescription.orEmpty()}")
        val folderTokens = item.folders.flatMap { tokenize(it) }
        val typeTokens = tokenize(item.type.displayName)

        // Exact phrase match bonus in title or content
        val rawLower = rawQuery.lowercase()
        if (rawLower.length >= 4) {
            if (item.title.contains(rawLower, ignoreCase = true)) {
                totalScore += 25.0
            }
            if (item.content.contains(rawLower, ignoreCase = true)) {
                totalScore += 15.0
            }
            if (item.extractedText?.contains(rawLower, ignoreCase = true) == true) {
                totalScore += 12.0
            }
        }

        for (qTerm in queryTerms) {
            var bestTermScore = 0.0

            // 1. Title Field
            val titleScore = scoreField(titleTokens, qTerm) * TITLE_WEIGHT
            if (titleScore > bestTermScore) bestTermScore = titleScore

            // 2. Folders Field
            val folderScore = scoreField(folderTokens, qTerm) * FOLDER_WEIGHT
            if (folderScore > bestTermScore) bestTermScore = folderScore

            // 3. OCR / Transcription Field
            val ocrScore = scoreField(ocrTokens, qTerm) * OCR_WEIGHT
            if (ocrScore > bestTermScore) bestTermScore = ocrScore

            // 4. Metadata Field
            val metaScore = scoreField(metaTokens, qTerm) * METADATA_WEIGHT
            if (metaScore > bestTermScore) bestTermScore = metaScore

            // 5. Main Content Field
            val contentScore = scoreField(contentTokens, qTerm) * CONTENT_WEIGHT
            if (contentScore > bestTermScore) bestTermScore = contentScore

            // 6. Type Field
            val typeScore = scoreField(typeTokens, qTerm) * TYPE_WEIGHT
            if (typeScore > bestTermScore) bestTermScore = typeScore

            if (bestTermScore > 0.0) {
                totalScore += bestTermScore
                matchedTermCount++
            }
        }

        // Multi-term conjunction bonus: reward documents matching all terms
        if (queryTerms.size > 1 && matchedTermCount == queryTerms.size) {
            totalScore *= 1.45
        } else if (matchedTermCount == 0) {
            return 0.0
        }

        return totalScore
    }

    private fun scoreField(docTokens: List<String>, qTerm: String): Double {
        if (docTokens.isEmpty()) return 0.0

        var bestMatch = 0.0
        var termFrequency = 0

        for (token in docTokens) {
            val similarity = computeTokenSimilarity(token, qTerm)
            if (similarity > bestMatch) {
                bestMatch = similarity
            }
            if (similarity >= 0.8) {
                termFrequency++
            }
        }

        if (bestMatch <= 0.0) return 0.0

        // BM25-style frequency saturation: tf / (tf + 1.2)
        val tfSaturation = if (termFrequency > 0) {
            val tf = termFrequency.toDouble()
            (tf * 2.2) / (tf + 1.2)
        } else {
            1.0
        }

        return bestMatch * 10.0 * tfSaturation
    }

    /**
     * Determines the similarity between a document token and query term (0.0 to 1.0).
     */
    private fun computeTokenSimilarity(token: String, queryTerm: String): Double {
        if (token == queryTerm) return 1.0

        // Prefix match (e.g. "mob" matches "mobile")
        if (token.startsWith(queryTerm) && queryTerm.length >= 3) {
            val ratio = queryTerm.length.toDouble() / token.length.toDouble()
            return 0.80 + (ratio * 0.15)
        }

        // Substring match
        if (token.contains(queryTerm) && queryTerm.length >= 4) {
            return 0.70
        }

        // Damerau-Levenshtein Edit Distance (Handles typos e.g. "moblie" -> "mobile", "phne" -> "phone")
        val maxAllowedDistance = when {
            queryTerm.length <= 3 -> 0
            queryTerm.length <= 6 -> 1
            else -> 2
        }

        if (maxAllowedDistance > 0 && Math.abs(token.length - queryTerm.length) <= maxAllowedDistance) {
            val dist = damerauLevenshteinDistance(token, queryTerm, maxAllowedDistance)
            if (dist <= maxAllowedDistance) {
                val maxLen = max(token.length, queryTerm.length).toDouble()
                return (1.0 - (dist.toDouble() / maxLen)) * 0.80
            }
        }

        // Bigram Dice coefficient for longer words
        if (token.length >= 4 && queryTerm.length >= 4) {
            val dice = diceCoefficient(token, queryTerm)
            if (dice >= 0.72) {
                return dice * 0.75
            }
        }

        return 0.0
    }

    private val bufferMatrix = ThreadLocal.withInitial { Array(64) { IntArray(64) } }

    /**
     * Fast bounded Damerau-Levenshtein distance calculation with adjacent transposition support
     * and zero object allocation using a reusable thread-local matrix.
     */
    fun damerauLevenshteinDistance(s1: String, s2: String, maxLimit: Int = 4): Int {
        val len1 = s1.length
        val len2 = s2.length

        if (Math.abs(len1 - len2) > maxLimit) return maxLimit + 1

        val d = if (len1 < 63 && len2 < 63) {
            bufferMatrix.get()
        } else {
            Array(len1 + 1) { IntArray(len2 + 1) }
        }

        for (i in 0..len1) d[i][0] = i
        for (j in 0..len2) d[0][j] = j

        for (i in 1..len1) {
            var rowMin = Int.MAX_VALUE
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                var minVal = min(d[i - 1][j] + 1, min(d[i][j - 1] + 1, d[i - 1][j - 1] + cost))

                // Transposition
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    minVal = min(minVal, d[i - 2][j - 2] + cost)
                }

                d[i][j] = minVal
                if (minVal < rowMin) rowMin = minVal
            }
            if (rowMin > maxLimit) return maxLimit + 1
        }

        return d[len1][len2]
    }

    /**
     * Character bigram Dice-Sorensen coefficient (2 * |A ∩ B| / (|A| + |B|)).
     */
    private fun diceCoefficient(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.length < 2 || s2.length < 2) return 0.0

        val n1 = s1.length - 1
        val n2 = s2.length - 1

        val bigrams1 = mutableMapOf<Int, Int>()
        for (i in 0 until n1) {
            val code = (s1[i].code shl 16) or s1[i + 1].code
            bigrams1[code] = (bigrams1[code] ?: 0) + 1
        }

        var matches = 0
        for (j in 0 until n2) {
            val code = (s2[j].code shl 16) or s2[j + 1].code
            val count = bigrams1[code] ?: 0
            if (count > 0) {
                matches++
                bigrams1[code] = count - 1
            }
        }

        return (2.0 * matches) / (n1 + n2).toDouble()
    }

    fun tokenize(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lowercase()
            .split(WORD_SPLIT_REGEX)
            .filter { it.length >= 2 }
    }
}

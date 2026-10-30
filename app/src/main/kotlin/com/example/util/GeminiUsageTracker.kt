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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeminiUsageStats(
    val totalRequests: Int = 0,
    val totalTokens: Long = 0L,
    val promptTokens: Long = 0L,
    val candidateTokens: Long = 0L,
    val ocrRequests: Int = 0,
    val ocrTokens: Long = 0L,
    val audioRequests: Int = 0,
    val audioTokens: Long = 0L,
    val lastModelUsed: String = "",
    val lastUsedTimestamp: Long = 0L,
    val dailyUsage: Map<String, Long> = emptyMap()
)

object GeminiUsageTracker {
    private const val PREFS_NAME = "gemini_usage_prefs"
    private const val KEY_TOTAL_REQUESTS = "total_requests"
    private const val KEY_TOTAL_TOKENS = "total_tokens"
    private const val KEY_PROMPT_TOKENS = "prompt_tokens"
    private const val KEY_CANDIDATE_TOKENS = "candidate_tokens"
    private const val KEY_OCR_REQUESTS = "ocr_requests"
    private const val KEY_OCR_TOKENS = "ocr_tokens"
    private const val KEY_AUDIO_REQUESTS = "audio_requests"
    private const val KEY_AUDIO_TOKENS = "audio_tokens"
    private const val KEY_LAST_MODEL = "last_model"
    private const val KEY_LAST_TIMESTAMP = "last_timestamp"

    private val _usageStats = MutableStateFlow(GeminiUsageStats())
    val usageStats: StateFlow<GeminiUsageStats> = _usageStats.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _usageStats.value = loadFromPrefs(prefs)
        isInitialized = true
    }

    fun recordUsage(
        context: Context,
        taskType: String, // "OCR" or "AUDIO"
        promptTokens: Int,
        candidateTokens: Int,
        totalTokens: Int,
        modelName: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val dayKey = "day_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

        val currentTotalReqs = prefs.getInt(KEY_TOTAL_REQUESTS, 0) + 1
        val currentTotalTokens = prefs.getLong(KEY_TOTAL_TOKENS, 0L) + totalTokens
        val currentPromptTokens = prefs.getLong(KEY_PROMPT_TOKENS, 0L) + promptTokens
        val currentCandTokens = prefs.getLong(KEY_CANDIDATE_TOKENS, 0L) + candidateTokens

        val isOcr = taskType.equals("OCR", ignoreCase = true)
        val currentOcrReqs = prefs.getInt(KEY_OCR_REQUESTS, 0) + (if (isOcr) 1 else 0)
        val currentOcrTokens = prefs.getLong(KEY_OCR_TOKENS, 0L) + (if (isOcr) totalTokens else 0)

        val isAudio = taskType.equals("AUDIO", ignoreCase = true)
        val currentAudioReqs = prefs.getInt(KEY_AUDIO_REQUESTS, 0) + (if (isAudio) 1 else 0)
        val currentAudioTokens = prefs.getLong(KEY_AUDIO_TOKENS, 0L) + (if (isAudio) totalTokens else 0)

        val dayTokens = prefs.getLong(dayKey, 0L) + totalTokens

        prefs.edit()
            .putInt(KEY_TOTAL_REQUESTS, currentTotalReqs)
            .putLong(KEY_TOTAL_TOKENS, currentTotalTokens)
            .putLong(KEY_PROMPT_TOKENS, currentPromptTokens)
            .putLong(KEY_CANDIDATE_TOKENS, currentCandTokens)
            .putInt(KEY_OCR_REQUESTS, currentOcrReqs)
            .putLong(KEY_OCR_TOKENS, currentOcrTokens)
            .putInt(KEY_AUDIO_REQUESTS, currentAudioReqs)
            .putLong(KEY_AUDIO_TOKENS, currentAudioTokens)
            .putString(KEY_LAST_MODEL, modelName)
            .putLong(KEY_LAST_TIMESTAMP, now)
            .putLong(dayKey, dayTokens)
            .apply()

        _usageStats.value = loadFromPrefs(prefs)
    }

    fun resetStats(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _usageStats.value = GeminiUsageStats()
    }

    private fun loadFromPrefs(prefs: SharedPreferences): GeminiUsageStats {
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        val dailyMap = mutableMapOf<String, Long>()

        // 1. Scan all prefs to pick up any stored day_* entries
        prefs.all.forEach { (k, v) ->
            if (k.startsWith("day_")) {
                val datePart = k.removePrefix("day_")
                val tokVal = when (v) {
                    is Long -> v
                    is Int -> v.toLong()
                    is Number -> v.toLong()
                    else -> 0L
                }
                dailyMap[datePart] = tokVal
            }
        }

        // 2. Ensure standard keys for the past 7 days exist in both "yyyy-MM-dd" and "MMM d" formats
        for (i in 0..6) {
            val dateStr = dayFormat.format(cal.time)
            val key = "day_$dateStr"
            val dateLabel = SimpleDateFormat("MMM d", Locale.US).format(cal.time)
            val tokens = prefs.getLong(key, dailyMap[dateStr] ?: 0L)
            dailyMap[dateStr] = tokens
            dailyMap[dateLabel] = tokens
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }

        val totalTokens = prefs.getLong(KEY_TOTAL_TOKENS, 0L)
        val lastUsedTimestamp = prefs.getLong(KEY_LAST_TIMESTAMP, 0L)

        // 3. Auto-recovery: if all 7 daily buckets in dailyMap are 0L but totalTokens > 0L,
        // attribute totalTokens to the day of last usage (or yesterday) and save it.
        val totalDailyInLast7 = (0..6).sumOf { i ->
            val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -i) }
            dailyMap[dayFormat.format(c.time)] ?: 0L
        }

        if (totalDailyInLast7 == 0L && totalTokens > 0L) {
            val targetCal = java.util.Calendar.getInstance().apply {
                if (lastUsedTimestamp > 0L) {
                    timeInMillis = lastUsedTimestamp
                } else {
                    add(java.util.Calendar.DAY_OF_YEAR, -1) // yesterday
                }
            }
            val recoveryKey = dayFormat.format(targetCal.time)
            val recoveryLabel = SimpleDateFormat("MMM d", Locale.US).format(targetCal.time)
            dailyMap[recoveryKey] = totalTokens
            dailyMap[recoveryLabel] = totalTokens
            prefs.edit().putLong("day_$recoveryKey", totalTokens).apply()
        }

        return GeminiUsageStats(
            totalRequests = prefs.getInt(KEY_TOTAL_REQUESTS, 0),
            totalTokens = totalTokens,
            promptTokens = prefs.getLong(KEY_PROMPT_TOKENS, 0L),
            candidateTokens = prefs.getLong(KEY_CANDIDATE_TOKENS, 0L),
            ocrRequests = prefs.getInt(KEY_OCR_REQUESTS, 0),
            ocrTokens = prefs.getLong(KEY_OCR_TOKENS, 0L),
            audioRequests = prefs.getInt(KEY_AUDIO_REQUESTS, 0),
            audioTokens = prefs.getLong(KEY_AUDIO_TOKENS, 0L),
            lastModelUsed = prefs.getString(KEY_LAST_MODEL, "") ?: "",
            lastUsedTimestamp = lastUsedTimestamp,
            dailyUsage = dailyMap
        )
    }
}

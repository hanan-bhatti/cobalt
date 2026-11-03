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

package com.example.data.model

import kotlin.math.abs
import kotlin.math.round

/**
 * Supported local text fonts shipped inside the APK.
 */
enum class TextFontFamily(val key: String, val displayName: String, val isVariable: Boolean) {
    INTER("inter", "Inter", isVariable = true),
    LITERATA("literata", "Literata", isVariable = true),
    SOURCE_SERIF_4("source_serif_4", "Source Serif 4", isVariable = true),
    ATKINSON_HYPERLEGIBLE("atkinson_hyperlegible", "Atkinson Hyperlegible", isVariable = false),
    LEXEND("lexend", "Lexend", isVariable = true),
    SYSTEM("system", "System", isVariable = false);

    companion object {
        fun fromKey(key: String?): TextFontFamily =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: INTER
    }
}

/**
 * Supported local code and monospace fonts.
 */
enum class CodeFontFamily(val key: String, val displayName: String) {
    JETBRAINS_MONO("jetbrains_mono", "JetBrains Mono"),
    SYSTEM_MONOSPACE("system_monospace", "System monospace");

    companion object {
        fun fromKey(key: String?): CodeFontFamily =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: JETBRAINS_MONO
    }
}

/**
 * Ergonomic presets for quick typography tuning.
 */
enum class TypographyPreset(val key: String, val displayName: String) {
    COMPACT("compact", "Compact"),
    COMFORTABLE("comfortable", "Comfortable"),
    READING("reading", "Reading"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromKey(key: String?): TypographyPreset =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: COMFORTABLE
    }
}

/**
 * Immutable model holding typography preferences for notes, editor, reader, and app UI.
 */
data class TypographyPrefs(
    val useCustomFont: Boolean = true,
    val applyToAppUi: Boolean = false,
    val textFont: TextFontFamily = TextFontFamily.INTER,
    val codeFont: CodeFontFamily = CodeFontFamily.JETBRAINS_MONO,
    val fontSize: Int = 16,
    val fontWeight: Int = 400,
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0.0f,
    val paragraphSpacing: Int = 8,
    val preset: TypographyPreset = TypographyPreset.COMFORTABLE
) {
    /**
     * Clamps all metrics strictly within valid ranges, rounds to step increments,
     * and handles weight constraints for static vs variable fonts.
     */
    fun validated(): TypographyPrefs {
        val clampedSize = fontSize.coerceIn(12, 24)
        val clampedWeight = if (!textFont.isVariable) {
            // Static font weight snapping: Regular 400 or Bold 700
            if (fontWeight > 550) 700 else 400
        } else {
            // Variable font: step by 50 in 300..700
            ((fontWeight + 25) / 50 * 50).coerceIn(300, 700)
        }
        val clampedLineHeight = (round(lineHeight * 20f) / 20f).coerceIn(1.2f, 2.0f)
        val clampedLetterSpacing = (round(letterSpacing * 100f) / 100f).coerceIn(-0.02f, 0.10f)
        val clampedParaSpacing = ((paragraphSpacing + 1) / 2 * 2).coerceIn(0, 16)

        val matched = matchPreset(
            clampedSize,
            clampedWeight,
            clampedLineHeight,
            clampedLetterSpacing,
            clampedParaSpacing
        )

        return copy(
            fontSize = clampedSize,
            fontWeight = clampedWeight,
            lineHeight = clampedLineHeight,
            letterSpacing = clampedLetterSpacing,
            paragraphSpacing = clampedParaSpacing,
            preset = if (preset != TypographyPreset.CUSTOM && matched == preset) preset else matched
        )
    }

    /**
     * Applies preset metric values.
     */
    fun withPreset(newPreset: TypographyPreset): TypographyPrefs {
        return when (newPreset) {
            TypographyPreset.COMPACT -> copy(
                fontSize = 14,
                fontWeight = if (!textFont.isVariable && fontWeight > 550) 700 else 400,
                lineHeight = 1.3f,
                letterSpacing = 0.0f,
                paragraphSpacing = 4,
                preset = TypographyPreset.COMPACT
            ).validated()
            TypographyPreset.COMFORTABLE -> copy(
                fontSize = 16,
                fontWeight = if (!textFont.isVariable && fontWeight > 550) 700 else 400,
                lineHeight = 1.5f,
                letterSpacing = 0.0f,
                paragraphSpacing = 8,
                preset = TypographyPreset.COMFORTABLE
            ).validated()
            TypographyPreset.READING -> copy(
                fontSize = 18,
                fontWeight = if (!textFont.isVariable && fontWeight > 550) 700 else 400,
                lineHeight = 1.7f,
                letterSpacing = 0.01f,
                paragraphSpacing = 12,
                preset = TypographyPreset.READING
            ).validated()
            TypographyPreset.CUSTOM -> copy(preset = TypographyPreset.CUSTOM)
        }
    }

    companion object {
        val DEFAULT = TypographyPrefs()

        private fun isClose(a: Float, b: Float, epsilon: Float = 0.005f): Boolean =
            abs(a - b) < epsilon

        fun matchPreset(
            fontSize: Int,
            fontWeight: Int,
            lineHeight: Float,
            letterSpacing: Float,
            paragraphSpacing: Int
        ): TypographyPreset {
            return when {
                fontSize == 14 && fontWeight == 400 && isClose(lineHeight, 1.3f) && isClose(letterSpacing, 0.0f) && paragraphSpacing == 4 ->
                    TypographyPreset.COMPACT
                fontSize == 16 && fontWeight == 400 && isClose(lineHeight, 1.5f) && isClose(letterSpacing, 0.0f) && paragraphSpacing == 8 ->
                    TypographyPreset.COMFORTABLE
                fontSize == 18 && fontWeight == 400 && isClose(lineHeight, 1.7f) && isClose(letterSpacing, 0.01f) && paragraphSpacing == 12 ->
                    TypographyPreset.READING
                else -> TypographyPreset.CUSTOM
            }
        }
    }
}

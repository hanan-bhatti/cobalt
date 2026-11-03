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

package com.example.ui.theme

import com.example.data.model.CodeFontFamily
import com.example.data.model.TextFontFamily
import com.example.data.model.TypographyPrefs
import com.example.data.model.TypographyPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyPrefsTest {

    @Test
    fun defaultValues_areValidAndMatchComfortablePreset() {
        val prefs = TypographyPrefs()
        assertTrue(prefs.useCustomFont)
        assertFalse(prefs.applyToAppUi)
        assertEquals(TextFontFamily.INTER, prefs.textFont)
        assertEquals(CodeFontFamily.JETBRAINS_MONO, prefs.codeFont)
        assertEquals(16, prefs.fontSize)
        assertEquals(400, prefs.fontWeight)
        assertEquals(1.50f, prefs.lineHeight)
        assertEquals(0.0f, prefs.letterSpacing)
        assertEquals(8, prefs.paragraphSpacing)
        assertEquals(TypographyPreset.COMFORTABLE, prefs.preset)
        assertEquals(TypographyPreset.COMFORTABLE, TypographyPrefs.matchPreset(16, 400, 1.50f, 0.0f, 8))
    }

    @Test
    fun clampingAndSanitization_keepsValuesWithinBounds() {
        // Below bounds
        val clampedLow = TypographyPrefs(
            fontSize = 5,
            fontWeight = 100,
            lineHeight = 0.5f,
            letterSpacing = -0.5f,
            paragraphSpacing = -10
        ).validated()

        assertEquals(12, clampedLow.fontSize)
        assertEquals(300, clampedLow.fontWeight)
        assertEquals(1.2f, clampedLow.lineHeight, 0.001f)
        assertEquals(-0.02f, clampedLow.letterSpacing, 0.001f)
        assertEquals(0, clampedLow.paragraphSpacing)

        // Above bounds
        val clampedHigh = TypographyPrefs(
            fontSize = 50,
            fontWeight = 1000,
            lineHeight = 5.0f,
            letterSpacing = 1.0f,
            paragraphSpacing = 100
        ).validated()

        assertEquals(24, clampedHigh.fontSize)
        assertEquals(700, clampedHigh.fontWeight)
        assertEquals(2.0f, clampedHigh.lineHeight, 0.001f)
        assertEquals(0.10f, clampedHigh.letterSpacing, 0.001f)
        assertEquals(16, clampedHigh.paragraphSpacing)
    }

    @Test
    fun presets_applyExpectedMetrics() {
        val compact = TypographyPrefs().withPreset(TypographyPreset.COMPACT)
        assertEquals(TypographyPreset.COMPACT, compact.preset)
        assertEquals(14, compact.fontSize)
        assertEquals(400, compact.fontWeight)
        assertEquals(1.3f, compact.lineHeight, 0.001f)
        assertEquals(0.0f, compact.letterSpacing, 0.001f)
        assertEquals(4, compact.paragraphSpacing)

        val reading = TypographyPrefs().withPreset(TypographyPreset.READING)
        assertEquals(TypographyPreset.READING, reading.preset)
        assertEquals(18, reading.fontSize)
        assertEquals(400, reading.fontWeight)
        assertEquals(1.7f, reading.lineHeight, 0.001f)
        assertEquals(0.01f, reading.letterSpacing, 0.001f)
        assertEquals(12, reading.paragraphSpacing)
    }

    @Test
    fun customAdjustment_flipsPresetToCustom() {
        val comfortable = TypographyPrefs().withPreset(TypographyPreset.COMFORTABLE)
        assertEquals(TypographyPreset.COMFORTABLE, comfortable.preset)

        val modified = comfortable.copy(fontSize = 17).validated()
        assertEquals(TypographyPreset.CUSTOM, modified.preset)
    }

    @Test
    fun fontFamilies_variableClassificationAndStaticSnapping() {
        assertTrue(TextFontFamily.INTER.isVariable)
        assertTrue(TextFontFamily.LITERATA.isVariable)
        assertTrue(TextFontFamily.SOURCE_SERIF_4.isVariable)
        assertTrue(TextFontFamily.LEXEND.isVariable)
        assertFalse(TextFontFamily.SYSTEM.isVariable)

        // Atkinson Hyperlegible is static (Regular 400, Bold 700)
        assertFalse(TextFontFamily.ATKINSON_HYPERLEGIBLE.isVariable)

        val atkinsonLight = TypographyPrefs(
            textFont = TextFontFamily.ATKINSON_HYPERLEGIBLE,
            fontWeight = 350
        ).validated()
        assertEquals(400, atkinsonLight.fontWeight)

        val atkinsonMid = TypographyPrefs(
            textFont = TextFontFamily.ATKINSON_HYPERLEGIBLE,
            fontWeight = 500
        ).validated()
        assertEquals(400, atkinsonMid.fontWeight)

        val atkinsonBold = TypographyPrefs(
            textFont = TextFontFamily.ATKINSON_HYPERLEGIBLE,
            fontWeight = 600
        ).validated()
        assertEquals(700, atkinsonBold.fontWeight)
    }

    @Test
    fun keyDerivations_fromTypography() {
        val base = TypographyPrefs()
        val keyBase = "${base.fontSize}_${base.fontWeight}_${base.lineHeight}_${base.letterSpacing}_${base.paragraphSpacing}_${base.textFont.key}"

        val changedFont = base.copy(textFont = TextFontFamily.LITERATA)
        val keyChangedFont = "${changedFont.fontSize}_${changedFont.fontWeight}_${changedFont.lineHeight}_${changedFont.letterSpacing}_${changedFont.paragraphSpacing}_${changedFont.textFont.key}"
        assertFalse(keyBase == keyChangedFont)

        val changedSize = base.copy(fontSize = 18)
        val keyChangedSize = "${changedSize.fontSize}_${changedSize.fontWeight}_${changedSize.lineHeight}_${changedSize.letterSpacing}_${changedSize.paragraphSpacing}_${changedSize.textFont.key}"
        assertFalse(keyBase == keyChangedSize)
    }
}

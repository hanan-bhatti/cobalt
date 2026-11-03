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

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CodeFontFamily
import com.example.data.model.TextFontFamily
import com.example.data.model.TypographyPrefs
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache key for FontFamily instances.
 */
private data class FontFamilyCacheKey(
    val familyKey: String,
    val weight: Int
)

/**
 * Cache key for complete CobaltTypography instances.
 */
private data class TypographyCacheKey(
    val useCustomFont: Boolean,
    val applyToAppUi: Boolean,
    val textFont: TextFontFamily,
    val codeFont: CodeFontFamily,
    val fontSize: Int,
    val fontWeight: Int,
    val lineHeight: Float,
    val letterSpacing: Float,
    val paragraphSpacing: Int
)

private val fontFamilyCache = ConcurrentHashMap<FontFamilyCacheKey, FontFamily>()
private val typographyCache = ConcurrentHashMap<TypographyCacheKey, CobaltTypography>()

/**
 * Resolves and caches the text FontFamily for the given family and target weight.
 * Handles variable fonts on API 26+ with graceful fallback for API 24-25.
 */
@OptIn(ExperimentalTextApi::class)
fun resolveTextFontFamily(font: TextFontFamily, weight: Int): FontFamily {
    if (font == TextFontFamily.SYSTEM) return FontFamily.Default

    val key = FontFamilyCacheKey(font.key, weight)
    return fontFamilyCache.getOrPut(key) {
        when (font) {
            TextFontFamily.INTER -> createVariableFont(R.font.inter_variable, weight)
            TextFontFamily.LITERATA -> createVariableFont(R.font.literata_variable, weight)
            TextFontFamily.SOURCE_SERIF_4 -> createVariableFont(R.font.source_serif_4_variable, weight)
            TextFontFamily.LEXEND -> createVariableFont(R.font.lexend_variable, weight)
            TextFontFamily.ATKINSON_HYPERLEGIBLE -> createAtkinsonFamily()
            TextFontFamily.SYSTEM -> FontFamily.Default
        }
    }
}

/**
 * Resolves and caches the code/monospace FontFamily.
 */
@OptIn(ExperimentalTextApi::class)
fun resolveCodeFontFamily(font: CodeFontFamily, weight: Int = 400): FontFamily {
    if (font == CodeFontFamily.SYSTEM_MONOSPACE) return FontFamily.Monospace

    val key = FontFamilyCacheKey(font.key, weight)
    return fontFamilyCache.getOrPut(key) {
        when (font) {
            CodeFontFamily.JETBRAINS_MONO -> createVariableFont(R.font.jetbrains_mono_variable, weight)
            CodeFontFamily.SYSTEM_MONOSPACE -> FontFamily.Monospace
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun createVariableFont(resId: Int, targetWeight: Int): FontFamily {
    val clamped = targetWeight.coerceIn(100, 1000)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        FontFamily(
            Font(
                resId = resId,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(clamped)
                ),
                weight = FontWeight(clamped)
            )
        )
    } else {
        // Fallback for API 24-25: map to nearest standard discrete FontWeight
        FontFamily(
            Font(
                resId = resId,
                weight = FontWeight(clamped)
            )
        )
    }
}

private fun createAtkinsonFamily(): FontFamily {
    return FontFamily(
        Font(R.font.atkinson_hyperlegible_regular, weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(R.font.atkinson_hyperlegible_bold, weight = FontWeight.Bold, style = FontStyle.Normal),
        Font(R.font.atkinson_hyperlegible_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
        Font(R.font.atkinson_hyperlegible_bold_italic, weight = FontWeight.Bold, style = FontStyle.Italic)
    )
}

/**
 * Central typography holder for Cobalt. Exposes resolved text styles, sizes, line heights,
 * letter spacings, and paragraph spacings.
 */
data class CobaltTypography(
    val prefs: TypographyPrefs,
    val textFontFamily: FontFamily,
    val codeFontFamily: FontFamily,
    val body: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    val inlineCode: TextStyle,
    val codeBlock: TextStyle,
    val quote: TextStyle,
    val checklist: TextStyle,
    val paragraphSpacingDp: Dp,
    val lineHeightMultiplier: Float,
    val letterSpacingSp: Float
) {
    /**
     * Converts to Material 3 Typography when "Apply to app UI" is enabled.
     */
    fun toMaterialTypography(base: Typography): Typography {
        if (!prefs.useCustomFont || !prefs.applyToAppUi) return base
        val family = textFontFamily
        return Typography(
            displayLarge = base.displayLarge.copy(fontFamily = family),
            displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.copy(fontFamily = family),
            headlineMedium = base.headlineMedium.copy(fontFamily = family),
            headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family),
            titleMedium = base.titleMedium.copy(fontFamily = family),
            titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family),
            bodyMedium = base.bodyMedium.copy(fontFamily = family),
            bodySmall = base.bodySmall.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family),
            labelMedium = base.labelMedium.copy(fontFamily = family),
            labelSmall = base.labelSmall.copy(fontFamily = family)
        )
    }

    companion object {
        fun fromPrefs(rawPrefs: TypographyPrefs): CobaltTypography {
            val prefs = rawPrefs.validated()
            val cacheKey = TypographyCacheKey(
                useCustomFont = prefs.useCustomFont,
                applyToAppUi = prefs.applyToAppUi,
                textFont = prefs.textFont,
                codeFont = prefs.codeFont,
                fontSize = prefs.fontSize,
                fontWeight = prefs.fontWeight,
                lineHeight = prefs.lineHeight,
                letterSpacing = prefs.letterSpacing,
                paragraphSpacing = prefs.paragraphSpacing
            )

            return typographyCache.getOrPut(cacheKey) {
                buildTypography(prefs)
            }
        }

        private fun buildTypography(prefs: TypographyPrefs): CobaltTypography {
            val textFamily = if (prefs.useCustomFont) {
                resolveTextFontFamily(prefs.textFont, prefs.fontWeight)
            } else {
                FontFamily.Default
            }

            val codeFamily = if (prefs.useCustomFont) {
                resolveCodeFontFamily(prefs.codeFont, 400)
            } else {
                FontFamily.Monospace
            }

            val baseSize = if (prefs.useCustomFont) prefs.fontSize.toFloat() else 16f
            val baseWeight = if (prefs.useCustomFont) FontWeight(prefs.fontWeight) else FontWeight.Normal
            val lineMultiplier = if (prefs.useCustomFont) prefs.lineHeight else 1.5f
            val letterSpacingSp = if (prefs.useCustomFont) (baseSize * prefs.letterSpacing) else 0f
            val paraSpacing = if (prefs.useCustomFont) prefs.paragraphSpacing.dp else 8.dp

            val baseBody = TextStyle(
                fontFamily = textFamily,
                fontWeight = baseWeight,
                fontSize = baseSize.sp,
                lineHeight = (baseSize * lineMultiplier).sp,
                letterSpacing = letterSpacingSp.sp,
                textDirection = TextDirection.Content
            )

            val bodyMedium = baseBody.copy(
                fontSize = (baseSize * 0.9375f).sp,
                lineHeight = (baseSize * 0.9375f * lineMultiplier).sp
            )

            val bodySmall = baseBody.copy(
                fontSize = (baseSize * 0.75f).sp,
                lineHeight = (baseSize * 0.75f * lineMultiplier).sp
            )

            // Scaled heading styles
            val h1 = TextStyle(
                fontFamily = textFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (baseSize * 1.625f).sp,
                lineHeight = (baseSize * 1.625f * 1.25f).sp,
                letterSpacing = letterSpacingSp.sp,
                textDirection = TextDirection.Content
            )

            val h2 = TextStyle(
                fontFamily = textFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (baseSize * 1.35f).sp,
                lineHeight = (baseSize * 1.35f * 1.28f).sp,
                letterSpacing = letterSpacingSp.sp,
                textDirection = TextDirection.Content
            )

            val h3 = TextStyle(
                fontFamily = textFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (baseSize * 1.15f).sp,
                lineHeight = (baseSize * 1.15f * 1.3f).sp,
                letterSpacing = letterSpacingSp.sp,
                textDirection = TextDirection.Content
            )

            val h4 = TextStyle(
                fontFamily = textFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = baseSize.sp,
                lineHeight = (baseSize * 1.32f).sp,
                letterSpacing = letterSpacingSp.sp,
                textDirection = TextDirection.Content
            )

            val codeSize = (baseSize * 0.88f).sp
            val inlineCode = TextStyle(
                fontFamily = codeFamily,
                fontWeight = FontWeight.Normal,
                fontSize = codeSize,
                lineHeight = (baseSize * 0.88f * 1.35f).sp,
                textDirection = TextDirection.Ltr
            )

            val codeBlock = TextStyle(
                fontFamily = codeFamily,
                fontWeight = FontWeight.Normal,
                fontSize = codeSize,
                lineHeight = (baseSize * 0.88f * 1.45f).sp,
                textDirection = TextDirection.Ltr
            )

            val quote = baseBody.copy(
                fontStyle = FontStyle.Italic,
                lineHeight = (baseSize * lineMultiplier * 1.05f).sp
            )

            val checklist = baseBody

            return CobaltTypography(
                prefs = prefs,
                textFontFamily = textFamily,
                codeFontFamily = codeFamily,
                body = baseBody,
                bodyMedium = bodyMedium,
                bodySmall = bodySmall,
                h1 = h1,
                h2 = h2,
                h3 = h3,
                h4 = h4,
                inlineCode = inlineCode,
                codeBlock = codeBlock,
                quote = quote,
                checklist = checklist,
                paragraphSpacingDp = paraSpacing,
                lineHeightMultiplier = lineMultiplier,
                letterSpacingSp = letterSpacingSp
            )
        }

        fun createDefault(): CobaltTypography = fromPrefs(TypographyPrefs.DEFAULT)
    }
}

val LocalCobaltTypography = staticCompositionLocalOf { CobaltTypography.createDefault() }

@Composable
fun CobaltTypographyProvider(
    typography: CobaltTypography,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalCobaltTypography provides typography,
        content = content
    )
}

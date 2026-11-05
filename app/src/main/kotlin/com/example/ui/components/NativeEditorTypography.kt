package com.example.ui.components

import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.CodeFontFamily
import com.example.data.model.TextFontFamily
import com.example.ui.theme.CobaltTypography
import io.github.rosemoe.sora.widget.CodeEditor
import java.util.WeakHashMap

/** Shared by resident and disk viewport editors; the existing local typography provider owns preferences. */
private data class NativeTypographyKey(val prefs: com.example.data.model.TypographyPrefs, val code: Boolean, val densityDpi: Int, val fontScale: Float)
private val nativeTypographyKeys = WeakHashMap<CodeEditor, NativeTypographyKey>()
internal fun CodeEditor.applyCobaltTypography(typography: CobaltTypography, code: Boolean) {
    val key = NativeTypographyKey(typography.prefs, code, resources.configuration.densityDpi, resources.configuration.fontScale)
    if (nativeTypographyKeys[this] == key) return
    val prefs = typography.prefs
    val resource = if (!prefs.useCustomFont) 0 else if (code) {
        if (prefs.codeFont == CodeFontFamily.JETBRAINS_MONO) R.font.jetbrains_mono_variable else 0
    } else when (prefs.textFont) {
        TextFontFamily.INTER -> R.font.inter_variable
        TextFontFamily.LITERATA -> R.font.literata_variable
        TextFontFamily.SOURCE_SERIF_4 -> R.font.source_serif_4_variable
        TextFontFamily.ATKINSON_HYPERLEGIBLE -> if (prefs.fontWeight >= 550) R.font.atkinson_hyperlegible_bold else R.font.atkinson_hyperlegible_regular
        TextFontFamily.LEXEND -> R.font.lexend_variable
        TextFontFamily.SYSTEM -> 0
    }
    val base = if (resource == 0) { if (code) Typeface.MONOSPACE else Typeface.DEFAULT }
        else ResourcesCompat.getFont(context, resource) ?: if (code) Typeface.MONOSPACE else Typeface.DEFAULT
    val face = if (!prefs.useCustomFont) base else if (android.os.Build.VERSION.SDK_INT >= 28) {
        Typeface.create(base, prefs.fontWeight, false)
    } else Typeface.create(base, if (prefs.fontWeight >= 550) Typeface.BOLD else Typeface.NORMAL)
    textLetterSpacing = if (prefs.useCustomFont) typography.letterSpacingSp / prefs.fontSize.coerceAtLeast(1) else 0f
    if (typefaceText != face) setTypefaceText(face)
    setTextSize(if (prefs.useCustomFont) prefs.fontSize.toFloat() else 16f)
    val metrics = textPaint.fontMetrics
    val target = if (prefs.useCustomFont) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, typography.body.lineHeight.value, resources.displayMetrics) else metrics.descent - metrics.ascent
    setLineSpacing((target - (metrics.descent - metrics.ascent)).coerceAtLeast(0f), 1f)
    nativeTypographyKeys[this] = key
}

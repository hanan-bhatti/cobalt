package com.example.ui.screens

import androidx.annotation.StringRes
import com.example.R

/** Describes visual parameters only; blur levels do not certify speed or readability. */
@StringRes
internal fun blurAppearanceGuidance(radius: Int, tint: Float): Int {
    val blurLevel = when {
        radius <= 16 -> 0
        radius <= 33 -> 1
        else -> 2
    }
    val tintLevel = when {
        tint <= 0.25f -> 0
        tint <= 0.55f -> 1
        else -> 2
    }
    return when (blurLevel * 3 + tintLevel) {
        0 -> R.string.settings_blur_light_tint_light
        1 -> R.string.settings_blur_light_tint_medium
        2 -> R.string.settings_blur_light_tint_strong
        3 -> R.string.settings_blur_medium_tint_light
        4 -> R.string.settings_blur_medium_tint_medium
        5 -> R.string.settings_blur_medium_tint_strong
        6 -> R.string.settings_blur_strong_tint_light
        7 -> R.string.settings_blur_strong_tint_medium
        else -> R.string.settings_blur_strong_tint_strong
    }
}

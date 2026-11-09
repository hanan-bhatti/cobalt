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

package com.example.web.server

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.ui.theme.DarkColorScheme
import com.example.ui.theme.LightColorScheme
import org.json.JSONObject

/**
 * Extracts and exports Cobalt's active Android theme colors (Dark, Light, or Dynamic Material You)
 * as CSS variables and JSON so the web companion mirrors the phone's exact aesthetic.
 */
object WebThemeExporter {

    fun exportThemeJson(context: Context?): String {
        if (context == null) {
            return fallbackDarkThemeJson()
        }

        return try {
            val prefs = context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)
            val themeMode = prefs.getString("theme_mode", "Light") ?: "Light"
            val dynamicColor = prefs.getBoolean("dynamic_color", true)

            val isDark = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> {
                    val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    currentNightMode == Configuration.UI_MODE_NIGHT_YES
                }
            }

            val scheme: ColorScheme = when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                else -> if (isDark) DarkColorScheme else LightColorScheme
            }

            val colorsObj = JSONObject().apply {
                put("primary", hex(scheme.primary))
                put("onPrimary", hex(scheme.onPrimary))
                put("primaryContainer", hex(scheme.primaryContainer))
                put("onPrimaryContainer", hex(scheme.onPrimaryContainer))
                put("background", hex(scheme.background))
                put("onBackground", hex(scheme.onBackground))
                put("surface", hex(scheme.surface))
                put("onSurface", hex(scheme.onSurface))
                put("surfaceVariant", hex(scheme.surfaceVariant))
                put("onSurfaceVariant", hex(scheme.onSurfaceVariant))
                put("surfaceContainer", hex(scheme.surfaceContainer))
                put("surfaceContainerHigh", hex(scheme.surfaceContainerHigh))
                put("outline", hex(scheme.outline))
                put("outlineVariant", hex(scheme.outlineVariant))
            }

            JSONObject().apply {
                put("isDark", isDark)
                put("dynamicColor", dynamicColor)
                put("colors", colorsObj)
            }.toString()
        } catch (_: Exception) {
            fallbackDarkThemeJson()
        }
    }

    private fun hex(color: Color): String {
        val argb = color.toArgb()
        return String.format("#%06X", 0xFFFFFF and argb).lowercase()
    }

    private fun fallbackDarkThemeJson(): String {
        val colorsObj = JSONObject().apply {
            put("primary", "#d1bfa8")
            put("onPrimary", "#3a2c1f")
            put("primaryContainer", "#524234")
            put("onPrimaryContainer", "#eedbc4")
            put("background", "#141311")
            put("onBackground", "#ede0d4")
            put("surface", "#1e1612")
            put("onSurface", "#ede0d4")
            put("surfaceVariant", "#4f453b")
            put("onSurfaceVariant", "#d3c4b4")
            put("surfaceContainer", "#241c18")
            put("surfaceContainerHigh", "#2e2420")
            put("outline", "#9c8e82")
            put("outlineVariant", "#4f453b")
        }

        return JSONObject().apply {
            put("isDark", true)
            put("dynamicColor", false)
            put("colors", colorsObj)
        }.toString()
    }
}

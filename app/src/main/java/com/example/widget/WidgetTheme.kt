/*
 * Second Brain - A universal capture and personal knowledge archive
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

package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import com.example.ui.theme.DarkColorScheme
import com.example.ui.theme.LightColorScheme

@Composable
fun getWidgetColorProviders(): ColorProviders {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("second_brain_settings", Context.MODE_PRIVATE)

    val themeMode = prefs.getString("theme_mode", "System Default") ?: "System Default"
    val useDynamic = prefs.getBoolean("dynamic_color", true)

    val isSystemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val isDark = when (themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemDark
    }

    return if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val lightScheme = dynamicLightColorScheme(context)
        val darkScheme = dynamicDarkColorScheme(context)
        when (themeMode) {
            "Light" -> ColorProviders(light = lightScheme, dark = lightScheme)
            "Dark" -> ColorProviders(light = darkScheme, dark = darkScheme)
            else -> ColorProviders(light = lightScheme, dark = darkScheme)
        }
    } else {
        when (themeMode) {
            "Light" -> ColorProviders(light = LightColorScheme, dark = LightColorScheme)
            "Dark" -> ColorProviders(light = DarkColorScheme, dark = DarkColorScheme)
            else -> ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
        }
    }
}

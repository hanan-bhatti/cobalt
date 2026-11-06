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
 import androidx.compose.ui.graphics.Color
 import androidx.compose.foundation.isSystemInDarkTheme
 import androidx.compose.material3.MaterialTheme
 import androidx.compose.material3.darkColorScheme
 import androidx.compose.material3.dynamicDarkColorScheme
 import androidx.compose.material3.dynamicLightColorScheme
 import androidx.compose.material3.lightColorScheme
 import androidx.compose.runtime.Composable
 import androidx.compose.ui.platform.LocalContext

 // Color definitions
 val DarkColorScheme = darkColorScheme(
     primary = PrimaryDark,
     onPrimary = OnPrimaryDark,
     primaryContainer = PrimaryContainerDark,
     onPrimaryContainer = OnPrimaryContainerDark,
     secondary = SecondaryDark,
     onSecondary = OnSecondaryDark,
     secondaryContainer = SecondaryContainerDark,
     onSecondaryContainer = OnSecondaryContainerDark,
     tertiary = TertiaryDark,
     onTertiary = OnTertiaryDark,
     tertiaryContainer = TertiaryContainerDark,
     onTertiaryContainer = OnTertiaryContainerDark,
     background = BackgroundDark,
     onBackground = OnBackgroundDark,
     surface = SurfaceDark,
     onSurface = OnSurfaceDark,
     surfaceVariant = SurfaceVariantDark,
     onSurfaceVariant = OnSurfaceVariantDark,
     surfaceContainer = Color(0xFF241C18),
     surfaceContainerHigh = Color(0xFF2E2420),
     surfaceContainerHighest = Color(0xFF382D28),
     surfaceContainerLow = Color(0xFF1E1612),
     outline = OutlineDark,
     outlineVariant = OutlineVariantDark
 )

 // Light theme colors
 val LightColorScheme = lightColorScheme(
     primary = Primary,
     onPrimary = OnPrimary,
     primaryContainer = PrimaryContainer,
     onPrimaryContainer = OnPrimaryContainer,
     secondary = Secondary,
     onSecondary = OnSecondary,
     secondaryContainer = SecondaryContainer,
     onSecondaryContainer = OnSecondaryContainer,
     tertiary = Tertiary,
     onTertiary = OnTertiary,
     tertiaryContainer = TertiaryContainer,
     onTertiaryContainer = OnTertiaryContainer,
     background = Background,
     onBackground = OnBackground,
     surface = Surface,
     onSurface = OnSurface,
     surfaceVariant = SurfaceVariant,
     onSurfaceVariant = OnSurfaceVariant,
     surfaceContainer = Color(0xFFF5EFE9),
     surfaceContainerHigh = Color(0xFFECE4DC),
     surfaceContainerHighest = Color(0xFFE4DAD0),
     surfaceContainerLow = Color(0xFFFAF5F0),
     outline = Outline,
     outlineVariant = OutlineVariant
 )

 @Composable
 fun MyApplicationTheme(
     darkTheme: Boolean = false,
     dynamicColor: Boolean = false,
     typographyPrefs: com.example.data.model.TypographyPrefs = com.example.data.model.TypographyPrefs.DEFAULT,
     content: @Composable () -> Unit,
 ) {
     val context = LocalContext.current
     val colorScheme = when {
         dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
             if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
         }
         else -> if (darkTheme) DarkColorScheme else LightColorScheme
     }
     val cobaltTypography = CobaltTypography.fromPrefs(typographyPrefs)
     val appTypography = cobaltTypography.toMaterialTypography(Typography)
     MaterialTheme(colorScheme = colorScheme, typography = appTypography) {
         CobaltTypographyProvider(typography = cobaltTypography) {
             com.example.ui.components.CobaltSelectionMenuHost(content)
         }
     }
 }

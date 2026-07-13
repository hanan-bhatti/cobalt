package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SuperrPrimaryDark,
    onPrimary = SuperrOnPrimaryDark,
    primaryContainer = SuperrPrimaryContainerDark,
    onPrimaryContainer = SuperrOnPrimaryContainerDark,
    secondary = SuperrSecondaryDark,
    onSecondary = SuperrOnSecondaryDark,
    secondaryContainer = SuperrSecondaryContainerDark,
    onSecondaryContainer = SuperrOnSecondaryContainerDark,
    tertiary = SuperrTertiaryDark,
    onTertiary = SuperrOnTertiaryDark,
    tertiaryContainer = SuperrTertiaryContainerDark,
    onTertiaryContainer = SuperrOnTertiaryContainerDark,
    background = SuperrBackgroundDark,
    onBackground = SuperrOnBackgroundDark,
    surface = SuperrSurfaceDark,
    onSurface = SuperrOnSurfaceDark,
    surfaceVariant = SuperrSurfaceVariantDark,
    onSurfaceVariant = SuperrOnSurfaceVariantDark,
    outline = SuperrOutlineDark,
    outlineVariant = SuperrOutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = SuperrPrimary,
    onPrimary = SuperrOnPrimary,
    primaryContainer = SuperrPrimaryContainer,
    onPrimaryContainer = SuperrOnPrimaryContainer,
    secondary = SuperrSecondary,
    onSecondary = SuperrOnSecondary,
    secondaryContainer = SuperrSecondaryContainer,
    onSecondaryContainer = SuperrOnSecondaryContainer,
    tertiary = SuperrTertiary,
    onTertiary = SuperrOnTertiary,
    tertiaryContainer = SuperrTertiaryContainer,
    onTertiaryContainer = SuperrOnTertiaryContainer,
    background = SuperrBackground,
    onBackground = SuperrOnBackground,
    surface = SuperrSurface,
    onSurface = SuperrOnSurface,
    surfaceVariant = SuperrSurfaceVariant,
    onSurfaceVariant = SuperrOnSurfaceVariant,
    outline = SuperrOutline,
    outlineVariant = SuperrOutlineVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled dynamic to enforce Superr theme
    content: @Composable () -> Unit,
) {
    // We are forcing the light theme for Superr because it's specifically described as a light theme
    // However, we support a basic dark theme if needed.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

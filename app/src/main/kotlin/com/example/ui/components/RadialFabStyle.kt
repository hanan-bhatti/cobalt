package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Pick the higher-contrast foreground for an opaque category fill. */
internal fun radialFabIconColor(fill: Color): Color =
    if (fill.luminance() > 0.179f) Color.Black else Color.White

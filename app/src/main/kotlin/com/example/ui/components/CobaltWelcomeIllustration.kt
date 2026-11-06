/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * Welcome screen illustration composable - Material 3 Refined
 */

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun CobaltWelcomeIllustration(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(width = 260.dp, height = 150.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Organic Blob Backdrop (Reduced dominance and softer tint)
        Icon(
            painter = painterResource(id = R.drawable.ic_welcome_blob),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier.size(width = 230.dp, height = 135.dp)
        )

        // 2. Small, Understated Sparkle Accents
        Icon(
            painter = painterResource(id = R.drawable.ic_custom_sparkle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            modifier = Modifier
                .size(13.dp)
                .offset(x = 92.dp, y = (-42).dp)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_custom_sparkle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
            modifier = Modifier
                .size(9.dp)
                .offset(x = (-94).dp, y = 30.dp)
        )

        // 3. Floating Content Cards Behind Center Tile (Using app's canonical icons)
        // Left Card: Link (ic_custom_link)
        IllustrationCard(
            modifier = Modifier
                .offset(x = (-56).dp, y = (-6).dp)
                .rotate(-13f),
            iconRes = R.drawable.ic_custom_link,
            iconSize = 26.dp,
            contentDescription = "Links"
        )

        // Middle/Top Card: Image (ic_custom_image) (Elevated & top-aligned so icon is fully visible above Cobalt tile)
        IllustrationCard(
            modifier = Modifier
                .offset(x = 0.dp, y = (-40).dp)
                .rotate(-1f),
            iconRes = R.drawable.ic_custom_image,
            iconSize = 26.dp,
            contentDescription = "Images",
            contentAlignment = Alignment.TopCenter,
            containerPadding = PaddingValues(top = 8.dp)
        )

        // Right Card: Text / Notes (ic_custom_text)
        IllustrationCard(
            modifier = Modifier
                .offset(x = 56.dp, y = (-4).dp)
                .rotate(12f),
            iconRes = R.drawable.ic_custom_text,
            iconSize = 26.dp,
            contentDescription = "Notes"
        )

        // 4. Center Foreground Tile: Visually Dominant Cobalt Logo
        Surface(
            modifier = Modifier
                .offset(y = 10.dp)
                .size(76.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                ),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                // The monogram occupies ~70-75% of usable container width
                Icon(
                    painter = painterResource(id = R.drawable.ic_cobalt_logo),
                    contentDescription = "Cobalt",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(54.dp)
                )
            }
        }
    }
}

@Composable
private fun IllustrationCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp = 26.dp,
    contentDescription: String,
    contentAlignment: Alignment = Alignment.Center,
    containerPadding: PaddingValues = PaddingValues(0.dp)
) {
    Surface(
        modifier = modifier
            .size(width = 64.dp, height = 72.dp)
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.08f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(containerPadding),
            contentAlignment = contentAlignment
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Preview(name = "Welcome Illustration Preview", showBackground = true)
@Composable
fun CobaltWelcomeIllustrationPreview() {
    MaterialTheme {
        CobaltWelcomeIllustration()
    }
}

package com.example.ui.dock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** The miniature and the real dock render the same surface and transform, at different scale. */
@Composable
internal fun SideDockMorphSurface(
    side: String,
    card: SideDockCardGeometry,
    morphState: () -> SideDockMorph,
    modifier: Modifier = Modifier,
    interactionModifier: Modifier = Modifier,
    useBlur: Boolean = false,
    blurOpacity: Float = 0.30f,
    handleOpacity: Float = 0.7f,
    content: @Composable BoxScope.() -> Unit
) {
    // Observe each frame in this small renderer, keeping the capture UI out of recomposition.
    val morph = morphState()
    val colors = MaterialTheme.colorScheme
    val baseSurface = if (useBlur) colors.surfaceContainerHigh.copy(
        alpha = (blurOpacity + 0.35f).coerceIn(0.45f, 0.82f)
    ) else colors.surfaceContainerHigh
    val handleColor = if (useBlur) colors.primary.copy(alpha = handleOpacity.coerceIn(0.6f, 1f)) else colors.primary
    val shape = if (side == "Right") RoundedCornerShape(
        topStart = morph.innerCornerDp.dp, bottomStart = morph.innerCornerDp.dp,
        topEnd = morph.outerCornerDp.dp, bottomEnd = morph.outerCornerDp.dp
    ) else RoundedCornerShape(
        topEnd = morph.innerCornerDp.dp, bottomEnd = morph.innerCornerDp.dp,
        topStart = morph.outerCornerDp.dp, bottomStart = morph.outerCornerDp.dp
    )

    Surface(
        modifier = modifier.width(card.widthDp.dp).height(card.heightDp.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(if (side == "Right") 1f else 0f, 0.5f)
                scaleX = morph.scaleX
                scaleY = morph.scaleY
                translationX = morph.translationXDp.dp.toPx()
                translationY = morph.translationYDp.dp.toPx()
                alpha = morph.containerAlpha
                this.shape = shape
                clip = true
            }.then(interactionModifier),
        shape = shape,
        color = lerp(handleColor, baseSurface, morph.surfaceFraction),
        border = BorderStroke(1.dp, lerp(
            Color.Transparent, colors.outlineVariant.copy(alpha = if (useBlur) 0.50f else 0.65f), morph.progressFraction
        )),
        tonalElevation = if (useBlur) 0.dp else 4.dp,
        shadowElevation = ((if (useBlur) 12f else 6f) * morph.progressFraction).dp
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}

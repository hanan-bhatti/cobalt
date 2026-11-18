package com.example.ui.dock

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring

/** The overlay and its Settings preview use this one spring definition. */
data class SideDockPhysics(
    val openingStiffness: Float,
    val openingDamping: Float,
    val closingStiffness: Float,
    val closingDamping: Float,
    val instant: Boolean = false
) {
    fun openingSpec(): AnimationSpec<Float> = if (instant) snap() else spring(
        stiffness = openingStiffness, dampingRatio = openingDamping, visibilityThreshold = 0.001f
    )

    fun closingSpec(): AnimationSpec<Float> = if (instant) snap() else spring(
        stiffness = closingStiffness, dampingRatio = closingDamping, visibilityThreshold = 0.001f
    )
}

val sideDockPresetIds = listOf("Smooth", "Fluid", "Bouncy", "Snappy", "Instant")

fun sideDockPhysics(preset: String, stiffness: Float = 340f, damping: Float = 0.72f): SideDockPhysics =
    when (preset) {
        // Distinct response profiles: steady, viscous, elastic, and immediate.
        "Fluid" -> SideDockPhysics(170f, 0.88f, 420f, 1f)
        "Bouncy" -> SideDockPhysics(380f, 0.52f, 700f, 0.85f)
        "Snappy" -> SideDockPhysics(900f, 1f, 1000f, 1f)
        "Instant" -> SideDockPhysics(1000f, 1f, 1000f, 1f, instant = true)
        "Custom" -> SideDockPhysics(
            stiffness.coerceIn(120f, 900f), damping.coerceIn(0.45f, 1f),
            (stiffness + 60f).coerceIn(180f, 1000f), (damping + 0.10f).coerceIn(0.55f, 1f)
        )
        else -> SideDockPhysics(430f, 1f, 650f, 1f)
    }

data class SideDockPlacement(val topDp: Float, val startOffsetYDp: Float, val isTopDock: Boolean)

fun sideDockPlacement(
    screenHeightDp: Float,
    card: SideDockCardGeometry,
    handleCenterYDp: Float,
    isLandscape: Boolean
): SideDockPlacement {
    val baseTop = handleCenterYDp - card.heightDp / 2f
    val minTop = if (isLandscape) 12f else 36f
    val maxTop = (screenHeightDp - card.heightDp - minTop).coerceAtLeast(minTop)
    val top = baseTop.coerceIn(minTop, maxTop)
    return SideDockPlacement(top, baseTop - top, top + card.heightDp / 2f < screenHeightDp / 2f)
}

data class SideDockMorph(
    val scaleX: Float,
    val scaleY: Float,
    val translationXDp: Float,
    val translationYDp: Float,
    val innerCornerDp: Float,
    val outerCornerDp: Float,
    val containerAlpha: Float,
    val contentAlpha: Float,
    val contentTranslationXDp: Float,
    val scrimAlpha: Float,
    val surfaceFraction: Float,
    val progressFraction: Float
)

/** Keep spring overshoot: only the color, corners, reveal and edge margin are clamped. */
fun sideDockMorph(
    progress: Float,
    card: SideDockCardGeometry,
    handleHeightDp: Int,
    handleThicknessDp: Int,
    side: String,
    startOffsetYDp: Float = 0f,
    predictiveBackProgress: Float = 0f,
    isDismissing: Boolean = false
): SideDockMorph {
    val fraction = progress.coerceIn(0f, 1f)
    val p = progress.coerceAtLeast(0f)
    val direction = if (side == "Right") 1f else -1f
    val minX = (handleThicknessDp.toFloat() / card.widthDp).coerceIn(0.01f, 0.25f)
    val minY = (handleHeightDp.toFloat() / card.heightDp).coerceIn(0.05f, 0.45f)
    val backScale = 1f - predictiveBackProgress * 0.12f
    val contentAlpha = ((progress - 0.38f) / 0.62f).coerceIn(0f, 1f)
    return SideDockMorph(
        scaleX = (minX + (1f - minX) * p).coerceAtLeast(minX) * backScale,
        scaleY = (minY + (1f - minY) * p).coerceAtLeast(minY) * backScale,
        translationXDp = direction * (1f - fraction) * 12f + card.widthDp * 0.35f * predictiveBackProgress * direction,
        translationYDp = startOffsetYDp * (1f - fraction),
        innerCornerDp = 8f + 16f * fraction,
        outerCornerDp = 24f * fraction,
        containerAlpha = if (isDismissing) (progress / 0.04f).coerceIn(0f, 1f) else 1f,
        contentAlpha = contentAlpha,
        contentTranslationXDp = direction * (1f - contentAlpha) * 16f,
        scrimAlpha = (progress * (1f - predictiveBackProgress * 0.35f)).coerceIn(0f, 1f) * 0.12f,
        surfaceFraction = (progress / 0.32f).coerceIn(0f, 1f),
        progressFraction = fraction
    )
}

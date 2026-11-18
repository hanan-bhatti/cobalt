package com.example.ui.dock

/** The resting card size, shared by the overlay, its content, and the Appearance preview. */
data class SideDockCardGeometry(val widthDp: Int, val heightDp: Int)

fun sideDockCardGeometry(screenHeightDp: Int, isLandscape: Boolean): SideDockCardGeometry =
    SideDockCardGeometry(
        widthDp = if (isLandscape) 330 else 280,
        heightDp = if (isLandscape) (screenHeightDp - 48).coerceAtMost(330) else 460
    )

fun sideDockHandleHeightDp(requestedHeightDp: Int, screenHeightDp: Int, isLandscape: Boolean): Int =
    if (isLandscape) (screenHeightDp * 0.14f).toInt().coerceIn(44, 68) else requestedHeightDp

/** WindowManager's offset from the screen center, including the dock's existing edge clearance. */
fun sideDockHandleOffsetYPx(yPercent: Float, screenHeightPx: Int, density: Float, isLandscape: Boolean): Int {
    val insetPx = ((if (isLandscape) 80 else 160) * density).toInt()
    val usableHeight = (screenHeightPx - insetPx).coerceAtLeast((60 * density).toInt())
    return (yPercent.coerceIn(0f, 1f) * usableHeight - usableHeight / 2).toInt()
}

/** Mirrors the handle window's center gravity and visible-frame limits. */
fun sideDockHandleCenterYDp(
    screenHeightDp: Float,
    offsetYDp: Float,
    handleHeightDp: Int,
    navigationTopDp: Float = 0f,
    navigationBottomDp: Float = 0f,
    statusTopDp: Float = 0f
): Float {
    val windowBottom = screenHeightDp - navigationBottomDp
    val halfHandle = handleHeightDp / 2f
    val minCenter = maxOf(navigationTopDp, statusTopDp) + halfHandle
    val maxCenter = (windowBottom - halfHandle).coerceAtLeast(minCenter)
    return ((navigationTopDp + windowBottom) / 2f + offsetYDp).coerceIn(minCenter, maxCenter)
}

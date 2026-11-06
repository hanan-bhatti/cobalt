package com.example.ui.components

import kotlin.math.*

/** Matches the fixed quarter-circle layout; never selects an unrelated sector. */
internal fun radialFabIndex(x: Float, y: Float, radius: Float, count: Int): Int {
    if (count <= 0 || radius <= 0 || !x.isFinite() || !y.isFinite()) return -1
    val distance = hypot(x, y)
    if (distance < radius * 0.6f || distance > radius + 32f) return -1
    val angle = (Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 360.0) % 360.0
    val step = if (count == 1) 90.0 else 90.0 / (count - 1)
    if (angle < 180.0 - step / 2 || angle > 270.0 + step / 2) return -1
    return ((angle - 180.0) / step).roundToInt().coerceIn(0, count - 1)
}

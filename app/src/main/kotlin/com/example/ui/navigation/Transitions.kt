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

package com.example.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

// Curva Emphasized oficial de Material 3 (la que usan los Pixel contemporáneos)
val M3EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
const val AOSP_TRANSITION_DURATION = 350

// Equivale a sud_slide_next_in
fun aospSharedAxisEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it / 3 }, // Arranca desde 1/3 de la pantalla hacia la derecha
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// Equivale a sud_slide_next_out
fun aospSharedAxisExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it / 3 }, // Se desplaza 1/3 de la pantalla hacia la izquierda
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + scaleOut(
        targetScale = 0.92f, // Escala hacia abajo para dar efecto de profundidad sin desaparecer por completo
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// Equivale a sud_slide_back_in (cuando volvés atrás, la pantalla previa reaparece desde la izquierda)
fun aospSharedAxisPopEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { -it / 3 }, // Reaparece desde 1/3 de la izquierda
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// Equivale a sud_slide_back_out (la pantalla secundaria se destruye deslizándose a la derecha)
fun aospSharedAxisPopExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it }, // Se desliza completamente fuera de la pantalla
        animationSpec = tween(
            durationMillis = AOSP_TRANSITION_DURATION,
            easing = Easing { f -> f * f * f } // Durante el arrastre se desplaza muy poco, y al completarse se desliza por completo
        )
    ) + scaleOut(
        targetScale = 0.85f, // Se achica más (efecto de profundidad más pronunciado)
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

// MD3 Expressive – Emphasized easing (matches Material Motion spec)
// cubic-bezier(0.2, 0, 0, 1.0) — fast start, smooth settle
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Decelerate for elements entering the screen — mirror of EmphasizedAccelerateEasing
val EmphasizedDecelerateEasing = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)

// Accelerate for elements leaving the screen
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

const val TRANSITION_DURATION = 450

// Push: Enter from Right
fun enterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.5f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing)
)

// Push: Exit to Left
fun exitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

// Pop: Enter from Left
fun popEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.25f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.95f
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedDecelerateEasing)
)

// Pop: Exit to Right
fun popExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.5f).toInt() }
) + scaleOut(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

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

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape

@OptIn(ExperimentalSharedTransitionApi::class)
val AppleSpringBoundsTransform = BoundsTransform { _, _ ->
    spring(
        dampingRatio = 0.85f,
        stiffness = 380f
    )
}

val PhysicsSpringBoundsTransform = AppleSpringBoundsTransform

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.maybeSharedElement(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: Any,
    boundsTransform: BoundsTransform = AppleSpringBoundsTransform,
    clipShape: Shape? = null
): Modifier = composed {
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            val clip = if (clipShape != null) OverlayClip(clipShape) else null
            if (clip != null) {
                this@composed.sharedElement(
                    sharedContentState = rememberSharedContentState(key = key),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = boundsTransform,
                    clipInOverlayDuringTransition = clip,
                    zIndexInOverlay = 2f
                )
            } else {
                this@composed.sharedElement(
                    sharedContentState = rememberSharedContentState(key = key),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = boundsTransform,
                    zIndexInOverlay = 2f
                )
            }
        }
    } else {
        this
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.maybeSharedBounds(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: Any,
    boundsTransform: BoundsTransform = AppleSpringBoundsTransform,
    clipShape: Shape? = null
): Modifier = composed {
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            val clip = if (clipShape != null) OverlayClip(clipShape) else null
            val enterSpec = fadeIn(
                animationSpec = tween(
                    durationMillis = 200,
                    delayMillis = 40,
                    easing = FastOutSlowInEasing
                )
            )
            val exitSpec = fadeOut(
                animationSpec = tween(
                    durationMillis = 110,
                    easing = LinearEasing
                )
            )
            if (clip != null) {
                this@composed.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = key),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = boundsTransform,
                    clipInOverlayDuringTransition = clip,
                    enter = enterSpec,
                    exit = exitSpec,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    zIndexInOverlay = 2f
                )
            } else {
                this@composed.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = key),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = boundsTransform,
                    enter = enterSpec,
                    exit = exitSpec,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    zIndexInOverlay = 2f
                )
            }
        }
    } else {
        this
    }
}

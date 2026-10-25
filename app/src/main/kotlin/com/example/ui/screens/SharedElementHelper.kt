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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape

@OptIn(ExperimentalSharedTransitionApi::class)
val PhysicsSpringBoundsTransform = BoundsTransform { _, _ ->
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.maybeSharedElement(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: Any,
    boundsTransform: BoundsTransform = PhysicsSpringBoundsTransform,
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
                    clipInOverlayDuringTransition = clip
                )
            } else {
                this@composed.sharedElement(
                    sharedContentState = rememberSharedContentState(key = key),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = boundsTransform
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
    boundsTransform: BoundsTransform = PhysicsSpringBoundsTransform,
    clipShape: Shape? = null
): Modifier = composed {
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            val clip = if (clipShape != null) OverlayClip(clipShape) else null
            val enterSpec = androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 320,
                    easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
                )
            )
            val exitSpec = androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 280,
                    easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
                )
            )
            if (clip != null) {
                this@composed.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = key),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = boundsTransform,
                    clipInOverlayDuringTransition = clip,
                    enter = enterSpec,
                    exit = exitSpec
                )
            } else {
                this@composed.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = key),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = boundsTransform,
                    enter = enterSpec,
                    exit = exitSpec
                )
            }
        }
    } else {
        this
    }
}

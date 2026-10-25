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

import android.os.Build
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Caches the RenderEffect Java object so we avoid allocating a new
 * blur effect on every animation frame.
 */
class BlurEffectCache {
    private var lastRadiusPx: Float = Float.NaN
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null

    fun get(radiusPx: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (radiusPx <= 0f) {
            lastRadiusPx = 0f
            cached = null
            return null
        }
        if (radiusPx != lastRadiusPx) {
            lastRadiusPx = radiusPx
            cached = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.graphics.RenderEffect
                    .createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            } else {
                null
            }
        }
        return cached
    }
}
